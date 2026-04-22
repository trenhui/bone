package com.bone.blueprint.domain.repository.dashboard;

import com.bone.blueprint.domain.model.dashboard.QuickAccess;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface QuickAccessRepository extends Repository<QuickAccess, Long> {
    List<QuickAccess> findByUserIdOrderByOrderAsc(Long userId);
}
