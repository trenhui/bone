package com.bone.masterdata.domain.model.record;

import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 主数据记录版本历史（UC-T7）：审批通过 / 发布时冻结数据快照，供追溯与审计。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_record_version")
public class MasterDataRecordVersion extends Entity<Long> {

  @Column(name = "record_id")
  private Long recordId;

  @Column(name = "version_number")
  private Integer versionNumber;

  private String data;

  /** 版本状态：取快照时的记录状态（APPROVED / PUBLISHED）。 */
  private String status;

  @Column(name = "change_description")
  private String changeDescription;

  @Column(name = "approved_by")
  private Long approvedBy;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Column(name = "created_by")
  private Long createdBy;

  private LocalDateTime createdAt;

  public static MasterDataRecordVersion snapshot(
      Long id,
      Long recordId,
      Integer versionNumber,
      String data,
      String status,
      String changeDescription,
      Long approvedBy,
      Long createdBy) {
    MasterDataRecordVersion version = new MasterDataRecordVersion();
    version.setId(id);
    version.recordId = recordId;
    version.versionNumber = versionNumber;
    version.data = data;
    version.status = status;
    version.changeDescription = changeDescription;
    version.approvedBy = approvedBy;
    version.approvedAt = approvedBy != null ? LocalDateTime.now() : null;
    version.createdBy = createdBy;
    version.createdAt = LocalDateTime.now();
    return version;
  }
}
