package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.dto.request.LiabilityRequests.CreateLiabilityReq;
import com.bone.claim.adapter.web.dto.request.LiabilityRequests.UpdateLiabilityReq;
import com.bone.claim.application.LiabilityConfigApplicationService.LiabilityView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.policy.ClaimCoverage;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 责任 ↔ 保障（四级建模）的三道闸门。
 *
 * <p><b>为什么需要这组用例</b>：{@code t_adj_liability.coverage_id} 是<b>跨聚合弱引用、库中无外键</b>，
 * 三条错配路径在配置面上都不会报错，只在理算/额度池里炸开：
 *
 * <ol>
 *   <li>指向<b>别的计划</b>的保障 ⇒ 责任被另一计划的险种额度约束（"配了错生效"）；
 *   <li>指向<b>已下线</b>保障 ⇒ 险种不可再引用，责任却仍是 ACTIVE；
 *   <li>指向<b>不存在</b>的保障 ⇒ 四级树里这条责任永远不显示，运营侧读作"配置丢失"。
 * </ol>
 *
 * <p>另加两条配套断言：<b>计划的级责任（coverageId=null）必须放行</b>（历史未分级数据与"只配到计划级" 的产品都靠它表达），其视图字段必须是 {@code null}
 * 而非字面量 {@code "null"}——后者会让前端把它当成 一个真实 ID 回传，随后被 {@code Long} 解析失败打成 400。
 *
 * <p>每条校验都配了正向对照（{@link #create_withValidCoverage_persistsCoverageId()}），避免把"永远拒绝" 误读成"守卫有效"。
 */
@ExtendWith(MockitoExtension.class)
class LiabilityCoverageGuardTest {

  private static final long PLAN_ID = 1001L;
  private static final long OTHER_PLAN_ID = 2002L;
  private static final long COVERAGE_ID = 3003L;

  @Mock LiabilityRepository liabilityRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock TenantPort tenantPort;
  @Mock DomainEventPublisher domainEventPublisher;
  @Mock QuotaConfigApplicationService quotaConfigApplicationService;

  @InjectMocks LiabilityConfigApplicationService service;

  /**
   * 启用联动（0094）的放行夹具：责任写入口会先校验所属保单的责任规则开关为「启用中」。 用 {@code lenient} 是因为个别用例（如聚合反向契约）不经过服务入口，stub
   * 不会被消费。
   */
  @BeforeEach
  void stubPolicyRuleEnabled() {
    ClaimPlan plan =
        ClaimPlan.draft(PLAN_ID, 1L, "POL-1", "PLAN-1", "门诊计划", BigDecimal.valueOf(1000), 15, "v1");
    lenient().when(claimPlanRepository.findPlanById(anyLong())).thenReturn(plan);
    ClaimPolicy policy =
        ClaimPolicy.open(
            9001L,
            1L,
            "POL-1",
            "保单A",
            "保司",
            "分公司",
            "投保方",
            "BIZ-1",
            "OUTPATIENT",
            "GROUP",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T00:00:00Z"),
            30,
            "v1",
            "[\"h1\"]",
            Instant.parse("2026-01-01T00:00:00Z"));
    policy.activate("tester");
    lenient().when(claimPolicyRepository.findByPolicyNo(any())).thenReturn(policy);
  }

  /**
   * 模拟"库里的那一行"：M2 ② Shadow 的服务入口在保存后会回读并校验 {@code scalar == project(json)}，纯 Mock 的仓储没有这条路径，故用 save
   * → map、findById → map 还原"真的落过库"。
   *
   * <p>用 {@code lenient} 是因为拒绝类用例（校验先于保存失败）根本走不到 save，属于有意不用。
   */
  @BeforeEach
  void simulatePersistence() {
    java.util.Map<Long, AdjLiability> rows = new java.util.HashMap<>();
    lenient()
        .when(liabilityRepository.save(any()))
        .thenAnswer(
            inv -> {
              AdjLiability saved = inv.getArgument(0);
              rows.put(saved.getId(), saved);
              return saved.getId();
            });
    lenient()
        .when(liabilityRepository.findById(anyLong()))
        .thenAnswer(inv -> rows.get(inv.getArgument(0)));
  }

  // ===================== 保障归属三道闸门 =====================

  @Test
  void create_coverageFromAnotherPlan_isRejected() {
    when(claimCoverageRepository.findCoverageById(COVERAGE_ID))
        .thenReturn(coverageOf(OTHER_PLAN_ID, "C01", false));

    assertThatThrownBy(() -> service.createLiability(createReq(COVERAGE_ID)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不属于该计划");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_retiredCoverage_isRejected() {
    when(claimCoverageRepository.findCoverageById(COVERAGE_ID))
        .thenReturn(coverageOf(PLAN_ID, "C01", true));

    assertThatThrownBy(() -> service.createLiability(createReq(COVERAGE_ID)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("已下线");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_unknownCoverage_isRejected() {
    when(claimCoverageRepository.findCoverageById(COVERAGE_ID)).thenReturn(null);

    assertThatThrownBy(() -> service.createLiability(createReq(COVERAGE_ID)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("保障不存在");

    verify(liabilityRepository, never()).save(any());
  }

  // ===================== 正向对照：守卫不能"一律拒绝" =====================

  @Test
  void create_withValidCoverage_persistsCoverageId() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(claimCoverageRepository.findCoverageById(COVERAGE_ID))
        .thenReturn(coverageOf(PLAN_ID, "C01", false));

    LiabilityView view = service.createLiability(createReq(COVERAGE_ID));

    ArgumentCaptor<AdjLiability> saved = ArgumentCaptor.forClass(AdjLiability.class);
    verify(liabilityRepository).save(saved.capture());
    assertThat(saved.getValue().getCoverageId()).isEqualTo(COVERAGE_ID);
    assertThat(view.coverageId()).isEqualTo(String.valueOf(COVERAGE_ID));
  }

  // ===================== 计划级责任：放行 + 视图不得出现 "null" =====================

  @Test
  void create_withoutCoverage_staysPlanLevelAndViewIsNull() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    LiabilityView view = service.createLiability(createReq(null));

    ArgumentCaptor<AdjLiability> saved = ArgumentCaptor.forClass(AdjLiability.class);
    verify(liabilityRepository).save(saved.capture());
    assertThat(saved.getValue().getCoverageId()).isNull();
    // 关键：null 不能被 String.valueOf 变成字面量 "null"
    assertThat(view.coverageId()).isNull();
    verify(claimCoverageRepository, never()).findCoverageById(anyLong());
  }

  // ===================== 解除归属：显式 detach 才能回退到方案级 =====================

  @Test
  void update_detachCoverage_returnsToPlanLevel() {
    AdjLiability existing = liability(BigDecimal.valueOf(90));
    when(liabilityRepository.findById(1L)).thenReturn(existing);

    LiabilityView view = service.updateLiability(1L, detachReq());

    assertThat(existing.getCoverageId()).isNull();
    assertThat(view.coverageId()).isNull();
  }

  @Test
  void update_withoutDetachCoverageFlag_keepsExistingCoverage() {
    AdjLiability existing = liability(BigDecimal.valueOf(90));
    when(liabilityRepository.findById(1L)).thenReturn(existing);

    service.updateLiability(1L, plainUpdateReq());

    assertThat(existing.getCoverageId()).isEqualTo(COVERAGE_ID);
  }

  // ===================== 赔付比例：只拦写入，不收紧聚合 =====================

  @Test
  void create_payPercentAbove100_isRejectedOnWrite() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(createReq(null, "120")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("0-100");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_payPercentNegative_isRejectedOnWrite() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(createReq(null, "-1")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("0-100");
  }

  @Test
  void create_garbagePercent_isRejectedNotSilentlyZeroed() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(createReq(null, "9O")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不是合法数值");
  }

  @Test
  void create_boundaryPercents_areAccepted() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThat(service.createLiability(createReq(null, "0")).payPercent()).isEqualTo("0");
    assertThat(service.createLiability(createReq(null, "100")).payPercent()).isEqualTo("100");
  }

  /**
   * <b>反向契约：聚合必须继续承载 &gt;100% 的比例。</b>
   *
   * <p>存量导入里存在超比例数据，理赔侧靠 {@code EngineFlags#clampRatioAt100} 把它夹到 100% 来防超赔（{@code
   * SettlementGuardContractTest#clampRatioAt100_neverOverpays} 用 150% 专门验证 这条护栏）。若有人把 0–100 的断言塞回
   * {@link AdjLiability}，那条护栏会连夹具都造不出来—— 本用例就是那个改动的绊线。
   */
  @Test
  void domain_toleratesAbove100Percent_soSettlementClampStaysTestable() {
    assertThat(liability(BigDecimal.valueOf(150)).getPayPercent()).isEqualByComparingTo("150");
  }

  // ===================== 夹具 =====================

  private static ClaimCoverage coverageOf(long planId, String code, boolean retired) {
    ClaimCoverage coverage =
        ClaimCoverage.draft(
            COVERAGE_ID, 1L, planId, code, "险种" + code, BigDecimal.ZERO, "REIMBURSEMENT");
    if (retired) {
      coverage.retire("tester");
    }
    return coverage;
  }

  private static CreateLiabilityReq createReq(Long coverageId) {
    return createReq(coverageId, "90");
  }

  private static CreateLiabilityReq createReq(Long coverageId, String payPercent) {
    return new CreateLiabilityReq(
        PLAN_ID,
        coverageId,
        "R01",
        null,
        payPercent,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /** 只带 detachCoverage=true 的编辑请求（其余字段全部沿用原值）。 */
  private static UpdateLiabilityReq detachReq() {
    return updateReq(true);
  }

  /** 不带 detach 的编辑请求：coverageId 与 detachCoverage 都为空 ⇒ 归属必须保持原样。 */
  private static UpdateLiabilityReq plainUpdateReq() {
    return updateReq(null);
  }

  private static UpdateLiabilityReq updateReq(Boolean detachCoverage) {
    return new UpdateLiabilityReq(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        detachCoverage);
  }

  /** 走 18 参最终构造器（含 coverageId），用来单测比例不变量。 */
  private static AdjLiability liability(BigDecimal payPercent) {
    return AdjLiability.of(
        1L,
        1L,
        "uuid-1",
        "v1",
        PLAN_ID,
        COVERAGE_ID,
        "R01",
        "REIMBURSEMENT",
        payPercent,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "NONE",
        1,
        0,
        "DRAFT");
  }
}
