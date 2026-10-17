package com.bone.aiapp.eval.domain.model;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 评测运行（一次门禁评测快照；指标字典的计量源）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("eval_run")
public class EvalRun extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "gate")
  private String gate;

  @Column(name = "case_id")
  private Long caseId;

  @Column(name = "predicted")
  private String predicted;

  @Column(name = "expected")
  private String expected;

  @Column(name = "expected_hit")
  private boolean expectedHit;

  @Column(name = "predicted_hit")
  private boolean predictedHit;

  @Column(name = "cited_clause")
  private String citedClause;

  @Column(name = "judge_score")
  private BigDecimal judgeScore;

  @Column(name = "passed")
  private boolean passed;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Deleted private Boolean deleted = false;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
