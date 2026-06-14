package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.metadata.sdk.Repository;

public interface MasterDataRecordRepository extends Repository<MasterDataRecord, Long> {
  // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
