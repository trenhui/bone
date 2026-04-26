package com.bone.tpa.intelligent.adjustment.converter;

import com.bone.core.result.PageResult;
import com.bone.tpa.intelligent.adjustment.dto.PolicyDTO;
import com.bone.tpa.sdk.adjustment.model.Policy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PolicyConvert {
    PageResult<PolicyDTO> toDtoPageResult(PageResult<Policy> pageResult);

    List<PolicyDTO> toDtoList(List<Policy>  list);

    PolicyDTO toDto(Policy policy);

}
