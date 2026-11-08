package com.bone.claim.application.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.core.exception.BizException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 额度年度裁决测试（{@code quota_year} 是额度账户行键的一部分，算错即"同一笔钱记两本账"）。
 *
 * <p>三个必守点：
 *
 * <ul>
 *   <li><b>按业务日、不按处理时刻</b>：跨年补报的案件不得因为"今天来报"就记进今年；
 *   <li><b>保单年度是 12 个月周期</b>：生效日非 1 月 1 日时，周年边界必须落在生效日上，不是自然年边界；
 *   <li><b>口径切到保单年度后不静默退回自然年</b>：期间查不到/业务日缺失/超出期间一律拒算——退回自然年会把 "配置已切换"伪装成成功，账却按另一条规则记。
 * </ul>
 */
@DisplayName("额度年度裁决（QuotaYearResolver）")
class QuotaYearResolverTest {

  private static final String POLICY = "P-2026-001";
  private static final long PLAN = 7L;

  private static PolicyQueryPort portReturning(PolicyQueryPort.PolicySnapshot snapshot) {
    return new PolicyQueryPort() {
      @Override
      public Optional<PolicySnapshot> findActivePolicy(String policyNo, Long planId) {
        return Optional.empty();
      }

      @Override
      public Optional<PolicySnapshot> findByPolicy(String policyNo, Long planId) {
        return Optional.ofNullable(snapshot);
      }

      /** 年度裁决不走保司号解析（那是收单前置校验的职责），这里恒空。 */
      @Override
      public java.util.List<PolicySnapshot> findActivePoliciesByInsurerNo(
          String insurerPolicyNo, Long planId) {
        return java.util.List.of();
      }
    };
  }

  private static PolicyQueryPort.PolicySnapshot term(LocalDate inception, LocalDate expiry) {
    return new PolicyQueryPort.PolicySnapshot(
        POLICY,
        "",
        PLAN,
        "v1",
        java.util.List.of("h-insured"),
        inception.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
        expiry.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
  }

  @Nested
  @DisplayName("CALENDAR（默认口径）")
  class Calendar {

    private final QuotaYearResolver resolver =
        QuotaYearResolver.of(QuotaYearResolver.Basis.CALENDAR, portReturning(null));

    @Test
    @DisplayName("按业务日归年度：去年 12 月的发票今年才赔，仍记去年（不记处理时刻）")
    void usesBusinessDateNotProcessingDate() {
      QuotaYearResolver.QuotaYear year = resolver.resolve(POLICY, PLAN, LocalDate.of(2025, 12, 31));

      assertThat(year.year()).as("按处理时刻会算成今年：免赔重置=少赔、封顶重置=超赔，且账上看不出来").isEqualTo(2025);
      assertThat(year.basis()).isEqualTo(QuotaYearResolver.Basis.CALENDAR);
    }

    @Test
    @DisplayName("业务日缺失（历史案件）退回处理日，不阻断账务")
    void nullBusinessDateFallsBackToCurrentYear() {
      assertThat(resolver.resolve(POLICY, PLAN, null).year())
          .isEqualTo(java.time.Year.now().getValue());
    }

    @Test
    @DisplayName("自然年口径不需要保单期间——端口未接线也不影响办件")
    void doesNotTouchPolicyPort() {
      // portReturning(null) 的 findByPolicy 永远返回空：若实现去查期间，这里会抛异常而不是算出年份。
      assertThat(resolver.resolve(POLICY, PLAN, LocalDate.of(2026, 5, 1)).year()).isEqualTo(2026);
    }
  }

  @Nested
  @DisplayName("POLICY_TERM（保单年度）")
  class PolicyTerm {

    /** 生效 2026-03-01、满期 2028-02-29（闰日边界）。 */
    private final QuotaYearResolver resolver =
        QuotaYearResolver.of(
            QuotaYearResolver.Basis.POLICY_TERM,
            portReturning(term(LocalDate.of(2026, 3, 1), LocalDate.of(2028, 2, 29))));

    @Test
    @DisplayName("首个保单年度：生效日起算，记起始年份")
    void firstPolicyYear() {
      assertThat(resolver.resolve(POLICY, PLAN, LocalDate.of(2026, 3, 1)).year()).isEqualTo(2026);
      assertThat(resolver.resolve(POLICY, PLAN, LocalDate.of(2026, 12, 31)).year()).isEqualTo(2026);
    }

    @Test
    @DisplayName("跨过自然年但未满 12 个月：仍属上一保单年度（自然年口径会在这里重置）")
    void crossesCalendarYearButStaysInSamePolicyYear() {
      QuotaYearResolver.QuotaYear jan = resolver.resolve(POLICY, PLAN, LocalDate.of(2027, 1, 15));
      QuotaYearResolver.QuotaYear febEnd =
          resolver.resolve(POLICY, PLAN, LocalDate.of(2027, 2, 28));

      assertThat(jan.year()).as("生效日非 1 月 1 日时，自然年与保单年度在 1–2 月必然不等").isEqualTo(2026);
      assertThat(febEnd.year()).isEqualTo(2026);
    }

    @Test
    @DisplayName("满 12 个月进入下一保单年度（周年边界，不是 1 月 1 日）")
    void anniversaryRollsToNextPolicyYear() {
      assertThat(resolver.resolve(POLICY, PLAN, LocalDate.of(2027, 3, 1)).year()).isEqualTo(2027);
      assertThat(resolver.resolve(POLICY, PLAN, LocalDate.of(2028, 2, 29)).year()).isEqualTo(2027);
      assertThat(resolver.resolve(POLICY, PLAN, LocalDate.of(2028, 2, 29)).basis())
          .isEqualTo(QuotaYearResolver.Basis.POLICY_TERM);
    }

    @Test
    @DisplayName("保单期间查不到 → 拒算（不得退回自然年）")
    void unknownPolicyTermFailsClosed() {
      QuotaYearResolver unwired =
          QuotaYearResolver.of(QuotaYearResolver.Basis.POLICY_TERM, portReturning(null));

      assertThatThrownBy(() -> unwired.resolve(POLICY, PLAN, LocalDate.of(2026, 5, 1)))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("SC_CLAIM_POLICY_NOT_ACTIVE");
    }

    @Test
    @DisplayName("半截期间（缺满期日）→ 拒算（用它算出的年份看似合法、实际是错的）")
    void incompleteTermFailsClosed() {
      QuotaYearResolver resolver =
          QuotaYearResolver.of(
              QuotaYearResolver.Basis.POLICY_TERM,
              portReturning(
                  new PolicyQueryPort.PolicySnapshot(
                      POLICY,
                      "",
                      PLAN,
                      "v1",
                      java.util.List.of("h-insured"),
                      java.time.Instant.now(),
                      null)));

      assertThatThrownBy(() -> resolver.resolve(POLICY, PLAN, LocalDate.of(2026, 5, 1)))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("SC_CLAIM_POLICY_NOT_ACTIVE");
    }

    @Test
    @DisplayName("业务日超出保单期间 → 拒算（期间外没有保单年度可言）")
    void outOfTermFailsClosed() {
      assertThatThrownBy(() -> resolver.resolve(POLICY, PLAN, LocalDate.of(2025, 2, 1)))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("SC_CLAIM_OUT_OF_POLICY_PERIOD");
      assertThatThrownBy(() -> resolver.resolve(POLICY, PLAN, LocalDate.of(2029, 1, 1)))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("SC_CLAIM_OUT_OF_POLICY_PERIOD");
    }

    @Test
    @DisplayName("业务日缺失 → 拒算（年度是主键的一部分，拿处理日顶替出来的账户行没人能认领）")
    void nullBusinessDateFailsClosed() {
      assertThatThrownBy(() -> resolver.resolve(POLICY, PLAN, null))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("SC_CLAIM_OUT_OF_POLICY_PERIOD");
    }
  }

  @Test
  @DisplayName("口径配置写错 → 启动期失败（继续跑等于按一条谁也没声明过的规则记账）")
  void unknownBasisFailsFast() {
    assertThatThrownBy(() -> new QuotaYearResolver("WEEKLY", portReturning(null)).basis())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bone.claim.quota-year.basis");
  }

  @Test
  @DisplayName("口径配置大小写不敏感，缺省即自然年")
  void basisParsingIsCaseInsensitive() {
    assertThat(new QuotaYearResolver(" calendar ", portReturning(null)).basis())
        .isEqualTo(QuotaYearResolver.Basis.CALENDAR);
    assertThat(new QuotaYearResolver("policy_term", portReturning(null)).basis())
        .isEqualTo(QuotaYearResolver.Basis.POLICY_TERM);
    assertThat(new QuotaYearResolver("", portReturning(null)).basis())
        .isEqualTo(QuotaYearResolver.Basis.CALENDAR);
  }
}
