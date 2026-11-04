package com.bone.claim.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.model.reference.RefDisease;
import com.bone.claim.domain.model.reference.RefDrug;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.RefDiseaseRepository;
import com.bone.claim.domain.repository.RefDrugRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 知识层：只有「事实齐备 + 目录命中」才允许 available=true，其余一律 fail-closed。 */
@ExtendWith(MockitoExtension.class)
class CatalogKnowledgeRetrievalPortAdapterTest {

  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 42L;

  @Mock private EvidenceRepository evidenceRepository;
  @Mock private RefDiseaseRepository refDiseaseRepository;
  @Mock private RefDrugRepository refDrugRepository;
  @Mock private TenantPort tenantPort;

  private CatalogKnowledgeRetrievalPortAdapter adapter;

  private final ClaimContextPort.ClaimContextView ctx =
      new ClaimContextPort.ClaimContextView(
          CLAIM_ID,
          "OUTPATIENT",
          new BigDecimal("300.00"),
          true,
          true,
          new BigDecimal("0.92"),
          true);

  @BeforeEach
  void setUp() {
    adapter =
        new CatalogKnowledgeRetrievalPortAdapter(
            evidenceRepository, refDiseaseRepository, refDrugRepository, tenantPort);
  }

  @Test
  @DisplayName("诊断目录命中：能力可用且核验范围明确落在 basis 受控码")
  void verifiedWhenFactAndCatalogMatch() {
    givenFacts(
        "[{\"code\":\"diagnosis_code\",\"value\":\"J06.9\",\"confidence\":0.85,"
            + "\"evidenceRef\":\"text@10:5\"}]");
    when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    when(refDiseaseRepository.findByDiseaseCode(TENANT_ID, "J06.9"))
        .thenReturn(RefDisease.of(1L, TENANT_ID, "J06.9", "急性上呼吸道感染", ""));

    KnowledgeRetrievalPort.KnowledgeResult result = adapter.match(ctx);

    assertThat(result.available()).isTrue();
    assertThat(result.clinicallyReasonable()).isTrue();
    assertThat(result.confidenceDelta()).isEqualByComparingTo(new BigDecimal("0.05"));
    assertThat(result.basis()).isEqualTo(CatalogKnowledgeRetrievalPortAdapter.BASIS_VERIFIED);
  }

  @Test
  @DisplayName("无解析事实：保持不可用，绝不把空输入解释成已核验")
  void noParsedFactFailsClosed() {
    givenFacts("[]");

    KnowledgeRetrievalPort.KnowledgeResult result = adapter.match(ctx);

    assertThat(result.available()).isFalse();
    assertThat(result.basis()).isEqualTo(CatalogKnowledgeRetrievalPortAdapter.BASIS_NO_FACTS);
  }

  @Test
  @DisplayName("只有票面事实：临床口径无法判定，不做目录准入外的推断")
  void invoiceOnlyFactsAreInsufficient() {
    givenFacts("[{\"code\":\"total_amount\",\"value\":\"300.00\",\"confidence\":0.95}]");

    KnowledgeRetrievalPort.KnowledgeResult result = adapter.match(ctx);

    assertThat(result.available()).isFalse();
    assertThat(result.basis()).isEqualTo(CatalogKnowledgeRetrievalPortAdapter.BASIS_INSUFFICIENT);
  }

  @Test
  @DisplayName("目录未命中：无法判定（不是「临床不合理」），避免产生无依据的拒赔建议")
  void catalogMissIsUnavailableNotUnreasonable() {
    givenFacts("[{\"code\":\"diagnosis_code\",\"value\":\"J06.9\",\"confidence\":0.85}]");
    when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    when(refDiseaseRepository.findByDiseaseCode(TENANT_ID, "J06.9")).thenReturn(null);

    KnowledgeRetrievalPort.KnowledgeResult result = adapter.match(ctx);

    assertThat(result.available()).isFalse();
    assertThat(result.clinicallyReasonable()).isFalse();
    assertThat(result.basis())
        .isEqualTo(CatalogKnowledgeRetrievalPortAdapter.BASIS_CATALOG_UNKNOWN);
  }

  @Test
  @DisplayName("药品目录未命中同样 fail-closed（诊断命中不足以放行）")
  void drugCatalogMissFailsClosed() {
    givenFacts("[{\"code\":\"drug_code\",\"value\":\"X0001\",\"confidence\":0.85}]");
    when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    when(refDrugRepository.findByDrugCode(TENANT_ID, "X0001")).thenReturn(null);

    KnowledgeRetrievalPort.KnowledgeResult result = adapter.match(ctx);

    assertThat(result.available()).isFalse();
    assertThat(result.basis())
        .isEqualTo(CatalogKnowledgeRetrievalPortAdapter.BASIS_CATALOG_UNKNOWN);
  }

  @Test
  @DisplayName("诊断与药品均命中：可用")
  void allCatalogsMatched() {
    givenFacts(
        "[{\"code\":\"diagnosis_code\",\"value\":\"J06.9\"},"
            + "{\"code\":\"drug_code\",\"value\":\"X0001\"}]");
    when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    when(refDiseaseRepository.findByDiseaseCode(TENANT_ID, "J06.9"))
        .thenReturn(RefDisease.of(1L, TENANT_ID, "J06.9", "急性上呼吸道感染", ""));
    when(refDrugRepository.findByDrugCode(TENANT_ID, "X0001"))
        .thenReturn(RefDrug.of(2L, TENANT_ID, "X0001", "药品甲", 1, "甲类"));

    assertThat(adapter.match(ctx).available()).isTrue();
  }

  @Test
  @DisplayName("损坏的解析 JSON：按无事实处理，不抛异常打断决策图")
  void brokenJsonFailsClosed() {
    givenFacts("not-json");

    KnowledgeRetrievalPort.KnowledgeResult result = adapter.match(ctx);

    assertThat(result.available()).isFalse();
    assertThat(result.basis()).isEqualTo(CatalogKnowledgeRetrievalPortAdapter.BASIS_NO_FACTS);
  }

  private void givenFacts(String fieldsJson) {
    Evidence evidence =
        Evidence.register(
            7L, TENANT_ID, CLAIM_ID, DocType.INVOICE, "file-ref", "hash-1", "UNKNOWN");
    evidence.markParsed(
        OcrStatus.DONE, new BigDecimal("0.92"), "{\"fields\":" + fieldsJson + "}", null);
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of(evidence));
  }
}
