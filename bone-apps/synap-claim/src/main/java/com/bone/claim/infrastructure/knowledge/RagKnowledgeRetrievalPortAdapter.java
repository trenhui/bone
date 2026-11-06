package com.bone.claim.infrastructure.knowledge;

import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.domain.model.decision.KnowledgeBasis;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.infrastructure.evidence.EvidenceFieldReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * D3 受控 RAG 组合适配器：目录准入核验逻辑不变 + 语义检索支路（bone-ai-rag）。
 *
 * <p>目录准入是第一道闸：准入已通过（available=true）原样返回，语义支路不改变结论；仅
 * 「目录未收录（CATALOG_UNKNOWN）且有可检索的临床编码」时进语义支路补查条款出处。 检索词取自感知层结构化事实的 diagnosis_code /
 * drug_code（与目录准入同一读取入口 {@link EvidenceFieldReader}），docScope 置 null（租户级检索）。
 *
 * <p><b>引用强制</b>：检索结果必须带 clauseRef，缺失引用的结果丢弃并计数（bone-ai-rag 检索链已前置剔除无出处切片，此处为消费侧二道防线）；全无命中 →
 * RAG_CORPUS_MISSING； 检索异常 → RAG_RETRIEVAL_UNAVAILABLE；命中 → RAG_CLAUSE_CITED。引用明细暂随检索事件留痕
 * （RagEventSink），KnowledgeBasis 只记成因码——端口契约不变。
 *
 * <p>门控 {@code bone.claim.knowledge.rag.enabled} 默认 false：本 Bean 不装配， 知识层行为与现状完全一致（默认配置不变现网行为）。
 */
@Primary
@Component
@ConditionalOnProperty(name = "bone.claim.knowledge.rag.enabled", havingValue = "true")
public class RagKnowledgeRetrievalPortAdapter implements KnowledgeRetrievalPort {

  private static final BigDecimal CONFIDENCE_DELTA = new BigDecimal("0.05");

  private final ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> catalog;
  private final RagApplicationService rag;
  private final EvidenceRepository evidenceRepository;

  public RagKnowledgeRetrievalPortAdapter(
      ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> catalog,
      RagApplicationService rag,
      EvidenceRepository evidenceRepository) {
    this.catalog = catalog;
    this.rag = rag;
    this.evidenceRepository = evidenceRepository;
  }

  @Override
  public KnowledgeResult match(ClaimContextPort.ClaimContextView ctx) {
    CatalogKnowledgeRetrievalPortAdapter admission = catalog.getIfAvailable();
    if (admission == null) {
      return unavailable(KnowledgeBasis.STUB_NOT_INTEGRATED.code());
    }
    KnowledgeResult base = admission.match(ctx);
    if (base.available() || KnowledgeBasis.of(base.basis()) != KnowledgeBasis.CATALOG_UNKNOWN) {
      return base; // 准入通过 / 成因不是「目录未收录」：语义支路不改变结论
    }
    List<String> terms = queryTerms(ctx.claimId());
    if (terms.isEmpty()) {
      return base; // 无检索素材：维持目录准入成因码
    }
    RagApplicationService.RagRetrieveOutcome outcome;
    try {
      outcome = rag.retrieve(new RagQuery(String.join(" ", terms), null, RagQuery.DEFAULT_TOP_K));
    } catch (RuntimeException ex) {
      return unavailable(KnowledgeBasis.RAG_RETRIEVAL_UNAVAILABLE.code());
    }
    Set<String> citedRefs = new LinkedHashSet<>();
    for (RagResult r : outcome.results()) {
      if (r.clauseRef() == null || r.clauseRef().isBlank()) {
        continue; // 消费侧引用强制二道防线：无引用切片丢弃
      }
      citedRefs.add(r.clauseRef());
    }
    if (citedRefs.isEmpty()) {
      return unavailable(KnowledgeBasis.RAG_CORPUS_MISSING.code());
    }
    return new KnowledgeResult(
        true, CONFIDENCE_DELTA, KnowledgeBasis.RAG_CLAUSE_CITED.code(), true);
  }

  /** 检索词：感知层结构化事实里的临床编码（诊断 + 药品），与目录准入同源。 */
  private List<String> queryTerms(long claimId) {
    Set<String> terms = new LinkedHashSet<>();
    for (Evidence evidence : evidenceRepository.findByClaimId(claimId)) {
      for (EvidenceFieldReader.ParsedField field :
          EvidenceFieldReader.read(evidence.getExtractedJson())) {
        if (field.value().isBlank()) {
          continue;
        }
        if ("diagnosis_code".equals(field.code()) || "drug_code".equals(field.code())) {
          terms.add(field.value());
        }
      }
    }
    return new ArrayList<>(terms);
  }

  private static KnowledgeResult unavailable(String basis) {
    return new KnowledgeResult(false, BigDecimal.ZERO, basis, false);
  }
}
