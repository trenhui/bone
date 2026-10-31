package com.bone.claim.infrastructure.quota;

import com.bone.claim.application.port.out.AccumulationPort;
import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 累计读侧适配器：把额度账户（{@code LIABILITY_CAP} 分行）投影成理算需要的 {@link Accumulation}。
 *
 * <p><b>这不是桩</b>——它读的是真实的 {@code t_quota_account}，与 A7 结算占额写的是<b>同一行</b>：
 *
 * <pre>
 *   责任限额已占用 = quota_claimed + quota_frozen      （frozen 必须计入，否则连续两案理算都看到"未占用"）
 *   年度免赔已消抵 = deductible_used                    （由 A7 软占时按理算产出的 deductibleApplied 累加）
 * </pre>
 *
 * <p><b>为什么 frozen 要计入</b>：只算 {@code claimed}（已付）的话，两案先后理算、都还没支付时，
 * 后案看到的仍是"零占用"，照样能突破责任限额——这正是参考实现诊断 N3 的成因。
 *
 * <p><b>缺口即 {@link Accumulation#MISSING}</b>：开户属承保/迁移职责（§8.3 配置外部化），理赔域不代开户。 账户不存在时返回 {@code
 * available=false} 并告警，由 A7 资金路径 fail-closed—— 理赔域"猜"一个账户出来，等于把超赔风险藏进默认值。
 */
@Component
@RequiredArgsConstructor
public class QuotaDrivenAccumulationPortAdapter implements AccumulationPort {

  private static final Logger log =
      LoggerFactory.getLogger(QuotaDrivenAccumulationPortAdapter.class);

  /** 责任账维度（§5.3）：与 {@code QuotaApplicationService} 写侧必须同源，否则读写两侧各记一本账。 */
  private static final String QUOTA_TYPE_LIABILITY_CAP = "LIABILITY_CAP";

  private final QuotaAccountRepository quotaAccountRepository;

  @Override
  public Accumulation load(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      java.time.LocalDate dailyWindow) {
    String uuid = liabilityUuid == null ? "" : liabilityUuid;
    QuotaAccount account =
        quotaAccountRepository.findAccount(
            insuredIdHash, policyNo, planId, quotaYear, QUOTA_TYPE_LIABILITY_CAP, uuid);
    if (account == null) {
      log.warn(
          "累计读侧未解析到责任账 → 返回 MISSING（A7 将拒绝占额）：tenant={} policy={} plan={} year={} liability={}",
          tenantId,
          policyNo,
          planId,
          quotaYear,
          uuid);
      return Accumulation.MISSING;
    }
    BigDecimal limitUsed = account.getQuotaClaimed().add(account.getQuotaFrozen());
    BigDecimal dailyUsed = dailyUsed(account, dailyWindow);
    if (dailyUsed.signum() < 0) {
      // 负的日用量 = ROLLBACK 超过 OCCUPY（重复释放 / 记错业务日 / 结构化改造留下的半截流水）。
      // 此前这里 `.max(ZERO)` 钳到 0：当日已用归零 ⇒ 日限额重新满额 ⇒ **静默超赔**，而账面上"什么都正常"。
      // 账实不明时的既定处置是 available=false（理算只标记、A7 拒绝占额），不是替它猜一个数。
      log.error(
          "日用量流水聚合为负（ROLLBACK 超过 OCCUPY）→ 账实不明，返回 available=false 由 A7 拒绝占额："
              + "tenant={} account={} window={} used={}",
          tenantId,
          account.getId(),
          dailyWindow,
          dailyUsed);
      return Accumulation.of(account.getDeductibleUsed(), limitUsed, false);
    }
    return Accumulation.of(account.getDeductibleUsed(), limitUsed, dailyUsed, dailyWindow, true);
  }

  /**
   * 某业务日的已占用赔付额（Q-05）：从 {@code txn_type=DAILY} 的流水按 {@code biz_date} 聚合。
   *
   * <p><b>为什么从流水算而不是账户上存一个"当日已用"字段</b>：存字段就必须在跨日时清零，而"清零"是个
   * 状态迁移——漏跑一次（服务重启、批处理失败、补赔跨日）就永久多算或少算一天，且没有任何事实可以复原。 流水是事实：{@code OCCUPY} 加、{@code ROLLBACK}
   * 减，任意一天随时可重算，倒签发票也能算对。
   *
   * <p>业务日为 {@code null}（本案不配日限额）时直接返回 0，不扫流水。
   *
   * <p><b>不在此处钳到 0</b>：负的聚合值是账实不符的信号（释放多于占用），由调用方转成 {@code available=false} 交给 A7
   * fail-closed。在这里钳零等于把"账本坏了"显示成"今天还没赔过"。
   */
  private BigDecimal dailyUsed(QuotaAccount account, java.time.LocalDate window) {
    if (window == null) {
      return BigDecimal.ZERO;
    }
    List<QuotaTransaction> transactions = quotaAccountRepository.findTransactions(account.getId());
    if (transactions == null || transactions.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal used = BigDecimal.ZERO;
    for (QuotaTransaction txn : transactions) {
      if (!"DAILY".equals(txn.getTxnType()) || txn.getBizDate() == null) {
        continue;
      }
      if (!window.equals(txn.getBizDate())) {
        continue;
      }
      if ("OCCUPY".equals(txn.getDirection())) {
        used = used.add(txn.getAmount());
      } else {
        used = used.subtract(txn.getAmount());
      }
    }
    return used;
  }
}
