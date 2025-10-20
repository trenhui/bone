package com.bone.procurement.engine.model;

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
public class PurchaseOrderItem {
    
    // 基础信息
    private String itemId;              // 明细ID
    private String orderId;             // 关联的订单ID
    private int itemNumber;             // 行号
    private String productId;           // 产品ID
    private String productCode;         // 产品编码
    private String productName;         // 产品名称
    private String specification;       // 规格型号
    private String unit;                // 单位
    private BigDecimal quantity;        // 数量
    private BigDecimal unitPrice;       // 单价
    private BigDecimal totalPrice;      // 总价
    
    // 金额信息
    private BigDecimal taxAmount;       // 税额
    private BigDecimal priceWithoutTax; // 不含税单价
    private String taxRate;             // 税率
    private String currency;            // 币种
    
    // 时间信息
    private LocalDateTime expectedDeliveryDate; // 期望交货日期
    private LocalDateTime actualDeliveryDate;   // 实际交货日期
    private LocalDateTime createTime;          // 创建时间
    private LocalDateTime updateTime;          // 更新时间
    
    // 状态信息
    private String status;               // 明细状态
    private String deliveryStatus;       // 交货状态
    private String qualityStatus;        // 质量状态
    
    // 项目信息
    private String projectId;            // 项目ID
    private String projectName;          // 项目名称
    private String departmentId;         // 部门ID
    private String departmentName;       // 部门名称
    private String budgetCode;           // 预算编码
    
    // 供应商信息
    private String supplierProductCode;  // 供应商产品编码
    
    // 扩展信息
    private String description;          // 描述
    private String extendField1;         // 扩展字段1
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