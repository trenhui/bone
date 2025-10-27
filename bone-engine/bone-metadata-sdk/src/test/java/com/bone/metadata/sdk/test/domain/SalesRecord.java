package com.bone.metadata.sdk.test.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简化的销售记录类
 * 移除了所有外部依赖
 */
public class SalesRecord {
    
    private Long id;
    private String category;
    private BigDecimal amount;
    private BigDecimal price;
    private String status;
    private LocalDateTime createTime;
    private String region;
    private String productName;
    private Integer quantity;
    private Boolean isDeleted;
    
    // 简单的构造器
    public SalesRecord() {
    }
    
    public SalesRecord(Long id, String category, BigDecimal amount, BigDecimal price) {
        this.id = id;
        this.category = category;
        this.amount = amount;
        this.price = price;
    }
    
    // 简单的getter和setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public Boolean getIsDeleted() {
        return isDeleted;
    }
    
    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
}