package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.exception.BusinessException;

import java.util.List;

/**
 * 采购订单服务接口
 * 定义采购订单相关的业务操作
 */
public interface PurchaseOrderService {

    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @return 创建后的采购订单
     * @throws BusinessException 业务异常
     */
    PurchaseOrder createOrder(PurchaseOrder order) throws BusinessException;

    /**
     * 根据ID获取订单信息
     * @param orderId 订单ID
     * @return 订单对象
     * @throws BusinessException 订单不存在时抛出
     */
    PurchaseOrder getOrder(Long orderId) throws BusinessException;

    /**
     * 更新订单信息
     * @param order 待更新的订单
     * @return 更新后的订单
     * @throws BusinessException 业务异常
     */
    PurchaseOrder updateOrder(PurchaseOrder order) throws BusinessException;

    /**
     * 提交订单审批
     * @param orderId 订单ID
     * @param submitterId 提交人ID
     * @return 更新后的采购订单
     * @throws BusinessException 业务异常
     */
    PurchaseOrder submitForApproval(Long orderId, String submitterId) throws BusinessException;

    /**
     * 审批订单
     * @param orderId 订单ID
     * @param approverId 审批人ID
     * @param comment 审批意见
     * @return 审批后的订单
     * @throws BusinessException 业务异常
     */
    PurchaseOrder approveOrder(Long orderId, String approverId, String comment) throws BusinessException;

    /**
     * 拒绝订单
     * @param orderId 订单ID
     * @param approverId 审批人ID
     * @param comment 拒绝原因
     * @return 拒绝后的订单
     * @throws BusinessException 业务异常
     */
    PurchaseOrder rejectOrder(Long orderId, String approverId, String comment) throws BusinessException;

    /**
     * 取消订单
     * @param orderId 订单ID
     * @param cancellerId 取消人ID
     * @param reason 取消原因
     * @return 更新后的采购订单
     * @throws BusinessException 业务异常
     */
    PurchaseOrder cancelOrder(Long orderId, String cancellerId, String reason) throws BusinessException;

    /**
     * 执行订单（正式下单）
     * @param orderId 订单ID
     * @return 执行后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    PurchaseOrder executeOrder(Long orderId) throws BusinessException;

    /**
     * 根据状态查询订单
     * @param status 订单状态
     * @return 订单列表
     */
    List<PurchaseOrder> findOrdersByStatus(String status);

    /**
     * 查询所有订单（带分页）
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 订单列表
     */
    List<PurchaseOrder> findAllOrders(int page, int size);
}