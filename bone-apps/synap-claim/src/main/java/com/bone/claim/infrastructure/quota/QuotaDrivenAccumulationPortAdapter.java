package com.bone.claim.infrastructure.quota;

import com.bone.claim.application.port.out.AccumulationPort;
import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
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
      String liabilityUuid) {
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
    return Accumulation.of(account.getDeductibleUsed(), limitUsed, true);
  }
}
