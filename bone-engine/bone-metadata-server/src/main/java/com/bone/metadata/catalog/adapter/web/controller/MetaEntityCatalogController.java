package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.MetaEntityApplicationService;
import com.bone.metadata.catalog.application.command.cmd.BatchDeleteMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.BatchPublishMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCommand;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.qry.MetaEntityPageQuery;
import com.bone.metadata.catalog.application.support.CatalogIdempotencySupport;
import com.bone.metadata.catalog.common.BatchOperateResult;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 元数据目录：实体建模 API（与扩展字段 /api/v1/metadata/fields:* 分离）。
 *
 * <p>入站边界仅依赖 {@link MetaEntityApplicationService}（ADR-0028）。横切关注点（创建 201+Location、更新 If-Match→412、
 * 发布 Idempotency-Key）保留在控制器层。
 */
@RestController
@RequestMapping("/api/v1/metadata/entities")
@RequiredArgsConstructor
public class MetaEntityCatalogController {

  private final MetaEntityApplicationService metaEntityApplicationService;
  private final CatalogIdempotencySupport catalogIdempotencySupport;
  private final ObjectMapper objectMapper;

  @PostMapping
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody CreateMetaEntityCommand cmd) {
    Long id = metaEntityApplicationService.createEntity(cmd);
    return ResponseEntity.created(URI.create("/api/v1/metadata/entities/" + id))
        .body(ApiResponse.success(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Void>> update(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody UpdateMetaEntityCommand cmd) {
    Integer version =
        metaEntityApplicationService.updateEntity(
            id, cmd, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(version))
        .body(ApiResponse.success());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<MetaEntityDTO>> page(MetaEntityPageQuery qry) {
    return ApiResponse.success(
        metaEntityApplicationService.pageEntities(
            qry.getKeyword(), qry.getStatus(), qry.getPageNum(), qry.getPageSize()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ResponseEntity<ApiResponse<MetaEntityDTO>> detail(@PathVariable Long id) {
    MetaEntityDTO dto = metaEntityApplicationService.getEntity(id);
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(dto.getVersion()))
        .body(ApiResponse.success(dto));
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasAnyAuthority('metadata:publish', 'metadata:write')")
  public ResponseEntity<ApiResponse<Void>> publish(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    String path = "/api/v1/metadata/entities/" + id + "/publish";
    String fingerprint = CatalogIdempotencySupport.fingerprint("");
    var apiVoidType =
        objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, Void.class);
    Optional<ResponseEntity<ApiResponse<Void>>> replay =
        catalogIdempotencySupport.replay(idempotencyKey, "POST", path, fingerprint, apiVoidType);
    if (replay.isPresent()) {
      return replay.get();
    }
    Integer version =
        metaEntityApplicationService.publishEntity(
            id, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    ResponseEntity<ApiResponse<Void>> response =
        ResponseEntity.ok()
            .eTag(CatalogHttpSupport.formatEtag(version))
            .body(ApiResponse.success());
    catalogIdempotencySupport.rememberApiResponse(
        idempotencyKey, "POST", path, fingerprint, response);
    return response;
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    metaEntityApplicationService.deleteEntity(id);
    return ApiResponse.success();
  }

  @PostMapping("/batch-publish")
  @PreAuthorize("hasAnyAuthority('metadata:publish', 'metadata:write')")
  public ApiResponse<BatchOperateResult> batchPublish(
      @Valid @RequestBody BatchPublishMetaEntityCommand cmd) {
    return ApiResponse.success(metaEntityApplicationService.batchPublishEntities(cmd));
  }

  @PostMapping("/batch-delete")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<BatchOperateResult> batchDelete(
      @Valid @RequestBody BatchDeleteMetaEntityCommand cmd) {
    return ApiResponse.success(metaEntityApplicationService.batchDeleteEntities(cmd));
  }
}
