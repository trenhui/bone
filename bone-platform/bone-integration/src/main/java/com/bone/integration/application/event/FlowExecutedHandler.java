package com.bone.integration.application.event;

import com.bone.integration.domain.model.flow.event.FlowExecutedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlowExecutedHandler {
    public void handle(FlowExecutedEvent event) {
        log.info("Flow executed: id={}, executionId={}, success={}, message={}", 
                event.flowId(), event.executionId(), event.success(), event.message());
        // TODO: 发送通知或执行其他逻辑
    }
}