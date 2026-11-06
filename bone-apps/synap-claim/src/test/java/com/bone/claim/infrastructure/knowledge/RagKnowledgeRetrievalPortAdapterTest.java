package com.bone.claim.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.model.RagResult;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.domain.repository.EvidenceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/** RAG 组合适配器：目录准入不变 + 语义支路三态（命中带引用/语料缺失/检索异常）+ 门控口径。 */
class RagKnowledgeRetrievalPortAdapterTest {

  private static final ClaimContextPort.ClaimContextView CTX =
      Mockito.mock(ClaimContextPort.ClaimContextView.class);

  static {
    when(CTX.claimId()).thenReturn(9001L);
  }

  /** 感知层事实桩：一条 diagnosis_code，让语义支路有检索词可取。 */
  private static EvidenceRepository evidenceWithDiagnosisCode() {
    EvidenceRepository repo = mock(EvidenceRepository.class);
    com.bone.claim.domain.model.evidence.Evidence evidence =
        mock(com.bone.claim.domain.model.evidence.Evidence.class);
    when(evidence.getExtractedJson())
        .thenReturn("{\"fields\":[{\"code\":\"diagnosis_code\",\"value\":\"J06.9\"}]}");
    when(repo.findByClaimId(9001L)).thenReturn(List.of(evidence));
    return repo;
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> providerOf(
      CatalogKnowledgeRetrievalPortAdapter catalog) {
    ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> p = Mockito.mock(ObjectProvider.class);
    when(p.getIfAvailable()).thenReturn(catalog);
    return p;
  }

  private static CatalogKnowledgeRetrievalPortAdapter catalogReturning(
      boolean available, String basis) {
    CatalogKnowledgeRetrievalPortAdapter catalog = mock(CatalogKnowledgeRetrievalPortAdapter.class);
    when(catalog.match(any()))
        .thenReturn(
            new com.bone.claim.application.port.out.KnowledgeRetrievalPort.KnowledgeResult(
                available,
                available ? new java.math.BigDecimal("0.05") : java.math.BigDecimal.ZERO,
                basis,
                available));
    return catalog;
  }

  private static RagApplicationService ragReturning(List<RagResult> results) {
    RagApplicationService rag = mock(RagApplicationService.class);
    when(rag.retrieve(any()))
        .thenReturn(new RagApplicationService.RagRetrieveOutcome(results, List.of()));
    return rag;
  }

  @Test
  @DisplayName("目录准入已通过：原样返回，不调 RAG（准入是第一道闸）")
  void catalogPassThrough() {
    RagApplicationService rag = ragReturning(List.of());
    var adapter =
        new RagKnowledgeRetrievalPortAdapter(
            providerOf(catalogReturning(true, "CATALOG_ADMISSION_VERIFIED")),
            rag,
            evidenceWithDiagnosisCode());

    var result = adapter.match(CTX);

    assertThat(result.available()).isTrue();
    assertThat(result.basis()).isEqualTo("CATALOG_ADMISSION_VERIFIED");
    verify(rag, never()).retrieve(any());
  }

  @Test
  @DisplayName("命中带 clauseRef → RAG_CLAUSE_CITED；无引用结果丢弃计数")
  void hitWithCitation() {
    RagApplicationService rag =
        ragReturning(
            List.of(
                new RagResult("r1", "ML-4.2.1", 1, 9, 0.9, "s", "text"),
                new RagResult("r2", "", 1, 9, 0.8, "s", "no-ref")));
    var adapter =
        new RagKnowledgeRetrievalPortAdapter(
            providerOf(catalogReturning(false, "CATALOG_UNKNOWN")),
            rag,
            evidenceWithDiagnosisCode());

    var result = adapter.match(CTX);

    assertThat(result.available()).isTrue();
    assertThat(result.basis()).isEqualTo("RAG_CLAUSE_CITED");
  }

  @Test
  @DisplayName("检索空结果 → RAG_CORPUS_MISSING（available=false，fail-closed）")
  void corpusMissing() {
    var adapter =
        new RagKnowledgeRetrievalPortAdapter(
            providerOf(catalogReturning(false, "CATALOG_UNKNOWN")),
            ragReturning(List.of()),
            evidenceWithDiagnosisCode());

    var result = adapter.match(CTX);

    assertThat(result.available()).isFalse();
    assertThat(result.basis()).isEqualTo("RAG_CORPUS_MISSING");
  }

  @Test
  @DisplayName("检索异常 → RAG_RETRIEVAL_UNAVAILABLE（不抛出，fail-closed 转人工）")
  void retrievalUnavailable() {
    RagApplicationService rag = mock(RagApplicationService.class);
    when(rag.retrieve(any())).thenThrow(new IllegalStateException("scope over limit"));
    var adapter =
        new RagKnowledgeRetrievalPortAdapter(
            providerOf(catalogReturning(false, "CATALOG_UNKNOWN")),
            rag,
            evidenceWithDiagnosisCode());

    var result = adapter.match(CTX);

    assertThat(result.available()).isFalse();
    assertThat(result.basis()).isEqualTo("RAG_RETRIEVAL_UNAVAILABLE");
  }

  @Test
  @DisplayName("非 CATALOG_UNKNOWN 成因 / 无检索词：维持目录准入成因码，不进检索支路")
  void nonRetrievableCausesStayPut() {
    RagApplicationService rag = ragReturning(List.of());
    var adapter =
        new RagKnowledgeRetrievalPortAdapter(
            providerOf(catalogReturning(false, "NO_PARSED_FACTS")),
            rag,
            evidenceWithDiagnosisCode());

    assertThat(adapter.match(CTX).basis()).isEqualTo("NO_PARSED_FACTS");
    verify(rag, never()).retrieve(any());
  }

  @Test
  @DisplayName("目录准入适配器缺席（knowledge.mode=stub）：如实给 STUB_NOT_INTEGRATED")
  void catalogAbsentIsHonest() {
    @SuppressWarnings("unchecked")
    ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> absent =
        Mockito.mock(ObjectProvider.class);
    when(absent.getIfAvailable()).thenReturn(null);
    var adapter =
        new RagKnowledgeRetrievalPortAdapter(
            absent, ragReturning(List.of()), evidenceWithDiagnosisCode());

    assertThat(adapter.match(CTX).basis()).isEqualTo("STUB_NOT_INTEGRATED");
  }
}
