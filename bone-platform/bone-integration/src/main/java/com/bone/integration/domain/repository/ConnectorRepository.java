package com.bone.integration.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.model.connector.vo.ConnectorStatus;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

public interface ConnectorRepository extends Repository<Connector, Long> {

  /** 本聚合分页。关键字走 Criteria 全模糊。 */
  default PageResult<Connector> findPage(
      String keyword, ConnectorType type, ConnectorStatus status, int pageNum, int pageSize) {
    Criteria<Connector> criteria =
        Criteria.<Connector>create()
            .like(keyword != null && !keyword.isBlank(), Connector::getName, keyword)
            .eq(type != null, Connector::getType, type)
            .eq(status != null, Connector::getStatus, status)
            .orderByDesc(Connector::getId)
            .page(pageNum, pageSize);
    return pageByCriteria(criteria);
  }

  default Connector findByName(String name) {
    return findOneByCriteria(Criteria.<Connector>create().eq(Connector::getName, name));
  }
}
