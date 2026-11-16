package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 合并审计：逐字段胜出决策 + 合并前后完整快照，支撑误合并的整笔撤销与事后仲裁。 */
public final class MergeAudit {

  /** 逐字段胜出决策（ruleId 与 lineage 同源，便于回放）。 */
  public static final class MergeDecision {
    private final String fieldCode;
    private final String winningSourceRecordId;
    private final String ruleId;
    private final String reason;

    private MergeDecision(
        String fieldCode, String winningSourceRecordId, String ruleId, String reason) {
      this.fieldCode = fieldCode;
      this.winningSourceRecordId = winningSourceRecordId;
      this.ruleId = ruleId;
      this.reason = reason;
    }

    public static MergeDecision of(
        String fieldCode, String winningSourceRecordId, String ruleId, String reason) {
      return new MergeDecision(fieldCode, winningSourceRecordId, ruleId, reason);
    }

    public String getFieldCode() {
      return fieldCode;
    }

    public String getWinningSourceRecordId() {
      return winningSourceRecordId;
    }

    public String getRuleId() {
      return ruleId;
    }

    public String getReason() {
      return reason;
    }
  }

  private final String mergeId;
  private final long tenantId;
  private final String partyId;

  /** 合并前快照；首次建立黄金记录时为 null（撤销即回到「无」）。 */
  private final GoldenRecord before;

  private final GoldenRecord after;
  private final List<MergeDecision> decisions;
  private final Instant createdAt;
  private boolean rolledBack;
  private String rollbackReason;
  private Instant rolledBackAt;
  private String rolledBackBy;

  private MergeAudit(
      String mergeId,
      long tenantId,
      String partyId,
      GoldenRecord before,
      GoldenRecord after,
      List<MergeDecision> decisions,
      Instant createdAt) {
    this.mergeId = mergeId;
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.before = before;
    this.after = after;
    this.decisions = Collections.unmodifiableList(new ArrayList<>(decisions));
    this.createdAt = createdAt;
  }

  public static MergeAudit of(
      String mergeId,
      long tenantId,
      String partyId,
      GoldenRecord before,
      GoldenRecord after,
      List<MergeDecision> decisions,
      Instant createdAt) {
    if (mergeId == null || mergeId.trim().isEmpty() || after == null || createdAt == null) {
      throw new DomainException("合并审计缺少 mergeId/after/createdAt");
    }
    return new MergeAudit(mergeId, tenantId, partyId, before, after, decisions, createdAt);
  }

  /** 整笔撤销：返回合并前完整快照（含字段级 lineage），不返回「部分字段恢复」。 撤销必须具名、可重复审计，且不可重复执行。 */
  public Optional<GoldenRecord> rollback(String reason, String operator, Instant at) {
    if (rolledBack) {
      throw new DomainException("该合并已撤销，不得重复回滚");
    }
    if (reason == null
        || reason.trim().isEmpty()
        || operator == null
        || operator.trim().isEmpty()) {
      throw new DomainException("撤销必须给出原因与操作人");
    }
    rolledBack = true;
    rollbackReason = reason;
    rolledBackBy = operator;
    rolledBackAt = at;
    return Optional.ofNullable(before);
  }

  public String getMergeId() {
    return mergeId;
  }

  public long getTenantId() {
    return tenantId;
  }

  public String getPartyId() {
    return partyId;
  }

  public GoldenRecord getBefore() {
    return before;
  }

  public GoldenRecord getAfter() {
    return after;
  }

  public List<MergeDecision> getDecisions() {
    return decisions;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isRolledBack() {
    return rolledBack;
  }

  public String getRollbackReason() {
    return rollbackReason;
  }

  public Instant getRolledBackAt() {
    return rolledBackAt;
  }

  public String getRolledBackBy() {
    return rolledBackBy;
  }
}
