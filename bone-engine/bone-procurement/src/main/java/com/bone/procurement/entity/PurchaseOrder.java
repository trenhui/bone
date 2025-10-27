package com.bone.procurement.entity;

import com.bone.core.domain.entity.Entity;
import com.bone.procurement.common.model.PurchaseOrder;
import com.bone.procurement.common.model.PurchaseOrderItem;
import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 采购订单适配器类
 * 适配共享模块中的PurchaseOrder模型，保持向后兼容性
 * 注意：此为适配层，最终应直接使用共享模块中的模型
 */
@SmartEntity(apiName = "PurchaseOrder", label = "采购订单", description = "企业采购商品或服务的订单记录")
@Getter
@Setter
public class PurchaseOrder extends Entity<Long> {
    
    // 持有共享模型实例
    private PurchaseOrder commonModel;
    
    public PurchaseOrder() {
        this.commonModel = new PurchaseOrder();
    }
    
    public PurchaseOrder(PurchaseOrder commonModel) {
        this.commonModel = commonModel;
    }
    
    public Long getId() {
        return commonModel.getId();
    }
    
    public void setId(Long id) {
        commonModel.setId(id);
    }
    
    @SmartField(name = "orderCode", label = "订单编号", type = FieldType.TEXT, required = true, unique = true, length = 50)
    public String getOrderCode() {
        return commonModel.getOrderCode();
    }
    
    public void setOrderCode(String orderCode) {
        commonModel.setOrderCode(orderCode);
    }
    
    // 转换供应商ID类型
    @SmartField(name = "supplierId", label = "供应商ID", type = FieldType.NUMBER, required = true)
    public Long getSupplierId() {
        return commonModel.getSupplierId() != null ? Long.valueOf(commonModel.getSupplierId()) : null;
    }
    
    public void setSupplierId(Long supplierId) {
        commonModel.setSupplierId(supplierId != null ? String.valueOf(supplierId) : null);
    }
    
    @SmartField(name = "orderType", label = "订单类型", type = FieldType.PICKLIST, required = true)
    @BusinessRule(name = "orderTypeRule", expression = "${orderType} != null && (${orderType}.equals('标准采购') || ${orderType}.equals('紧急采购') || ${orderType}.equals('日常采购'))", errorMessage = "订单类型必须为标准采购、紧急采购或日常采购")
    public String getOrderType() {
        return commonModel.getOrderType();
    }
    
    public void setOrderType(String orderType) {
        commonModel.setOrderType(orderType);
    }
    
    @SmartField(name = "orderStatus", label = "订单状态", type = FieldType.PICKLIST, required = true)
    public String getOrderStatus() {
        return commonModel.getOrderStatus();
    }
    
    public void setOrderStatus(String orderStatus) {
        commonModel.setOrderStatus(orderStatus);
    }
    
    @SmartField(name = "estimatedAmount", label = "预计金额", type = FieldType.CURRENCY, required = true)
    @BusinessRule(name = "estimatedAmountRule", expression = "${estimatedAmount} != null && ${estimatedAmount}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "预计金额必须大于0")
    public BigDecimal getEstimatedAmount() {
        return commonModel.getEstimatedAmount();
    }
    
    public void setEstimatedAmount(BigDecimal estimatedAmount) {
        commonModel.setEstimatedAmount(estimatedAmount);
    }
    
    @SmartField(name = "expectedDeliveryDate", label = "期望交货日期", type = FieldType.DATE, required = true)
    @BusinessRule(name = "expectedDeliveryDateRule", expression = "${expectedDeliveryDate} != null && ${expectedDeliveryDate}.isAfter(java.time.LocalDateTime.now())", errorMessage = "期望交货日期必须晚于当前日期")
    public LocalDateTime getExpectedDeliveryDate() {
        return commonModel.getExpectedDeliveryDate();
    }
    
    public void setExpectedDeliveryDate(LocalDateTime expectedDeliveryDate) {
        commonModel.setExpectedDeliveryDate(expectedDeliveryDate);
    }
    
    @SmartField(name = "taxRate", label = "税率", type = FieldType.PERCENT, defaultValue = "0.13")
    public Double getTaxRate() {
        return commonModel.getTaxRate();
    }
    
    public void setTaxRate(Double taxRate) {
        commonModel.setTaxRate(taxRate);
    }
    
    @SmartField(name = "totalAmountWithoutTax", label = "不含税总金额", type = FieldType.CURRENCY)
    public BigDecimal getTotalAmountWithoutTax() {
        return commonModel.getTotalAmountWithoutTax();
    }
    
    public void setTotalAmountWithoutTax(BigDecimal totalAmountWithoutTax) {
        commonModel.setTotalAmountWithoutTax(totalAmountWithoutTax);
    }
    
    @SmartField(name = "taxAmount", label = "税额", type = FieldType.CURRENCY)
    public BigDecimal getTaxAmount() {
        return commonModel.getTaxAmount();
    }
    
    public void setTaxAmount(BigDecimal taxAmount) {
        commonModel.setTaxAmount(taxAmount);
    }
    
    // 适配字段名差异
    @SmartField(name = "totalAmountWithTax", label = "含税总金额", type = FieldType.CURRENCY)
    public BigDecimal getTotalAmountWithTax() {
        return commonModel.getTotalAmount();
    }
    
    public void setTotalAmountWithTax(BigDecimal totalAmountWithTax) {
        commonModel.setTotalAmount(totalAmountWithTax);
    }
    
    @SmartField(name = "items", label = "订单项列表", type = FieldType.TEXT)
    public List<PurchaseOrderItem> getOrderItems() {
        if (commonModel.getOrderItems() == null) {
            return new ArrayList<>();
        }
        // 这里需要进行类型转换，因为两个模块的PurchaseOrderItem是不同的类
        // 后续需要修改PurchaseOrderItem类以支持适配
        return new ArrayList<>();
    }
    
    public void setOrderItems(List<PurchaseOrderItem> orderItems) {
        // 这里需要进行类型转换
        // 后续需要修改PurchaseOrderItem类以支持适配
    }
    
    @SmartField(name = "creationDate", label = "创建日期", type = FieldType.DATE)
    public LocalDateTime getCreationDate() {
        return commonModel.getCreationDate();
    }
    
    public void setCreationDate(LocalDateTime creationDate) {
        commonModel.setCreationDate(creationDate);
    }
    
    @SmartField(name = "createdBy", label = "创建人", type = FieldType.NUMBER)
    public Long getCreatedBy() {
        return commonModel.getCreatedBy();
    }
    
    public void setCreatedBy(Long createdBy) {
        commonModel.setCreatedBy(createdBy);
    }
    
    @SmartField(name = "approvedBy", label = "审批人", type = FieldType.NUMBER)
    public Long getApprovedBy() {
        return commonModel.getApprovedBy();
    }
    
    public void setApprovedBy(Long approvedBy) {
        commonModel.setApprovedBy(approvedBy);
    }
    
    @SmartField(name = "approvedDate", label = "审批日期", type = FieldType.DATE)
    public LocalDateTime getApprovedDate() {
        return commonModel.getApprovalDate();
    }
    
    public void setApprovedDate(LocalDateTime approvedDate) {
        commonModel.setApprovalDate(approvedDate);
    }
    
    @SmartField(name = "executionDate", label = "执行日期", type = FieldType.DATE)
    public LocalDateTime getExecutionDate() {
        return commonModel.getExecutionDate();
    }
    
    public void setExecutionDate(LocalDateTime executionDate) {
        commonModel.setExecutionDate(executionDate);
    }
    
    @SmartField(name = "currentApprovalNode", label = "当前审批节点", type = FieldType.TEXT, length = 100)
    public String getCurrentApprovalNode() {
        return commonModel.getCurrentApprovalNode();
    }
    
    public void setCurrentApprovalNode(String currentApprovalNode) {
        commonModel.setCurrentApprovalNode(currentApprovalNode);
    }
    
    @SmartField(name = "approvalProcessId", label = "审批流程ID", type = FieldType.TEXT, length = 50)
    public String getApprovalProcessId() {
        return commonModel.getApprovalProcessId();
    }
    
    public void setApprovalProcessId(String approvalProcessId) {
        commonModel.setApprovalProcessId(approvalProcessId);
    }
    
    @SmartField(name = "isOverdue", label = "是否逾期", type = FieldType.BOOLEAN)
    public Boolean getIsOverdue() {
        return commonModel.getIsOverdue();
    }
    
    public void setIsOverdue(Boolean isOverdue) {
        commonModel.setIsOverdue(isOverdue);
    }
    
    @SmartField(name = "delayDays", label = "延迟天数", type = FieldType.NUMBER)
    public Long getDelayDays() {
        return commonModel.getDelayDays();
    }
    
    public void setDelayDays(Long delayDays) {
        commonModel.setDelayDays(delayDays);
    }
    
    @SmartField(name = "deliveryAddress", label = "交货地址", type = FieldType.TEXT, length = 500)
    public String getDeliveryAddress() {
        return commonModel.getDeliveryAddress();
    }
    
    public void setDeliveryAddress(String deliveryAddress) {
        commonModel.setDeliveryAddress(deliveryAddress);
    }
    
    @SmartField(name = "paymentTerms", label = "付款条件", type = FieldType.TEXT, length = 200)
    public String getPaymentTerms() {
        return commonModel.getPaymentTerms();
    }
    
    public void setPaymentTerms(String paymentTerms) {
        commonModel.setPaymentTerms(paymentTerms);
    }
    
    @SmartField(name = "deliveryMethod", label = "交货方式", type = FieldType.TEXT, length = 100)
    public String getDeliveryMethod() {
        return commonModel.getDeliveryMethod();
    }
    
    public void setDeliveryMethod(String deliveryMethod) {
        commonModel.setDeliveryMethod(deliveryMethod);
    }
    
    @SmartField(name = "trackingNumber", label = "物流单号", type = FieldType.TEXT, length = 100)
    public String getTrackingNumber() {
        return commonModel.getTrackingNumber();
    }
    
    public void setTrackingNumber(String trackingNumber) {
        commonModel.setTrackingNumber(trackingNumber);
    }
    
    @SmartField(name = "internalRemarks", label = "内部备注", type = FieldType.TEXT, length = 1000)
    public String getInternalRemarks() {
        return commonModel.getInternalRemarks();
    }
    
    public void setInternalRemarks(String internalRemarks) {
        commonModel.setInternalRemarks(internalRemarks);
    }
    
    @SmartField(name = "externalRemarks", label = "外部备注", type = FieldType.TEXT, length = 1000)
    public String getExternalRemarks() {
        return commonModel.getExternalRemarks();
    }
    
    public void setExternalRemarks(String externalRemarks) {
        commonModel.setExternalRemarks(externalRemarks);
    }
    
    @SmartField(name = "orderSummary", label = "订单摘要", type = FieldType.TEXT)
    public String getOrderSummary() {
        return commonModel.getOrderSummary();
    }
    
    public void setOrderSummary(String orderSummary) {
        commonModel.setOrderSummary(orderSummary);
    }
    
    /**
     * 获取共享模型实例
     */
    public PurchaseOrder toCommonModel() {
        return commonModel;
    }
    
    /**
     * 从共享模型创建适配器实例
     */
    public static PurchaseOrder fromCommonModel(PurchaseOrder commonModel) {
        return new PurchaseOrder(commonModel);
    }
}
