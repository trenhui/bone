package com.bone.procurement.common.model;

import com.bone.core.domain.entity.Entity;
import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.SmartRelationship;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单共享领域模型
 * 整合了原有两个版本的采购订单实体，作为系统中统一的采购订单数据模型
 */
@SmartEntity(
    apiName = "PurchaseOrder",
    label = "采购订单",
    description = "企业采购业务的核心实体，包含订单基本信息、供应商信息、金额信息、状态信息等",
    domain = "procurement",
    trackHistory = true,
    importance = SmartEntity.EntityImportance.HIGH
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder extends Entity<Long> {
    
    // 主键信息
    private Long id;
    
    @SmartField(name = "orderCode", label = "订单编号", type = FieldType.TEXT, required = true, unique = true, length = 50, indexed = true, description = "业务主键")
    private String orderCode;
    
    // 订单基本信息
    @SmartField(name = "orderType", label = "订单类型", type = FieldType.TEXT, required = true, defaultValue = "常规采购", description = "通过元数据驱动不同采购类型的差异化流程")
    @BusinessRule(name = "orderTypeRule", expression = "${orderType} != null && (${orderType}.equals('标准采购') || ${orderType}.equals('紧急采购') || ${orderType}.equals('日常采购') || ${orderType}.equals('常规采购'))", errorMessage = "订单类型必须为标准采购、紧急采购、日常采购或常规采购")
    private String orderType;
    
    @SmartField(name = "source", label = "订单来源", type = FieldType.TEXT)
    private String source;               // 订单来源
    
    @SmartField(name = "requestId", label = "申请单ID", type = FieldType.TEXT)
    private String requestId;            // 关联的申请单ID
    
    @SmartField(name = "businessType", label = "业务类型", type = FieldType.TEXT)
    private String businessType;         // 业务类型
    
    @SmartField(name = "description", label = "描述", type = FieldType.TEXT)
    private String description;          // 描述
    
    // 供应商信息
    @SmartField(name = "supplierId", label = "供应商ID", required = true, type = FieldType.TEXT, description = "关联供应商表")
    private String supplierId;           // 供应商ID
    
    @SmartField(name = "supplierName", label = "供应商名称", type = FieldType.TEXT)
    private String supplierName;         // 供应商名称
    
    // 金额信息
    @SmartField(name = "estimatedAmount", label = "预计金额", type = FieldType.CURRENCY, required = true)
    @BusinessRule(name = "estimatedAmountRule", expression = "${estimatedAmount} != null && ${estimatedAmount}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "预计金额必须大于0")
    private BigDecimal estimatedAmount;
    
    @SmartField(name = "totalAmount", label = "总金额", type = FieldType.NUMBER, precision = 10, scale = 2, calculationExpression = "SUM(orderItems.totalPrice)", description = "通过规则引擎计算得出")
    private BigDecimal totalAmount;      // 总金额
    
    @SmartField(name = "taxAmount", label = "税额", type = FieldType.NUMBER, precision = 10, scale = 2)
    private BigDecimal taxAmount;        // 税额
    
    @SmartField(name = "totalAmountWithoutTax", label = "不含税总金额", type = FieldType.CURRENCY)
    private BigDecimal totalAmountWithoutTax;  // 不含税金额
    
    @SmartField(name = "taxRate", label = "税率", type = FieldType.PERCENT, defaultValue = "0.13")
    private Double taxRate;              // 税率
    
    @SmartField(name = "currency", label = "币种", type = FieldType.TEXT, defaultValue = "CNY")
    private String currency;             // 币种
    
    // 时间信息
    @SmartField(name = "expectedDeliveryDate", label = "期望交货日期", type = FieldType.DATE, required = true)
    @BusinessRule(name = "expectedDeliveryDateRule", expression = "${expectedDeliveryDate} != null && ${expectedDeliveryDate}.isAfter(java.time.LocalDateTime.now())", errorMessage = "期望交货日期必须晚于当前日期")
    private LocalDateTime expectedDeliveryDate; // 期望交货日期
    
    @SmartField(name = "actualDeliveryDate", label = "实际交货日期", type = FieldType.DATE)
    private LocalDateTime actualDeliveryDate;   // 实际交货日期
    
    @SmartField(name = "creationDate", label = "创建日期", type = FieldType.DATE)
    private LocalDateTime creationDate;  // 创建日期
    
    @SmartField(name = "approvalDate", label = "审批日期", type = FieldType.DATE_TIME)
    private LocalDateTime approvalDate;        // 审批日期
    
    @SmartField(name = "executionDate", label = "执行日期", type = FieldType.DATE)
    private LocalDateTime executionDate; // 执行日期
    
    @SmartField(name = "updateTime", label = "更新时间", type = FieldType.DATE_TIME)
    private LocalDateTime updateTime;          // 更新时间
    
    // 状态信息
    @SmartField(name = "orderStatus", label = "订单状态", type = FieldType.TEXT, required = true, defaultValue = "DRAFT", indexed = true, description = "定义状态流转规则，确保业务流程合规")
    private String orderStatus;          // 订单状态
    
    @SmartField(name = "approvalStatus", label = "审批状态", type = FieldType.TEXT, required = true, defaultValue = "PENDING", indexed = true)
    private String approvalStatus;       // 审批状态
    
    @SmartField(name = "paymentStatus", label = "付款状态", type = FieldType.TEXT, defaultValue = "UNPAID")
    private String paymentStatus;        // 付款状态
    
    @SmartField(name = "deliveryStatus", label = "交货状态", type = FieldType.TEXT, defaultValue = "PENDING")
    private String deliveryStatus;       // 交货状态
    
    // 审批信息
    @SmartField(name = "currentApprovalNode", label = "当前审批节点", type = FieldType.TEXT, length = 100)
    private String currentApprovalNode;  // 当前审批节点
    
    @SmartField(name = "approvalProcessId", label = "审批流程ID", type = FieldType.TEXT, length = 50)
    private String approvalProcessId;    // 审批流程ID
    
    @SmartField(name = "createdBy", label = "创建人", type = FieldType.NUMBER)
    private Long createdBy;              // 创建人
    
    @SmartField(name = "approvedBy", label = "审批人", type = FieldType.NUMBER)
    private Long approvedBy;             // 审批人
    
    // 物流信息
    @SmartField(name = "isOverdue", label = "是否逾期", type = FieldType.BOOLEAN)
    private Boolean isOverdue;           // 是否逾期
    
    @SmartField(name = "delayDays", label = "延迟天数", type = FieldType.NUMBER)
    private Long delayDays;              // 延迟天数
    
    @SmartField(name = "deliveryAddress", label = "交货地址", type = FieldType.TEXT, length = 500)
    private String deliveryAddress;      // 交货地址
    
    @SmartField(name = "paymentTerms", label = "付款条件", type = FieldType.TEXT, length = 200)
    private String paymentTerms;         // 付款条件
    
    @SmartField(name = "deliveryMethod", label = "交货方式", type = FieldType.TEXT, length = 100)
    private String deliveryMethod;       // 交货方式
    
    @SmartField(name = "trackingNumber", label = "物流单号", type = FieldType.TEXT, length = 100)
    private String trackingNumber;       // 物流单号
    
    // 备注信息
    @SmartField(name = "internalRemarks", label = "内部备注", type = FieldType.TEXT, length = 1000)
    private String internalRemarks;      // 内部备注
    
    @SmartField(name = "externalRemarks", label = "外部备注", type = FieldType.TEXT, length = 1000)
    private String externalRemarks;      // 外部备注
    
    @SmartField(name = "orderSummary", label = "订单摘要", type = FieldType.TEXT)
    private String orderSummary;         // 订单摘要
    
    // 关联订单项
    @SmartRelationship(
        name = "orderItems",
        label = "订单项列表",
        targetEntity = "PurchaseOrderItem",
        relationshipType = SmartRelationship.RelationshipType.ONE_TO_MANY,
        cascade = true
    )
    private List<PurchaseOrderItem> orderItems; // 订单明细列表
}