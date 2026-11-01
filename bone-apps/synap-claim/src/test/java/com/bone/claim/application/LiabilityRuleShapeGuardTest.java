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
import com.bone.core.exception.BizException;
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
 * 责任规则「半配置」的写入期闸门。
 *
 * <p><b>为什么这组用例存在</b>：日限额、适用限定、目录准入都是<b>成对</b>的配置（金额 + 口径 / 模式 + 名单 / 策略 +
 * 目录编码）。只配一边在库里是合法数据、在页面上显示"已配置"，但理算期会被 {@code LiabilityRule#validate()}
 * 整类拒绝——后果不是某个字段错，而是<b>这类赔案全都算不出钱</b>，而配置岗从页面上看不出任何异常。 所以判定必须在保存时就发生。
 *
 * <p><b>为什么校验要作用在「合并后的生效值」上</b>：编辑接口的语义是"空字段沿用原值"。{@link
 * #update_dailyLimitAddedWhileBasisStaysNone_isRejected()} 专门守这条：库里 {@code
 * daily_limit=0/basis=NONE}， 本次只提交金额 500——单看命令两边都不矛盾，合并后却是"配了金额没配口径"。只校验命令的实现会放它过去。
 *
 * <p><b>为什么不断言"定额给付型也被拒"</b>：那两种责任<b>允许建档</b>（业务必须能登记在售产品），只是不参与自动理算。 若照搬 {@code validate()}
 * 会把配置面变成"定额责任根本建不了"——见 {@link #create_fixedAmountType_isStillAllowed()}。
 */
@ExtendWith(MockitoExtension.class)
class LiabilityRuleShapeGuardTest {

  private static final long PLAN_ID = 1001L;

  @Mock LiabilityRepository liabilityRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock TenantPort tenantPort;
  @Mock DomainEventPublisher domainEventPublisher;
  @Mock QuotaConfigApplicationService quotaConfigApplicationService;

  @InjectMocks LiabilityConfigApplicationService service;

  /** 启用联动（0094）的放行夹具：责任写入口会先校验所属保单的责任规则开关为「启用中」。 用 {@code lenient} 以兼容个别不经服务入口的用例。 */
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

  // ===================== 日限额：金额 ↔ 口径必须同配 =====================

  @Test
  void create_dailyLimitWithoutBasis_isRejected() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(req().dailyLimit("500").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("未配「日」的口径");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_dailyBasisWithoutLimit_isRejected() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(
            () -> service.createLiability(req().dailyLimitBasis("SAME_DAY_INVOICE").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("日限额为 0");

    verify(liabilityRepository, never()).save(any());
  }

  /**
   * 合并口径的判别用例：命令里只有金额，口径沿用库里的 {@code NONE}。
   *
   * <p>若把校验从"合并生效值"退回"只看命令值"，本用例会转绿——这正是它存在的意义。
   */
  @Test
  void update_dailyLimitAddedWhileBasisStaysNone_isRejected() {
    when(liabilityRepository.findById(1L)).thenReturn(liability("DRAFT"));

    assertThatThrownBy(() -> service.updateLiability(1L, req().dailyLimit("500").update()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("未配「日」的口径");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_dailyLimitWithBasis_isAccepted_andPersisted() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    LiabilityView view =
        service.createLiability(
            req().dailyLimit("500").dailyLimitBasis("SAME_DAY_INVOICE").create());

    ArgumentCaptor<AdjLiability> saved = ArgumentCaptor.forClass(AdjLiability.class);
    verify(liabilityRepository).save(saved.capture());
    assertThat(saved.getValue().dailyLimit()).isEqualByComparingTo("500");
    assertThat(saved.getValue().dailyLimitBasis()).isEqualTo("SAME_DAY_INVOICE");
    assertThat(view.dailyLimitBasis()).isEqualTo("SAME_DAY_INVOICE");
  }

  // ===================== 适用限定：模式 ↔ 名单必须同配 =====================

  @Test
  void create_limitModeWithEmptyLists_isRejected() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(req().limitMode("WHITELIST").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("名单都为空");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_limitModeWithHospitalCodes_isAccepted() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    LiabilityView view =
        service.createLiability(
            req().limitMode("WHITELIST").limitHospitalCodes("H001,H002").create());

    assertThat(view.limitMode()).isEqualTo("WHITELIST");
    assertThat(view.limitHospitalCodes()).isEqualTo("H001,H002");
  }

  /**
   * 「有保障归属 + 有适用限定」这个组合必须两样都落库。
   *
   * <p>变异判别力：{@link AdjLiability} 的目录准入重载此前委托给"含 coverageId 但不含 limitMode"的重载，
   * 三条限定列被静默丢掉——配置面填了白名单，保存 200、库里是 {@code NONE}，页面上看不出任何异常。 删掉那个重载里补赋值的三行，本用例转红。
   */
  @Test
  void create_withCoverageAndLimitedConditions_persistsBoth() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(claimCoverageRepository.findCoverageById(3003L))
        .thenReturn(
            ClaimCoverage.draft(3003L, 1L, PLAN_ID, "C01", "门诊", BigDecimal.ZERO, "REIMBURSEMENT"));

    service.createLiability(
        req().coverageId(3003L).limitMode("BLACKLIST").limitDiagnosisCodes("D001").create());

    ArgumentCaptor<AdjLiability> saved = ArgumentCaptor.forClass(AdjLiability.class);
    verify(liabilityRepository).save(saved.capture());
    assertThat(saved.getValue().getCoverageId()).isEqualTo(3003L);
    assertThat(saved.getValue().getLimitMode()).isEqualTo("BLACKLIST");
    assertThat(saved.getValue().getLimitDiagnosisCodes()).isEqualTo("D001");
  }

  /** 无法识别的枚举必须 fail-closed（回落 NONE 等于"写错一个字母，限定静默失效"）。 */
  @Test
  void create_unknownLimitMode_isRejectedNotSilentlyDisabled() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(
            () ->
                service.createLiability(
                    req().limitMode("WHITELISTED").limitHospitalCodes("H001").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("无法识别的适用限定方式");

    verify(liabilityRepository, never()).save(any());
  }

  // ===================== 目录准入：策略 ↔ 目录编码必须同配 =====================

  @Test
  void create_catalogCodeWithoutMandatory_isRejected() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(req().catalogCode("NATIONAL_2024").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("永远不会被查询");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_mandatoryWithoutCatalogCode_isRejected() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(req().catalogPolicy("MANDATORY").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("未填目录编码");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_catalogPolicyWithCode_isAccepted() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    LiabilityView view =
        service.createLiability(
            req().catalogPolicy("MANDATORY").catalogCode("NATIONAL_2024").create());

    assertThat(view.catalogPolicy()).isEqualTo("MANDATORY");
    assertThat(view.catalogCode()).isEqualTo("NATIONAL_2024");
  }

  // ===================== 金额：负数会被引擎静默夹成 0（= 少扣钱） =====================

  @Test
  void create_negativeInvoiceDeduct_isRejected() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(() -> service.createLiability(req().invoiceDeduct("-1").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("不能为负");

    verify(liabilityRepository, never()).save(any());
  }

  @Test
  void create_negativeDailyLimit_isRejected() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    assertThatThrownBy(
            () ->
                service.createLiability(
                    req().dailyLimit("-100").dailyLimitBasis("SAME_DAY_CLAIM").create()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("不能为负");
  }

  // ===================== 反向契约：允许建档 ≠ 允许自动理算 =====================

  @Test
  void create_fixedAmountType_isStillAllowed() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    LiabilityView view = service.createLiability(req().liabilityType("FIXED_AMOUNT").create());

    assertThat(view.liabilityType()).isEqualTo("FIXED_AMOUNT");
    verify(liabilityRepository).save(any());
  }

  /** 已生效责任编辑会克隆出新版本草稿：克隆前必须也过同一道闸门，否则脏配置被"版本化"扩散。 */
  @Test
  void update_activeLiabilityWithHalfConfig_isRejectedBeforeCloning() {
    when(liabilityRepository.findById(1L)).thenReturn(liability("ACTIVE"));

    assertThatThrownBy(() -> service.updateLiability(1L, req().limitMode("BLACKLIST").update()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("名单都为空");

    verify(liabilityRepository, never()).save(any());
  }

  // ===================== 夹具 =====================

  /**
   * 可增量覆盖的请求夹具。
   *
   * <p>用可变夹具而不是逐字段写 {@code new CreateLiabilityReq(...)}：{@code CreateLiabilityReq} 有 19 个位置参数，
   * 每个用例再抄一遍既容易错位，也会让"这条用例到底在测哪个字段"埋进一堆 {@code null} 里。
   */
  private static Req req() {
    return new Req();
  }

  private static final class Req {
    private Long coverageId;
    private String liabilityType;
    private String payPercent = "90";
    private String invoiceDeduct;
    private String adjustDeduct;
    private String dailyLimit;
    private String dailyLimitBasis;
    private String limitMode;
    private String limitHospitalCodes;
    private String limitDiagnosisCodes;
    private String catalogPolicy;
    private String catalogCode;

    Req coverageId(Long v) {
      this.coverageId = v;
      return this;
    }

    Req liabilityType(String v) {
      this.liabilityType = v;
      return this;
    }

    Req payPercent(String v) {
      this.payPercent = v;
      return this;
    }

    Req invoiceDeduct(String v) {
      this.invoiceDeduct = v;
      return this;
    }

    Req adjustDeduct(String v) {
      this.adjustDeduct = v;
      return this;
    }

    Req dailyLimit(String v) {
      this.dailyLimit = v;
      return this;
    }

    Req dailyLimitBasis(String v) {
      this.dailyLimitBasis = v;
      return this;
    }

    Req limitMode(String v) {
      this.limitMode = v;
      return this;
    }

    Req limitHospitalCodes(String v) {
      this.limitHospitalCodes = v;
      return this;
    }

    Req limitDiagnosisCodes(String v) {
      this.limitDiagnosisCodes = v;
      return this;
    }

    Req catalogPolicy(String v) {
      this.catalogPolicy = v;
      return this;
    }

    Req catalogCode(String v) {
      this.catalogCode = v;
      return this;
    }

    CreateLiabilityReq create() {
      return new CreateLiabilityReq(
          PLAN_ID,
          coverageId,
          "R01",
          liabilityType,
          payPercent,
          null,
          null,
          invoiceDeduct,
          adjustDeduct,
          dailyLimit,
          dailyLimitBasis,
          limitMode,
          limitHospitalCodes,
          limitDiagnosisCodes,
          catalogPolicy,
          catalogCode,
          1,
          0,
          null);
    }

    /** 编辑请求：只提交本夹具显式设过的字段（其余为 null ⇒ 沿用库里原值）。 */
    UpdateLiabilityReq update() {
      return new UpdateLiabilityReq(
          null,
          coverageId,
          liabilityType,
          payPercent,
          null,
          null,
          invoiceDeduct,
          adjustDeduct,
          dailyLimit,
          dailyLimitBasis,
          limitMode,
          limitHospitalCodes,
          limitDiagnosisCodes,
          catalogPolicy,
          catalogCode,
          null,
          null,
          null);
    }
  }

  /** 存量责任夹具：日限额为 0、口径 NONE（不含任何半配置）。 */
  private static AdjLiability liability(String status) {
    return AdjLiability.of(
        1L,
        1L,
        "uuid-1",
        "v1",
        PLAN_ID,
        null,
        "R01",
        "REIMBURSEMENT",
        BigDecimal.valueOf(90),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "NONE",
        1,
        0,
        status);
  }
}
