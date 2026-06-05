package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaRelationCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaRelationCommand;
import com.bone.metadata.catalog.application.command.handler.CreateMetaRelationHandler;
import com.bone.metadata.catalog.application.command.handler.DeleteMetaRelationHandler;
import com.bone.metadata.catalog.application.command.handler.UpdateMetaRelationHandler;
import com.bone.metadata.catalog.application.query.dto.MetaRelationDTO;
import com.bone.metadata.catalog.application.query.handler.MetaRelationDetailQueryHandler;
import com.bone.metadata.catalog.application.query.handler.MetaRelationPageQueryHandler;
import com.bone.metadata.catalog.application.query.qry.MetaRelationPageQuery;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 元数据目录：实体关系 API。
 *
 * <p>类名 {@code MetaRelation*}（短称）与 DDL 表 {@code meta_entity_relation} / 领域聚合根 {@code
 * MetaEntityRelation} 对齐；HTTP 路径仍用语义化 {@code relationships}。
 */
@RestController
@RequestMapping("/api/v1/metadata/relationships")
@RequiredArgsConstructor
public class MetaRelationCatalogController {

  private final CreateMetaRelationHandler createMetaRelationHandler;
  private final UpdateMetaRelationHandler updateMetaRelationHandler;
  private final DeleteMetaRelationHandler deleteMetaRelationHandler;
  private final MetaRelationPageQueryHandler metaRelationPageQueryHandler;
  private final MetaRelationDetailQueryHandler metaRelationDetailQueryHandler;

  @PostMapping
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(
      @Valid @RequestBody CreateMetaRelationCommand cmd) {
    Long id = createMetaRelationHandler.handle(cmd);
    return ResponseEntity.created(URI.create("/api/v1/metadata/relationships/" + id))
        .body(ApiResponse.success(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Void>> update(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody UpdateMetaRelationCommand cmd) {
    Integer version =
        updateMetaRelationHandler.handle(
            id, cmd, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(version))
        .body(ApiResponse.success());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<MetaRelationDTO>> page(MetaRelationPageQuery qry) {
    return ApiResponse.success(metaRelationPageQueryHandler.handle(qry));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ResponseEntity<ApiResponse<MetaRelationDTO>> detail(@PathVariable Long id) {
    MetaRelationDTO dto = metaRelationDetailQueryHandler.handle(id);
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(dto.getVersion()))
        .body(ApiResponse.success(dto));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteMetaRelationHandler.handle(id);
    return ApiResponse.success();
  }
}
