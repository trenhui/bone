package com.bone.claim.domain.model.adjustment;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 理算聚合根（A6，多租户）。
 *
 * <p><b>AI 不碰钱</b>（P1 原则）：本类是赔付金额的<b>唯一</b>计算入口。自 V1 起，金额计算全部委托 {@link AdjustmentEngine}（费用池 → 比例查表
 * → 免赔 6 维 → 控额 7 层 → 不变量自证）， 本类只保留<b>聚合外壳</b>职责：状态机（FROZEN/CONFIRMED/UNFROZEN/ROLLBACKED）、
 * 快照物化、乐观锁与明细落库。
 *
 * <p><b>为什么不再在聚合里写算式</b>：V1 的理算链有 6 维免赔 × 7 层控额 × 多维比例，写进聚合会让
 * 这个类同时承担"规则执行"和"状态治理"两种变化原因——改条款动它、改流程也动它，测试无法分辨是规则错了还是状态错了。
 * 拆开后，"同一份条款两种算法"的风险从"两处实现"降为"零处"：聚合里没有第二条算式。
 *
 * <p><b>快照不可仅版本引用</b>（§5.2）：理算把当期责任参数<b>物化</b>进 {@code formulaSnapshot}
 * ——源配置后续被改时，本记录仍可自包含复算（金融理算「同输入同输出」是合规硬要求）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_adj_calculation")
public class Adjustment extends TenantAggregateRoot<Long> {

  /** 理算结果状态：FROZEN 已冻结 / CONFIRMED 已确认 / UNFROZEN 已解冻 / ROLLBACKED 已回滚（§5.3）。 */
  public enum ResultStatus {
    FROZEN,
    CONFIRMED,
    UNFROZEN,
    ROLLBACKED
  }

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "liability_uuid", nullable = false)
  private String liabilityUuid;

  @Column(name = "liability_version", nullable = false)
  private String liabilityVersion;

  @Column(name = "rule_version")
  private String ruleVersion;

  /** 公式快照（物化的责任参数，JSON；复算的唯一依据）。 */
  @Column(name = "formula_snapshot", nullable = false)
  private String formulaSnapshot;

  /** 快照哈希（复算校验：同输入同输出）。 */
  @Column(name = "formula_hash", nullable = false)
  private String formulaHash;

  /** 理算合计（= Σ 明细实付）。 */
  @Column(name = "total_amount", nullable = false)
  private BigDecimal totalAmount;

  /**
   * 本次实际消抵的年度免赔（{@code ⑩} 跨案累计的<b>写侧增量</b>，不落库）。
   *
   * <p>由理算内核产出、由 A7 结算经 {@code QuotaApplicationService} 累加进责任账 {@code
   * deductible_used}——不回写就没有次案，等于护栏只装了一半。
   *
   * <p><b>相对免赔时可能为 0</b>：达门槛后"一分不减"，确实没有消抵（账面不虚增）。
   */
  @Transient private BigDecimal deductibleApplied;

  /**
   * 本次理算所依据的跨案累计是否真实可读（⑩，不落库）。
   *
   * <p>{@code false} = 理算是在"已用未知"的前提下算出来的，结果仅供人工参考，A7 不得据此占额。
   */
  @Transient private boolean accumulationAvailable;

  @Column(name = "result_status", nullable = false)
  private String resultStatus;

  @Transient
  @Cascade(foreignKey = "calculationId", orphanRemoval = false)
  private List<AdjustmentLine> lines = new ArrayList<>();

  @Version
  @Column(name = "version")
  private Long version;

  // 审计列：基类（TenantAggregateRoot）不含审计字段（见其类注释「审计由子类自行声明」），
  // SDK 只映射 @Column 字段——漏声明会让 INSERT 不带 created_at/updated_at，
  // 而 0058 DDL 这两列 NOT NULL 无默认值，执行理算必然 1364。
  @Column(name = "created_at", nullable = false)
  private java.time.Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private java.time.Instant updatedAt;

  private Adjustment(Long id, Long tenantId, Long claimId, String ruleVersion) {
    setId(id);
    setTenantId(tenantId);
    this.claimId = claimId;
    this.ruleVersion = ruleVersion == null ? "" : ruleVersion;
    this.resultStatus = ResultStatus.FROZEN.name();
    this.totalAmount = new BigDecimal("0.00");
    this.deductibleApplied = new BigDecimal("0.00");
    java.time.Instant now = java.time.Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * 执行理算（工厂方法，产出即冻结 {@code FROZEN}）。
   *
   * @param rows 费用明细（读侧投影，须已三段拆分：{@code validAmount}/{@code selfPay1} 就绪； 取自 {@code
   *     findClaimWithItems}——聚合 {@code findById} 不回填集合，与 blueprint 同纪律）
   * @param liability 当期责任配置（版本化，参数被物化进快照）
   * @param accumulation 跨案累计快照（⑩；必填，无默认重载）
   */
  public static Adjustment calculate(
      Long id,
      Long tenantId,
      Long claimId,
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      String ruleVersion,
      Accumulation accumulation) {
    if (rows == null || rows.isEmpty()) {
      throw new DomainException("理算明细不能为空");
    }
    if (liability == null) {
      throw new DomainException("责任配置缺失或未生效，无法理算");
    }
    if (!liability.isActive()) {
      throw new DomainException("责任配置缺失或未生效，无法理算");
    }
    if (accumulation == null) {
      throw new DomainException("跨案累计快照缺失，无法理算（⑩：禁止按『已用 0』隐式计算）");
    }
    Adjustment adjustment = new Adjustment(id, tenantId, claimId, ruleVersion);
    adjustment.apply(rows, liability, accumulation);
    return adjustment;
  }

  /** 重算（同一聚合内重新计算；已确认 CONFIRMED 禁止调用，见 {@link #requireModifiable}）。 */
  public void recalculate(
      List<ClaimWithItemsProjection> rows, AdjLiability liability, Accumulation accumulation) {
    requireModifiable("已确认的理算结果禁止重算");
    apply(rows, liability, accumulation);
    this.updatedAt = java.time.Instant.now();
  }

  /** 核赔确认（frozen → claim，同额转移不二次占用）。 */
  public void confirm() {
    requireModifiable("已确认的理算结果不可重复确认");
    this.resultStatus = ResultStatus.CONFIRMED.name();
    this.updatedAt = java.time.Instant.now();
  }

  /** 解冻（释放冻结额度，回到可重算态）。 */
  public void unfreeze() {
    requireModifiable("已确认的理算结果禁止解冻（须先走冲正）");
    this.resultStatus = ResultStatus.UNFROZEN.name();
    this.updatedAt = java.time.Instant.now();
  }

  /** 回滚（拒赔/撤销：释放占用）。 */
  public void rollback() {
    requireModifiable("已确认的理算结果禁止直接回滚（须先走冲正）");
    this.resultStatus = ResultStatus.ROLLBACKED.name();
    this.updatedAt = java.time.Instant.now();
  }

  public ResultStatus resultStatusEnum() {
    return ResultStatus.valueOf(resultStatus);
  }

  /** 明细集合（包级可见；理由同 blueprint：{@code findById} 不回填集合，读侧走仓储投影）。 */
  List<AdjustmentLine> lines() {
    return Collections.unmodifiableList(lines);
  }

  // ===================== 委托理算内核 =====================

  /**
   * 调 {@link AdjustmentEngine} 并把结果映射回聚合。
   *
   * <p><b>外壳只做三件事</b>：把旧责任配置映射成 {@link LiabilityRule}、把 {@link RuleGap} 翻译成统一的 {@link
   * DomainException}、把引擎输出物化成聚合字段与明细行。这里<b>没有任何算术</b>。
   */
  private void apply(
      List<ClaimWithItemsProjection> rows, AdjLiability liability, Accumulation accumulation) {
    LiabilityRule rule =
        LiabilityRule.fromLegacy(
            liability.getLiabilityUuid(),
            liability.getLiabilityVersion(),
            liability.getPayPercent(),
            liability.deduct(),
            liability.limit(),
            liability.isSocialOnly(),
            liability.invoiceDeduct(),
            liability.adjustDeduct());

    AdjustmentEngine.Outcome outcome;
    try {
      outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(getClaimId(), rows, rule, accumulation)
                  .withRuleVersion(this.ruleVersion));
    } catch (RuleGap gap) {
      // 规则缺口属"业务上算不出"，外壳对外统一成领域异常；原因码留在异常信息里可被审计日志提取。
      throw new DomainException(gap.getMessage());
    }

    this.liabilityUuid = liability.getLiabilityUuid();
    this.liabilityVersion = liability.getLiabilityVersion();
    this.formulaSnapshot = outcome.snapshot();
    this.formulaHash = sha256(outcome.snapshot());
    this.totalAmount = outcome.totalAmount();
    this.deductibleApplied = outcome.deductibleApplied();
    this.accumulationAvailable = outcome.accumulationAvailable();
    this.lines = new ArrayList<>();
    for (AdjustmentEngine.Line line : outcome.lines()) {
      AdjustmentLine outputLine =
          AdjustmentLine.of(
              null,
              getId(),
              this.liabilityUuid,
              "OUTPUT",
              inputSnapshotOf(line),
              line.feeCalculate(),
              line.feeDeduct(),
              line.ratioPayout(),
              line.feeOccupied(),
              line.feePayout(),
              line.feeRemain(),
              line.zeroReason(),
              line.rejectType(),
              this.formulaHash);
      // 明细行随聚合落库，租户口径与聚合根一致（t_adj_calculation_line.tenant_id NOT NULL）
      outputLine.setTenantId(getTenantId());
      this.lines.add(outputLine);
    }
  }

  /**
   * 行快照（{@code t_adj_calculation_line.input_snapshot}）。
   *
   * <p><b>为什么把 bucketLabel / ratioPercent 一起落库</b>：引擎的 {@code Line} 已算出这两个值， 但外壳只写了 {@code bucket}
   * 与 {@code scope}（都是英文码：{@code REASONABLE} / {@code IN|OUT}）。 前端要显示「合理金额 / 自付二 /
   * 总自费」与本次赔付比例，只能自己再建一份桶名映射—— 那就是**同一套词汇的第三份副本**（引擎一份、文档一份、前端一份），改一处必然漏两处。 落库后展示口径与计算口径同源。
   *
   * <p>取值均为受控枚举与定点小数，不含自由文本，故直接内联拼接即可（无需 JSON 转义）。
   */
  private static String inputSnapshotOf(AdjustmentEngine.Line line) {
    String ratio = line.ratioPercent() == null ? "null" : line.ratioPercent().toPlainString();
    return "{\"bucket\":\""
        + line.bucket()
        + "\",\"bucketLabel\":\""
        + line.bucketLabel()
        + "\",\"scope\":\""
        + line.scope()
        + "\",\"ratioPercent\":"
        + ratio
        + "}";
  }

  private static String sha256(String input) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

  private void requireModifiable(String message) {
    if (ResultStatus.CONFIRMED == resultStatusEnum()) {
      throw new DomainException(message + "（§5.3 硬不变量：已付额度禁止重复释放）");
    }
  }
}
