package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 通知服务类 - 负责处理采购订单相关的通知业务逻辑
 */
@Service
public class NotificationService {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    /**
     * 发送订单创建通知
     * @param order 采购订单实体
     */
    public void sendOrderCreatedNotification(PurchaseOrder order) {
        if (order != null) {
            logger.info("发送订单创建通知：订单编号={}", order.getOrderCode());
            // 在实际项目中，这里会集成邮件、短信或站内信系统
        }
    }

    /**
     * 发送订单审批通知
     * @param order 采购订单实体
     */
    public void sendOrderApprovedNotification(PurchaseOrder order) {
        if (order != null) {
            logger.info("发送订单审批通知：订单编号={}", order.getOrderCode());
            // 在实际项目中，这里会集成邮件、短信或站内信系统
        }
    }

    /**
     * 发送订单拒绝通知
     * @param order 采购订单实体
     */
    public void sendOrderRejectedNotification(PurchaseOrder order) {
        if (order != null) {
            logger.info("发送订单拒绝通知：订单编号={}", order.getOrderCode());
            // 在实际项目中，这里会集成邮件、短信或站内信系统
        }
    }

    /**
     * 发送订单完成通知
     * @param order 采购订单实体
     */
    public void sendOrderCompletedNotification(PurchaseOrder order) {
        if (order != null) {
            logger.info("发送订单完成通知：订单编号={}", order.getOrderCode());
            // 在实际项目中，这里会集成邮件、短信或站内信系统
        }
    }
}