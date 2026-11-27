package com.bone.procurement.business.domain.model.business;

import java.util.Date;
import java.util.Objects;

/** 投标聚合：显式状态迁移；到期、撤回态与终态都不可提交。 */
public final class Bid {

  private final Long tenantId;
  private final String bidId;
  private final String rfxNo;
  private final String supplierId;
  private final String sourceRef;
  private final Date dueAt;
  private BidStatus status;

  private Bid(
      Long tenantId,
      String bidId,
      String rfxNo,
      String supplierId,
      String sourceRef,
      Date dueAt,
      BidStatus status) {
    this.tenantId = requireTenant(tenantId);
    this.bidId = requireText(bidId, "投标ID");
    this.rfxNo = requireText(rfxNo, "RFx编号");
    this.supplierId = requireText(supplierId, "供应商");
    this.sourceRef = sourceRef;
    this.dueAt = Objects.requireNonNull(dueAt, "截止时间");
    this.status = Objects.requireNonNull(status, "投标状态");
  }

  public static Bid draft(
      Long tenantId, String bidId, String rfxNo, String supplierId, String sourceRef, Date dueAt) {
    return new Bid(tenantId, bidId, rfxNo, supplierId, sourceRef, dueAt, BidStatus.DRAFT);
  }

  /** 从持久化事实重建；不重放业务动作，避免用假时间误判生命周期。 */
  public static Bid restore(
      Long tenantId,
      String bidId,
      String rfxNo,
      String supplierId,
      String sourceRef,
      Date dueAt,
      BidStatus status) {
    return new Bid(tenantId, bidId, rfxNo, supplierId, sourceRef, dueAt, status);
  }

  public Bid submit(Date now) {
    Objects.requireNonNull(now, "当前时间");
    if (status != BidStatus.DRAFT) {
      throw new IllegalStateException("仅 DRAFT 投标可提交，当前=" + status);
    }
    if (!now.before(dueAt)) {
      throw new IllegalStateException("RFx 已截止，禁止接收新投标");
    }
    this.status = BidStatus.SUBMITTED;
    return this;
  }

  public Bid withdraw() {
    if (status == BidStatus.WITHDRAWN || status == BidStatus.REJECTED) {
      throw new IllegalStateException("终态投标不可再次撤回");
    }
    this.status = BidStatus.WITHDRAWN;
    return this;
  }

  public Long tenantId() {
    return tenantId;
  }

  public String bidId() {
    return bidId;
  }

  public String rfxNo() {
    return rfxNo;
  }

  public String supplierId() {
    return supplierId;
  }

  public String sourceRef() {
    return sourceRef;
  }

  public Date dueAt() {
    return new Date(dueAt.getTime());
  }

  public BidStatus status() {
    return status;
  }

  private static Long requireTenant(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new IllegalArgumentException("缺失登录态租户，fail-closed");
    }
    return tenantId;
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不得为空");
    }
    return value.trim();
  }
}
