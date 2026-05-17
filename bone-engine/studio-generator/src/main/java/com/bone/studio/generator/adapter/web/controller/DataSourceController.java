package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.command.cmd.*;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQry;
import com.bone.studio.generator.application.query.qry.DataSourceByIdQry;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCmd;
import com.bone.studio.generator.application.query.handler.LoadTablesHandler;
import com.bone.studio.generator.application.query.qry.LoadTablesQry;
import com.bone.studio.generator.application.usecase.GetDataSourceByIdUseCase;
import com.bone.studio.generator.application.usecase.GetDataSourceListUseCase;
import com.bone.studio.generator.application.usecase.SyncTableMetadataUseCase;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.application.usecase.TestDataSourceConnectionUseCase;
import com.bone.studio.generator.application.usecase.standard.CreateDataSourceUseCase;
import com.bone.studio.generator.application.usecase.standard.DeleteDataSourceUseCase;
import com.bone.studio.generator.application.usecase.standard.UpdateDataSourceUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(GeneratorApiPaths.DATA_SOURCES)
@RequiredArgsConstructor
public class DataSourceController {

    private final CreateDataSourceUseCase createDataSourceUseCase;
    private final UpdateDataSourceUseCase updateDataSourceUseCase;
    private final DeleteDataSourceUseCase deleteDataSourceUseCase;
    private final TestDataSourceConnectionUseCase testDataSourceConnectionUseCase;
    private final GetDataSourceListUseCase getDataSourceListUseCase;
    private final GetDataSourceByIdUseCase getDataSourceByIdUseCase;
    private final LoadTablesHandler loadTablesHandler;
    private final SyncTableMetadataUseCase syncTableMetadataUseCase;

    @PostMapping
    public ApiResponse<String> createDataSource(@RequestBody CreateDataSourceCommand command) {
        return ApiResponse.success(createDataSourceUseCase.execute(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<String> updateDataSource(@PathVariable String id, @RequestBody UpdateDataSourceCommand command) {
        command = UpdateDataSourceCommand.builder()
                .id(id)
                .name(command.getName())
                .type(command.getType())
                .host(command.getHost())
                .port(command.getPort())
                .database(command.getDatabase())
                .username(command.getUsername())
                .password(command.getPassword())
                .build();
        return ApiResponse.success(updateDataSourceUseCase.execute(command));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteDataSource(@PathVariable String id) {
        DeleteDataSourceCommand command = DeleteDataSourceCommand.builder()
                .id(id)
                .build();
        deleteDataSourceUseCase.execute(command);
        return ApiResponse.success(true);
    }

    /** 物理库表发现（JDBC 元数据），详设 §5.2 */
    @GetMapping("/{id}/tables")
    public ApiResponse<List<DatabaseTable>> listTables(@PathVariable String id) {
        LoadTablesQry query = LoadTablesQry.builder().dataSourceId(id).build();
        return ApiResponse.success(loadTablesHandler.handle(query));
    }

    @PostMapping("/{id}/tables:sync")
    public ApiResponse<Void> syncTables(@PathVariable String id) {
        SyncTableMetadataCmd cmd = SyncTableMetadataCmd.builder().dataSourceId(id).build();
        syncTableMetadataUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @PostMapping("/{id}:test-connection")
    public ApiResponse<Boolean> testDataSourceConnection(@PathVariable String id) {
        TestDataSourceConnectionCommand command = TestDataSourceConnectionCommand.builder()
                .id(id)
                .build();
        return ApiResponse.success(testDataSourceConnectionUseCase.execute(command));
    }

    @GetMapping("/{id}")
    public ApiResponse<DataSource> getDataSourceById(@PathVariable String id) {
        DataSourceByIdQry qry = DataSourceByIdQry.builder().id(id).build();
        return ApiResponse.success(getDataSourceByIdUseCase.execute(qry));
    }

    @GetMapping
    public ApiResponse<PageResult<?>> getDataSourceList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        GetDataSourceListQry qry = GetDataSourceListQry.builder()
                .page(page)
                .size(size)
                .name(name)
                .type(type)
                .status(status)
                .build();
        return ApiResponse.success(getDataSourceListUseCase.execute(qry));
    }
}
