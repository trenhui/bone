package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单项目实体类
 * 演示bone-smartmeta在复杂业务场景中的应用，包括动态计算和业务规则验证
 */
@Data
@SmartEntity(apiName = "PurchaseOrderItem", label = "采购订单项目", description = "采购订单中的具体商品或服务项")
public class PurchaseOrderItem {
    
    private Long id;
    
    private Long purchaseOrderId;
    
    @SmartField(name = "materialCode", label = "物料编码", type = FieldType.TEXT, required = true, length = 50)
    private String materialCode;
    
    @SmartField(name = "materialName", label = "物料名称", type = FieldType.TEXT, required = true, length = 200)
    private String materialName;
    
    @SmartField(name = "specification", label = "规格型号", type = FieldType.TEXT, length = 200)
    private String specification;
    
    @SmartField(name = "unit", label = "单位", type = FieldType.TEXT, required = true, length = 20)
    private String unit;
    
    @SmartField(name = "quantity", label = "数量", type = FieldType.NUMBER, required = true)
    @BusinessRule(expression = "${quantity} > 0", errorMessage = "数量必须大于0")
    private Integer quantity;
    
    @SmartField(name = "unitPrice", label = "单价", type = FieldType.CURRENCY, required = true)
    @BusinessRule(expression = "${unitPrice}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "单价必须大于0")
    private BigDecimal unitPrice;
    
    @SmartField(name = "taxRate", label = "税率", type = FieldType.PERCENT, defaultValue = "0.13")
    private Double taxRate;
    
    @SmartField(name = "requestingDepartment", label = "需求部门", type = FieldType.TEXT, length = 100)
    private String requestingDepartment;
    
    @SmartField(name = "usage", label = "项目用途", type = FieldType.TEXT, length = 500)
    private String usage;
    
    @SmartField(name = "remarks", label = "备注", type = FieldType.TEXT, length = 500)
    private String remarks;
    
    // 计算字段：项目金额（不含税）
    @SmartField(name = "amountWithoutTax", label = "项目金额（不含税）", type = FieldType.CURRENCY)
    private BigDecimal amountWithoutTax;
    
    // 计算字段：项目税额
    @SmartField(name = "taxAmount", label = "项目税额", type = FieldType.CURRENCY)
    private BigDecimal taxAmount;
    
    // 计算字段：项目总金额（含税）
    @SmartField(name = "totalAmount", label = "项目总金额（含税）", type = FieldType.CURRENCY)
    private BigDecimal totalAmount;
    
    // 虚拟字段：项目描述
    @SmartField(name = "itemDescription", label = "项目描述", type = FieldType.TEXT, virtual = true)
    private String itemDescription;
    
    // 虚拟字段：是否大额项目
    @SmartField(name = "isLargeAmountItem", label = "是否大额项目", type = FieldType.BOOLEAN, virtual = true)
    private Boolean isLargeAmountItem;
}