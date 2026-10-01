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
public class UpdateMasterDataRecordCommand {
  private Long id;
  private String data;

  /** 业务唯一编码；草稿态可改，发布态允许补登（存量治理场景）。 */
  private String recordCode;

  /** 显示名称。 */
  private String displayName;

  /** 生效开始时间。 */
  private LocalDateTime effectiveFrom;

  /** 生效结束时间（传此值即为"到期停售/停用法"）。 */
  private LocalDateTime effectiveTo;
}
