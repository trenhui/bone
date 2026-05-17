package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaFieldCmd;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaFieldCmd;
import com.bone.metadata.catalog.application.command.handler.CreateMetaFieldHandler;
import com.bone.metadata.catalog.application.command.handler.DeleteMetaFieldHandler;
import com.bone.metadata.catalog.application.command.handler.UpdateMetaFieldHandler;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.handler.MetaFieldDetailQueryHandler;
import com.bone.metadata.catalog.application.query.handler.MetaFieldPageQueryHandler;
import com.bone.metadata.catalog.application.query.qry.MetaFieldPageQry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 元数据目录：建模字段 API（嵌套于实体，与 EAV /v1/metadata/fields:* 分离） */
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
  public ApiResponse<Long> create(
      @PathVariable Long entityId, @Valid @RequestBody CreateMetaFieldCmd cmd) {
    cmd.setEntityId(entityId);
    return ApiResponse.success(createMetaFieldHandler.handle(cmd));
  }

  @PutMapping("/{fieldId}")
  public ApiResponse<Void> update(
      @PathVariable Long entityId,
      @PathVariable Long fieldId,
      @Valid @RequestBody UpdateMetaFieldCmd cmd) {
    updateMetaFieldHandler.handle(fieldId, cmd);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<PageResult<MetaFieldDTO>> page(
      @PathVariable Long entityId, MetaFieldPageQry qry) {
    qry.setEntityId(entityId);
    return ApiResponse.success(metaFieldPageQueryHandler.handle(qry));
  }

  @GetMapping("/{fieldId}")
  public ApiResponse<MetaFieldDTO> detail(
      @PathVariable Long entityId, @PathVariable Long fieldId) {
    return ApiResponse.success(metaFieldDetailQueryHandler.handle(entityId, fieldId));
  }

  @DeleteMapping("/{fieldId}")
  public ApiResponse<Void> delete(@PathVariable Long entityId, @PathVariable Long fieldId) {
    deleteMetaFieldHandler.handle(fieldId);
    return ApiResponse.success();
  }
}
