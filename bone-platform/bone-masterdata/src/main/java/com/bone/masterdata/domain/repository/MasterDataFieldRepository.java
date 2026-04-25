package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.metadata.sdk.Repository;

public interface MasterDataFieldRepository extends Repository<MasterDataField, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
