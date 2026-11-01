package com.bone.claim.application;

import com.bone.claim.adapter.web.dto.request.LiabilityRequests;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.policy.ClaimCoverage;
import com.bone.claim.domain.model.policy.ClaimPlan;
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
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;
  private final QuotaConfigApplicationService quotaConfigApplicationService;

  /** 新建责任（DRAFT 草稿态，版本 v1）。 */
  @Transactional
  public LiabilityView createLiability(LiabilityRequests.CreateLiabilityReq command) {
    long tenantId = tenantPort.currentTenantId();
    assertCoverageUsable(command.planId(), command.coverageId());
    assertPercent(command.payPercent());
    AdjLiability liability =
        AdjLiability.of(
            DistributedIdGeneratorHolder.next(),
            tenantId,
            UUID.randomUUID().toString(),
            "v1",
            command.planId(),
            command.coverageId(),
            command.liabilityCode(),
            command.liabilityType(),
            toDecimal(command.payPercent()),
            toDecimal(command.liabilityDeduct()),
            toDecimal(command.liabilityLimit()),
            toDecimal(command.invoiceDeduct()),
            toDecimal(command.adjustDeduct()),
            toDecimal(command.dailyLimit()),
            command.dailyLimitBasis(),
            command.limitMode(),
            command.limitHospitalCodes(),
            command.limitDiagnosisCodes(),
            command.catalogPolicy(),
            command.catalogCode(),
            command.socialOnly(),
            command.waitingPeriod(),
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
    if (existing.isActive()) {
      // 已生效版本不可改：生成同 UUID 的新版本草稿（新行，无需乐观锁）
      AdjLiability next =
          AdjLiability.of(
              DistributedIdGeneratorHolder.next(),
              tenantPort.currentTenantId(),
              existing.getLiabilityUuid(),
              nextVersion(existing.getLiabilityVersion()),
              existing.getPlanId(),
              targetCoverageId,
              or(command.liabilityCode(), existing.getLiabilityCode()),
              or(command.liabilityType(), existing.getLiabilityType()),
              num(command.payPercent(), existing.getPayPercent()),
              num(command.liabilityDeduct(), existing.getLiabilityDeduct()),
              num(command.liabilityLimit(), existing.getLiabilityLimit()),
              num(command.invoiceDeduct(), existing.getInvoiceDeduct()),
              num(command.adjustDeduct(), existing.getAdjustDeduct()),
              num(command.dailyLimit(), existing.getDailyLimit()),
              or(command.dailyLimitBasis(), existing.getDailyLimitBasis()),
              or(command.limitMode(), existing.getLimitMode()),
              or(command.limitHospitalCodes(), existing.getLimitHospitalCodes()),
              or(command.limitDiagnosisCodes(), existing.getLimitDiagnosisCodes()),
              or(command.catalogPolicy(), existing.getCatalogPolicy()),
              or(command.catalogCode(), existing.getCatalogCode()),
              command.socialOnly() != null ? command.socialOnly() : existing.getSocialOnly(),
              command.waitingPeriod() != null
                  ? command.waitingPeriod()
                  : existing.getWaitingPeriod(),
              "DRAFT");
      liabilityRepository.save(next);
      domainEventPublisher.publishFrom(next);
      return LiabilityView.from(next);
    }
    // DRAFT 原地编辑：保留 SDK 管理的 version（乐观锁），并发编辑冲突由框架抛异常→控制器转 409
    existing.applyEdit(
        or(command.liabilityCode(), existing.getLiabilityCode()),
        or(command.liabilityType(), existing.getLiabilityType()),
        num(command.payPercent(), existing.getPayPercent()),
        num(command.liabilityDeduct(), existing.getLiabilityDeduct()),
        num(command.liabilityLimit(), existing.getLiabilityLimit()),
        num(command.invoiceDeduct(), existing.getInvoiceDeduct()),
        num(command.adjustDeduct(), existing.getAdjustDeduct()),
        num(command.dailyLimit(), existing.getDailyLimit()),
        or(command.dailyLimitBasis(), existing.getDailyLimitBasis()),
        or(command.limitMode(), existing.getLimitMode()),
        or(command.limitHospitalCodes(), existing.getLimitHospitalCodes()),
        or(command.limitDiagnosisCodes(), existing.getLimitDiagnosisCodes()),
        or(command.catalogPolicy(), existing.getCatalogPolicy()),
        or(command.catalogCode(), existing.getCatalogCode()),
        command.socialOnly() != null ? command.socialOnly() : existing.getSocialOnly(),
        command.waitingPeriod() != null ? command.waitingPeriod() : existing.getWaitingPeriod(),
        targetCoverageId);
    liabilityRepository.save(existing);
    domainEventPublisher.publishFrom(existing);
    return LiabilityView.from(existing);
  }

  /** 发布责任（DRAFT → ACTIVE）：同时将该 UUID 旧的 ACTIVE 版本置为 RETIRED，保证唯一生效版本。 */
  @Transactional
  public LiabilityView publishLiability(long liabilityId) {
    AdjLiability liability = requireLiability(liabilityId);
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
        liability.getPlanId(), liability.getLiabilityUuid(), liability.getLiabilityLimit());
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
      String status) {

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
          l.getStatus());
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
