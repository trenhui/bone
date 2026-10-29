package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A2 证据应用服务（材料登记 / 解析回填 / 锁定）。
 *
 * <p>编排原则（E-6.4）：只做「加载聚合 → 调聚合行为 → 保存」，金额与状态规则一律在聚合内。
 */
@Service
@RequiredArgsConstructor
public class EvidenceApplicationService {

  private final EvidenceRepository evidenceRepository;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;
  private final com.bone.claim.domain.repository.ClaimCaseRepository claimCaseRepository;

  /** 登记材料入参。 */
  public record RegisterEvidence(
      long claimId, DocType docType, String fileRef, String contentHash, String verifyValid) {}

  /**
   * 登记材料（重；复内容哈希 → 409）。
   *
   * <p>重复判定在应用服务做而非依赖 DB 唯一键：唯一键是最后防线（防并发绕过）， 但业务语义需要「返回首次结果」而非抛 DB 异常（§10.3 幂等口径）。
   */
  @Transactional
  public long register(RegisterEvidence command) {
    long tenantId = tenantPort.currentTenantId();
    List<Evidence> existing = evidenceRepository.findByClaimId(command.claimId());
    boolean duplicated =
        existing.stream()
            .anyMatch(
                e ->
                    e.getContentHash() != null && e.getContentHash().equals(command.contentHash()));
    if (duplicated) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVIDENCE_DUPLICATED, command.claimId());
    }
    if (existing.size() >= 20) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVIDENCE_LIMIT_EXCEEDED, command.claimId());
    }

    Evidence evidence =
        Evidence.register(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            command.claimId(),
            command.docType(),
            command.fileRef(),
            command.contentHash(),
            command.verifyValid());
    evidenceRepository.insert(evidence);
    domainEventPublisher.publishFrom(evidence);
    return evidence.getId();
  }

  /** 解析结果回填（OCR/LLM 回调入口）。 */
  @Transactional
  public void markParsed(
      long evidenceId,
      OcrStatus status,
      BigDecimal confidence,
      String extractedJson,
      String verifyValid) {
    Evidence evidence = load(evidenceId);
    evidence.markParsed(status, confidence, extractedJson, verifyValid);
    evidenceRepository.save(evidence);
    domainEventPublisher.publishFrom(evidence);
  }

  /** 人工补录。 */
  @Transactional
  public void fillManually(long evidenceId, String extractedJson, String operatorId) {
    Evidence evidence = load(evidenceId);
    evidence.fillManually(extractedJson, operatorId);
    evidenceRepository.save(evidence);
    domainEventPublisher.publishFrom(evidence);
  }

  /**
   * 锁定案件证据集（理算前置）。
   *
   * <p>逐份锁定并为每份发 {@code EvidenceLockedEvent}（聚合内发），事件数为案件证据份数—— 消费方按 {@code claimId} 聚合即可，无需单条处理。
   */
  @Transactional
  public int lockAll(long claimId, String operatorId) {
    List<Evidence> evidences = evidenceRepository.findByClaimId(claimId);
    if (evidences.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, claimId);
    }
    for (Evidence evidence : evidences) {
      evidence.lock(operatorId, evidences.size());
      evidenceRepository.save(evidence);
      domainEventPublisher.publishFrom(evidence);
    }
    reflowEvidenceSubmitting(claimId, operatorId);
    return evidences.size();
  }

  /**
   * 补录完成回流（21 → 10）：案件处于 {@code EVIDENCE_SUBMITTING}（核赔员要求补件）且证据集被 再次锁定时，视为「补录完成」——聚合的 {@code
   * evidenceSubmitted} 是唯一合法回流入口。
   *
   * <p>此前 {@code evidenceSubmitted} 只有聚合方法与应用服务方法、没有任何适配器调用它： 补件在界面上传完成后案件永远停在 21，仅能等 30
   * 天超期关单——补件循环是条死路。 「补齐并锁定」正是补录完成的业务时点（§4.3 锁定后证据集不可改）。
   */
  private void reflowEvidenceSubmitting(long claimId, String operatorId) {
    var claim = claimCaseRepository.findById(claimId);
    if (claim != null
        && com.bone.claim.domain.model.claim.valueobject.ClaimStatus.EVIDENCE_SUBMITTING
            == claim.status()) {
      claim.evidenceSubmitted(operatorId);
      claimCaseRepository.save(claim);
      domainEventPublisher.publishFrom(claim);
    }
  }

  @Transactional(readOnly = true)
  public List<Evidence> listByClaim(long claimId) {
    return evidenceRepository.findByClaimId(claimId);
  }

  private Evidence load(long evidenceId) {
    Evidence evidence = evidenceRepository.findById(evidenceId);
    if (evidence == null) {
      throw ClaimErrors.supplier(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, evidenceId).get();
    }
    return evidence;
  }
}
