package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.studio.generator.application.CodeGenerationHistoryQueryApplicationService;
import com.bone.studio.generator.application.DataSourceTablesApplicationService;
import com.bone.studio.generator.application.SyncTableMetadataApplicationService;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCommand;
import com.bone.studio.generator.application.query.qry.CodeGenerationHistoryQuery;
import com.bone.studio.generator.application.query.qry.DataSourceTablesQuery;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import com.bone.studio.generator.domain.model.history.CodeGenerationHistory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简写路径控制器：提供 /tables、/tables/metadata、/history 等端点。
 *
 * <p>这些路径是对 data-sources / metadata-entity-snapshots 的简写别名。代码生成入口已统一收敛到 {@code /code-generation}（见
 * {@link CodeGenerationController}），本控制器不再暴露 /generate、 /generations 等重复入口。
 */
@RestController
@RequiredArgsConstructor
public class GeneratorShortcutController {

  private final DataSourceTablesApplicationService dataSourceTablesHandler;
  private final SyncTableMetadataApplicationService syncTableMetadataHandler;
  private final CodeGenerationHistoryQueryApplicationService codeGenerationHistoryQueryHandler;

  /** GET /api/v1/generator/tables?dataSourceId=xxx */
  @GetMapping(GeneratorApiPaths.TABLES)
  public ApiResponse<List<DatabaseTable>> listTables(
      @RequestParam(value = "dataSourceId", required = false) String dataSourceId) {
    DataSourceTablesQuery query =
        DataSourceTablesQuery.builder().dataSourceId(dataSourceId).build();
    return ApiResponse.success(dataSourceTablesHandler.handle(query));
  }

  /** POST /api/v1/generator/tables/metadata — 同步表元数据 */
  @PostMapping(GeneratorApiPaths.TABLES_METADATA)
  public ApiResponse<Void> syncTableMetadata(@RequestBody SyncTableMetadataCommand command) {
    syncTableMetadataHandler.handle(command);
    return ApiResponse.success();
  }

  /** GET /api/v1/generator/history — 查询生成历史 */
  @GetMapping(GeneratorApiPaths.HISTORY)
  public ApiResponse<List<CodeGenerationHistory>> getHistory() {
    List<CodeGenerationHistory> histories =
        codeGenerationHistoryQueryHandler.handle(new CodeGenerationHistoryQuery());
    return ApiResponse.success(histories);
  }
}
