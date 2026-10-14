package com.bone.metadata.runtime.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.support.CatalogIdempotencySupport;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.engine.runtime.RuntimePageQuery;
import com.bone.metadata.runtime.application.RuntimeAccessGuard;
import com.bone.metadata.runtime.application.RuntimeActionDescribeSupport;
import com.bone.metadata.runtime.application.RuntimeRecordApplicationService;
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
 *
 * <p><b>分层</b>（ADR-0028）：控制器只做 HTTP 适配（参数绑定、ETag/If-Match 头、幂等重放、ResponseEntity
 * 组装）；记录编排与横切判定（租户、审计操作者、行级 ACL owner、状态机守卫、ID 生成）在 {@link RuntimeRecordApplicationService}。本类不持有任何
 * engine 持久化服务——原直注 {@code JdbcRuntimeRecordService} 的写法曾绕过 god-object 门禁谓词（该规则只查 {@code
 * ..application..} 包，而 Jdbc 服务在 {@code engine.runtime} 包），收敛后由「adapter 不得 depend on engine
 * 运行时持久化服务」规则兜底。
 */
@RestController
@RequestMapping("/api/v1/runtime/entities/{entityCode}/records")
@RequiredArgsConstructor
public class RuntimeRecordController {

  private final RuntimeRecordApplicationService recordService;

  private final CatalogIdempotencySupport catalogIdempotencySupport;
  private final ObjectMapper objectMapper;
  private final RuntimeAccessGuard accessGuard;
  private final RuntimeActionDescribeSupport describeSupport;
  private final com.bone.metadata.runtime.application.RuntimeActionExecutionRepository
      actionExecutionRepository;

  @GetMapping
  @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")
  public ApiResponse<PageResult<Map<String, Object>>> page(
      @PathVariable String entityCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String fields,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q) {
    accessGuard.requireRead(entityCode);
    RuntimePageQuery query = RuntimePageQuery.parse(fields, sort, q);
    PageResult<Map<String, Object>> apiPage =
        CatalogPageMapper.toApiPage(recordService.page(entityCode, page, size, query), row -> row);
    // 动作发现唯一形态（Runtime §4.3.1）：按 status 分组一次算全量，天然防 N+1，列表与详情共用一份
    apiPage.setMeta(describeSupport.describe(recordService.currentTenantId(), entityCode));
    return ApiResponse.success(apiPage);
  }

  @GetMapping("/{id}")
  @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")
  public ResponseEntity<ApiResponse<Map<String, Object>>> get(
      @PathVariable String entityCode, @PathVariable String id) {
    accessGuard.requireRead(entityCode);
    Map<String, Object> row = recordService.getById(entityCode, id);
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
  @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")
  public ApiResponse<List<Map<String, Object>>> actionExecutions(
      @PathVariable String entityCode, @PathVariable String id) {
    accessGuard.requireRead(entityCode);
    long tenantId = recordService.currentTenantId();
    return ApiResponse.success(actionExecutionRepository.findByRecord(tenantId, entityCode, id));
  }

  @PostMapping
  @PreAuthorize("@runtimeAccessGuard.canWrite(#entityCode)")
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @PathVariable String entityCode,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody Map<String, Object> body)
      throws JsonProcessingException {
    accessGuard.requireWrite(entityCode);
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

    Map<String, Object> created = recordService.create(entityCode, body);
    Object pk = created != null ? created.get("id") : null;
    if (pk == null) {
      pk = recordService.newRecordId();
    }
    ResponseEntity<ApiResponse<Map<String, Object>>> response =
        ResponseEntity.created(
                URI.create("/api/v1/runtime/entities/" + entityCode + "/records/" + pk))
            .body(ApiResponse.success(created));
    catalogIdempotencySupport.rememberApiResponse(
        idempotencyKey, "POST", path, fingerprint, response);
    return response;
  }

  @PutMapping("/{id}")
  @PreAuthorize("@runtimeAccessGuard.canWrite(#entityCode)")
  public ResponseEntity<ApiResponse<Map<String, Object>>> update(
      @PathVariable String entityCode,
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody Map<String, Object> body) {
    accessGuard.requireWrite(entityCode);
    Map<String, Object> updated =
        recordService.update(
            entityCode, id, body, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
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
  @PreAuthorize("@runtimeAccessGuard.canWrite(#entityCode)")
  public ApiResponse<Void> delete(
      @PathVariable String entityCode,
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    accessGuard.requireWrite(entityCode);
    recordService.delete(
        entityCode, id, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    return ApiResponse.success();
  }

  /**
   * 回收站视图（只查 {@code deleted = 1}）。
   *
   * <p>为什么必须补：Runtime §0.3 实测——软删单向不可逆，且列探测失败会静默降级为<strong>物理 DELETE</strong>。
   * 「不可恢复」只能是<strong>明示</strong>，不能是默认行为；否则一次误归档就是永久丢数据。
   */
  @GetMapping("/deleted")
  @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")
  public ApiResponse<PageResult<Map<String, Object>>> trash(
      @PathVariable String entityCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String fields,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q) {
    accessGuard.requireRead(entityCode);
    RuntimePageQuery query = RuntimePageQuery.parse(fields, sort, q);
    return ApiResponse.success(
        CatalogPageMapper.toApiPage(
            recordService.pageDeleted(entityCode, page, size, query), row -> row));
  }

  /** 恢复归档记录（回收站 → 在用）。 */
  @PostMapping("/{id}/restore")
  @PreAuthorize("@runtimeAccessGuard.canWrite(#entityCode)")
  public ApiResponse<Map<String, Object>> restore(
      @PathVariable String entityCode, @PathVariable String id) {
    accessGuard.requireWrite(entityCode);
    return ApiResponse.success(recordService.restore(entityCode, id));
  }
}
