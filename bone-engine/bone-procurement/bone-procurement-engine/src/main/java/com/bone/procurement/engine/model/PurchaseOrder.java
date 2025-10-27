package com.bone.procurement.engine.model;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.SmartRelationship;
import com.bone.procurement.common.model.PurchaseOrder;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 采购订单适配器类
 * 适配共享模块中的PurchaseOrder模型，保持向后兼容性
 * 注意：此为适配层，最终应直接使用共享模块中的模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {    
    // 持有共享模型实例
    private PurchaseOrder commonModel;
    
    // 扩展字段，共享模型中不存在的字段
    private String businessDomain;       // 业务域
    private String supplierContact;      // 供应商联系人
    private String supplierPhone;        // 供应商联系电话
    private LocalDateTime orderDate;     // 下单日期
    private Boolean isUrgent;            // 是否紧急
    private String createdByName;        // 创建人名称
    private String updatedBy;            // 更新人ID
    private String updatedByName;        // 更新人名称
    private String approverId;           // 审批人ID
    private String approverName;         // 审批人名称
    private String projectId;            // 项目ID
    private String projectName;          // 项目名称
    private String departmentId;         // 部门ID
    private String departmentName;       // 部门名称
    private String budgetCode;           // 预算编码
    private String tenantId;             // 租户ID
    private String extendField1;         // 扩展字段1
    private String extendField2;         // 扩展字段2
    private String extendField3;         // 扩展字段3
    
    // 订单项列表
    private List<PurchaseOrderItem> orderItems; // 订单明细列表
    
    // 构造方法
    public PurchaseOrder(PurchaseOrder commonModel) {
        this.commonModel = commonModel;
        // 初始化扩展字段
        this.isUrgent = false; // 默认非紧急
        
        // 将共享模型中的订单项转换为引擎模块的订单项
        if (commonModel != null && commonModel.getOrderItems() != null) {
            this.orderItems = commonModel.getOrderItems().stream()
                    .map(item -> {
                        PurchaseOrderItem engineItem = new PurchaseOrderItem();
                        // 映射字段...
                        return engineItem;
                    })
                    .collect(Collectors.toList());
        }
    }
    
    // 代理方法：通过共享模型访问基础信息
    public String getOrderId() {
        return commonModel != null ? String.valueOf(commonModel.getId()) : null;
    }
    
    public String getOrderCode() {
        return commonModel != null ? commonModel.getOrderCode() : null;
    }
    
    public void setOrderCode(String orderCode) {
        if (commonModel != null) {
            commonModel.setOrderCode(orderCode);
        }
    }
    
    public String getOrderType() {
        return commonModel != null ? commonModel.getOrderType() : null;
    }
    
    public void setOrderType(String orderType) {
        if (commonModel != null) {
            commonModel.setOrderType(orderType);
        }
    }
    
    public String getSource() {
        return commonModel != null ? commonModel.getSource() : null;
    }
    
    public void setSource(String source) {
        if (commonModel != null) {
            commonModel.setSource(source);
        }
    }
    
    public String getRequestId() {
        return commonModel != null ? commonModel.getRequestId() : null;
    }
    
    public void setRequestId(String requestId) {
        if (commonModel != null) {
            commonModel.setRequestId(requestId);
        }
    }
    
    public String getBusinessType() {
        return commonModel != null ? commonModel.getBusinessType() : null;
    }
    
    public void setBusinessType(String businessType) {
        if (commonModel != null) {
            commonModel.setBusinessType(businessType);
        }
    }
    
    public String getDescription() {
        return commonModel != null ? commonModel.getDescription() : null;
    }
    
    public void setDescription(String description) {
        if (commonModel != null) {
            commonModel.setDescription(description);
        }
    }
    
    public String getSupplierId() {
        return commonModel != null ? commonModel.getSupplierId() : null;
    }
    
    public void setSupplierId(String supplierId) {
        if (commonModel != null) {
            commonModel.setSupplierId(supplierId);
        }
    }
    
    public String getSupplierName() {
        return commonModel != null ? commonModel.getSupplierName() : null;
    }
    
    public void setSupplierName(String supplierName) {
        if (commonModel != null) {
            commonModel.setSupplierName(supplierName);
        }
    }
    
    public BigDecimal getTotalAmount() {
        return commonModel != null ? commonModel.getTotalAmount() : null;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        if (commonModel != null) {
            commonModel.setTotalAmount(totalAmount);
        }
    }
    
    public BigDecimal getTaxAmount() {
        return commonModel != null ? commonModel.getTaxAmount() : null;
    }
    
    public void setTaxAmount(BigDecimal taxAmount) {
        if (commonModel != null) {
            commonModel.setTaxAmount(taxAmount);
        }
    }
    
    public BigDecimal getTotalWithoutTax() {
        return commonModel != null ? commonModel.getTotalAmountWithoutTax() : null;
    }
    
    public void setTotalWithoutTax(BigDecimal totalWithoutTax) {
        if (commonModel != null) {
            commonModel.setTotalAmountWithoutTax(totalWithoutTax);
        }
    }
    
    public String getCurrency() {
        return commonModel != null ? commonModel.getCurrency() : null;
    }
    
    public void setCurrency(String currency) {
        if (commonModel != null) {
            commonModel.setCurrency(currency);
        }
    }
    
    public String getTaxRate() {
        return commonModel != null && commonModel.getTaxRate() != null ? String.valueOf(commonModel.getTaxRate()) : null;
    }
    
    public void setTaxRate(String taxRate) {
        if (commonModel != null) {
            try {
                commonModel.setTaxRate(Double.valueOf(taxRate));
            } catch (Exception e) {
                // 税率格式错误，忽略设置
            }
        }
    }
    
    public LocalDateTime getExpectedDeliveryDate() {
        return commonModel != null ? commonModel.getExpectedDeliveryDate() : null;
    }
    
    public void setExpectedDeliveryDate(LocalDateTime expectedDeliveryDate) {
        if (commonModel != null) {
            commonModel.setExpectedDeliveryDate(expectedDeliveryDate);
        }
    }
    
    public LocalDateTime getActualDeliveryDate() {
        return commonModel != null ? commonModel.getActualDeliveryDate() : null;
    }
    
    public void setActualDeliveryDate(LocalDateTime actualDeliveryDate) {
        if (commonModel != null) {
            commonModel.setActualDeliveryDate(actualDeliveryDate);
        }
    }
    
    public LocalDateTime getApprovalDate() {
        return commonModel != null ? commonModel.getApprovalDate() : null;
    }
    
    public void setApprovalDate(LocalDateTime approvalDate) {
        if (commonModel != null) {
            commonModel.setApprovalDate(approvalDate);
        }
    }
    
    public LocalDateTime getCreationDate() {
        return commonModel != null ? commonModel.getCreationDate() : null;
    }
    
    public void setCreationDate(LocalDateTime creationDate) {
        if (commonModel != null) {
            commonModel.setCreationDate(creationDate);
        }
    }
    
    // 兼容方法
    public LocalDateTime getCreateTime() {
        return getCreationDate();
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        setCreationDate(createTime);
    }
    
    public LocalDateTime getUpdateTime() {
        return commonModel != null ? commonModel.getUpdateTime() : null;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        if (commonModel != null) {
            commonModel.setUpdateTime(updateTime);
        }
    }
    
    public String getOrderStatus() {
        return commonModel != null ? commonModel.getOrderStatus() : null;
    }
    
    public void setOrderStatus(String orderStatus) {
        if (commonModel != null) {
            commonModel.setOrderStatus(orderStatus);
        }
    }
    
    // 兼容方法
    public String getStatus() {
        return getOrderStatus();
    }
    
    public void setStatus(String status) {
        setOrderStatus(status);
    }
    
    public String getApprovalStatus() {
        return commonModel != null ? commonModel.getApprovalStatus() : null;
    }
    
    public void setApprovalStatus(String approvalStatus) {
        if (commonModel != null) {
            commonModel.setApprovalStatus(approvalStatus);
        }
    }
    
    public String getPaymentStatus() {
        return commonModel != null ? commonModel.getPaymentStatus() : null;
    }
    
    public void setPaymentStatus(String paymentStatus) {
        if (commonModel != null) {
            commonModel.setPaymentStatus(paymentStatus);
        }
    }
    
    public String getDeliveryStatus() {
        return commonModel != null ? commonModel.getDeliveryStatus() : null;
    }
    
    public void setDeliveryStatus(String deliveryStatus) {
        if (commonModel != null) {
            commonModel.setDeliveryStatus(deliveryStatus);
        }
    }
    
    public String getCreatedBy() {
        return commonModel != null && commonModel.getCreatedBy() != null ? String.valueOf(commonModel.getCreatedBy()) : null;
    }
    
    public void setCreatedBy(String createdBy) {
        if (commonModel != null) {
            try {
                commonModel.setCreatedBy(Long.valueOf(createdBy));
            } catch (Exception e) {
                // ID格式错误，忽略设置
            }
        }
    }
    
    public String getApprovedBy() {
        return commonModel != null && commonModel.getApprovedBy() != null ? String.valueOf(commonModel.getApprovedBy()) : null;
    }
    
    public void setApprovedBy(String approvedBy) {
        if (commonModel != null) {
            try {
                commonModel.setApprovedBy(Long.valueOf(approvedBy));
            } catch (Exception e) {
                // ID格式错误，忽略设置
            }
        }
    }
    
    // 兼容方法
    public String getCurrentApprovalNode() {
        return commonModel != null ? commonModel.getCurrentApprovalNode() : null;
    }
    
    public void setCurrentApprovalNode(String currentApprovalNode) {
        if (commonModel != null) {
            commonModel.setCurrentApprovalNode(currentApprovalNode);
        }
    }
    
    public String getApprovalProcessId() {
        return commonModel != null ? commonModel.getApprovalProcessId() : null;
    }
    
    public void setApprovalProcessId(String approvalProcessId) {
        if (commonModel != null) {
            commonModel.setApprovalProcessId(approvalProcessId);
        }
    }
    
    public Boolean getIsOverdue() {
        return commonModel != null ? commonModel.getIsOverdue() : null;
    }
    
    public void setIsOverdue(Boolean isOverdue) {
        if (commonModel != null) {
            commonModel.setIsOverdue(isOverdue);
        }
    }
    
    public Long getDelayDays() {
        return commonModel != null ? commonModel.getDelayDays() : null;
    }
    
    public void setDelayDays(Long delayDays) {
        if (commonModel != null) {
            commonModel.setDelayDays(delayDays);
        }
    }
    
    public String getDeliveryAddress() {
        return commonModel != null ? commonModel.getDeliveryAddress() : null;
    }
    
    public void setDeliveryAddress(String deliveryAddress) {
        if (commonModel != null) {
            commonModel.setDeliveryAddress(deliveryAddress);
        }
    }
    
    public String getPaymentTerms() {
        return commonModel != null ? commonModel.getPaymentTerms() : null;
    }
    
    public void setPaymentTerms(String paymentTerms) {
        if (commonModel != null) {
            commonModel.setPaymentTerms(paymentTerms);
        }
    }
    
    public String getDeliveryMethod() {
        return commonModel != null ? commonModel.getDeliveryMethod() : null;
    }
    
    public void setDeliveryMethod(String deliveryMethod) {
        if (commonModel != null) {
            commonModel.setDeliveryMethod(deliveryMethod);
        }
    }
    
    public String getTrackingNumber() {
        return commonModel != null ? commonModel.getTrackingNumber() : null;
    }
    
    public void setTrackingNumber(String trackingNumber) {
        if (commonModel != null) {
            commonModel.setTrackingNumber(trackingNumber);
        }
    }
    
    public String getInternalRemarks() {
        return commonModel != null ? commonModel.getInternalRemarks() : null;
    }
    
    public void setInternalRemarks(String internalRemarks) {
        if (commonModel != null) {
            commonModel.setInternalRemarks(internalRemarks);
        }
    }
    
    public String getExternalRemarks() {
        return commonModel != null ? commonModel.getExternalRemarks() : null;
    }
    
    public void setExternalRemarks(String externalRemarks) {
        if (commonModel != null) {
            commonModel.setExternalRemarks(externalRemarks);
        }
    }
    
    public String getOrderSummary() {
        return commonModel != null ? commonModel.getOrderSummary() : null;
    }
    
    public void setOrderSummary(String orderSummary) {
        if (commonModel != null) {
            commonModel.setOrderSummary(orderSummary);
        }
    }
    
    /**
     * 计算订单总金额
     */
    public void calculateTotalAmount() {
        if (orderItems != null && !orderItems.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (PurchaseOrderItem item : orderItems) {
                if (item.getTotalPrice() != null) {
                    total = total.add(item.getTotalPrice());
                }
            }
            setTotalAmount(total);
        }
    }
    
    /**
     * 判断是否为紧急采购
     */
    public boolean isEmergencyPurchase() {
        return Boolean.TRUE.equals(isUrgent);
    }
    
    /**
     * 获取订单的关键审批信息
     */
    public ApprovalKeyInfo getApprovalKeyInfo() {
        return ApprovalKeyInfo.builder()
                .orderId(getOrderId())
                .orderCode(getOrderCode())
                .totalAmount(getTotalAmount())
                .orderType(getOrderType())
                .supplierId(getSupplierId())
                .isUrgent(isUrgent)
                .expectedDeliveryDate(getExpectedDeliveryDate())
                .build();
    }
    
    /**
     * 审批关键信息类
     * 用于存储审批流程所需的关键信息
     */
    @Data
    @Builder
    public static class ApprovalKeyInfo {
        private String orderId;
        private String orderCode;
        private BigDecimal totalAmount;
        private String orderType;
        private String supplierId;
        private Boolean isUrgent;
        private LocalDateTime expectedDeliveryDate;
    }
    
    /**
     * 获取共享模型实例
     * 用于与其他系统组件集成
     */
    public PurchaseOrder getCommonModel() {
        return commonModel;
    }
    
    /**
     * 设置共享模型实例
     * 用于与其他系统组件集成
     */
    public void setCommonModel(PurchaseOrder commonModel) {
        this.commonModel = commonModel;
    }
    
    /**
     * 注意：当前实现为适配层
     * 后续应直接使用共享模块中的PurchaseOrder模型
     * 此适配器将在所有系统组件迁移完成后被移除
     */
}