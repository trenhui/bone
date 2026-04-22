package com.bone.masterdata.application.command.cmd;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateMasterDataFieldCmd {
    private Long masterDataEntityId;
    private String name;
    private String type;
    private Integer length;
    private Boolean required;
    private String defaultValue;
    private String description;
}