package com.bone.procurement.engine.model;

import com.bone.smartmeta.annotation.SmartEntity;
import com.bone.smartmeta.annotation.SmartField;
import com.bone.smartmeta.enums.FieldType;
import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单明细类
 * 表示采购订单中的具体商品或服务项
 */
@Data
@Builder
@SmartEntity(
    apiName = "PurchaseOrderItem",
    label = "采购订单项",
    description = "采购订单的明细项，包含产品、数量、单价等信息",
    category = "采购管理",
    supportMultiTenancy = true,
    auditEnabled = true,
    versionEnabled = true
)
public class PurchaseOrderItem {
    
    // 基础信息
    @SmartField(label = "明细ID", fieldType = FieldType.TEXT, required = true, unique = true, readOnly = true)
    private String itemId;              // 明细ID
    
    @SmartField(label = "关联的订单ID", fieldType = FieldType.REFERENCE, targetEntity = "PurchaseOrder", required = true, indexed = true)
    private String orderId;             // 关联的订单ID
    
    @SmartField(label = "行号", fieldType = FieldType.INTEGER, required = true)
    private int itemNumber;             // 行号
    
    @SmartField(label = "产品ID", fieldType = FieldType.REFERENCE, targetEntity = "Product", required = true, indexed = true)
    private String productId;           // 产品ID
    
    @SmartField(label = "产品编码", fieldType = FieldType.TEXT)
    private String productCode;         // 产品编码
    
    @SmartField(label = "产品名称", fieldType = FieldType.TEXT)
    private String productName;         // 产品名称
    
    @SmartField(label = "规格型号", fieldType = FieldType.TEXT)
    private String specification;       // 规格型号
    
    @SmartField(label = "单位", fieldType = FieldType.TEXT, required = true)
    private String unit;                // 单位
    
    @SmartField(label = "数量", fieldType = FieldType.DECIMAL, required = true, minValue = 0)
    private BigDecimal quantity;        // 数量
    
    @SmartField(label = "单价", fieldType = FieldType.CURRENCY, required = true, minValue = 0)
    private BigDecimal unitPrice;       // 单价
    
    @SmartField(label = "总价", fieldType = FieldType.CURRENCY, readOnly = true, calculationExpression = "quantity * unitPrice")
    private BigDecimal totalPrice;      // 总价
    
    // 金额信息
    @SmartField(label = "税额", fieldType = FieldType.CURRENCY, readOnly = true)
    private BigDecimal taxAmount;       // 税额
    
    @SmartField(label = "不含税单价", fieldType = FieldType.CURRENCY, readOnly = true)
    private BigDecimal priceWithoutTax; // 不含税单价
    
    @SmartField(label = "税率", fieldType = FieldType.PERCENTAGE)
    private String taxRate;             // 税率
    
    @SmartField(label = "币种", fieldType = FieldType.TEXT, defaultValue = "CNY")
    private String currency;            // 币种
    
    // 时间信息
    @SmartField(label = "期望交货日期", fieldType = FieldType.DATE, required = true)
    private LocalDateTime expectedDeliveryDate; // 期望交货日期
    
    @SmartField(label = "实际交货日期", fieldType = FieldType.DATE)
    private LocalDateTime actualDeliveryDate;   // 实际交货日期
    
    @SmartField(label = "创建时间", fieldType = FieldType.DATETIME, readOnly = true, autoFill = true)
    private LocalDateTime createTime;          // 创建时间
    
    @SmartField(label = "更新时间", fieldType = FieldType.DATETIME, readOnly = true, autoFill = true)
    private LocalDateTime updateTime;          // 更新时间
    
    // 状态信息
    @SmartField(label = "明细状态", fieldType = FieldType.ENUMERATION, required = true)
    private String status;               // 明细状态
    
    @SmartField(label = "交货状态", fieldType = FieldType.ENUMERATION)
    private String deliveryStatus;       // 交货状态
    
    @SmartField(label = "质量状态", fieldType = FieldType.ENUMERATION)
    private String qualityStatus;        // 质量状态
    
    // 项目信息
    @SmartField(label = "项目ID", fieldType = FieldType.REFERENCE, targetEntity = "Project")
    private String projectId;            // 项目ID
    
    @SmartField(label = "项目名称", fieldType = FieldType.TEXT)
    private String projectName;          // 项目名称
    
    @SmartField(label = "部门ID", fieldType = FieldType.REFERENCE, targetEntity = "Department")
    private String departmentId;         // 部门ID
    
    @SmartField(label = "部门名称", fieldType = FieldType.TEXT)
    private String departmentName;       // 部门名称
    
    @SmartField(label = "预算编码", fieldType = FieldType.TEXT)
    private String budgetCode;           // 预算编码
    
    // 供应商信息
    @SmartField(label = "供应商产品编码", fieldType = FieldType.TEXT)
    private String supplierProductCode;  // 供应商产品编码
    
    // 扩展信息
    @SmartField(label = "描述", fieldType = FieldType.TEXTAREA)
    private String description;          // 描述
    
    @SmartField(label = "扩展字段1", fieldType = FieldType.TEXT)
    private String extendField1;         // 扩展字段1
    
    @SmartField(label = "扩展字段2", fieldType = FieldType.TEXT)
    private String extendField2;         // 扩展字段2
    
    /**
     * 计算总价
     */
    public void calculateTotalPrice() {
        if (quantity != null && unitPrice != null) {
            this.totalPrice = quantity.multiply(unitPrice);
        }
    }
    
    /**
     * 计算不含税单价
     */
    public void calculatePriceWithoutTax() {
        if (unitPrice != null && taxRate != null) {
            try {
                BigDecimal rate = new BigDecimal(taxRate);
                this.priceWithoutTax = unitPrice.divide(rate.add(BigDecimal.ONE), 4, BigDecimal.ROUND_HALF_UP);
            } catch (Exception e) {
                // 税率格式错误，默认不做计算
            }
        }
    }
    
    /**
     * 计算税额
     */
    public void calculateTaxAmount() {
        if (totalPrice != null && taxRate != null) {
            try {
                BigDecimal rate = new BigDecimal(taxRate);
                this.taxAmount = totalPrice.subtract(totalPrice.divide(rate.add(BigDecimal.ONE), 4, BigDecimal.ROUND_HALF_UP));
            } catch (Exception e) {
                // 税率格式错误，默认不做计算
            }
        }
    }
    
    /**
     * 检查是否为有效订单项
     */
    public boolean isValid() {
        return productId != null && !productId.isEmpty() &&
               quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0 &&
               unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) >= 0;
    }
}