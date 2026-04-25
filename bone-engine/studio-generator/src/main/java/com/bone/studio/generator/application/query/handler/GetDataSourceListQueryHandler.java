package com.bone.studio.generator.application.query.handler;

import com.bone.core.result.PageResult;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQry;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetDataSourceListQueryHandler {

    private final DataSourceRepository dataSourceRepository;

    public PageResult<DataSource> handle(GetDataSourceListQry qry) {
        List<DataSource> dataSources = dataSourceRepository.findByCriteria(null);
        int total = dataSources.size();
        return PageResult.of(dataSources, total, qry.getPage(), qry.getSize());
    }
}
