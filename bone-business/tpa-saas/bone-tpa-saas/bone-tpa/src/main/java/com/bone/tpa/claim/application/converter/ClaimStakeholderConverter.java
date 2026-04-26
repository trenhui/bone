package com.bone.tpa.claim.application.converter;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.application.dto.ClaimStakeholderDTO;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.Map;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimStakeholderConverter {
    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toJsonString")
    ClaimStakeholder toEntity(ClaimStakeholderDTO claimStakeholderDTO);

    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toExtraMap")
    ClaimStakeholderDTO toDTO(ClaimStakeholder claimStakeholder);



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
