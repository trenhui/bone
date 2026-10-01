package com.bone.masterdata.application.query.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 批量导入中单行失败明细。
 *
 * <p>真实场景：ERP / Excel 批量同步几千条主数据时，混入几条脏数据是常态。业务方需要的是
 * 「第几行、什么编码、为什么失败」的可执行清单，而不是一句"导入失败"——否则只能人工逐条比对。
 */
@Data
@Builder
public class ImportFailureDTO {
  /** 行号（1 起，与导入文件行序一致）。 */
  private int rowNumber;

  /** 该行的业务编码（可能为空，用于定位）。 */
  private String recordCode;

  /** 失败原因（原样透出校验信息，便于直接修正数据）。 */
  private String reason;
}
