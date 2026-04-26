package com.bone.tpa.claim.application.converter;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.application.dto.ClaimDTO;
import com.bone.tpa.sdk.claim.model.Claim;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.Map;

/**
 * 赔案对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimConverter {
    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toJsonString")
    Claim toEntity(ClaimDTO claimDTO);

    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toExtraMap")
    ClaimDTO toDTO(Claim claim);

    @Named("toJsonString")
    default String toJsonString(Map value) {
        if( value == null){
            return "{}";
        }
        return JSONObject.toJSONString(value);
    }

    @Named("toExtraMap")
    default Map<String,Object> toExtraMap(String value) {
        if( value == null || value.length() == 0){
            return new HashMap<>();
        }
        return JSONObject.parseObject(value,Map.class);
    }
}
