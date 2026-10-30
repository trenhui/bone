package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A3 决策执行单测（只追加 / 结果确定后不可改 / 活跃位释放）。 */
class DecisionExecutionTest {

  private static DecisionExecution newExecution() {
    return DecisionExecution.start(
        9001L, 42L, "trace-1", 1001L, "graph-v1", "rule-v1", "model-v1", "prompt-v1", "cat-v1");
  }

  @Test
  @DisplayName("启动：RUNNING + 活跃位 1")
  void startStatus() {
    DecisionExecution execution = newExecution();
    assertThat(execution.statusEnum()).isEqualTo(DecisionExecution.Status.RUNNING);
    assertThat(execution.getActiveFlag()).isEqualTo(1);
    assertThat(execution.getTraceId()).isEqualTo("trace-1");
  }

  @Test
  @DisplayName("轨迹按节点序号追加，顺序即执行顺序")
  void recordTrace() {
    DecisionExecution execution = newExecution();
    execution.recordTrace(
        1,
        DecisionNodeType.OCR,
        DecisionGraph.EVIDENCE_LOAD,
        "材料 3 份",
        "{}",
        DecisionNodeStatus.SUCCEEDED,
        "OK",
        12);
    execution.recordTrace(
        2,
        DecisionNodeType.RULE,
        DecisionGraph.RULE_CHECK,
        "命中 R-DIR-01",
        "{}",
        DecisionNodeStatus.SUCCEEDED,
        "OK",
        5);
    assertThat(execution.traces()).hasSize(2);
    assertThat(execution.traces().get(0).getNodeName()).isEqualTo(DecisionGraph.EVIDENCE_LOAD);
    assertThat(execution.traces().get(1).getNodeName()).isEqualTo(DecisionGraph.RULE_CHECK);
    assertThat(execution.traces().get(1).nodeTypeEnum()).isEqualTo(DecisionNodeType.RULE);
  }

  @Test
  @DisplayName("完成：结论落位 + 活跃位释放（NULL 不参与唯一约束）")
  void complete() {
    DecisionExecution execution = newExecution();
    execution.complete(
        DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, new BigDecimal("0.93"), false);
    assertThat(execution.decisionResultEnum()).isEqualTo(DecisionResult.AUTO_APPROVE);
    assertThat(execution.statusEnum()).isEqualTo(DecisionExecution.Status.SUCCESS);
    assertThat(execution.getActiveFlag()).isNull();
    assertThat(execution.getFinishedAt()).isNotNull();
  }

  @Test
  @DisplayName("结果确定后不可改：不可重复落结论、不可再追加轨迹")
  void appendOnlyAfterComplete() {
    DecisionExecution execution = newExecution();
    execution.complete(
        DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, new BigDecimal("0.93"), false);
    assertThatThrownBy(
            () ->
                execution.complete(
                    DecisionResult.MANUAL_REQUIRED,
                    DecisionTier.MANUAL,
                    new BigDecimal("0.1"),
                    false))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不可重复落结论");
    assertThatThrownBy(
            () ->
                execution.recordTrace(
                    9,
                    DecisionNodeType.RULE,
                    DecisionGraph.RULE_CHECK,
                    "",
                    null,
                    DecisionNodeStatus.SUCCEEDED,
                    "OK",
                    1))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("只追加");
  }

  @Test
  @DisplayName("失败：结论强制转人工并释放活跃位")
  void fail() {
    DecisionExecution execution = newExecution();
    execution.fail();
    assertThat(execution.statusEnum()).isEqualTo(DecisionExecution.Status.FAILED);
    assertThat(execution.decisionResultEnum()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(execution.getActiveFlag()).isNull();
  }

  @Test
  @DisplayName("消费结果：执行终结后落位，consumedAt 与 finishedAt 是两个时点")
  void recordConsumption() {
    DecisionExecution execution = newExecution();
    execution.complete(
        DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, new BigDecimal("0.9"), false);
    assertThat(execution.getConsumptionReason()).isNull();

    execution.recordConsumption("CAP_EXCEEDED");

    assertThat(execution.getConsumptionReason()).isEqualTo("CAP_EXCEEDED");
    assertThat(execution.getConsumedAt()).isNotNull();
    // 未消费过时不得凭空造时间；消费后时间不早于决策结束时间
    assertThat(execution.getConsumedAt()).isAfterOrEqualTo(execution.getFinishedAt());
  }

  @Test
  @DisplayName("消费结果：RUNNING 期间不可记录（消费不可能先于结论确定）")
  void rejectsConsumptionWhileRunning() {
    DecisionExecution execution = newExecution();
    assertThatThrownBy(() -> execution.recordConsumption("APPLIED"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("执行未终结");
  }

  @Test
  @DisplayName("消费结果：首写为准，重复写入不覆盖（两次判定矛盾时保留第一次的痕迹）")
  void keepsFirstConsumption() {
    DecisionExecution execution = newExecution();
    execution.complete(
        DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, new BigDecimal("0.9"), false);
    execution.recordConsumption("AUTO_APPROVE_DISABLED");

    execution.recordConsumption("APPLIED");

    assertThat(execution.getConsumptionReason()).isEqualTo("AUTO_APPROVE_DISABLED");
  }

  @Test
  @DisplayName("消费结果：空原因拒绝（无消费动作应保持 NULL，而不是写空串冒充已判定）")
  void rejectsBlankConsumption() {
    DecisionExecution execution = newExecution();
    execution.complete(
        DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, new BigDecimal("0.9"), false);
    assertThatThrownBy(() -> execution.recordConsumption(" "))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不得为空");
  }
}
