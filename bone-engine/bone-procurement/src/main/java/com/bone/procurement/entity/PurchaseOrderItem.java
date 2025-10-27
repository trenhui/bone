package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import com.bone.procurement.common.model.PurchaseOrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 采购订单项适配器类
 * 适配共享模块中的PurchaseOrderItem模型，保持向后兼容性
 * 注意：此为适配层，最终应直接使用共享模块中的模型
 */
@SmartEntity(apiName = "PurchaseOrderItem", label = "采购订单项", description = "采购订单中包含的具体商品或服务明细")
public class PurchaseOrderItem {
    
    // 直接在类中定义所有需要的字段
    // 注：这是一个临时解决方案，后续应直接使用共享模块中的模型
    private Long id;
    private Long purchaseOrderId;
    private Long productId;
    private String productCode;
    private String productName;
    private String description;
    private Integer quantity;
    private String unit;
    private BigDecimal unitPrice;
    private Double taxRate;
    private BigDecimal amountWithoutTax;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String brand;
    private String specification;
    private String category;
    private String origin;
    private Long warehouseId;
    private Integer priorityLevel;
    private String remarks;
    
    @SmartField(name = "productCode", label = "产品编码", type = FieldType.TEXT, required = true, length = 50)
    public String getProductCode() {
        return productCode;
    }
    
    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }
    
    @SmartField(name = "productName", label = "产品名称", type = FieldType.TEXT, required = true, length = 200)
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    @SmartField(name = "description", label = "产品描述", type = FieldType.TEXT, length = 1000)
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @SmartField(name = "quantity", label = "数量", type = FieldType.NUMBER, required = true)
    @BusinessRule(name = "quantityRule", expression = "${quantity} > 0", errorMessage = "数量必须大于0")
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    @SmartField(name = "unit", label = "单位", type = FieldType.TEXT, required = true, length = 20)
    public String getUnit() {
        return unit;
    }
    
    public void setUnit(String unit) {
        this.unit = unit;
    }
    
    @SmartField(name = "unitPrice", label = "单价", type = FieldType.CURRENCY, required = true)
    @BusinessRule(name = "unitPriceRule", expression = "${unitPrice} != null && ${unitPrice}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "单价必须大于0")
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
    
    @SmartField(name = "taxRate", label = "税率", type = FieldType.PERCENT, defaultValue = "0.13")
    public Double getTaxRate() {
        return taxRate;
    }
    
    public void setTaxRate(Double taxRate) {
        this.taxRate = taxRate;
    }
    
    @SmartField(name = "amountWithoutTax", label = "不含税金额", type = FieldType.CURRENCY)
    public BigDecimal getAmountWithoutTax() {
        return amountWithoutTax;
    }
    
    public void setAmountWithoutTax(BigDecimal amountWithoutTax) {
        this.amountWithoutTax = amountWithoutTax;
    }
    
    @SmartField(name = "taxAmount", label = "税额", type = FieldType.CURRENCY)
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }
    
    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }
    
    @SmartField(name = "totalAmount", label = "含税总金额", type = FieldType.CURRENCY)
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    @SmartField(name = "brand", label = "品牌", type = FieldType.TEXT, length = 100)
    public String getBrand() {
        return brand;
    }
    
    public void setBrand(String brand) {
        this.brand = brand;
    }
    
    @SmartField(name = "specification", label = "规格型号", type = FieldType.TEXT, length = 200)
    public String getSpecification() {
        return specification;
    }
    
    public void setSpecification(String specification) {
        this.specification = specification;
    }
    
    @SmartField(name = "category", label = "产品类别", type = FieldType.TEXT, length = 100)
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    @SmartField(name = "origin", label = "产地", type = FieldType.TEXT, length = 100)
    public String getOrigin() {
        return origin;
    }
    
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    
    @SmartField(name = "warehouseId", label = "仓库ID", type = FieldType.NUMBER)
    public Long getWarehouseId() {
        return warehouseId;
    }
    
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }
    
    @SmartField(name = "priorityLevel", label = "优先级", type = FieldType.NUMBER)
    public Integer getPriorityLevel() {
        return priorityLevel;
    }
    
    public void setPriorityLevel(Integer priorityLevel) {
        this.priorityLevel = priorityLevel;
    }
    
    @SmartField(name = "remarks", label = "备注", type = FieldType.TEXT, length = 500)
    public String getRemarks() {
        return remarks;
    }
    
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
    
    @SmartField(name = "id", label = "ID", type = FieldType.NUMBER)
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    @SmartField(name = "purchaseOrderId", label = "采购订单ID", type = FieldType.NUMBER, required = true)
    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }
    
    public void setPurchaseOrderId(Long purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }
    
    @SmartField(name = "productId", label = "产品ID", type = FieldType.NUMBER, required = true)
    public Long getProductId() {
        return productId;
    }
    
    public void setProductId(Long productId) {
        this.productId = productId;
    }
    
    /**
     * 注意：当前实现暂时保留原有字段结构
     * 后续应直接使用共享模块中的PurchaseOrderItem模型
     * 并通过映射工具在两个模型之间进行转换
     */
}