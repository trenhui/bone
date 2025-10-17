package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;

import java.math.BigDecimal;

/**
 * 采购订单项实体类
 * 表示采购订单中的具体商品或服务项
 */
@SmartEntity(apiName = "PurchaseOrderItem", label = "采购订单项", description = "采购订单中包含的具体商品或服务明细")
public class PurchaseOrderItem {
    
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
    private Double taxRate;
    
    @SmartField(name = "amountWithoutTax", label = "不含税金额", type = FieldType.CURRENCY)
    private BigDecimal amountWithoutTax;
    
    @SmartField(name = "taxAmount", label = "税额", type = FieldType.CURRENCY)
    private BigDecimal taxAmount;
    
    @SmartField(name = "totalAmount", label = "含税总金额", type = FieldType.CURRENCY)
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
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    
    public Double getTaxRate() { return taxRate; }
    public void setTaxRate(Double taxRate) { this.taxRate = taxRate; }
    
    public BigDecimal getAmountWithoutTax() { return amountWithoutTax; }
    public void setAmountWithoutTax(BigDecimal amountWithoutTax) { this.amountWithoutTax = amountWithoutTax; }
    
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    
    public Integer getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(Integer priorityLevel) { this.priorityLevel = priorityLevel; }
    
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}