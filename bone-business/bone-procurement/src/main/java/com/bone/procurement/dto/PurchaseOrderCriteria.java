package com.bone.procurement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单查询条件DTO
 */
public class PurchaseOrderCriteria {
    private List<String> statuses;
    private String departmentId;
    private String vendorId;
    private LocalDate needByDateFrom;
    private LocalDate needByDateTo;
    private LocalDateTime createdDateFrom;
    private LocalDateTime createdDateTo;
    private String createdBy;
    private Boolean isHighValueOrder;
    private Integer page;
    private Integer pageSize;
    // Additional fields needed by PurchaseOrderService
    private java.math.BigDecimal minAmount;
    private java.math.BigDecimal maxAmount;
    private java.time.LocalDateTime startDate;
    private java.time.LocalDateTime endDate;
    private Boolean isHighValue;
    
    // Getter and Setter methods
    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }
    
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    
    public String getVendorId() { return vendorId; }
    public void setVendorId(String vendorId) { this.vendorId = vendorId; }
    
    public LocalDate getNeedByDateFrom() { return needByDateFrom; }
    public void setNeedByDateFrom(LocalDate needByDateFrom) { this.needByDateFrom = needByDateFrom; }
    
    public LocalDate getNeedByDateTo() { return needByDateTo; }
    public void setNeedByDateTo(LocalDate needByDateTo) { this.needByDateTo = needByDateTo; }
    
    public LocalDateTime getCreatedDateFrom() { return createdDateFrom; }
    public void setCreatedDateFrom(LocalDateTime createdDateFrom) { this.createdDateFrom = createdDateFrom; }
    
    public LocalDateTime getCreatedDateTo() { return createdDateTo; }
    public void setCreatedDateTo(LocalDateTime createdDateTo) { this.createdDateTo = createdDateTo; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public Boolean getIsHighValueOrder() { return isHighValueOrder; }
    public void setIsHighValueOrder(Boolean isHighValueOrder) { this.isHighValueOrder = isHighValueOrder; }
    
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    
    // Additional getter and setter methods for fields used in PurchaseOrderService
    public java.math.BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(java.math.BigDecimal minAmount) { this.minAmount = minAmount; }
    
    public java.math.BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(java.math.BigDecimal maxAmount) { this.maxAmount = maxAmount; }
    
    public java.time.LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(java.time.LocalDateTime startDate) { this.startDate = startDate; }
    
    public java.time.LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(java.time.LocalDateTime endDate) { this.endDate = endDate; }
    
    public Boolean getIsHighValue() { return isHighValue; }
    public void setIsHighValue(Boolean isHighValue) { this.isHighValue = isHighValue; }
}