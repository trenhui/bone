package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.handler.CreateMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.DeleteMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.PublishMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.UpdateMetaEntityHandler;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.handler.MetaEntityDetailQueryHandler;
import com.bone.metadata.catalog.application.query.handler.MetaEntityPageQueryHandler;
import com.bone.metadata.catalog.application.query.qry.MetaEntityPageQuery;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 元数据目录：实体建模 API（与扩展字段 /api/v1/metadata/fields:* 分离）。
 *
 * <p>权限 scope：read → {@code metadata:read}，write/publish → {@code metadata:write}（
 * Target 态发布操作可独立为 {@code metadata:publish}，见详设 §5.1）。
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

  @PostMapping
  @PreAuthorize("hasAuthority('metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody CreateMetaEntityCommand cmd) {
    Long id = createMetaEntityHandler.handle(cmd);
    return ResponseEntity.created(URI.create("/api/v1/metadata/entities/" + id))
        .body(ApiResponse.success(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateMetaEntityCommand cmd) {
    updateMetaEntityHandler.handle(id, cmd);
    return ApiResponse.success();
  }

  @GetMapping
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<PageResult<MetaEntityDTO>> page(MetaEntityPageQuery qry) {
    return ApiResponse.success(metaEntityPageQueryHandler.handle(qry));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:read')")
  public ApiResponse<MetaEntityDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(metaEntityDetailQueryHandler.handle(id));
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    publishMetaEntityHandler.handle(id);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('metadata:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteMetaEntityHandler.handle(id);
    return ApiResponse.success();
  }
}
