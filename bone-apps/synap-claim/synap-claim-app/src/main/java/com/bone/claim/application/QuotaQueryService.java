package com.bone.claim.application;

import com.bone.claim.application.quota.QuotaYearResolver;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A5 额度账户读模型（只读）。
 *
 * <p>资金工作台按案件展示额度（三值：initial/claimed/frozen，可用 = initial − claimed − frozen）与流水。 读路径跨聚合（案件 →
 * 额度账户），属读模型，不写额度、不触发领域事件。
 *
 * <p>写侧仍走 {@link QuotaApplicationService}（单聚合、额度守恒写路径强制）；本服务仅查询投影。
 */
@Service
@RequiredArgsConstructor
public class QuotaQueryService {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private final ClaimCaseRepository claimCaseRepository;
  private final QuotaAccountRepository quotaAccountRepository;
  private final QuotaYearResolver quotaYearResolver;

  /**
   * 按案件查额度账户。赔案不存在才抛 {@code SC_CLAIM_NOT_FOUND}。
   *
   * <p><b>账户未开立不抛 404，返回 {@code opened=false} 的空摘要</b>（2026-11-01）：额度账户是按 {@code (被保人, 保单, 计划, 年度,
   * 维度)} 开户的，而年度归属取决于业务日与口径（见 {@link
   * QuotaYearResolver}）——"这个案子还没有额度账户"是<b>一个需要被看见的状态</b>，不是"资源不存在"。 此前一律抛 {@code
   * SC_CLAIM_QUOTA_NOT_FOUND}，运维在工作台上只拿到一个 404 与一个错误码， 分不清是"赔案查不到""年度口径切了但账户未重映射"还是"开户没跑"，只能去数据库里猜。
   *
   * <p>代价是页面必须<b>读 {@code opened}</b> 再决定渲染（0 元额度 ≠ 没有账户）；收益是同一份响应同时
   * 携带有诊断意义的两件事：查的是<b>哪一年</b>的账户（{@code quotaYear}）与按<b>什么口径</b>算的（{@code quotaYearBasis}）。
   *
   * <p>资金路径（A7）不受影响：它走 {@link QuotaApplicationService}，缺账户仍 fail-closed 拒算—— 查不到 ≠ 可以付。
   */
  @Transactional(readOnly = true)
  public QuotaSummary getByClaim(long claimId) {
    List<ClaimWithItemsProjection> ctx = claimCaseRepository.findClaimWithItems(claimId);
    if (ctx == null || ctx.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
    }
    ClaimWithItemsProjection head = ctx.get(0);
    // 账户行键缺"保单"或"计划"时连年度都裁不出来（POLICY_TERM 要靠 plan_id 取保单期间），
    // 兜底给出 opened=false + quotaYear=null，而不是 NPE 变 500，也不是一个猜出来的年份。
    if (head.getPolicyNo() == null || head.getPlanId() == null) {
      return QuotaSummary.unknownYear(quotaYearResolver.basis());
    }
    // 与 A7 写侧同一口径（赔案业务日 + QuotaYearResolver）：查询若按"当前自然年"取账户，
    // 跨年案件会查到一个空账户或别人的账户，页面显示"额度为 0"而钱其实记在上一保单年度。
    QuotaYearResolver.QuotaYear quotaYear =
        quotaYearResolver.resolve(head.getPolicyNo(), head.getPlanId(), ClaimBusinessDate.of(ctx));
    if (head.getInsuredIdHash() == null) {
      // 报案数据不全的历史案件是常态：工作台要能显示"该案没有额度账户"，而不是白屏。
      return QuotaSummary.notOpened(quotaYear);
    }
    QuotaAccount account =
        quotaAccountRepository.findAccount(
            head.getInsuredIdHash(),
            head.getPolicyNo(),
            head.getPlanId(),
            quotaYear.year(),
            "PERSONAL",
            "");
    if (account == null) {
      return QuotaSummary.notOpened(quotaYear);
    }
    List<QuotaTransaction> txns = quotaAccountRepository.findTransactions(account.getId());
    return QuotaSummary.of(
        account,
        quotaYear,
        txns == null
            ? List.of()
            : txns.stream().map(QuotaSummary.Txn::from).collect(Collectors.toList()));
  }

  /**
   * 额度账户快照 + 流水（append-only 事实）。
   *
   * @param quotaYear 查到的是<b>哪一年</b>的账户（口径见 {@code quotaYearBasis}）。跨年/切换口径时， "看到
   *     0"与"看错年份"在页面上长得一模一样，年度必须随数据一起返回。{@code null} 表示案件缺少 行键（保单号/计划），年度不可确定——不是 0。
   * @param quotaYearBasis 年度口径（{@code CALENDAR} 自然年 / {@code POLICY_TERM} 保单年度）
   * @param opened 账户是否已开立；{@code false} 时其余金额字段均为 0，**不得**当作"额度为 0"
   * @param capReached 是否已达封顶（{@code claimed + frozen >= initial}，即已无可赔额度）。
   *     <b>读模型推导字段，不落库</b>（§5.3：{@code quota_account} 不增列，避免与 {@code cap_used} 双源漂移）。 未开户恒为 {@code
   *     false}——"没有账户"不是"封顶了"，把两者混成一个 true 会让工作台的 "已达封顶"筛选把未开户的案子也抓进来。
   * @param transactions 流水（口径见 {@link Txn}）
   */
  public record QuotaSummary(
      Integer quotaYear,
      String quotaYearBasis,
      boolean opened,
      BigDecimal initial,
      BigDecimal claimed,
      BigDecimal frozen,
      BigDecimal deductibleUsed,
      BigDecimal capUsed,
      BigDecimal available,
      boolean capReached,
      List<Txn> transactions) {

    /** 账户未开立：金额全 0、{@code opened=false}，仍带回年度与口径供排障。 */
    static QuotaSummary notOpened(QuotaYearResolver.QuotaYear quotaYear) {
      return build(quotaYear.year(), quotaYear.basis(), false, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    /** 连年度都裁不出来（行键缺保单/计划）：{@code quotaYear=null}，明确"未知"而非"0 年"。 */
    static QuotaSummary unknownYear(QuotaYearResolver.Basis basis) {
      return build(null, basis, false, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    static QuotaSummary of(
        QuotaAccount account, QuotaYearResolver.QuotaYear quotaYear, List<Txn> transactions) {
      BigDecimal claimed = nz(account.getQuotaClaimed());
      BigDecimal frozen = nz(account.getQuotaFrozen());
      BigDecimal initial = nz(account.getQuotaInitial());
      return new QuotaSummary(
          quotaYear.year(),
          quotaYear.basis().name(),
          true,
          initial,
          claimed,
          frozen,
          nz(account.getDeductibleUsed()),
          nz(account.getCapUsed()),
          account.available(),
          // 已无可赔额度即"已达封顶"：含冻结占用（冻着的钱已经占掉额度，不能算还能赔）。
          claimed.add(frozen).compareTo(initial) >= 0,
          transactions);
    }

    private static QuotaSummary build(
        Integer quotaYear,
        QuotaYearResolver.Basis basis,
        boolean opened,
        BigDecimal initial,
        BigDecimal claimed,
        BigDecimal frozen,
        BigDecimal deductibleUsed,
        BigDecimal capUsed,
        BigDecimal available) {
      return new QuotaSummary(
          quotaYear,
          basis.name(),
          opened,
          initial,
          claimed,
          frozen,
          deductibleUsed,
          capUsed,
          available,
          false,
          List.of());
    }

    private static BigDecimal nz(BigDecimal value) {
      return value == null ? ZERO : value;
    }

    /**
     * 额度流水视图（守恒校验与审计的数据源）。
     *
     * <p><b>{@code beforeAmount}/{@code afterAmount} 只对"改余额"的流水有意义</b>：{@code
     * txn_type=DAILY}（日用量，Q-05）与 {@code DEDUCTIBLE}（免赔消抵）记的是事实，其一侧或两侧恒为 0——前台若把它渲染成"变动前 → 变动后"，
     * 会像一笔"金额 X 却没改变余额"的坏数据。判断某行改没改余额，看的是 {@code txnType} 而不是金额是否大于 0。
     *
     * <p><b>{@code bizDate} 必须出现在视图里</b>：日限额按业务日累计，排障时第一个问题就是"这笔算在哪一天"。 只给 {@code
     * occurredAt}（落账时刻）回答不了——9 月的发票 10 月才赔，两个日期本来就不同，缺了业务日就分不清"当日额度真赔满了"还是"取错了天"。
     */
    public record Txn(
        String direction,
        String txnType,
        BigDecimal amount,
        BigDecimal beforeAmount,
        BigDecimal afterAmount,
        java.time.LocalDate bizDate,
        String idempotencyKey,
        Instant occurredAt) {

      static Txn from(QuotaTransaction t) {
        return new Txn(
            t.getDirection(),
            t.getTxnType(),
            t.getAmount(),
            t.getBeforeAmount(),
            t.getAfterAmount(),
            t.getBizDate(),
            t.getIdempotencyKey(),
            t.getOccurredAt());
      }
    }
  }
}
