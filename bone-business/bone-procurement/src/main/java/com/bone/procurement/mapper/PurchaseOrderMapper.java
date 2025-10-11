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
    @Mapping(source = ".", target = "vendorId", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "departmentId", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "orderStatus", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "needByDate", qualifiedByName = "getFieldLocalDate")
    @Mapping(source = ".", target = "description", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "priority", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "isHighValueOrder", qualifiedByName = "getFieldBoolean")
    @Mapping(source = ".", target = "createdBy", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "createdDate", qualifiedByName = "getFieldLocalDateTime")
    @Mapping(source = ".", target = "submittedDate", qualifiedByName = "getFieldLocalDateTime")
    @Mapping(source = ".", target = "submittedBy", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "approvedDate", qualifiedByName = "getFieldLocalDateTime")
    @Mapping(source = ".", target = "approvedBy", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "approvalNotes", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "rejectedDate", qualifiedByName = "getFieldLocalDateTime")
    @Mapping(source = ".", target = "rejectedBy", qualifiedByName = "getFieldString")
    @Mapping(source = ".", target = "rejectionReason", qualifiedByName = "getFieldString")
    PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder);
    
    /**
     * 将PurchaseOrderRequest转换为PurchaseOrder
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "orderTitle", target = "name")
    @Mapping(source = "orderTitle", target = "orderTitle")
    @Mapping(source = "totalAmount", target = "totalAmount")
    @Mapping(source = "vendorId", target = ".", qualifiedByName = "setFieldString")
    @Mapping(source = "departmentId", target = ".", qualifiedByName = "setFieldString")
    @Mapping(source = "needByDate", target = ".", qualifiedByName = "setFieldLocalDate")
    @Mapping(source = "description", target = ".", qualifiedByName = "setFieldString")
    @Mapping(source = "priority", target = ".", qualifiedByName = "setFieldString")
    PurchaseOrder toEntity(PurchaseOrderRequest request);
    
    // 自定义映射方法，用于处理getField调用
    @Named("getFieldString")
    default String getFieldString(PurchaseOrder purchaseOrder, String fieldName) {
        if (purchaseOrder == null) {
            return null;
        }
        return (String) purchaseOrder.getField(fieldName);
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
    
    @Named("getFieldLocalDateTime")
    default LocalDateTime getFieldLocalDateTime(PurchaseOrder purchaseOrder, String fieldName) {
        if (purchaseOrder == null) {
            return null;
        }
        return (LocalDateTime) purchaseOrder.getField(fieldName);
    }
    
    // 用于设置字段值的方法
    @Named("setFieldString")
    default void setFieldString(PurchaseOrder purchaseOrder, String value, String fieldName) {
        if (purchaseOrder != null) {
            purchaseOrder.setField(fieldName, value);
        }
    }
    
    @Named("setFieldLocalDate")
    default void setFieldLocalDate(PurchaseOrder purchaseOrder, LocalDate value) {
        if (purchaseOrder != null) {
            purchaseOrder.setField("needByDate", value);
        }
    }
}