package com.bone.masterdata.application.command;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMasterDataRecordCommand {
  private Long masterDataEntityId;
  private String data;

  /**
   * 业务唯一编码（租户+实体内唯一）。
   *
   * <p>此前该字段根本不存在于命令里，创建记录时 record_code 恒为 NULL——下游只能靠 data JSON 里的 业务字段定位记录，无法按业务编码去重、检索、订阅分发。实体有
   * entityCode、记录却没有 recordCode， 是主数据"业务主键"链路上的最后一段断链。
   */
  private String recordCode;

  /** 显示名称（列表页/下拉框直接可读，不必再解 data JSON）。 */
  private String displayName;

  /** 生效开始时间（不传表示立即生效）。 */
  private LocalDateTime effectiveFrom;

  /** 生效结束时间（不传表示长期有效）。 */
  private LocalDateTime effectiveTo;
}
