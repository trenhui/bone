package com.bone.masterdata.application.command.cmd;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ImportMasterDataRecordsCommand {
    private Long masterDataEntityId;
    private MultipartFile file;
}