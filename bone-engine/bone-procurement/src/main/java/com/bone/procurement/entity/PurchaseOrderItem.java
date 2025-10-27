package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购订单项类
 */
@SmartEntity(apiName = "PurchaseOrderItem", label = "采购订单项", description = "采购订单中包含的具体商品或服务明细")
@Data
public class PurchaseOrderItem {
    
    @SmartField(name = "id", label = "ID", type = FieldType.NUMBER)
    private Long id;
    
    @SmartField(name = "purchaseOrderId", label = "采购订单ID", type = FieldType.NUMBER, required = true)
    private Long purchaseOrderId;
    
    @SmartField(name = "productId", label = "产品ID", type = FieldType.NUMBER, required = true)
    private Long productId;
    
    @SmartField(name = "productCode", label = "产品编码", type = FieldType.TEXT, required = true, length = 50)
    private String productCode;
    
    @SmartField(name = "productName", label = "产品名称", type = FieldType.TEXT, required = true, length = 200)
    private String productName;
    
    @SmartField(name = "description", label = "产品描述", type = FieldType.TEXT, length = 1000)
    private String description;
    
    @SmartField(name = "quantity", label = "数量", type = FieldType.NUMBER, required = true)
    @BusinessRule(name = "quantityRule", expression = "${quantity} > 0", errorMessage = "数量必须大于0")
    private Integer quantity;
    
    @SmartField(name = "unit", label = "单位", type = FieldType.TEXT, required = true, length = 20)
    private String unit;
    
    @SmartField(name = "unitPrice", label = "单价", type = FieldType.CURRENCY, required = true)
    @BusinessRule(name = "unitPriceRule", expression = "${unitPrice} != null && ${unitPrice}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "单价必须大于0")
    private BigDecimal unitPrice;
    
    @SmartField(name = "taxRate", label = "税率", type = FieldType.PERCENT, defaultValue = "0.13")
    private Double taxRate = 0.13;
    
    @SmartField(name = "amountWithoutTax", label = "不含税金额", type = FieldType.CURRENCY, 
               calculationExpression = "${quantity != null && unitPrice != null ? (new java.math.BigDecimal(quantity).multiply(unitPrice)).setScale(2, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO}",
               calculationDependencies = {"quantity", "unitPrice"},
               virtual = true)
    private BigDecimal amountWithoutTax;
    
    @SmartField(name = "taxAmount", label = "税额", type = FieldType.CURRENCY,
               calculationExpression = "${amountWithoutTax != null && taxRate != null ? amountWithoutTax.multiply(new java.math.BigDecimal(taxRate)).setScale(2, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO}",
               calculationDependencies = {"amountWithoutTax", "taxRate"},
               virtual = true)
    private BigDecimal taxAmount;
    
    @SmartField(name = "totalAmount", label = "含税总金额", type = FieldType.CURRENCY,
               calculationExpression = "${amountWithoutTax != null && taxAmount != null ? amountWithoutTax.add(taxAmount).setScale(2, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO}",
               calculationDependencies = {"amountWithoutTax", "taxAmount"},
               virtual = true)
    private BigDecimal totalAmount;
    
    @SmartField(name = "brand", label = "品牌", type = FieldType.TEXT, length = 100)
    private String brand;
    
    @SmartField(name = "specification", label = "规格型号", type = FieldType.TEXT, length = 200)
    private String specification;
    
    @SmartField(name = "category", label = "产品类别", type = FieldType.TEXT, length = 100)
    private String category;
    
    @SmartField(name = "origin", label = "产地", type = FieldType.TEXT, length = 100)
    private String origin;
    
    @SmartField(name = "warehouseId", label = "仓库ID", type = FieldType.NUMBER)
    private Long warehouseId;
    
    @SmartField(name = "priorityLevel", label = "优先级", type = FieldType.NUMBER)
    private Integer priorityLevel;
    
    @SmartField(name = "remarks", label = "备注", type = FieldType.TEXT, length = 500)
    private String remarks;
}