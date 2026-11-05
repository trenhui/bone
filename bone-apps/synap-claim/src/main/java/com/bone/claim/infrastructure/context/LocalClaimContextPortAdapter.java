package com.bone.claim.infrastructure.context;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.ClaimContextPort.EvidenceFact;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.EvidenceFactPolicy;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.infrastructure.evidence.EvidenceFieldReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 案件上下文查询适配器（出站端口实现，须 {@code *PortAdapter} 命名）。
 *
 * <p>一期经 {@code ClaimCaseRepository}/{@code EvidenceRepository} 拼装决策上下文；二期替换为查询服务/读模型投影——
 * 替换只动本类，领域与应用层零感知（防腐层 ACL 价值即在此）。键 = claimId。
 *
 * <p><b>材料事实为什么要从聚合标量里分出来单独带</b>：认知层原先只拿到「有材料 / 都解析了 / 最低 OCR 置信度」
 * 三个标量，模型无法核对票面金额、无法引用诊断编码，只能把这三个标量复述一遍当「事实」 落进轨迹（实测 {@code aiFacts} 的 code 恰是 {@code
 * claimType}/{@code claimAmount}）。 感知层明明已抽出带原文定位的结构化字段（如 {@code total_amount=1234.56,
 * ref=text@67:8}），却在通往认知层的这一段被丢掉——「AI 原生」的输入侧断链就在这里。
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
        allVerify,
        factsOf(evidences));
  }

  /**
   * 汇总各材料的解析事实，供认知层使用。
   *
   * <p>三条约束都在这里落地，方向都是「宁可少送，不可送错」：① 只送通过 {@link EvidenceFactPolicy} 的字段码 （姓名 / 证件 / 病历号不出域）；②
   * 空值跳过（空事实既不能核验也不能展示）；③ 总条数封顶 （材料多的案件不能把提示词撑爆）。
   *
   * <p>未解析完成的材料同样参与：半份材料里已解析出的字段仍是模型可用的证据；材料是否「齐备」由 {@code evidenceAllParsed} 这个独立标量表达，二者不互相吞并。
   */
  private static List<EvidenceFact> factsOf(List<Evidence> evidences) {
    List<EvidenceFact> facts = new ArrayList<>();
    for (Evidence evidence : evidences) {
      for (EvidenceFieldReader.ParsedField field :
          EvidenceFieldReader.read(evidence.getExtractedJson())) {
        if (facts.size() >= EvidenceFactPolicy.MAX_FACTS) {
          return List.copyOf(facts);
        }
        String value = EvidenceFactPolicy.normalizeValue(field.value());
        if (value.isEmpty() || !EvidenceFactPolicy.exportable(field.code())) {
          continue;
        }
        facts.add(
            new EvidenceFact(evidence.getDocType(), field.code(), value, field.evidenceRef()));
      }
    }
    return List.copyOf(facts);
  }
}
