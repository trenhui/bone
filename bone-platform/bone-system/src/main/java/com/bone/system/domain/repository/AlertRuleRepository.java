package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.system.domain.model.alert.AlertRule;

import java.util.List;

public interface AlertRuleRepository extends Repository<AlertRule, Long> {
    List<AlertRule> findByEnabled(boolean enabled);
}
