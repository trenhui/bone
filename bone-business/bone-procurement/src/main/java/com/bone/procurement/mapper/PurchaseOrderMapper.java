package com.bone.procurement.mapper;

import com.bone.procurement.dto.PurchaseOrderResponse;
import com.bone.procurement.entity.PurchaseOrder;

/**
 * 采购订单实体与DTO映射
 */
public class PurchaseOrderMapper {
    public static final PurchaseOrderMapper INSTANCE = new PurchaseOrderMapper();
    
    private PurchaseOrderMapper() {
        // 私有构造函数，防止外部实例化
    }
    
    public PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        
        PurchaseOrderResponse response = new PurchaseOrderResponse();
        response.setId(purchaseOrder.getId());
        response.setName(purchaseOrder.getName());
        response.setOrderNumber(purchaseOrder.getOrderNumber());
        response.setOrderTitle(purchaseOrder.getOrderTitle());
        response.setTotalAmount(purchaseOrder.getTotalAmount());
        response.setTotalAmountWithTax(purchaseOrder.getTotalAmountWithTax());
        response.setVendorId(purchaseOrder.getVendorId());
        response.setDepartmentId(purchaseOrder.getDepartmentId());
        response.setOrderStatus(purchaseOrder.getOrderStatus());
        response.setNeedByDate(purchaseOrder.getNeedByDate());
        response.setDescription(purchaseOrder.getDescription());
        response.setPriority(purchaseOrder.getPriority());
        response.setIsHighValueOrder(purchaseOrder.getIsHighValueOrder());
        response.setCreatedBy((String) purchaseOrder.getField("createdBy"));
        response.setCreatedDate((java.time.LocalDateTime) purchaseOrder.getField("createdDate"));
        response.setSubmittedDate((java.time.LocalDateTime) purchaseOrder.getField("submittedDate"));
        response.setSubmittedBy((String) purchaseOrder.getField("submittedBy"));
        response.setApprovedDate((java.time.LocalDateTime) purchaseOrder.getField("approvedDate"));
        response.setApprovedBy((String) purchaseOrder.getField("approvedBy"));
        response.setApprovalNotes((String) purchaseOrder.getField("approvalNotes"));
        response.setRejectedDate((java.time.LocalDateTime) purchaseOrder.getField("rejectedDate"));
        response.setRejectedBy((String) purchaseOrder.getField("rejectedBy"));
        response.setRejectionReason((String) purchaseOrder.getField("rejectionReason"));
        
        return response;
    }
}