package com.bone.procurement.business.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.business.domain.model.business.Award;
import com.bone.procurement.business.domain.model.business.Capa;
import com.bone.procurement.business.domain.model.business.CapaStatus;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessPersistenceRestartTest {

  private static final Date NOW = Date.from(Instant.parse("2026-11-26T00:00:00Z"));

  @Test
  @DisplayName("具名授标重启后读回同一确认人、时间和理由")
  void awardReadBackAfterRestart() {
    BusinessSdkFixture fixture = new BusinessSdkFixture();
    BusinessPersistenceAdapter before = fixture.newAdapter();
    Award award = Award.decide(1L, "RFX-RESTART", "SUP-1", "alice", "综合评估最优", NOW);
    before.saveAward(award);

    BusinessPersistenceAdapter after = fixture.newAdapter();

    assertThat(after.findAward(1L, "RFX-RESTART"))
        .hasValueSatisfying(
            found -> {
              assertThat(found.confirmedBy()).isEqualTo("alice");
              assertThat(found.confirmedAt()).isEqualTo(NOW);
              assertThat(found.reason()).isEqualTo("综合评估最优");
            });
    assertThat(fixture.storedAwards()).isEqualTo(1);
  }

  @Test
  @DisplayName("CAPA 超期转人工后重启仍为 OVERDUE 且保留原期限")
  void capaOverdueReadBackAfterRestart() {
    BusinessSdkFixture fixture = new BusinessSdkFixture();
    BusinessPersistenceAdapter before = fixture.newAdapter();
    Date due = Date.from(NOW.toInstant().minusSeconds(86_400));
    Capa capa = Capa.open(1L, "CAPA-RESTART", "SUP-1", "交付延误", "alice", due);
    before.saveCapa(capa);
    before.updateCapa(capa.markOverdue(NOW));

    BusinessPersistenceAdapter after = fixture.newAdapter();

    assertThat(after.findCapa(1L, "CAPA-RESTART"))
        .hasValueSatisfying(
            found -> {
              assertThat(found.status()).isEqualTo(CapaStatus.OVERDUE);
              assertThat(found.followUpRequired()).isTrue();
              assertThat(found.dueAt()).isEqualTo(due);
            });
  }

  @Test
  @DisplayName("CAPA 状态更新保留原创建时间")
  void capaUpdateKeepsCreatedAt() {
    BusinessSdkFixture fixture = new BusinessSdkFixture();
    BusinessPersistenceAdapter adapter = fixture.newAdapter();
    Capa capa = Capa.open(1L, "CAPA-AUDIT", "SUP-1", "交付延误", "alice", plusDay());
    adapter.saveCapa(capa);
    Date createdAt = fixture.createdAtOfCapa("CAPA-AUDIT");

    adapter.updateCapa(capa.markOverdue(Date.from(NOW.toInstant().plusSeconds(172_800))));

    assertThat(fixture.createdAtOfCapa("CAPA-AUDIT")).isEqualTo(createdAt);
  }

  @Test
  @DisplayName("CAPA 关闭后重启一致，终态回写被持久化层拒绝")
  void capaClosedAndTerminalRewriteRejected() {
    BusinessSdkFixture fixture = new BusinessSdkFixture();
    BusinessPersistenceAdapter before = fixture.newAdapter();
    Capa capa =
        Capa.open(1L, "CAPA-CLOSE", "SUP-1", "交付延误", "alice", plusDay())
            .close("bob", "整改附件引用", NOW);
    before.saveCapa(capa);

    BusinessPersistenceAdapter after = fixture.newAdapter();
    assertThat(after.findCapa(1L, "CAPA-CLOSE"))
        .hasValueSatisfying(
            found -> {
              assertThat(found.status()).isEqualTo(CapaStatus.CLOSED);
              assertThat(found.evidenceRef()).isEqualTo("整改附件引用");
            });
    Capa persisted = after.findCapa(1L, "CAPA-CLOSE").orElseThrow();
    assertThatThrownBy(() -> after.updateCapa(persisted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("终态");
  }

  private static Date plusDay() {
    return Date.from(NOW.toInstant().plusSeconds(86_400));
  }
}
