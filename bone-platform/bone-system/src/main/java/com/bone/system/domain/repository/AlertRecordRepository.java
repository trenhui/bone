package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.system.domain.alert.AlertRecord;

public interface AlertRecordRepository extends Repository<AlertRecord, Long> {
  // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
