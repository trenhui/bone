package com.bone.claim.domain.model.claim.valueobject;

/**
 * 案件状态码（内部细粒度，pk-tpa-saas 数字码体系移植）。
 *
 * <p><b>对外只投影两值</b>（{@code PENDING_EVIDENCE} / {@code REVIEWING}），见 {@link #toExternal()}；
 * 数字码属内部状态机私有，落在 {@code t_claim_case.status}，仅 {@code integration_sync_log} 与存量桥接层可见（工程落地版 §6.1）。
 *
 * <p>合法跃迁只允许由 {@code ClaimCase} 聚合根发起（转换表 = 工程落地版 §6.2）。
 */
public enum ClaimStatus {

  /** 草稿（未受理）。 */
  DRAFT("DRAFT", "草稿"),
  /** 已签收/受理（对外 PENDING_EVIDENCE）。 */
  ACCEPTED("7", "已签收"),
  /** 等待初审-未分配。 */
  PENDING_PRE_EXAM("10", "待初审分配"),
  /** 初审中（人工）。 */
  PRE_EXAMINING("11", "初审中"),
  /** 自动化初审中（决策图执行）。 */
  AUTO_PRE_EXAMINING("13", "自动化初审中"),
  /** 已初审（决策完成，产出 DecisionResult）。 */
  PRE_EXAMINED("12", "已初审"),
  /** 补录中（缺件/OCR，对外 PENDING_EVIDENCE）。 */
  EVIDENCE_SUBMITTING("21", "补录中"),
  /** 等待人工核赔。 */
  PENDING_ADJUST("40", "待核赔"),
  /** 人工核赔中。 */
  ADJUSTING("41", "核赔中"),
  /** 已审核（人工审核通过）。 */
  ADJUSTED("42", "已审核"),
  /** 复核中（大额 ≥1 万双人复核）。 */
  REVIEWING("44", "复核中"),
  /** 已撤销/拒赔生效（终态，不回传对外枚举）。 */
  CLOSED_REJECTED("45", "已撤销/拒赔生效"),
  /** 已结案（已支付，终态）。 */
  FINISHED("50", "已结案"),
  /** 申诉中（48h 复议，对外仍投影 REVIEWING）。 */
  APPEALING("60", "申诉中");

  private final String code;
  private final String displayName;

  ClaimStatus(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  /** 内部数字码（DB 持久化值）。 */
  public String code() {
    return code;
  }

  public String displayName() {
    return displayName;
  }

  /** 是否终态（不再跃迁，仅可由申诉入口进入 60）。 */
  public boolean terminal() {
    return this == CLOSED_REJECTED || this == FINISHED;
  }

  /** 材料未齐/待受理段（对外 PENDING_EVIDENCE）。 */
  public boolean pendingEvidence() {
    return this == DRAFT || this == ACCEPTED || this == EVIDENCE_SUBMITTING;
  }

  /** 已进入决策或人工审核段（对外 REVIEWING）。 */
  public boolean reviewing() {
    return !pendingEvidence() && !terminal();
  }

  /** 对外枚举两值投影（delivery/05 唯一真源；终态不回传状态枚举）。 */
  public String toExternal() {
    if (terminal()) {
      return null;
    }
    return pendingEvidence() ? "PENDING_EVIDENCE" : "REVIEWING";
  }

  /** DB name → 枚举（SDK String 持久化口径）。 */
  public static ClaimStatus ofName(String name) {
    for (ClaimStatus s : values()) {
      if (s.name().equals(name)) {
        return s;
      }
    }
    throw new IllegalArgumentException("未知案件状态: " + name);
  }

  public static ClaimStatus ofCode(String code) {
    for (ClaimStatus s : values()) {
      if (s.code.equals(code)) {
        return s;
      }
    }
    throw new IllegalArgumentException("未知案件状态码: " + code);
  }
}
