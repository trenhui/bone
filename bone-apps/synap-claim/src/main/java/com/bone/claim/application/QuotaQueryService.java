package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
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
 * <p>写侧仍走 {@link QuotaApplicationService}（REQUIRES_NEW 单聚合、额度守恒写路径强制）；本服务仅查询投影。
 */
@Service
@RequiredArgsConstructor
public class QuotaQueryService {

  private final ClaimCaseRepository claimCaseRepository;
  private final QuotaAccountRepository quotaAccountRepository;

  @Transactional(readOnly = true)
  public QuotaSummary getByClaim(long claimId) {
    List<ClaimWithItemsProjection> ctx = claimCaseRepository.findClaimWithItems(claimId);
    if (ctx == null || ctx.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
    }
    ClaimWithItemsProjection head = ctx.get(0);
    QuotaAccount account =
        quotaAccountRepository.findAccount(
            head.getInsuredIdHash(),
            head.getPolicyNo(),
            head.getPlanId(),
            Year.now().getValue(),
            "PERSONAL",
            "");
    if (account == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, head.getPolicyNo());
    }
    List<QuotaTransaction> txns = quotaAccountRepository.findTransactions(account.getId());
    return new QuotaSummary(
        account.getQuotaInitial(),
        account.getQuotaClaimed(),
        account.getQuotaFrozen(),
        account.available(),
        txns.stream().map(QuotaSummary.Txn::from).collect(Collectors.toList()));
  }

  /** 额度账户快照 + 流水（append-only 事实）。 */
  public record QuotaSummary(
      BigDecimal initial,
      BigDecimal claimed,
      BigDecimal frozen,
      BigDecimal available,
      List<Txn> transactions) {

    /** 额度流水视图（守恒校验与审计的数据源）。 */
    public record Txn(
        String direction,
        String txnType,
        BigDecimal amount,
        BigDecimal beforeAmount,
        BigDecimal afterAmount,
        String idempotencyKey,
        Instant occurredAt) {

      static Txn from(QuotaTransaction t) {
        return new Txn(
            t.getDirection(),
            t.getTxnType(),
            t.getAmount(),
            t.getBeforeAmount(),
            t.getAfterAmount(),
            t.getIdempotencyKey(),
            t.getOccurredAt());
      }
    }
  }
}
