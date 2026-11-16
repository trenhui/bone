package com.bone.masterdata.domain.model.changerequest;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 独立变更请求（Change Request）聚合：分段并行审批 + SoD + 双人复核 + 具名 specialist 路由。
 *
 * <p><b>为什么必须独立成对象</b>：SAP MDG 的 CR 之所以独立于记录版本，是因为一次业务变更会同时触及 General / CompanyCode / PurchasingOrg
 * 三个视图，每段由不同 Data Steward 审批，且必须全部通过才能覆盖 Active 区。 单记录版本状态机（{@code
 * status/approved_by}）表达不了「三段并行、缺一不可、各段不同人、审批与应用分离」。 v1.1 把它降为 [Vision] 的减法在终局架构下不成立，见 {@code 3a 详设
 * §11.9}。
 *
 * <p><b>终局一致性</b>：本对象是平台共享服务层的领域内核，与 adapter / DDL / 工作流引擎实现无关； 生产落地只替换 {@code ChangeRequestPort} 与
 * {@code ChangeRequestWorkflowPort} 的实现，语义与 ID 不变。
 *
 * <p><b>硬约束</b>（逐条由负向用例守护）：
 *
 * <ol>
 *   <li>三段必须齐全且各有<b>具名</b> Owner，缺任一段不得创建；
 *   <li>审批人必须等于该段预派 Owner，且不得是提交人（SoD 禁止自审）；
 *   <li>仅当状态为 {@link Status#APPROVED} 才允许 {@link #apply}，禁止未审批直接覆盖 Active 区；
 *   <li>应用前至少两名互不相同的审批人（双人复核），应用者不得是提交人或任一审批人（提交/审批/应用三权分立）；
 *   <li>整笔 CR 携带 tenantId，跨租户读写一律 fail-closed；重复审批、重复应用、撤销后应用一律拒绝。
 * </ol>
 *
 * <p><b>不可变</b>：所有状态迁移返回新实例，调用方持有的旧实例不会被动变化；决策流水 {@link TrailEntry} 只追加不修改。
 */
public final class ChangeRequest {

  /** 三段业务视图（对齐 SAP MDG Business Partner 的 General / Company Code / Purchasing Org）。 */
  public enum Segment {
    GENERAL,
    COMPANY_CODE,
    PURCHASING_ORG;

    public static Segment of(String code) {
      if (code == null || code.trim().isEmpty()) {
        throw new DomainException("审批分段为空");
      }
      for (Segment s : values()) {
        if (s.name().equalsIgnoreCase(code.trim())) {
          return s;
        }
      }
      throw new DomainException("未知审批分段: " + code);
    }
  }

  public enum Status {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    APPLIED,
    CANCELLED
  }

  public enum ApprovalState {
    PENDING,
    APPROVED,
    REJECTED
  }

  /** 单段审批单：预派 Owner + 裁决结果，整段不可变。 */
  public static final class SegmentApproval {
    private final Segment segment;
    private final String assignedOwner;
    private final ApprovalState state;
    private final String decidedBy;
    private final Instant decidedAt;
    private final String reason;

    private SegmentApproval(
        Segment segment,
        String assignedOwner,
        ApprovalState state,
        String decidedBy,
        Instant decidedAt,
        String reason) {
      this.segment = segment;
      this.assignedOwner = assignedOwner;
      this.state = state;
      this.decidedBy = decidedBy;
      this.decidedAt = decidedAt;
      this.reason = reason;
    }

    static SegmentApproval pending(Segment segment, String assignedOwner) {
      if (segment == null || isBlank(assignedOwner)) {
        throw new DomainException("分段必须指定具名 Owner，禁止空路由");
      }
      return new SegmentApproval(segment, assignedOwner, ApprovalState.PENDING, null, null, null);
    }

    SegmentApproval decide(ApprovalState target, String actor, Instant at, String reason) {
      if (state != ApprovalState.PENDING) {
        throw new DomainException("该分段已裁决，禁止重复审批: " + segment);
      }
      if (!assignedOwner.equals(actor)) {
        throw new DomainException("审批人不是该分段具名 Owner，禁止越权审批: " + segment);
      }
      if (target == ApprovalState.REJECTED && isBlank(reason)) {
        throw new DomainException("驳回必须填写原因，便于事后仲裁");
      }
      return new SegmentApproval(segment, assignedOwner, target, actor, at, reason);
    }

    public Segment getSegment() {
      return segment;
    }

    public String getAssignedOwner() {
      return assignedOwner;
    }

    public ApprovalState getState() {
      return state;
    }

    public String getDecidedBy() {
      return decidedBy;
    }

    public Instant getDecidedAt() {
      return decidedAt;
    }

    public String getReason() {
      return reason;
    }
  }

  /** 决策流水条目（只追加，不回传字段值/PII）。 */
  public record TrailEntry(
      String action, Segment segment, String actor, Instant occurredAt, String reason) {}

  private final String changeRequestId;
  private final long tenantId;
  private final String partyId;
  private final long targetVersion;
  private final String changeDescription;
  private final String createdBy;
  private final Instant createdAt;
  private final Map<Segment, SegmentApproval> segments;
  private final Status status;
  private final String appliedBy;
  private final Instant appliedAt;
  private final List<TrailEntry> trail;

  private ChangeRequest(
      String changeRequestId,
      long tenantId,
      String partyId,
      long targetVersion,
      String changeDescription,
      String createdBy,
      Instant createdAt,
      Map<Segment, SegmentApproval> segments,
      Status status,
      String appliedBy,
      Instant appliedAt,
      List<TrailEntry> trail) {
    this.changeRequestId = changeRequestId;
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.targetVersion = targetVersion;
    this.changeDescription = changeDescription;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.segments = Collections.unmodifiableMap(new LinkedHashMap<>(segments));
    this.status = status;
    this.appliedBy = appliedBy;
    this.appliedAt = appliedAt;
    this.trail = Collections.unmodifiableList(new ArrayList<>(trail));
  }

  /** 创建分段 CR（初始 DRAFT）。三段路由必须齐全，否则 fail-closed——缺任一段的 CR 会让「全部通过」判定失真。 */
  public static ChangeRequest create(
      String changeRequestId,
      long tenantId,
      String partyId,
      long targetVersion,
      String changeDescription,
      String createdBy,
      Instant at,
      Map<Segment, String> segmentOwners) {
    if (isBlank(changeRequestId)
        || isBlank(partyId)
        || isBlank(changeDescription)
        || isBlank(createdBy)
        || at == null) {
      throw new DomainException("变更请求缺少 changeRequestId/partyId/说明/提交人/时间");
    }
    if (tenantId < 0 || targetVersion <= 0) {
      throw new DomainException("tenantId 非法或目标版本必须为正数");
    }
    if (segmentOwners == null || segmentOwners.size() != Segment.values().length) {
      throw new DomainException("三段 specialist 路由必须齐全，禁止缺省分段审批");
    }
    Map<Segment, SegmentApproval> approvals = new LinkedHashMap<>();
    for (Segment s : Segment.values()) {
      String owner = segmentOwners.get(s);
      if (isBlank(owner)) {
        throw new DomainException("分段缺少具名 Owner: " + s);
      }
      approvals.put(s, SegmentApproval.pending(s, owner));
    }
    List<TrailEntry> entries = new ArrayList<>();
    entries.add(new TrailEntry("CREATED", null, createdBy, at, changeDescription));
    return new ChangeRequest(
        changeRequestId,
        tenantId,
        partyId,
        targetVersion,
        changeDescription,
        createdBy,
        at,
        approvals,
        Status.DRAFT,
        null,
        null,
        entries);
  }

  /** 提交进入并行审批；仅提交人可提交，且只能提交一次。 */
  public ChangeRequest submit(String operator, Instant at) {
    if (status != Status.DRAFT) {
      throw new DomainException("仅 DRAFT 可提交审批，当前状态: " + status);
    }
    if (!createdBy.equals(operator) || at == null) {
      throw new DomainException("仅提交人本人可提交变更请求");
    }
    return with(Status.IN_REVIEW, "SUBMITTED", null, operator, at, null);
  }

  /** 单段裁决：三段可任意顺序并行处理，全部通过才进入 APPROVED。 */
  public ChangeRequest decide(
      Segment segment, ApprovalState target, String actor, Instant at, String reason) {
    if (status != Status.IN_REVIEW) {
      throw new DomainException("变更请求不在并行审批中，禁止裁决，当前状态: " + status);
    }
    if (segment == null || (target != ApprovalState.APPROVED && target != ApprovalState.REJECTED)) {
      throw new DomainException("裁决分段或目标状态非法");
    }
    if (isBlank(actor) || at == null) {
      throw new DomainException("裁决人与裁决时间必填，禁止匿名审批");
    }
    // SoD：提交人不得审批自己提交的变更（N2 负向探针）。
    if (createdBy.equals(actor)) {
      throw new DomainException("SoD 违规：提交人不得自审");
    }
    SegmentApproval current = segments.get(segment);
    if (current == null) {
      throw new DomainException("变更请求缺少该分段: " + segment);
    }
    SegmentApproval decided = current.decide(target, actor, at, reason);
    Map<Segment, SegmentApproval> next = new LinkedHashMap<>(segments);
    next.put(segment, decided);
    Status nextStatus =
        target == ApprovalState.REJECTED
            ? Status.REJECTED
            : (allApproved(next) ? Status.APPROVED : Status.IN_REVIEW);
    List<TrailEntry> entries = new ArrayList<>(trail);
    entries.add(
        new TrailEntry(
            target == ApprovalState.APPROVED ? "APPROVED" : "REJECTED",
            segment,
            actor,
            at,
            reason));
    return new ChangeRequest(
        changeRequestId,
        tenantId,
        partyId,
        targetVersion,
        changeDescription,
        createdBy,
        createdAt,
        next,
        nextStatus,
        appliedBy,
        appliedAt,
        entries);
  }

  /** 应用到 Active 区的唯一入口。<b>调用方（编排层）必须在本方法返回后才允许写 Active 区</b>， 否则「未审批直接覆盖」就绕过了领域裁决（N1 负向探针）。 */
  public ChangeRequest apply(String applier, Instant at) {
    if (status == Status.APPLIED) {
      throw new DomainException("变更请求已应用，禁止重复应用");
    }
    if (status != Status.APPROVED) {
      throw new DomainException("变更请求未全部审批通过，禁止覆盖 Active 区，当前状态: " + status);
    }
    if (isBlank(applier) || at == null) {
      throw new DomainException("应用人与应用时间必填");
    }
    Set<String> approvers = approvers();
    if (approvers.size() < 2) {
      throw new DomainException("双人复核未满足：三段审批人至少需两名不同具名账号");
    }
    if (applier.equals(createdBy) || approvers.contains(applier)) {
      throw new DomainException("SoD 违规：应用人不得是提交人或任一审批人");
    }
    List<TrailEntry> entries = new ArrayList<>(trail);
    entries.add(new TrailEntry("APPLIED", null, applier, at, null));
    return new ChangeRequest(
        changeRequestId,
        tenantId,
        partyId,
        targetVersion,
        changeDescription,
        createdBy,
        createdAt,
        segments,
        Status.APPLIED,
        applier,
        at,
        entries);
  }

  public ChangeRequest cancel(String operator, Instant at) {
    if (status == Status.APPLIED || status == Status.CANCELLED) {
      throw new DomainException("已应用/已撤销的变更请求不可再撤销");
    }
    if (isBlank(operator) || at == null) {
      throw new DomainException("撤销人与时间必填");
    }
    return with(Status.CANCELLED, "CANCELLED", null, operator, at, null);
  }

  /** 已通过审批的具名账号集合（去重用）。 */
  public Set<String> approvers() {
    Set<String> result = new LinkedHashSet<>();
    for (SegmentApproval approval : segments.values()) {
      if (approval.getState() == ApprovalState.APPROVED) {
        result.add(approval.getDecidedBy());
      }
    }
    return result;
  }

  public boolean allSegmentsApproved() {
    return allApproved(segments);
  }

  private ChangeRequest with(
      Status newStatus, String action, Segment segment, String actor, Instant at, String reason) {
    List<TrailEntry> entries = new ArrayList<>(trail);
    entries.add(new TrailEntry(action, segment, actor, at, reason));
    return new ChangeRequest(
        changeRequestId,
        tenantId,
        partyId,
        targetVersion,
        changeDescription,
        createdBy,
        createdAt,
        segments,
        newStatus,
        appliedBy,
        appliedAt,
        entries);
  }

  private static boolean allApproved(Map<Segment, SegmentApproval> approvals) {
    if (approvals.size() != Segment.values().length) {
      return false;
    }
    for (Segment s : Segment.values()) {
      SegmentApproval a = approvals.get(s);
      if (a == null || a.getState() != ApprovalState.APPROVED) {
        return false;
      }
    }
    return true;
  }

  private static boolean isBlank(String v) {
    return v == null || v.trim().isEmpty();
  }

  public String getChangeRequestId() {
    return changeRequestId;
  }

  public long getTenantId() {
    return tenantId;
  }

  public String getPartyId() {
    return partyId;
  }

  public long getTargetVersion() {
    return targetVersion;
  }

  public String getChangeDescription() {
    return changeDescription;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Map<Segment, SegmentApproval> getSegments() {
    return segments;
  }

  public Status getStatus() {
    return status;
  }

  public String getAppliedBy() {
    return appliedBy;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public List<TrailEntry> getTrail() {
    return trail;
  }
}
