package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaChangeLog;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.QuotaChangeLogRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 额度配置应用服务（配置面）：开户 / 调额 / 减人 / 恢复 / 保全记录查询。
 *
 * <p><b>与理赔占用路径（{@link QuotaApplicationService}）的分工</b>：占用路径处理"赔案占额/确认/释放"，
 * 本类处理"运营给某个被保人开账户、改保额、把人减掉"。两者写的是同一张 {@code t_quota_account}， 但语义正交：
 *
 * <ul>
 *   <li>占用路径只改 {@code claimed}/{@code frozen}，<b>从不改 {@code initial}</b>；
 *   <li>本类只改 {@code initial} 与 {@code insured_state}，<b>从不改 claimed/frozen</b>。
 * </ul>
 *
 * <p><b>因此本类不落 {@code QuotaTransaction}</b>：额度流水是<b>理赔占用流水</b>， 守恒校验（{@code available = initial −
 * claimed − frozen}）依赖它；把配置变更混进去会让 "按 txn_type 统计占用"的读侧把保全算成占用。配置变更的留痕走独立的 {@link QuotaChangeLog}。
 *
 * <p><b>传播语义</b>：本类的写方法用 {@link Propagation#REQUIRED}（加入调用方事务）。 原因与 {@code QuotaApplicationService}
 * 同款——额度账户与保全记录必须同生共死： 账户改了而记录没落 ⇒ 事后无从追溯这笔保额是怎么来的；记录落了而账户没改 ⇒ 账面与事实不符。
 *
 * <p><b>E-5.4 豁免说明（为何声明 {@link NoDomainEvent}）</b>：额度配置变更的事实载体是 <b>{@link
 * QuotaChangeLog}（保全记录表）</b>，不是领域事件。理由有三：
 *
 * <ul>
 *   <li><b>事实已在 append-only 表里</b>：开户/调额/减人/恢复每次都落一行保全记录（含变更前后值、
 *       操作人、说明、时间）。再发一份领域事件等于把同一事实记两遍，两处一旦不一致就无从判断谁对；
 *   <li><b>与理赔占用路径同款处理</b>：{@link QuotaApplicationService} 的额度占用变更同样只落 {@code QuotaTransaction}
 *       流水、不发领域事件——本类与它写的是同一张账户表，处置口径必须一致；
 *   <li><b>消费方是运营查询而非订阅</b>：保全记录的唯一读者是"查这笔保额怎么来的"的运营人员 （配置面接口直接读表），没有需要订阅实时通知的下游。
 * </ul>
 */
@Service
@NoDomainEvent
@RequiredArgsConstructor
public class QuotaConfigApplicationService {

  private final QuotaAccountRepository quotaAccountRepository;
  private final QuotaChangeLogRepository quotaChangeLogRepository;
  private final TenantPort tenantPort;

  /** 某保单下的额度账户列表（配置面）。 */
  @Transactional(readOnly = true)
  public List<QuotaAccountView> listByPolicy(String policyNo) {
    return quotaAccountRepository.findAccountsByPolicyNo(policyNo).stream()
        .map(QuotaAccountView::from)
        .toList();
  }

  /** 某被保人在某保单下的额度账户列表。 */
  @Transactional(readOnly = true)
  public List<QuotaAccountView> listByPolicyAndInsured(String policyNo, String insuredIdHash) {
    return quotaAccountRepository.findAccountsByPolicyAndInsured(policyNo, insuredIdHash).stream()
        .map(QuotaAccountView::from)
        .toList();
  }

  /** 某额度账户的保全记录（时间正序）。 */
  @Transactional(readOnly = true)
  public List<QuotaChangeView> listChanges(long quotaAccountId) {
    return quotaChangeLogRepository.findByAccountId(quotaAccountId).stream()
        .map(QuotaChangeView::from)
        .toList();
  }

  /**
   * 开户（初始化个人额度）。
   *
   * <p>此前 {@code QuotaAccount.open(...)} 生产零调用 —— 额度账户<b>没有任何创建入口</b>， 只有理算链路内部的读（{@code
   * loadPersonal} 找不到即抛 {@code SC_CLAIM_QUOTA_NOT_FOUND}）。
   * 这意味着运营无法为被保人预设任何额度，参考实现里的"初始化个人额度"操作在本仓完全做不到。
   *
   * <p><b>幂等</b>：同 (被保人, 保单, 计划, 年度, 维度, 责任) 已有账户时直接返回既有账户， 而非报冲突——重复点"初始化"是运营的正常操作，报错只会让人不知所措。
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public QuotaAccountView openAccount(OpenQuota command) {
    long tenantId = tenantPort.currentTenantId();
    int quotaYear = command.quotaYear() > 0 ? command.quotaYear() : Year.now().getValue();
    QuotaAccount existing =
        quotaAccountRepository.findAccount(
            command.insuredIdHash(),
            command.policyNo(),
            command.planId(),
            quotaYear,
            command.quotaType(),
            command.liabilityUuid() == null ? "" : command.liabilityUuid());
    if (existing != null) {
      return QuotaAccountView.from(existing);
    }
    QuotaAccount account =
        QuotaAccount.open(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            command.insuredIdHash(),
            command.policyNo(),
            command.planId(),
            quotaYear,
            command.quotaType(),
            command.liabilityUuid() == null ? "" : command.liabilityUuid(),
            command.quotaInitial());
    quotaAccountRepository.save(account);
    appendChangeLog(
        account,
        QuotaChangeLog.TYPE_INIT,
        BigDecimal.ZERO,
        account.initial(),
        command.operatorId(),
        command.operatorName(),
        command.remark());
    return QuotaAccountView.from(account);
  }

  /**
   * 调额（运营保全：改保额）。
   *
   * <p><b>下调的下界约束由领域层强制</b>（{@link QuotaAccount#adjustInitial}）：新初始额度不得低于 {@code claimed +
   * frozen}。理由与该方法的 javadoc 一致：已赔金额已超新保额时，是否追回是 <b>理赔决定</b>而非配置决定，系统无权在此处默默放行（否则 available
   * 变负、后续占额全被守恒拦下）。
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public QuotaAccountView adjustInitial(AdjustQuota command) {
    QuotaAccount account = requireAccount(command.quotaAccountId());
    ClaimConfigApplicationService.assertEditableVersion(
        "额度账户", account.getVersion(), command.version());
    BigDecimal before = account.adjustInitial(command.newInitial());
    quotaAccountRepository.save(account);
    appendChangeLog(
        account,
        QuotaChangeLog.TYPE_ADJUST,
        before,
        account.initial(),
        command.operatorId(),
        command.operatorName(),
        command.remark());
    return QuotaAccountView.from(account);
  }

  /** 减人（该被保人不再参与额度累计）。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public QuotaAccountView removeInsured(AdjustQuota command) {
    QuotaAccount account = requireAccount(command.quotaAccountId());
    ClaimConfigApplicationService.assertEditableVersion(
        "额度账户", account.getVersion(), command.version());
    BigDecimal before = account.initial();
    account.markRemoved();
    quotaAccountRepository.save(account);
    appendChangeLog(
        account,
        QuotaChangeLog.TYPE_REMOVE,
        before,
        account.initial(),
        command.operatorId(),
        command.operatorName(),
        command.remark());
    return QuotaAccountView.from(account);
  }

  /** 恢复（撤销减人，重新参与额度累计）。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public QuotaAccountView restoreInsured(AdjustQuota command) {
    QuotaAccount account = requireAccount(command.quotaAccountId());
    ClaimConfigApplicationService.assertEditableVersion(
        "额度账户", account.getVersion(), command.version());
    BigDecimal before = account.initial();
    account.restore();
    quotaAccountRepository.save(account);
    appendChangeLog(
        account,
        QuotaChangeLog.TYPE_RESTORE,
        before,
        account.initial(),
        command.operatorId(),
        command.operatorName(),
        command.remark());
    return QuotaAccountView.from(account);
  }

  // ===================== 内部 =====================

  private QuotaAccount requireAccount(long quotaAccountId) {
    QuotaAccount account = quotaAccountRepository.findById(quotaAccountId);
    if (account == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, quotaAccountId);
    }
    return account;
  }

  private void appendChangeLog(
      QuotaAccount account,
      String changeType,
      BigDecimal before,
      BigDecimal after,
      String operatorId,
      String operatorName,
      String remark) {
    quotaChangeLogRepository.save(
        QuotaChangeLog.of(
            DistributedIdGenerator.generateLongId(),
            account.getTenantId(),
            account.getId(),
            account.getPolicyNo(),
            account.getInsuredIdHash(),
            changeType,
            before,
            after,
            operatorId,
            operatorName,
            remark,
            Instant.now()));
  }

  // ===================== 命令 / 视图 =====================

  /** 开户命令。 */
  public record OpenQuota(
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String quotaType,
      String liabilityUuid,
      BigDecimal quotaInitial,
      String operatorId,
      String operatorName,
      String remark) {}

  /** 调额/减人/恢复命令（共用一个载体：三者都改同一张账户、都需要留痕）。 */
  public record AdjustQuota(
      long quotaAccountId,
      BigDecimal newInitial,
      String operatorId,
      String operatorName,
      String remark,
      /** 乐观锁版本号（编辑提交时原样带回，用于并发冲突检测）；为空时放行以兼容非前端调用方。 */
      Long version) {}

  /** 额度账户视图（配置面读模型；含三值与可用额度）。 */
  public record QuotaAccountView(
      String quotaAccountId,
      String insuredIdHash,
      String policyNo,
      String planId,
      int quotaYear,
      String quotaType,
      String liabilityUuid,
      BigDecimal initial,
      BigDecimal claimed,
      BigDecimal frozen,
      BigDecimal available,
      BigDecimal deductibleUsed,
      BigDecimal capUsed,
      boolean removed,
      Instant updatedAt,
      /** 乐观锁版本号（编辑提交时原样带回，用于并发冲突检测）。 */
      Long version) {

    static QuotaAccountView from(QuotaAccount a) {
      return new QuotaAccountView(
          String.valueOf(a.getId()),
          a.getInsuredIdHash(),
          a.getPolicyNo(),
          String.valueOf(a.getPlanId()),
          a.getQuotaYear(),
          a.getQuotaType(),
          a.getLiabilityUuid(),
          a.getQuotaInitial(),
          a.getQuotaClaimed(),
          a.getQuotaFrozen(),
          a.available(),
          a.getDeductibleUsed(),
          a.getCapUsed(),
          a.isRemoved(),
          a.getUpdatedAt(),
          a.getVersion());
    }
  }

  /** 保全记录视图。 */
  public record QuotaChangeView(
      String id,
      String quotaAccountId,
      String policyNo,
      String insuredIdHash,
      String changeType,
      BigDecimal beforeInitial,
      BigDecimal afterInitial,
      String operatorId,
      String operatorName,
      String remark,
      Instant occurredAt) {

    static QuotaChangeView from(QuotaChangeLog c) {
      return new QuotaChangeView(
          String.valueOf(c.getId()),
          String.valueOf(c.getQuotaAccountId()),
          c.getPolicyNo(),
          c.getInsuredIdHash(),
          c.getChangeType(),
          c.getBeforeInitial(),
          c.getAfterInitial(),
          c.getOperatorId(),
          c.getOperatorName(),
          c.getRemark(),
          c.getOccurredAt());
    }
  }
}
