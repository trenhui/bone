package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.system.domain.config.SystemConfig;

public interface SystemConfigRepository extends Repository<SystemConfig, Long> {
  // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
