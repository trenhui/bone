package com.bone.procurement.service.notification;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.User;
import com.bone.procurement.model.NotificationType;

import java.util.List;
import java.util.Map;

/**
 * 通知服务接口
 * 提供订单相关的通知发送功能
 */
public interface NotificationService {
    
    /**
     * 发送审批通知给审批人
     * @param order 采购订单
     * @param approvers 审批人列表
     */
    void sendApprovalNotification(PurchaseOrder order, List<User> approvers);
    
    /**
     * 发送审批结果通知
     * @param order 采购订单
     * @param approved 是否审批通过
     */
    void sendApprovalResultNotification(PurchaseOrder order, boolean approved);
    
    /**
     * 发送订单状态变更通知
     * @param order 采购订单
     * @param oldStatus 旧状态
     * @param newStatus 新状态
     * @param operatorId 操作人ID
     */
    void sendStatusChangeNotification(PurchaseOrder order, String oldStatus, String newStatus, String operatorId);
    
    /**
     * 发送订单提醒通知
     * @param order 采购订单
     * @param reminderType 提醒类型
     * @param recipients 接收人列表
     */
    void sendReminderNotification(PurchaseOrder order, String reminderType, List<User> recipients);
    
    /**
     * 发送批量通知
     * @param notificationType 通知类型
     * @param recipients 接收人列表
     * @param content 通知内容
     * @param metadata 附加元数据
     */
    void sendBatchNotification(NotificationType notificationType, List<User> recipients, 
                              String content, Map<String, Object> metadata);
    
    /**
     * 发送系统异常通知给管理员
     * @param message 异常消息
     * @param errorDetails 错误详情
     * @param affectedOrderId 受影响的订单ID
     */
    void sendSystemErrorNotification(String message, String errorDetails, String affectedOrderId);
    
    /**
     * 创建通知内容模板
     * @param templateName 模板名称
     * @param variables 变量替换映射
     * @return 生成的通知内容
     */
    String createNotificationContent(String templateName, Map<String, Object> variables);
    
    /**
     * 发送自定义通知
     * @param userId 用户ID
     * @param title 通知标题
     * @param content 通知内容
     * @param type 通知类型
     * @param relatedEntityId 相关实体ID
     */
    void sendCustomNotification(String userId, String title, String content, 
                               NotificationType type, String relatedEntityId);
    
    /**
     * 记录通知发送日志
     * @param notificationId 通知ID
     * @param userId 用户ID
     * @param type 通知类型
     * @param status 发送状态
     * @param errorMessage 错误消息（如果有）
     */
    void logNotification(String notificationId, String userId, NotificationType type, 
                        boolean status, String errorMessage);
}