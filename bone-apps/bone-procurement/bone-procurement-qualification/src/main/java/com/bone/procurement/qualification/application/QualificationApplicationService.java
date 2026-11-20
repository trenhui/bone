package com.bone.procurement.qualification.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
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

  public void reviewOcrByHuman(Long qualificationId, String reviewer, boolean accurate, Date now) {
    // 骨架：仓储按 ID 取实体后交由领域复核；生产持久化就绪后替换 findById 实现。
    throw new UnsupportedOperationException("需生产持久化端口就绪后开放");
  }

  public List<Qualification> expiring(Long tenantId) {
    return repository.findExpiring(tenantId);
  }
}
