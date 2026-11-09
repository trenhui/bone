package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.dto.request.LiabilityRequests.CreateLiabilityReq;
import com.bone.claim.adapter.web.dto.request.LiabilityRequests.UpdateLiabilityReq;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.LiabilityRuleJson;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * M2 ② Shadow：写入即回读比对（{@code scalar == project(json)}）。
 *
 * <p><b>为什么在服务层测</b>：不变量由三段共同保证——标量装配、JSON 编码、落库后回读。前两段是纯函数， 第三段只在服务里发生；只测前两段等于证明"内存里自洽"，而真实事故（SDK
 * 写不进 JSON 列、模板少列读不回） 恰恰发生在第三段。
 *
 * <p><b>为什么既要正例也要"回读不一致"的反例</b>：只测正例时，把回读整段删掉用例照样绿——
 * 因为内存对象天然自洽。反例才会在看到"影子与标量不一致"时真的转红，才是这条不变量存在的证据。
 */
@ExtendWith(MockitoExtension.class)
class LiabilityRuleShadowWriteTest {

  private static final long PLAN_ID = 1001L;
  private static final long LIABILITY_ID = 5001L;

  @Mock LiabilityRepository liabilityRepository;
  @Mock ClaimCoverageRepository claimCoverageRepository;
  @Mock ClaimPlanRepository claimPlanRepository;
  @Mock ClaimPolicyRepository claimPolicyRepository;
  @Mock TenantPort tenantPort;
  @Mock DomainEventPublisher domainEventPublisher;
  @Mock QuotaConfigApplicationService quotaConfigApplicationService;

  @InjectMocks LiabilityConfigApplicationService service;

  /** 模拟"库里的那一行"：save 记下来，findById 再取出来，回读才走真实路径。 */
  private final AtomicReference<AdjLiability> persisted = new AtomicReference<>();

  @BeforeEach
  void setUp() {
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
    lenient().when(tenantPort.currentTenantId()).thenReturn(1L);
    when(liabilityRepository.save(any()))
        .thenAnswer(
            inv -> {
              AdjLiability savedEntity = inv.getArgument(0);
              persisted.set(savedEntity);
              return savedEntity.getId();
            });
    lenient().when(liabilityRepository.findById(anyLong())).thenAnswer(inv -> persisted.get());
  }

  @Test
  void create_writesShadowThatProjectsBackToTheScalars() {
    service.createLiability(createReq());

    AdjLiability saved = persisted.get();
    assertThat(saved.getLiabilityRuleJson()).isNotNull();
    LiabilityRuleJson shadow = LiabilityRuleJsonCodec.decode(saved.getLiabilityRuleJson());
    assertThat(shadow.project(LiabilityRuleJson.KEY_PAY_PERCENT)).isEqualByComparingTo("90.00");
    assertThat(shadow.project(LiabilityRuleJson.KEY_LIABILITY_DEDUCT))
        .isEqualByComparingTo("200.00");
    assertThat(shadow.project(LiabilityRuleJson.KEY_LIABILITY_LIMIT))
        .isEqualByComparingTo("5000.00");
    // ② 只写可映射子集：B 类键不编造默认值（缺键 = 不施加约束）。
    assertThat(shadow.dimensions()).isEmpty();
  }

  /**
   * 回读不一致必须拒绝提交。
   *
   * <p>用"库里那一行的比例被改成 80"模拟影子漂移（真实成因：并发写、模板列错位、迁移脚本直接改标量）。 若把回读比对删掉，本用例会转绿——这就是它存在的意义。
   */
  @Test
  void create_rejectsWhenReadBackDisagreesWithShadow() {
    AdjLiability drifted =
        AdjLiability.of(
            LIABILITY_ID,
            1L,
            "uuid-x",
            "v1",
            PLAN_ID,
            "R01",
            "REIMBURSEMENT",
            new BigDecimal("80.00"),
            new BigDecimal("200.00"),
            new BigDecimal("5000.00"),
            1,
            0,
            "DRAFT");
    drifted.attachRuleJsonShadow(
        LiabilityRuleJsonCodec.encode(
            LiabilityRuleJson.ofScalars(
                new BigDecimal("90.00"), new BigDecimal("200.00"), new BigDecimal("5000.00"))));
    when(liabilityRepository.findById(anyLong())).thenReturn(drifted);

    assertThatThrownBy(() -> service.createLiability(createReq()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("影子与标量不一致");
  }

  /**
   * 草稿编辑：影子跟着标量走，但 B 类维度必须原样保留。
   *
   * <p>反例（用 {@code ofScalars} 重建影子）会静默删光限定条件——"改个比例顺手取消白名单"。
   */
  @Test
  void draftEdit_updatesShadowAndKeepsDimensions() {
    AdjLiability existing =
        AdjLiability.of(
            LIABILITY_ID,
            1L,
            "uuid-1",
            "v1",
            PLAN_ID,
            "R01",
            "REIMBURSEMENT",
            new BigDecimal("90.00"),
            new BigDecimal("200.00"),
            new BigDecimal("5000.00"),
            1,
            0,
            "DRAFT");
    existing.attachRuleJsonShadow(
        "{\"schemaVersion\":1,\"pay_percent\":90.00,\"liability_deduct\":200.00,"
            + "\"liability_limit\":5000.00,\"restrict_scope\":{\"mode\":\"WHITELIST\","
            + "\"hospitalCodes\":[\"H1\"]}}");
    when(liabilityRepository.findById(LIABILITY_ID)).thenReturn(existing);

    service.updateLiability(LIABILITY_ID, updateReq("80"));

    LiabilityRuleJson shadow = LiabilityRuleJsonCodec.decode(existing.getLiabilityRuleJson());
    assertThat(shadow.project(LiabilityRuleJson.KEY_PAY_PERCENT)).isEqualByComparingTo("80");
    assertThat(shadow.dimensions()).containsKey("restrict_scope");
    assertThat(shadow.dimensions().get("restrict_scope")).contains("H1");
  }

  private static CreateLiabilityReq createReq() {
    return new CreateLiabilityReq(
        PLAN_ID,
        null,
        "R01",
        "REIMBURSEMENT",
        "90",
        "200",
        "5000",
        "0",
        "0",
        "0",
        "NONE",
        "NONE",
        "",
        "",
        "NONE",
        "",
        null,
        1,
        0,
        "",
        "",
        "DRAFT");
  }

  private static UpdateLiabilityReq updateReq(String payPercent) {
    return new UpdateLiabilityReq(
        "R01",
        null,
        "REIMBURSEMENT",
        payPercent,
        "200",
        "5000",
        "0",
        "0",
        "0",
        "NONE",
        "NONE",
        "",
        "",
        "NONE",
        "",
        null,
        1,
        0,
        "",
        "",
        false);
  }
}
