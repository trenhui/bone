package com.bone.metadata.runtime.adapter.web;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.common.CatalogTenantSupport;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模式 B：已发布 RUNTIME 实体的动态 CRUD（META-002B）。
 *
 * <p>权限 scope：read → {@code metadata:read}，write → {@code metadata:write}。
 */
@RestController
@RequestMapping("/api/v1/runtime/entities/{entityCode}/records")
@RequiredArgsConstructor
public class RuntimeRecordController {

  private final JdbcRuntimeRecordService runtimeRecordService;

  @GetMapping
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<Map<String, Object>>> page(
      @PathVariable String entityCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    var sdkPage = runtimeRecordService.page(entityCode, tenantId, page, size);
    return ApiResponse.success(CatalogPageMapper.toApiPage(sdkPage, row -> row));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<Map<String, Object>> get(
      @PathVariable String entityCode, @PathVariable String id) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    return ApiResponse.success(runtimeRecordService.getById(entityCode, tenantId, id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @PathVariable String entityCode, @RequestBody Map<String, Object> body) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    long newId = DistributedIdGenerator.generateLongId();
    Map<String, Object> created =
        runtimeRecordService.create(entityCode, tenantId, body, newId);
    Object pk = created != null ? created.getOrDefault("id", newId) : newId;
    return ResponseEntity.created(
            URI.create("/api/v1/runtime/entities/" + entityCode + "/records/" + pk))
        .body(ApiResponse.success(created));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable String entityCode,
      @PathVariable String id,
      @RequestBody Map<String, Object> body) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    return ApiResponse.success(runtimeRecordService.update(entityCode, tenantId, id, body));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(@PathVariable String entityCode, @PathVariable String id) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    runtimeRecordService.delete(entityCode, tenantId, id);
    return ApiResponse.success();
  }
}
