package com.bone.claim.application;

import com.bone.claim.application.port.out.InvoiceVerificationPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发票验真 / 验重用例：把感知层结构化事实交给 {@link InvoiceVerificationPort}，结果落回证据聚合。
 *
 * <p>结果落在既有 {@code t_claim_evidence.verify_valid} 列，由 {@code LocalClaimContextPortAdapter} 读进
 * {@code evidenceAllVerifyPassed}，再经 R-FRAUD-06 规则进入决策图硬闸——因此本用例<b>不需要</b>
 * 扩展决策上下文或修改决策图节点，验真结论天然在放款前生效。
 */
@Service
@RequiredArgsConstructor
public class InvoiceVerificationApplicationService {

  private final EvidenceRepository evidenceRepository;
  private final InvoiceVerificationPort invoiceVerificationPort;
  private final DomainEventPublisher domainEventPublisher;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional
  public InvoiceVerificationPort.VerificationResult verify(long evidenceId) {
    Evidence evidence = evidenceRepository.findById(evidenceId);
    if (evidence == null) {
      throw ClaimErrors.supplier(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, evidenceId).get();
    }
    Map<String, String> fields = fieldsOf(evidence.getExtractedJson());
    BigDecimal invoiceAmount = decimalOf(fields.get("total_amount"));
    BigDecimal itemTotal = itemTotalOf(evidence);
    InvoiceVerificationPort.VerificationResult result =
        invoiceVerificationPort.verify(
            new InvoiceVerificationPort.InvoiceFacts(
                evidence.getClaimId(),
                evidence.getTenantId(),
                fields.get("invoice_code"),
                fields.get("invoice_no"),
                invoiceAmount,
                itemTotal,
                fields.get("invoice_date")));
    evidence.markVerified(result.verifyValid());
    evidenceRepository.save(evidence);
    domainEventPublisher.publishFrom(evidence);
    return result;
  }

  /** 清单类材料的金额合计；无清单证据时返回 null（不按 0 判「差额 >1 元」误伤）。 */
  private BigDecimal itemTotalOf(Evidence target) {
    List<Evidence> evidences = evidenceRepository.findByClaimId(target.getClaimId());
    if (evidences == null) {
      return null;
    }
    BigDecimal sum = BigDecimal.ZERO;
    boolean found = false;
    for (Evidence other : evidences) {
      if (other == null || other.getId().equals(target.getId())) {
        continue;
      }
      BigDecimal amount = decimalOf(fieldsOf(other.getExtractedJson()).get("total_amount"));
      if (amount != null) {
        sum = sum.add(amount);
        found = true;
      }
    }
    return found ? sum : null;
  }

  /** 兼容本域解析器与外部回调：只读 {@code fields[].code/value}，损坏 JSON 视为无事实。 */
  private Map<String, String> fieldsOf(String extractedJson) {
    Map<String, String> values = new LinkedHashMap<>();
    if (extractedJson == null || extractedJson.isBlank()) {
      return values;
    }
    try {
      JsonNode fields = objectMapper.readTree(extractedJson).path("fields");
      if (!fields.isArray()) {
        return values;
      }
      for (JsonNode field : fields) {
        String code = field.path("code").asText("");
        String value = field.path("value").asText("");
        if (!code.isBlank() && !value.isBlank()) {
          values.putIfAbsent(code, value);
        }
      }
    } catch (Exception ex) {
      // 解析产物损坏 → 返回空事实，由端口判 INVOICE_KEY_MISSING（fail-closed），不抛异常打断材料流程。
    }
    return values;
  }

  private static BigDecimal decimalOf(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value.replace(",", "").trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
