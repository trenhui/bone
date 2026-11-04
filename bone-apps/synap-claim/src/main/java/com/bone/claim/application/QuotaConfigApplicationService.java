package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.common.PageBounds;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaChangeLog;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.QuotaChangeLogRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
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
  private final ClaimPlanRepository claimPlanRepository;
  private final ClaimPolicyRepository claimPolicyRepository;
  private final TenantPort tenantPort;

  /**
   * 某保单下的额度账户（配置面列表：分页 + 被保人关键字 / 维度 / 减人状态筛选）。
   *
   * <p><b>为什么分页而不是全量返回</b>：团体险单个保单下可有成百上千个被保人账户（每人 × 每计划 × 每年度）， 全量返回会让列表页一次拉几 MB、渲染卡死；分页 +
   * 服务端筛选后，运营按人搜也无需把全表拉到浏览器再过滤。
   */
  @Transactional(readOnly = true)
  public PageResult<QuotaAccountView> listByPolicy(
      String policyNo, String keyword, String quotaType, Boolean removed, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<QuotaAccount> records =
        quotaAccountRepository.findAccountsPage(
            tenantId, policyNo, keyword, quotaType, removed, bounds.page(), bounds.size());
    long total =
        quotaAccountRepository.countAccountsPage(tenantId, policyNo, keyword, quotaType, removed);
    return PageResult.of(
        records.stream().map(QuotaAccountView::from).toList(), total, bounds.page(), bounds.size());
  }

  /**
   * 同筛选条件的全量账户（CSV 导出用）。
   *
   * <p>与 {@link #listByPolicy} 共用 {@code accountListCriteria}：导出必须与列表页所见一致， 否则"页面 3 条、导出 300
   * 条"会让运营对数据失去信任。
   */
  @Transactional(readOnly = true)
  public List<QuotaAccountView> exportByPolicy(
      String policyNo, String keyword, String quotaType, Boolean removed) {
    long tenantId = tenantPort.currentTenantId();
    return quotaAccountRepository
        .findAccountsFiltered(tenantId, policyNo, keyword, quotaType, removed)
        .stream()
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

  /**
   * 公账汇总：把某保单下所有 LIABILITY_CAP（公账/责任额度池）账户按责任 UUID 聚合， 给运营一个"这项责任今年总共还能赔多少"的对账口径——逐条明细只能看单人占用，
   * 汇总才能发现"整个责任池快赔完了"这类池级风险。
   *
   * <p><b>为什么内存聚合而非 SQL GROUP BY</b>：责任账账户数 = 被保人数 × 责任数， 单保单量级在万级以内，一次性拉取后聚合足够；且 Criteria DSL 无
   * groupBy 表达， 为此引入原生 SQL 会破坏仓库层抽象（且与既有 findAccountsFiltered 口径不一致）。
   */
  @Transactional(readOnly = true)
  public List<PublicQuotaSummaryView> summarizePublic(String policyNo) {
    long tenantId = tenantPort.currentTenantId();
    List<QuotaAccount> accounts =
        quotaAccountRepository.findAccountsFiltered(
            tenantId, policyNo, null, "LIABILITY_CAP", null);
    return accounts.stream()
        .collect(java.util.stream.Collectors.groupingBy(QuotaAccount::getLiabilityUuid))
        .values()
        .stream()
        .map(
            group -> {
              QuotaAccount first = group.get(0);
              return new PublicQuotaSummaryView(
                  first.getLiabilityUuid(),
                  first.getPolicyNo(),
                  first.getPlanId(),
                  group.size(),
                  group.stream().filter(QuotaAccount::isRemoved).count(),
                  group.stream()
                      .map(QuotaAccount::getQuotaInitial)
                      .reduce(BigDecimal.ZERO, BigDecimal::add),
                  group.stream()
                      .map(QuotaAccount::getQuotaClaimed)
                      .reduce(BigDecimal.ZERO, BigDecimal::add),
                  group.stream()
                      .map(QuotaAccount::getQuotaFrozen)
                      .reduce(BigDecimal.ZERO, BigDecimal::add),
                  group.stream()
                      .map(QuotaAccount::available)
                      .reduce(BigDecimal.ZERO, BigDecimal::add));
            })
        .sorted(java.util.Comparator.comparing(PublicQuotaSummaryView::liabilityUuid))
        .toList();
  }

  /**
   * 责任发布联动：为该计划下所有已开户的个账（PERSONAL、未减人）被保人， 自动开立对应责任的 LIABILITY_CAP 公账账户（额度 = 责任的责任限额）。
   *
   * <p><b>为什么挂在发布动作上</b>：公账额度池的生命周期应跟随责任生效—— 人工逐个被保人初始化责任账既易漏配（漏的人理算时 {@code
   * CLAIM_QUOTA_NOT_FOUND}） 又易错配额度；发布是责任"开始起赔"的唯一入口，在这里联动一步到位。
   *
   * <p><b>幂等</b>：复用 openAccount 的同键幂等（已存在直接返回），重复发布/重发布新版本不会覆盖既有占用。
   *
   * <p><b>保单类型的口径差异（团体 / 个人）</b>：两者结构同构，差异在被保人数量——团体保单是多被保人共享，
   * 个人保单是"单人单账户"。故个人保单下一旦发现<b>多于一个</b>未减人的个人账户，就不是"要多开几个池"，
   * 而是<b>保单类型或被保人名单已经错了</b>：继续按人开池会把责任额度开到不存在的人头上（额度虚增、 理赔对不上人）。这种时候 fail-closed 报 {@code
   * SC_CLAIM_POLICY_TYPE_CONFLICT}，让人先去修数据， 而不是在这里替它"按现状多开几个"。
   *
   * @return 已覆盖的被保人账户数（含幂等命中既有账户）
   */
  @Transactional
  public int initPublicAccountsForLiability(
      long planId, String liabilityUuid, String sharedCode, java.math.BigDecimal initialLimit) {
    ClaimPlan plan = claimPlanRepository.findPlanById(planId);
    if (plan == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_PLAN_NOT_FOUND, planId);
    }
    String policyNo = plan.policyNo();
    List<QuotaAccount> personalAccounts =
        quotaAccountRepository.findAccountsByPolicyNo(policyNo).stream()
            .filter(
                a ->
                    a.getPlanId() == planId
                        && "PERSONAL".equals(a.getQuotaType())
                        && !a.isRemoved())
            .toList();
    assertPolicyTypeMatchesInsuredCount(policyNo, personalAccounts.size());
    int quotaYear = Year.now().getValue();
    for (QuotaAccount personal : personalAccounts) {
      if (QuotaAccount.isShared(sharedCode)) {
        // 共享责任：责任限额账走 SHARED_POOL 维度，按共保代码分行（liability_uuid 列存共保代码，与运行期路由键同源）
        openAccount(
            new OpenQuota(
                personal.getInsuredIdHash(),
                policyNo,
                planId,
                quotaYear,
                QuotaAccount.QUOTA_TYPE_SHARED_POOL,
                sharedCode,
                initialLimit == null ? BigDecimal.ZERO : initialLimit,
                "system",
                "共享池发布联动",
                "共享池发布自动初始化"));
      } else {
        openAccount(
            new OpenQuota(
                personal.getInsuredIdHash(),
                policyNo,
                planId,
                quotaYear,
                "LIABILITY_CAP",
                liabilityUuid,
                initialLimit == null ? BigDecimal.ZERO : initialLimit,
                "system",
                "责任发布联动",
                "责任发布自动初始化"));
      }
    }
    return personalAccounts.size();
  }

  /**
   * 保单类型 ↔ 被保人账户数一致性（责任发布前的闸门）。
   *
   * <p>个人保单 = 单人单账户，故其名下未减人的个人额度账户<b>最多一个</b>。多于一个只可能是：① 保单类型标错 （实为团单）；②
   * 被保人名单串到了别的保单。两种情况下继续按人开责任池，都会把额度开到不存在的人头上， 而这笔虚增额度在理赔时表现为"某不存在的被保人占着额度"，对账时极难回溯。
   *
   * <p>查不到保单时放行（{@code 配置面存量/测试数据} 可能没有保单主数据）：本闸门只用来挡"确定矛盾"， 不承担"保单必须存在"的校验——那是报案前置校验（{@code
   * findActivePolicy}）的职责，混在一起会让 责任发布因为一张历史保单缺失而整体不可用。
   */
  private void assertPolicyTypeMatchesInsuredCount(String policyNo, int insuredCount) {
    if (insuredCount <= 1) {
      return;
    }
    ClaimPolicy policy = claimPolicyRepository.findByPolicyNo(policyNo);
    if (policy == null || !policy.isIndividualPolicy()) {
      return;
    }
    throw ClaimErrors.of(
        ClaimErrorCodes.CLAIM_POLICY_TYPE_CONFLICT,
        "个人保单 "
            + policyNo
            + " 下存在 "
            + insuredCount
            + " 个未减人的个人额度账户，"
            + "与「单人单账户」矛盾：请核实保单类型（是否为团体保单）或被保人名单是否串单；"
            + "修好后重新发布责任即可自动开立责任额度池");
  }

  /**
   * 公账（责任额度池）汇总视图：按责任 UUID 聚合的池级口径。
   *
   * @param liabilityUuid 责任 UUID（理算按它占用/归还）
   * @param policyNo 保单号
   * @param planId 计划 ID
   * @param accountCount 账户数（= 覆盖的被保人数）
   * @param removedCount 已减人账户数
   * @param initialTotal 初始额度合计
   * @param claimedTotal 已扣减合计
   * @param frozenTotal 已冻结合计
   * @param availableTotal 可用额度合计
   */
  public record PublicQuotaSummaryView(
      String liabilityUuid,
      String policyNo,
      long planId,
      long accountCount,
      long removedCount,
      BigDecimal initialTotal,
      BigDecimal claimedTotal,
      BigDecimal frozenTotal,
      BigDecimal availableTotal) {}

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
    // 外键完整性：额度账户必须挂在**真实存在且属于本保单**的计划上。
    //
    // <p><b>为什么这是必需的一道闸</b>：额度账户自身（额度/占用/守恒）全对，只有"按计划查账户"或理算时
    // 才会发现计划为 null，且没有任何一条错误信息指向真因 ⇒ 孤儿账户极难排查。
    // 这条洞真实可达：雪花 ID 是 18~19 位、超出 JS Number 上限 2^53，调用方让 ID 经过一次 {@code Number()}
    // 往返末位就被静默截断，后端照单全收 ⇒ 账户挂到"一个不存在的计划"上，全程零报错。
    //
    // <p><b>为什么放在幂等分支之后</b>：已存在同键账户时直接返回（运营重复点"初始化"是正常操作），
    // 该路径不做计划存在性校验——否则计划已下线/删除的历史数据会让运营再也点不动"初始化"。
    requirePlanOfPolicy(command.planId(), command.policyNo());
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
    assertVersionSubmitted("额度账户", command.version());
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
    assertVersionSubmitted("额度账户", command.version());
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
    assertVersionSubmitted("额度账户", command.version());
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

  /**
   * 外键完整性闸：计划必须存在且属于该保单。
   *
   * <p>复用既有的 {@code CLAIM_PLAN_NOT_FOUND}，不新增错误码——语义上「该保单下没有这个计划」
   * 与「计划不存在」对调用方是同一件事（都是"你传的计划用不了"），拆两个码只会让前端多写一个分支。
   */
  private void requirePlanOfPolicy(long planId, String policyNo) {
    ClaimPlan plan = claimPlanRepository.findPlanById(planId);
    if (plan == null || !policyNo.equals(plan.policyNo())) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_PLAN_NOT_FOUND, planId);
    }
  }

  /**
   * 版本号必填校验（A3 资金配置防并发覆盖）。
   *
   * <p><b>为什么单独拦 null</b>：{@link ClaimConfigApplicationService#assertEditableVersion} 对 {@code
   * null} 提交版本采取「静默放行」语义（服务于保单/计划/险种等配置面的内部调用），但<b>额度账户是资金口径</b>，
   * 不带版本即等于放弃乐观锁、允许覆盖他人刚做的调额/减人——故在此显式拒绝。改配置必须带当前 {@code version}，由前端从详情接口取回后回填。
   */
  private static void assertVersionSubmitted(String kind, Long submittedVersion) {
    if (submittedVersion == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_CONCURRENT_CONFLICT, kind + "更新必须携带版本号，请刷新后重试");
    }
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
