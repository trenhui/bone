package com.bone.masterdata.application.command;

import lombok.Data;

@Data
public class ImportMasterDataRecordsCommand {
  private Long masterDataEntityId;

  /**
   * 业务编码重复时的处理策略：FAIL（默认，重码行计入失败清单）或 UPDATE（命中已有记录则更新）。
   *
   * <p>真实场景：ERP/上游系统每天全量同步商品表，同一批编码反复导入——默认逐次报"重码失败" 会让同步永远追不上。UPDATE 策略下按编码做幂等
   * upsert；已发布记录的数据变更仍被领域层门禁拦下（须走变更审批）。
   */
  private String duplicateStrategy;

  /** 上传文件的原始文件名 */
  private String originalFilename;

  /** 上传文件的输入流（由 adapter 层从 MultipartFile 转换） */
  private java.io.InputStream dataStream;
}
