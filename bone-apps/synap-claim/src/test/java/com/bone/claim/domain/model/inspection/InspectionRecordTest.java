package com.bone.claim.domain.model.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.inspection.event.InspectionConcludedEvent;
import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 后督抽检聚合测试：状态机前进性 + 结论自洽不变量。
 *
 * <p>每个反例都对应一条真实脏数据形态：「结论 PASS 却带处置动作」「结论 DIFF 却无归因」「已结论被二次覆盖」——这三类会让后督
 * 指标（差异率/处置率）失真，且无法从数据上分辨是质检员填错还是系统算错。
 */
class InspectionRecordTest {

  private static final long TENANT = 7L;
  private static final long CLAIM = 11L;

  @Test
  @DisplayName("发起抽检 → 待抽检；领取 → 抽检中；出结论 → 已结论（终态）")
  void happyPath() {
    InspectionRecord record = open();
    assertThat(record.statusEnum()).isEqualTo(InspectionStatus.PENDING);

    record.claim("qc-1");
    assertThat(record.statusEnum()).isEqualTo(InspectionStatus.IN_PROGRESS);
    assertThat(record.getInspectorId()).isEqualTo("qc-1");

    record.conclude(
        InspectionResult.DIFF,
        InspectionDiffType.ADJUST_MISMATCH,
        "{\"approvedAmount\":\"120.00->98.00\"}",
        InspectionAction.RECALCULATE,
        "ADJUSTING",
        "qc-1");

    assertThat(record.statusEnum()).isEqualTo(InspectionStatus.CONCLUDED);
    assertThat(record.resultEnum()).isEqualTo(InspectionResult.DIFF);
    assertThat(record.diffTypeEnum()).isEqualTo(InspectionDiffType.ADJUST_MISMATCH);
    assertThat(record.actionEnum()).isEqualTo(InspectionAction.RECALCULATE);
    assertThat(record.getTargetStatus()).isEqualTo("ADJUSTING");
    assertThat(record.getInspectedAt()).isNotNull();
    assertThat(record.getDomainEvents()).hasSize(1);
    assertThat(record.getDomainEvents().get(0)).isInstanceOf(InspectionConcludedEvent.class);
  }

  @Test
  @DisplayName("同一质检人重复领取幂等（不产生第二个状态跃迁）")
  void claimIsIdempotent() {
    InspectionRecord record = open();
    record.claim("qc-1");
    record.claim("qc-1");
    assertThat(record.statusEnum()).isEqualTo(InspectionStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("结论 PASS 却带差异类型/处置动作/明细 → 拒绝（结论必须自洽）")
  void passMustNotCarryDiff() {
    InspectionRecord record = open();
    assertThatThrownBy(
            () ->
                record.conclude(
                    InspectionResult.PASS,
                    InspectionDiffType.RULE_MISS,
                    null,
                    InspectionAction.NONE,
                    "",
                    "qc-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("结论须自洽");

    assertThatThrownBy(
            () ->
                record.conclude(
                    InspectionResult.PASS,
                    InspectionDiffType.NONE,
                    "{\"a\":\"b\"}",
                    InspectionAction.NONE,
                    "",
                    "qc-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("结论须自洽");
  }

  @Test
  @DisplayName("结论 DIFF 缺差异类型/处置动作/明细 → 拒绝（有差异必须说清差在哪、怎么处置）")
  void diffRequiresAttribution() {
    assertThatThrownBy(
            () ->
                open()
                    .conclude(
                        InspectionResult.DIFF, null, "{}", InspectionAction.REVIEW, "", "qc-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("受控差异类型");

    assertThatThrownBy(
            () ->
                open()
                    .conclude(
                        InspectionResult.DIFF,
                        InspectionDiffType.AI_MISJUDGE,
                        "{}",
                        InspectionAction.NONE,
                        "",
                        "qc-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("处置动作");

    assertThatThrownBy(
            () ->
                open()
                    .conclude(
                        InspectionResult.DIFF,
                        InspectionDiffType.AI_MISJUDGE,
                        "  ",
                        InspectionAction.REVIEW,
                        "",
                        "qc-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("差异明细");
  }

  @Test
  @DisplayName("已结论不可再改写（含重复领取/二次结论）")
  void concludedIsImmutable() {
    InspectionRecord record = concludeAsPass();
    assertThatThrownBy(() -> record.claim("qc-2"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不可改写");
    assertThatThrownBy(
            () ->
                record.conclude(
                    InspectionResult.DIFF,
                    InspectionDiffType.QUOTA_ABNORMAL,
                    "{}",
                    InspectionAction.REVIEW,
                    "",
                    "qc-2"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不可改写");
  }

  @Test
  @DisplayName("处置目标状态须受控：§6.2 枚举名或质检段 30/31/32；其余拒绝")
  void targetStatusIsControlled() {
    // 质检段保留码（一期只登记，不执行回流）
    assertThat(concludeAsDiffWithTarget("30").getTargetStatus()).isEqualTo("30");
    // 已建模状态枚举名
    assertThat(concludeAsDiffWithTarget("ADJUSTING").getTargetStatus()).isEqualTo("ADJUSTING");

    assertThatThrownBy(() -> concludeAsDiffWithTarget("随便写"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("处置目标状态非法");
  }

  @Test
  @DisplayName("差异明细超长拒绝（防 JSON 列写爆）")
  void diffDetailTooLong() {
    String huge = "x".repeat(1001);
    assertThatThrownBy(
            () ->
                open()
                    .conclude(
                        InspectionResult.DIFF,
                        InspectionDiffType.ADJUST_MISMATCH,
                        huge,
                        InspectionAction.RECALCULATE,
                        "",
                        "qc-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("差异明细超长");
  }

  @Test
  @DisplayName("发起抽检必填校验：单号/案件/维度/发起方式")
  void openRequiresMandatoryFields() {
    assertThatThrownBy(
            () ->
                InspectionRecord.open(
                    1L,
                    TENANT,
                    " ",
                    CLAIM,
                    "CL1",
                    InspectionDimension.AMOUNT,
                    "",
                    InspectionType.AUTO))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("抽检单号");
    assertThatThrownBy(
            () ->
                InspectionRecord.open(
                    1L,
                    TENANT,
                    "QI1",
                    null,
                    "CL1",
                    InspectionDimension.AMOUNT,
                    "",
                    InspectionType.AUTO))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("关联案件");
    assertThatThrownBy(
            () ->
                InspectionRecord.open(
                    1L, TENANT, "QI1", CLAIM, "CL1", null, "", InspectionType.AUTO))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("抽检维度");
  }

  private static InspectionRecord open() {
    return InspectionRecord.open(
        1L,
        TENANT,
        "QI" + TENANT + CLAIM,
        CLAIM,
        "CL20261028001",
        InspectionDimension.AMOUNT,
        "500_1000#WECHAT",
        InspectionType.AUTO);
  }

  private static InspectionRecord concludeAsPass() {
    InspectionRecord record = open();
    record.conclude(InspectionResult.PASS, null, null, null, "", "qc-1");
    return record;
  }

  private static InspectionRecord concludeAsDiffWithTarget(String targetStatus) {
    InspectionRecord record = open();
    record.conclude(
        InspectionResult.DIFF,
        InspectionDiffType.ADJUST_MISMATCH,
        "{\"approvedAmount\":\"120.00->98.00\"}",
        InspectionAction.RECALCULATE,
        targetStatus,
        "qc-1");
    return record;
  }
}
