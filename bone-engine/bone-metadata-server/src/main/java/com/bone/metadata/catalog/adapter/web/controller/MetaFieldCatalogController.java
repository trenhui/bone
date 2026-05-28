package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaFieldCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaFieldCommand;
import com.bone.metadata.catalog.application.command.handler.CreateMetaFieldHandler;
import com.bone.metadata.catalog.application.command.handler.DeleteMetaFieldHandler;
import com.bone.metadata.catalog.application.command.handler.UpdateMetaFieldHandler;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.handler.MetaFieldDetailQueryHandler;
import com.bone.metadata.catalog.application.query.handler.MetaFieldPageQueryHandler;
import com.bone.metadata.catalog.application.query.qry.MetaFieldPageQuery;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 元数据目录：建模字段 API（嵌套于实体，与 EAV /api/v1/metadata/fields:* 分离）。
 *
 * <p>权限 scope：read → {@code metadata:read}，write → {@code metadata:write}。
 */
@RestController
@RequestMapping("/api/v1/metadata/entities/{entityId}/fields")
@RequiredArgsConstructor
public class MetaFieldCatalogController {

  private final CreateMetaFieldHandler createMetaFieldHandler;
  private final UpdateMetaFieldHandler updateMetaFieldHandler;
  private final DeleteMetaFieldHandler deleteMetaFieldHandler;
  private final MetaFieldPageQueryHandler metaFieldPageQueryHandler;
  private final MetaFieldDetailQueryHandler metaFieldDetailQueryHandler;

  @PostMapping
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(
      @PathVariable Long entityId, @Valid @RequestBody CreateMetaFieldCommand cmd) {
    cmd.setEntityId(entityId);
    Long id = createMetaFieldHandler.handle(cmd);
    return ResponseEntity.created(
            URI.create("/api/v1/metadata/entities/" + entityId + "/fields/" + id))
        .body(ApiResponse.success(id));
  }

  @PutMapping("/{fieldId}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> update(
      @PathVariable Long entityId,
      @PathVariable Long fieldId,
      @Valid @RequestBody UpdateMetaFieldCommand cmd) {
    updateMetaFieldHandler.handle(fieldId, cmd);
    return ApiResponse.success();
  }

  @GetMapping
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<MetaFieldDTO>> page(
      @PathVariable Long entityId, MetaFieldPageQuery qry) {
    qry.setEntityId(entityId);
    return ApiResponse.success(metaFieldPageQueryHandler.handle(qry));
  }

  @GetMapping("/{fieldId}")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<MetaFieldDTO> detail(
      @PathVariable Long entityId, @PathVariable Long fieldId) {
    return ApiResponse.success(metaFieldDetailQueryHandler.handle(entityId, fieldId));
  }

  @DeleteMapping("/{fieldId}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(@PathVariable Long entityId, @PathVariable Long fieldId) {
    deleteMetaFieldHandler.handle(fieldId);
    return ApiResponse.success();
  }
}
