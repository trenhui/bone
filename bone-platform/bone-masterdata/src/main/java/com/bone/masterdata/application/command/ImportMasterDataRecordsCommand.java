package com.bone.masterdata.application.command;

import lombok.Data;

@Data
public class ImportMasterDataRecordsCommand {
  private Long masterDataEntityId;

  /** 上传文件的原始文件名 */
  private String originalFilename;

  /** 上传文件的输入流（由 adapter 层从 MultipartFile 转换） */
  private java.io.InputStream dataStream;
}
