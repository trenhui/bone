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

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public static QualityCheck create(Long id, Long masterDataEntityId) {
    QualityCheck check = new QualityCheck();
    check.id = id;
    check.masterDataEntityId = masterDataEntityId;
    check.checkName = "quality-check";
    check.startedAt = LocalDateTime.now();
    check.status = "RUNNING";
    check.createdAt = LocalDateTime.now();
    check.updatedAt = check.createdAt;
    // mdm_qcheck_task 的计数列为 NOT NULL，且 SDK 走显式全列 INSERT（NULL 会覆盖 DEFAULT 0），
    // 因此"先落RUNNING 再回填结果"的写法必须在创建时就把计数初始化为 0，否则插入即失败。
    check.totalRecords = 0;
    check.passedRecords = 0;
    check.failedRecords = 0;
    return check;
  }

  /**
   * 正常终态：COMPLETED。
   *
   * <p>必须显式刷新 updatedAt —— bone-metadata-sdk 的 update() 不会自动填该列（与 insert/deleteById
   * 一致），漏赋值就会得到一个恒为初始值的列，任务状态变更时刻不可追溯。
   */
  public void complete(Integer totalRecords, Integer passedRecords, Integer failedRecords) {
    this.endedAt = LocalDateTime.now();
    this.status = "COMPLETED";
    this.totalRecords = totalRecords;
    this.passedRecords = passedRecords;
    this.failedRecords = failedRecords;
    this.updatedAt = LocalDateTime.now();
    this.addDomainEvent(new QualityCheckCompletedEvent(this));
  }

  // 刻意不提供 fail()：QualityApplicationService#performCheck 是 @Transactional，
  // 任何在同事务内写 FAILED 终态的尝试都会随异常一起回滚，代码看似处理了失败、实则不留痕。
  // 若日后需要保留失败任务，须由独立 bean 的 REQUIRES_NEW 方法落终态，
  // 不得在 performCheck 内 try/catch 后写库（那会产生"以为记录了、其实没有"的最坏状态）。
}
