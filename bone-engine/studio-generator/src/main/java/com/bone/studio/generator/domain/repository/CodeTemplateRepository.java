package com.bone.studio.generator.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.data.CodeTemplate;

public interface CodeTemplateRepository extends Repository<CodeTemplate, Long> {

  default PageResult<CodeTemplate> findPage(int pageNo, int pageSize) {
    return pageByCriteria(Criteria.<CodeTemplate>create().page(pageNo, pageSize));
  }
}
