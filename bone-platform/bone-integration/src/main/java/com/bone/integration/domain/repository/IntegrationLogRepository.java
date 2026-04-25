package com.bone.integration.domain.repository;

import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.metadata.sdk.Repository;

public interface IntegrationLogRepository extends Repository<IntegrationLog, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
