package com.bone.procurement.business.domain.model.business;

import java.util.Date;
import java.util.Objects;

/** CAPA 聚合：责任人/期限是立项硬门槛，超期只转人工，关闭必须具名且留证据引用。 */
public final class Capa {

  private final Long tenantId;
  private final String capaNo;
  private final String supplierId;
  private final String issue;
  private final String owner;
  private final Date dueAt;
  private CapaStatus status;
  private boolean followUpRequired;
  private String closedBy;
  private Date closedAt;
  private String evidenceRef;

  private Capa(
      Long tenantId, String capaNo, String supplierId, String issue, String owner, Date dueAt) {
    this.tenantId = tenantId;
    this.capaNo = capaNo;
    this.supplierId = supplierId;
    this.issue = issue;
    this.owner = owner;
    this.dueAt = dueAt;
    this.status = CapaStatus.OPEN;
  }

  public static Capa open(
      Long tenantId, String capaNo, String supplierId, String issue, String owner, Date dueAt) {
    if (tenantId == null || tenantId <= 0) {
      throw new IllegalArgumentException("缺失登录态租户，fail-closed");
    }
    requireText(capaNo, "CAPA编号");
    requireText(supplierId, "供应商");
    requireText(issue, "问题描述");
    requireText(owner, "责任人");
    if (dueAt == null) {
      throw new IllegalArgumentException("整改期限不得为空");
    }
    return new Capa(
        tenantId,
        capaNo.trim(),
        supplierId.trim(),
        issue.trim(),
        owner.trim(),
        new Date(dueAt.getTime()));
  }

  /** 从持久化事实重建；关闭证据与操作人一并读回，不用业务动作重放。 */
  public static Capa restore(
      Long tenantId,
      String capaNo,
      String supplierId,
      String issue,
      String owner,
      Date dueAt,
      CapaStatus status,
      boolean followUpRequired,
      String closedBy,
      Date closedAt,
      String evidenceRef) {
    Capa capa = new Capa(tenantId, capaNo, supplierId, issue, owner, dueAt);
    capa.status = status;
    capa.followUpRequired = followUpRequired;
    capa.closedBy = closedBy;
    capa.closedAt = closedAt;
    capa.evidenceRef = evidenceRef;
    return capa;
  }

  public Capa markOverdue(Date now) {
    Objects.requireNonNull(now, "当前时间");
    if (status == CapaStatus.CLOSED || !now.after(dueAt)) {
      return this;
    }
    this.status = CapaStatus.OVERDUE;
    this.followUpRequired = true;
    return this;
  }

  public Capa close(String operator, String evidence, Date now) {
    assertWritable();
    requireText(operator, "操作人");
    Award.requireHuman(operator);
    requireText(evidence, "关闭证据");
    Objects.requireNonNull(now, "关闭时间");
    this.status = CapaStatus.CLOSED;
    this.closedBy = operator.trim();
    this.closedAt = new Date(now.getTime());
    this.evidenceRef = evidence.trim();
    return this;
  }

  public void assertWritable() {
    if (status == CapaStatus.CLOSED) {
      throw new IllegalStateException("CAPA 已关闭，终态不可回写");
    }
  }

  public Long tenantId() {
    return tenantId;
  }

  public String capaNo() {
    return capaNo;
  }

  public String supplierId() {
    return supplierId;
  }

  public String issue() {
    return issue;
  }

  public String owner() {
    return owner;
  }

  public Date dueAt() {
    return new Date(dueAt.getTime());
  }

  public CapaStatus status() {
    return status;
  }

  public boolean followUpRequired() {
    return followUpRequired;
  }

  public String closedBy() {
    return closedBy;
  }

  public Date closedAt() {
    return closedAt == null ? null : new Date(closedAt.getTime());
  }

  public String evidenceRef() {
    return evidenceRef;
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不得为空");
    }
  }
}
