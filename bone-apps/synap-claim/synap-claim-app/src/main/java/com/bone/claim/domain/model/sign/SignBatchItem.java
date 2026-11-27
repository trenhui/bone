package com.bone.claim.domain.model.sign;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 签收明细（子集合）：一条赔案引用，或一组足以自动建案的要素。 */
@Getter
@NoArgsConstructor
@Table("t_claim_sign_batch_item")
public class SignBatchItem extends TenantAbstractEntity<Long> {

  public static final String CASE_PENDING = "PENDING";
  public static final String CASE_NEW = "NEW";
  public static final String CASE_SKIPPED = "SKIPPED";
  public static final String CASE_AWAIT_MANUAL = "AWAIT_MANUAL";
  public static final String CASE_FAILED = "FAILED";

  @Column(name = "batch_id")
  private Long batchId;

  @Column(name = "claim_id")
  private Long claimId;

  @Column(name = "claim_no")
  private String claimNo;

  @Column(name = "image_ready")
  private Boolean imageReady;

  @Column(name = "policy_no")
  private String policyNo;

  @Column(name = "plan_id")
  private Long planId;

  @Column(name = "insured_name")
  private String insuredName;

  @Column(name = "insured_id_no")
  private String insuredIdNo;

  @Column(name = "identity_type")
  private String identityType;

  @Column(name = "counting_result")
  private Integer countingResult;

  @Column(name = "image_count")
  private Integer imageCount;

  @Column(name = "image_upload_count")
  private Integer imageUploadCount;

  @Column(name = "image_upload_time")
  private Instant imageUploadTime;

  @Column(name = "image_uploader")
  private String imageUploader;

  @Column(name = "out_insure_time")
  private Instant outInsureTime;

  @Column(name = "claim_type")
  private String claimType;

  @Column(name = "source_channel")
  private String sourceChannel;

  @Column(name = "business_key")
  private String businessKey;

  @Column(name = "case_status")
  private String caseStatus;

  @Column(name = "created_case_id")
  private Long createdCaseId;

  /** 引用本地已存在的赔案：老导入名单路径，签收后不重复建案。 */
  public static SignBatchItem of(long tenantId, long batchId, long claimId, String claimNo) {
    SignBatchItem item = new SignBatchItem();
    item.setTenantId(tenantId);
    item.batchId = batchId;
    item.claimId = claimId;
    item.claimNo = claimNo;
    item.imageReady = false;
    // 保留 NULL：MySQL 唯一索引允许多 NULL，同一本地案件被多个批次引用不应冲突。
    item.caseStatus = CASE_SKIPPED;
    stampAuditFields(item);
    return item;
  }

  /** 携带建案要素：签收确认后按 business_key 幂等创建报案与赔案。 */
  public static SignBatchItem withCaseElements(
      long tenantId,
      long batchId,
      String claimNo,
      String businessKey,
      String policyNo,
      Long planId,
      String insuredName,
      String insuredIdNo,
      Instant outInsureTime,
      String claimType,
      String sourceChannel) {
    SignBatchItem item = new SignBatchItem();
    item.setTenantId(tenantId);
    item.batchId = batchId;
    item.claimNo = claimNo;
    item.businessKey = businessKey;
    item.policyNo = policyNo;
    item.planId = planId == null ? 0L : planId;
    item.insuredName = insuredName;
    item.insuredIdNo = insuredIdNo;
    item.outInsureTime = outInsureTime;
    item.claimType = claimType;
    item.sourceChannel = sourceChannel;
    item.imageReady = false;
    item.caseStatus = CASE_PENDING;
    stampAuditFields(item);
    return item;
  }

  public boolean isImageReady() {
    return Boolean.TRUE.equals(imageReady);
  }

  public void markImageReady() {
    this.imageReady = true;
  }

  /** TPA 批次详情页所需的赔案影像要素，由名单导入方按需写入。 */
  public void withImageAudit(
      String identityType,
      Integer countingResult,
      Integer imageCount,
      Integer imageUploadCount,
      Instant imageUploadTime,
      String imageUploader) {
    this.identityType = identityType;
    this.countingResult = countingResult;
    this.imageCount = imageCount;
    this.imageUploadCount = imageUploadCount;
    this.imageUploadTime = imageUploadTime;
    this.imageUploader = imageUploader;
    touch();
  }

  /** 是否尚未关联案件且要素齐全，可进入自动建案。 */
  public boolean needsCaseCreation() {
    return claimId == null && createdCaseId == null && hasCaseElements();
  }

  public boolean hasCaseElements() {
    return present(businessKey)
        && present(policyNo)
        && present(insuredName)
        && present(insuredIdNo)
        && outInsureTime != null;
  }

  public void linkCase(long caseId, String status) {
    this.claimId = caseId;
    this.createdCaseId = caseId;
    this.caseStatus = status;
    touch();
  }

  public void markSkipped(String status) {
    this.caseStatus = status;
    touch();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private void touch() {
    setUpdatedAt(new java.util.Date());
  }

  private static void stampAuditFields(SignBatchItem item) {
    // SDK 的 @Cascade 插入不填审计字段，DB 侧 NOT NULL：不显式设置会在根 save 后失败。
    java.util.Date now = new java.util.Date();
    item.setCreatedAt(now);
    item.setUpdatedAt(now);
  }
}
