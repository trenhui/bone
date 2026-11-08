package com.bone.claim.domain.model.adjustment;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.LiabilityFeeRule;
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

  /** 本案所属共享池代码（与 liability_uuid 同源，结算选账户用）。 */
  @Column(name = "shared_code", nullable = false)
  private String sharedCode;

  @Column(name = "rule_version")
  private String ruleVersion;

  // ── §9.4 版本链（0100 迁移备列，受理时刻锁定；空串 = 该维度未参与本次理算）──
  // 与 formula_snapshot 的分工：快照物化"责任参数"，版本链物化"环境指纹"——
  // 审计链 SEC-05 的可重建性缺了环境维度就无法与 claim_decision_execution 对齐到同一次执行。

  /** 计划版本（与 claim_case.plan_version 一致）。 */
  @Column(name = "plan_version")
  private String planVersion;

  /** 目录版本（医保目录锁定值）。 */
  @Column(name = "catalog_version")
  private String catalogVersion;

  /** 模型版本（AI 评估节点）。 */
  @Column(name = "model_version")
  private String modelVersion;

  /** 提示词版本（AI 评估节点）。 */
  @Column(name = "prompt_version")
  private String promptVersion;

  /** 决策图版本（与 claim_decision_execution 一致）。 */
  @Column(name = "graph_version")
  private String graphVersion;

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
    return calculate(id, tenantId, claimId, rows, liability, ruleVersion, accumulation, null);
  }

  /**
   * 执行理算（带赔案受理日）。
   *
   * @param claimDate 赔案受理日：日限额「按同日赔案累计」的窗口来源；不配该口径时可为 {@code null}
   */
  public static Adjustment calculate(
      Long id,
      Long tenantId,
      Long claimId,
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      String ruleVersion,
      Accumulation accumulation,
      java.time.LocalDate claimDate) {
    return calculate(
        id,
        tenantId,
        claimId,
        rows,
        liability,
        ruleVersion,
        accumulation,
        claimDate,
        null,
        java.util.Map.of());
  }

  /**
   * 执行理算（带目录准入结论，缺口 ①）。
   *
   * <p><b>为什么目录结论由调用方传进来</b>：目录是外部主数据，查它是基础设施职责；聚合只消费事实， 才能保证"同输入同输出"。不传（走上面的重载）时 {@code
   * catalogAsOf=null}、结论为空——若责任配了 {@code MANDATORY}，{@link CatalogAdmission#of} 会直接拒绝（<b>不传 ≠
   * 关闭</b>），不会静默放行。
   *
   * @param catalogAsOf 目录判定基准日（发票日期，缺失回落赔案受理日）
   * @param catalogVerdicts {@code itemId → 准入结论}（由 {@code ItemCatalogPort} 查得）
   */
  public static Adjustment calculate(
      Long id,
      Long tenantId,
      Long claimId,
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      String ruleVersion,
      Accumulation accumulation,
      java.time.LocalDate claimDate,
      java.time.LocalDate catalogAsOf,
      java.util.Map<Long, CatalogAdmission.Verdict> catalogVerdicts) {
    return calculate(
        id,
        tenantId,
        claimId,
        rows,
        liability,
        ruleVersion,
        accumulation,
        claimDate,
        catalogAsOf,
        catalogVerdicts,
        List.of());
  }

  /**
   * 执行理算（带目录准入结论与<b>费用类别规则</b>）。
   *
   * <p><b>为什么类别规则由调用方传进来</b>：规则存在 {@code t_adj_liability_fee_rule}（配置态，挂 {@code
   * liability_uuid}），读它是应用层的职责。聚合只消费"这一案生效的规则"，才能保持"同输入同输出"——
   * 聚合自己去查库，复算时的输入就不再是显式参数，快照也无从证明用的是哪一版规则。
   *
   * @param feeRules 生效的费用类别规则（空 = 责任未做类别细化，引擎行为与引入前逐字一致）
   */
  public static Adjustment calculate(
      Long id,
      Long tenantId,
      Long claimId,
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      String ruleVersion,
      Accumulation accumulation,
      java.time.LocalDate claimDate,
      java.time.LocalDate catalogAsOf,
      java.util.Map<Long, CatalogAdmission.Verdict> catalogVerdicts,
      List<LiabilityFeeRule> feeRules) {
    return calculate(
        id,
        tenantId,
        claimId,
        rows,
        liability,
        ruleVersion,
        accumulation,
        claimDate,
        catalogAsOf,
        catalogVerdicts,
        feeRules,
        CalculationVersions.NONE);
  }

  /**
   * 执行理算（带目录准入结论、费用类别规则与<b>版本链</b>，终态工厂）。
   *
   * <p>版本链在受理时刻锁定（§9.4 全版本链，回放与复算必需）：计划取案件、 目录/模型/提示词/决策图取当次决策执行；未跑决策图等维度落空串。
   */
  public static Adjustment calculate(
      Long id,
      Long tenantId,
      Long claimId,
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      String ruleVersion,
      Accumulation accumulation,
      java.time.LocalDate claimDate,
      java.time.LocalDate catalogAsOf,
      java.util.Map<Long, CatalogAdmission.Verdict> catalogVerdicts,
      List<LiabilityFeeRule> feeRules,
      CalculationVersions versions) {
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
    CalculationVersions chain = versions == null ? CalculationVersions.NONE : versions;
    Adjustment adjustment = new Adjustment(id, tenantId, claimId, ruleVersion);
    adjustment.sharedCode = liability.getSharedCode();
    adjustment.planVersion = chain.planVersion();
    adjustment.catalogVersion = chain.catalogVersion();
    adjustment.modelVersion = chain.modelVersion();
    adjustment.promptVersion = chain.promptVersion();
    adjustment.graphVersion = chain.graphVersion();
    adjustment.apply(
        rows, liability, accumulation, claimDate, catalogAsOf, catalogVerdicts, feeRules);
    return adjustment;
  }

  /** 重算（同一聚合内重新计算；已确认 CONFIRMED 禁止调用，见 {@link #requireModifiable}）。 */
  public void recalculate(
      List<ClaimWithItemsProjection> rows, AdjLiability liability, Accumulation accumulation) {
    recalculate(rows, liability, accumulation, null, null, java.util.Map.of());
  }

  /** 重算（带赔案受理日，口径同 {@link #recalculate(List, AdjLiability, Accumulation)}）。 */
  public void recalculate(
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      Accumulation accumulation,
      java.time.LocalDate claimDate) {
    recalculate(rows, liability, accumulation, claimDate, null, java.util.Map.of());
  }

  /** 重算（带赔案受理日 + 目录准入结论，口径同 {@code calculate} 的十参重载）。 */
  public void recalculate(
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      Accumulation accumulation,
      java.time.LocalDate claimDate,
      java.time.LocalDate catalogAsOf,
      java.util.Map<Long, CatalogAdmission.Verdict> catalogVerdicts) {
    recalculate(rows, liability, accumulation, claimDate, catalogAsOf, catalogVerdicts, List.of());
  }

  /** 重算（带费用类别规则，口径同 {@code calculate} 的十一参重载）。 */
  public void recalculate(
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      Accumulation accumulation,
      java.time.LocalDate claimDate,
      java.time.LocalDate catalogAsOf,
      java.util.Map<Long, CatalogAdmission.Verdict> catalogVerdicts,
      List<LiabilityFeeRule> feeRules) {
    recalculate(
        rows, liability, accumulation, claimDate, catalogAsOf, catalogVerdicts, feeRules, null);
  }

  /**
   * 重算（带费用类别规则与<b>版本链刷新</b>，终态重载）。
   *
   * <p><b>{@code versions == null} 表示"保持原版本链"而不是清空</b>：旧签名调用方 并不知道版本链的存在，若把 null
   * 当空串写回，等于让"重算"把审计链的环境维度 抹掉——审计视角下"重算用了哪版规则/模型"永远不可知。只有显式传入新链 （应用层从当前决策执行/案件重新取值）才刷新。
   */
  public void recalculate(
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      Accumulation accumulation,
      java.time.LocalDate claimDate,
      java.time.LocalDate catalogAsOf,
      java.util.Map<Long, CatalogAdmission.Verdict> catalogVerdicts,
      List<LiabilityFeeRule> feeRules,
      CalculationVersions versions) {
    requireModifiable("已确认的理算结果禁止重算");
    if (versions != null) {
      this.planVersion = versions.planVersion();
      this.catalogVersion = versions.catalogVersion();
      this.modelVersion = versions.modelVersion();
      this.promptVersion = versions.promptVersion();
      this.graphVersion = versions.graphVersion();
    }
    apply(rows, liability, accumulation, claimDate, catalogAsOf, catalogVerdicts, feeRules);
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
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      Accumulation accumulation,
      java.time.LocalDate claimDate,
      java.time.LocalDate catalogAsOf,
      java.util.Map<Long, CatalogAdmission.Verdict> catalogVerdicts,
      List<LiabilityFeeRule> feeRules) {
    LiabilityRule rule =
        LiabilityRule.fromLegacy(
                liability.getLiabilityUuid(),
                liability.getLiabilityVersion(),
                liability.getPayPercent(),
                liability.deduct(),
                liability.limit(),
                liability.isSocialOnly(),
                liability.invoiceDeduct(),
                liability.adjustDeduct(),
                liability.dailyLimit(),
                LiabilityRule.DailyBasis.fromCode(liability.dailyLimitBasis()),
                LiabilityRule.LimitedConditions.of(
                    LiabilityRule.LimitedConditions.Mode.fromCode(liability.limitMode()),
                    liability.limitHospitalCodes(),
                    liability.limitDiagnosisCodes()))
            .withFeeRules(feeRulesOf(feeRules))
            .withRelation(
                LiabilityRule.Relation.of(
                    liability.getSharedCode(), liability.getPriorLiabilityUuid()));

    AdjustmentEngine.Outcome outcome;
    try {
      // 目录准入（缺口 ①）：策略与目录编码都来自责任配置，结论由应用层查端口后作为事实传入——
      // 聚合不查外部系统，保证理算"同输入同输出"可复算。反向一致性校验在 CatalogAdmission.of 内，
      // 与日限额同一套纪律：有编码没开策略、开了策略没编码/没基准日/没结论，一律拒绝。
      CatalogAdmission catalog =
          CatalogAdmission.of(
              liability.catalogPolicy(), liability.catalogCode(), catalogAsOf, catalogVerdicts);
      outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(getClaimId(), rows, rule, accumulation)
                  .withRuleVersion(this.ruleVersion)
                  .withClaimDate(claimDate)
                  .withCatalog(catalog));
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
   * 费用类别规则：实体 → 引擎规则值对象。
   *
   * <p><b>为什么不把实体直接塞进 {@link LiabilityRule}</b>：引擎是纯计算的领域对象，它的规则集必须能被快照、 被复算、被单测直接构造；带
   * {@code @Table}/{@code @Version} 的持久化实体进来，会让"能不能算"取决于 "这行有没有落过库"。映射放在这一层，口径只有一处。
   *
   * <p>{@code socialOnly} 实体是 {@code Integer}(1/0)，{@code null} 按<b>社保内</b>处理（与 {@link
   * LiabilityFeeRule#of} 的默认值一致）：取严的那一侧是安全侧，放宽才会超赔。
   */
  private static List<LiabilityRule.FeeCategoryRule> feeRulesOf(List<LiabilityFeeRule> feeRules) {
    if (feeRules == null || feeRules.isEmpty()) {
      return List.of();
    }
    List<LiabilityRule.FeeCategoryRule> rules = new ArrayList<>(feeRules.size());
    for (LiabilityFeeRule entity : feeRules) {
      if (entity == null) {
        continue;
      }
      rules.add(
          new LiabilityRule.FeeCategoryRule(
              entity.getFeeCode(),
              entity.getFeeName(),
              entity.getPayPercent(),
              entity.getLimitAmount(),
              entity.getSocialOnly() == null || entity.getSocialOnly() == 1));
    }
    return rules;
  }

  /**
   * 行快照（{@code t_adj_calculation_line.input_snapshot}）。
   *
   * <p><b>为什么把 bucketLabel / ratioPercent 一起落库</b>：引擎的 {@code Line} 已算出这两个值， 但外壳只写了 {@code bucket}
   * 与 {@code scope}（都是英文码：{@code REASONABLE} / {@code IN|OUT}）。 前端要显示「合理金额 / 自付二 /
   * 总自费」与本次赔付比例，只能自己再建一份桶名映射—— 那就是**同一套词汇的第三份副本**（引擎一份、文档一份、前端一份），改一处必然漏两处。 落库后展示口径与计算口径同源。
   *
   * <p><b>为什么还要落 itemId / itemName</b>：逐行分位归因（GAP⑨）落地后，一行对应一笔费用明细，
   * 缺了费用项标识这行就无从追溯到发票上的具体收费项，审计与客户对账都会回到"按池汇总"的老问题。 明细表没有 item_id 列，故随快照落库（{@code itemName}
   * 是自由文本，必须转义——JSON 里一个引号就会让整段快照失效）。
   */
  private static String inputSnapshotOf(AdjustmentEngine.Line line) {
    String ratio = line.ratioPercent() == null ? "null" : line.ratioPercent().toPlainString();
    // 费用类别仅在拆过类别时输出：未配类别规则的责任沿用旧形态，存量行的快照不会因本次改动而变样。
    String feeTag =
        line.feeCode() == null || line.feeCode().isEmpty()
            ? ""
            : ",\"feeCode\":\"" + jsonEscape(line.feeCode()) + "\"";
    return "{\"bucket\":\""
        + line.bucket()
        + "\",\"bucketLabel\":\""
        + line.bucketLabel()
        + "\",\"scope\":\""
        + line.scope()
        + "\",\"ratioPercent\":"
        + ratio
        + feeTag
        + ",\"itemId\":"
        + (line.itemId() == null ? "null" : line.itemId())
        + ",\"itemName\":\""
        + jsonEscape(line.itemName())
        + "\",\"cappedBy\":\""
        + jsonEscape(line.cappedBy())
        + "\"}";
  }

  /** 最小 JSON 字符串转义（只做 JSON 合法性所需，不改变原文语义）。 */
  private static String jsonEscape(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (ch < 0x20) {
            sb.append("\\u").append(String.format("%04x", (int) ch));
          } else {
            sb.append(ch);
          }
        }
      }
    }
    return sb.toString();
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
