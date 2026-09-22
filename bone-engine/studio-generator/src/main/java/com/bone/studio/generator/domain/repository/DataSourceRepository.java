package com.bone.studio.generator.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.model.data.DataSource;

public interface DataSourceRepository extends Repository<DataSource, Long> {

  default PageResult<DataSource> findPage(int pageNo, int pageSize) {
    return pageByCriteria(Criteria.<DataSource>create().page(pageNo, pageSize));
  }
}
