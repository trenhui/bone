package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.vo.DataQualityRuleId;
import com.bone.metadata.sdk.Repository;

public interface DataQualityRuleRepository extends Repository<DataQualityRule, DataQualityRuleId> {
}