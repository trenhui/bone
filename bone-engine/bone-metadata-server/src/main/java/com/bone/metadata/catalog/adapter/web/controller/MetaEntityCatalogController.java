package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCmd;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCmd;
import com.bone.metadata.catalog.application.command.handler.CreateMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.DeleteMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.PublishMetaEntityHandler;
import com.bone.metadata.catalog.application.command.handler.UpdateMetaEntityHandler;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.handler.MetaEntityDetailQueryHandler;
import com.bone.metadata.catalog.application.query.handler.MetaEntityPageQueryHandler;
import com.bone.metadata.catalog.application.query.qry.MetaEntityPageQry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 元数据目录：实体建模 API（与扩展字段 /v1/metadata/fields:* 分离） */
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
  public ApiResponse<Long> create(@Valid @RequestBody CreateMetaEntityCmd cmd) {
    return ApiResponse.success(createMetaEntityHandler.handle(cmd));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateMetaEntityCmd cmd) {
    updateMetaEntityHandler.handle(id, cmd);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<PageResult<MetaEntityDTO>> page(MetaEntityPageQry qry) {
    return ApiResponse.success(metaEntityPageQueryHandler.handle(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<MetaEntityDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(metaEntityDetailQueryHandler.handle(id));
  }

  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    publishMetaEntityHandler.handle(id);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteMetaEntityHandler.handle(id);
    return ApiResponse.success();
  }
}
