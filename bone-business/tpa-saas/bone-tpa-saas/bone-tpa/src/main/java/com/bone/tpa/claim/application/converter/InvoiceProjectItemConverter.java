package com.bone.tpa.claim.application.converter;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.application.dto.InvoiceProjectItemDTO;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.Map;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InvoiceProjectItemConverter {
    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toJsonString")
    InvoiceProjectItem toEntity(InvoiceProjectItemDTO invoiceProjectItemDTO);

    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toExtraMap")
    InvoiceProjectItemDTO toDTO(InvoiceProjectItem invoiceProjectItem);

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
