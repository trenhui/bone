package com.bone.studio.generator.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQuery;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetDataSourceListQueryHandler {

  private final DataSourceRepository dataSourceRepository;

  public PageResult<DataSource> handle(GetDataSourceListQuery qry) {
    int pageNo = qry.getPage() != null ? qry.getPage() : 1;
    int pageSize = qry.getSize() != null ? qry.getSize() : 10;
    Criteria<DataSource> criteria = Criteria.<DataSource>create().page(pageNo, pageSize);
    com.bone.core.model.PageResult<DataSource> sdkPage =
        dataSourceRepository.pageByCriteria(criteria);
    long total = sdkPage.getTotal() != null ? sdkPage.getTotal() : 0L;
    return PageResult.of(sdkPage.getRecords(), total, pageNo, pageSize);
  }
}
