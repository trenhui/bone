package com.bone.procurement.common.rules;

import com.bone.procurement.common.model.PurchaseOrder;
import com.bone.procurement.common.model.PurchaseOrderItem;

import java.util.List;
import java.util.Map;

/**
 * 采购业务规则引擎接口
 * 定义采购业务相关的规则执行方法，供所有模块共享使用
 */
public interface ProcurementRuleEngine {
    
    /**
     * 验证采购订单是否符合业务规则
     * @param order 采购订单
     * @param context 上下文参数
     * @return 验证结果列表
     */
    List<RuleValidationResult> validateOrder(PurchaseOrder order, Map<String, Object> context);
    
    /**
     * 验证单个采购订单项
     * @param item 采购订单项
     * @param context 上下文参数
     * @return 验证结果列表
     */
    List<RuleValidationResult> validateOrderItem(PurchaseOrderItem item, Map<String, Object> context);
    
    /**
     * 判断订单是否需要审批
     * @param order 采购订单
     * @param context 上下文参数
     * @return 是否需要审批
     */
    boolean requiresApproval(PurchaseOrder order, Map<String, Object> context);
    
    /**
     * 获取订单的审批流程定义
     * @param order 采购订单
     * @param context 上下文参数
     * @return 审批流程定义ID
     */
    String getApprovalFlowDefinition(PurchaseOrder order, Map<String, Object> context);
    
    /**
     * 获取订单的审批级别
     * @param order 采购订单
     * @param context 上下文参数
     * @return 审批级别
     */
    int getApprovalLevel(PurchaseOrder order, Map<String, Object> context);
    
    /**
     * 获取下一审批人
     * @param order 采购订单
     * @param currentApproverId 当前审批人ID
     * @param context 上下文参数
     * @return 下一审批人ID
     */
    String getNextApprover(PurchaseOrder order, String currentApproverId, Map<String, Object> context);
    
    /**
     * 执行紧急采购规则
     * @param order 采购订单
     * @param context 上下文参数
     * @return 审批结果
     */
    ApprovalResult executeEmergencyPurchaseRule(PurchaseOrder order, Map<String, Object> context);
    
    /**
     * 计算订单金额
     * @param order 采购订单
     * @param context 上下文参数
     */
    void calculateOrderAmounts(PurchaseOrder order, Map<String, Object> context);
    
    /**
     * 计算订单项金额
     * @param item 采购订单项
     * @param context 上下文参数
     */
    void calculateItemAmounts(PurchaseOrderItem item, Map<String, Object> context);
    
    /**
     * 处理订单状态变更
     * @param order 采购订单
     * @param newStatus 新状态
     * @param context 上下文参数
     * @return 处理结果
     */
    StatusChangeResult processStatusChange(PurchaseOrder order, String newStatus, Map<String, Object> context);
}