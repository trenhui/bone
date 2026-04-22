package com.bone.integration.application.event;

import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlowActivatedHandler {
    public void handle(FlowActivatedEvent event) {
        log.info("Flow activated: id={}, name={}", event.flowId(), event.name());
        // TODO: 发送通知或执行其他逻辑
    }
}