package com.bone.claim.domain.model.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 保单聚合根单测（配置面）。
 *
 * <p><b>为什么这些用例必须存在</b>：保单是"这张单今天还能不能收案"的判定依据， 规则开关与承保期间的语义一旦被改错，报案要么全被拒（生产不可用）要么全被放（超赔）。
 * 这些用例锁的是语义，不是实现细节。
 */
class ClaimPolicyTest {

  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-12-31T23:59:59Z");

  private ClaimPolicy newPolicy() {
    return ClaimPolicy.open(
        1L,
        0L,
        "POL-001",
        "测试保单",
        "保司",
        "分公司",
        "投保公司",
        "BIZ-1",
        "OUTPATIENT",
        "GROUP",
        FROM,
        TO,
        0,
        "v1",
        "[\"hash-a\",\"hash-b\"]",
        Instant.now());
  }

  @Nested
  @DisplayName("新建")
  class Create {

    @Test
    @DisplayName("保单号为空 → 拒绝（保单号是对外业务号，没有它无法定位）")
    void blankPolicyNoRejected() {
      assertThatThrownBy(
              () ->
                  ClaimPolicy.open(
                      1L,
                      0L,
                      "  ",
                      "名称",
                      "",
                      "",
                      "",
                      "",
                      "OUTPATIENT",
                      "GROUP",
                      FROM,
                      TO,
                      0,
                      "v1",
                      null,
                      Instant.now()))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("保单号");
    }

    @Test
    @DisplayName("生效起 >= 生效止 → 拒绝（承保期间为空等于随时不可保）")
    void invertedPeriodRejected() {
      assertThatThrownBy(
              () ->
                  ClaimPolicy.open(
                      1L,
                      0L,
                      "POL-001",
                      "名称",
                      "",
                      "",
                      "",
                      "",
                      "OUTPATIENT",
                      "GROUP",
                      TO,
                      FROM,
                      0,
                      "v1",
                      null,
                      Instant.now()))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("生效");
    }

    @Test
    @DisplayName("等待期为负 → 拒绝")
    void negativeWaitingPeriodRejected() {
      assertThatThrownBy(
              () ->
                  ClaimPolicy.open(
                      1L,
                      0L,
                      "POL-001",
                      "名称",
                      "",
                      "",
                      "",
                      "",
                      "OUTPATIENT",
                      "GROUP",
                      FROM,
                      TO,
                      -1,
                      "v1",
                      null,
                      Instant.now()))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("等待期");
    }

    @Test
    @DisplayName("新建后规则开关为「未启用」（配置面刚建出来，责任多半还没配）")
    void defaultsToInactive() {
      assertThat(newPolicy().configStatus()).isEqualTo(ClaimPolicy.CONFIG_INACTIVE);
      assertThat(newPolicy().isConfigActive()).isFalse();
    }
  }

  @Nested
  @DisplayName("规则开关")
  class ConfigStatus {

    @Test
    @DisplayName("启用 → 开关变为 ACTIVE 且发领域事件")
    void activateSwitchesToActive() {
      ClaimPolicy policy = newPolicy();
      policy.activate("op-1");

      assertThat(policy.configStatus()).isEqualTo(ClaimPolicy.CONFIG_ACTIVE);
      assertThat(policy.isConfigActive()).isTrue();
      assertThat(policy.getDomainEvents()).hasSize(1);
      assertThat(policy.getDomainEvents().get(0))
          .isInstanceOf(
              com.bone.claim.domain.model.policy.event.PolicyConfigStatusChangedEvent.class);
    }

    @Test
    @DisplayName("重复启用 → 幂等（不重复发事件，不产生无谓的 updated_at 抖动）")
    void activateIsIdempotent() {
      ClaimPolicy policy = newPolicy();
      policy.activate("op-1");
      policy.activate("op-2");

      assertThat(policy.getDomainEvents()).hasSize(1);
    }

    @Test
    @DisplayName("停用 → 开关变为 PAUSE 且发领域事件")
    void pauseSwitchesToPaused() {
      ClaimPolicy policy = newPolicy();
      policy.activate("op-1");
      policy.pause("op-2");

      assertThat(policy.configStatus()).isEqualTo(ClaimPolicy.CONFIG_PAUSE);
      assertThat(policy.getDomainEvents()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("承保期间判定")
  class Coverage {

    @Test
    @DisplayName("期间内 → 承保")
    void insidePeriodCovered() {
      assertThat(newPolicy().coversPeriod(Instant.parse("2026-06-15T00:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("期间前 / 期间后 → 不承保")
    void outsidePeriodNotCovered() {
      ClaimPolicy policy = newPolicy();
      assertThat(policy.coversPeriod(Instant.parse("2025-12-31T23:59:59Z"))).isFalse();
      assertThat(policy.coversPeriod(Instant.parse("2027-01-01T00:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("期间判定只看时间、不看规则开关（开关由报案校验另行判定）")
    void periodCheckIgnoresConfigStatus() {
      ClaimPolicy policy = newPolicy();
      policy.pause("op-1");

      // 停用的保单在期间内仍是"期间内"——两个维度正交，混在一起会让额度年度裁决失去依据
      assertThat(policy.coversPeriod(Instant.parse("2026-06-15T00:00:00Z"))).isTrue();
    }
  }
}
