package com.bone.integration.infrastructure.converter;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.bone.integration.application.dto.MockDTO;
import com.bone.integration.domain.model.MockDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MockConverter {

    @Mapping(source = "conditions", target = "conditions", qualifiedByName = "jsonStringToList")
    MockDTO toDto(MockDO po);

    @Mapping(source = "conditions", target = "conditions", qualifiedByName = "listToJsonString")
    @Mapping(target = "mockKey", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    MockDO toEntity(MockDTO dto);

    List<MockDTO> toDtoList(List<MockDO> poList);
    List<MockDO> toEntityList(List<MockDTO> dtoList);

    /**
     * 将JSON字符串转换为MockCondition列表
     */
    @Named("jsonStringToList")
    default List<MockDTO.MockCondition> jsonStringToList(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return List.of();
        }
        return JSONUtil.toList(jsonString, MockDTO.MockCondition.class);
    }

    /**
     * 将MockCondition列表转换为JSON字符串
     */
    @Named("listToJsonString")
    default String listToJsonString(List<MockDTO.MockCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "[]";
        }
        // 过滤掉空的response header
        conditions = conditions.stream().peek(condition -> {
            if (condition.getResponse() != null && condition.getResponse().getResponseHeader() != null) {
                condition.getResponse().setResponseHeader(
                    condition.getResponse().getResponseHeader().stream()
                        .filter(header -> header.getKey() != null && !header.getKey().isEmpty())
                        .collect(Collectors.toList())
                );
            }
        }).collect(Collectors.toList());
        return new JSONArray(conditions).toString();
    }
}
