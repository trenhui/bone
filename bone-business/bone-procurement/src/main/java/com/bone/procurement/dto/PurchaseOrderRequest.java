package com.bone.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单请求DTO
 */
public class PurchaseOrderRequest {
    private String orderTitle;
    private BigDecimal totalAmount;
    private String vendorId;
    private String departmentId;
    private LocalDate needByDate;
    private String description;
    private String priority;
    
    // Getter and Setter methods
    public String getOrderTitle() { return orderTitle; }
    public void setOrderTitle(String orderTitle) { this.orderTitle = orderTitle; }
    
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    
    public String getVendorId() { return vendorId; }
    public void setVendorId(String vendorId) { this.vendorId = vendorId; }
    
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    
    public LocalDate getNeedByDate() { return needByDate; }
    public void setNeedByDate(LocalDate needByDate) { this.needByDate = needByDate; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}