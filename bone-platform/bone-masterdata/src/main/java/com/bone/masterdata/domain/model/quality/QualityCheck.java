package com.bone.masterdata.domain.model.quality;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.masterdata.domain.model.quality.event.QualityCheckCompletedEvent;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_qcheck_task")
public class QualityCheck extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  @Column(name = "check_name")
  private String checkName;

  private String status;

  @Column(name = "total_records")
  private Integer totalRecords;

  @Column(name = "passed_records")
  private Integer passedRecords;

  @Column(name = "failed_records")
  private Integer failedRecords;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime endedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public static QualityCheck create(Long id, Long masterDataEntityId) {
    QualityCheck check = new QualityCheck();
    check.id = id;
    check.masterDataEntityId = masterDataEntityId;
    check.checkName = "quality-check";
    check.startedAt = LocalDateTime.now();
    check.status = "RUNNING";
    check.createdAt = LocalDateTime.now();
    // mdm_qcheck_task 的计数列为 NOT NULL，且 SDK 走显式全列 INSERT（NULL 会覆盖 DEFAULT 0），
    // 因此"先落 RUNNING 再回填结果"的写法必须在创建时就把计数初始化为 0，否则插入即失败。
    check.totalRecords = 0;
    check.passedRecords = 0;
    check.failedRecords = 0;
    return check;
  }

  public void complete(Integer totalRecords, Integer passedRecords, Integer failedRecords) {
    this.endedAt = LocalDateTime.now();
    this.status = "COMPLETED";
    this.totalRecords = totalRecords;
    this.passedRecords = passedRecords;
    this.failedRecords = failedRecords;
    this.addDomainEvent(new QualityCheckCompletedEvent(this));
  }
}
