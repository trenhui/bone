package com.bone.blueprint.application.command.handler.dashboard;

import com.bone.blueprint.application.command.cmd.dashboard.UpdateWidgetCmd;
import com.bone.blueprint.domain.model.dashboard.Widget;
import com.bone.blueprint.domain.repository.dashboard.WidgetRepository;
import com.bone.blueprint.adapter.web.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateWidgetHandler {
    private final WidgetRepository widgetRepository;
    
    @Transactional
    public void handle(UpdateWidgetCmd cmd) {
        // 获取组件
        Widget widget = widgetRepository.findById(cmd.getId())
                .orElseThrow(() -> new InvalidRequestException("组件不存在"));
        
        // 更新组件
        widget.update(cmd.getTitle(), cmd.getType(), cmd.getConfig(),
                cmd.getPositionX(), cmd.getPositionY(), cmd.getWidth(), cmd.getHeight());
        widgetRepository.save(widget);
    }
}
