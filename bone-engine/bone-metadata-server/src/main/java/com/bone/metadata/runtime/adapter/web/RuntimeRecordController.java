package com.bone.metadata.runtime.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.application.idempotency.CatalogIdempotencyService;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.common.CatalogTenantSupport;
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
  private final CatalogIdempotencyService catalogIdempotencyService;
  private final ObjectMapper objectMapper;

  @GetMapping
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<Map<String, Object>>> page(
      @PathVariable String entityCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String fields,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    RuntimePageQuery query = RuntimePageQuery.parse(fields, sort, q);
    var sdkPage = runtimeRecordService.page(entityCode, tenantId, page, size, query);
    return ApiResponse.success(CatalogPageMapper.toApiPage(sdkPage, row -> row));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> get(
      @PathVariable String entityCode, @PathVariable String id) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    Map<String, Object> row = runtimeRecordService.getById(entityCode, tenantId, id);
    Object version = row.get("version");
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (version instanceof Number number) {
      builder.eTag(CatalogHttpSupport.formatEtag(number.intValue()));
    }
    return builder.body(ApiResponse.success(row));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @PathVariable String entityCode,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody Map<String, Object> body)
      throws JsonProcessingException {
    String path = "/api/v1/runtime/entities/" + entityCode + "/records";
    String fingerprint =
        CatalogIdempotencyService.fingerprint(objectMapper.writeValueAsString(body));
    var apiType =
        objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, Map.class);
    Optional<ResponseEntity<ApiResponse<Map<String, Object>>>> replay =
        catalogIdempotencyService.replay(idempotencyKey, "POST", path, fingerprint, apiType);
    if (replay.isPresent()) {
      return replay.get();
    }

    long tenantId = CatalogTenantSupport.currentTenantId();
    long newId = DistributedIdGenerator.generateLongId();
    Map<String, Object> created = runtimeRecordService.create(entityCode, tenantId, body, newId);
    Object pk = created != null ? created.getOrDefault("id", newId) : newId;
    ResponseEntity<ApiResponse<Map<String, Object>>> response =
        ResponseEntity.created(
                URI.create("/api/v1/runtime/entities/" + entityCode + "/records/" + pk))
            .body(ApiResponse.success(created));
    catalogIdempotencyService.rememberApiResponse(
        idempotencyKey, "POST", path, fingerprint, response);
    return response;
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> update(
      @PathVariable String entityCode,
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody Map<String, Object> body) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    Map<String, Object> updated =
        runtimeRecordService.update(
            entityCode,
            tenantId,
            id,
            body,
            CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    Object version = updated.get("version");
    if (version instanceof Number number) {
      builder.eTag(CatalogHttpSupport.formatEtag(number.intValue()));
    }
    return builder.body(ApiResponse.success(updated));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(@PathVariable String entityCode, @PathVariable String id) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    runtimeRecordService.delete(entityCode, tenantId, id);
    return ApiResponse.success();
  }
}
