package com.bone.procurement.engine.model;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    trackHistory = true
)
public class PurchaseOrderItem {
    
    // 基础信息
    @SmartField(name = "itemId", label = "明细ID", type = SmartField.FieldType.TEXT, required = true, unique = true)
    private String itemId;              // 明细ID
    
    @SmartField(name = "orderId", label = "关联的订单ID", type = SmartField.FieldType.TEXT, referenceTo = "PurchaseOrder", required = true, indexed = true)
    private String orderId;             // 关联的订单ID
    
    @SmartField(name = "itemNumber", label = "行号", type = SmartField.FieldType.NUMBER, required = true)
    private int itemNumber;             // 行号
    
    @SmartField(name = "productId", label = "产品ID", type = SmartField.FieldType.TEXT, referenceTo = "Product", required = true, indexed = true)
    private String productId;           // 产品ID
    
    @SmartField(name = "productCode", label = "产品编码", type = SmartField.FieldType.TEXT)
    private String productCode;         // 产品编码
    
    @SmartField(name = "productName", label = "产品名称", type = SmartField.FieldType.TEXT)
    private String productName;         // 产品名称
    
    @SmartField(name = "specification", label = "规格型号", type = SmartField.FieldType.TEXT)
    private String specification;       // 规格型号
    
    @SmartField(name = "unit", label = "单位", type = SmartField.FieldType.TEXT, required = true)
    private String unit;                // 单位
    
    @SmartField(name = "quantity", label = "数量", type = SmartField.FieldType.NUMBER, precision = 10, scale = 2, required = true)
    private BigDecimal quantity;        // 数量
    
    @SmartField(name = "unitPrice", label = "单价", type = SmartField.FieldType.NUMBER, precision = 10, scale = 2, required = true)
    private BigDecimal unitPrice;       // 单价
    
    @SmartField(name = "totalPrice", label = "总价", type = SmartField.FieldType.NUMBER, precision = 10, scale = 2, description = "quantity * unitPrice")
    private BigDecimal totalPrice;      // 总价
    
    // 金额信息
    @SmartField(name = "taxAmount", label = "税额", type = SmartField.FieldType.NUMBER, precision = 10, scale = 2)
    private BigDecimal taxAmount;       // 税额
    
    @SmartField(name = "priceWithoutTax", label = "不含税单价", type = SmartField.FieldType.NUMBER, precision = 10, scale = 2)
    private BigDecimal priceWithoutTax; // 不含税单价
    
    @SmartField(name = "taxRate", label = "税率", type = SmartField.FieldType.TEXT)
    private String taxRate;             // 税率
    
    @SmartField(name = "currency", label = "币种", type = SmartField.FieldType.TEXT, defaultValue = "CNY")
    private String currency;            // 币种
    
    // 时间信息
    @SmartField(name = "expectedDeliveryDate", label = "期望交货日期", type = SmartField.FieldType.DATE, required = true)
    private LocalDateTime expectedDeliveryDate; // 期望交货日期
    
    @SmartField(name = "actualDeliveryDate", label = "实际交货日期", type = SmartField.FieldType.DATE)
    private LocalDateTime actualDeliveryDate;   // 实际交货日期
    
    @SmartField(name = "createTime", label = "创建时间", type = SmartField.FieldType.DATE_TIME)
    private LocalDateTime createTime;          // 创建时间
    
    @SmartField(name = "updateTime", label = "更新时间", type = SmartField.FieldType.DATE_TIME)
    private LocalDateTime updateTime;          // 更新时间
    
    // 状态信息
    @SmartField(name = "status", label = "明细状态", type = SmartField.FieldType.TEXT, required = true)
    private String status;               // 明细状态
    
    @SmartField(name = "deliveryStatus", label = "交货状态", type = SmartField.FieldType.TEXT)
    private String deliveryStatus;       // 交货状态
    
    @SmartField(name = "qualityStatus", label = "质量状态", type = SmartField.FieldType.TEXT)
    private String qualityStatus;        // 质量状态
    
    // 项目信息
    @SmartField(name = "projectId", label = "项目ID", type = SmartField.FieldType.TEXT, referenceTo = "Project")
    private String projectId;            // 项目ID
    
    @SmartField(name = "projectName", label = "项目名称", type = SmartField.FieldType.TEXT)
    private String projectName;          // 项目名称
    
    @SmartField(name = "departmentId", label = "部门ID", type = SmartField.FieldType.TEXT, referenceTo = "Department")
    private String departmentId;         // 部门ID
    
    @SmartField(name = "departmentName", label = "部门名称", type = SmartField.FieldType.TEXT)
    private String departmentName;       // 部门名称
    
    @SmartField(name = "budgetCode", label = "预算编码", type = SmartField.FieldType.TEXT)
    private String budgetCode;           // 预算编码
    
    // 供应商信息
    @SmartField(name = "supplierProductCode", label = "供应商产品编码", type = SmartField.FieldType.TEXT)
    private String supplierProductCode;  // 供应商产品编码
    
    // 扩展信息
    @SmartField(name = "description", label = "描述", type = SmartField.FieldType.TEXT)
    private String description;          // 描述
    
    @SmartField(name = "extendField1", label = "扩展字段1", type = SmartField.FieldType.TEXT)
    private String extendField1;         // 扩展字段1
    
    @SmartField(name = "extendField2", label = "扩展字段2", type = SmartField.FieldType.TEXT)
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
                this.priceWithoutTax = unitPrice.divide(rate.add(BigDecimal.ONE), 4, RoundingMode.HALF_UP);
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
                this.taxAmount = totalPrice.subtract(totalPrice.divide(rate.add(BigDecimal.ONE), 4, RoundingMode.HALF_UP));
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