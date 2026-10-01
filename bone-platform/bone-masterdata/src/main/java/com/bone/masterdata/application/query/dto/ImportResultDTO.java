package com.bone.masterdata.application.query.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 批量导入结果（部分成功语义）。
 *
 * <p>此前导入接口只返回成功行的 ID 列表，任一行校验失败即整批异常回滚——真实批量同步场景下 一次脏数据会让整批几千条数据作废。本结果对象把「成功清单 + 失败清单」一起返回，
 * 由调用方决定是继续推进还是整体撤销。
 */
@Data
@Builder
public class ImportResultDTO {
  /** 解析出的总行数。 */
  private int total;

  /** 成功入库行数。 */
  private int successCount;

  /** 按编码命中已有记录并更新（duplicateStrategy=UPDATE）的行数。 */
  private int updatedCount;

  /** 失败行数。 */
  private int failureCount;

  /** 成功行的记录 ID。 */
  private List<Long> recordIds;

  /** 失败行明细（行号 + 编码 + 原因）。 */
  private List<ImportFailureDTO> failures;
}
