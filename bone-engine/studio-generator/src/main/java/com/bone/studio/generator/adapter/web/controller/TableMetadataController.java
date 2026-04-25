package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCmd;
import com.bone.studio.generator.application.query.qry.DataSourceTablesQry;
import com.bone.studio.generator.application.usecase.GetDataSourceTablesUseCase;
import com.bone.studio.generator.application.usecase.SyncTableMetadataUseCase;
import com.bone.studio.generator.domain.data.DatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/table-metadata")
@RequiredArgsConstructor
public class TableMetadataController {

    private final SyncTableMetadataUseCase syncTableMetadataUseCase;
    private final GetDataSourceTablesUseCase getDataSourceTablesUseCase;

    @PostMapping("/sync")
    public ApiResponse<Void> syncTableMetadata(@RequestBody SyncTableMetadataCmd cmd) {
        syncTableMetadataUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @GetMapping("/data-source/{dataSourceId}")
    public ApiResponse<List<DatabaseTable>> getDataSourceTables(@PathVariable String dataSourceId) {
        DataSourceTablesQry query = DataSourceTablesQry.builder()
                .dataSourceId(dataSourceId)
                .build();
        return ApiResponse.success(getDataSourceTablesUseCase.execute(query));
    }
}
