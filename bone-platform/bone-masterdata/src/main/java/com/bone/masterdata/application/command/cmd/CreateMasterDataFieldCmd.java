package com.bone.masterdata.application.command.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMasterDataFieldCmd {
    private Long masterDataEntityId;
    private String name;
    private String type;
    private Integer length;
    private Boolean required;
    private String defaultValue;
    private String description;
}