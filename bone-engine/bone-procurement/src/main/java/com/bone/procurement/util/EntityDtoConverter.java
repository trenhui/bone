package com.bone.procurement.util;

import com.bone.procurement.dto.PurchaseOrderRequest;
import com.bone.procurement.dto.PurchaseOrderResponse;
import com.bone.procurement.entity.PurchaseOrder;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 实体和DTO转换工具类
 */
@Component
public class EntityDtoConverter {
    
    /**
     * 将请求DTO转换为实体
     * @param request 采购订单请求DTO
     * @return 采购订单实体
     */
    public PurchaseOrder convertToEntity(PurchaseOrderRequest request) {
        if (request == null) {
            return null;
        }
        
        PurchaseOrder order = new PurchaseOrder();
        
        // 设置基本信息
        if (request.getId() != null) {
            order.setId(Long.valueOf(request.getId()));
        }
        order.setOrderNumber(request.getOrderNumber());
        order.setSupplierId(request.getSupplierId());
        order.setDepartment(request.getDepartment());
        order.setCreator(request.getCreator());
        order.setCreateTime(request.getCreateTime());
        order.setModifier(request.getModifier());
        order.setModifyTime(request.getModifyTime());
        order.setStatus(request.getStatus());
        order.setSubmitter(request.getSubmitter());
        order.setSubmitTime(request.getSubmitTime());
        order.setApprover(request.getApprover());
        order.setApproveTime(request.getApproveTime());
        order.setRejectReason(request.getRejectReason());
        order.setCancelReason(request.getCancelReason());
        order.setOrderType(request.getOrderType());
        order.setTotalAmount(request.getTotalAmount());
        order.setCurrency(request.getCurrency());
        order.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        order.setRemark(request.getRemark());
        
        // 处理订单项
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            // 这里可以添加订单项的转换逻辑
        }
        
        return order;
    }
    
    /**
     * 将实体转换为响应DTO
     * @param order 采购订单实体
     * @return 采购订单响应DTO
     */
    public PurchaseOrderResponse convertToResponse(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        
        PurchaseOrderResponse response = new PurchaseOrderResponse();
        
        // 设置基本信息
        response.setId(String.valueOf(order.getId()));
        response.setOrderNumber(order.getOrderNumber());
        response.setSupplierId(order.getSupplierId());
        response.setDepartment(order.getDepartment());
        response.setCreator(order.getCreator());
        response.setCreateTime(order.getCreateTime());
        response.setModifier(order.getModifier());
        response.setModifyTime(order.getModifyTime());
        response.setStatus(order.getStatus());
        
        // 设置状态文本（可以根据状态码转换为中文描述）
        response.setStatusText(getStatusText(order.getStatus()));
        
        response.setSubmitter(order.getSubmitter());
        response.setSubmitTime(order.getSubmitTime());
        response.setApprover(order.getApprover());
        response.setApproveTime(order.getApproveTime());
        response.setRejectReason(order.getRejectReason());
        response.setCancelReason(order.getCancelReason());
        response.setOrderType(order.getOrderType());
        
        // 设置订单类型文本
        response.setOrderTypeText(getOrderTypeText(order.getOrderType()));
        
        response.setTotalAmount(order.getTotalAmount());
        response.setCurrency(order.getCurrency());
        response.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        response.setRemark(order.getRemark());
        
        // 处理订单项
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            // 这里可以添加订单项的转换逻辑
        }
        
        return response;
    }
    
    /**
     * 获取状态文本
     * @param statusCode 状态码
     * @return 状态文本
     */
    private String getStatusText(String statusCode) {
        // 根据状态码返回对应的中文描述
        if (statusCode == null) {
            return "";
        }
        
        switch (statusCode) {
            case "DRAFT":
                return "草稿";
            case "SUBMITTED":
                return "已提交";
            case "APPROVED":
                return "已审批";
            case "REJECTED":
                return "已拒绝";
            case "CANCELLED":
                return "已取消";
            case "EXECUTING":
                return "执行中";
            case "COMPLETED":
                return "已完成";
            default:
                return statusCode;
        }
    }
    
    /**
     * 获取订单类型文本
     * @param orderType 订单类型
     * @return 订单类型文本
     */
    private String getOrderTypeText(String orderType) {
        if (orderType == null) {
            return "";
        }
        
        switch (orderType) {
            case "REGULAR":
                return "常规采购";
            case "EMERGENCY":
                return "紧急采购";
            case "CONTRACTUAL":
                return "合同采购";
            default:
                return orderType;
        }
    }
}