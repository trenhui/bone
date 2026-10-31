package com.bone.claim.domain.model.adjustment.engine;

import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 理算引擎（V1 §4.2 九步主流程的唯一实现）。
 *
 * <p><b>与旧内核的关系</b>：旧 {@code Adjustment.apply} 是"一个基数 → 扣免赔 → 乘比例 → 截断"的标量四步， 只能表达"单责任单比例"。 本引擎按 V1
 * 重建为<b>分层管线</b>：费用池 → 比例查表 → 免赔（6 维）→ 控额（7 层）→ 次限额分组 → 不变量自证。
 * 旧聚合保留为外壳（事务、状态机、持久化），金额计算全部委托此处——<b>不允许两套算法并存</b>， 那正是"同一份条款两种算法"的来源。
 *
 * <p><b>管线顺序不可颠倒</b>（§4.2）：
 *
 * <pre>
 *   ① 费用归类（三池 + 医保内外）
 *   ② 比例查表（多维，未命中即 fail-closed）
 *   ③ 免赔（按 target 决定"折后减额"还是"减额后折"）
 *   ④ 控额（按 bucket 分组截断 → 责任 → 共保 → 险种 → 计划）
 *   ⑤ 汇总 + 归因（零赔付必须能说出为什么）
 * </pre>
 *
 * <p>把 ③ 和 ④ 调换会系统性<b>多赔</b>：先控额再扣免赔，等于用未扣免赔的金额去占用额度， 后续发票的可赔额被提前吃掉；反之先免赔再控额才符合 §4.2 步 5→7 的顺序。
 */
public final class AdjustmentEngine {

  /** 零赔付归因（受控枚举，禁自由文本；与 {@code t_adj_calculation_line.zero_reason} 对齐）。 */
  public static final String ZERO_REASONABLE = "ZERO_REASONABLE";

  public static final String ZERO_DEDUCT = "ZERO_DEDUCT";
  public static final String ZERO_PERCENT = "ZERO_PERCENT";
  public static final String ZERO_LIMIT = "ZERO_LIMIT";
  public static final String ZERO_LIMIT_EXHAUSTED = "ZERO_LIMIT_EXHAUSTED";

  /** 日限额（Q-05）当日已用满：与本年度额度用尽不同，改日即可再赔。 */
  public static final String ZERO_DAILY_EXHAUSTED = "ZERO_DAILY_EXHAUSTED";

  public static final String ZERO_NOT_COVERED = "ZERO_NOT_COVERED";

  private AdjustmentEngine() {}

  // ===================== 入参 / 出参 =====================

  /**
   * 理算请求。
   *
   * @param claimId 赔案 ID
   * @param rows 费用明细（读侧投影，三段拆分已就绪）
   * @param rule 责任规则（V1 十一维）
   * @param accumulation 跨案累计（⑩：必填，无"默认首案"重载）
   * @param ruleVersion 规则版本（写进快照）
   * @param historicalAccumulated 按费用类型的历史累计（比例区间因子用；缺失视为 0）
   * @param invoiceTotal 发票总费用（"发票总费用分段区间"因子用；缺失按明细合计）
   * @param hospitalLevel 医院等级（比例因子）
   * @param diseaseType 疾病类型（比例因子）
   * @param daysSinceEffective 出险日距保险生效日的天数（等待期判定；{@code null} = 不判）
   * @param extraLayers 额外额度层（共保/险种/计划；可选）
   * @param claimDate 赔案受理日（日限额「按同日赔案累计」的窗口来源；{@code null} = 该口径不可用， 配置了该口径即 fail-closed）
   */
  public record Request(
      Long claimId,
      List<ClaimWithItemsProjection> rows,
      LiabilityRule rule,
      Accumulation accumulation,
      String ruleVersion,
      Map<FeePools.Bucket, BigDecimal> historicalAccumulated,
      BigDecimal invoiceTotal,
      String hospitalLevel,
      String diseaseType,
      Integer daysSinceEffective,
      List<QuotaStack.LayerQuota> extraLayers,
      java.time.LocalDate claimDate) {

    public Request {
      rows = rows == null ? List.of() : List.copyOf(rows);
      historicalAccumulated =
          historicalAccumulated == null ? Map.of() : Map.copyOf(historicalAccumulated);
      extraLayers = extraLayers == null ? List.of() : List.copyOf(extraLayers);
      ruleVersion = ruleVersion == null ? "" : ruleVersion;
    }

    /** 最小请求：只给金额计算必需的入参。 */
    public static Request of(
        Long claimId, List<ClaimWithItemsProjection> rows, LiabilityRule rule, Accumulation acc) {
      return new Request(
          claimId, rows, rule, acc, "", Map.of(), null, null, null, null, List.of(), null);
    }

    public Request withRuleVersion(String version) {
      return new Request(
          claimId,
          rows,
          rule,
          accumulation,
          version,
          historicalAccumulated,
          invoiceTotal,
          hospitalLevel,
          diseaseType,
          daysSinceEffective,
          extraLayers,
          claimDate);
    }

    public Request withExtraLayers(List<QuotaStack.LayerQuota> layers) {
      return new Request(
          claimId,
          rows,
          rule,
          accumulation,
          ruleVersion,
          historicalAccumulated,
          invoiceTotal,
          hospitalLevel,
          diseaseType,
          daysSinceEffective,
          layers,
          claimDate);
    }

    /** 附赔案受理日（日限额「按同日赔案累计」口径的窗口）。 */
    public Request withClaimDate(java.time.LocalDate date) {
      return new Request(
          claimId,
          rows,
          rule,
          accumulation,
          ruleVersion,
          historicalAccumulated,
          invoiceTotal,
          hospitalLevel,
          diseaseType,
          daysSinceEffective,
          extraLayers,
          date);
    }
  }

  /** 一条归因明细（对应 {@code t_adj_calculation_line} 的六个成分金额）。 */
  public record Line(
      String bucket,
      String bucketLabel,
      String scope,
      BigDecimal feeCalculate,
      BigDecimal feeDeduct,
      BigDecimal ratioPayout,
      BigDecimal feeOccupied,
      BigDecimal feePayout,
      BigDecimal feeRemain,
      BigDecimal ratioPercent,
      String zeroReason,
      String rejectType,
      String cappedBy,
      Long itemId,
      String itemName) {}

  /** 理算结果。 */
  public record Outcome(
      BigDecimal totalAmount,
      List<Line> lines,
      BigDecimal deductibleApplied,
      boolean accumulationAvailable,
      String zeroReason,
      String rejectType,
      String snapshot,
      List<String> diagnostics) {

    public Outcome {
      lines = List.copyOf(lines);
      diagnostics = List.copyOf(diagnostics);
    }
  }

  // ===================== 主流程 =====================

  /**
   * 执行理算（九步主流程）。
   *
   * @throws RuleGap 前置校验不过 / 规则缺失 / 能力未定义（fail-closed，绝不出"猜出来的金额"）
   */
  public static Outcome calculate(Request request) {
    return calculate(request, null);
  }

  /**
   * 执行理算，并显式指定「次」的分组事实。
   *
   * <p><b>为什么要外部传分组事实</b>：次限额（§7.3）的分组维度是发票日期 / 医院 / 科室 / 病种，
   * 而当前明细投影没有这些列。若在此静默退化成"整案一次"，配置了"同日"的责任会少赔（见 {@link
   * OccurrenceGrouper#requireDimensions}）。故本方法要求调用方在有维度时显式给出： 给了就按它分组，<b>缺维度则拒绝</b>；不给（{@code
   * null}）才回落到"一赔案一次"。
   *
   * @param facts 与 {@code request.rows()} 下标一一对应的事实；{@code null} = 按赔案单次处理
   */
  public static Outcome calculate(Request request, List<OccurrenceGrouper.Fact> facts) {
    requireInputs(request);
    LiabilityRule rule = request.rule();
    rule.validate();
    checkPreconditions(request, rule);

    boolean inScopeOnly = rule.socialOnly();
    FeePools.Aggregation aggregation = FeePools.aggregateDetailed(request.rows(), inScopeOnly);
    FeePools pools = aggregation.pools();

    // 次限额分组（§7.3）：先分组，每组独立走一遍完整理算
    List<OccurrenceGrouper.Fact> groupingFacts = resolveFacts(request, facts);
    boolean singleInvoiceProvable = singleInvoiceProvable(request.rows());
    List<String> degradedDimensions =
        OccurrenceGrouper.requireDimensions(
            rule.occurrences().basis(), groupingFacts, singleInvoiceProvable);
    List<OccurrenceGrouper.Occurrence> occurrences =
        OccurrenceGrouper.group(rule.occurrences().basis(), groupingFacts);

    List<String> diagnostics = new ArrayList<>();
    for (String degraded : degradedDimensions) {
      // 降级必须可见：否则"证据没回填"与"规则如此"在报表上长得一模一样。
      diagnostics.add("次分组降级：" + degraded);
    }
    if (occurrences.size() > 1) {
      diagnostics.add("次限额分组：%d 组（%s）".formatted(occurrences.size(), rule.occurrences().basis()));
    }
    // 费用类型级免赔 × 多「次」：V1 §6.7 只规定了"各费用类型各自扣"，没有规定组间是否共享各类型额度（Q-04）。
    // 按"每组重新拿满额"实现等于放大免赔、按"跨组递减"实现等于缩小——两种都是猜，故拒绝。
    if (occurrences.size() > 1
        && rule.deductible().granularity() == DeductibleRule.Granularity.FEE_TYPE) {
      throw RuleGap.of(
          RuleGap.Code.DEDUCTIBLE_CONFIG_INVALID,
          "费用类型级免赔出现 %d 个「次」分组：各费用类型的免赔额度是否跨组共享未定义（Q-04），" + "按任一口径实现都会系统性偏差，须业务先定口径",
          occurrences.size());
    }
    if (Money.isZero(aggregation.excludedOutOfScope()) == false) {
      diagnostics.add("socialOnly 剔除范围外合理金额 " + Money.round(aggregation.excludedOutOfScope()));
    }
    // 不可获利护栏（§3.4 / D6）：REASONABLE 池已在 FeePools.aggregate 内净扣统筹+第三方已付，
    // 此处只核对"已付不得超合理"这一硬约束，并对净扣动作落诊断（保证运维可见、可审计）。
    checkSettlementConsistency(request, pools, diagnostics);

    // 本次理算的额度消耗（多层跨组共享：同一次理算内，前一组用掉的额度后一组不能用）
    DailyGuard daily = resolveDaily(request, rule, diagnostics);
    MutableQuotaPool quotaPool =
        new MutableQuotaPool(
            rule, request.accumulation(), request.extraLayers(), pools, daily.remaining());
    // 免赔账本：年度免赔在多「次」之间共享余额（按次免赔每组独立，由 resolveRemaining 决定）
    DeductibleLedger deductibleLedger =
        new DeductibleLedger(rule.deductible(), request.accumulation().deductibleUsed());

    BigDecimal total = Money.ZERO;
    BigDecimal deductibleApplied = Money.ZERO;
    BigDecimal ratioPayoutTotal = Money.ZERO;
    BigDecimal afterDeductTotal = Money.ZERO;
    BigDecimal invoiceDeductTotal = Money.ZERO;
    BigDecimal adjustDeductTotal = Money.ZERO;
    BigDecimal afterAdjustTotal = Money.ZERO;
    BigDecimal baseTotal = Money.ZERO;
    BigDecimal occupiedTotal = Money.ZERO;
    BigDecimal remainTotal = Money.ZERO;
    List<Line> lines = new ArrayList<>();
    List<String> ratioHits = new ArrayList<>();
    List<String> deductibleBranches = new ArrayList<>();
    List<String> quotaTrace = new ArrayList<>();

    for (OccurrenceGrouper.Occurrence occurrence : occurrences) {
      FeePools groupPools = subset(pools, occurrence.memberIndexes(), request.rows(), inScopeOnly);
      GroupResult group =
          computeGroup(
              groupPools,
              rule,
              request,
              quotaPool,
              deductibleLedger,
              diagnostics,
              ratioHits,
              deductibleBranches,
              quotaTrace);
      total = total.add(group.payable());
      deductibleApplied = deductibleApplied.add(group.deductibleApplied());
      ratioPayoutTotal = ratioPayoutTotal.add(group.ratioPayout());
      afterDeductTotal = afterDeductTotal.add(group.afterDeduct());
      invoiceDeductTotal = invoiceDeductTotal.add(group.invoiceDeductApplied());
      adjustDeductTotal = adjustDeductTotal.add(group.adjustDeductApplied());
      afterAdjustTotal = afterAdjustTotal.add(group.afterAdjust());
      baseTotal = baseTotal.add(group.base());
      occupiedTotal = occupiedTotal.add(group.occupied());
      remainTotal = remainTotal.add(group.remaining());
      lines.addAll(group.lines());
    }

    String zeroReason = "";
    if (total.signum() == 0) {
      // 零赔付归因以"扣完所有免赔层（发票+年度+理算）之后"的金额为基准，
      // 两层新增免赔把金额扣到 0 时归因为 ZERO_DEDUCT，与年度免赔口径一致。
      zeroReason = attributeZero(baseTotal, afterAdjustTotal, ratioPayoutTotal, rule, lines);
    }

    BigDecimal finalTotal = Money.round(total);
    Outcome outcome =
        new Outcome(
            finalTotal,
            lines,
            Money.round(deductibleApplied),
            request.accumulation().available(),
            zeroReason,
            rejectionCodeOf(zeroReason),
            buildSnapshot(
                request,
                rule,
                pools,
                baseTotal,
                ratioPayoutTotal,
                afterDeductTotal,
                occupiedTotal,
                remainTotal,
                deductibleApplied,
                invoiceDeductTotal,
                adjustDeductTotal,
                finalTotal,
                zeroReason,
                ratioHits,
                deductibleBranches,
                quotaTrace,
                lines,
                diagnostics,
                groupingJson(
                    rule.occurrences(),
                    occurrences.size(),
                    degradedDimensions,
                    singleInvoiceProvable),
                daily),
            diagnostics);
    InvariantGuard.verify(outcome, request, pools, occurrences.size());
    return outcome;
  }

  // ===================== 日限额（Q-05） =====================

  /**
   * 日限额的执行状态。
   *
   * @param window 本案的业务日（日窗口）；未启用日限额时为 {@code null}
   * @param remaining 当日剩余可赔额（{@code null} = 未启用日限额，不参与截断）
   */
  private record DailyGuard(java.time.LocalDate window, BigDecimal remaining) {

    static final DailyGuard INACTIVE = new DailyGuard(null, null);
  }

  /**
   * 解析日窗口并折算"当日剩余可赔额"（V1 §7.4 / Q-05）。
   *
   * <p><b>为什么要校验账本窗口与本案业务日一致</b>：日用量是"某一天"的量，拿错天的用量算出来的剩余额，看着完全正常， 方向却是错的——账本给的是更晚的一天 ⇒
   * 本案当天的额度被当成"已用满"（少赔到 0）；给的是更早的一天 ⇒ 当成"没赔过"（超赔）。 读侧按 {@code biz_date}
   * 分日聚合时两者必然相等，故这里是一道<b>接线护栏</b>： 对不上就说明有人把口径串了，此时宁可拒绝，也不拿别天的数算这天。
   *
   * <p>三种情形：
   *
   * <ol>
   *   <li>相同 ⇒ 直接用当日已用；
   *   <li>账本窗口<b>更早</b> ⇒ 账本里没有本案这一天的占用，已用 = 0（昨日赔完、今日重新起算的正常形态）；
   *   <li>账本窗口<b>更晚</b> ⇒ 口径串了（拿别天的用量算这天），fail-closed。
   * </ol>
   *
   * <p>账实不可读（{@code accumulation.available=false}）时与年度/责任额度同口径：只标记不阻断——理算结果还要给人看， 而 A7
   * 结算会因同一标记拒绝占额，真正的资金动作不会带着未知账本记账。
   */
  private static DailyGuard resolveDaily(
      Request request, LiabilityRule rule, List<String> diagnostics) {
    LiabilityRule.LimitSet limits = rule.limits();
    if (!limits.dailyActive()) {
      return DailyGuard.INACTIVE;
    }
    java.time.LocalDate window = dailyWindowOf(request, limits.dailyBasis());
    Accumulation accumulation = request.accumulation();
    BigDecimal used;
    if (!accumulation.available()) {
      used = Money.ZERO;
      diagnostics.add("日限额账本不可读：按「当日未占用」计算，结算前须复核（A7 会 fail-closed）");
    } else {
      used = dailyUsedOn(accumulation, window);
    }
    BigDecimal remaining = Money.nonNegative(limits.dailyLimit().subtract(used));
    diagnostics.add(
        "日限额 %s（%s，窗口 %s 已用 %s，剩余 %s）"
            .formatted(
                Money.round(limits.dailyLimit()),
                limits.dailyBasis(),
                window,
                Money.round(used),
                Money.round(remaining)));
    return new DailyGuard(window, remaining);
  }

  /** 本案业务日：按同日发票取发票日期，按同日赔案取赔案受理日。判不出来即拒绝，不替客户选一天。 */
  private static java.time.LocalDate dailyWindowOf(
      Request request, LiabilityRule.DailyBasis basis) {
    if (basis == LiabilityRule.DailyBasis.SAME_DAY_INVOICE) {
      java.time.LocalDate day = null;
      for (ClaimWithItemsProjection row : request.rows()) {
        if (row == null || row.getItemId() == null || row.getInvoiceDate() == null) {
          continue;
        }
        if (day == null) {
          day = row.getInvoiceDate();
        } else if (!day.equals(row.getInvoiceDate())) {
          throw RuleGap.of(
              RuleGap.Code.DAILY_LIMIT_WINDOW_UNDETERMINED,
              "日限额按「同日发票累计」，但本案明细跨 %s / %s 两个发票日期：一天只有一个日窗口，" + "把两天的票并成一张会少赔、按其中一天算会超赔，须拆案",
              day,
              row.getInvoiceDate());
        }
      }
      if (day == null) {
        throw RuleGap.of(
            RuleGap.Code.DAILY_LIMIT_WINDOW_UNDETERMINED,
            "日限额按「同日发票累计」，但全部 %d 条明细都缺发票日期：日窗口判不出来，" + "按任意一天算都会把别天的赔付算进来（或漏掉），须先补发票日期",
            request.rows().size());
      }
      return day;
    }
    if (request.claimDate() == null) {
      throw RuleGap.of(
          RuleGap.Code.DAILY_LIMIT_WINDOW_UNDETERMINED,
          "日限额按「同日赔案累计」，但本次理算未提供赔案受理日：日窗口判不出来，不能按发票日期顶替" + "（补赔场景下两者不同一天）");
    }
    return request.claimDate();
  }

  /** 账本上"本案业务日"的已用额（三态判定见 {@link #resolveDaily}）。 */
  private static BigDecimal dailyUsedOn(Accumulation accumulation, java.time.LocalDate window) {
    java.time.LocalDate ledgerWindow = accumulation.dailyWindow();
    if (ledgerWindow == null) {
      if (accumulation.dailyUsed().signum() > 0) {
        throw RuleGap.of(
            RuleGap.Code.DAILY_LIMIT_WINDOW_UNDETERMINED,
            "按日账本记了已用 %s 却没有窗口日：这份账无法归到任何一天，不能当作本案当日已用",
            Money.round(accumulation.dailyUsed()));
      }
      return Money.ZERO;
    }
    int compare = ledgerWindow.compareTo(window);
    if (compare == 0) {
      return accumulation.dailyUsed();
    }
    if (compare < 0) {
      return Money.ZERO;
    }
    throw RuleGap.of(
        RuleGap.Code.DAILY_LIMIT_WINDOW_STALE,
        "按日账本给出的窗口是 %s，晚于本案业务日 %s：拿别天的用量算这天要么少赔到 0、要么当没赔过，" + "须核对日窗口口径（daily_limit_basis）与读侧取数",
        ledgerWindow,
        window);
  }

  // ===================== 单组计算 =====================

  private record GroupResult(
      BigDecimal payable,
      BigDecimal deductibleApplied,
      BigDecimal ratioPayout,
      BigDecimal afterDeduct,
      BigDecimal base,
      BigDecimal occupied,
      BigDecimal remaining,
      List<Line> lines,
      BigDecimal invoiceDeductApplied,
      BigDecimal adjustDeductApplied,
      BigDecimal afterAdjust) {}

  /**
   * 一组（一次）的完整理算：比例 → 免赔 → 控额。
   *
   * <p>顺序固定，理由见类注释。
   */
  private static GroupResult computeGroup(
      FeePools pools,
      LiabilityRule rule,
      Request request,
      MutableQuotaPool quotaPool,
      DeductibleLedger deductibleLedger,
      List<String> diagnostics,
      List<String> ratioHits,
      List<String> deductibleBranches,
      List<String> quotaTrace) {
    boolean inScopeOnly = rule.socialOnly();

    // ① 逐池取基数与比例（多维查表；未命中即 fail-closed）
    List<Cell> cells = new ArrayList<>();
    for (FeePools.Pool pool : pools.pools()) {
      if (!rule.covers(pool.bucket())) {
        if (pool.total().signum() > 0) {
          diagnostics.add("责任不承担费用类型 " + pool.label() + "，金额 " + pool.total() + " 不参与理算");
        }
        continue;
      }
      BigDecimal base = pool.base(inScopeOnly);
      if (base.signum() == 0) {
        // 零金额的池不查表：没有金额可乘，"该池缺格"不等于配置缺失。
        // 若此处仍查表，一张只有合理金额的发票会因为"总自费没配比例"被整案拒绝——把缺省当缺陷。
        cells.add(
            new Cell(
                pool.bucket(),
                pool.label(),
                Money.ZERO,
                pool.inScope(),
                Money.ZERO,
                Money.ZERO,
                null));
        continue;
      }
      PayRatioTable.Resolution resolution = resolve(rule, pool, request);
      ratioHits.add(
          pool.bucket()
              + "="
              + resolution.percent().stripTrailingZeros().toPlainString()
              + "%"
              + (resolution.label().isBlank() ? "" : "(" + resolution.label() + ")"));
      cells.add(
          new Cell(
              pool.bucket(),
              pool.label(),
              base,
              base.signum() > 0 ? pool.inScope() : pool.inScope(),
              Money.percentToRate(resolution.percent()),
              resolution.percent(),
              resolution.hasFeeLimit() ? resolution.feeLimit() : null));
      if (Money.isRateOverflow(resolution.percent())) {
        diagnostics.add(
            "费用类型 " + pool.label() + " 配置比例 " + resolution.percent() + "% 超 100%，已钳制（配置错误，须复核）");
      }
    }

    BigDecimal baseTotal = Money.ZERO;
    for (Cell cell : cells) {
      baseTotal = baseTotal.add(cell.base);
    }

    // ② 发票级免赔（GAP②）：作用于比例之前、年度免赔之前，按各池基数占比分摊。无配置则 no-op。
    BigDecimal invoiceDeductApplied =
        applyInvoiceDeductible(cells, rule.invoiceDeductible(), deductibleBranches);

    // ④ 年度免赔（按 target 决定运算顺序）；now 作用在已扣发票级免赔的有效基数上。
    BigDecimal deductibleApplied =
        applyDeductible(cells, rule, deductibleLedger, deductibleBranches);

    BigDecimal ratioPayout = Money.ZERO;
    BigDecimal afterDeduct = Money.ZERO;
    for (Cell cell : cells) {
      ratioPayout = ratioPayout.add(cell.ratioPayout);
      afterDeduct = afterDeduct.add(cell.payable);
    }

    // ⑥ 理算级免赔（GAP⑥）：作用于比例之后、控额之前，对理算金额按占比分摊。无配置则 no-op。
    BigDecimal adjustDeductApplied =
        applyAdjustDeductible(cells, rule.adjustDeductible(), deductibleBranches);

    BigDecimal afterAdjust = Money.ZERO;
    for (Cell cell : cells) {
      afterAdjust = afterAdjust.add(cell.payable);
    }

    // ③ 控额（分池截断 → 责任 → 共保 → 险种 → 计划）
    QuotaStack.Result capped = quotaPool.cap(cells, rule, quotaTrace);

    BigDecimal occupied = Money.ZERO;
    BigDecimal remaining = Money.ZERO;
    List<Line> lines = new ArrayList<>();
    // 零基数池是否出行：仅当整组都为 0 时才出（否则归因明细为空，测试与前端都读不到"为什么没赔"）；
    // 组内有非零池时，零池不单独出行——避免一张票拆出 5 行 0 元噪声。
    boolean allZero = cells.stream().allMatch(cell -> cell.base.signum() == 0);
    for (Cell cell : cells) {
      if (!allZero && cell.base.signum() == 0 && cell.payout.signum() == 0) {
        continue;
      }
      occupied = occupied.add(cell.occupied);
      remaining = remaining.add(cell.remaining);
      String cellZero = cell.payout.signum() == 0 ? cellZeroReason(cell) : "";
      // 逐行分位归因（GAP⑨，oracle S27）：把池级最终赔付按各费用项在池内基数占比下钻到费用项明细行，
      // Σ 各行 feePayout ≡ 池 feePayout（I1 守恒）；无正贡献（理论不出现）时退回池级单行兜底。
      lines.addAll(attributeToItems(pools, cell, cellZero));
    }
    return new GroupResult(
        capped.payable(),
        deductibleApplied,
        ratioPayout,
        afterDeduct,
        baseTotal,
        occupied,
        remaining,
        lines,
        invoiceDeductApplied,
        adjustDeductApplied,
        afterAdjust);
  }

  /**
   * 逐行分位归因（GAP⑨，对齐 oracle S27 的"分位守恒"口径）。
   *
   * <p><b>为什么必须逐费用项出行</b>：池级单行只能回答"这张票的合理金额赔了多少"，回答不了"哪一笔费用赔了多少"。
   * 客户对账、拒赔说明、监管抽样都要求明细可加回合计——这正是"明细可加回合计"这条硬口径的来源。
   *
   * <p><b>分摊口径</b>：以各费用项在该池内的基数贡献为权重，用 {@link Money#proRata} 拆分池级各成分金额， 保证 <b>Σ 各费用项 ≡ 池</b>（I1
   * 不破）。权重只取正贡献项：{@code socialOnly} 下被剔除的范围外金额权重为 0， 不单独出行（出行反而是"这条被赔了 0"的噪声，其不赔原因已由池级 {@code
   * socialOnly} 诊断说明）。
   *
   * <p><b>剩余额度不外摊</b>：{@code cell.remaining} 是"组级"口径（同一分组内各池取同一个责任剩余额），
   * 不是可按费用项分割的金额；摊到每行会让汇总口径随明细条数漂移，故整额记在本池首行。
   */
  private static List<Line> attributeToItems(FeePools pools, Cell cell, String zeroReason) {
    String rejectType = zeroReason.isBlank() ? "" : rejectionCodeOf(zeroReason);
    List<FeePools.ItemContribution> items =
        pools.contributions().stream()
            .filter(item -> item.bucket() == cell.bucket && item.amount().signum() > 0)
            .toList();
    if (items.isEmpty()) {
      // 零基数池（或自定义费用类型无费用项映射）：没有权重可分，退回池级单行，
      // 否则"为什么不赔"的归因行会凭空消失。
      return List.of(poolLine(cell, zeroReason, rejectType));
    }
    List<BigDecimal> weights = items.stream().map(FeePools.ItemContribution::amount).toList();
    List<BigDecimal> bases = Money.proRata(cell.base, weights);
    List<BigDecimal> deducted = Money.proRata(cell.deducted, weights);
    List<BigDecimal> ratioPayouts = Money.proRata(cell.ratioPayout, weights);
    List<BigDecimal> occupied = Money.proRata(cell.occupied, weights);
    List<BigDecimal> payouts = Money.proRata(cell.payout, weights);
    List<Line> lines = new ArrayList<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      FeePools.ItemContribution item = items.get(i);
      lines.add(
          new Line(
              cell.bucket.name(),
              cell.label,
              item.inScope() ? "IN" : "OUT",
              bases.get(i),
              deducted.get(i),
              ratioPayouts.get(i),
              occupied.get(i),
              payouts.get(i),
              i == 0 ? Money.round(cell.remaining) : Money.ZERO,
              cell.ratioPercent,
              zeroReason,
              rejectType,
              cell.cappedBy,
              item.itemId(),
              item.itemName()));
    }
    return lines;
  }

  /** 池级单行（无费用项可分摊时的兜底，{@code itemId} 留空）。 */
  private static Line poolLine(Cell cell, String zeroReason, String rejectType) {
    return new Line(
        cell.bucket.name(),
        cell.label,
        cell.inScope.signum() > 0 ? "IN" : "OUT",
        Money.round(cell.base),
        Money.round(cell.deducted),
        Money.round(cell.ratioPayout),
        Money.round(cell.occupied),
        Money.round(cell.payout),
        Money.round(cell.remaining),
        cell.ratioPercent,
        zeroReason,
        rejectType,
        cell.cappedBy,
        null,
        null);
  }

  /** 比例查表（累计区间因子 = 历史累计 + 本池当前金额，§5.4）。 */
  private static PayRatioTable.Resolution resolve(
      LiabilityRule rule, FeePools.Pool pool, Request request) {
    BigDecimal historical = Money.nz(request.historicalAccumulated().get(pool.bucket()));
    BigDecimal accumulated = PayRatioTable.accumulatedOf(historical, pool.total());
    BigDecimal invoiceTotal =
        request.invoiceTotal() != null
            ? request.invoiceTotal()
            : Money.sum(
                request.rows().stream()
                    .filter(row -> row != null && row.getItemId() != null)
                    .map(row -> Money.nz(row.getItemAmount()))
                    .toList());
    PayRatioTable.RatioQuery query =
        new PayRatioTable.RatioQuery(
            pool.bucket(),
            null,
            accumulated,
            invoiceTotal,
            request.hospitalLevel(),
            request.diseaseType());
    return rule.ratios().lookup(query);
  }

  // ===================== 免赔 =====================

  /**
   * 执行免赔（V1 §6）。
   *
   * <p>两条分支的差别只在<b>顺序</b>，但结果不同，故此处按 {@link DeductibleRule.Target} 分开写：
   *
   * <ul>
   *   <li>{@code CALCULATED_AMOUNT}：先算 {@code base × ratio}，再减剩余免赔；
   *   <li>{@code INVOICE_AMOUNT}：先减剩余免赔得到"免赔后基数"，再乘比例。
   * </ul>
   *
   * <p><b>剩余免赔取自账本而不是跨案累计</b>：一次理算可能包含多个「次」，年度免赔是全案共享的一本账
   * ——每组各自重读跨案累计，等于第二组又拿到一份满额免赔，多扣的钱会直接变成客户少赔。
   *
   * @return 本次实际消抵的免赔额
   */
  private static BigDecimal applyDeductible(
      List<Cell> cells, LiabilityRule rule, DeductibleLedger ledger, List<String> branches) {
    DeductibleRule deductible = rule.deductible();
    if (deductible.inactive()) {
      for (Cell cell : cells) {
        // 即使年度免赔未启用，发票级免赔（GAP②）仍可能已扣减基数，故比例须基于有效基数。
        cell.ratioPayout = Money.round(Money.calc(cell.effectiveBase().multiply(cell.rate)));
        cell.payable = cell.ratioPayout;
        cell.deducted = Money.ZERO;
      }
      branches.add("NOT_APPLIED");
      return Money.ZERO;
    }
    BigDecimal applied = deductibleForGroup(cells, deductible, ledger.remaining(), branches);
    ledger.consume(applied);
    return applied;
  }

  /** 单组免赔（运算顺序 + 粒度）。{@code remaining} 由账本给出，本方法不自行解析跨案累计。 */
  private static BigDecimal deductibleForGroup(
      List<Cell> cells, DeductibleRule deductible, BigDecimal remaining, List<String> branches) {
    if (deductible.target() == DeductibleRule.Target.CALCULATED_AMOUNT) {
      // 先比例（基数已扣过发票级免赔 effectiveBase）
      for (Cell cell : cells) {
        cell.ratioPayout = Money.round(Money.calc(cell.effectiveBase().multiply(cell.rate)));
        cell.payable = cell.ratioPayout;
      }
      return deductibleGranularity(cells, deductible, remaining, branches);
    }

    // 先扣免赔（对象 = 发票费用），再比例
    BigDecimal deductibleApplied;
    if (deductible.granularity() == DeductibleRule.Granularity.LIABILITY) {
      BigDecimal totalBase = sumEffectiveBase(cells);
      DeductibleEngine.Outcome outcome = DeductibleEngine.apply(deductible, remaining, totalBase);
      branches.add(outcome.branch().name());
      distributeBase(cells, outcome.payable());
      deductibleApplied = outcome.deducted();
    } else {
      deductibleApplied = Money.ZERO;
      for (Map.Entry<FeePools.Bucket, List<Cell>> entry : byBucket(cells).entrySet()) {
        BigDecimal bucketBase = sumEffectiveBase(entry.getValue());
        DeductibleEngine.Outcome outcome =
            DeductibleEngine.apply(deductible, deductible.amountOf(entry.getKey()), bucketBase);
        branches.add(entry.getKey() + ":" + outcome.branch().name());
        distributeBase(entry.getValue(), outcome.payable());
        deductibleApplied = deductibleApplied.add(outcome.deducted());
      }
    }
    for (Cell cell : cells) {
      // 关键：乘比例的基数是**免赔后基数**（payableBase），不是原始基数。
      // 写成 cell.base 会让"先扣免赔再打折"退化成"只打折不扣免赔"——免赔配置静默失效。
      cell.ratioPayout = Money.round(Money.calc(cell.payableBase.multiply(cell.rate)));
      cell.payable = cell.ratioPayout;
      // 年度免赔扣减只记"有效基数 ⇒ 免赔后基数"之差，不含已扣的发票级免赔，避免两层免赔混记。
      cell.deducted = Money.round(cell.effectiveBase().subtract(cell.payableBase));
    }
    return Money.round(deductibleApplied);
  }

  /**
   * GAP② 发票级免赔：在<b>比例之前、年度免赔之前</b>，对各费用池基数按占比分摊一次绝对额扣减。
   *
   * <p>顺序铁律（oracle S 链）：发票级免赔必须先于年度免赔与比例。它扣的是"发票费用"本身， 与年度免赔（GAP④）是<b>两层独立免赔</b>——此处只改各池的 {@code
   * invoiceDeducted}， 由 {@link Cell#effectiveBase()} 把"已扣发票免赔的基数"透传给年度免赔，故两层不会重复计数。
   *
   * @return 本次实际消抵的发票级免赔额（不参与跨案累计，仅本赔案一次）
   */
  private static BigDecimal applyInvoiceDeductible(
      List<Cell> cells, DeductibleRule layer, List<String> branches) {
    if (layer == null || layer.inactive()) {
      return Money.ZERO;
    }
    BigDecimal amount = layer.amount();
    BigDecimal totalBase = sumEffectiveBase(cells);
    if (amount.signum() <= 0 || totalBase.signum() <= 0) {
      branches.add("INVOICE_DEDUCT_NOT_APPLIED");
      return Money.ZERO;
    }
    // 已扣额取 min(配置额, 总基数)：发票级免赔是"扣到 0 为止"，不会把基数扣成负。
    BigDecimal applied = Money.nonNegative(amount.min(totalBase));
    List<BigDecimal> weights = cells.stream().map(cell -> cell.base).toList();
    // 剩余基数按比例回分，保证 Σ 各池剩余基数 ≡ 原基数 − applied（I1 守恒、归因可追溯）。
    List<BigDecimal> shares = Money.proRata(totalBase.subtract(applied), weights);
    for (int i = 0; i < cells.size(); i++) {
      cells.get(i).invoiceDeducted = Money.nonNegative(cells.get(i).base.subtract(shares.get(i)));
    }
    branches.add("INVOICE_DEDUCT:" + Money.round(applied).toPlainString());
    return applied;
  }

  /**
   * GAP⑥ 理算级免赔：在<b>比例之后、控额之前</b>，对理算金额（各池 {@code payable}）按占比分摊一次绝对额扣减。
   *
   * <p>它是 oracle S 链里"按比例后"的第二个免赔层，与年度免赔（GAP④，作用于比例前/后由 target 决定）、
   * 发票级免赔（GAP②，作用于比例前）彼此独立。扣减后得到的金额再进入多维控额。
   *
   * @return 本次实际消抵的理算级免赔额（不参与跨案累计，仅本赔案一次）
   */
  private static BigDecimal applyAdjustDeductible(
      List<Cell> cells, DeductibleRule layer, List<String> branches) {
    if (layer == null || layer.inactive()) {
      return Money.ZERO;
    }
    BigDecimal amount = layer.amount();
    BigDecimal totalPayout = Money.ZERO;
    for (Cell cell : cells) {
      totalPayout = totalPayout.add(cell.payable);
    }
    if (amount.signum() <= 0 || totalPayout.signum() <= 0) {
      branches.add("ADJUST_DEDUCT_NOT_APPLIED");
      return Money.ZERO;
    }
    BigDecimal applied = Money.nonNegative(amount.min(totalPayout));
    List<BigDecimal> weights = cells.stream().map(cell -> cell.payable).toList();
    List<BigDecimal> shares = Money.proRata(totalPayout.subtract(applied), weights);
    for (int i = 0; i < cells.size(); i++) {
      cells.get(i).adjustDeducted = Money.nonNegative(cells.get(i).payable.subtract(shares.get(i)));
      cells.get(i).payable = shares.get(i);
    }
    branches.add("ADJUST_DEDUCT:" + Money.round(applied).toPlainString());
    return applied;
  }

  /** 免赔对象为"理算金额"时，按粒度扣减（此时 {@code cell.payable} 已是比例后金额）。 */
  private static BigDecimal deductibleGranularity(
      List<Cell> cells, DeductibleRule deductible, BigDecimal remaining, List<String> branches) {
    if (deductible.granularity() == DeductibleRule.Granularity.LIABILITY) {
      BigDecimal total = Money.ZERO;
      for (Cell cell : cells) {
        total = total.add(cell.payable);
      }
      DeductibleEngine.Outcome outcome = DeductibleEngine.apply(deductible, remaining, total);
      branches.add(outcome.branch().name());
      distributePayout(cells, outcome.payable(), outcome.deducted());
      return outcome.deducted();
    }
    BigDecimal applied = Money.ZERO;
    for (Map.Entry<FeePools.Bucket, List<Cell>> entry : byBucket(cells).entrySet()) {
      BigDecimal bucketPayout = Money.ZERO;
      for (Cell cell : entry.getValue()) {
        bucketPayout = bucketPayout.add(cell.payable);
      }
      DeductibleEngine.Outcome outcome =
          DeductibleEngine.apply(deductible, deductible.amountOf(entry.getKey()), bucketPayout);
      branches.add(entry.getKey() + ":" + outcome.branch().name());
      distributePayout(entry.getValue(), outcome.payable(), outcome.deducted());
      applied = applied.add(outcome.deducted());
    }
    return Money.round(applied);
  }

  private static void distributeBase(List<Cell> cells, BigDecimal totalPayableBase) {
    List<BigDecimal> weights = cells.stream().map(cell -> cell.base).toList();
    List<BigDecimal> shares = Money.proRata(totalPayableBase, weights);
    for (int i = 0; i < cells.size(); i++) {
      cells.get(i).payableBase = shares.get(i);
    }
  }

  private static void distributePayout(
      List<Cell> cells, BigDecimal totalPayable, BigDecimal totalDeducted) {
    List<BigDecimal> weights = cells.stream().map(cell -> cell.payable).toList();
    List<BigDecimal> shares = Money.proRata(totalPayable, weights);
    for (int i = 0; i < cells.size(); i++) {
      cells.get(i).payable = shares.get(i);
    }
    // 抵扣额按原比例回分（归因明细要能解释"这一池被扣了多少免赔"）
    List<BigDecimal> deductedShares = Money.proRata(totalDeducted, weights);
    for (int i = 0; i < cells.size(); i++) {
      cells.get(i).deducted = deductedShares.get(i);
    }
  }

  private static Map<FeePools.Bucket, List<Cell>> byBucket(List<Cell> cells) {
    Map<FeePools.Bucket, List<Cell>> map = new LinkedHashMap<>();
    for (Cell cell : cells) {
      map.computeIfAbsent(cell.bucket, ignored -> new ArrayList<>()).add(cell);
    }
    return map;
  }

  /** 各池<b>扣除发票级免赔后的</b>有效基数之和（GAP②）。年度免赔（GAP④）必须基于这一基数， 否则发票级免赔会被年度免赔重新叠加（双重计数 ⇒ 系统性多扣）。 */
  private static BigDecimal sumEffectiveBase(List<Cell> cells) {
    BigDecimal total = Money.ZERO;
    for (Cell cell : cells) {
      total = total.add(cell.effectiveBase());
    }
    return total;
  }

  // ===================== 额度池 =====================

  /**
   * 免赔账本：本次理算内多「次」分组的免赔余额。
   *
   * <p><b>为什么不每组各自 {@code resolveRemaining}</b>：年度免赔的累计键是"保单 + 主被 + 责任 + 年度"，
   * 与「次」无关。若每组都按跨案累计重算，第二组又会拿到一份满额免赔——多扣的部分直接变成客户少赔， 而且金额看着"更保守"，复核时几乎不会被发现。
   *
   * <p>{@code PER_CLAIM}（按次）不受账本影响：{@link DeductibleEngine#resolveRemaining} 对按次免赔
   * 每案给满额，账本只负责在组间递减，不改变其初始值。
   */
  private static final class DeductibleLedger {

    private final DeductibleRule rule;
    private BigDecimal annual;

    DeductibleLedger(DeductibleRule rule, BigDecimal accumulatedUsed) {
      this.rule = rule == null ? DeductibleRule.none() : rule;
      this.annual = DeductibleEngine.resolveRemaining(this.rule, accumulatedUsed);
    }

    /** 本组可用剩余免赔。按次免赔每组都拿满额（{@code resolveRemaining} 对 PER_CLAIM 直接返回配置额）。 */
    BigDecimal remaining() {
      return rule.basis() == DeductibleRule.Basis.PER_CLAIM ? configured() : annual;
    }

    /**
     * 记账：本组实际消抵额从年度余额中扣掉。
     *
     * <p>按次免赔不受影响——{@link #remaining()} 对 {@code PER_CLAIM} 每「次」都返回配置额， 账本对它是无操作（此处不再重复判一次
     * basis，避免两处判断在后续改动中分叉）。
     */
    void consume(BigDecimal applied) {
      BigDecimal used = Money.nz(applied);
      if (used.signum() <= 0) {
        return;
      }
      annual = Money.nonNegative(annual.subtract(used));
    }

    private BigDecimal configured() {
      return DeductibleEngine.resolveRemaining(rule, Money.ZERO);
    }
  }

  /**
   * 本次理算内的额度消耗池：责任/共保/险种/计划额度在<b>多个「次」分组间共享</b>。
   *
   * <p>为什么不每组各建一个池：次限额分组是"分别理算"，但责任额度是同一个。若每组都从满额开始截断， 分 3 组就会赔出 3 倍责任额度——这正是 §7.3〔缺〕里最容易踩的坑。
   */
  private static final class MutableQuotaPool {

    private final LiabilityRule rule;
    private BigDecimal liabilityRemaining;
    private final Map<QuotaStack.Layer, BigDecimal> layerRemaining =
        new EnumMap<>(QuotaStack.Layer.class);
    private final BigDecimal personalRemaining;

    /** 当日剩余可赔额（Q-05）：与责任额度一样是<b>同一次理算内跨「次」共享</b>的池—— 同一天分 3 组各从满额起算，等于把日限额放大 3 倍。 */
    private BigDecimal dailyRemaining;

    MutableQuotaPool(
        LiabilityRule rule,
        Accumulation accumulation,
        List<QuotaStack.LayerQuota> extraLayers,
        FeePools pools,
        BigDecimal dailyRemaining) {
      this.rule = rule;
      this.dailyRemaining = dailyRemaining;
      this.liabilityRemaining =
          rule.limits().liabilityUncapped()
              ? null
              : Money.nonNegative(
                  rule.limits().liabilityLimit().subtract(accumulation.limitUsed()));
      this.personalRemaining =
          rule.limits().personalLimit().signum() > 0 ? rule.limits().personalLimit() : null;
      for (QuotaStack.LayerQuota layer : extraLayers) {
        if (layer.layer() == QuotaStack.Layer.LIABILITY) {
          continue; // 责任层来自责任规则本身，避免两处配置打架
        }
        layerRemaining.put(layer.layer(), layer.available());
      }
      requireUpperLayersSourced(rule.limits(), layerRemaining);
      requirePersonalConsumable(rule);
    }

    /**
     * 险种 / 计划 / 共保额度的<b>来源</b>必须在装载期确定。
     *
     * <p>三层都是"跨案共享的池"，可用余额只能来自外部账本（如责任额度来自 {@code accumulation.limitUsed}）。
     * 早前实现把这三层当装饰字段：配了不报错、也不生效——超赔会持续到计划总额被穿透才在财务侧暴露。 现在只有两条路：由 {@code extraLayers}
     * 显式给出可用余额（并与配置额度交叉校验），或在装载期拒绝。
     */
    private static void requireUpperLayersSourced(
        LiabilityRule.LimitSet limits, Map<QuotaStack.Layer, BigDecimal> provided) {
      Map<QuotaStack.Layer, BigDecimal> declared = new LinkedHashMap<>();
      declared.put(QuotaStack.Layer.SHARED, limits.sharedLimit());
      declared.put(QuotaStack.Layer.PRODUCT, limits.productLimit());
      declared.put(QuotaStack.Layer.PLAN, limits.planLimit());
      for (Map.Entry<QuotaStack.Layer, BigDecimal> entry : declared.entrySet()) {
        BigDecimal configured = entry.getValue();
        if (configured.signum() <= 0) {
          continue;
        }
        BigDecimal available = provided.get(entry.getKey());
        if (available == null) {
          throw RuleGap.of(
              RuleGap.Code.LIMIT_LAYER_UNDEFINED,
              "%s 额度为 %s 但未提供可用余额：该层是跨案共享池，剩余额只能来自外部账本" + "（否则配了不生效 ⇒ 超赔）",
              entry.getKey(),
              configured.toPlainString());
        }
        if (available.compareTo(configured) > 0) {
          throw RuleGap.of(
              RuleGap.Code.LIMIT_LAYER_UNDEFINED,
              "%s 可用余额 %s 超过配置额度 %s：额度来源与责任配置不自洽，两个数必有一个错",
              entry.getKey(),
              available.toPlainString(),
              configured.toPlainString());
        }
      }
    }

    /** 个人额度只在扣减方式 4/5 有消耗路径；其它方式下配置它等于没配。 */
    private static void requirePersonalConsumable(LiabilityRule rule) {
      if (rule.limits().personalLimit().signum() > 0 && !rule.quota().mode().sequential()) {
        throw RuleGap.of(
            RuleGap.Code.LIMIT_LAYER_UNDEFINED,
            "个人额度 %s 配置在扣减方式 %s 下没有消耗路径（该方式只用责任额度）：配了不生效 ⇒ 超赔，请改用方式 4/5",
            rule.limits().personalLimit().toPlainString(),
            rule.quota().mode());
      }
    }

    /**
     * 控额：委托 {@link QuotaStack}（七层与五种扣减方式的<b>唯一</b>实现），本类只做两件外壳的事： 把"费用池"翻译成控额分组、把本次消耗回写到跨组共享的额度账本。
     *
     * <p><b>为什么不在这里再写一遍 min/pro-rata</b>：控额是资金正确性的第二现场，两处实现必然在某次改动后分叉， 而分叉只在"多组 / 多费用池 /
     * 多层额度同时生效"的案子上暴露——那正是最难复现的场景。
     */
    QuotaStack.Result cap(List<Cell> cells, LiabilityRule liabilityRule, List<String> trace) {
      List<QuotaStack.Group> groups = new ArrayList<>(cells.size());
      Map<String, BigDecimal> groupCaps = new LinkedHashMap<>();
      for (Cell cell : cells) {
        groups.add(new QuotaStack.Group(cell.bucket.name(), cell.payable));
        if (cell.feeLimit != null && cell.feeLimit.signum() > 0) {
          groupCaps.put(cell.bucket.name(), cell.feeLimit);
        }
      }

      List<QuotaStack.LayerQuota> layers = new ArrayList<>();
      // 顺序即截断顺序，按 V1 §7.2：③ 次限额（在 Demand 内预截断）→ ④ 共保 → ⑤ 责任 → ⑥ 险种 → ⑦ 计划
      for (QuotaStack.Layer layer : List.of(QuotaStack.Layer.SHARED)) {
        if (layerRemaining.containsKey(layer)) {
          layers.add(new QuotaStack.LayerQuota(layer, layer.name(), layerRemaining.get(layer)));
        }
      }
      boolean liabilityLayerUsed =
          rule.quota().mode() != QuotaStack.DeductionMode.PERSONAL_ONLY
              && liabilityRemaining != null;
      if (liabilityLayerUsed) {
        layers.add(
            new QuotaStack.LayerQuota(QuotaStack.Layer.LIABILITY, "LIABILITY", liabilityRemaining));
      }
      for (QuotaStack.Layer layer : List.of(QuotaStack.Layer.PRODUCT, QuotaStack.Layer.PLAN)) {
        if (layerRemaining.containsKey(layer)) {
          layers.add(new QuotaStack.LayerQuota(layer, layer.name(), layerRemaining.get(layer)));
        }
      }

      QuotaStack.Result result =
          QuotaStack.cap(
              new QuotaStack.Demand(
                  groups, groupCaps, personalRemaining, occurrenceCap(), dailyRemaining),
              layers,
              rule.quota().mode());

      BigDecimal totalPaid = result.payable();
      BigDecimal remainingAfter =
          liabilityRemaining == null
              ? Money.ZERO
              : Money.nonNegative(liabilityRemaining.subtract(totalPaid));
      for (int i = 0; i < cells.size(); i++) {
        Cell cell = cells.get(i);
        QuotaStack.GroupResult group = result.groups().get(i);
        cell.payable = group.payable();
        cell.payout = group.payable();
        cell.occupied = group.payable();
        cell.remaining = remainingAfter;
        // 控额归因必须逐组带回来：QuotaStack 已经算好"这一组被哪一维截断"，此处不接就等于白算——
        // 少赔的客户解释、双跑差异归因（MOU「差异可归因率 ≥95%」）、后督抽检都要这一维标识。
        cell.cappedBy = group.cappedBy();
      }
      // "本年度额度已用尽"与"本案单次超限"的解释不同，靠截断层可用额为 0 来区分
      for (QuotaStack.Step step : result.steps()) {
        trace.add(
            step.layer()
                + ":"
                + step.key()
                + " 可用 "
                + step.available()
                + "（申请 "
                + step.before()
                + "）");
        if (step.layer() == QuotaStack.Layer.LIABILITY && step.available().signum() == 0) {
          for (Cell cell : cells) {
            cell.quotaExhausted = true;
          }
        }
      }

      // 回写账本：同一次理算内的多个「次」分组共享同一额度池
      if (liabilityRemaining != null) {
        liabilityRemaining = Money.nonNegative(liabilityRemaining.subtract(totalPaid));
      }
      if (dailyRemaining != null) {
        dailyRemaining = Money.nonNegative(dailyRemaining.subtract(totalPaid));
      }
      for (QuotaStack.Layer layer : List.copyOf(layerRemaining.keySet())) {
        BigDecimal available = layerRemaining.get(layer);
        if (available != null) {
          layerRemaining.put(layer, Money.nonNegative(available.subtract(totalPaid)));
        }
      }
      return result;
    }

    /**
     * 本「次」的次限额（层③）。
     *
     * <p>每次分组都从<b>配置额度</b>起算，不进入跨组账本：次限额的语义就是"每一次各自受此限额约束"， 若像责任额度那样跨组递减，第二个「次」会一分不赔。
     */
    private BigDecimal occurrenceCap() {
      return rule.limits().hasOccurrenceLimit() ? rule.limits().occurrenceLimit() : null;
    }
  }

  // ===================== 归因 =====================

  private static String attributeZero(
      BigDecimal baseTotal,
      BigDecimal afterDeduct,
      BigDecimal ratioPayout,
      LiabilityRule rule,
      List<Line> lines) {
    if (ratioPayout.signum() == 0 && baseTotal.signum() > 0) {
      return ZERO_PERCENT;
    }
    if (afterDeduct.signum() == 0 && baseTotal.signum() > 0) {
      return ZERO_DEDUCT;
    }
    if (baseTotal.signum() == 0) {
      return ZERO_REASONABLE;
    }
    // 行级归因已经区分了"年度用尽"与"单次超限"，总额归因沿用同一事实（不另判一次，避免两处口径）
    if (!lines.isEmpty()
        && lines.stream().allMatch(line -> ZERO_DAILY_EXHAUSTED.equals(line.zeroReason()))) {
      // 日限额把整案压到 0：与"本年度额度用尽"分开报，客户第二天就能再赔
      return ZERO_DAILY_EXHAUSTED;
    }
    if (!lines.isEmpty()
        && lines.stream().allMatch(line -> ZERO_LIMIT_EXHAUSTED.equals(line.zeroReason()))) {
      return ZERO_LIMIT_EXHAUSTED;
    }
    return ZERO_LIMIT;
  }

  private static String cellZeroReason(Cell cell) {
    if (cell.payable.signum() > 0) {
      return "";
    }
    if (cell.base.signum() == 0) {
      return ZERO_REASONABLE;
    }
    // 免赔吃光必须排在"比例为 0"之前：免赔对象为发票费用时，基数被免赔扣到 0 会让比例后金额也是 0，
    // 若先判比例就会把"免赔扣光"误报成"合理费用为 0"，拒赔解释直接指向错误事实。
    if (cell.deducted.signum() > 0) {
      return ZERO_DEDUCT;
    }
    if (cell.rate.signum() == 0) {
      return ZERO_PERCENT;
    }
    if (cell.ratioPayout.signum() == 0) {
      return ZERO_REASONABLE;
    }
    // 日限额用满是"改日再赔"，与本年度额度用尽（今年没了）是两件事，拒赔解释必须分开：
    // 都报 ZERO_LIMIT_EXHAUSTED 会让客户以为这一年的保障已经用完，实际第二天就能赔。
    if (cell.cappedBy != null && cell.cappedBy.contains(QuotaStack.Layer.DAILY.name())) {
      return ZERO_DAILY_EXHAUSTED;
    }
    // 区分"本年度额度已用尽"与"本案单次超限"：两者对客户的解释完全不同（拒赔五要素的事实认定要求）
    return cell.quotaExhausted ? ZERO_LIMIT_EXHAUSTED : ZERO_LIMIT;
  }

  /** 零赔付归因 → 受控原因码（§8.3 字典映射）。 */
  private static String rejectionCodeOf(String zeroReason) {
    return switch (zeroReason) {
      case ZERO_DEDUCT -> "06";
      case ZERO_LIMIT, ZERO_LIMIT_EXHAUSTED, ZERO_DAILY_EXHAUSTED -> "09";
      case ZERO_PERCENT, ZERO_REASONABLE, ZERO_NOT_COVERED -> "05";
      default -> "";
    };
  }

  // ===================== 校验 =====================

  /**
   * 不可获利护栏（§3.4 / D6）。
   *
   * <p>引擎已在 {@code FeePools.aggregate} 内把"统筹+第三方已付"从可赔基数净扣（oracle S17 口径）， 故本方法不再假设 {@code
   * valid_amount} 已净额，而是直接守住两条硬约束：
   *
   * <ul>
   *   <li><b>不可获利（fail-closed）</b>：逐行 {@code 统筹 + 第三方已付 > 合理费用} ⇒ 已付超过实际损失， 不可能，疑似把已付又算进合理 ⇒ 拒绝（防
   *       D6 超赔）。这是原"负残差+三方⇒拒绝"的纠正： 原逻辑因假设 valid 已净额，凡是带第三方已付的案子一律拒绝，反而把本应赔付的案子挡在门外；
   *       现在引擎已净扣，只需拦截"已付超合理"这种真正不可能的数据。
   *   <li><b>净扣可见（诊断）</b>：存在已付即落诊断，保证运维在快照里看得到"这笔钱已被抵扣"，可审计。
   * </ul>
   */
  private static void checkSettlementConsistency(
      Request request, FeePools pools, List<String> diagnostics) {
    FeePools.Reconciliation reconciliation = pools.reconcile(request.rows());
    BigDecimal tolerance = new BigDecimal("0.01");

    for (ClaimWithItemsProjection row : request.rows()) {
      if (row == null || row.getItemId() == null) {
        continue;
      }
      BigDecimal valid = Money.nz(row.getItemValidAmount());
      BigDecimal priorPaid =
          Money.nz(row.getItemPoolingAmount()).add(Money.nz(row.getItemThirdPartyPaid()));

      // 不可获利硬约束：已付（统筹+第三方）不得超过合理费用，否则"已付又赔"必然超赔。
      if (priorPaid.compareTo(valid.add(tolerance)) > 0) {
        throw RuleGap.of(
            RuleGap.Code.INVOICE_SETTLEMENT_INCONSISTENT,
            "费用项「%s」的统筹+第三方已付 %s 超过合理费用 %s：已付超过实际损失，疑似重复计入，" + "须先核对票面拆分",
            row.getItemName() == null ? String.valueOf(row.getItemId()) : row.getItemName(),
            Money.round(priorPaid),
            Money.round(valid));
      }
    }

    if (reconciliation.priorPaid().signum() > 0) {
      diagnostics.add(
          "三方已赔付 "
              + Money.round(reconciliation.priorPaid())
              + "（统筹+商保）已从可赔基数扣除：赔付+已付 ≤ 总费用（不可获利原则）");
    }
  }

  private static void requireInputs(Request request) {
    if (request == null) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "理算请求为空");
    }
    if (request.rows().isEmpty()) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "理算明细不能为空");
    }
    if (request.rule() == null) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "责任规则缺失，无法理算");
    }
    if (request.accumulation() == null) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "跨案累计快照缺失（⑩：禁止按『已用 0』隐式计算）");
    }
    boolean hasItem =
        request.rows().stream().anyMatch(row -> row != null && row.getItemId() != null);
    if (!hasItem) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "理算明细全为赔案头行，无费用明细");
    }
  }

  /** 前置校验（§4.1）：不过即不进理算（不是"算成 0"）。 */
  private static void checkPreconditions(Request request, LiabilityRule rule) {
    if (request.daysSinceEffective() != null
        && rule.waitingPeriodDays() > 0
        && request.daysSinceEffective() < rule.waitingPeriodDays()) {
      throw RuleGap.of(
          RuleGap.Code.WAITING_PERIOD_NOT_MET,
          "出险距生效 %d 天，未过等待期 %d 天",
          request.daysSinceEffective(),
          rule.waitingPeriodDays());
    }
    if (rule.limitedConditions().enabled()) {
      throw RuleGap.of(
          RuleGap.Code.LIMITED_CONDITION_NOT_MET,
          "责任启用了适用限定（医院=%s / 病种=%s）：名单比对需要外部目录，V1 未接入",
          rule.limitedConditions().hospitalCode(),
          rule.limitedConditions().diseaseCode());
    }
  }

  /**
   * 分组事实：调用方给了就用它（并校验下标对齐），否则回落到"一赔案一次"。
   *
   * <p>下标必须与明细一一对应——分组结果返回的是下标，错位会把 A 票的日期配到 B 票的金额上， 算出来的每一组都"看起来合理"。故此处强校验而非截断。
   */
  private static List<OccurrenceGrouper.Fact> resolveFacts(
      Request request, List<OccurrenceGrouper.Fact> facts) {
    if (facts == null) {
      return factsOf(request.rows(), request.claimId());
    }
    if (facts.size() != request.rows().size()) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "分组事实 %d 条与明细 %d 条不一一对应：按下标分组会错位",
          facts.size(),
          request.rows().size());
    }
    return List.copyOf(facts);
  }

  private static List<OccurrenceGrouper.Fact> factsOf(
      List<ClaimWithItemsProjection> rows, Long claimId) {
    List<OccurrenceGrouper.Fact> facts = new ArrayList<>(rows.size());
    String fallbackClaimNo = claimId == null ? "" : String.valueOf(claimId);
    for (ClaimWithItemsProjection row : rows) {
      if (row == null) {
        facts.add(OccurrenceGrouper.Fact.ofClaim(fallbackClaimNo));
        continue;
      }
      // 分组维度一律来自投影的真实列，不再"按一赔案一次"糊过去：
      // 糊过去会让配了"同日/同科室"的责任少赔，且少赔得毫无痕迹。
      facts.add(
          new OccurrenceGrouper.Fact(
              row.getClaimNo() == null || row.getClaimNo().isBlank()
                  ? fallbackClaimNo
                  : row.getClaimNo(),
              row.getInvoiceDate(),
              row.getHospitalCode(),
              row.getItemDepartment(),
              row.getDiagnosisCode()));
    }
    return facts;
  }

  /**
   * 是否可证"本案只对应一张发票"：赔案有发票号即为真（{@code uk_tenant_invoice} 限定单一发票号）。
   *
   * <p>这是发票级维度缺失时<b>唯一</b>允许降级的依据。没有发票号时，"案内明细同属一张票"只是假设， 而按假设把多天多票并成一次会少赔，故必须拒绝。
   */
  private static boolean singleInvoiceProvable(List<ClaimWithItemsProjection> rows) {
    for (ClaimWithItemsProjection row : rows) {
      if (row != null && row.getInvoiceNo() != null && !row.getInvoiceNo().isBlank()) {
        return true;
      }
    }
    return false;
  }

  /** 分组事实的物化（快照用）：组数、维度、是否降级——复算与审计都要能看出"这一次是怎么分的"。 */
  private static String groupingJson(
      LiabilityRule.OccurrenceSpec spec,
      int groups,
      List<String> degradedDimensions,
      boolean singleInvoiceProvable) {
    StringBuilder sb = new StringBuilder("{\"basis\":\"");
    sb.append(spec.basis().name())
        .append("\",\"occurrenceLimit\":")
        .append(num(Money.round(spec.limit())))
        .append(",\"groups\":")
        .append(groups)
        .append(",\"singleInvoiceProvable\":")
        .append(singleInvoiceProvable)
        .append(",\"dimensions\":[");
    boolean first = true;
    for (OccurrenceGrouper.Dimension dimension : OccurrenceGrouper.dimensionsOf(spec.basis())) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"').append(dimension.name()).append('"');
    }
    sb.append("],");
    appendArray(sb, "degraded", degradedDimensions);
    sb.append('}');
    return sb.toString();
  }

  private static FeePools subset(
      FeePools pools,
      List<Integer> memberIndexes,
      List<ClaimWithItemsProjection> rows,
      boolean inScopeOnly) {
    List<ClaimWithItemsProjection> memberRows = new ArrayList<>(memberIndexes.size());
    for (Integer index : memberIndexes) {
      if (index >= 0 && index < rows.size()) {
        memberRows.add(rows.get(index));
      }
    }
    return FeePools.aggregate(memberRows, inScopeOnly);
  }

  // ===================== 快照 =====================

  private static String buildSnapshot(
      Request request,
      LiabilityRule rule,
      FeePools pools,
      BigDecimal base,
      BigDecimal ratioPayout,
      BigDecimal afterDeduct,
      BigDecimal occupied,
      BigDecimal remain,
      BigDecimal deductibleApplied,
      BigDecimal invoiceDeductApplied,
      BigDecimal adjustDeductApplied,
      BigDecimal total,
      String zeroReason,
      List<String> ratioHits,
      List<String> deductibleBranches,
      List<String> quotaTrace,
      List<Line> lines,
      List<String> diagnostics,
      String groupingJson,
      DailyGuard daily) {
    StringBuilder sb = new StringBuilder("{");
    append(sb, "liabilityUuid", rule.liabilityUuid());
    append(sb, "liabilityVersion", rule.liabilityVersion());
    // 同一比例形态时输出单一 payPercent（向后兼容旧快照读法）；多维形态输出 null + ratios
    boolean uniform = rule.ratios().uniformForm();
    sb.append("\"payPercent\":")
        .append(
            uniform && !rule.ratios().entries().isEmpty()
                ? rule.ratios().entries().get(0).percent().stripTrailingZeros().toPlainString()
                : "null")
        .append(',');
    append(sb, "ruleVersion", request.ruleVersion());
    sb.append("\"deduct\":").append(num(deductibleApplied)).append(',');
    // GAP② 发票级免赔、GAP⑥ 理算级免赔：与年度免赔（deduct）是三层独立免赔，分别物化，便于审计对账。
    sb.append("\"invoiceDeduct\":").append(num(Money.round(invoiceDeductApplied))).append(',');
    sb.append("\"adjustDeduct\":").append(num(Money.round(adjustDeductApplied))).append(',');
    append(sb, "deductibleKind", rule.deductible().kind().name());
    append(sb, "deductibleTarget", rule.deductible().target().name());
    append(sb, "deductibleGranularity", rule.deductible().granularity().name());
    append(sb, "deductibleBasis", rule.deductible().basis().name());
    sb.append("\"deductibleBefore\":")
        .append(num(Money.round(rule.deductible().amount())))
        .append(',');
    sb.append("\"deductibleUsedBefore\":")
        .append(num(Money.round(request.accumulation().deductibleUsed())))
        .append(',');
    sb.append("\"limit\":")
        .append(rule.limits().liabilityLimit().stripTrailingZeros().toPlainString())
        .append(',');
    sb.append("\"limitUsedBefore\":")
        .append(num(Money.round(request.accumulation().limitUsed())))
        .append(',');
    // Q-05 日赔付限额：金额、口径、窗口日、当日已用、当日剩余五项一起物化——
    // 只记"被日限额截断"而不记截断时的窗口与已用量，事后无法判断这笔少赔是当日额度真的用完了，还是取数取错了天。
    sb.append("\"dailyLimit\":")
        .append(rule.limits().dailyLimit().stripTrailingZeros().toPlainString())
        .append(',');
    append(sb, "dailyBasis", rule.limits().dailyBasis().name());
    sb.append("\"dailyUsedBefore\":")
        .append(num(Money.round(request.accumulation().dailyUsed())))
        .append(',');
    sb.append("\"dailyWindow\":")
        .append(
            request.accumulation().dailyWindow() == null
                ? "null"
                : "\"" + request.accumulation().dailyWindow() + "\"")
        .append(',');
    sb.append("\"dailyRemaining\":")
        .append(daily.remaining() == null ? "null" : num(Money.round(daily.remaining())))
        .append(',');
    sb.append("\"accumulationAvailable\":").append(request.accumulation().available()).append(',');
    sb.append("\"socialOnly\":").append(rule.socialOnly()).append(',');
    sb.append("\"base\":").append(num(Money.round(base))).append(',');
    sb.append("\"selfPay1Total\":")
        .append(num(Money.round(sumSelfPay1(request.rows()))))
        .append(',');
    sb.append("\"afterDeduct\":").append(num(Money.round(afterDeduct))).append(',');
    sb.append("\"ratioPayout\":").append(num(Money.round(ratioPayout))).append(',');
    sb.append("\"occupied\":").append(num(Money.round(occupied))).append(',');
    sb.append("\"feeRemain\":").append(num(Money.round(remain))).append(',');
    sb.append("\"total\":").append(num(Money.round(total))).append(',');
    append(sb, "zeroReason", zeroReason);
    sb.append("\"pools\":{");
    boolean firstPool = true;
    for (FeePools.Pool pool : pools.pools()) {
      if (!firstPool) {
        sb.append(',');
      }
      firstPool = false;
      sb.append('"')
          .append(pool.label())
          .append("\":{\"inScope\":")
          .append(num(Money.round(pool.inScope())))
          .append(",\"outOfScope\":")
          .append(num(Money.round(pool.outOfScope())))
          .append('}');
    }
    sb.append("},");
    appendArray(sb, "ratioHits", ratioHits);
    appendArray(sb, "deductibleBranches", deductibleBranches);
    appendArray(sb, "quotaSteps", quotaTrace);
    sb.append("\"grouping\":").append(groupingJson).append(',');
    // 诊断必须落库：这些提示（三方已赔未扣、比例超 100%、责任不承担的费用类型、次分组数）此前只存在于
    // 内存返回值里，聚合外壳从不读它 ⇒ 生产环境一条也看不到，等于没报。
    appendArray(sb, "diagnostics", diagnostics);
    // 快照里的 lines 保持**池级**口径（逐费用项明细落 t_adj_calculation_line，不在这里展开）：
    // 一者 formula_snapshot 是 VARCHAR(4000) 的复算快照，逐行展开会让一张多明细发票直接溢出；
    // 二者快照的语义是"复算所需的责任参数与池级归因"，逐行审计溯源由明细行表承担，两处各司其职。
    // 池级聚合保证既有场景的快照字节不变（formula_hash 因此保持稳定）。
    appendArray(
        sb, "lines", poolLevelLines(lines).stream().map(AdjustmentEngine::lineJson).toList());
    sb.append("\"items\":").append(itemsJson(request.rows()));
    sb.append('}');
    return sb.toString();
  }

  /**
   * 把逐费用项明细行按费用池合回一条（快照口径）。
   *
   * <p>与 {@link #attributeToItems} 互为逆运算：明细行的六个成分金额相加必须回到池级值， 这样"快照池级口径 +
   * 明细行逐项口径"两种读法对得上，复算与审计不会各说各话。
   */
  private static List<Line> poolLevelLines(List<Line> lines) {
    // 只合并**相邻**同池行：跨分组（次限额分组）的同名池必须各自成行，否则快照会把两组的
    // 赔付混成一条，复算时看不出"这一次为什么只赔这么多"。
    List<Line> merged = new ArrayList<>();
    for (Line line : lines) {
      int last = merged.size() - 1;
      if (last >= 0 && merged.get(last).bucket().equals(line.bucket())) {
        merged.set(last, mergeLine(merged.get(last), line));
      } else {
        merged.add(line);
      }
    }
    return List.copyOf(merged);
  }

  private static Line mergeLine(Line left, Line right) {
    return new Line(
        left.bucket(),
        left.bucketLabel(),
        left.scope(),
        left.feeCalculate().add(right.feeCalculate()),
        left.feeDeduct().add(right.feeDeduct()),
        left.ratioPayout().add(right.ratioPayout()),
        left.feeOccupied().add(right.feeOccupied()),
        left.feePayout().add(right.feePayout()),
        left.feeRemain().add(right.feeRemain()),
        left.ratioPercent(),
        left.zeroReason(),
        left.rejectType(),
        left.cappedBy(),
        null,
        null);
  }

  private static String lineJson(Line line) {
    return "{"
        + "\"bucket\":\""
        + line.bucket()
        + "\",\"calc\":"
        + num(line.feeCalculate())
        + ",\"deduct\":"
        + num(line.feeDeduct())
        + ",\"ratio\":"
        + num(line.ratioPayout())
        + ",\"payout\":"
        + num(line.feePayout())
        + ",\"zero\":\""
        + line.zeroReason()
        + "\",\"cappedBy\":\""
        + line.cappedBy()
        + "\"}";
  }

  private static String itemsJson(List<ClaimWithItemsProjection> rows) {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (ClaimWithItemsProjection row : rows) {
      if (row == null || row.getItemId() == null) {
        continue;
      }
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append("{\"item\":\"")
          .append(row.getItemName())
          .append("\",\"valid\":")
          .append(num(Money.round(Money.nz(row.getItemValidAmount()))))
          .append(",\"inScope\":")
          .append("IN".equalsIgnoreCase(row.getItemSocialFlag()))
          .append('}');
    }
    return sb.append(']').toString();
  }

  private static BigDecimal sumSelfPay1(List<ClaimWithItemsProjection> rows) {
    BigDecimal total = Money.ZERO;
    for (ClaimWithItemsProjection row : rows) {
      if (row != null && row.getItemId() != null) {
        total = total.add(Money.nz(row.getItemSelfPay1()));
      }
    }
    return total;
  }

  private static void append(StringBuilder sb, String key, String value) {
    sb.append('"').append(key).append("\":\"").append(value == null ? "" : value).append("\",");
  }

  private static void appendArray(StringBuilder sb, String key, List<String> values) {
    sb.append('"').append(key).append("\":[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('"').append(values.get(i)).append('"');
    }
    sb.append("],");
  }

  private static String num(BigDecimal value) {
    return Money.nz(value).stripTrailingZeros().toPlainString();
  }

  // ===================== 内部单元格 =====================

  /** 一个费用池的理算中间态（局部可变，不逃逸出引擎）。 */
  private static final class Cell {
    private final FeePools.Bucket bucket;
    private final String label;
    private final BigDecimal base;
    private final BigDecimal inScope;
    private final BigDecimal rate;
    private final BigDecimal ratioPercent;
    private final BigDecimal feeLimit;

    private BigDecimal payableBase;
    private BigDecimal ratioPayout = Money.ZERO;
    private BigDecimal payable = Money.ZERO;
    private BigDecimal deducted = Money.ZERO;
    private BigDecimal payout = Money.ZERO;
    private BigDecimal occupied = Money.ZERO;
    private BigDecimal remaining = Money.ZERO;

    /** 发票级免赔（GAP②）本池已扣减额（比例之前、年度免赔之前）。 */
    private BigDecimal invoiceDeducted = Money.ZERO;

    /** 理算级免赔（GAP⑥）本池已扣减额（比例之后、控额之前）。 */
    private BigDecimal adjustDeducted = Money.ZERO;

    /** 责任额度是否"本年度已用尽"（区别于"本案单次超限"，决定零赔付归因）。 */
    private boolean quotaExhausted;

    /**
     * 本池被哪一维额度截断（{@code ""} = 未被截断）。
     *
     * <p>取值形如 {@code LIABILITY} / {@code LIABILITY:<key>} / {@code FEE_TYPE:REASONABLE} / {@code
     * OCCURRENCE}， 由 {@link QuotaStack.GroupResult#cappedBy()} 逐组带回。没有它，"少赔"只能看到金额变小，
     * 说不清是责任额度、费用子限额还是次限额吃掉的——拒赔五要素里"事实认定"这一条就落不了地。
     */
    private String cappedBy = "";

    Cell(
        FeePools.Bucket bucket,
        String label,
        BigDecimal base,
        BigDecimal inScope,
        BigDecimal rate,
        BigDecimal ratioPercent,
        BigDecimal feeLimit) {
      this.bucket = bucket;
      this.label = label;
      this.base = base;
      this.inScope = inScope;
      this.rate = rate;
      this.ratioPercent = ratioPercent;
      this.feeLimit = feeLimit;
      this.payableBase = base;
    }

    /** 扣除发票级免赔后的<b>有效基数</b>：年度免赔（GAP④）必须作用在这一基数上， 否则发票级免赔会被年度免赔重新叠加上去（双重计数 ⇒ 系统性多扣）。 */
    BigDecimal effectiveBase() {
      return Money.nonNegative(base.subtract(invoiceDeducted));
    }
  }
}
