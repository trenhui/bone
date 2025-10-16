package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购订单项目实体类
 * 作为采购订单的子项，展示复杂对象关系和字段计算功能
 */
@Data
@SmartEntity(displayName = "采购订单项目")
public class PurchaseOrderItem {
    
    private Long id;
    
    @SmartField(displayName = "物料编码", required = true, maxLength = 50)
    private String materialCode;
    
    @SmartField(displayName = "物料名称", required = true, maxLength = 200)
    private String materialName;
    
    @SmartField(displayName = "规格型号", maxLength = 200)
    private String specification;
    
    @SmartField(displayName = "单位", required = true, maxLength = 20)
    private String unit;
    
    @SmartField(displayName = "数量", required = true)
    private Integer quantity;
    
    @SmartField(displayName = "单价", required = true)
    private BigDecimal unitPrice;
    
    @SmartField(displayName = "税率", defaultValue = "0.13")
    private Double taxRate;
    
    @SmartField(displayName = "需求部门", maxLength = 100)
    private String requestingDepartment;
    
    @SmartField(displayName = "项目用途", maxLength = 500)
    private String usage;
    
    @SmartField(displayName = "备注", maxLength = 500)
    private String remarks;
    
    // 计算字段：项目金额（不含税）
    @SmartField(displayName = "项目金额（不含税）", calculated = true,
                calculationExpression = "${unitPrice}.multiply(java.math.BigDecimal.valueOf(${quantity}))")
    private BigDecimal amountWithoutTax;
    
    // 计算字段：项目税额
    @SmartField(displayName = "项目税额", calculated = true,
                calculationExpression = "${amountWithoutTax}.multiply(java.math.BigDecimal.valueOf(${taxRate}))")
    private BigDecimal taxAmount;
    
    // 计算字段：项目总金额（含税）
    @SmartField(displayName = "项目总金额（含税）", calculated = true,
                calculationExpression = "${amountWithoutTax}.add(${taxAmount})")
    private BigDecimal totalAmount;
    
    // 业务规则验证
    @BusinessRule(expression = "${quantity} > 0", message = "采购数量必须大于0")
    @BusinessRule(expression = "${unitPrice}.compareTo(java.math.BigDecimal.ZERO) > 0", message = "单价必须大于0")
    
    // 虚拟字段：项目描述
    @SmartField(displayName = "项目描述", virtual = true,
                expression = "${materialName} (${specification}) - ${quantity}${unit} - ${totalAmount}元")
    private String itemDescription;
    
    // 虚拟字段：是否大额项目
    @SmartField(displayName = "是否大额项目", virtual = true,
                expression = "${totalAmount}.compareTo(java.math.BigDecimal.valueOf(10000)) > 0")
    private Boolean isLargeAmountItem;
}