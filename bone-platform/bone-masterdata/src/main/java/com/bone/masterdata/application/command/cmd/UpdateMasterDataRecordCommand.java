package com.bone.masterdata.application.command.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMasterDataRecordCommand {
    private Long id;
    private String data;
}
