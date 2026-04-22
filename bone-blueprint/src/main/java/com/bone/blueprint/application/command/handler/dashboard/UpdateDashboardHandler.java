package com.bone.blueprint.application.command.handler.dashboard;

import com.bone.blueprint.application.command.cmd.dashboard.UpdateDashboardCmd;
import com.bone.blueprint.domain.model.dashboard.Dashboard;
import com.bone.blueprint.domain.repository.dashboard.DashboardRepository;
import com.bone.blueprint.adapter.web.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateDashboardHandler {
    private final DashboardRepository dashboardRepository;
    
    @Transactional
    public void handle(UpdateDashboardCmd cmd) {
        // 获取仪表板
        Dashboard dashboard = dashboardRepository.findById(cmd.getId())
                .orElseThrow(() -> new InvalidRequestException("仪表板不存在"));
        
        // 检查名称是否已存在（如果名称有变更）
        if (!dashboard.getName().equals(cmd.getName()) && 
            dashboardRepository.existsByNameAndUserId(cmd.getName(), dashboard.getUserId())) {
            throw new InvalidRequestException("仪表板名称已存在");
        }
        
        // 更新仪表板
        dashboard.update(cmd.getName(), cmd.getDescription(), cmd.isPublic());
        dashboardRepository.save(dashboard);
    }
}
