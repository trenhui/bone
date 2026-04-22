package com.bone.blueprint.application.command.handler.dashboard;

import com.bone.blueprint.application.command.cmd.dashboard.CreateWidgetCmd;
import com.bone.blueprint.domain.model.dashboard.Widget;
import com.bone.blueprint.domain.repository.dashboard.WidgetRepository;
import com.bone.blueprint.domain.repository.dashboard.DashboardRepository;
import com.bone.blueprint.adapter.web.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateWidgetHandler {
    private final WidgetRepository widgetRepository;
    private final DashboardRepository dashboardRepository;
    
    @Transactional
    public Long handle(CreateWidgetCmd cmd) {
        // 检查仪表板是否存在
        if (!dashboardRepository.existsById(cmd.getDashboardId())) {
            throw new InvalidRequestException("仪表板不存在");
        }
        
        // 创建组件
        Widget widget = Widget.create(cmd.getDashboardId(), cmd.getTitle(), cmd.getType(), cmd.getConfig(),
                cmd.getPositionX(), cmd.getPositionY(), cmd.getWidth(), cmd.getHeight());
        widgetRepository.save(widget);
        
        return widget.getId();
    }
}
