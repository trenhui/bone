package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.system.domain.alert.AlertEvent;

public interface AlertEventRepository extends Repository<AlertEvent, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
