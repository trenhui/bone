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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 元数据目录：实体关系 API */
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
  public ApiResponse<Long> create(@Valid @RequestBody CreateMetaRelationCommand cmd) {
    return ApiResponse.success(createMetaRelationHandler.handle(cmd));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateMetaRelationCommand cmd) {
    updateMetaRelationHandler.handle(id, cmd);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<PageResult<MetaRelationDTO>> page(MetaRelationPageQuery qry) {
    return ApiResponse.success(metaRelationPageQueryHandler.handle(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<MetaRelationDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(metaRelationDetailQueryHandler.handle(id));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteMetaRelationHandler.handle(id);
    return ApiResponse.success();
  }
}
