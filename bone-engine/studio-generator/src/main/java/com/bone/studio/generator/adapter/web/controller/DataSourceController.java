package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.command.cmd.*;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCommand;
import com.bone.studio.generator.application.command.handler.CreateDataSourceApplicationService;
import com.bone.studio.generator.application.command.handler.DeleteDataSourceApplicationService;
import com.bone.studio.generator.application.command.handler.SyncTableMetadataApplicationService;
import com.bone.studio.generator.application.command.handler.TestDataSourceConnectionApplicationService;
import com.bone.studio.generator.application.command.handler.UpdateDataSourceApplicationService;
import com.bone.studio.generator.application.query.handler.DataSourceByIdApplicationService;
import com.bone.studio.generator.application.query.handler.GetDataSourceListQueryApplicationService;
import com.bone.studio.generator.application.query.handler.ListSyncedTablesApplicationService;
import com.bone.studio.generator.application.query.handler.LoadTablesApplicationService;
import com.bone.studio.generator.application.query.qry.DataSourceByIdQuery;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQuery;
import com.bone.studio.generator.application.query.qry.ListSyncedTablesQuery;
import com.bone.studio.generator.application.query.qry.LoadTablesQuery;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(GeneratorApiPaths.DATA_SOURCES)
@RequiredArgsConstructor
public class DataSourceController {

  private final CreateDataSourceApplicationService createDataSourceHandler;
  private final UpdateDataSourceApplicationService updateDataSourceHandler;
  private final DeleteDataSourceApplicationService deleteDataSourceHandler;
  private final TestDataSourceConnectionApplicationService testDataSourceConnectionHandler;
  private final GetDataSourceListQueryApplicationService queryHandler;
  private final DataSourceByIdApplicationService dataSourceByIdHandler;
  private final LoadTablesApplicationService loadTablesHandler;
  private final ListSyncedTablesApplicationService listSyncedTablesHandler;
  private final SyncTableMetadataApplicationService syncHandler;

  @PostMapping
  public ApiResponse<String> createDataSource(@RequestBody CreateDataSourceCommand command) {
    // 注意：success(String) 命中 message 重载，字符串数据须用双参形式
    return ApiResponse.success("创建成功", createDataSourceHandler.handle(command));
  }

  @PutMapping("/{id}")
  public ApiResponse<String> updateDataSource(
      @PathVariable String id, @RequestBody UpdateDataSourceCommand command) {
    command =
        UpdateDataSourceCommand.builder()
            .id(id)
            .name(command.getName())
            .type(command.getType())
            .host(command.getHost())
            .port(command.getPort())
            .database(command.getDatabase())
            .username(command.getUsername())
            .password(command.getPassword())
            .build();
    return ApiResponse.success("更新成功", updateDataSourceHandler.handle(command));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Boolean> deleteDataSource(@PathVariable String id) {
    DeleteDataSourceCommand command = DeleteDataSourceCommand.builder().id(id).build();
    deleteDataSourceHandler.handle(command);
    return ApiResponse.success(true);
  }

  /** 物理库表发现（JDBC 元数据），详设 §5.2 */
  @GetMapping("/{id}/tables")
  public ApiResponse<List<DatabaseTable>> listTables(@PathVariable String id) {
    LoadTablesQuery query = LoadTablesQuery.builder().dataSourceId(id).build();
    return ApiResponse.success(loadTablesHandler.handle(query));
  }

  /** 已写入 gen_table_metadata 的表（供生成页勾选） */
  @GetMapping("/{id}/synced-tables")
  public ApiResponse<List<DatabaseTable>> listSyncedTables(@PathVariable String id) {
    ListSyncedTablesQuery qry = ListSyncedTablesQuery.builder().dataSourceId(id).build();
    return ApiResponse.success(listSyncedTablesHandler.handle(qry));
  }

  @PostMapping("/{id}/tables:sync")
  public ApiResponse<Void> syncTables(
      @PathVariable String id, @RequestBody(required = false) SyncTableMetadataCommand body) {
    SyncTableMetadataCommand cmd =
        SyncTableMetadataCommand.builder()
            .dataSourceId(id)
            .tableNames(body != null ? body.getTableNames() : null)
            .build();
    syncHandler.handle(cmd);
    return ApiResponse.success();
  }

  @PostMapping("/{id}:test-connection")
  public ApiResponse<Boolean> testDataSourceConnection(@PathVariable String id) {
    TestDataSourceConnectionCommand command =
        TestDataSourceConnectionCommand.builder().id(id).build();
    return ApiResponse.success(testDataSourceConnectionHandler.handle(command));
  }

  @GetMapping("/{id}")
  public ApiResponse<DataSource> getDataSourceById(@PathVariable String id) {
    DataSourceByIdQuery qry = DataSourceByIdQuery.builder().id(id).build();
    return ApiResponse.success(dataSourceByIdHandler.handle(qry));
  }

  @GetMapping
  public ApiResponse<PageResult<?>> getDataSourceList(
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "10") Integer size,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status) {
    GetDataSourceListQuery qry =
        GetDataSourceListQuery.builder()
            .page(page)
            .size(size)
            .name(name)
            .type(type)
            .status(status)
            .build();
    return ApiResponse.success(queryHandler.handle(qry));
  }
}
