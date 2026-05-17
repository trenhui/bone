package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.query.handler.LoadCatalogTablesHandler;
import com.bone.studio.generator.application.query.qry.LoadCatalogTablesQry;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.domain.data.DatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代码生成只读：已发布 meta_* 快照（status=1）。
 *
 * <p>建模 CRUD 见 metadata-server {@code /api/v1/metadata/entities}；本接口仅服务 generator 域选型。
 */
@RestController
@RequestMapping(GeneratorApiPaths.METADATA_ENTITY_SNAPSHOTS)
@RequiredArgsConstructor
public class MetadataEntitySnapshotController {

  private final LoadCatalogTablesHandler loadCatalogTablesHandler;

  @GetMapping
  public ApiResponse<PageResult<DatabaseTable>> list(LoadCatalogTablesQry qry) {
    return ApiResponse.success(loadCatalogTablesHandler.handle(qry));
  }
}
