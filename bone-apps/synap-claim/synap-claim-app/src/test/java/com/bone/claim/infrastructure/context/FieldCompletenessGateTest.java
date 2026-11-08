package com.bone.claim.infrastructure.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ClaimContextPort.ClaimContextView;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 初审「字段级完整性」汇入判据的表征钉子（2026-11-07 第 10 轮循环复核）。
 *
 * <p>取证结论：{@code ParsedDocument.complete}（必填字段齐备）此前只映射到 {@code OcrStatus} （缺字段→MANUAL），<b>从未汇入
 * {@code ClaimContextView.evidenceAllParsed()}</b>，于是 决策图只拿到「有材料 / 都解析了」两个布尔，与能力差距评估登记的
 * 「完整性校验退化为布尔」一致。
 *
 * <p>修复采用 <b>opt-in 标记</b>：仅当 {@code extracted_json} 顶层显式带布尔 {@code complete} 键时采信它；键缺失 / 坏 JSON /
 * 手工回填 {@code {"amount":...}} 一律回退 {@code true}， 保持既有语义（人工补录与外部 OCR 回调按设计算「解析完成」）。这不是放宽—— 真实 {@code
 * POST /{id}/parse} 与 {@code parse-image} 的产物都带 {@code complete} 键， 缺必填字段时判据才会真正收紧到 {@code false}。
 *
 * <p>这组用例必须与「无标记回退」成对：只测收紧会把实现推向「恒 false」（全量退补件， 与 #25 的 MANDATORY 无端口同型）；只测回退又会漏掉收紧本身。
 */
@ExtendWith(MockitoExtension.class)
class FieldCompletenessGateTest {

  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 42L;

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private EvidenceRepository evidenceRepository;

  private LocalClaimContextPortAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LocalClaimContextPortAdapter(claimCaseRepository, evidenceRepository);
    ClaimCase claim = mock(ClaimCase.class);
    lenient().when(claim.getClaimType()).thenReturn("OUTPATIENT");
    lenient().when(claim.claimAmount()).thenReturn(new BigDecimal("100.00"));
    lenient().when(claimCaseRepository.findById(CLAIM_ID)).thenReturn(claim);
  }

  @Test
  @DisplayName("显式 complete=false 必须让证据齐备判据收紧（缺必填字段不得静默放行）")
  void declaredIncompleteTightensParsedGate() {
    givenEvidence(json(false));
    assertThat(adapter.getContext(CLAIM_ID).evidenceAllParsed()).isFalse();
  }

  @Test
  @DisplayName("显式 complete=true 保持齐备（合规正例，防恒假）")
  void declaredCompleteKeepsParsedGate() {
    givenEvidence(json(true));
    assertThat(adapter.getContext(CLAIM_ID).evidenceAllParsed()).isTrue();
  }

  @Test
  @DisplayName("无 complete 键的手工回填 JSON 回退为齐备（既有 E2E 零回归钉子）")
  void missingMarkerFallsBackToLegacySemantics() {
    givenEvidence("{\"amount\":1234.56}");
    assertThat(adapter.getContext(CLAIM_ID).evidenceAllParsed()).isTrue();
  }

  @Test
  @DisplayName("坏 JSON / 空串 / null / 非布尔 complete 一律回退，绝不因解析失败误判为不齐备")
  void malformedOrNonBooleanMarkerFallsBack() {
    for (String bad :
        new String[] {
          "not-json",
          "{}",
          "",
          "{\"complete\":\"false\"}",
          "{\"complete\":1}",
          "{\"complete\":null}",
          null
        }) {
      givenEvidence(bad);
      ClaimContextView view = adapter.getContext(CLAIM_ID);
      assertThat(view.evidenceAllParsed()).as("payload=%s", bad).isTrue();
    }
  }

  @Test
  @DisplayName("多材料中任一显式不齐备即整体不齐备（判据是 allMatch 而非 anyMatch）")
  void anyIncompleteEvidenceTightensWholeGate() {
    List<Evidence> evidences = new ArrayList<>();
    evidences.add(evidence(json(true)));
    evidences.add(evidence(json(false)));
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(evidences);
    assertThat(adapter.getContext(CLAIM_ID).evidenceAllParsed()).isFalse();
  }

  @Test
  @DisplayName("FAILED 状态即使带 complete=true 仍不齐备（状态闸不得被标记绕过）")
  void failedStatusStillBlocksParsedGate() {
    Evidence evidence =
        Evidence.register(
            7L, TENANT_ID, CLAIM_ID, DocType.INVOICE, "file-ref", "hash-1", "UNKNOWN");
    evidence.markParsed(OcrStatus.FAILED, null, json(true), null);
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of(evidence));
    assertThat(adapter.getContext(CLAIM_ID).evidenceAllParsed()).isFalse();
  }

  private static String json(boolean complete) {
    return "{\"available\":true,\"complete\":"
        + complete
        + ",\"fields\":[{\"code\":\"total_amount\",\"value\":\"100.00\"}]}";
  }

  private static Evidence evidence(String extractedJson) {
    Evidence evidence =
        Evidence.register(
            7L, TENANT_ID, CLAIM_ID, DocType.INVOICE, "file-ref", "hash-1", "UNKNOWN");
    evidence.markParsed(OcrStatus.DONE, new BigDecimal("0.92"), extractedJson, null);
    return evidence;
  }

  private void givenEvidence(String extractedJson) {
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of(evidence(extractedJson)));
  }
}
