package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.query.qry.DataSourceTablesQry;
import com.bone.studio.generator.application.query.handler.DataSourceTablesHandler;
import com.bone.studio.generator.domain.data.DatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@UseCase(name = "GetDataSourceTables", description = "获取数据源表列表", transactional = false)
@Service
@RequiredArgsConstructor
public class GetDataSourceTablesUseCase implements UseCaseExecutor<DataSourceTablesQry, List<DatabaseTable>> {

    private final DataSourceTablesHandler dataSourceTablesHandler;

    @Override
    public List<DatabaseTable> execute(DataSourceTablesQry query) {
        return dataSourceTablesHandler.handle(query);
    }
}
