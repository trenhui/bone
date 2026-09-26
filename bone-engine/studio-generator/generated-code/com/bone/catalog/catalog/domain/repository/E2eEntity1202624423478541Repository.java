package com.bone.catalog.catalog.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.catalog.catalog.domain.model.e2eentity1202624423478541.E2eEntity1202624423478541;

/**
 * E2E实体 (物理表: meta_e2e_1202624423590500)仓储。
 *
 * <p>由代码生成器基于表 e2e_entity_1202624423478541 生成。本聚合分页留在域仓储，不另建 QueryPort。
 */
public interface E2eEntity1202624423478541Repository extends Repository<E2eEntity1202624423478541, Long> {

  default PageResult<E2eEntity1202624423478541> findPage(int pageNum, int pageSize) {
    return pageByCriteria(
        Criteria.<E2eEntity1202624423478541>create()
            .orderByDesc(E2eEntity1202624423478541::getId)
            .page(pageNum, pageSize));
  }
}
