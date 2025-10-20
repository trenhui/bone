package com.bone.procurement.service;

import com.bone.procurement.engine.model.PurchaseOrder;
import com.bone.procurement.engine.model.PurchaseOrderItem;
import com.bone.procurement.engine.model.ApprovalResult;
import com.bone.procurement.engine.model.RuleExecutionContext;
import com.bone.procurement.engine.model.RuleValidationResult;
import java.util.List;
import java.util.Map;

/**
 * 采购订单服务接口
 * 定义采购订单的核心业务操作
 */
public interface PurchaseOrderService {
    
    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @param context 规则执行上下文
     * @return 创建的采购订单
     */
    PurchaseOrder createOrder(PurchaseOrder order, RuleExecutionContext context);
    
    /**
     * 更新采购订单
     * @param orderId 订单ID
     * @param order 更新的订单信息
     * @param context 规则执行上下文
     * @return 更新后的采购订单
     */
    PurchaseOrder updateOrder(String orderId, PurchaseOrder order, RuleExecutionContext context);
    
    /**
     * 查询采购订单
     * @param orderId 订单ID
     * @return 采购订单对象
     */
    PurchaseOrder getOrderById(String orderId);
    
    /**
     * 查询采购订单
     * @param orderCode 订单编号
     * @return 采购订单对象
     */
    PurchaseOrder getOrderByCode(String orderCode);
    
    /**
     * 条件查询采购订单列表
     * @param conditions 查询条件
     * @param page 页码
     * @param pageSize 每页大小
     * @return 采购订单列表
     */
    List<PurchaseOrder> findOrdersByConditions(Map<String, Object> conditions, int page, int pageSize);
    
    /**
     * 提交采购订单审批
     * @param orderId 订单ID
     * @param context 规则执行上下文
     * @return 审批结果
     */
    ApprovalResult submitForApproval(String orderId, RuleExecutionContext context);
    
    /**
     * 审批采购订单
     * @param orderId 订单ID
     * @param action 审批动作：approve/reject
     * @param comment 审批意见
     * @param context 规则执行上下文
     * @return 审批结果
     */
    ApprovalResult approveOrder(String orderId, String action, String comment, RuleExecutionContext context);
    
    /**
     * 取消采购订单
     * @param orderId 订单ID
     * @param reason 取消原因
     * @param context 规则执行上下文
     * @return 更新后的采购订单
     */
    PurchaseOrder cancelOrder(String orderId, String reason, RuleExecutionContext context);
    
    /**
     * 关闭采购订单
     * @param orderId 订单ID
     * @param context 规则执行上下文
     * @return 更新后的采购订单
     */
    PurchaseOrder closeOrder(String orderId, RuleExecutionContext context);
    
    /**
     * 验证采购订单
     * @param order 采购订单对象
     * @param context 规则执行上下文
     * @return 验证结果列表
     */
    List<RuleValidationResult> validateOrder(PurchaseOrder order, RuleExecutionContext context);
    
    /**
     * 获取采购订单统计信息
     * @param conditions 统计条件
     * @return 统计结果
     */
    Map<String, Object> getOrderStatistics(Map<String, Object> conditions);
}