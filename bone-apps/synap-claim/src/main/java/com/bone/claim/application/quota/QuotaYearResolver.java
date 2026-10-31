package com.bone.claim.application.quota;

import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import java.time.LocalDate;
import java.time.Year;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 额度年度口径的唯一裁决点（A5/A6/A7 读写两侧共用）。
 *
 * <p><b>为什么必须收成一处</b>：额度账户的行键是 {@code (insured_id_hash, policy_no, plan_id, quota_year, quota_type,
 * liability_uuid)}，年度是<b>主键的一部分</b>。此前 8 处调用点各写一次 {@code Year.now()}，其中"理算读"与"结算写"分属两个类：
 * 任何一侧改变口径（哪怕只改成"按业务日"），同一笔钱就会落到<b>两个不同的账户行</b>——读侧看到"未占用"（超赔），
 * 写侧在另一个年度上累积（账本对不上）。所以这里不只是去重，是把"主键怎么算"变成单一事实。
 *
 * <p><b>两种口径</b>（由 {@code bone.claim.quota-year.basis} 选择）：
 *
 * <ul>
 *   <li>{@code CALENDAR}（默认）：自然年。业务日缺失的历史案件退回处理日（与改动前行为一致），
 *       是<b>唯一</b>不阻断账务的取值——此时"年度"是按处理时间近似，非保单条款口径。
 *   <li>{@code POLICY_TERM}：保单年度。按保单生效日起算，每满 12 个月为一个保单年度，{@code quota_year} 取该年度的<b>起始年份</b>（例：生效
 *       2026-03-01 的保单，2027-02-28 仍属 2026 保单年度）。 期间查不到、业务日缺失、业务日落在保单期间之外，一律 fail-closed（{@code
 *       SC_CLAIM_POLICY_NOT_ACTIVE} / {@code
 *       SC_CLAIM_OUT_OF_POLICY_PERIOD}）——<b>绝不退回自然年</b>：那会把"配置已切到保单年度"这件事 伪装成成功，账上却按另一条规则记，比拒算更难发现。
 * </ul>
 *
 * <p><b>为什么默认是 {@code CALENDAR} 而不是直接切保单年度</b>：切换是<b>数据迁移</b>而不只是开关——已开户的账户行按自然年落了 {@code
 * quota_year}，切换后同一年度会解析到不同的行键，历史累计读不到（免赔重扣、封顶重满）。故默认保持现状、 由部署方在完成账户年度重映射后显式开启；开启前须先把 {@code
 * PolicyQueryPort} 接到真实保单系统。
 *
 * <p><b>为什么用业务日而不是 {@code now()}</b>：保单年度的归属看的是费用发生时间。跨年补报的案件按处理时刻归年度，
 * 会把去年的费用记进今年（免赔重置=少赔、封顶重置=超赔），而账面上看不出任何异常。
 */
@Component
public class QuotaYearResolver {

  private static final Logger log = LoggerFactory.getLogger(QuotaYearResolver.class);

  /** 年度口径。配置值大小写不敏感；无法识别即启动失败（不静默退回默认口径）。 */
  public enum Basis {
    /** 自然年（默认）。 */
    CALENDAR,
    /** 保单年度（按保单生效日起算的 12 个月周期）。 */
    POLICY_TERM
  }

  /**
   * 年度裁决结果。
   *
   * @param year 落到 {@code quota_year} 的值（自然年 = 业务日年份；保单年度 = 该年度起始年份）
   * @param basis 实际采用的口径（写进日志与排障视图，回答"这笔账按哪条规则归年度"）
   */
  public record QuotaYear(int year, Basis basis) {}

  private final PolicyQueryPort policyQueryPort;

  private final Basis basis;

  /**
   * 容器装配入口。
   *
   * <p>配置项缺省即 {@code CALENDAR}：升级本版本不改变任何既有账务行为，切换是显式动作。
   */
  @Autowired
  public QuotaYearResolver(
      @Value("${bone.claim.quota-year.basis:CALENDAR}") String basis,
      PolicyQueryPort policyQueryPort) {
    this(parse(basis), policyQueryPort);
    if (this.basis == Basis.POLICY_TERM) {
      log.info("额度年度口径 = POLICY_TERM：按保单生效日起算的 12 个月周期归属（需 PolicyQueryPort 已接真实保单系统）");
    }
  }

  private QuotaYearResolver(Basis basis, PolicyQueryPort policyQueryPort) {
    this.basis = basis;
    this.policyQueryPort = policyQueryPort;
  }

  /** 单测/固定口径构造（不依赖 Spring）。 */
  public static QuotaYearResolver of(Basis basis, PolicyQueryPort policyQueryPort) {
    return new QuotaYearResolver(basis, policyQueryPort);
  }

  /** 当前生效口径（诊断与启动自检用；不参与金额计算）。 */
  public Basis basis() {
    return basis;
  }

  /**
   * 裁决某笔费用的额度年度。
   *
   * @param businessDate 业务日（{@link com.bone.claim.application.ClaimBusinessDate}）；{@code null} =
   *     发票日与受理时间戳都缺
   * @throws com.bone.core.exception.DomainException 口径为保单年度而期间/业务日不可证时
   */
  public QuotaYear resolve(String policyNo, long planId, LocalDate businessDate) {
    if (basis == Basis.CALENDAR) {
      // 业务日缺失时退回处理日：这是改动前的行为，也是唯一不阻断账务的取值。
      // 不在此处 fail-closed，是因为自然年口径下"今年"总是可证的，缺失的只是更精确的业务日。
      LocalDate anchor = businessDate == null ? LocalDate.now() : businessDate;
      return new QuotaYear(Year.from(anchor).getValue(), Basis.CALENDAR);
    }
    return resolvePolicyTerm(policyNo, planId, businessDate);
  }

  private QuotaYear resolvePolicyTerm(String policyNo, long planId, LocalDate businessDate) {
    if (businessDate == null) {
      // 年度是账户主键的一部分：判不出就不许落账，而不是拿处理日顶替（顶替出来的账户行没人能认领）。
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_OUT_OF_POLICY_PERIOD, policyNo + " 业务日缺失，无法定位保单年度");
    }
    PolicyQueryPort.PolicySnapshot snapshot = snapshotOf(policyNo, planId);
    LocalDate inception =
        LocalDate.ofInstant(snapshot.effectiveFrom(), java.time.ZoneId.systemDefault());
    LocalDate expiry =
        LocalDate.ofInstant(snapshot.effectiveTo(), java.time.ZoneId.systemDefault());
    if (businessDate.isBefore(inception) || businessDate.isAfter(expiry)) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_OUT_OF_POLICY_PERIOD,
          policyNo + " 业务日 " + businessDate + " 不在保单期间 [" + inception + ", " + expiry + "]");
    }
    // 保单年度 = 自生效日起每满 12 个月为一个周期；quota_year 记为周期起始年份。
    LocalDate start = inception;
    int guard = 0;
    while (!businessDate.isBefore(start.plusYears(1))) {
      start = start.plusYears(1);
      if (++guard > 200) {
        // 生效日在未来/业务日异常导致无限推进时不静默兜底，按期间外处理。
        throw ClaimErrors.of(ClaimErrorCodes.CLAIM_OUT_OF_POLICY_PERIOD, policyNo);
      }
    }
    return new QuotaYear(start.getYear(), Basis.POLICY_TERM);
  }

  private PolicyQueryPort.PolicySnapshot snapshotOf(String policyNo, long planId) {
    if (policyQueryPort == null || policyNo == null || policyNo.isBlank()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE, policyNo);
    }
    // 用 findByPolicy 而不是 findActivePolicy：已满期保单的补赔/红冲仍要回到原保单年度记账，
    // "此刻生效中"的过滤会把这类案件误判成"保单不存在"。
    java.util.Optional<PolicyQueryPort.PolicySnapshot> found =
        policyQueryPort.findByPolicy(policyNo, planId);
    if (found == null || found.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE, policyNo);
    }
    PolicyQueryPort.PolicySnapshot snapshot = found.get();
    if (snapshot.effectiveFrom() == null || snapshot.effectiveTo() == null) {
      // 半截期间算出来的年度看似合法，实际是错的：宁可拒算。
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE, policyNo + " 保单期间不完整");
    }
    return snapshot;
  }

  private static Basis parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Basis.CALENDAR;
    }
    try {
      return Basis.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      // 启动期失败：口径写错时继续跑，等于让账按一个谁也没声明过的规则记。
      throw new IllegalArgumentException(
          "bone.claim.quota-year.basis 只支持 CALENDAR / POLICY_TERM，实际为 " + raw, ex);
    }
  }
}
