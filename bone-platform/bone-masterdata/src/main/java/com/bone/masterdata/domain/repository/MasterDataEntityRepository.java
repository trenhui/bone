package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.metadata.sdk.Repository;

public interface MasterDataEntityRepository extends Repository<MasterDataEntity, Long> {
  // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
