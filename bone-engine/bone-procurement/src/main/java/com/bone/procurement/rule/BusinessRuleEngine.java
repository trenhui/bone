/**
 * 业务规则引擎接口
 * 提供采购订单相关的业务规则验证和计算功能
 */
package com.bone.procurement.rule;

import com.bone.procurement.model.PurchaseOrder;
import com.bone.procurement.model.PurchaseOrderItem;
import com.bone.procurement.exception.BusinessException;
import java.util.List;
import java.math.BigDecimal;

public interface BusinessRuleEngine {
    
    /**
     * 验证订单项列表
     * @param items 订单项列表
     * @throws BusinessException 验证失败时抛出异常
     */
    void validateOrderItems(List<PurchaseOrderItem> items) throws BusinessException;
    
    /**
     * 计算订单字段（金额、税额等）
     * @param order 订单对象
     */
    void calculateOrderFields(PurchaseOrder order);
    
    /**
     * 验证订单金额是否需要多级审批
     * @param order 订单对象
     * @return 是否需要多级审批
     */
    boolean requiresMultiLevelApproval(PurchaseOrder order);
    
    /**
     * 获取订单的审批级别
     * @param order 订单对象
     * @return 审批级别
     */
    int getApprovalLevel(PurchaseOrder order);
    
    /**
     * 验证审批人权限
     * @param approverId 审批人ID
     * @param approvalLevel 审批级别
     * @param orderAmount 订单金额
     * @return 是否有权限审批
     */
    boolean hasApprovalPermission(String approverId, int approvalLevel, BigDecimal orderAmount);
    
    /**
     * 验证订单是否可以被取消
     * @param order 订单对象
     * @return 是否可以取消
     */
    boolean canCancelOrder(PurchaseOrder order);
    
    /**
     * 验证订单是否可以被修改
     * @param order 订单对象
     * @return 是否可以修改
     */
    boolean canModifyOrder(PurchaseOrder order);
    
    /**
     * 获取订单状态流转规则
     * @param currentStatus 当前状态
     * @return 可以流转到的目标状态列表
     */
    List<String> getValidStateTransitions(String currentStatus);
}