package com.bone.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 智能采购订单响应DTO
 * 集成AI增强功能和动态字段支持
 */
@Data
public class PurchaseOrderResponse {
    private String id;
    private String name;
    private String orderNumber;
    private String orderTitle;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountWithTax;
    private String vendorId;
    private String departmentId;
    private String orderStatus;
    private LocalDate needByDate;
    private String description;
    private String priority;
    private Boolean isHighValueOrder;
    private String intelligentSuggestion;
    private BigDecimal riskScore;
    private String createdBy;
    private LocalDateTime createdDate;
    private LocalDateTime submittedDate;
    private String submittedBy;
    private LocalDateTime approvedDate;
    private String approvedBy;
    private String approvalNotes;
    private LocalDateTime rejectedDate;
    private String rejectedBy;
    private String rejectionReason;
    

}