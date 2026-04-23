package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单支付成功事件处理器
 * <p>
 * 处理订单支付成功后的一系列业务逻辑，包括：
 * 1. 发送支付成功通知
 * 2. 更新库存
 * 3. 生成物流单
 * 4. 记录交易日志
 * 5. 触发积分计算
 * 6. 处理优惠券使用
 */
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {
    
    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventHandler.class);
    
    private final InventoryGateway inventoryGateway;
    
    /**
     * 处理订单支付成功事件
     * 
     * @param event 订单支付成功事件
     */
    public void handle(OrderPaidEvent event) {
        log.info("订单支付成功: 订单ID = {}", event.getOrderId());
        
        try {
            // 1. 发送支付成功通知（邮件、短信、站内信）
            sendPaymentNotification(event);
            
            // 2. 更新库存
            updateInventory(event);
            
            // 3. 生成物流单
            generateShippingOrder(event);
            
            // 4. 记录交易日志
            recordTransactionLog(event);
            
            // 5. 触发积分计算
            calculatePoints(event);
            
            // 6. 处理优惠券使用
            handleCouponUsage(event);
            
            log.info("订单支付后续处理完成: 订单ID = {}", event.getOrderId());
        } catch (Exception e) {
            log.error("订单支付后续处理失败: 订单ID = {}", event.getOrderId(), e);
            // 这里可以添加重试机制或发送告警
        }
    }
    
    /**
     * 发送支付成功通知
     * 
     * @param event 订单支付成功事件
     */
    private void sendPaymentNotification(OrderPaidEvent event) {
        // 实现发送邮件、短信、站内信等通知逻辑
        log.info("发送支付成功通知: 订单ID = {}", event.getOrderId());
    }
    
    /**
     * 更新库存
     * 
     * @param event 订单支付成功事件
     */
    private void updateInventory(OrderPaidEvent event) {
        // 通过InventoryGateway扣减库存
        Order order = event.getOrder();
        for (OrderItem item : order.getItems()) {
            inventoryGateway.deductStock(item.getProductId(), item.getQuantity());
        }
        log.info("更新库存完成: 订单ID = {}", event.getOrderId());
    }
    
    /**
     * 生成物流单
     * 
     * @param event 订单支付成功事件
     */
    private void generateShippingOrder(OrderPaidEvent event) {
        // 实现生成物流单的逻辑
        log.info("生成物流单: 订单ID = {}", event.getOrderId());
    }
    
    /**
     * 记录交易日志
     * 
     * @param event 订单支付成功事件
     */
    private void recordTransactionLog(OrderPaidEvent event) {
        // 实现记录交易日志的逻辑
        log.info("记录交易日志: 订单ID = {}", event.getOrderId());
    }
    
    /**
     * 触发积分计算
     * 
     * @param event 订单支付成功事件
     */
    private void calculatePoints(OrderPaidEvent event) {
        // 实现积分计算的逻辑
        log.info("触发积分计算: 订单ID = {}", event.getOrderId());
    }
    
    /**
     * 处理优惠券使用
     * 
     * @param event 订单支付成功事件
     */
    private void handleCouponUsage(OrderPaidEvent event) {
        // 实现优惠券使用的逻辑
        log.info("处理优惠券使用: 订单ID = {}", event.getOrderId());
    }
}