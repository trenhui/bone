package com.bone.claim.infrastructure.context;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 案件上下文查询适配器（出站端口实现，须 {@code *PortAdapter} 命名）。
 *
 * <p>一期经 {@code ClaimCaseRepository}/{@code EvidenceRepository} 拼装决策上下文；二期替换为查询服务/读模型投影——
 * 替换只动本类，领域与应用层零感知（防腐层 ACL 价值即在此）。键 = claimId。
 */
@Component
public class LocalClaimContextPortAdapter implements ClaimContextPort {

  private final ClaimCaseRepository claimCaseRepository;
  private final EvidenceRepository evidenceRepository;

  public LocalClaimContextPortAdapter(
      ClaimCaseRepository claimCaseRepository, EvidenceRepository evidenceRepository) {
    this.claimCaseRepository = claimCaseRepository;
    this.evidenceRepository = evidenceRepository;
  }

  @Override
  public ClaimContextView getContext(long claimId) {
    ClaimCase claim = claimCaseRepository.findById(claimId);
    List<Evidence> evidences = evidenceRepository.findByClaimId(claimId);
    boolean hasEvidence = !evidences.isEmpty();
    // 只有 DONE（解析完成）与 MANUAL（人工补录完成）算「证据已解析」。
    // 原判据只排除 PENDING，会把 FAILED/PARSING 也当成已解析——感知层一旦落地 FAILED 状态，
    // 决策图就会拿「没有事实输入」的材料继续核责，属于静默放行。
    boolean allParsed =
        hasEvidence
            && evidences.stream()
                .allMatch(
                    e ->
                        e.ocrStatusEnum() == OcrStatus.DONE
                            || e.ocrStatusEnum() == OcrStatus.MANUAL);
    BigDecimal minOcr =
        evidences.stream()
            .map(Evidence::getOcrConfidence)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);
    boolean allVerify = evidences.stream().allMatch(Evidence::isVerifyPassed);
    return new ClaimContextView(
        claimId,
        claim.getClaimType(),
        claim.claimAmount(),
        hasEvidence,
        allParsed,
        minOcr,
        allVerify);
  }
}
