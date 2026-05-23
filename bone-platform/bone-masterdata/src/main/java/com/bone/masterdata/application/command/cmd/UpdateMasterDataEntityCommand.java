package com.bone.masterdata.application.command.cmd;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateMasterDataEntityCommand {
    private Long id;
    private String name;
    private String description;
    private String category;
}