package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.studio.generator.application.command.cmd.GenerateCodeCommand;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCommand;
import com.bone.studio.generator.application.command.handler.GenerateCodeHandler;
import com.bone.studio.generator.application.command.handler.SyncTableMetadataHandler;
import com.bone.studio.generator.application.dto.GeneratorOperationView;
import com.bone.studio.generator.application.query.handler.CodeGenerationHistoryQueryHandler;
import com.bone.studio.generator.application.query.handler.DataSourceTablesHandler;
import com.bone.studio.generator.application.query.handler.GenerationOperationViewQueryHandler;
import com.bone.studio.generator.application.query.qry.CodeGenerationHistoryQuery;
import com.bone.studio.generator.application.query.qry.DataSourceTablesQuery;
import com.bone.studio.generator.application.query.qry.GenerationOperationViewQuery;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.domain.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.history.CodeGenerationHistory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 简写路径控制器：提供 /tables、/generations、/generate、/history 等端点。
 *
 * <p>这些路径是对 data-sources / code-generation / generation-tasks 的简写别名。
 */
@RestController
@RequiredArgsConstructor
public class GeneratorShortcutController {

  private final DataSourceTablesHandler dataSourceTablesHandler;
  private final SyncTableMetadataHandler syncTableMetadataHandler;
  private final GenerateCodeHandler generateCodeHandler;
  private final GenerationOperationViewQueryHandler generationOperationViewQueryHandler;
  private final CodeGenerationHistoryQueryHandler codeGenerationHistoryQueryHandler;

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

  /** POST /api/v1/generator/generations — 创建代码生成任务（异步 LRO） */
  @PostMapping(GeneratorApiPaths.GENERATIONS)
  public ResponseEntity<ApiResponse<?>> createGeneration(@RequestBody GenerateCodeCommand command) {
    CodeGenerationResponse response = generateCodeHandler.handle(command);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /** POST /api/v1/generator/generate — 同步代码生成 */
  @PostMapping(GeneratorApiPaths.GENERATE)
  public ApiResponse<CodeGenerationResponse> generate(@RequestBody GenerateCodeCommand command) {
    return ApiResponse.success(generateCodeHandler.handle(command));
  }

  /** GET /api/v1/generator/generations/{id} — 查询生成状态 */
  @GetMapping(GeneratorApiPaths.GENERATIONS + "/{id}")
  public ApiResponse<GeneratorOperationView> getGeneration(@PathVariable String id) {
    GeneratorOperationView view =
        generationOperationViewQueryHandler.handle(new GenerationOperationViewQuery(id));
    if (view == null) {
      return ApiResponse.success(null);
    }
    return ApiResponse.success(view);
  }

  /** GET /api/v1/generator/history — 查询生成历史 */
  @GetMapping(GeneratorApiPaths.HISTORY)
  public ApiResponse<List<CodeGenerationHistory>> getHistory() {
    List<CodeGenerationHistory> histories =
        codeGenerationHistoryQueryHandler.handle(new CodeGenerationHistoryQuery());
    return ApiResponse.success(histories);
  }
}
