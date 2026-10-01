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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
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

  private final JdbcRuntimeRecordService runtimeRecordService;
  private final CatalogIdempotencySupport catalogIdempotencySupport;
  private final ObjectMapper objectMapper;
  private final TenantProvider tenantProvider;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<PageResult<Map<String, Object>>> page(
      @PathVariable String entityCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String fields,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q) {
    long tenantId = tenantProvider.currentTenantId();
    RuntimePageQuery query = RuntimePageQuery.parse(fields, sort, q);
    var sdkPage = runtimeRecordService.page(entityCode, tenantId, page, size, query);
    return ApiResponse.success(CatalogPageMapper.toApiPage(sdkPage, row -> row));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> get(
      @PathVariable String entityCode, @PathVariable String id) {
    long tenantId = tenantProvider.currentTenantId();
    Map<String, Object> row = runtimeRecordService.getById(entityCode, tenantId, id);
    Object version = row.get("version");
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (version instanceof Number number) {
      builder.eTag(CatalogHttpSupport.formatEtag(number.intValue()));
    }
    return builder.body(ApiResponse.success(row));
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @PathVariable String entityCode,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody Map<String, Object> body)
      throws JsonProcessingException {
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

    long tenantId = tenantProvider.currentTenantId();
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
    long tenantId = tenantProvider.currentTenantId();
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

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Void> delete(@PathVariable String entityCode, @PathVariable String id) {
    long tenantId = tenantProvider.currentTenantId();
    runtimeRecordService.delete(entityCode, tenantId, id);
    return ApiResponse.success();
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
}
