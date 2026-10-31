package com.bone.claim.application;

import com.bone.claim.application.port.out.AccumulationPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.adjustment.AdjustmentLine;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.util.List;
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
  private final EvidenceRepository evidenceRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final AccumulationPort accumulationPort;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

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
    long tenantId = tenantPort.currentTenantId();

    List<Evidence> evidences = evidenceRepository.findByClaimId(claimId);
    if (evidences.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, claimId);
    }
    boolean allLocked = evidences.stream().allMatch(Evidence::isLocked);
    if (!allLocked) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_EVIDENCE_NOT_LOCKED, claimId);
    }

    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    AdjLiability liability = resolveLiability(rows);
    Accumulation accumulation = accumulate(rows, liability);

    Adjustment adjustment;
    try {
      adjustment =
          Adjustment.calculate(
              DistributedIdGenerator.generateLongId(),
              tenantId,
              claimId,
              rows,
              liability,
              ruleVersion,
              accumulation,
              claimDateOf(rows));
    } catch (DomainException ex) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, claimId, ex);
    }
    adjustmentRepository.insert(adjustment);
    domainEventPublisher.publishFrom(adjustment);
    return toResult(adjustment);
  }

  /** 重算（规则/责任版本变更后；已 CONFIRMED 拒绝，由聚合抛领域异常）。 */
  @Transactional
  public AdjustmentResult recalculate(long claimId, String ruleVersion) {
    Adjustment latest = adjustmentRepository.findLatestByClaimId(claimId);
    if (latest == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, claimId);
    }
    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    AdjLiability liability = resolveLiability(rows);
    latest.recalculate(rows, liability, accumulate(rows, liability), claimDateOf(rows));
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
        base.deductibleApplied(),
        accumulationAvailable);
  }

  /**
   * 读取跨案累计（⑩）。
   *
   * <p><b>缺账本不阻断理算</b>：理算结果还要给人看，直接 422 会让整条链路在缺数据时完全不可用。 改为在快照里落 {@code
   * accumulationAvailable=false} 并告警，由 A7 资金路径 fail-closed—— 结算是真正把钱占出去的动作，账实不明时不允许记账。
   *
   * <p><b>年度口径</b>：与 {@code QuotaApplicationService#loadPersonal} 同为 {@code Year.now()}。
   * 严格来说应为<b>保单年度</b>（DDL 注释即"按保单年度"），保单生效月非 1 月时两者不等； 此处刻意与既有写侧保持一致——读写两侧若用不同口径，两本账立刻对不上，
   * 那比"用错年度"更糟。保单年度口径作为独立改动（需保单快照提供生效日）另行推进。
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
        java.time.Year.now().getValue(),
        liability.getLiabilityUuid(),
        dailyWindowOf(rows, liability));
  }

  /** 本案的日限额业务日（Q-05）。A7 占额写按日流水时必须与理算读用<b>同一个窗口</b>， 否则"算的时候看 A 天、写的时候记 B 天"，日限额在跨天补赔场景下必然错配。 */
  public java.time.LocalDate dailyWindow(long claimId) {
    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    return dailyWindowOf(rows, resolveLiability(rows));
  }

  /**
   * 日窗口：按同日发票取发票日期，按同日赔案取赔案受理日；未配日限额返回 {@code null}。
   *
   * <p><b>为什么赔案受理日取 {@code createdAt}</b>：这是系统里唯一有"受理"语义的时间戳（案件落库时刻）。 它是瞬时值，转业务日必须选时区——这里与 {@code
   * Year.now()} 同口径用系统时区，理由是理赔作业本身按 本地日历排班；若将来引入保单所在时区，应在此单点替换，而不是散落到各调用方。
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

  private AdjustmentResult toResult(Adjustment adjustment) {
    return new AdjustmentResult(
        adjustment.getId(),
        adjustment.getTotalAmount(),
        adjustment.getFormulaHash(),
        adjustment.getFormulaSnapshot(),
        adjustmentRepository.findLinesByCalculationId(adjustment.getId()),
        adjustment.getLiabilityUuid(),
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
