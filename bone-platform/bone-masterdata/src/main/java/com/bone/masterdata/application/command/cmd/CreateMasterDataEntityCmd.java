package com.bone.masterdata.application.command.cmd;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateMasterDataEntityCmd {
    private String name;
    private String description;
    private String category;
}