package com.bone.blueprint.application.command.handler.dashboard;

import com.bone.blueprint.application.command.cmd.dashboard.CreateDashboardCmd;
import com.bone.blueprint.domain.model.dashboard.Dashboard;
import com.bone.blueprint.domain.repository.dashboard.DashboardRepository;
import com.bone.blueprint.adapter.web.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateDashboardHandler {
    private final DashboardRepository dashboardRepository;
    
    @Transactional
    public Long handle(CreateDashboardCmd cmd) {
        // 检查仪表板名称是否已存在
        if (dashboardRepository.existsByNameAndUserId(cmd.getName(), cmd.getUserId())) {
            throw new InvalidRequestException("仪表板名称已存在");
        }
        
        // 创建仪表板
        Dashboard dashboard = Dashboard.create(cmd.getName(), cmd.getDescription(), cmd.getUserId(), cmd.isPublic());
        dashboardRepository.save(dashboard);
        
        return dashboard.getId();
    }
}
