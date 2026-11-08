package com.bone.claim.domain.model.reconciliation;

import com.bone.core.exception.DomainException;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 对账差异明细（finding append-only，§11.7 / §9.5）。
 *
 * <p><b>什么不变、什么变</b>：{@code diff_type/金额/阶段/对方状态} 是对账<b>结论</b>，一经落库不可改（改它就等于 改写证据）；{@code
 * resolution/resolved_by} 是<b>处置态</b>，只允许从 {@code PENDING} 单向迁移一次。 每次迁移同时追加一条 {@code
 * t_reconciliation_action}（处置全历史），因此"谁在何时把它标成什么、为什么" 不会因为后续再次处置而丢失。
 *
 * <p><b>为什么处置态不整体搬到处置流水里</b>：{@code idx_resolution(resolution, created_at)} 与 {@code findPending}
 * 都要按"还没处置"筛，处置态留一行是最直接的过滤条件；全历史仍以流水为准。
 *
 * <p><b>{@code resolved_at} 为什么没有映射</b>：§9.5 目标态与 {@code db/migration/V7} 里都有这一列，但**生效通道** （{@code
 * scripts/migration/0072} 与 {@code bone-init.sql}）没有建它。写一个库里不存在的列 = 运行期 ERROR 1054，
 * 且只在真正处置差异时才炸——故处置时间以 {@code t_reconciliation_action.occurred_at} 为准（同一事务内写入，两者等价）。
 * 要补这一列须先走生效通道迁移，不能只在实体上加字段。
 */
@Getter
@Table("t_reconciliation_diff")
public class ReconciliationDiff extends TenantAbstractEntity<Long> {

  public static final String RESOLUTION_PENDING = "PENDING";

  /** 已消除（重跑对账不再出现，或人工确认账实已一致）。 */
  public static final String RESOLUTION_RESOLVED = "RESOLVED";

  /** 已调账（走了补偿流水 / 更正单据，账已改）。 */
  public static final String RESOLUTION_ADJUSTED = "ADJUSTED";

  /** 已豁免（确认属可接受的差异，不做处理，须写明理由）。 */
  public static final String RESOLUTION_WAIVED = "WAIVED";

  /**
   * 允许的处置结论集合（{@code PENDING} 不是可写入的结论）。
   *
   * <p>用 {@code List} 而不是 {@code Set}：这个集合同时被接口层当作"可选值"渲染（前端下拉）， 无序集合会让每次重建的顺序都可能不同。元素只有三个，{@code
   * contains} 的线性查找可忽略。
   */
  public static final java.util.List<String> RESOLUTIONS =
      java.util.List.of(RESOLUTION_RESOLVED, RESOLUTION_ADJUSTED, RESOLUTION_WAIVED);

  @Column(name = "report_id", nullable = false)
  private Long reportId;

  @Column(name = "claim_no", nullable = false)
  private String claimNo;

  @Column(name = "settlement_no", nullable = false)
  private String settlementNo;

  @Column(name = "diff_type", nullable = false)
  private String diffType;

  @Column(name = "local_amount")
  private BigDecimal localAmount;

  @Column(name = "remote_amount")
  private BigDecimal remoteAmount;

  @Column(name = "local_stage", nullable = false)
  private String localStage;

  @Column(name = "remote_status", nullable = false)
  private String remoteStatus;

  @Column(name = "resolution", nullable = false)
  private String resolution;

  @Column(name = "resolved_by", nullable = false)
  private String resolvedBy;

  /**
   * 乐观锁版本（并发处置的唯一仲裁者）。
   *
   * <p><b>它挡住的是什么</b>：两个运维同时打开同一条差异，各自读到 {@code PENDING}。{@link #resolve} 的前置检查只能 保证"读到的这一份是
   * PENDING"，无法保证"写回时仍是 PENDING"。没有版本条件时，后写者整行覆盖先写者的处置态，
   * 界面表现为"我明明标了已调账，刷新后变成了已豁免"，而两条处置流水都在——查得到历史，查不出谁覆盖了谁。
   *
   * <p><b>为什么默认值是 0 而不是 null</b>：SDK 遇到 {@code @Version} 为 null 会直接拒绝写入（无法构造 {@code version = :old}
   * 条件）。生效通道（{@code scripts/migration/0098} 与 {@code bone-init.sql}）给了 {@code DEFAULT 0}， 存量行等价于"第
   * 0 版"，首次处置即 0 → 1，与新建行一致，无需数据迁移。
   *
   * <p><b>失败如何呈现</b>：版本条件命中 0 行时 SDK 抛 {@code OptimisticLockingFailureException}，由 {@code
   * ReconciliationDispositionService} 翻译成 409 {@code SC_CLAIM_RECON_DIFF_CONFLICT}
   * 并提示"刷新后重试"。不在此处吞掉 异常——静默忽略会让调用方以为处置成功。
   */
  @Version
  @Column(name = "version")
  private Long version;

  ReconciliationDiff() {}

  public static ReconciliationDiff create(
      Long tenantId,
      Long reportId,
      String claimNo,
      String settlementNo,
      DiffType diffType,
      BigDecimal localAmount,
      BigDecimal remoteAmount,
      String localStage,
      String remoteStatus) {
    ReconciliationDiff diff = new ReconciliationDiff();
    diff.setTenantId(tenantId);
    diff.reportId = reportId;
    diff.claimNo = claimNo == null ? "" : claimNo;
    diff.settlementNo = settlementNo == null ? "" : settlementNo;
    diff.diffType = diffType.name();
    diff.localAmount = localAmount;
    diff.remoteAmount = remoteAmount;
    diff.localStage = localStage == null ? "" : localStage;
    diff.remoteStatus = remoteStatus == null ? "" : remoteStatus;
    diff.resolution = RESOLUTION_PENDING;
    diff.resolvedBy = "";
    // 审计列由 SDK 映射但**不会自动填充**：DDL 两列 NOT NULL 无默认值，落库前必须显式赋值
    java.util.Date now = new java.util.Date();
    diff.setCreatedAt(now);
    diff.setUpdatedAt(now);
    return diff;
  }

  /** 是否尚未处置（处置队列与"重复处置"判定的唯一判据）。 */
  public boolean isPending() {
    return RESOLUTION_PENDING.equals(resolution);
  }

  /**
   * 给出处置结论（{@code PENDING → RESOLVED/ADJUSTED/WAIVED}，单向一次）。
   *
   * <p>不变量放在领域层：应用层负责把"差异不存在 / 已处置 / 结论非法"翻译成可读错误码，领域层保证不管从哪条路径进来都改不出 一个非法状态（例如将来加批量处置接口时，谁能少写一个
   * if，它不会跟着漏）。
   *
   * @param targetResolution 处置结论，取值见 {@link #RESOLUTIONS}
   * @param operatorId 处置人（鉴权身份）
   */
  public void resolve(String targetResolution, String operatorId) {
    if (!isPending()) {
      throw new DomainException("差异已处置（" + resolution + "），处置态只允许从 PENDING 单向迁移一次；要更正请追加新的处置流水");
    }
    if (targetResolution == null || !RESOLUTIONS.contains(targetResolution)) {
      throw new DomainException("非法处置结论: " + targetResolution + "（允许 " + RESOLUTIONS + "）");
    }
    if (operatorId == null || operatorId.isBlank()) {
      throw new DomainException("处置人不能为空");
    }
    this.resolution = targetResolution;
    this.resolvedBy = operatorId;
    this.setUpdatedAt(new java.util.Date());
  }
}
