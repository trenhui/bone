package com.bone.procurement.engine.model;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单模型类
 * 基于元数据定义的采购订单实体，包含完整的业务字段
 */
@Data
@Builder
public class PurchaseOrder {
    
    // 基础信息
    private String orderId;              // 订单ID
    private String orderCode;            // 订单编号
    private String orderType;            // 订单类型
    private String source;               // 订单来源
    private String requestId;            // 关联的申请单ID
    private String businessType;         // 业务类型
    private String businessDomain;       // 业务域
    private String description;          // 描述
    
    // 供应商信息
    private String supplierId;           // 供应商ID
    private String supplierName;         // 供应商名称
    private String supplierContact;      // 供应商联系人
    private String supplierPhone;        // 供应商联系电话
    
    // 金额信息
    private BigDecimal totalAmount;      // 总金额
    private BigDecimal taxAmount;        // 税额
    private BigDecimal totalWithoutTax;  // 不含税金额
    private String currency;             // 币种
    private String taxRate;              // 税率
    
    // 时间信息
    private LocalDateTime expectedDeliveryDate; // 期望交货日期
    private LocalDateTime actualDeliveryDate;   // 实际交货日期
    private LocalDateTime orderDate;           // 下单日期
    private LocalDateTime approvalDate;        // 审批日期
    private LocalDateTime createTime;          // 创建时间
    private LocalDateTime updateTime;          // 更新时间
    
    // 状态信息
    private String status;               // 订单状态
    private String approvalStatus;       // 审批状态
    private String paymentStatus;        // 付款状态
    private String deliveryStatus;       // 交货状态
    private Boolean isUrgent;            // 是否紧急
    
    // 人员信息
    private String createdBy;            // 创建人ID
    private String createdByName;        // 创建人名称
    private String updatedBy;            // 更新人ID
    private String updatedByName;        // 更新人名称
    private String approverId;           // 审批人ID
    private String approverName;         // 审批人名称
    
    // 关联信息
    private String projectId;            // 项目ID
    private String projectName;          // 项目名称
    private String departmentId;         // 部门ID
    private String departmentName;       // 部门名称
    private String budgetCode;           // 预算编码
    
    // 一对多关系：订单明细
    private List<PurchaseOrderItem> orderItems; // 订单明细列表
    
    // 扩展字段
    private String extendField1;         // 扩展字段1
    private String extendField2;         // 扩展字段2
    private String extendField3;         // 扩展字段3
    
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
            this.totalAmount = total;
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
                .orderId(orderId)
                .orderCode(orderCode)
                .totalAmount(totalAmount)
                .orderType(orderType)
                .supplierId(supplierId)
                .isUrgent(isUrgent)
                .expectedDeliveryDate(expectedDeliveryDate)
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
}