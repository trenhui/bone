package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.quality.QualityCheck;
import com.bone.metadata.sdk.Repository;

public interface QualityCheckRepository extends Repository<QualityCheck, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
