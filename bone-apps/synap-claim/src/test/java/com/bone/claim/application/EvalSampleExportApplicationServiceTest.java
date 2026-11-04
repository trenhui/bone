package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bone.claim.application.EvalSampleExportApplicationService.AgreementSummary;
import com.bone.claim.application.EvalSampleExportApplicationService.EvalSampleExport;
import com.bone.claim.application.EvalSampleExportApplicationService.EvalSampleExport.Sample;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 评测样本导出的判据：<b>不许把「AI 没判断」记成「AI 判断对了」</b>，也不许把参数错误报成 500。
 *
 * <p>这组用例的存在理由不是覆盖率，而是三条会真实导致决策失真的断言：
 *
 * <ul>
 *   <li><b>MANUAL_REQUIRED 必须落 UNKNOWN</b>。若把它算作 AGREE（「AI 建议转人工、人工也处理了」听起来很合理）， 准确率会被「AI
 *       没给结论」的样本持续抬高——这是指标造假的经典形态，且从报表上看不出来。
 *   <li><b>时间窗/条数越界必须 400 且带受控错误码</b>。若让 IllegalArgumentException 冒泡，全局兜底会翻成
 *       500，运维把调用方参数错当服务故障排查，SLI 5xx 率被污染。
 *   <li><b>样本键不得可直接反查案件号</b>。导出物会离开系统边界（交给评测流水线），带明文 claimId 等于 把「哪个案子被评测过」一起交出去。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EvalSampleExportApplicationServiceTest {

  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

  @Mock DecisionExecutionRepository decisionExecutionRepository;
  @Mock ClaimCaseRepository claimCaseRepository;

  @InjectMocks EvalSampleExportApplicationService service;

  // ===== 一致性判定：三态语义 =====

  @Test
  void autoApproveWithFinishedIsAgree() {
    assertThat(EvalSampleExportApplicationService.judge("AUTO_APPROVE", "FINISHED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.AGREE);
  }

  @Test
  void autoApproveWithRejectedIsDisagree() {
    assertThat(EvalSampleExportApplicationService.judge("AUTO_APPROVE", "CLOSED_REJECTED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.DISAGREE);
  }

  @Test
  void suggestRejectWithRejectedIsAgree() {
    assertThat(EvalSampleExportApplicationService.judge("SUGGEST_REJECT", "CLOSED_REJECTED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.AGREE);
  }

  @Test
  void suggestRejectButPaidIsDisagree() {
    assertThat(EvalSampleExportApplicationService.judge("SUGGEST_REJECT", "FINISHED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.DISAGREE);
  }

  /** 关键负向：AI 转人工不是「判断对了」，必须落 UNKNOWN 且给出不计入准确率的依据。 */
  @Test
  void manualRequiredIsUnknownNotAgree() {
    assertThat(EvalSampleExportApplicationService.judge("MANUAL_REQUIRED", "FINISHED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.UNKNOWN_NO_BINARY_VERDICT);
    assertThat(EvalSampleExportApplicationService.judge("NEED_MORE_EVIDENCE", "CLOSED_REJECTED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.UNKNOWN_NO_BINARY_VERDICT);
  }

  @Test
  void nonTerminalCaseIsUnknown() {
    assertThat(EvalSampleExportApplicationService.judge("AUTO_APPROVE", "PRE_EXAMINED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.UNKNOWN_NOT_CONVERGED);
    assertThat(EvalSampleExportApplicationService.judge("AUTO_APPROVE", "PENDING_ADJUST"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.UNKNOWN_NOT_CONVERGED);
  }

  @Test
  void nullInputsAreUnknown() {
    assertThat(EvalSampleExportApplicationService.judge(null, "FINISHED"))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.UNKNOWN_NOT_CONVERGED);
    assertThat(EvalSampleExportApplicationService.judge("AUTO_APPROVE", null))
        .isEqualTo(EvalSampleExportApplicationService.Agreement.UNKNOWN_NOT_CONVERGED);
  }

  // ===== 参数护栏：越界必须 400 + 受控码，不得静默裁剪 =====

  @Test
  void windowLongerThanLimitIsRejected() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-03-01T00:00:00Z");
    assertThatThrownBy(() -> service.export(from, to, 10))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID)
        .hasMessageContaining("时间窗过大");
  }

  @Test
  void reversedWindowIsRejected() {
    assertThatThrownBy(() -> service.export(TO, FROM, 10))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID);
  }

  @Test
  void nullWindowIsRejected() {
    assertThatThrownBy(() -> service.export(null, TO, 10))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID);
  }

  @Test
  void limitOutOfRangeIsRejected() {
    assertThatThrownBy(() -> service.export(FROM, TO, 0))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID);
    assertThatThrownBy(
            () -> service.export(FROM, TO, EvalSampleExportApplicationService.MAX_SAMPLES + 1))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID);
  }

  // ===== 脱敏与哈希 =====

  @Test
  void sampleKeyIsStableAndDoesNotLeakClaimId() {
    String key = EvalSampleExportApplicationService.sampleKey(1234567890123L);
    assertThat(key)
        .hasSize(16)
        .isEqualTo(EvalSampleExportApplicationService.sampleKey(1234567890123L));
    assertThat(key).doesNotContain("1234567890123");
    assertThat(EvalSampleExportApplicationService.sampleKey(9999999999999L)).isNotEqualTo(key);
  }

  @Test
  void hashIsOrderInsensitive() {
    Sample a = sample("aaaa", "AUTO_APPROVE", "FINISHED", "AGREE");
    Sample b = sample("bbbb", "SUGGEST_REJECT", "CLOSED_REJECTED", "AGREE");
    assertThat(EvalSampleExportApplicationService.hashSamples(List.of(a, b)))
        .isEqualTo(EvalSampleExportApplicationService.hashSamples(List.of(b, a)));
  }

  @Test
  void hashChangesWhenAnyFieldChanges() {
    Sample base = sample("aaaa", "AUTO_APPROVE", "FINISHED", "AGREE");
    String baseHash = EvalSampleExportApplicationService.hashSamples(List.of(base));
    Sample changed = sample("aaaa", "SUGGEST_REJECT", "FINISHED", "AGREE");
    assertThat(EvalSampleExportApplicationService.hashSamples(List.of(changed)))
        .isNotEqualTo(baseHash);
  }

  @Test
  void emptyHashIsStable() {
    assertThat(EvalSampleExportApplicationService.hashSamples(List.of()))
        .isEqualTo(EvalSampleExportApplicationService.hashSamples(null))
        .hasSize(64);
  }

  // ===== 聚合口径：0 条可比结论 ≠ 一致率 0% =====

  @Test
  void summaryCountsEveryState() {
    List<Sample> samples =
        List.of(
            sample("a", "AUTO_APPROVE", "FINISHED", "AGREE"),
            sample("b", "SUGGEST_REJECT", "CLOSED_REJECTED", "AGREE"),
            sample("c", "AUTO_APPROVE", "CLOSED_REJECTED", "DISAGREE"),
            sample("d", "MANUAL_REQUIRED", "FINISHED", "UNKNOWN_NO_BINARY_VERDICT"),
            sample("e", "AUTO_APPROVE", "PRE_EXAMINED", "UNKNOWN_NOT_CONVERGED"));

    AgreementSummary summary = EvalSampleExportApplicationService.summarize(samples);

    assertThat(summary.agree()).isEqualTo(2);
    assertThat(summary.disagree()).isEqualTo(1);
    assertThat(summary.unknownNoBinaryVerdict()).isEqualTo(1);
    assertThat(summary.unknownNotConverged()).isEqualTo(1);
    assertThat(summary.evaluableCount()).isEqualTo(3);
    assertThat(summary.agreementRate()).isEqualByComparingTo("0.6667");
    // 四态之和必须等于样本数：漏统计某个状态会让一致率虚高，而总量看上去仍然正常。
    assertThat(
            summary.agree()
                + summary.disagree()
                + summary.unknownNoBinaryVerdict()
                + summary.unknownNotConverged())
        .isEqualTo(samples.size());
  }

  /** 关键负向：全部「AI 转人工」时必须给 null，不能给 0——0 会被下游当作「模型全错」写进基线。 */
  @Test
  void noComparableVerdictYieldsNullRate() {
    List<Sample> samples =
        List.of(
            sample("a", "MANUAL_REQUIRED", "FINISHED", "UNKNOWN_NO_BINARY_VERDICT"),
            sample("b", "NEED_MORE_EVIDENCE", "CLOSED_REJECTED", "UNKNOWN_NO_BINARY_VERDICT"));

    AgreementSummary summary = EvalSampleExportApplicationService.summarize(samples);

    assertThat(summary.evaluableCount()).isZero();
    assertThat(summary.agreementRate()).isNull();
  }

  @Test
  void emptySummaryHasNullRate() {
    AgreementSummary summary = EvalSampleExportApplicationService.summarize(List.of());

    assertThat(summary.evaluableCount()).isZero();
    assertThat(summary.agreementRate()).isNull();
  }

  /** 新增判定态却忘了更新聚合时必须炸：静默漏统会让「条数之和 ≠ 样本数」永远无人发现。 */
  @Test
  void unknownAgreementValueFailsFast() {
    Sample stranger = sample("a", "AUTO_APPROVE", "FINISHED", "SOME_FUTURE_STATE");

    assertThatThrownBy(() -> EvalSampleExportApplicationService.summarize(List.of(stranger)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ===== 组装：缺失案件跳过、截断标记、依据随样本 =====

  @Test
  void missingCaseIsSkippedInsteadOfGuessed() {
    // Mockito 禁止在 when(...) 的参数里再调 when(...)：先把替身造出来，再整体 stub。
    DecisionExecution first = execution(1L, "AUTO_APPROVE");
    DecisionExecution second = execution(2L, "AUTO_APPROVE");
    when(decisionExecutionRepository.findConvergedInWindow(FROM, TO))
        .thenReturn(List.of(first, second));
    // 只返回 1 号案件：2 号已物理清理或跨库不可见，不得用 AI 建议冒充标签
    ClaimCase existing = claim(1L, "FINISHED", "42", "u1");
    when(claimCaseRepository.findByIds(anyList())).thenReturn(List.of(existing));

    EvalSampleExport export = service.export(FROM, TO, 100);

    assertThat(export.returned()).isEqualTo(1);
    assertThat(export.samples()).extracting(Sample::finalStatus).containsExactly("FINISHED");
  }

  @Test
  void truncatedFlagIsSetWhenMoreSamplesThanLimit() {
    DecisionExecution first = execution(1L, "AUTO_APPROVE");
    DecisionExecution second = execution(2L, "AUTO_APPROVE");
    when(decisionExecutionRepository.findConvergedInWindow(FROM, TO))
        .thenReturn(List.of(first, second));
    ClaimCase firstCase = claim(1L, "FINISHED", "42", "u1");
    ClaimCase secondCase = claim(2L, "FINISHED", "42", "u1");
    when(claimCaseRepository.findByIds(anyList())).thenReturn(List.of(firstCase, secondCase));

    EvalSampleExport export = service.export(FROM, TO, 1);

    assertThat(export.returned()).isEqualTo(1);
    assertThat(export.truncated()).isTrue();
  }

  @Test
  void emptyWindowReturnsZeroWithoutCallingCaseRepository() {
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(List.of());

    EvalSampleExport export = service.export(FROM, TO, 100);

    assertThat(export.returned()).isZero();
    assertThat(export.truncated()).isFalse();
    assertThat(export.samples()).isEmpty();
  }

  @Test
  void samplesCarryAgreementBasisForAudit() {
    DecisionExecution single = execution(1L, "MANUAL_REQUIRED");
    when(decisionExecutionRepository.findConvergedInWindow(FROM, TO)).thenReturn(List.of(single));
    ClaimCase singleCase = claim(1L, "FINISHED", "", "");
    when(claimCaseRepository.findByIds(anyList())).thenReturn(List.of(singleCase));

    EvalSampleExport export = service.export(FROM, TO, 100);

    assertThat(export.samples()).hasSize(1);
    Sample only = export.samples().get(0);
    assertThat(only.agreement()).isEqualTo("UNKNOWN_NO_BINARY_VERDICT");
    assertThat(only.agreementBasis()).contains("不计入准确率");
    assertThat(only.humanInvolved()).isFalse();
    // 导出路径也要带上聚合：只有样本、没有聚合，调用方就会自己去数（多套口径）。
    assertThat(export.agreementSummary().unknownNoBinaryVerdict()).isEqualTo(1);
    assertThat(export.agreementSummary().evaluableCount()).isZero();
    assertThat(export.agreementSummary().agreementRate()).isNull();
  }

  /** 派生字段必须真的出现在 JSON 里：文档承诺了 labelSource / splitPolicy，返回体缺字段就是静默失约。 */
  @Test
  void derivedFieldsAreSerialized() throws Exception {
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(List.of());

    // findAndRegisterModules 注册 JSR-310：裸 ObjectMapper 不认识 Instant，会误报成「字段不存在」。
    String json =
        new ObjectMapper()
            .findAndRegisterModules()
            .writeValueAsString(service.export(FROM, TO, 10));

    assertThat(json)
        .contains("\"labelSource\"")
        .contains("\"splitPolicy\"")
        .contains("\"generatedAt\"");
    assertThat(json).contains("eval-only");
    // 聚合与「不可计算」语义必须出现在返回体里：null 被吞掉，调用方就只能看到「没这个字段」。
    assertThat(json).contains("\"agreementSummary\"").contains("\"rateMeaning\"");
    assertThat(json).contains("\"agreementRate\":null");
  }

  // ===== 夹具 =====

  private static Sample sample(String key, String aiResult, String finalStatus, String agreement) {
    return new Sample(
        key,
        "OUTPATIENT",
        aiResult,
        "AUTO",
        new BigDecimal("0.9000"),
        "m1",
        "p1",
        "RISK_HIT",
        finalStatus,
        new BigDecimal("120.00"),
        "",
        false,
        agreement,
        "依据");
  }

  private static DecisionExecution execution(long claimId, String decisionResult) {
    DecisionExecution execution = org.mockito.Mockito.mock(DecisionExecution.class);
    lenient().when(execution.getClaimId()).thenReturn(claimId);
    lenient().when(execution.getDecisionResult()).thenReturn(decisionResult);
    lenient().when(execution.getDecisionTier()).thenReturn("AUTO");
    lenient().when(execution.getAiConfidence()).thenReturn(new BigDecimal("0.9000"));
    lenient().when(execution.getModelVersion()).thenReturn("m1");
    lenient().when(execution.getPromptVersion()).thenReturn("p1");
    lenient().when(execution.getTriageReason()).thenReturn("RISK_HIT");
    return execution;
  }

  private static ClaimCase claim(
      long id, String status, String approvedAmount, String firstReviewerId) {
    ClaimCase claim = org.mockito.Mockito.mock(ClaimCase.class);
    lenient().when(claim.getId()).thenReturn(id);
    lenient().when(claim.getClaimType()).thenReturn("OUTPATIENT");
    lenient().when(claim.getStatus()).thenReturn(status);
    lenient()
        .when(claim.getApprovedAmount())
        .thenReturn(approvedAmount.isEmpty() ? null : new BigDecimal(approvedAmount));
    lenient().when(claim.getRejectType()).thenReturn("");
    lenient().when(claim.getFirstReviewerId()).thenReturn(firstReviewerId);
    lenient().when(claim.getSecondReviewerId()).thenReturn("");
    return claim;
  }
}
