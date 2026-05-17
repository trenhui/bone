package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.query.handler.DataSourceByIdHandler;
import com.bone.studio.generator.application.query.qry.DataSourceByIdQry;
import com.bone.studio.generator.domain.data.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "GetDataSourceById", description = "根据ID获取数据源", transactional = false)
@Service
@RequiredArgsConstructor
public class GetDataSourceByIdUseCase implements UseCaseExecutor<DataSourceByIdQry, DataSource> {

    private final DataSourceByIdHandler dataSourceByIdHandler;

    @Override
    public DataSource execute(DataSourceByIdQry qry) {
        return dataSourceByIdHandler.handle(qry);
    }
}
