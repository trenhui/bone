package com.bone.masterdata.domain.model.record;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordCreatedEvent;
import com.bone.masterdata.domain.model.record.event.MasterDataRecordPublishedEvent;
import com.bone.masterdata.domain.model.record.valueobject.MasterDataRecordStatus;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 主数据记录聚合根。
// G1 收敛：持久化目标由 md_record 切换为 mdm_record。
// 承载 UC-T7 六态状态机（DRAFT → PENDING_APPROVAL → APPROVED → PUBLISHED →
// SUPERSEDED → ARCHIVED）与版本/生效期能力。
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_record")
public class MasterDataRecord extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  /** 业务唯一编码（租户+实体内唯一）。 */
  private String recordCode;

  /** 显示名称。 */
  private String displayName;

  /** 当前生效属性数据；列名 current_data。 */
  @Column(name = "current_data")
  private String data;

  private MasterDataRecordStatus status;

  /** 当前版本号。 */
  private Integer versionNumber;

  /** 生效开始时间（UC-T7 定时生效）。 */
  private LocalDateTime effectiveFrom;

  /** 生效结束时间。 */
  private LocalDateTime effectiveTo;

  /** 是否当前有效版本（发布时切换）。 */
  private Boolean isCurrent;

  /** 审批提交人ID（SoD 校验：审批人不得为提交人）。 */
  @Column(name = "submitted_by")
  private Long submittedBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime publishTime;

  public static MasterDataRecord create(Long id, Long masterDataEntityId, String data) {
    MasterDataRecord record = new MasterDataRecord();
    record.id = id;
    record.masterDataEntityId = masterDataEntityId;
    record.data = data;
    record.status = MasterDataRecordStatus.DRAFT;
    record.versionNumber = 1;
    record.isCurrent = false;
    record.createdAt = LocalDateTime.now();
    record.updatedAt = LocalDateTime.now();
    record.addDomainEvent(new MasterDataRecordCreatedEvent(record));
    return record;
  }

  // 带业务主键创建（推荐入口）：recordCode 是去重、引用与订阅分发的基础。
  public static MasterDataRecord create(
      Long id, Long masterDataEntityId, String recordCode, String displayName, String data) {
    MasterDataRecord record = create(id, masterDataEntityId, data);
    record.recordCode = recordCode;
    record.displayName = displayName;
    return record;
  }

  // 提交审批（UC-T7）。L2/L3 治理等级下，未经审批不得直接发布。
  public void submitForApproval() {
    if (this.status != MasterDataRecordStatus.DRAFT) {
      throw new DomainException("仅草稿状态的记录可提交审批");
    }
    this.status = MasterDataRecordStatus.PENDING_APPROVAL;
    this.updatedAt = LocalDateTime.now();
  }

  /** 记录审批提交人（SoD 校验数据源），由应用层在提交审批时调用。 */
  public void markSubmittedBy(Long userId) {
    this.submittedBy = userId;
    this.updatedAt = LocalDateTime.now();
  }

  // 审批通过（UC-T7）。审批人不得为提交人（SoD，由应用层校验）。
  public void approve() {
    if (this.status != MasterDataRecordStatus.PENDING_APPROVAL) {
      throw new DomainException("仅待审批状态的记录可审批");
    }
    this.status = MasterDataRecordStatus.APPROVED;
    this.updatedAt = LocalDateTime.now();
  }

  // 审批驳回：退回草稿待修改。
  public void reject() {
    if (this.status != MasterDataRecordStatus.PENDING_APPROVAL) {
      throw new DomainException("仅待审批状态的记录可驳回");
    }
    this.status = MasterDataRecordStatus.DRAFT;
    this.updatedAt = LocalDateTime.now();
  }

  public void publish() {
    if (this.status == MasterDataRecordStatus.PUBLISHED) {
      throw new DomainException("主数据记录已发布");
    }
    this.status = MasterDataRecordStatus.PUBLISHED;
    this.isCurrent = true;
    this.publishTime = LocalDateTime.now();
    if (this.effectiveFrom == null) {
      this.effectiveFrom = this.publishTime;
    }
    this.updatedAt = LocalDateTime.now();
    this.addDomainEvent(new MasterDataRecordPublishedEvent(this));
  }

  // 被新版本取代时调用，保留历史可追溯。
  public void supersede() {
    this.status = MasterDataRecordStatus.SUPERSEDED;
    this.isCurrent = false;
    this.effectiveTo = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public void update(String data) {
    if (this.status == MasterDataRecordStatus.PUBLISHED) {
      throw new DomainException("已发布的主数据记录不能修改，请走变更审批");
    }
    this.data = data;
    this.updatedAt = LocalDateTime.now();
  }

  public void updateData(String recordCode, String displayName, String data) {
    this.recordCode = recordCode;
    this.displayName = displayName;
    update(data);
  }

  public void archive() {
    if (this.status == MasterDataRecordStatus.ARCHIVED) {
      throw new DomainException("主数据记录已归档");
    }
    this.status = MasterDataRecordStatus.ARCHIVED;
    this.isCurrent = false;
    this.updatedAt = LocalDateTime.now();
  }
}
