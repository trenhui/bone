package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 通知服务
 */
@Service
public class NotificationService {
    // Logger instance
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    /**
     * 发送采购订单创建通知
     */
    public void sendPurchaseOrderCreatedNotification(PurchaseOrder order) {
        log.info("发送采购订单创建通知: {}", order.getOrderNumber());
    }
    
    /**
     * 发送采购订单提交通知
     */
    public void sendPurchaseOrderSubmittedNotification(PurchaseOrder order) {
        log.info("发送采购订单提交通知: {}", order.getOrderNumber());
    }
    
    /**
     * 发送采购订单批准通知
     */
    public void sendPurchaseOrderApprovedNotification(PurchaseOrder order) {
        log.info("发送采购订单批准通知: {}", order.getOrderNumber());
    }
    
    /**
     * 发送采购订单拒绝通知
     */
    public void sendPurchaseOrderRejectedNotification(PurchaseOrder order) {
        log.info("发送采购订单拒绝通知: {}", order.getOrderNumber());
    }
}