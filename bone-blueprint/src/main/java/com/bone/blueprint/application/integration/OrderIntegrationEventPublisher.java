package com.bone.blueprint.application.integration;

import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 集成事件出站（示范：可替换为 MQ Producer / Outbox 投递）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderIntegrationEventPublisher {

    public void publishOrderPaid(OrderPaidIntegrationEvent event) {
        log.info(
                "发布集成事件 OrderPaidIntegrationEvent: orderId={}, schema={}",
                event.orderId(),
                event.schemaVersion());
    }
}
