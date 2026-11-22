package com.bone.procurement.qualification.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
import com.bone.procurement.qualification.domain.model.qualification.VerifyStatus;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

/** 证照应用服务（骨架）：提交只落脱敏与引用，复核必须具名人工。 */
@Service
@NoDomainEvent
public class QualificationApplicationService {

  private final QualificationRepository repository;

  public QualificationApplicationService(QualificationRepository repository) {
    this.repository = repository;
  }

  public Long submit(
      Long tenantId,
      String supplierId,
      String certType,
      String maskedCertNo,
      Date expiresAt,
      String attachmentRef,
      String ocrResultJson) {
    Qualification q =
        Qualification.submit(
            tenantId, supplierId, certType, maskedCertNo, expiresAt, attachmentRef, ocrResultJson);
    return repository.save(q);
  }

  /** OCR 人工终决（INV-11）：具名复核、租户锚定、幂等。缺租户/跨租户一律拒绝。 */
  public void reviewOcrByHuman(
      Long tenantId, Long qualificationId, String reviewer, boolean accurate, Date now) {
    if (tenantId == null) {
      throw new IllegalArgumentException("OCR 复核需要登录租户上下文，禁止缺省租户");
    }
    Qualification q =
        repository
            .findById(tenantId, qualificationId)
            .orElseThrow(() -> new IllegalArgumentException("证照不存在或不属于当前租户"));
    // 幂等：已终决且复核人一致则直接返回，不重复写入；不同复核人不得覆盖既有终决。
    if (q.status() == VerifyStatus.VERIFIED || q.status() == VerifyStatus.REJECTED) {
      if (reviewer != null && reviewer.equals(q.getReviewedBy())) {
        return;
      }
      throw new IllegalStateException("证照已人工终决，不得由他人覆写");
    }
    q.reviewByHuman(reviewer, accurate, now);
    repository.save(q);
  }

  public List<Qualification> expiring(Long tenantId) {
    return repository.findExpiring(tenantId);
  }

  /** 人工终决读回：供 HITL 工作台刷新后展示后端权威状态，不使用本地乐观值。 */
  public Qualification findOne(Long tenantId, Long qualificationId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("查询证照需要登录租户上下文");
    }
    return repository
        .findById(tenantId, qualificationId)
        .orElseThrow(() -> new IllegalArgumentException("证照不存在或不属于当前租户"));
  }
}
