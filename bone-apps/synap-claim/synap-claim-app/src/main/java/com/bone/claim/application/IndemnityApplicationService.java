package com.bone.claim.application;

import com.bone.claim.application.catalog.ItemCatalogGateway;
import com.bone.claim.application.port.out.AccumulationPort;
import com.bone.claim.application.port.out.ItemCatalogPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.application.quota.QuotaYearResolver;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.adjustment.AdjustmentLine;
import com.bone.claim.domain.model.adjustment.CalculationVersions;
import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A6 理算应用服务（金额计算的编排入口，AI 不参与）。
 *
 * <p>一事务一聚合（CORE-06）：本服务每个方法只写 {@code Adjustment} 一个聚合—— 额度占额按 §5.3「理算只算不占额」归 A7 资金阶段（{@code
 * SettlementOrder} 创建时 SOFT/HARD），不在此处调用，规避"一事务改两个聚合"。
 */
@Service
@RequiredArgsConstructor
public class IndemnityApplicationService {

  private final AdjustmentRepository adjustmentRepository;
  private final LiabilityRepository liabilityRepository;
  private final com.bone.claim.domain.repository.LiabilityFeeRuleRepository
      liabilityFeeRuleRepository;
  private final EvidenceRepository evidenceRepository;
  private final DecisionExecutionRepository decisionExecutionRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final AccumulationPort accumulationPort;
  private final ItemCatalogGateway itemCatalogGateway;
  private final QuotaYearResolver quotaYearResolver;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;
  private final LiabilityRuleReadGate liabilityRuleReadGate;

  /**
   * 理算结果（含逐行归因，供拒赔五要素「事实认定」引用）。
   *
   * @param deductibleApplied 本次实际消抵的年度免赔（⑩ 写侧增量）：A7 结算须把它累加进责任账 {@code
   *     deductible_used}，否则次案仍会重扣全额免赔——护栏只装一半等于没装。
   * @param accumulationAvailable 累计读侧是否解析到责任账；{@code false} 时 A7 必须拒绝占额（账实不明不记账）
   */
  public record AdjustmentResult(
      Long calculationId,
      BigDecimal totalAmount,
      String formulaHash,
      String snapshot,
      List<AdjustmentLine> lines,
      String liabilityUuid,
      String sharedCode,
      BigDecimal deductibleApplied,
      boolean accumulationAvailable) {}

  /**
   * 执行理算（产出即 {@code FROZEN}）。
   *
   * <p>前置：① 证据集已锁定（§4.3，未锁定即 422——理算输入事后可变则"同输入同输出"无从谈起）； ② 计划下有生效责任配置（缺失即 422，绝不"默认 90%"隐式放行，§8.2
   * R-RAT）。
   */
  @Transactional
  public AdjustmentResult calculate(long claimId, String ruleVersion) {
    List<Evidence> evidences = evidenceRepository.findByClaimId(claimId);
    if (evidences.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, claimId);
    }
    boolean allLocked = evidences.stream().allMatch(Evidence::isLocked);
    if (!allLocked) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_EVIDENCE_NOT_LOCKED, claimId);
    }

    CalculationInput input = prepare(claimId);
    Adjustment adjustment;
    try {
      adjustment =
          Adjustment.calculate(
              DistributedIdGenerator.generateLongId(),
              tenantPort.currentTenantId(),
              claimId,
              input.rows(),
              input.liability(),
              ruleVersion,
              input.accumulation(),
              claimDateOf(input.rows()),
              input.catalog().asOf(),
              input.catalog().verdicts(),
              feeRulesOf(input.liability()),
              versionChainOf(claimId));
    } catch (DomainException ex) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, claimId, ex);
    }
    liabilityRuleReadGate.logDiffs(input.liability());
    adjustmentRepository.insert(adjustment);
    domainEventPublisher.publishFrom(adjustment);
    return toResult(adjustment);
  }

  /** 理算输入（共享准备路径：明细投影 → 责任 → 跨案累计 → 目录事实）。 */
  private record CalculationInput(
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      Accumulation accumulation,
      CatalogFacts catalog) {}

  private CalculationInput prepare(long claimId) {
    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    AdjLiability liability = resolveLiability(rows);
    // ③ 3a：开关关闭时这里什么都不做（连 JSON 都不解析），保证零行为变更可被证明；
    // 打开时挂载按键回落视图，装配阶段产出差异，算出金额后再由 logDiffs 记录。
    liabilityRuleReadGate.attach(liability);
    return new CalculationInput(
        rows, liability, accumulate(rows, liability), catalogFactsOf(rows, liability));
  }

  /**
   * 试算预览（§10.2 规则变更前试算比对）：<b>只算不落库</b>，产出候选结果与现行基线的差异。
   *
   * <p>与 {@link #calculate} 的三点差异：不做证据锁定前置（预览是"what-if"沙盘，输入允许尚未定格）； 不写 {@code
   * t_adj_calculation}（同输入同输出的落库语义只属于正式受理）；返回值携带新旧对照而非单边结果。 幂等天然成立：纯读路径。
   */
  @Transactional(readOnly = true)
  public AdjustmentPreview preview(long claimId, String ruleVersion) {
    CalculationInput input = prepare(claimId);
    Adjustment candidate;
    try {
      candidate =
          Adjustment.calculate(
              DistributedIdGenerator.generateLongId(),
              tenantPort.currentTenantId(),
              claimId,
              input.rows(),
              input.liability(),
              ruleVersion,
              input.accumulation(),
              claimDateOf(input.rows()),
              input.catalog().asOf(),
              input.catalog().verdicts(),
              feeRulesOf(input.liability()),
              versionChainOf(claimId));
    } catch (DomainException ex) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, claimId, ex);
    }
    liabilityRuleReadGate.logDiffs(input.liability());
    Adjustment baseline = adjustmentRepository.findLatestByClaimId(claimId);
    return new AdjustmentPreview(
        claimId,
        ruleVersion,
        baseline == null ? null : baseline.getId(),
        baseline == null ? null : baseline.getTotalAmount(),
        baseline == null ? null : baseline.getFormulaHash(),
        candidate.getTotalAmount(),
        candidate.getFormulaHash(),
        baseline == null || candidate.getTotalAmount().compareTo(baseline.getTotalAmount()) != 0,
        baseline == null || !candidate.getFormulaHash().equals(baseline.getFormulaHash()));
  }

  /** 试算预览结果：候选（未落库）对照现行基线（最新一次已落库理算）。 */
  public record AdjustmentPreview(
      Long claimId,
      String ruleVersion,
      Long baselineCalculationId,
      BigDecimal baselineTotalAmount,
      String baselineFormulaHash,
      BigDecimal candidateTotalAmount,
      String candidateFormulaHash,
      boolean totalChanged,
      boolean formulaHashChanged) {}

  /** 重算（规则/责任版本变更后；已 CONFIRMED 拒绝，由聚合抛领域异常）。 */
  @Transactional
  public AdjustmentResult recalculate(long claimId, String ruleVersion) {
    Adjustment latest = adjustmentRepository.findLatestByClaimId(claimId);
    if (latest == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, claimId);
    }
    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    AdjLiability liability = resolveLiability(rows);
    CatalogFacts catalog = catalogFactsOf(rows, liability);
    latest.recalculate(
        rows,
        liability,
        accumulate(rows, liability),
        claimDateOf(rows),
        catalog.asOf(),
        catalog.verdicts(),
        feeRulesOf(liability),
        versionChainOf(claimId));
    // 重算 = 用同一 calculation_id 重出一套归因。聚合级联写的 orphanRemoval 是 false
    // （否则 confirm() 这类"只改状态、不带集合"的 save 会把归因行全软删），
    // 故旧行不会自动清理——不显式删，同一 calculation_id 下会叠出多套互相矛盾的逐行归因。
    adjustmentRepository.supersedeLinesByCalculationId(latest.getId());
    adjustmentRepository.save(latest);
    domainEventPublisher.publishFrom(latest);
    return toResult(latest);
  }

  /** 核赔确认（frozen 语义：理算结果锁定，等 A7 资金阶段真正占额）。 */
  @Transactional
  public void confirm(long claimId) {
    Adjustment latest = adjustmentRepository.findLatestByClaimId(claimId);
    if (latest == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, claimId);
    }
    latest.confirm();
    adjustmentRepository.save(latest);
    domainEventPublisher.publishFrom(latest);
  }

  /**
   * 读取最新理算结果（A7 结算占额前的账实复核入口）。
   *
   * <p><b>关键修正（P0）</b>：{@code accumulationAvailable} 是瞬态标记（{@link
   * Adjustment#isAccumulationAvailable()} 标注 {@code @Transient}，不落库）。若直接 {@code
   * findLatestByClaimId} 后读该标记，reload 后恒为 {@code false}， 导致 <b>每一笔</b> 结算都走到 {@code
   * SC_CLAIM_QUOTA_NOT_FOUND} 而 fail-closed——即系统永远付不出钱。
   *
   * <p>按设计意图（"不落库，读侧实时复算账实可读性"），此处必须<b>实时复算</b>：重新拉取跨案累计， 以当前账本实际可读性决定 A7 是否放行。账实不明（缺责任账/缺责任配置）即
   * {@code false}，结算继续 fail-closed。
   */
  @Transactional(readOnly = true)
  public AdjustmentResult latest(long claimId) {
    Adjustment latest = adjustmentRepository.findLatestByClaimId(claimId);
    if (latest == null) {
      return null;
    }
    boolean accumulationAvailable;
    try {
      List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
      AdjLiability liability = resolveLiability(rows);
      Accumulation accumulation = accumulate(rows, liability);
      accumulationAvailable = accumulation.available();
    } catch (DomainException ex) {
      // 账实不明（缺责任配置/责任账）→ fail-closed，与计算期语义一致
      accumulationAvailable = false;
    }
    AdjustmentResult base = toResult(latest);
    return new AdjustmentResult(
        base.calculationId(),
        base.totalAmount(),
        base.formulaHash(),
        base.snapshot(),
        base.lines(),
        base.liabilityUuid(),
        base.sharedCode(),
        base.deductibleApplied(),
        accumulationAvailable);
  }

  /**
   * 读取跨案累计（⑩）。
   *
   * <p><b>缺账本不阻断理算</b>：理算结果还要给人看，直接 422 会让整条链路在缺数据时完全不可用。 改为在快照里落 {@code
   * accumulationAvailable=false} 并告警，由 A7 资金路径 fail-closed—— 结算是真正把钱占出去的动作，账实不明时不允许记账。
   *
   * <p><b>年度口径</b>：由 {@code QuotaYearResolver} 单一裁决（{@code bone.claim.quota-year.basis}）， 与 A7
   * 结算写侧<b>调用同一个方法</b>（{@link #quotaYear(long)}）。年度是额度账户主键的一部分，读写两侧各算一次
   * 就会在跨年/跨口径时落到两行账上——那不是"用了错的年度"，而是同一笔钱存在两本账。
   */
  private Accumulation accumulate(List<ClaimWithItemsProjection> rows, AdjLiability liability) {
    if (rows.isEmpty()) {
      return Accumulation.MISSING;
    }
    ClaimWithItemsProjection head = rows.get(0);
    if (head.getPolicyNo() == null || head.getInsuredIdHash() == null || head.getPlanId() == null) {
      return Accumulation.MISSING;
    }
    return accumulationPort.load(
        tenantPort.currentTenantId(),
        head.getInsuredIdHash(),
        head.getPolicyNo(),
        head.getPlanId(),
        quotaYearOf(rows),
        liability.getLiabilityUuid(),
        liability.getSharedCode(),
        dailyWindowOf(rows, liability));
  }

  /**
   * 本案的额度年度（A6 读侧与 A7 写侧<b>共用</b>，年度是账户主键的一部分）。
   *
   * <p>由赔案业务日（发票日 → 受理日）与保单期间裁决，<b>不看处理时刻</b>：跨年补报的案件按处理时刻归年度会把去年的费用 记进今年。口径与 fail-closed 语义见
   * {@link QuotaYearResolver}。
   */
  public int quotaYear(long claimId) {
    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    if (rows == null || rows.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
    }
    return quotaYearOf(rows);
  }

  private int quotaYearOf(List<ClaimWithItemsProjection> rows) {
    ClaimWithItemsProjection head = rows.get(0);
    return quotaYearResolver
        .resolve(head.getPolicyNo(), head.getPlanId(), ClaimBusinessDate.of(rows))
        .year();
  }

  /** 本案的日限额业务日（Q-05）。A7 占额写按日流水时必须与理算读用<b>同一个窗口</b>， 否则"算的时候看 A 天、写的时候记 B 天"，日限额在跨天补赔场景下必然错配。 */
  public java.time.LocalDate dailyWindow(long claimId) {
    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    return dailyWindowOf(rows, resolveLiability(rows));
  }

  /**
   * 目录准入事实（缺口 ①）：判定基准日 + 每条明细的结论。
   *
   * @param asOf 判定基准日；{@code null} = 判不出来（由 {@code CatalogAdmission.of} fail-closed）
   * @param verdicts {@code itemId → 结论}；空 = 一条都没查到（同上）
   */
  private record CatalogFacts(
      java.time.LocalDate asOf, Map<Long, CatalogAdmission.Verdict> verdicts) {

    static final CatalogFacts NONE = new CatalogFacts(null, Map.of());
  }

  /**
   * 取目录准入事实。
   *
   * <p><b>策略为 {@code NONE} 时一次外部调用都不发</b>：绝大多数责任不配目录，若每案都去打一次目录端口， 等于给全部理赔加一跳网络依赖，而收益为零。
   *
   * <p><b>基准日取发票日期、缺失回落赔案受理日</b>：与日限额 Q-05 同一条口径（{@link #dailyWindowOf}）。
   * 两个口径若各取各的，同一天的两件事（日限额窗口、目录版本）会指向不同日期，事后对不上账。
   *
   * <p>判不出来时<b>不在这里抛</b>，交给 {@code CatalogAdmission.of} 统一裁决：那里同时守"有编码没开策略"
   * 等反向一致性，散成两处会让同一类错误在不同入口给出不同结论。
   */
  private CatalogFacts catalogFactsOf(List<ClaimWithItemsProjection> rows, AdjLiability liability) {
    if (!"MANDATORY".equalsIgnoreCase(liability.catalogPolicy())) {
      return CatalogFacts.NONE;
    }
    java.time.LocalDate asOf = catalogDateOf(rows);
    if (asOf == null) {
      return new CatalogFacts(null, Map.of());
    }
    List<ItemCatalogPort.Item> items = new ArrayList<>();
    for (ClaimWithItemsProjection row : rows) {
      if (row != null && row.getItemId() != null) {
        items.add(new ItemCatalogPort.Item(row.getItemId(), row.getItemCode()));
      }
    }
    if (items.isEmpty()) {
      return new CatalogFacts(asOf, Map.of());
    }
    return new CatalogFacts(asOf, itemCatalogGateway.check(liability.catalogCode(), asOf, items));
  }

  /**
   * 目录判定基准日：发票日期优先（一案一发票），缺失回落赔案受理日；都缺返回 {@code null}。
   *
   * <p>与额度年度、日限额窗口共用同一个业务日定义（{@link ClaimBusinessDate}）：三处若各取各的，
   * 同一天的三件事（目录版本、按日限额、按年度累计）会指向不同日期，事后对不上账。
   */
  private java.time.LocalDate catalogDateOf(List<ClaimWithItemsProjection> rows) {
    return ClaimBusinessDate.of(rows);
  }

  /**
   * 日窗口：按同日发票取发票日期，按同日赔案取赔案受理日；未配日限额返回 {@code null}。
   *
   * <p><b>为什么赔案受理日取 {@code createdAt}</b>：这是系统里唯一有"受理"语义的时间戳（案件落库时刻）。 它是瞬时值，转业务日必须选时区——这里与 {@link
   * ClaimBusinessDate} 同口径用系统时区，理由是理赔作业本身按 本地日历排班；若将来引入保单所在时区，应在该单点替换，而不是散落到各调用方。
   */
  private java.time.LocalDate dailyWindowOf(
      List<ClaimWithItemsProjection> rows, AdjLiability liability) {
    if (liability == null || liability.dailyLimit().signum() <= 0) {
      return null;
    }
    String basis = liability.dailyLimitBasis();
    if ("SAME_DAY_INVOICE".equalsIgnoreCase(basis)) {
      return rows.isEmpty() ? null : rows.get(0).getInvoiceDate();
    }
    if ("SAME_DAY_CLAIM".equalsIgnoreCase(basis)) {
      return claimDateOf(rows);
    }
    return null;
  }

  /** 赔案受理日（{@code createdAt} 的业务日）；缺失返回 {@code null}（引擎据此 fail-closed）。 */
  private java.time.LocalDate claimDateOf(List<ClaimWithItemsProjection> rows) {
    if (rows == null || rows.isEmpty() || rows.get(0).getCreatedAt() == null) {
      return null;
    }
    return java.time.LocalDate.ofInstant(
        rows.get(0).getCreatedAt(), java.time.ZoneId.systemDefault());
  }

  /**
   * 取该责任生效的费用类别规则（{@code t_adj_liability_fee_rule}）。
   *
   * <p><b>为什么在这里读、而不是在聚合里读</b>：规则是"这一案生效的输入"，读库属应用层职责。
   * 聚合拿到的必须是显式参数——它同时要被快照物化、被复算复现；若聚合自己去查，复算时就变成"读今天的配置算昨天的账"， 快照也证明不了到底用了哪一版规则。
   *
   * <p><b>读不到就按"未细化"处理</b>（空集合）：这是配置面的正常形态，绝大多数责任没有类别规则；
   * 但<b>不会</b>因此静默忽略已配的规则——配了的规则一定被读到并进快照，这是本方法存在的意义。
   */
  private List<com.bone.claim.domain.model.liability.LiabilityFeeRule> feeRulesOf(
      AdjLiability liability) {
    if (liability == null
        || liability.getLiabilityUuid() == null
        || liability.getLiabilityUuid().isBlank()) {
      return List.of();
    }
    return liabilityFeeRuleRepository.findByLiabilityUuid(
        tenantPort.currentTenantId(), liability.getLiabilityUuid());
  }

  /**
   * 版本链取值（§9.4 受理时刻锁定）：计划取案件 {@code plan_version}， 目录/模型/提示词/决策图取该案<b>最新一次</b>决策执行——与
   * claim_decision_execution 对齐到同一次执行，审计回放时两侧可互证。未跑决策图（人工直受理算）时决策四维落空串。
   */
  private CalculationVersions versionChainOf(long claimId) {
    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    DecisionExecution execution = decisionExecutionRepository.findLatestByClaimId(claimId);
    return new CalculationVersions(
        claim.getPlanVersion(),
        execution == null ? null : execution.getCatalogVersion(),
        execution == null ? null : execution.getModelVersion(),
        execution == null ? null : execution.getPromptVersion(),
        execution == null ? null : execution.getGraphVersion());
  }

  private AdjustmentResult toResult(Adjustment adjustment) {
    return new AdjustmentResult(
        adjustment.getId(),
        adjustment.getTotalAmount(),
        adjustment.getFormulaHash(),
        adjustment.getFormulaSnapshot(),
        adjustmentRepository.findLinesByCalculationId(adjustment.getId()),
        adjustment.getLiabilityUuid(),
        adjustment.getSharedCode(),
        adjustment.getDeductibleApplied(),
        adjustment.isAccumulationAvailable());
  }

  /**
   * 取当期生效责任（多责任时取第一条；多责任链式赔付属二期）。
   *
   * <p>取不到即 422——责任配置是理算的唯一依据，没有"默认责任"。
   */
  private AdjLiability resolveLiability(List<ClaimWithItemsProjection> rows) {
    Long planId = rows.isEmpty() ? null : rows.get(0).getPlanId();
    if (planId == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, "planId 缺失");
    }
    List<AdjLiability> liabilities = liabilityRepository.findActiveByPlanId(planId);
    if (liabilities.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, planId);
    }
    return liabilities.get(0);
  }
}
