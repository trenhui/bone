package com.bone.studio.generator.application.usecase;

import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.query.handler.GetDataSourceListQueryHandler;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQry;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.application.usecase.UseCaseExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetDataSourceListUseCase implements UseCaseExecutor<GetDataSourceListQry, PageResult<DataSource>> {

    private final GetDataSourceListQueryHandler queryHandler;

    @Override
    public PageResult<DataSource> execute(GetDataSourceListQry qry) {
        return queryHandler.handle(qry);
    }
}
