package com.bone.claim.infrastructure.knowledge;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.decision.KnowledgeBasis;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.RefDiseaseRepository;
import com.bone.claim.domain.repository.RefDrugRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 知识层一期实现：基于感知层结构化事实与租户医学目录做受控核验。
 *
 * <p>解决原 {@link LocalKnowledgeRetrievalPortAdapter} 恒 {@code available=false} 导致决策图 无条件 fail-closed
 * 转人工的结构性缺陷。本适配器只在「事实齐备 + 目录命中」时给出 {@code available=true}，任一环节无法判定仍返回 {@code available=false}（保持 P1
 * 红线与 fail-closed）。
 *
 * <p><b>核验范围（写进 {@code basis} 受控码，不得外溢解释）</b>：诊断编码 / 药品编码在租户目录中的准入，
 * 以及解析事实的齐备性。一期<b>不含</b>模型临床推理、适应症图谱与向量检索—— 因此 {@code clinicallyReasonable=true}
 * 的准确含义是「在上述受控范围内未发现差异」， 不是「模型确认临床合理」。二期接 bone-ai C6 受控 RAG 时替换本类，端口契约不变。
 */
@Component
@ConditionalOnProperty(
    name = "bone.claim.knowledge.mode",
    havingValue = "catalog",
    matchIfMissing = true)
public class CatalogKnowledgeRetrievalPortAdapter implements KnowledgeRetrievalPort {

  // 基据码唯一真源在领域枚举（含处置动作）；此处只做别名，避免「适配器写一套、聚合认一套」的漂移。
  static final String BASIS_VERIFIED = KnowledgeBasis.CATALOG_ADMISSION_VERIFIED.code();
  static final String BASIS_NO_FACTS = KnowledgeBasis.NO_PARSED_FACTS.code();
  static final String BASIS_INSUFFICIENT = KnowledgeBasis.INSUFFICIENT_CLINICAL_FACTS.code();
  static final String BASIS_CATALOG_UNKNOWN = KnowledgeBasis.CATALOG_UNKNOWN.code();

  private static final BigDecimal CONFIDENCE_DELTA = new BigDecimal("0.05");

  private final EvidenceRepository evidenceRepository;
  private final RefDiseaseRepository refDiseaseRepository;
  private final RefDrugRepository refDrugRepository;
  private final TenantPort tenantPort;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public CatalogKnowledgeRetrievalPortAdapter(
      EvidenceRepository evidenceRepository,
      RefDiseaseRepository refDiseaseRepository,
      RefDrugRepository refDrugRepository,
      TenantPort tenantPort) {
    this.evidenceRepository = evidenceRepository;
    this.refDiseaseRepository = refDiseaseRepository;
    this.refDrugRepository = refDrugRepository;
    this.tenantPort = tenantPort;
  }

  @Override
  public KnowledgeResult match(ClaimContextPort.ClaimContextView ctx) {
    List<String> diagnoses = new ArrayList<>();
    List<String> drugs = new ArrayList<>();
    boolean anyParsedFact = false;

    for (Evidence evidence : evidenceRepository.findByClaimId(ctx.claimId())) {
      JsonNode fields = fieldsOf(evidence.getExtractedJson());
      for (JsonNode field : fields) {
        String code = field.path("code").asText("");
        String value = field.path("value").asText("");
        if (value.isBlank()) {
          continue;
        }
        anyParsedFact = true;
        if ("diagnosis_code".equals(code)) {
          diagnoses.add(value);
        } else if ("drug_code".equals(code)) {
          drugs.add(value);
        }
      }
    }

    if (!anyParsedFact) {
      return unavailable(BASIS_NO_FACTS);
    }
    if (diagnoses.isEmpty() && drugs.isEmpty()) {
      // 只有金额/票号等票面事实，做不了任何临床口径核验；不能据此宣称"临床合理"。
      return unavailable(BASIS_INSUFFICIENT);
    }

    Long tenantId = tenantPort.currentTenantId();
    for (String diagnosis : diagnoses) {
      if (refDiseaseRepository.findByDiseaseCode(tenantId, diagnosis) == null) {
        return unavailable(BASIS_CATALOG_UNKNOWN);
      }
    }
    for (String drug : drugs) {
      if (refDrugRepository.findByDrugCode(tenantId, drug) == null) {
        return unavailable(BASIS_CATALOG_UNKNOWN);
      }
    }
    return new KnowledgeResult(true, CONFIDENCE_DELTA, BASIS_VERIFIED, true);
  }

  /** 非结构化 / 损坏的解析结果一律视为「没有事实」，不抛异常打断决策图。 */
  private JsonNode fieldsOf(String extractedJson) {
    if (extractedJson == null || extractedJson.isBlank()) {
      return objectMapper.createArrayNode();
    }
    try {
      JsonNode fields = objectMapper.readTree(extractedJson).path("fields");
      return fields.isArray() ? fields : objectMapper.createArrayNode();
    } catch (Exception ex) {
      return objectMapper.createArrayNode();
    }
  }

  private static KnowledgeResult unavailable(String basis) {
    return new KnowledgeResult(false, BigDecimal.ZERO, basis, false);
  }
}
