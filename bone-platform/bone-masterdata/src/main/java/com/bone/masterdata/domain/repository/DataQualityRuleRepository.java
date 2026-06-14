package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.metadata.sdk.Repository;

public interface DataQualityRuleRepository extends Repository<DataQualityRule, Long> {
  // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
