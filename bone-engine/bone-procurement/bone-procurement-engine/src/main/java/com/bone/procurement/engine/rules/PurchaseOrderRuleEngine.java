package com.bone.procurement.engine.rules;

import com.bone.procurement.engine.model.PurchaseOrder;
import com.bone.procurement.engine.model.ApprovalResult;
import com.bone.procurement.engine.model.RuleExecutionContext;
import java.util.List;

/**
 * 采购订单业务规则引擎接口
 * 负责执行采购订单相关的业务规则验证、审批流程判断等
 */
public interface PurchaseOrderRuleEngine {
    
    /**
     * 验证采购订单是否符合业务规则
     * @param order 采购订单对象
     * @param context 规则执行上下文
     * @return 规则验证结果列表
     */
    List<RuleValidationResult> validateOrder(PurchaseOrder order, RuleExecutionContext context);
    
    /**
     * 判断采购订单是否需要审批
     * @param order 采购订单对象
     * @param context 规则执行上下文
     * @return 是否需要审批
     */
    boolean requiresApproval(PurchaseOrder order, RuleExecutionContext context);
    
    /**
     * 获取采购订单的审批流程定义
     * @param order 采购订单对象
     * @param context 规则执行上下文
     * @return 审批流程定义标识符
     */
    String getApprovalFlowDefinition(PurchaseOrder order, RuleExecutionContext context);
    
    /**
     * 获取审批级别
     * @param order 采购订单对象
     * @param context 规则执行上下文
     * @return 审批级别
     */
    int getApprovalLevel(PurchaseOrder order, RuleExecutionContext context);
    
    /**
     * 获取下一审批人
     * @param order 采购订单对象
     * @param currentApprover 当前审批人
     * @param context 规则执行上下文
     * @return 下一审批人ID
     */
    String getNextApprover(PurchaseOrder order, String currentApprover, RuleExecutionContext context);
    
    /**
     * 执行紧急采购规则
     * @param order 采购订单对象
     * @param context 规则执行上下文
     * @return 审批结果
     */
    ApprovalResult executeEmergencyPurchaseRule(PurchaseOrder order, RuleExecutionContext context);
}