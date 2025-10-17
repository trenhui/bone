package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单实体类
 * 演示bone-smartmeta引擎在采购订单管理场景中的应用
 */
@SmartEntity(apiName = "PurchaseOrder", label = "采购订单", description = "企业采购商品或服务的订单记录")
public class PurchaseOrder {
    
    private Long id;
    
    @SmartField(name = "orderCode", label = "订单编号", type = FieldType.TEXT, required = true, unique = true, length = 50)
    private String orderCode;
    
    @SmartField(name = "supplierId", label = "供应商ID", type = FieldType.NUMBER, required = true)
    private Long supplierId;
    
    @SmartField(name = "orderType", label = "订单类型", type = FieldType.PICKLIST, required = true)
    @BusinessRule(name = "orderTypeRule", expression = "${orderType} != null && (${orderType}.equals('标准采购') || ${orderType}.equals('紧急采购') || ${orderType}.equals('日常采购'))", errorMessage = "订单类型必须为标准采购、紧急采购或日常采购")
    private String orderType;
    
    @SmartField(name = "orderStatus", label = "订单状态", type = FieldType.PICKLIST, required = true)
    private String orderStatus;
    
    @SmartField(name = "estimatedAmount", label = "预计金额", type = FieldType.CURRENCY, required = true)
    @BusinessRule(name = "estimatedAmountRule", expression = "${estimatedAmount} != null && ${estimatedAmount}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "预计金额必须大于0")
    private BigDecimal estimatedAmount;
    
    @SmartField(name = "expectedDeliveryDate", label = "期望交货日期", type = FieldType.DATE, required = true)
    @BusinessRule(name = "expectedDeliveryDateRule", expression = "${expectedDeliveryDate} != null && ${expectedDeliveryDate}.isAfter(java.time.LocalDateTime.now())", errorMessage = "期望交货日期必须晚于当前日期")
    private LocalDateTime expectedDeliveryDate;
    
    @SmartField(name = "taxRate", label = "税率", type = FieldType.PERCENT, defaultValue = "0.13")
    private Double taxRate;
    
    @SmartField(name = "totalAmountWithoutTax", label = "不含税总金额", type = FieldType.CURRENCY)
    private BigDecimal totalAmountWithoutTax;
    
    @SmartField(name = "taxAmount", label = "税额", type = FieldType.CURRENCY)
    private BigDecimal taxAmount;
    
    @SmartField(name = "totalAmountWithTax", label = "含税总金额", type = FieldType.CURRENCY)
    private BigDecimal totalAmountWithTax;
    
    @SmartField(name = "items", label = "订单项列表", type = FieldType.TEXT)
    private List<PurchaseOrderItem> orderItems;
    
    @SmartField(name = "creationDate", label = "创建日期", type = FieldType.DATE)
    private LocalDateTime creationDate;
    
    @SmartField(name = "createdBy", label = "创建人", type = FieldType.NUMBER)
    private Long createdBy;
    
    @SmartField(name = "approvedBy", label = "审批人", type = FieldType.NUMBER)
    private Long approvedBy;
    
    @SmartField(name = "approvedDate", label = "审批日期", type = FieldType.DATE)
    private LocalDateTime approvedDate;
    
    @SmartField(name = "executionDate", label = "执行日期", type = FieldType.DATE)
    private LocalDateTime executionDate;
    
    @SmartField(name = "currentApprovalNode", label = "当前审批节点", type = FieldType.TEXT, length = 100)
    private String currentApprovalNode;
    
    @SmartField(name = "approvalProcessId", label = "审批流程ID", type = FieldType.TEXT, length = 50)
    private String approvalProcessId;
    
    @SmartField(name = "isOverdue", label = "是否逾期", type = FieldType.BOOLEAN)
    private Boolean isOverdue;
    
    @SmartField(name = "delayDays", label = "延迟天数", type = FieldType.NUMBER)
    private Long delayDays;
    
    @SmartField(name = "deliveryAddress", label = "交货地址", type = FieldType.TEXT, length = 500)
    private String deliveryAddress;
    
    @SmartField(name = "paymentTerms", label = "付款条件", type = FieldType.TEXT, length = 200)
    private String paymentTerms;
    
    @SmartField(name = "deliveryMethod", label = "交货方式", type = FieldType.TEXT, length = 100)
    private String deliveryMethod;
    
    @SmartField(name = "trackingNumber", label = "物流单号", type = FieldType.TEXT, length = 100)
    private String trackingNumber;
    
    @SmartField(name = "internalRemarks", label = "内部备注", type = FieldType.TEXT, length = 1000)
    private String internalRemarks;
    
    @SmartField(name = "externalRemarks", label = "外部备注", type = FieldType.TEXT, length = 1000)
    private String externalRemarks;
    
    @SmartField(name = "orderSummary", label = "订单摘要", type = FieldType.TEXT)
    private String orderSummary;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    
    public LocalDateTime getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDateTime expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
    
    public Double getTaxRate() { return taxRate; }
    public void setTaxRate(Double taxRate) { this.taxRate = taxRate; }
    
    public BigDecimal getTotalAmountWithoutTax() { return totalAmountWithoutTax; }
    public void setTotalAmountWithoutTax(BigDecimal totalAmountWithoutTax) { this.totalAmountWithoutTax = totalAmountWithoutTax; }
    
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    
    public BigDecimal getTotalAmountWithTax() { return totalAmountWithTax; }
    public void setTotalAmountWithTax(BigDecimal totalAmountWithTax) { this.totalAmountWithTax = totalAmountWithTax; }
    
    public List<PurchaseOrderItem> getOrderItems() { return orderItems; }
    public void setOrderItems(List<PurchaseOrderItem> orderItems) { this.orderItems = orderItems; }
    
    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    
    public LocalDateTime getApprovedDate() { return approvedDate; }
    public void setApprovedDate(LocalDateTime approvedDate) { this.approvedDate = approvedDate; }
    
    public LocalDateTime getExecutionDate() { return executionDate; }
    public void setExecutionDate(LocalDateTime executionDate) { this.executionDate = executionDate; }
    
    public String getCurrentApprovalNode() { return currentApprovalNode; }
    public void setCurrentApprovalNode(String currentApprovalNode) { this.currentApprovalNode = currentApprovalNode; }
    
    public String getApprovalProcessId() { return approvalProcessId; }
    public void setApprovalProcessId(String approvalProcessId) { this.approvalProcessId = approvalProcessId; }
    
    public Boolean getIsOverdue() { return isOverdue; }
    public void setIsOverdue(Boolean isOverdue) { this.isOverdue = isOverdue; }
    
    public Long getDelayDays() { return delayDays; }
    public void setDelayDays(Long delayDays) { this.delayDays = delayDays; }
    
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    
    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }
    
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    
    public String getInternalRemarks() { return internalRemarks; }
    public void setInternalRemarks(String internalRemarks) { this.internalRemarks = internalRemarks; }
    
    public String getExternalRemarks() { return externalRemarks; }
    public void setExternalRemarks(String externalRemarks) { this.externalRemarks = externalRemarks; }
    
    public String getOrderSummary() { return orderSummary; }
    public void setOrderSummary(String orderSummary) { this.orderSummary = orderSummary; }
}
