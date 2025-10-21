package com.bone.procurement.engine.model;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.SmartRelationship;
import com.bone.smartmeta.engine.annotation.validation.Required;
import com.bone.smartmeta.engine.annotation.validation.Unique;
import com.bone.smartmeta.engine.annotation.validation.Range;
import com.bone.smartmeta.engine.enums.FieldType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单模型类
 * 基于元数据定义的采购订单实体，包含完整的业务字段
 */
@SmartEntity(
    apiName = "PurchaseOrder",
    label = "采购订单",
    description = "企业采购业务的核心实体",
    domain = "procurement",
    version = "3.5.0",
    auditEnabled = true,
    trackingLevel = "ALL",
    tenantIsolation = true,
    tenantField = "tenantId"
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {
    
    // 基础信息
    @SmartField(label = "订单ID", required = true, unique = true, fieldType = FieldType.TEXT, readOnly = true)
    private String orderId;              // 订单ID
    
    @SmartField(label = "订单编号", required = true, unique = true, fieldType = FieldType.TEXT, indexed = true, description = "业务主键")
    private String orderCode;            // 订单编号
    
    @SmartField(label = "订单类型", required = true, fieldType = FieldType.TEXT, defaultValue = "常规采购", description = "通过元数据驱动不同采购类型的差异化流程")
    private String orderType;            // 订单类型
    
    @SmartField(label = "订单来源", fieldType = FieldType.TEXT)
    private String source;               // 订单来源
    
    @SmartField(label = "申请单ID", fieldType = FieldType.TEXT)
    private String requestId;            // 关联的申请单ID
    
    @SmartField(label = "业务类型", fieldType = FieldType.TEXT)
    private String businessType;         // 业务类型
    
    @SmartField(label = "业务域", fieldType = FieldType.TEXT)
    private String businessDomain;       // 业务域
    
    @SmartField(label = "描述", fieldType = FieldType.TEXT)
    private String description;          // 描述
    
    // 供应商信息
    @SmartField(label = "供应商ID", required = true, fieldType = FieldType.TEXT, description = "关联供应商表")
    private String supplierId;           // 供应商ID
    
    @SmartField(label = "供应商名称", fieldType = FieldType.TEXT)
    private String supplierName;         // 供应商名称
    
    @SmartField(label = "供应商联系人", fieldType = FieldType.TEXT)
    private String supplierContact;      // 供应商联系人
    
    @SmartField(label = "供应商联系电话", fieldType = FieldType.TEXT)
    private String supplierPhone;        // 供应商联系电话
    
    // 金额信息
    @SmartField(label = "总金额", fieldType = FieldType.CURRENCY, precision = 10, scale = 2, calculationExpression = "SUM(orderItems.totalPrice)", description = "通过规则引擎计算得出")
    private BigDecimal totalAmount;      // 总金额
    
    @SmartField(label = "税额", fieldType = FieldType.CURRENCY, precision = 10, scale = 2, calculationExpression = "SUM(orderItems.taxAmount)")
    private BigDecimal taxAmount;        // 税额
    
    @SmartField(label = "不含税金额", fieldType = FieldType.CURRENCY, precision = 10, scale = 2, calculationExpression = "totalAmount - taxAmount")
    private BigDecimal totalWithoutTax;  // 不含税金额
    
    @SmartField(label = "币种", fieldType = FieldType.TEXT, defaultValue = "CNY")
    private String currency;             // 币种
    
    @SmartField(label = "税率", fieldType = FieldType.PERCENT, precision = 5, scale = 2)
    private String taxRate;              // 税率
    
    // 时间信息
    @SmartField(label = "期望交货日期", fieldType = FieldType.DATE)
    private LocalDateTime expectedDeliveryDate; // 期望交货日期
    
    @SmartField(label = "实际交货日期", fieldType = FieldType.DATE)
    private LocalDateTime actualDeliveryDate;   // 实际交货日期
    
    @SmartField(label = "下单日期", fieldType = FieldType.DATE, required = true, defaultValueExpression = "${now().toLocalDate()}", indexed = true)
    private LocalDateTime orderDate;           // 下单日期
    
    @SmartField(label = "审批日期", fieldType = FieldType.DATE_TIME, trackHistory = true)
    private LocalDateTime approvalDate;        // 审批日期
    
    @SmartField(label = "创建时间", fieldType = FieldType.DATE_TIME, readOnly = true)
    private LocalDateTime createTime;          // 创建时间
    
    @SmartField(label = "更新时间", fieldType = FieldType.DATE_TIME, readOnly = true)
    private LocalDateTime updateTime;          // 更新时间
    
    // 状态信息
    @SmartField(label = "订单状态", fieldType = FieldType.TEXT, required = true, defaultValue = "DRAFT", indexed = true, description = "定义状态流转规则，确保业务流程合规")
    private String status;               // 订单状态
    
    @SmartField(label = "审批状态", fieldType = FieldType.TEXT, required = true, defaultValue = "PENDING", indexed = true)
    private String approvalStatus;       // 审批状态
    
    @SmartField(label = "付款状态", fieldType = FieldType.TEXT, defaultValue = "UNPAID")
    private String paymentStatus;        // 付款状态
    
    @SmartField(label = "交货状态", fieldType = FieldType.TEXT, defaultValue = "PENDING")
    private String deliveryStatus;       // 交货状态
    
    @SmartField(label = "是否紧急", fieldType = FieldType.BOOLEAN, defaultValue = "false")
    private Boolean isUrgent;            // 是否紧急
    
    // 人员信息
    @SmartField(label = "创建人ID", fieldType = FieldType.REFERENCE, targetEntity = "Base_User")
    private String createdBy;            // 创建人ID
    
    @SmartField(label = "创建人名称", fieldType = FieldType.TEXT)
    private String createdByName;        // 创建人名称
    
    @SmartField(label = "更新人ID", fieldType = FieldType.REFERENCE, targetEntity = "Base_User")
    private String updatedBy;            // 更新人ID
    
    @SmartField(label = "更新人名称", fieldType = FieldType.TEXT)
    private String updatedByName;        // 更新人名称
    
    @SmartField(label = "审批人", fieldType = FieldType.REFERENCE, targetEntity = "Base_User", indexed = true, trackHistory = true)
    private String approverId;           // 审批人ID
    
    @SmartField(label = "审批人名称", fieldType = FieldType.TEXT)
    private String approverName;         // 审批人名称
    
    // 关联信息
    @SmartField(label = "项目ID", fieldType = FieldType.REFERENCE, targetEntity = "Project", indexed = true, trackHistory = true)
    private String projectId;            // 项目ID
    
    @SmartField(label = "项目名称", fieldType = FieldType.TEXT)
    private String projectName;          // 项目名称
    
    @SmartField(label = "部门ID", fieldType = FieldType.REFERENCE, targetEntity = "Department", required = true, indexed = true, trackHistory = true)
    private String departmentId;         // 部门ID
    
    @SmartField(label = "部门名称", fieldType = FieldType.TEXT)
    private String departmentName;       // 部门名称
    
    @SmartField(label = "预算编码", fieldType = FieldType.TEXT)
    private String budgetCode;           // 预算编码
    
    // 多租户隔离字段
    @SmartField(label = "租户ID", fieldType = FieldType.TEXT, required = true, readOnly = true)
    private String tenantId;             // 租户ID
    
    // 一对多关系：订单明细
    @SmartRelationship(
        relationshipName = "orderItems",
        relationshipType = "ONE_TO_MANY",
        targetEntity = "PurchaseOrderItem",
        sourceField = "orderId",
        targetField = "orderId",
        cascadeType = {"ALL"}
    )
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