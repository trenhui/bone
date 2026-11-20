package com.bone.procurement.qualification.domain.model;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 供应商证照聚合根（INV-10 EXPIRED 不得作准入依据；INV-11 OCR 结果须人工复核才生效）。
 *
 * <p>certNo 为 PII：只存脱敏值，AI 入参不传原文。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_qualification")
public class Qualification extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "cert_type")
  private String certType;

  @Column(name = "cert_no")
  private String certNo;

  @Column(name = "issued_at")
  private Date issuedAt;

  @Column(name = "expires_at")
  private Date expiresAt;

  @Column(name = "verify_status")
  private String verifyStatus;

  @Column(name = "attachment_ref")
  private String attachmentRef;

  @Column(name = "ocr_result_json")
  private String ocrResultJson;

  @Column(name = "reviewed_by")
  private String reviewedBy;

  @Version
  @Column(name = "version")
  private Integer version;

  public static Qualification submit(
      Long tenantId,
      String supplierId,
      String certType,
      String maskedCertNo,
      Date expiresAt,
      String attachmentRef,
      String ocrResultJson) {
    Qualification q = new Qualification();
    q.tenantId = tenantId;
    q.supplierId = supplierId;
    q.certType = certType;
    q.certNo = maskedCertNo;
    q.expiresAt = expiresAt;
    q.attachmentRef = attachmentRef;
    q.ocrResultJson = ocrResultJson;
    q.verifyStatus = VerifyStatus.PENDING.name();
    q.version = 0;
    return q;
  }

  public VerifyStatus status() {
    return VerifyStatus.valueOf(verifyStatus);
  }

  public boolean isExpiredAt(Date now) {
    return expiresAt != null && now != null && !now.before(expiresAt);
  }

  /** 到期巡检：不改业务状态，只把过期证照标记 EXPIRED，禁止继续作准入依据。 */
  public void markExpiredIfDue(Date now) {
    if (isExpiredAt(now) && status() != VerifyStatus.EXPIRED) {
      this.verifyStatus = VerifyStatus.EXPIRED.name();
      this.reviewedBy = null;
    }
  }

  /** OCR 复核必须具名人工（INV-11）：OCR 自报结果不得自证；过期证照不得复核通过（INV-10）。 */
  public void reviewByHuman(String reviewer, boolean ocrAccurate, Date now) {
    if (reviewer == null || reviewer.isBlank()) {
      throw new IllegalArgumentException("OCR 结果须具名人工复核，OCR 不得自证");
    }
    if (status() == VerifyStatus.EXPIRED) {
      throw new IllegalStateException("EXPIRED 终态不可回写");
    }
    if (isExpiredAt(now)) {
      throw new IllegalStateException("证照已过期，不得作为准入通过依据（INV-10）");
    }
    this.verifyStatus = (ocrAccurate ? VerifyStatus.VERIFIED : VerifyStatus.REJECTED).name();
    this.reviewedBy = reviewer;
  }
}
