package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单实体类
 * 演示bone-smartmeta在复杂业务场景中的应用，包括多级审批、金额计算和状态流转
 */
@Data
@SmartEntity(displayName = "采购订单", description = "企业采购物料或服务的正式订单")
public class PurchaseOrder {
    
    private Long id;
    
    @SmartField(displayName = "订单编号", required = true, unique = true, maxLength = 50)
    private String orderCode;
    
    @SmartField(displayName = "订单名称", required = true, maxLength = 200)
    private String orderName;
    
    @SmartField(displayName = "供应商ID", required = true)
    private Long supplierId;
    
    @SmartField(displayName = "订单类型", options = {"标准采购", "紧急采购", "战略性采购", "临时采购"})
    private String orderType;
    
    @SmartField(displayName = "订单状态", options = {"草稿", "待审批", "已审批", "已拒绝", "已下单", "已发货", "已收货", "已完成", "已取消"})
    private String orderStatus;
    
    @SmartField(displayName = "预计金额", required = true)
    private BigDecimal estimatedAmount;
    
    @SmartField(displayName = "实际金额")
    private BigDecimal actualAmount;
    
    @SmartField(displayName = "订单创建日期")
    private LocalDateTime creationDate;
    
    @SmartField(displayName = "期望交货日期", required = true)
    private LocalDateTime expectedDeliveryDate;
    
    @SmartField(displayName = "实际交货日期")
    private LocalDateTime actualDeliveryDate;
    
    @SmartField(displayName = "下单人ID")
    private Long createdBy;
    
    @SmartField(displayName = "审批人ID")
    private Long approvedBy;
    
    @SmartField(displayName = "审批日期")
    private LocalDateTime approvedDate;
    
    @SmartField(displayName = "收货地址ID")
    private Long deliveryAddressId;
    
    @SmartField(displayName = "付款方式", options = {"货到付款", "预付30%", "预付50%", "全额预付", "月结30天", "月结60天"})
    private String paymentMethod;
    
    @SmartField(displayName = "税率", defaultValue = "0.13")
    private Double taxRate;
    
    @SmartField(displayName = "采购项目", multiple = true)
    private List<PurchaseOrderItem> orderItems;
    
    // 计算字段：订单总金额（不含税）
    @SmartField(displayName = "订单总金额（不含税）", calculated = true,
                calculationExpression = "${orderItems.stream().mapToDouble(item -> item.getUnitPrice().multiply(new java.math.BigDecimal(item.getQuantity()))).sum()}")
    private BigDecimal totalAmountWithoutTax;
    
    // 计算字段：订单税额
    @SmartField(displayName = "订单税额", calculated = true,
                calculationExpression = "${totalAmountWithoutTax.multiply(java.math.BigDecimal.valueOf(${taxRate}))}")
    private BigDecimal taxAmount;
    
    // 计算字段：订单总金额（含税）
    @SmartField(displayName = "订单总金额（含税）", calculated = true,
                calculationExpression = "${totalAmountWithoutTax.add(${taxAmount})}")
    private BigDecimal totalAmountWithTax;
    
    // 计算字段：订单是否超时
    @SmartField(displayName = "是否超时", calculated = true,
                expression = "${orderStatus.equals('已下单') && ${expectedDeliveryDate}.isBefore(java.time.LocalDateTime.now())}")
    private Boolean isOverdue;
    
    // 计算字段：订单延迟天数
    @SmartField(displayName = "延迟天数", calculated = true,
                expression = "${isOverdue ? java.time.temporal.ChronoUnit.DAYS.between(${expectedDeliveryDate}, java.time.LocalDateTime.now()) : 0}")
    private Long delayDays;
    
    // 业务规则验证
    @BusinessRule(expression = "${estimatedAmount}.compareTo(java.math.BigDecimal.ZERO) > 0", message = "预计金额必须大于0")
    @BusinessRule(expression = "${expectedDeliveryDate}.isAfter(java.time.LocalDateTime.now())", message = "期望交货日期必须晚于当前日期")
    @BusinessRule(expression = "${orderItems != null && !${orderItems}.isEmpty()}", message = "采购订单必须包含至少一个采购项目")
    
    // 虚拟字段：订单摘要
    @SmartField(displayName = "订单摘要", virtual = true,
                expression = "订单${orderCode} - ${supplierName} - ${totalAmountWithTax}元 - ${orderStatus}")
    private String orderSummary;
    
    // 审批流相关字段
    @SmartField(displayName = "当前审批节点")
    private String currentApprovalNode;
    
    @SmartField(displayName = "审批流程ID")
    private String approvalProcessId;
    
    // 备注信息
    @SmartField(displayName = "备注", maxLength = 1000)
    private String remarks;
}
