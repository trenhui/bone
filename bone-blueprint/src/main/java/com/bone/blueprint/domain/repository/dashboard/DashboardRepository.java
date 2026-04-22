package com.bone.blueprint.domain.repository.dashboard;

import com.bone.blueprint.domain.model.dashboard.Dashboard;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface DashboardRepository extends Repository<Dashboard, Long> {
    List<Dashboard> findByUserId(Long userId);
    boolean existsByNameAndUserId(String name, Long userId);
}
