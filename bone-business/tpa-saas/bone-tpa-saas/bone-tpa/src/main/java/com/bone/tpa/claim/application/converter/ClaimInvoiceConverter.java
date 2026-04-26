package com.bone.tpa.claim.application.converter;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.application.dto.ClaimInvoiceDTO;
import com.bone.tpa.intelligent.adjustment.model.LiabilityToBind;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimInvoiceConverter {

    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toJsonString")
    @Mapping(target = "relateLiability", source = "relateLiability", qualifiedByName = "noTransferFromList")
    ClaimInvoice toEntity(ClaimInvoiceDTO claimInvoiceDTO);

    @Mapping(target = "extraStore", source = "extraStore", qualifiedByName = "toExtraMap")
    @Mapping(target = "relateLiability", source = "relateLiability", qualifiedByName = "noTransferFromString")
    ClaimInvoiceDTO toDTO(ClaimInvoice claimInvoice);


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

    @Named("noTransferFromString")
    default List<LiabilityToBind> noTransferFromString(String value) {
        return null;
    }

    @Named("noTransferFromList")
    default String noTransferFromList(List<LiabilityToBind> value) {
        return null;
    }
}
