package com.bone.metadata.runtime.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.application.support.CatalogIdempotencySupport;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.RuntimePageQuery;
import com.bone.metadata.runtime.application.RuntimeAccessGuard;
import com.bone.metadata.runtime.application.RuntimeActionDescribeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模式 B：已发布 RUNTIME 实体的动态 CRUD（META-002B）。
 *
 * <p>横切：列表 {@code fields|sort|q}；更新 {@code If-Match}；创建 {@code Idempotency-Key}。
 */
@RestController
@RequestMapping("/api/v1/runtime/entities/{entityCode}/records")
@RequiredArgsConstructor
public class RuntimeRecordController {

  /**
   * 注意：此处直接注入 {@code JdbcRuntimeRecordService}（位于 engine.runtime 包，非 {@code application} 包）， 命中
   * {@code adapterControllersMustNotDependOnGodObjects} 谓词，属 <strong>已冻结的存量例外</strong>
   * （metadata-server {@code ArchitectureTest} 中 {@code FreezingArchRule.freeze(...)} 登记）。 根因：动态元数据
   * schema 无法有类型化 {@code *ApplicationService}，运行记录以 {@code Map<String,Object>} 承载， 统一信封（{@code
   * ApiResponse<PageResult<Map<String,Object>>>}）正确，但 payload 非结构化 DTO。 若未来引入类型化投影，应改为经
   * ApplicationService，并同步解冻该 ArchUnit 规则。
   */
  private final JdbcRuntimeRecordService runtimeRecordService;

  private final CatalogIdempotencySupport catalogIdempotencySupport;
  private final ObjectMapper objectMapper;
  private final TenantProvider tenantProvider;
  private final com.bone.metadata.runtime.application.RuntimeStatusGuard statusGuard;
  private final RuntimeAccessGuard accessGuard;
  private final RuntimeActionDescribeSupport describeSupport;
  private final com.bone.metadata.runtime.application.RuntimeActionExecutionRepository
      actionExecutionRepository;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<PageResult<Map<String, Object>>> page(
      @PathVariable String entityCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String fields,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q) {
    accessGuard.requireRead(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    RuntimePageQuery query = RuntimePageQuery.parse(fields, sort, q);
    PageResult<Map<String, Object>> apiPage =
        CatalogPageMapper.toApiPage(
            runtimeRecordService.page(entityCode, tenantId, page, size, query, resolveRowOwnerId()),
            row -> row);
    // 动作发现唯一形态（Runtime §4.3.1）：按 status 分组一次算全量，天然防 N+1，列表与详情共用一份
    apiPage.setMeta(describeSupport.describe(tenantId, entityCode));
    return ApiResponse.success(apiPage);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> get(
      @PathVariable String entityCode, @PathVariable String id) {
    accessGuard.requireRead(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    Map<String, Object> row =
        runtimeRecordService.getById(entityCode, tenantId, id, resolveRowOwnerId());
    Object version = row.get("version");
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (version instanceof Number number) {
      builder.eTag(CatalogHttpSupport.formatEtag(number.intValue()));
    }
    return builder.body(ApiResponse.success(row));
  }

  /**
   * 记录的动作执行时间线（按时间正序）。
   *
   * <p>为什么单独一个端点：执行日志（{@code meta_runtime_action_execution}）此前只服务幂等去重的点查，
   * 没有按记录回放的读路径；而「这张单据被谁何时提交/审批/打款、每次结果如何」是审计刚需 （等保/SOX）。P0-2 的半事务时序保证任何一次执行都留有痕迹（含孤儿），时间线即业务事实的投影。
   *
   * <p>读权限与记录详情一致（{@code requireRead}）：能看单据就能看它的变更历史—— 反过来（能看历史却看不了单据） 没有任何业务意义，且会诱导越权探测。
   */
  @GetMapping("/{id}/action-executions")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<List<Map<String, Object>>> actionExecutions(
      @PathVariable String entityCode, @PathVariable String id) {
    accessGuard.requireRead(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    return ApiResponse.success(actionExecutionRepository.findByRecord(tenantId, entityCode, id));
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @PathVariable String entityCode,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody Map<String, Object> body)
      throws JsonProcessingException {
    accessGuard.requireWrite(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    // 状态机守卫：客户端不得指定状态字段（指定即 422）；入口状态由写路径按转移矩阵注入
    statusGuard.applyCreateGuard(tenantId, entityCode, body);
    String path = "/api/v1/runtime/entities/" + entityCode + "/records";
    String fingerprint =
        CatalogIdempotencySupport.fingerprint(objectMapper.writeValueAsString(body));
    var apiType =
        objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, Map.class);
    Optional<ResponseEntity<ApiResponse<Map<String, Object>>>> replay =
        catalogIdempotencySupport.replay(idempotencyKey, "POST", path, fingerprint, apiType);
    if (replay.isPresent()) {
      return replay.get();
    }

    long newId = DistributedIdGenerator.generateLongId();
    Map<String, Object> created =
        runtimeRecordService.create(entityCode, tenantId, body, newId, currentOperator());
    Object pk = created != null ? created.getOrDefault("id", newId) : newId;
    ResponseEntity<ApiResponse<Map<String, Object>>> response =
        ResponseEntity.created(
                URI.create("/api/v1/runtime/entities/" + entityCode + "/records/" + pk))
            .body(ApiResponse.success(created));
    catalogIdempotencySupport.rememberApiResponse(
        idempotencyKey, "POST", path, fingerprint, response);
    return response;
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> update(
      @PathVariable String entityCode,
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody Map<String, Object> body) {
    accessGuard.requireWrite(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    // 状态机守卫：状态字段只允许经动作端点流转（G14 收口，客户端携带即 422）
    statusGuard.applyUpdateGuard(tenantId, entityCode, body);
    Map<String, Object> updated =
        runtimeRecordService.update(
            entityCode,
            tenantId,
            id,
            body,
            CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null),
            currentOperator());
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    Object version = updated.get("version");
    if (version instanceof Number number) {
      builder.eTag(CatalogHttpSupport.formatEtag(number.intValue()));
    }
    return builder.body(ApiResponse.success(updated));
  }

  /**
   * 归档（软删）。
   *
   * <p>{@code If-Match} <strong>可选但生效</strong>（判据 R13）：携带时按版本前置条件校验，冲突 412；
   * 未携带时按无并发保护处理。归档记录进回收站（{@code GET /records/deleted}），可经 {@code restore} 恢复。
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Void> delete(
      @PathVariable String entityCode,
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    accessGuard.requireWrite(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    runtimeRecordService.delete(
        entityCode,
        tenantId,
        id,
        CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null),
        currentOperator());
    return ApiResponse.success();
  }

  /**
   * 回收站视图（只查 {@code deleted = 1}）。
   *
   * <p>为什么必须补：Runtime §0.3 实测——软删单向不可逆，且列探测失败会静默降级为<strong>物理 DELETE</strong>。
   * 「不可恢复」只能是<strong>明示</strong>，不能是默认行为；否则一次误归档就是永久丢数据。
   */
  @GetMapping("/deleted")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<PageResult<Map<String, Object>>> trash(
      @PathVariable String entityCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String fields,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q) {
    accessGuard.requireRead(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    RuntimePageQuery query = RuntimePageQuery.parse(fields, sort, q);
    return ApiResponse.success(
        CatalogPageMapper.toApiPage(
            runtimeRecordService.pageDeleted(entityCode, tenantId, page, size, query), row -> row));
  }

  /** 恢复归档记录（回收站 → 在用）。 */
  @PostMapping("/{id}/restore")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> restore(
      @PathVariable String entityCode, @PathVariable String id) {
    accessGuard.requireWrite(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    return ApiResponse.success(
        runtimeRecordService.restore(entityCode, tenantId, id, currentOperator()));
  }

  /**
   * 从 Spring Security 上下文取当前操作者标识，用于审计列写入。
   *
   * <p>优先级：① 认证详情中的数值 userId（JWT 的 userId claim，审计所需的 BIGINT 用户 ID，见 {@code
   * JwtAuthenticationFilter} 写入 details）；② 主体名（仅当其本身为数值时）。 未认证、匿名主体（dev 关闭安全时为
   * anonymousUser）或主体名非数值时返回 null —— 由 {@code JdbcRuntimeRecordService#stampAuditColumns} 负责跳过
   * BIGINT 审计列、仅注入时间戳，避免把字符串写入 BIGINT 报 "Incorrect integer value"。
   */
  private static String currentOperator() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || auth
            instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
      return null;
    }
    Object details = auth.getDetails();
    if (details instanceof Number) {
      return details.toString();
    }
    if (details instanceof String s && s.matches("\\d+")) {
      return s;
    }
    String name = auth.getName();
    if (name != null && name.matches("\\d+")) {
      return name;
    }
    // 主体名为非数值（如 username）时仍返回，交由下层按数值校验跳过 BIGINT 列
    return name;
  }

  /**
   * 行级 ACL 操作者判定（Runtime §4.7.1）：平台管理员传 null（豁免）；非数值主体传 null（与 BIGINT created_by
   * 无可比性，按既有审计语义不启用）；否则传数值用户 ID，数据面按本人过滤。
   */
  private Long resolveRowOwnerId() {
    if (accessGuard.isPlatformAdmin()) {
      return null;
    }
    String op = currentOperator();
    if (op == null || !op.matches("\\d+")) {
      return null;
    }
    return Long.parseLong(op);
  }
}
