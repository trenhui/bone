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

/** AI 评测用例（评测集资产：输入/期望/标注一致性；宿主为发布流水线，应用侧只读）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("eval_case")
public class EvalCase extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "scene")
  private String scene;

  @Column(name = "case_type")
  private String caseType;

  @Column(name = "input")
  private String input;

  @Column(name = "expected")
  private String expected;

  @Column(name = "expected_hit")
  private boolean expectedHit;

  @Column(name = "difficulty")
  private Integer difficulty;

  @Column(name = "kappa")
  private BigDecimal kappa;

  @Deleted private Boolean deleted = false;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
