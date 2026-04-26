package com.bone.tpa.sdk.service;

import com.bone.core.util.JsonUtil;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.adjustment.model.liability.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

/**
 * 责任配置与模型转换器（精准控制JSON转换范围）
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LiabilityConverter {

    //================================================================================
    // 正向转换：LiabilityConfig → Liability
    //================================================================================
    @Mapping(target = "allowanceDetail", source = "allowanceDetail", qualifiedByName = "mapAllowanceDetail")
    @Mapping(target = "restrictObject", source = "restrictObject", qualifiedByName = "mapRestrictObject")
    @Mapping(target = "restrictScope", source = "restrictScope", qualifiedByName = "mapRestrictScope")
    @Mapping(target = "restrictOutInsure", source = "restrictOutInsure", qualifiedByName = "mapRestrictOutInsure")
    @Mapping(target = "payPercent", source = "payPercent", qualifiedByName = "mapPayPercent")
    @Mapping(target = "liabilityDeduct", source = "liabilityDeduct", qualifiedByName = "mapLiabilityDeduct")
    @Mapping(target = "timesLimit", source = "timesLimit", qualifiedByName = "mapTimesLimit")
    @Mapping(target = "quotaController", source = "quotaController", qualifiedByName = "mapQuotaController")
    @Mapping(target = "liabilityLimit", source = "liabilityLimit", qualifiedByName = "mapLiabilityLimit")
    @Mapping(target = "adjustmentDetail", source = "adjustmentDetail", qualifiedByName = "mapAdjustmentDetail")
    Liability toLiability(LiabilityConfig liabilityConfig);

    //================================================================================
    // 逆向转换：Liability → LiabilityConfig
    //================================================================================
    @Mapping(target = "allowanceDetail", source = "allowanceDetail", qualifiedByName = "mapToAllowanceDetail")
    @Mapping(target = "restrictObject", source = "restrictObject", qualifiedByName = "mapToRestrictObject")
    @Mapping(target = "restrictScope", source = "restrictScope", qualifiedByName = "mapToRestrictScope")
    @Mapping(target = "restrictOutInsure", source = "restrictOutInsure", qualifiedByName = "mapToRestrictOutInsure")
    @Mapping(target = "payPercent", source = "payPercent", qualifiedByName = "mapToPayPercent")
    @Mapping(target = "liabilityDeduct", source = "liabilityDeduct", qualifiedByName = "mapToLiabilityDeduct")
    @Mapping(target = "timesLimit", source = "timesLimit", qualifiedByName = "mapToTimesLimit")
    @Mapping(target = "quotaController", source = "quotaController", qualifiedByName = "mapToQuotaController")
    @Mapping(target = "liabilityLimit", source = "liabilityLimit", qualifiedByName = "mapToLiabilityLimit")
    @Mapping(target = "adjustmentDetail", source = "adjustmentDetail", qualifiedByName = "mapToAdjustmentDetail")
    LiabilityConfig toLiabilityConfig(Liability liability);

    //================================================================================
    // 精准JSON转换方法（仅处理复杂对象 ↔ JSON字符串）
    //================================================================================

    //---------------- 正向转换：对象 → JSON字符串 ----------------
    @Named("mapAllowanceDetail")
    default String mapAllowanceDetail(AllowanceDetail value) {
        return toJson(value);
    }

    @Named("mapRestrictObject")
    default String mapRestrictObject(RestrictObject value) {
        return toJson(value);
    }

    @Named("mapRestrictScope")
    default String mapRestrictScope(List<RestrictScope> value) {
        return toJson(value);
    }

    @Named("mapRestrictOutInsure")
    default String mapRestrictOutInsure(RestrictOutInsure value) {
        return toJson(value);
    }

    @Named("mapPayPercent")
    default String mapPayPercent(PayPercent value) {
        return toJson(value);
    }

    @Named("mapLiabilityDeduct")
    default String mapLiabilityDeduct(LiabilityDeduct value) {
        return toJson(value);
    }

    @Named("mapTimesLimit")
    default String mapTimesLimit(TimesLimit value) {
        return toJson(value);
    }

    @Named("mapQuotaController")
    default String mapQuotaController(QuotaController value) {
        return toJson(value);
    }

    @Named("mapLiabilityLimit")
    default String mapLiabilityLimit(LiabilityLimit value) {
        return toJson(value);
    }

    @Named("mapAdjustmentDetail")
    default String mapAdjustmentDetail(List<AdjustmentDetail> value) {
        return toJson(value);
    }

    //---------------- 逆向转换：JSON字符串 → 对象 ----------------
    @Named("mapToAllowanceDetail")
    default AllowanceDetail mapToAllowanceDetail(String value) {
        return fromJson(value, AllowanceDetail.class);
    }

    @Named("mapToRestrictObject")
    default RestrictObject mapToRestrictObject(String value) {
        return fromJson(value, RestrictObject.class);
    }

    @Named("mapToRestrictScope")
    default List<RestrictScope> mapToRestrictScope(String value) {
        return fromJson(value, new TypeReference<List<RestrictScope>>() {});
    }

    @Named("mapToRestrictOutInsure")
    default RestrictOutInsure mapToRestrictOutInsure(String value) {
        return fromJson(value, RestrictOutInsure.class);
    }

    @Named("mapToPayPercent")
    default PayPercent mapToPayPercent(String value) {
        return fromJson(value, PayPercent.class);
    }

    @Named("mapToLiabilityDeduct")
    default LiabilityDeduct mapToLiabilityDeduct(String value) {
        return fromJson(value, LiabilityDeduct.class);
    }

    @Named("mapToTimesLimit")
    default TimesLimit mapToTimesLimit(String value) {
        return fromJson(value, TimesLimit.class);
    }

    @Named("mapToQuotaController")
    default QuotaController mapToQuotaController(String value) {
        return fromJson(value, QuotaController.class);
    }

    @Named("mapToLiabilityLimit")
    default LiabilityLimit mapToLiabilityLimit(String value) {
        return fromJson(value, LiabilityLimit.class);
    }

    @Named("mapToAdjustmentDetail")
    default List<AdjustmentDetail> mapToAdjustmentDetail(String value) {
        return fromJson(value, new TypeReference<List<AdjustmentDetail>>() {});
    }

    //================================================================================
    // 基础JSON工具方法（私有方法仅供内部调用）
    //================================================================================
    private String toJson(Object obj) {
        if (obj == null) return "";
        return JsonUtil.toJson(obj);
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null) return null;
        return JsonUtil.fromJson(json, type);
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null) return null;
        return JsonUtil.fromJson(json, typeReference);
    }

    //================================================================================
    // 枚举类型特殊处理（示例：LiabilityType）
    //================================================================================
    @Named("mapLiabilityType")
    default String mapLiabilityType(LiabilityTypeEnum liabilityType) {
        return liabilityType != null ? liabilityType.name() : null;
    }

    @Named("mapToLiabilityType")
    default LiabilityTypeEnum mapToLiabilityType(String liabilityType) {
        if (liabilityType == null) return null;
        return LiabilityTypeEnum.valueOf(liabilityType);
    }
}
