package com.bone.claim.domain.model.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 资金阶段链校验（§11.7 不变量 3）：合法迁移表就是 {@link FundingStage} 的语义。 */
class FundingStageChainTest {

  private static final Instant BASE = Instant.parse("2026-10-24T01:00:00Z");

  @Nested
  @DisplayName("合法链：不产生违规")
  class Legal {

    @Test
    @DisplayName("小额：SOFT → HARD → PAID")
    void smallAmountFlow() {
      assertThat(FundingStageChain.check(chain("SOFT_RESERVED", "HARD_RESERVED", "PAID")))
          .isEmpty();
    }

    @Test
    @DisplayName("大额复核：SOFT → REVIEW_FREEZE → HARD → PAID（复核放行不算逆向）")
    void reviewFlow() {
      assertThat(
              FundingStageChain.check(
                  chain("SOFT_RESERVED", "REVIEW_FREEZE", "HARD_RESERVED", "PAID")))
          .isEmpty();
    }

    @Test
    @DisplayName("撤销：SOFT → RELEASED；支付失败：SOFT → FAILED")
    void cancelFlows() {
      assertThat(FundingStageChain.check(chain("SOFT_RESERVED", "RELEASED"))).isEmpty();
      assertThat(FundingStageChain.check(chain("SOFT_RESERVED", "FAILED"))).isEmpty();
    }

    @Test
    @DisplayName("退票退款：PAID → REFUNDED")
    void refundFlow() {
      assertThat(FundingStageChain.check(chain("HARD_RESERVED", "PAID", "REFUNDED"))).isEmpty();
    }

    @Test
    @DisplayName("首条不是 SOFT 不判违规（历史版本 HARD 直接起手）")
    void legacyHardFirst() {
      assertThat(FundingStageChain.check(chain("HARD_RESERVED", "PAID"))).isEmpty();
    }

    @Test
    @DisplayName("空流水不判违规（缺流水由 MISSING_LOCAL 承载，不由两个判据重复报）")
    void emptyLedger() {
      assertThat(FundingStageChain.check(List.of())).isEmpty();
      assertThat(FundingStageChain.check(null)).isEmpty();
    }
  }

  @Nested
  @DisplayName("违规：状态机被倒拨或被打乱")
  class Illegal {

    @Test
    @DisplayName("PAID 后又追加 SOFT_RESERVED ⇒ 非法迁移")
    void paidThenSoft() {
      List<FundingStageChain.Breach> breaches =
          FundingStageChain.check(chain("SOFT_RESERVED", "HARD_RESERVED", "PAID", "SOFT_RESERVED"));

      assertThat(breaches).hasSize(1);
      assertThat(breaches.get(0).kind()).isEqualTo(FundingStageChain.BreachKind.ILLEGAL_TRANSITION);
      assertThat(breaches.get(0).from()).isEqualTo("PAID");
      assertThat(breaches.get(0).to()).isEqualTo("SOFT_RESERVED");
    }

    @Test
    @DisplayName("终态之后再出事件 ⇒ 非法迁移（终态没有后继）")
    void afterTerminal() {
      List<FundingStageChain.Breach> breaches =
          FundingStageChain.check(chain("SOFT_RESERVED", "RELEASED", "HARD_RESERVED"));

      assertThat(breaches).hasSize(1);
      assertThat(breaches.get(0).from()).isEqualTo("RELEASED");
      assertThat(breaches.get(0).to()).isEqualTo("HARD_RESERVED");
    }

    @Test
    @DisplayName("同阶段重复追加 ⇒ 非法迁移（幂等键本该拦住重复写）")
    void duplicateStage() {
      List<FundingStageChain.Breach> breaches =
          FundingStageChain.check(chain("SOFT_RESERVED", "SOFT_RESERVED"));

      assertThat(breaches).hasSize(1);
      assertThat(breaches.get(0).from()).isEqualTo("SOFT_RESERVED");
      assertThat(breaches.get(0).to()).isEqualTo("SOFT_RESERVED");
    }

    @Test
    @DisplayName("事件时间倒序 ⇒ OUT_OF_ORDER（补数/乱序重放）")
    void outOfOrderTime() {
      List<FundingEvent> events = new ArrayList<>();
      events.add(event("SOFT_RESERVED", BASE.plusSeconds(10)));
      events.add(event("HARD_RESERVED", BASE));

      List<FundingStageChain.Breach> breaches = FundingStageChain.check(events);

      assertThat(breaches).hasSize(1);
      assertThat(breaches.get(0).kind()).isEqualTo(FundingStageChain.BreachKind.OUT_OF_ORDER);
      assertThat(breaches.get(0).from()).isEqualTo("EVENT_TIME");
      assertThat(breaches.get(0).to()).isEqualTo("OUT_OF_ORDER");
    }

    @Test
    @DisplayName("枚举外的 stage 不当合法前序（否则脏数据会被当成正常状态的来源）")
    void unknownStage() {
      // 真实数据里 stage 列是 NOT NULL VARCHAR，能落进枚举外的值（写错/迁移），故用桩造这种行
      FundingEvent unknown = mock(FundingEvent.class);
      when(unknown.getStage()).thenReturn("SETTLED");
      when(unknown.stageEnum()).thenThrow(new IllegalArgumentException("No enum constant SETTLED"));
      when(unknown.getEventTime()).thenReturn(BASE.plusSeconds(1));

      List<FundingEvent> events = new ArrayList<>();
      events.add(event("SOFT_RESERVED", BASE));
      events.add(unknown);
      events.add(event("PAID", BASE.plusSeconds(2)));

      List<FundingStageChain.Breach> breaches = FundingStageChain.check(events);

      assertThat(breaches).hasSize(1);
      assertThat(breaches.get(0).from()).isEqualTo("SOFT_RESERVED");
      assertThat(breaches.get(0).to()).isEqualTo("SETTLED");
    }
  }

  private static List<FundingEvent> chain(String... stages) {
    List<FundingEvent> events = new ArrayList<>();
    for (int i = 0; i < stages.length; i++) {
      events.add(event(stages[i], BASE.plusSeconds(i)));
    }
    return events;
  }

  private static FundingEvent event(String stage, Instant at) {
    return FundingEvent.of(
        1L,
        7L,
        "SO1",
        100L,
        200L,
        FundingStage.valueOf(stage),
        new BigDecimal("800.00"),
        "op",
        AccountType.NO_ACCOUNT,
        "C1:" + stage + ":" + at.toEpochMilli(),
        at);
  }
}
