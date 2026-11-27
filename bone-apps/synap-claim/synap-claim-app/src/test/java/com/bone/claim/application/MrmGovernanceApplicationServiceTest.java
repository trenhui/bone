package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.Verdict;
import com.bone.claim.domain.model.evaluation.AiModelCard;
import com.bone.claim.domain.model.evaluation.AiModelCard.DriftVerdict;
import com.bone.claim.domain.model.evaluation.AiModelCard.ModelCard;
import com.bone.claim.domain.model.evaluation.AiModelCard.Verification;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import com.bone.claim.infrastructure.mrm.MrmModelCardRegistry;
import com.bone.claim.infrastructure.mrm.MrmModelCardRegistry.DriftObservation;
import com.bone.claim.infrastructure.mrm.MrmModelCardRegistry.LoadStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MRM 治理只读服务：空清单/无样本/样本不足 fail-closed，分组样本量可见，漂移复用内核判据。 */
class MrmGovernanceApplicationServiceTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 11, 27);
  private static final String SHA = "a".repeat(64);
  private static final DriftObservation NO_OBSERVATION =
      new DriftObservation(null, new BigDecimal("0.10"), new BigDecimal("0.20"));

  private BlindLabelTaskRepository repository;

  @BeforeEach
  void setUp() {
    repository = mock(BlindLabelTaskRepository.class);
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of());
  }

  private MrmGovernanceApplicationService service(
      List<ModelCard> cards, DriftObservation drift, LoadStatus status) {
    return new MrmGovernanceApplicationService(
        new MrmModelCardRegistry(cards, drift, status), repository);
  }

  private static ModelCard card(String id, Verification verification, LocalDate nextReview) {
    return new ModelCard(
        id,
        "prompt-v1",
        "理赔分诊",
        "内部脱敏数据",
        SHA,
        "金额段×渠道",
        "小样本切片不稳定",
        "PSI 超阈值触发复验",
        "risk-team",
        verification,
        nextReview);
  }

  private static BlindLabelTask paired(
      long id, double confidence, String aiDecision, String human, String channel) {
    BlindLabelTask t =
        BlindLabelTask.open(
            id,
            42L,
            "BL-" + id,
            9001L,
            "annotator-1",
            "{\"claimNo\":\"C-" + id + "\",\"channel\":\"" + channel + "\"}",
            "a".repeat(64));
    t.submit("{\"decision\":\"" + human + "\"}", Instant.now());
    t.unblind(
        "{\"aiConfidence\":" + confidence + ",\"decisionResult\":\"" + aiDecision + "\"}",
        BlindLabelTask.Agreement.PAIRED,
        Instant.now());
    return t;
  }

  @Test
  @DisplayName("空清单：status=NO_MODEL_CARDS，inventory.clean() 为真也不得被误读为治理健康")
  void emptyRegistryIsNotHealthy() {
    var report = service(List.of(), NO_OBSERVATION, LoadStatus.NO_MODEL_CARDS).governance(AS_OF);

    assertThat(report.modelCardStatus()).isEqualTo("NO_MODEL_CARDS");
    assertThat(report.status()).isEqualTo("NO_MODEL_CARDS");
    assertThat(report.modelCards()).isEmpty();
    assertThat(report.inventory().total()).isZero();
    assertThat(report.inventory().clean()).isTrue(); // 内核口径：空清单无 blocker
    assertThat(report.note()).contains("不代表治理健康");
    assertThat(report.championChallenger().status()).isEqualTo("NO_EVIDENCE");
  }

  @Test
  @DisplayName("无样本：champion-challenger=NO_EVIDENCE 且不给优劣结论；漂移无 PSI 观测为 NO_DATA")
  void noSamplesAndNoPsi() {
    var report = service(List.of(), NO_OBSERVATION, LoadStatus.NO_MODEL_CARDS).governance(AS_OF);

    assertThat(report.driftVerdict()).isEqualTo(DriftVerdict.NO_DATA);
    assertThat(report.psi()).isNull();
    assertThat(report.championChallenger().status()).isEqualTo("NO_EVIDENCE");
    assertThat(report.championChallenger().reason()).isEqualTo("NO_BLIND_LABEL_PAIRS");
    assertThat(report.championChallenger().conclusion()).isNull();
    assertThat(report.championChallenger().sampleCount()).isZero();
  }

  @Test
  @DisplayName("样本不足：champion=INSUFFICIENT_SAMPLES；bias 低样本切片不给任何数值/结论")
  void insufficientSamples() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of(paired(1L, 0.9, "APPROVE", "APPROVE", "AUTO")));
    var svc = service(List.of(), NO_OBSERVATION, LoadStatus.NO_MODEL_CARDS);

    var gov = svc.governance(AS_OF);
    var bias = svc.biasMonitoring("CHANNEL", null);

    assertThat(gov.championChallenger().status()).isEqualTo("INSUFFICIENT_SAMPLES");
    assertThat(gov.championChallenger().evidenceVerdict())
        .isEqualTo(Verdict.INSUFFICIENT_SAMPLES.name());
    assertThat(gov.championChallenger().conclusion()).isNull();
    assertThat(bias.status()).isEqualTo("INSUFFICIENT_SAMPLES");
    assertThat(bias.slices()).hasSize(1);
    var slice = bias.slices().get(0);
    assertThat(slice.sliceKey()).isEqualTo("AUTO");
    assertThat(slice.sampleCount()).isEqualTo(1);
    assertThat(slice.verdict()).isEqualTo("INSUFFICIENT_SAMPLES");
    assertThat(slice.agreeCount()).isNull();
    assertThat(slice.agreementRate()).isNull();
    assertThat(slice.conclusion()).isNull();
  }

  @Test
  @DisplayName("按真实 CHANNEL 分组：每组样本量可见，达标组才给观测值")
  void channelGroupsVisible() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(
            List.of(
                paired(1L, 0.9, "APPROVE", "APPROVE", "AUTO"),
                paired(2L, 0.8, "REVIEW", "APPROVE", "AUTO"),
                paired(3L, 0.7, "APPROVE", "APPROVE", "MANUAL")));
    var svc = service(List.of(), NO_OBSERVATION, LoadStatus.NO_MODEL_CARDS);

    var bias = svc.biasMonitoring("CHANNEL", 2);

    assertThat(bias.effectiveMinSamplesPerSlice()).isEqualTo(2);
    assertThat(bias.totalSamples()).isEqualTo(3);
    var byKey =
        bias.slices().stream()
            .collect(java.util.stream.Collectors.toMap(r -> r.sliceKey(), r -> r));
    assertThat(byKey.get("AUTO").sampleCount()).isEqualTo(2);
    assertThat(byKey.get("AUTO").verdict()).isEqualTo("OBSERVABLE");
    assertThat(byKey.get("AUTO").agreeCount()).isEqualTo(1);
    assertThat(byKey.get("AUTO").agreementRate()).isNotNull();
    assertThat(byKey.get("MANUAL").sampleCount()).isEqualTo(1);
    assertThat(byKey.get("MANUAL").verdict()).isEqualTo("INSUFFICIENT_SAMPLES");
    assertThat(byKey.get("MANUAL").agreementRate()).isNull();
    assertThat(byKey.get("AUTO").conclusion()).isNull();
  }

  @Test
  @DisplayName("按真实 DECISION_RESULT 分组：维度取自 AI 建议快照，不编造")
  void decisionResultGroupVisible() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(
            List.of(
                paired(1L, 0.9, "APPROVE", "APPROVE", "AUTO"),
                paired(2L, 0.8, "REVIEW", "APPROVE", "AUTO")));
    var svc = service(List.of(), NO_OBSERVATION, LoadStatus.NO_MODEL_CARDS);

    var bias = svc.biasMonitoring("DECISION_RESULT", 1);

    assertThat(bias.slices())
        .extracting(r -> r.sliceKey())
        .containsExactlyInAnyOrder("APPROVE", "REVIEW");
    assertThat(bias.slices()).allSatisfy(r -> assertThat(r.sampleCount()).isEqualTo(1));
  }

  @Test
  @DisplayName("机构等级维度无真实字段：DIMENSION_UNAVAILABLE 并说明需样本携带字段")
  void institutionLevelUnavailable() {
    var bias =
        service(List.of(), NO_OBSERVATION, LoadStatus.NO_MODEL_CARDS)
            .biasMonitoring("INSTITUTION_LEVEL", null);

    assertThat(bias.status()).isEqualTo("DIMENSION_UNAVAILABLE");
    assertThat(bias.slices()).isEmpty();
    assertThat(bias.note()).contains("机构等级");
  }

  @Test
  @DisplayName("未验证卡不得标已验证：清单报告如实暴露 unverified 与 blocker")
  void unverifiedCardNotMarkedVerified() {
    var cards =
        List.of(
            card("self-only", Verification.SELF_TEST_ONLY, AS_OF.plusDays(10)),
            card("verified", Verification.INDEPENDENT_VERIFIED, AS_OF.minusDays(1)));
    var report = service(cards, NO_OBSERVATION, LoadStatus.LOADED).governance(AS_OF);

    assertThat(report.modelCardStatus()).isEqualTo("LOADED");
    assertThat(report.status()).isEqualTo("INVENTORY_BLOCKED");
    assertThat(report.inventory().total()).isEqualTo(2);
    assertThat(report.inventory().unverified()).isEqualTo(1);
    assertThat(report.inventory().reviewOverdue()).isEqualTo(1);
    assertThat(report.inventory().clean()).isFalse();
    assertThat(report.inventory().blockers()).anyMatch(b -> b.contains("未经二线独立验证"));
    assertThat(report.modelCards().get(0).eligibleForProduction(AS_OF)).isFalse();
  }

  @Test
  @DisplayName("漂移判据复用 AiModelCard.drift：同一阈值下 0.15 落 WATCH，不另写口径")
  void driftReusesKernel() {
    var drift =
        new DriftObservation(
            new BigDecimal("0.15"), new BigDecimal("0.10"), new BigDecimal("0.20"));
    var report = service(List.of(), drift, LoadStatus.LOADED).governance(AS_OF);

    assertThat(report.driftVerdict()).isEqualTo(DriftVerdict.WATCH);
    assertThat(report.driftVerdict())
        .isEqualTo(AiModelCard.drift(drift.psi(), drift.watchRatio(), drift.threshold()));
  }

  @Test
  @DisplayName("minSamplesPerSlice 缺省/上限保护：null→30，1→1（离线口径），超大→500")
  void minSamplesGuard() {
    assertThat(MrmGovernanceApplicationService.effectiveMinSamplesPerSlice(null)).isEqualTo(30);
    assertThat(MrmGovernanceApplicationService.effectiveMinSamplesPerSlice(1)).isEqualTo(1);
    assertThat(MrmGovernanceApplicationService.effectiveMinSamplesPerSlice(100)).isEqualTo(100);
    assertThat(MrmGovernanceApplicationService.effectiveMinSamplesPerSlice(Integer.MAX_VALUE))
        .isEqualTo(500);
  }

  @Test
  @DisplayName("只读：两次查询只有仓储读，零写交互")
  void readOnly() {
    var svc = service(List.of(), NO_OBSERVATION, LoadStatus.NO_MODEL_CARDS);

    svc.governance(AS_OF);
    svc.biasMonitoring("CHANNEL", null);

    verify(repository, org.mockito.Mockito.times(2))
        .findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT);
    verifyNoMoreInteractions(repository);
  }
}
