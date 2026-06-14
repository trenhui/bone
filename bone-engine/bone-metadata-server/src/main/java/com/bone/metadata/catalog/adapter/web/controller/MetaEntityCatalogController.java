package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.handler.CreateMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.DeleteMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.PublishMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.UpdateMetaEntityHandler;
import com.bone.metadata.catalog.application.idempotency.CatalogIdempotencyService;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.handler.MetaEntityDetailQueryHandler;
import com.bone.metadata.catalog.application.query.handler.MetaEntityPageQueryHandler;
import com.bone.metadata.catalog.application.query.qry.MetaEntityPageQuery;
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
 * <p>权限 scope：read → {@code metadata:read}，write/publish → {@code metadata:write}（ Target 态发布操作可独立为
 * {@code metadata:publish}，见详设 §5.1）。
 *
 * <p>横切：创建 {@code 201+Location}；更新 {@code If-Match → 412}；发布 {@code Idempotency-Key}。
 */
@RestController
@RequestMapping("/api/v1/metadata/entities")
@RequiredArgsConstructor
public class MetaEntityCatalogController {

  private final CreateMetaEntityHandler createMetaEntityHandler;
  private final UpdateMetaEntityHandler updateMetaEntityHandler;
  private final DeleteMetaEntityHandler deleteMetaEntityHandler;
  private final PublishMetaEntityHandler publishMetaEntityHandler;
  private final MetaEntityPageQueryHandler metaEntityPageQueryHandler;
  private final MetaEntityDetailQueryHandler metaEntityDetailQueryHandler;
  private final CatalogIdempotencyService catalogIdempotencyService;
  private final ObjectMapper objectMapper;

  @PostMapping
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody CreateMetaEntityCommand cmd) {
    Long id = createMetaEntityHandler.handle(cmd);
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
        updateMetaEntityHandler.handle(
            id, cmd, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(version))
        .body(ApiResponse.success());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<MetaEntityDTO>> page(MetaEntityPageQuery qry) {
    return ApiResponse.success(metaEntityPageQueryHandler.handle(qry));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ResponseEntity<ApiResponse<MetaEntityDTO>> detail(@PathVariable Long id) {
    MetaEntityDTO dto = metaEntityDetailQueryHandler.handle(id);
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
    String fingerprint = CatalogIdempotencyService.fingerprint("");
    var apiVoidType =
        objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, Void.class);
    Optional<ResponseEntity<ApiResponse<Void>>> replay =
        catalogIdempotencyService.replay(idempotencyKey, "POST", path, fingerprint, apiVoidType);
    if (replay.isPresent()) {
      return replay.get();
    }
    Integer version =
        publishMetaEntityHandler.handle(
            id, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    ResponseEntity<ApiResponse<Void>> response =
        ResponseEntity.ok()
            .eTag(CatalogHttpSupport.formatEtag(version))
            .body(ApiResponse.success());
    catalogIdempotencyService.rememberApiResponse(
        idempotencyKey, "POST", path, fingerprint, response);
    return response;
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteMetaEntityHandler.handle(id);
    return ApiResponse.success();
  }
}
