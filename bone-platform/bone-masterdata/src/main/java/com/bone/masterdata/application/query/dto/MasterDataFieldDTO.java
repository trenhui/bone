package com.bone.masterdata.application.query.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MasterDataFieldDTO {
    private Long id;
    private Long masterDataEntityId;
    private String name;
    private String code;
    private String type;
    private Integer length;
    private Boolean required;
    private String defaultValue;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}