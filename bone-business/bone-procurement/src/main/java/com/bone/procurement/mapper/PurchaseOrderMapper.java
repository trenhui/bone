package com.bone.procurement.mapper;

import com.bone.procurement.dto.PurchaseOrderRequest;
import com.bone.procurement.dto.PurchaseOrderResponse;
import com.bone.procurement.entity.PurchaseOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单实体与DTO映射
 */
@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {
    PurchaseOrderMapper INSTANCE = Mappers.getMapper(PurchaseOrderMapper.class);
    
    /**
     * 将PurchaseOrder实体转换为PurchaseOrderResponse
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "orderNumber", target = "orderNumber")
    @Mapping(source = "orderTitle", target = "orderTitle")
    @Mapping(source = "totalAmount", target = "totalAmount")
    @Mapping(source = ".", target = "totalAmountWithTax", qualifiedByName = "getFieldBigDecimal")
    @Mapping(source = ".", target = "vendorId", qualifiedByName = "getFieldVendorId")
    @Mapping(source = ".", target = "departmentId", qualifiedByName = "getFieldDepartmentId")
    @Mapping(source = ".", target = "orderStatus", qualifiedByName = "getFieldOrderStatus")
    @Mapping(source = ".", target = "needByDate", qualifiedByName = "getFieldLocalDate")
    @Mapping(source = ".", target = "description", qualifiedByName = "getFieldDescription")
    @Mapping(source = ".", target = "priority", qualifiedByName = "getFieldPriority")
    @Mapping(source = ".", target = "isHighValueOrder", qualifiedByName = "getFieldBoolean")
    @Mapping(source = ".", target = "createdBy", qualifiedByName = "getFieldCreatedBy")
    @Mapping(source = ".", target = "createdDate", qualifiedByName = "getFieldCreatedDate")
    @Mapping(source = ".", target = "submittedDate", qualifiedByName = "getFieldSubmittedDate")
    @Mapping(source = ".", target = "submittedBy", qualifiedByName = "getFieldSubmittedBy")
    @Mapping(source = ".", target = "approvedDate", qualifiedByName = "getFieldApprovedDate")
    @Mapping(source = ".", target = "approvedBy", qualifiedByName = "getFieldApprovedBy")
    @Mapping(source = ".", target = "approvalNotes", qualifiedByName = "getFieldApprovalNotes")
    @Mapping(source = ".", target = "rejectedDate", qualifiedByName = "getFieldRejectedDate")
    @Mapping(source = ".", target = "rejectedBy", qualifiedByName = "getFieldRejectedBy")
    @Mapping(source = ".", target = "rejectionReason", qualifiedByName = "getFieldRejectionReason")
    PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder);
    
    /**
     * 将PurchaseOrderRequest转换为PurchaseOrder
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "orderTitle", target = "name")
    @Mapping(source = "orderTitle", target = "orderTitle")
    @Mapping(source = "totalAmount", target = "totalAmount")
    @Mapping(source = "vendorId", target = ".", qualifiedByName = "setFieldVendorId")
    @Mapping(source = "departmentId", target = ".", qualifiedByName = "setFieldDepartmentId")
    @Mapping(source = "needByDate", target = ".", qualifiedByName = "setFieldLocalDate")
    @Mapping(source = "description", target = ".", qualifiedByName = "setFieldDescription")
    @Mapping(source = "priority", target = ".", qualifiedByName = "setFieldPriority")
    PurchaseOrder toEntity(PurchaseOrderRequest request);
    
    // 自定义映射方法，用于处理getField调用
    @Named("getFieldVendorId")
    default String getFieldVendorId(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("vendorId");
    }
    
    @Named("getFieldDepartmentId")
    default String getFieldDepartmentId(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("departmentId");
    }
    
    @Named("getFieldOrderStatus")
    default String getFieldOrderStatus(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("orderStatus");
    }
    
    @Named("getFieldDescription")
    default String getFieldDescription(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("description");
    }
    
    @Named("getFieldPriority")
    default String getFieldPriority(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("priority");
    }
    
    @Named("getFieldCreatedBy")
    default String getFieldCreatedBy(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("createdBy");
    }
    
    @Named("getFieldSubmittedBy")
    default String getFieldSubmittedBy(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("submittedBy");
    }
    
    @Named("getFieldApprovedBy")
    default String getFieldApprovedBy(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("approvedBy");
    }
    
    @Named("getFieldApprovalNotes")
    default String getFieldApprovalNotes(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("approvalNotes");
    }
    
    @Named("getFieldRejectedBy")
    default String getFieldRejectedBy(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("rejectedBy");
    }
    
    @Named("getFieldRejectionReason")
    default String getFieldRejectionReason(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField("rejectionReason");
    }
    
    @Named("getFieldBoolean")
    default Boolean getFieldBoolean(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (Boolean) purchaseOrder.getField("isHighValueOrder");
    }
    
    @Named("getFieldBigDecimal")
    default BigDecimal getFieldBigDecimal(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (BigDecimal) purchaseOrder.getField("totalAmountWithTax");
    }
    
    @Named("getFieldLocalDate")
    default LocalDate getFieldLocalDate(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (LocalDate) purchaseOrder.getField("needByDate");
    }
    
    @Named("setFieldLocalDate")
    default void setFieldLocalDate(PurchaseOrder purchaseOrder, LocalDate needByDate) {
        if (purchaseOrder != null) {
            purchaseOrder.setField("needByDate", needByDate);
        }
    }
    
    @Named("getFieldCreatedDate")
    default LocalDateTime getFieldCreatedDate(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (LocalDateTime) purchaseOrder.getField("createdDate");
    }
    
    @Named("getFieldSubmittedDate")
    default LocalDateTime getFieldSubmittedDate(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (LocalDateTime) purchaseOrder.getField("submittedDate");
    }
    
    @Named("getFieldApprovedDate")
    default LocalDateTime getFieldApprovedDate(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (LocalDateTime) purchaseOrder.getField("approvedDate");
    }
    
    @Named("getFieldRejectedDate")
    default LocalDateTime getFieldRejectedDate(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (LocalDateTime) purchaseOrder.getField("rejectedDate");
    }
    
    @Named("getFieldLocalDateTime")
    default LocalDateTime getFieldLocalDateTime(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return null;
        }
        return (LocalDateTime) purchaseOrder.getField("updatedAt");
    }
    
    // 设置字段的方法
    @Named("setFieldVendorId")
    default void setFieldVendorId(PurchaseOrder purchaseOrder, String vendorId) {
        if (purchaseOrder != null) {
            purchaseOrder.setField("vendorId", vendorId);
        }
    }
    
    @Named("setFieldDepartmentId")
    default void setFieldDepartmentId(PurchaseOrder purchaseOrder, String departmentId) {
        if (purchaseOrder != null) {
            purchaseOrder.setField("departmentId", departmentId);
        }
    }
    
    @Named("setFieldDescription")
    default void setFieldDescription(PurchaseOrder purchaseOrder, String description) {
        if (purchaseOrder != null) {
            purchaseOrder.setField("description", description);
        }
    }
    
    @Named("setFieldPriority")
    default void setFieldPriority(PurchaseOrder purchaseOrder, String priority) {
        if (purchaseOrder != null) {
            purchaseOrder.setField("priority", priority);
        }
    }
    
    // 用于设置字段值的方法
    @Named("setFieldString")
    default void setFieldString(PurchaseOrder purchaseOrder, String value, String fieldName) {
        if (purchaseOrder != null) {
            purchaseOrder.setField(fieldName, value);
        }
    }
    

}