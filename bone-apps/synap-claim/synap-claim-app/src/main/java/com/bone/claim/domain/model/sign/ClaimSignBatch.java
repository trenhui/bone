package com.bone.claim.domain.model.sign;

import com.bone.claim.domain.model.sign.event.SignStatusChangedEvent;
import com.bone.claim.domain.model.sign.valueobject.SignChannel;
import com.bone.claim.domain.model.sign.valueobject.SignSource;
import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.claim.domain.model.sign.valueobject.SignType;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 签收批次聚合根：批次号唯一、四态状态机，明细与轨迹由 SDK 级联存取。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_sign_batch")
public class ClaimSignBatch extends TenantAggregateRoot<Long> {

  @Column(name = "batch_no")
  private String batchNo;

  @Column(name = "source")
  private String source;

  @Column(name = "sign_type")
  private String signType;

  @Column(name = "sign_channel")
  private String signChannel;

  @Column(name = "insurance_company")
  private String insuranceCompany;

  @Column(name = "insurance_subsidiary")
  private String insuranceSubsidiary;

  @Column(name = "insuring_agency")
  private String insuringAgency;

  @Column(name = "sign_status")
  private String signStatus;

  @Column(name = "business_key")
  private String businessKey;

  @Column(name = "claim_count")
  private Integer claimCount;

  @Column(name = "emergency")
  private String emergency;

  @Column(name = "image_ready_count")
  private Integer imageReadyCount;

  @Column(name = "express_company")
  private String expressCompany;

  @Column(name = "express_no")
  private String expressNo;

  @Column(name = "sender")
  private String sender;

  @Column(name = "arrive_time")
  private Instant arriveTime;

  @Column(name = "sign_institution")
  private String signInstitution;

  @Column(name = "sign_operator")
  private String signOperator;

  @Column(name = "sign_time")
  private Instant signTime;

  @Column(name = "sign_remark")
  private String signRemark;

  @Column(name = "version")
  @Version
  private Long version;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "deleted")
  private Boolean deleted;

  @Transient
  @Cascade(foreignKey = "batchId", orphanRemoval = false)
  private final List<SignBatchItem> items = new ArrayList<>();

  public static ClaimSignBatch create(
      long id,
      long tenantId,
      String batchNo,
      SignSource source,
      SignType signType,
      SignChannel signChannel,
      String businessKey,
      String expressCompany,
      String expressNo,
      String sender,
      Instant arriveTime) {
    if (batchNo == null || batchNo.isBlank()) {
      throw new DomainException("签收批次号不能为空");
    }
    ClaimSignBatch batch = new ClaimSignBatch();
    batch.setId(id);
    batch.setTenantId(tenantId);
    batch.batchNo = batchNo;
    batch.source = source.code();
    batch.signType = signType.code();
    batch.signChannel = signChannel.code();
    batch.businessKey = businessKey;
    batch.expressCompany = expressCompany;
    batch.expressNo = expressNo;
    batch.sender = sender;
    batch.arriveTime = arriveTime;
    batch.signStatus = SignStatus.WAIT_FOR_PARTICIPANT.code();
    batch.claimCount = 0;
    batch.imageReadyCount = 0;
    Instant now = Instant.now();
    batch.createdAt = now;
    batch.updatedAt = now;
    batch.version = 0L;
    batch.deleted = false;
    batch.addDomainEvent(
        new SignStatusChangedEvent(
            id, tenantId, batchNo, null, SignStatus.WAIT_FOR_PARTICIPANT, "CREATE", null, now));
    return batch;
  }

  public SignStatus status() {
    return signStatus == null ? SignStatus.WAIT_FOR_PARTICIPANT : SignStatus.ofCode(signStatus);
  }

  public SignSource sourceEnum() {
    return SignSource.ofCode(source);
  }

  public SignType typeEnum() {
    return SignType.ofCode(signType);
  }

  public SignChannel channelEnum() {
    return SignChannel.ofCode(signChannel);
  }

  /** 对齐 TPA 页面的可选展示字段；不参与状态迁移与幂等判定。 */
  public void withSigningParties(
      String insuranceCompany,
      String insuranceSubsidiary,
      String insuringAgency,
      String emergency) {
    this.insuranceCompany = insuranceCompany;
    this.insuranceSubsidiary = insuranceSubsidiary;
    this.insuringAgency = insuringAgency;
    this.emergency = emergency;
    this.updatedAt = Instant.now();
  }

  /** 状态迁移唯一入口；非法跃迁与终态重复签收均被拒绝。 */
  public boolean transitionTo(SignStatus to, String actionCode, String operatorId) {
    SignStatus from = status();
    if (from.terminal()) {
      throw new DomainException("签收批次已处于终态 " + from.displayName() + "，禁止再次签收");
    }
    if (from == to) {
      return false;
    }
    if (!from.canTransitionTo(to)) {
      throw new DomainException("签收批次状态非法跃迁: " + from.displayName() + " -> " + to.displayName());
    }
    this.signStatus = to.code();
    this.updatedAt = Instant.now();
    addDomainEvent(
        new SignStatusChangedEvent(
            getId(), getTenantId(), batchNo, from, to, actionCode, operatorId, Instant.now()));
    return true;
  }

  public void addItem(SignBatchItem item) {
    if (item == null) {
      throw new DomainException("签收明细不能为空");
    }
    this.items.add(item);
    this.claimCount = this.items.size();
  }

  /** 登记一枚明细影像完备；计数持久化，findById 不回填集合也不影响。 */
  public void registerImageReady() {
    if (claimCount == null || imageReadyCount == null) {
      throw new DomainException("签收批次尚未初始化计数");
    }
    if (imageReadyCount >= claimCount) {
      throw new DomainException("影像完备数已等于明细数，禁止超额登记");
    }
    this.imageReadyCount++;
    this.updatedAt = Instant.now();
  }

  /** 确认签收前置校验：所有明细影像完备。 */
  public void requireImagesReady() {
    if (claimCount != null && imageReadyCount != null && imageReadyCount < claimCount) {
      throw new DomainException("存在明细未完成影像上传: " + (claimCount - imageReadyCount) + " 条");
    }
  }

  public void completeSign(String operator, String remark, Instant now) {
    if (remark == null || remark.isBlank() || remark.trim().length() < 10) {
      throw new DomainException("签收意见不能为空且不少于10字");
    }
    this.signOperator = operator;
    this.signRemark = remark;
    this.signTime = now;
  }

  public List<SignBatchItem> getItems() {
    return List.copyOf(items);
  }
}
