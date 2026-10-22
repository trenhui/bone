package com.bone.claim.domain.model.evidence;

import com.bone.claim.domain.model.evidence.event.EvidenceLockedEvent;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 证据聚合根（A2，多租户 + 领域事件）。
 *
 * <p>职责：材料登记 → 解析（OCR/LLM 结果回填）→ 锁定。**锁定后证据集不可改**（§4.3 不变量）， 这是理算可复算的前提：理算输入若可在事后被改，"同输入同输出"即失去意义。
 *
 * <p>解析结果只登记**字段与置信度**，影像本体走平台附件服务（STS 直传，§7.2 上传通道纪律）， 本域只持有 {@code fileRef} 引用，不落二进制。
 *
 * <p>重复上传判定：{@code contentHash}（文件内容 SHA256，§9.5）——与案件内已有证据比对， 命中即 409（端点声明），避免同一发票重复入账。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_evidence")
public class Evidence extends TenantAggregateRoot<Long> {

  /** 所属案件（聚合间以 ID 引用，不持有对象引用）。 */
  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  /** 材料类型。 */
  @Column(name = "doc_type", nullable = false)
  private String docType;

  /** 附件引用（平台附件服务返回的引用，非物理路径）。 */
  @Column(name = "file_ref", nullable = false)
  private String fileRef;

  /** 文件内容 SHA256（重复上传判定依据，§9.5 uk(claim_id, content_hash)）。 */
  @Column(name = "content_hash", nullable = false)
  private String contentHash;

  /** 解析状态。 */
  @Column(name = "ocr_status", nullable = false)
  private String ocrStatus;

  /** 解析置信度（<0.7 转人工补录，R-MAN-01；与 AI 决策置信度相互独立）。 */
  @Column(name = "ocr_confidence")
  private java.math.BigDecimal ocrConfidence;

  /** 票面验真结果（UNKNOWN/PASS/FAIL/UNVERIFIABLE，后两者禁入快赔，R-FRAUD-06）。 */
  @Column(name = "verify_valid", nullable = false)
  private String verifyValid;

  /** 解析结果（字段 + 置信度 + 证据坐标，JSON）。 */
  @Column(name = "extracted_json")
  private String extractedJson;

  /** 是否锁定（锁定后不可改）。 */
  @Column(name = "locked", nullable = false)
  private Integer locked;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Version
  @Column(name = "version")
  private Long version;

  private Evidence(
      Long id,
      Long tenantId,
      Long claimId,
      DocType docType,
      String fileRef,
      String contentHash,
      String verifyValid) {
    setId(id);
    setTenantId(tenantId);
    this.claimId = claimId;
    this.docType = docType.name();
    this.fileRef = fileRef;
    this.contentHash = contentHash;
    this.ocrStatus = OcrStatus.PENDING.name();
    this.verifyValid = verifyValid;
    this.locked = 0;
  }

  /**
   * 登记材料（工厂方法）。
   *
   * @param verifyValid 票面验真结果；{@code null} 视为 UNKNOWN
   */
  public static Evidence register(
      Long id,
      Long tenantId,
      Long claimId,
      DocType docType,
      String fileRef,
      String contentHash,
      String verifyValid) {
    if (docType == null) {
      throw new DomainException("材料类型不能为空");
    }
    if (fileRef == null || fileRef.isBlank()) {
      throw new DomainException("附件引用不能为空");
    }
    if (contentHash == null || contentHash.isBlank()) {
      throw new DomainException("文件内容哈希不能为空（重复上传判定依赖它）");
    }
    return new Evidence(
        id,
        tenantId,
        claimId,
        docType,
        fileRef,
        contentHash,
        verifyValid == null || verifyValid.isBlank() ? "UNKNOWN" : verifyValid);
  }

  /**
   * 回填解析结果。
   *
   * <p>置信度 <0.7 一律置 {@link OcrStatus#MANUAL}（转人工补录，R-MAN-01）；验真不通过
   * （FAIL/UNVERIFIABLE）不阻断登记，但由快赔规约在自动通过前置中拦截——聚合不替规约做放行决策。
   */
  public void markParsed(
      OcrStatus status, java.math.BigDecimal confidence, String extractedJson, String verifyValid) {
    requireUnlocked("证据已锁定，禁止回填解析结果");
    if (status == null) {
      throw new DomainException("解析状态不能为空");
    }
    this.ocrStatus = status.name();
    this.ocrConfidence = confidence;
    this.extractedJson = extractedJson;
    if (verifyValid != null && !verifyValid.isBlank()) {
      this.verifyValid = verifyValid;
    }
    if (confidence != null && confidence.compareTo(new java.math.BigDecimal("0.7")) < 0) {
      this.ocrStatus = OcrStatus.MANUAL.name();
    }
  }

  /** 人工补录（替代解析结果，同样受锁定约束）。 */
  public void fillManually(String extractedJson, String operatorId) {
    requireUnlocked("证据已锁定，禁止人工补录");
    if (operatorId == null || operatorId.isBlank()) {
      throw new DomainException("补录人不能为空");
    }
    this.ocrStatus = OcrStatus.MANUAL.name();
    this.extractedJson = extractedJson;
  }

  /** 锁定证据集（理算前置，§4.3 不变量；发 {@link EvidenceLockedEvent}）。 */
  public void lock(String operatorId, int evidenceCount) {
    if (this.locked != null && this.locked == 1) {
      return; // 幂等：重复锁定无副作用
    }
    this.locked = 1;
    this.lockedAt = Instant.now();
    addDomainEvent(
        new EvidenceLockedEvent(claimId, getTenantId(), evidenceCount, operatorId, Instant.now()));
  }

  public boolean isLocked() {
    return locked != null && locked == 1;
  }

  public DocType docTypeEnum() {
    return DocType.valueOf(docType);
  }

  public OcrStatus ocrStatusEnum() {
    return OcrStatus.valueOf(ocrStatus);
  }

  /** 验真是否可用于快赔（PASS 方可，其余转人工，R-FRAUD-06 从严）。 */
  public boolean isVerifyPassed() {
    return "PASS".equals(verifyValid);
  }

  private void requireUnlocked(String message) {
    if (isLocked()) {
      throw new DomainException(message);
    }
  }
}
