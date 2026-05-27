package com.bone.blueprint.adapter.mq.listener;

import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.command.handler.PayOrderCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 入站 MQ 示范：外部系统以「订单 ID」触发支付（与 Outbox 发出的集成事件不同）。
 * <p>
 * 生产环境消费 {@link OrderPaidIntegrationEvent} 请使用 {@link OrderPaidIntegrationMqListener}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidListener {
    
    private final PayOrderCommandHandler payOrderCommandHandler;
    
    /**
     * 处理订单支付消息
     * 
     * @param message 消息内容，包含订单ID
     */
    public void onOrderPaid(String message) {
        try {
            log.info("收到订单支付消息: {}", message);
            
            // 解析消息，提取订单ID
            Long orderId = Long.parseLong(message);
            
            log.info("处理订单支付: orderId={}", orderId);
            
            PayOrderCommand command = new PayOrderCommand();
            command.setOrderId(orderId);
            
            payOrderCommandHandler.handle(command);
            
            log.info("订单支付处理成功: orderId={}", orderId);
        } catch (NumberFormatException e) {
            log.error("消息格式错误，无法解析订单ID: {}", message, e);
        } catch (Exception e) {
            log.error("处理订单支付消息失败: {}", message, e);
        }
    }
}
