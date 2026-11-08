package com.bone.claim.application;

import com.bone.claim.adapter.web.dto.request.LiabilityRequests;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.policy.ClaimCoverage;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 责任配置应用服务（A4 配置端）。
 *
 * <p><b>版本化不变式（§8.2「已生效版本不可改」）</b>：已生效（ACTIVE）的责任<b>不可原地修改</b>， 对其编辑会生成<b>同 {@code liabilityUuid}
 * 的新版本</b>（DRAFT），旧版本冻结保留；发布新版本时自动将同 UUID 的旧 ACTIVE 版本置为 RETIRED，保证每个 UUID
 * 同时只有一个生效版本。草稿（DRAFT）可原地编辑。
 *
 * <p><b>与既有设计的关系</b>：此前 {@link LiabilityRepository} 约定"只读、配置由外部导入"；本服务在不破坏 读模型（理算）的前提下补上运营侧写入口（逐条维护
 * / 批量导入），属于该约定的有意演进。
 */
@Service
@RequiredArgsConstructor
public class LiabilityConfigApplicationService {

  private final LiabilityRepository liabilityRepository;
  private final ClaimCoverageRepository claimCoverageRepository;
  private final com.bone.claim.domain.repository.ClaimPlanRepository claimPlanRepository;
  private final com.bone.claim.domain.repository.ClaimPolicyRepository claimPolicyRepository;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;
  private final QuotaConfigApplicationService quotaConfigApplicationService;

  /** 新建责任（DRAFT 草稿态，版本 v1）。 */
  @Transactional
  public LiabilityView createLiability(LiabilityRequests.CreateLiabilityReq command) {
    long tenantId = tenantPort.currentTenantId();
    assertPolicyRuleEnabled(command.planId());
    assertCoverageUsable(command.planId(), command.coverageId());
    assertPercent(command.payPercent());
    RuleValues values = RuleValues.ofCreate(command);
    assertRuleUsable(values);
    assertCatalogMandatoryConfirmed(values.catalogPolicy(), command.confirmCatalogMandatory());
    AdjLiability liability =
        AdjLiability.ofWithShared(
            DistributedIdGeneratorHolder.next(),
            tenantId,
            UUID.randomUUID().toString(),
            "v1",
            command.planId(),
            command.coverageId(),
            values.liabilityCode(),
            values.liabilityType(),
            values.payPercent(),
            values.liabilityDeduct(),
            values.liabilityLimit(),
            values.invoiceDeduct(),
            values.adjustDeduct(),
            values.dailyLimit(),
            values.dailyLimitBasis(),
            values.limitMode(),
            values.limitHospitalCodes(),
            values.limitDiagnosisCodes(),
            values.catalogPolicy(),
            values.catalogCode(),
            values.socialOnly(),
            values.waitingPeriod(),
            command.sharedCode(),
            command.priorLiabilityUuid(),
            command.status() == null || command.status().isBlank() ? "DRAFT" : command.status());
    liabilityRepository.save(liability);
    domainEventPublisher.publishFrom(liability);
    return LiabilityView.from(liability);
  }

  /** 编辑责任：ACTIVE 生成同 UUID 新版本（DRAFT），DRAFT 原地编辑。空字段沿用原值。 */
  @Transactional
  public LiabilityView updateLiability(
      long liabilityId, LiabilityRequests.UpdateLiabilityReq command) {
    AdjLiability existing = requireLiability(liabilityId);
    // 启用联动（0094）：保单责任规则未启用（INACTIVE/PAUSE）时禁止编辑（含生成新版本）
    assertPolicyRuleEnabled(existing.getPlanId());
    // 目标保障：显式 detach 优先（退回方案级），否则"传了就改、没传沿用原值"
    Long targetCoverageId =
        Boolean.TRUE.equals(command.detachCoverage())
            ? null
            : (command.coverageId() != null ? command.coverageId() : existing.getCoverageId());
    // 仅在"改挂到另一个保障"时校验：已挂好的保障后续被下线，不应该连累既有责任的其它字段编辑
    if (command.coverageId() != null && !command.coverageId().equals(existing.getCoverageId())) {
      assertCoverageUsable(existing.getPlanId(), command.coverageId());
    }
    assertPercent(command.payPercent());
    // 生效值只算一次：校验与落库读同一份，否则会出现「校验看的是合并值、落库存的是命令值」的两套口径。
    RuleValues values = RuleValues.merge(existing, command);
    assertRuleUsable(values);
    assertCatalogMandatoryConfirmed(values.catalogPolicy(), command.confirmCatalogMandatory());
    if (existing.isActive()) {
      // 已生效版本不可改：生成同 UUID 的新版本草稿（新行，无需乐观锁）
      // 共享关系（共保代码 / 前序责任）：命令显式传了就用命令值，否则沿用原值（与 RuleValues.merge 的空字段沿用原值一致）
      String sharedCode =
          command.sharedCode() != null ? command.sharedCode() : existing.getSharedCode();
      String priorLiabilityUuid =
          command.priorLiabilityUuid() != null
              ? command.priorLiabilityUuid()
              : existing.getPriorLiabilityUuid();
      AdjLiability next =
          AdjLiability.ofWithShared(
              DistributedIdGeneratorHolder.next(),
              tenantPort.currentTenantId(),
              existing.getLiabilityUuid(),
              nextVersion(existing.getLiabilityVersion()),
              existing.getPlanId(),
              targetCoverageId,
              values.liabilityCode(),
              values.liabilityType(),
              values.payPercent(),
              values.liabilityDeduct(),
              values.liabilityLimit(),
              values.invoiceDeduct(),
              values.adjustDeduct(),
              values.dailyLimit(),
              values.dailyLimitBasis(),
              values.limitMode(),
              values.limitHospitalCodes(),
              values.limitDiagnosisCodes(),
              values.catalogPolicy(),
              values.catalogCode(),
              values.socialOnly(),
              values.waitingPeriod(),
              sharedCode,
              priorLiabilityUuid,
              "DRAFT");
      liabilityRepository.save(next);
      domainEventPublisher.publishFrom(next);
      return LiabilityView.from(next);
    }
    // DRAFT 原地编辑：保留 SDK 管理的 version（乐观锁），并发编辑冲突由框架抛异常→控制器转 409
    existing.applyEdit(
        values.liabilityCode(),
        values.liabilityType(),
        values.payPercent(),
        values.liabilityDeduct(),
        values.liabilityLimit(),
        values.invoiceDeduct(),
        values.adjustDeduct(),
        values.dailyLimit(),
        values.dailyLimitBasis(),
        values.limitMode(),
        values.limitHospitalCodes(),
        values.limitDiagnosisCodes(),
        values.catalogPolicy(),
        values.catalogCode(),
        values.socialOnly(),
        values.waitingPeriod(),
        targetCoverageId);
    liabilityRepository.save(existing);
    domainEventPublisher.publishFrom(existing);
    return LiabilityView.from(existing);
  }

  /** 发布责任（DRAFT → ACTIVE）：同时将该 UUID 旧的 ACTIVE 版本置为 RETIRED，保证唯一生效版本。 */
  @Transactional
  public LiabilityView publishLiability(long liabilityId) {
    AdjLiability liability = requireLiability(liabilityId);
    // 启用联动（0094）：发布是规则"生效"动作，保单规则开关未启用时发布无从生效
    assertPolicyRuleEnabled(liability.getPlanId());
    if ("RETIRED".equals(liability.getStatus())) {
      throw new DomainException("责任已下线，不可发布");
    }
    AdjLiability oldActive = liabilityRepository.findActiveByUuid(liability.getLiabilityUuid());
    if (oldActive != null && !oldActive.getId().equals(liability.getId())) {
      oldActive.retire();
      liabilityRepository.save(oldActive);
      domainEventPublisher.publishFrom(oldActive);
    }
    liability.publish();
    liabilityRepository.save(liability);
    domainEventPublisher.publishFrom(liability);
    // 公账联动：责任生效即为该计划下已开户被保人开立责任额度池（幂等，不会覆盖既有占用）。
    // 漏开的后果是理算时 CLAIM_QUOTA_NOT_FOUND——把这道初始化挂在发布唯一入口上，
    // 避免运营逐人手工开公账。
    quotaConfigApplicationService.initPublicAccountsForLiability(
        liability.getPlanId(),
        liability.getLiabilityUuid(),
        liability.getSharedCode(),
        liability.getLiabilityLimit());
    return LiabilityView.from(liability);
  }

  /** 下线责任（ACTIVE/DRAFT → RETIRED）。 */
  @Transactional
  public LiabilityView retireLiability(long liabilityId) {
    AdjLiability liability = requireLiability(liabilityId);
    liability.retire();
    liabilityRepository.save(liability);
    domainEventPublisher.publishFrom(liability);
    return LiabilityView.from(liability);
  }

  /** 按计划列出全部责任（含各版本，配置面树形展示用）。 */
  @Transactional(readOnly = true)
  public List<LiabilityView> listByPlan(long planId) {
    return liabilityRepository.findByPlanId(planId).stream().map(LiabilityView::from).toList();
  }

  // ===================== 规则自洽校验 =====================

  /**
   * 启用联动校验（0094）：责任所属保单的责任规则开关必须是「启用中」。
   *
   * <p><b>为什么拦在配置写入期</b>：规则开关（{@code t_claim_policy.config_status}）是责任真正"生效"的
   * 总闸——未启用的保单其责任不参与理算受理链路。 允许在未启用保单下新建/编辑/发布责任，运营会误以为"配置已生效"，实际所有赔案都被前置校验拒绝； 把这道检查挂在
   * create/update/publish 三个写入口，与前端禁用态（Alert 提示）共同保证"配置面看不见未启用保单的可写动作"。
   */
  private void assertPolicyRuleEnabled(Long planId) {
    if (planId == null) {
      return;
    }
    ClaimPlan plan = claimPlanRepository.findPlanById(planId);
    if (plan == null) {
      throw new DomainException("计划不存在: " + planId);
    }
    ClaimPolicy policy = claimPolicyRepository.findByPolicyNo(plan.policyNo());
    if (policy == null) {
      throw new DomainException("保单不存在: " + plan.policyNo());
    }
    if (!policy.isConfigActive()) {
      throw new DomainException(
          "保单「" + plan.policyNo() + "」的责任规则未启用（当前：" + policy.configStatus() + "），请先在保单管理启用后再配置责任");
    }
  }

  /**
   * 责任配置的<b>生效值</b>：新建时等于命令值，编辑时等于「命令值覆盖既有值」后的合并结果。
   *
   * <p><b>为什么必须先算出这一份再校验</b>：编辑接口的语义是「空字段沿用原值」，只看命令本身会漏掉 最危险的一种半配置——库里已有 {@code
   * daily_limit=500}、本次只提交 {@code daily_limit_basis=NONE}，
   * 单看命令两边"各有一个值"都不算矛盾，合并后却是"配了金额没配口径"。只有对合并结果求值， 校验与落库才是同一份事实。
   */
  private record RuleValues(
      String liabilityCode,
      String liabilityType,
      BigDecimal payPercent,
      BigDecimal liabilityDeduct,
      BigDecimal liabilityLimit,
      BigDecimal invoiceDeduct,
      BigDecimal adjustDeduct,
      BigDecimal dailyLimit,
      String dailyLimitBasis,
      String limitMode,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String catalogPolicy,
      String catalogCode,
      Integer socialOnly,
      Integer waitingPeriod) {

    static RuleValues ofCreate(LiabilityRequests.CreateLiabilityReq c) {
      return new RuleValues(
          c.liabilityCode(),
          c.liabilityType(),
          toDecimal(c.payPercent()),
          toDecimal(c.liabilityDeduct()),
          toDecimal(c.liabilityLimit()),
          toDecimal(c.invoiceDeduct()),
          toDecimal(c.adjustDeduct()),
          toDecimal(c.dailyLimit()),
          c.dailyLimitBasis(),
          c.limitMode(),
          c.limitHospitalCodes(),
          c.limitDiagnosisCodes(),
          c.catalogPolicy(),
          c.catalogCode(),
          c.socialOnly(),
          c.waitingPeriod());
    }

    static RuleValues merge(AdjLiability e, LiabilityRequests.UpdateLiabilityReq c) {
      return new RuleValues(
          or(c.liabilityCode(), e.getLiabilityCode()),
          or(c.liabilityType(), e.getLiabilityType()),
          num(c.payPercent(), e.getPayPercent()),
          num(c.liabilityDeduct(), e.getLiabilityDeduct()),
          num(c.liabilityLimit(), e.getLiabilityLimit()),
          num(c.invoiceDeduct(), e.getInvoiceDeduct()),
          num(c.adjustDeduct(), e.getAdjustDeduct()),
          num(c.dailyLimit(), e.getDailyLimit()),
          or(c.dailyLimitBasis(), e.getDailyLimitBasis()),
          or(c.limitMode(), e.getLimitMode()),
          or(c.limitHospitalCodes(), e.getLimitHospitalCodes()),
          or(c.limitDiagnosisCodes(), e.getLimitDiagnosisCodes()),
          or(c.catalogPolicy(), e.getCatalogPolicy()),
          or(c.catalogCode(), e.getCatalogCode()),
          c.socialOnly() != null ? c.socialOnly() : e.getSocialOnly(),
          c.waitingPeriod() != null ? c.waitingPeriod() : e.getWaitingPeriod());
    }
  }

  /**
   * 配置写入期的规则自洽校验（把「配了不生效」拦在保存时，而不是等客户来报销）。
   *
   * <p><b>它防的是什么</b>：日限额配了金额没配口径、开了白/黑名单却没给名单、目录编码与准入策略只配一边—— 这些形态在库里是合法数据、在页面上看着"已配置"，但理算走到 {@code
   * LiabilityRule#validate()} 就整类赔案 算不出钱。而配置错的代价与发现时点强相关：越晚发现，受影响的赔案越多，且运营完全不知道是自己配错了。
   *
   * <p><b>判定逻辑不新写一份</b>：装配出真正的 {@link LiabilityRule} 后调 {@link
   * LiabilityRule#validateConfigShape()}——与理算期同一段代码。若这里另写一套，会出现"配置面放行、理算再拒"
   * 的两套判据，运营看着保存成功的配置却永远赔不出钱。
   *
   * <p><b>为什么不调 {@code validate()}</b>：那会连带拒绝定额给付 / 津贴给付型。那两种责任<b>允许建档</b>
   * （业务必须能登记），只是不参与自动理算——由配置面的「可自动理算」提示告知运营，而不是禁止录入。
   */
  private static void assertRuleUsable(RuleValues v) {
    assertNonNegative("年度免赔额", v.liabilityDeduct());
    assertNonNegative("发票级免赔", v.invoiceDeduct());
    assertNonNegative("理算级免赔", v.adjustDeduct());
    assertNonNegative("日赔付限额", v.dailyLimit());
    // 责任额度上限：负数必须拒。
    //
    // 为什么单独一条、而不是"沿用负数=不封顶"的老口径：负数额度在理算期被解释为**不封顶**
    // （见 LiabilityRule#liabilityUncapped），也就是"运营手滑写错一个数 ⇒ 该责任静默变成
    // 无限额赔付"——它不是"少扣钱"，而是**赔得更多**，风险方向与免赔/日限额相反。
    //
    // 三条依据：
    // ① reference-impl S25 的原文是「额度 **0** 表示不限制」（见 AdjustmentDualTrackFixture
    //    场景 S25-零额度不封顶），负数从未被定义过——老注释"负数按不封顶解释"是 Bone 的自行扩展；
    // ② 同域的三个同类字段早已一致地拒绝负数（计划额度 ClaimPlan / 险种额度 ClaimCoverage /
    //    额度账户 QuotaAccount），责任额度是唯一漏网的；
    // ③ 「不封顶」有 0 与不填两种明确表达，负数不承载任何额外信息 ⇒ 属隐式哨兵反模式。
    //
    // 放行 0 与不填（null ⇒ toDecimal → ZERO）：那是"不封顶"的唯一合法写法，不能一并拦掉
    // （否则运营无路可走，只能乱填）。错误消息要写清后果与正确写法，便于运营自查。
    assertNonNegative("责任额度上限", v.liabilityLimit(), "负数会被按「不封顶」解释，等于取消限额；不封顶请填 0 或留空");
    //
    // 日限额的「金额 ↔ 口径」在这里<b>按原始值</b>判一次：装配器 {@code LiabilityRule.fromLegacy} 在
    // {@code dailyLimit <= 0} 时会把口径直接置回 NONE（它把"没有金额"当作"不启用日限额"），
    // 于是"只配了口径没配金额"这一半配置<b>在域层看不出来</b>。这正是必须按原始值判的原因——
    // 交给域校验就等于放它进库，然后理算时静默按"不限额"赔付。
    boolean hasDailyLimit = v.dailyLimit() != null && v.dailyLimit().signum() > 0;
    boolean hasDailyBasis =
        v.dailyLimitBasis() != null
            && !v.dailyLimitBasis().isBlank()
            && !"NONE".equalsIgnoreCase(v.dailyLimitBasis().trim());
    if (hasDailyLimit && !hasDailyBasis) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT,
          "配置了日限额 "
              + v.dailyLimit().toPlainString()
              + " 但未配「日」的口径（SAME_DAY_INVOICE 按同日发票 / SAME_DAY_CLAIM 按同日赔案）："
              + "窗口日判不出来，日限额无法执行");
    }
    if (!hasDailyLimit && hasDailyBasis) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT,
          "配置了日限额口径 " + v.dailyLimitBasis() + " 但日限额为 0：该口径永远不会生效（配了不生效 ⇒ 超赔）");
    }
    try {
      // 目录准入：策略与编码必须同配或同不配。CatalogAdmission.of 还要基准日与逐条结论（配置面拿不到），
      // 故这里只跑它对"策略 ↔ 编码"这一对的反向一致性口径。
      CatalogAdmission.Mode mode = CatalogAdmission.Mode.fromCode(v.catalogPolicy());
      boolean hasCode = v.catalogCode() != null && !v.catalogCode().isBlank();
      if (mode == CatalogAdmission.Mode.NONE && hasCode) {
        throw ClaimErrors.of(
            ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT,
            "配置了目录编码「"
                + v.catalogCode().trim()
                + "」但准入策略为 NONE：该编码永远不会被查询"
                + "（配了不生效 ⇒ 超赔），要么把策略改为 MANDATORY，要么清空目录编码");
      }
      if (mode != CatalogAdmission.Mode.NONE && !hasCode) {
        throw ClaimErrors.of(
            ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT,
            "目录准入策略为 " + mode + " 但未填目录编码：不知道该查哪一版目录，理算只能退补件");
      }
      LiabilityRule rule =
          LiabilityRule.fromLegacy(
              v.liabilityCode() == null ? "" : v.liabilityCode(),
              "validate-only",
              v.liabilityType(),
              v.payPercent(),
              v.liabilityDeduct(),
              v.liabilityLimit(),
              v.socialOnly() == null || v.socialOnly() == 1,
              v.invoiceDeduct(),
              v.adjustDeduct(),
              v.dailyLimit(),
              LiabilityRule.DailyBasis.fromCode(v.dailyLimitBasis()),
              LiabilityRule.LimitedConditions.of(
                  LiabilityRule.LimitedConditions.Mode.fromCode(v.limitMode()),
                  v.limitHospitalCodes(),
                  v.limitDiagnosisCodes()));
      rule.validateConfigShape();
    } catch (RuleGap gap) {
      // 解析期拒绝（口径/模式写错）与形状拒绝（半配置）同一出口：都是"这条配置不能这么存"。
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT, gap.detail());
    }
  }

  /** 金额类配置非负校验（负数会被引擎按 0 静默处理 = 少扣钱）。 */
  private static void assertNonNegative(String label, BigDecimal value) {
    assertNonNegative(label, value, null);
  }

  /**
   * 金额类配置非负校验。
   *
   * @param consequence 可选的"负数会造成什么后果"说明；拼进错误消息，让运营知道该改成什么 而不是只知道"不合法"。原因见 {@link #assertRuleUsable}
   *     里对责任额度上限的论证。
   */
  private static void assertNonNegative(String label, BigDecimal value, String consequence) {
    if (value != null && value.signum() < 0) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT,
          label
              + "不能为负: "
              + value.toPlainString()
              + (consequence == null ? "" : "（" + consequence + "）"));
    }
  }

  // ===================== 内部 =====================

  private AdjLiability requireLiability(long id) {
    AdjLiability l = liabilityRepository.findById(id);
    if (l == null) {
      throw new DomainException("责任不存在: " + id);
    }
    return l;
  }

  /**
   * 保障归属校验：责任挂到保障上时，保障必须存在、属于同一计划、且未下线。
   *
   * <p><b>为什么必须在这里拦</b>：{@code t_adj_liability.coverage_id} 是跨聚合弱引用，库里没有外键。
   * 不拦的三种后果都不会在配置面暴露，只会在理算/额度池炸开：① 指向不存在的保障 ⇒ 四级树里 这条责任永远不显示（配置丢失观感）；② 指向<b>别的计划</b>的保障 ⇒
   * 该责任被别的计划的险种额度 约束，属于"配了错生效"；③ 挂到已下线保障 ⇒ 险种不可引用，责任却仍是 ACTIVE。
   *
   * <p>缺失保障（{@code coverageId == null}）是<b>合法</b>的：历史未分级数据与"只配到计划级"的产品 都靠它表达，故此处直接放行。
   */
  private void assertCoverageUsable(Long planId, Long coverageId) {
    if (coverageId == null) {
      return;
    }
    ClaimCoverage coverage = claimCoverageRepository.findCoverageById(coverageId);
    if (coverage == null) {
      throw new DomainException("保障不存在: " + coverageId);
    }
    if (planId != null && !planId.equals(coverage.planId())) {
      throw new DomainException("保障不属于该计划: coverageId=" + coverageId + ", planId=" + planId);
    }
    if (ClaimPlan.STATUS_RETIRED.equals(coverage.status())) {
      throw new DomainException("保障已下线，不可挂新责任: " + coverage.getCoverageCode());
    }
  }

  private static String nextVersion(String current) {
    if (current == null || current.isBlank()) {
      return "v1";
    }
    int idx = current.lastIndexOf('v');
    String numPart = idx >= 0 ? current.substring(idx + 1) : current;
    try {
      return "v" + (Integer.parseInt(numPart.trim()) + 1);
    } catch (NumberFormatException e) {
      return current + "-rev";
    }
  }

  private static String or(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /** 赔付比例上界（百分数）。 */
  private static final BigDecimal PERCENT_MAX = new BigDecimal("100");

  /**
   * 赔付比例写入校验：只允许 0–100 的百分数。
   *
   * <p><b>为什么拦在应用服务而不是聚合</b>：{@link com.bone.claim.domain.model.liability.AdjLiability}
   * 必须能承载"脏数据"——存量导入里存在 &gt;100% 的比例，理赔侧正是靠 {@code EngineFlags#clampRatioAt100} 把它夹到 100%
   * 来防超赔（{@code SettlementGuardContractTest} 用 150% 的配置专门验证这条护栏）。若在聚合里拒绝，那条防护连测试夹具都造不出来，等于把老数据的
   * 兜底能力一起删掉。故这里只拦<b>新的写入</b>，不改变聚合的容错语义。
   *
   * <p>空值放行：编辑接口的"不传 = 沿用原值"语义靠它。
   */
  /**
   * 目录准入 MANDATORY 二次确认（业务裁定 2026-11-08，决策 2 = B：允许配，但必须显式确认）。
   *
   * <p><b>为什么是确认位而不是直接禁止</b>：MANDATORY 本身是正确配置（目录接入后应当打开），直接禁止会把
   * 「还没接入」和「不该用」混为一谈。真正的风险不是值配错，而是<b>不清楚它的爆炸半径</b>——目录取不到时 每条明细都落 {@code UNKNOWN} → {@code
   * CATALOG_UNRESOLVED} → 整批案件转人工补件。要求调用方显式承认这个后果再落库。
   *
   * <p><b>校验的是合并后的生效值</b>：{@code RuleValues.merge} 已处理「空字段沿用原值」，因此编辑一条原本就是 MANDATORY 的责任时，即使本次没传
   * {@code catalogPolicy} 也必须带确认位——否则「改个赔付比例」就能绕过确认， 等于没做这个校验。
   */
  private static void assertCatalogMandatoryConfirmed(String catalogPolicy, Boolean confirmed) {
    boolean mandatory = catalogPolicy != null && "MANDATORY".equalsIgnoreCase(catalogPolicy.trim());
    if (!mandatory || Boolean.TRUE.equals(confirmed)) {
      return;
    }
    throw new DomainException(
        "目录准入策略设为 MANDATORY 需显式确认（confirmCatalogMandatory=true）："
            + "该策略要求每条费用明细都取得目录结论，目录数据未接入时全部明细判为 UNKNOWN、"
            + "落 CATALOG_UNRESOLVED 转人工补件——即整批案件退件。"
            + "请先确认目录已接入，再带确认位重试。");
  }

  private static void assertPercent(String payPercent) {
    if (payPercent == null || payPercent.isBlank()) {
      return;
    }
    BigDecimal value;
    try {
      value = new BigDecimal(payPercent.trim());
    } catch (NumberFormatException e) {
      throw new DomainException("赔付比例不是合法数值: " + payPercent);
    }
    if (value.signum() < 0 || value.compareTo(PERCENT_MAX) > 0) {
      throw new DomainException("赔付比例必须在 0-100 之间: " + payPercent);
    }
  }

  private static BigDecimal toDecimal(String s) {
    if (s == null || s.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      // 金融配置必须 fast-fail：脏值（如 "9O"、"abc"）不能再静默归零，否则会系统性少赔且无报错。
      // 空白/缺省仍按既有语义归 0（见 num() 与 createLiability 的默认分支）。
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT, "数值字段不是合法数字: \"" + s.trim() + "\"");
    }
  }

  private static BigDecimal num(String s, BigDecimal fallback) {
    if (s == null || s.isBlank()) {
      return fallback;
    }
    return toDecimal(s);
  }

  /** 雪花 ID 生成（避免在此类直接耦合 {@code DistributedIdGenerator} 静态工具，统一收口）。 */
  private static final class DistributedIdGeneratorHolder {
    private DistributedIdGeneratorHolder() {}

    static long next() {
      return com.bone.core.util.DistributedIdGenerator.generateLongId();
    }
  }

  /** 责任视图（配置面读模型；放在 application 层因其为给 adapter 消费的投影）。 */
  public record LiabilityView(
      String liabilityId,
      String liabilityUuid,
      String liabilityVersion,
      String planId,
      String coverageId,
      String liabilityCode,
      String liabilityType,
      String payPercent,
      String liabilityDeduct,
      String liabilityLimit,
      String invoiceDeduct,
      String adjustDeduct,
      String dailyLimit,
      String dailyLimitBasis,
      String limitMode,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String catalogPolicy,
      String catalogCode,
      Integer socialOnly,
      Integer waitingPeriod,
      String status,
      String sharedCode,
      String priorLiabilityUuid) {

    static LiabilityView from(AdjLiability l) {
      return new LiabilityView(
          String.valueOf(l.getId()),
          l.getLiabilityUuid(),
          l.getLiabilityVersion(),
          String.valueOf(l.getPlanId()),
          idOrNull(l.getCoverageId()),
          l.getLiabilityCode(),
          l.getLiabilityType(),
          plain(l.getPayPercent()),
          plain(l.getLiabilityDeduct()),
          plain(l.getLiabilityLimit()),
          plain(l.getInvoiceDeduct()),
          plain(l.getAdjustDeduct()),
          plain(l.getDailyLimit()),
          l.getDailyLimitBasis(),
          l.getLimitMode(),
          l.getLimitHospitalCodes(),
          l.getLimitDiagnosisCodes(),
          l.getCatalogPolicy(),
          l.getCatalogCode(),
          l.getSocialOnly(),
          l.getWaitingPeriod(),
          l.getStatus(),
          l.getSharedCode(),
          l.getPriorLiabilityUuid());
    }

    private static String plain(BigDecimal v) {
      return v == null ? "0" : v.toPlainString();
    }

    /**
     * 可空外键转字符串，<b>null 必须原样返回 null</b>。
     *
     * <p>不能用 {@code String.valueOf(...)}：它会把 null 变成字面量 {@code "null"}，前端据此 认为"该责任归属保障
     * null"，既无法归入「未分配保障」分组，编辑回传时还会被后端按 {@code Long} 解析失败而 400——一个只在"责任未挂保障"时才出现的假 ID。
     */
    private static String idOrNull(Long v) {
      return v == null ? null : String.valueOf(v);
    }
  }
}
