package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单实体类
 * 演示bone-smartmeta在复杂业务场景中的应用，包括多级审批、金额计算和状态流转
 */
@Data
@SmartEntity(apiName = "PurchaseOrder", label = "采购订单", description = "企业采购物料或服务的正式订单")
public class PurchaseOrder {
    
    private Long id;
    
    @SmartField(name = "orderCode", label = "订单编号", type = FieldType.TEXT, required = true, unique = true, length = 50)
    private String orderCode;
    
    @SmartField(name = "orderName", label = "订单名称", type = FieldType.TEXT, required = true, length = 200)
    private String orderName;
    
    @SmartField(name = "supplierId", label = "供应商ID", type = FieldType.NUMBER, required = true)
    private Long supplierId;
    
    @SmartField(name = "orderType", label = "订单类型", type = FieldType.PICKLIST)
    private String orderType;
    
    @SmartField(name = "orderStatus", label = "订单状态", type = FieldType.PICKLIST)
    private String orderStatus;
    
    @SmartField(name = "estimatedAmount", label = "预计金额", type = FieldType.CURRENCY, required = true)
    @BusinessRule(expression = "${estimatedAmount}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "预计金额必须大于0")
    private BigDecimal estimatedAmount;
    
    @SmartField(name = "actualAmount", label = "实际金额", type = FieldType.CURRENCY)
    private BigDecimal actualAmount;
    
    @SmartField(name = "creationDate", label = "订单创建日期", type = FieldType.DATE_TIME)
    private LocalDateTime creationDate;
    
    @SmartField(name = "expectedDeliveryDate", label = "期望交货日期", type = FieldType.DATE_TIME, required = true)
    @BusinessRule(expression = "${expectedDeliveryDate}.isAfter(java.time.LocalDateTime.now())", errorMessage = "期望交货日期必须晚于当前日期")
    private LocalDateTime expectedDeliveryDate;
    
    @SmartField(name = "actualDeliveryDate", label = "实际交货日期", type = FieldType.DATE_TIME)
    private LocalDateTime actualDeliveryDate;
    
    @SmartField(name = "createdBy", label = "下单人ID", type = FieldType.NUMBER)
    private Long createdBy;
    
    @SmartField(name = "approvedBy", label = "审批人ID", type = FieldType.NUMBER)
    private Long approvedBy;
    
    @SmartField(name = "approvedDate", label = "审批日期", type = FieldType.DATE_TIME)
    private LocalDateTime approvedDate;
    
    @SmartField(name = "deliveryAddressId", label = "收货地址ID", type = FieldType.NUMBER)
    private Long deliveryAddressId;
    
    @SmartField(name = "paymentMethod", label = "付款方式", type = FieldType.PICKLIST)
    private String paymentMethod;
    
    @SmartField(name = "taxRate", label = "税率", type = FieldType.PERCENT, defaultValue = "0.13")
    private Double taxRate;
    
    @SmartField(name = "orderItems", label = "采购项目", type = FieldType.LOOKUP)
    @BusinessRule(expression = "${orderItems != null && !${orderItems}.isEmpty()}", errorMessage = "采购订单必须包含至少一个采购项目")
    private List<PurchaseOrderItem> orderItems;
    
    // 计算字段：订单总金额（不含税）
    @SmartField(name = "totalAmountWithoutTax", label = "订单总金额（不含税）", type = FieldType.CURRENCY)
    private BigDecimal totalAmountWithoutTax;
    
    // 计算字段：订单税额
    @SmartField(name = "taxAmount", label = "订单税额", type = FieldType.CURRENCY)
    private BigDecimal taxAmount;
    
    // 计算字段：订单总金额（含税）
    @SmartField(name = "totalAmountWithTax", label = "订单总金额（含税）", type = FieldType.CURRENCY)
    private BigDecimal totalAmountWithTax;
    
    // 计算字段：订单是否超时
    @SmartField(name = "isOverdue", label = "是否超时", type = FieldType.BOOLEAN)
    private Boolean isOverdue;
    
    // 计算字段：订单延迟天数
    @SmartField(name = "delayDays", label = "延迟天数", type = FieldType.NUMBER)
    private Long delayDays;
    
    // 虚拟字段：订单摘要
    @SmartField(name = "orderSummary", label = "订单摘要", type = FieldType.TEXT, virtual = true)
    private String orderSummary;
    
    // 审批流相关字段
    @SmartField(name = "currentApprovalNode", label = "当前审批节点", type = FieldType.TEXT)
    private String currentApprovalNode;
    
    @SmartField(name = "approvalProcessId", label = "审批流程ID", type = FieldType.TEXT)
    private String approvalProcessId;
    
    // 备注信息
    @SmartField(name = "remarks", label = "备注", type = FieldType.TEXT, length = 1000)
    private String remarks;
}
