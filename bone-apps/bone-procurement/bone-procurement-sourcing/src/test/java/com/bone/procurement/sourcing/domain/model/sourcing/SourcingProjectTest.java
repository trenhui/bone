package com.bone.procurement.sourcing.domain.model.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 寻源不变量测试：状态迁移 fail-closed、冻结字段、人工授标红线、RFx 截止单调。 */
class SourcingProjectTest {

  private SourcingProject draft() {
    return SourcingProject.create(
        1L, "PRJ-1", "CAT-1", new BigDecimal("100000"), "CNY", "COMPETITIVE", "owner-1");
  }

  @Test
  @DisplayName("DRAFT 可以发布，PUBLISHED 后不可回退且关键字段冻结")
  void publishThenFrozen() {
    SourcingProject p = draft();
    p.publish();
    assertThat(p.status()).isEqualTo(SourcingProjectStatus.PUBLISHED);
    assertThat(p.status().canTransitionTo(SourcingProjectStatus.DRAFT)).isFalse();
    assertThatThrownBy(() -> p.changeKeyFields("CAT-2", BigDecimal.TEN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("冻结");
  }

  @Test
  @DisplayName("未列出的状态迁移一律拒绝")
  void illegalTransitionRejected() {
    SourcingProject p = draft();
    p.publish();
    p.closeBidding();
    p.awardByHuman("reviewer-1");
    assertThatThrownBy(() -> p.changeKeyFields("CAT-2", BigDecimal.TEN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("冻结");
    assertThatThrownBy(p::publish)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非法状态迁移");
  }

  @Test
  @DisplayName("AI 或匿名不得授标，必须具名人工操作人")
  void aiCannotAward() {
    SourcingProject p = draft();
    p.publish();
    p.closeBidding();
    assertThatThrownBy(() -> p.awardByHuman("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("人工");
    p.awardByHuman("human-1");
    assertThat(p.status()).isEqualTo(SourcingProjectStatus.AWARDED);
  }

  @Test
  @DisplayName("RFx 截止时间单调，到期后不收标")
  void rfxDueDateMonotonic() {
    Date due = new Date(System.currentTimeMillis() + 86400000L);
    Rfx rfx = Rfx.create(1L, 1L, "RFX-1", "RFQ", due);
    rfx.publish(new Date());
    assertThat(rfx.acceptsBidAt(new Date())).isTrue();
    assertThatThrownBy(() -> rfx.extendDueAt(new Date(due.getTime() - 1000)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("单调");
    Date later = new Date(due.getTime() + 3600000L);
    rfx.extendDueAt(later);
    assertThat(rfx.acceptsBidAt(later)).isFalse();
  }
}
