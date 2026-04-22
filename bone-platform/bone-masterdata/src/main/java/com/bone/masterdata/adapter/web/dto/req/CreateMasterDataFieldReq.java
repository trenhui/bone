package com.bone.masterdata.adapter.web.dto.req;

import lombok.Data;

@Data
public class CreateMasterDataFieldReq {
    private Long masterDataEntityId;
    private String name;
    private String code;
    private String type;
    private Integer length;
    private Boolean required;
    private String defaultValue;
    private String description;
    private Integer sortOrder;
}
