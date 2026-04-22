package com.bone.blueprint.domain.repository.dashboard;

import com.bone.blueprint.domain.model.dashboard.Widget;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface WidgetRepository extends Repository<Widget, Long> {
    List<Widget> findByDashboardId(Long dashboardId);
}
