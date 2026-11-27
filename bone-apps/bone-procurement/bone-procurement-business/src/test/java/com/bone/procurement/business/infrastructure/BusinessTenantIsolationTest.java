package com.bone.procurement.business.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.business.domain.model.business.Bid;
import com.bone.procurement.business.domain.model.business.Capa;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessTenantIsolationTest {

  private static final Date NOW = Date.from(Instant.parse("2026-11-26T00:00:00Z"));

  @Test
  @DisplayName("同租户相同业务键重复写入被拒")
  void sameTenantDuplicateRejected() {
    BusinessPersistenceAdapter adapter = new BusinessSdkFixture().newAdapter();
    adapter.saveCapa(openCapa(1L, "CAPA-DUP"));

    assertThatThrownBy(() -> adapter.saveCapa(openCapa(1L, "CAPA-DUP")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("重复");
  }

  @Test
  @DisplayName("跨租户同业务键并存但不可互读或覆写")
  void crossTenantCoexistsButIsolated() {
    BusinessPersistenceAdapter adapter = new BusinessSdkFixture().newAdapter();
    adapter.saveCapa(openCapa(1L, "CAPA-SHARED"));
    adapter.saveCapa(openCapa(2L, "CAPA-SHARED"));

    assertThat(adapter.findCapa(1L, "CAPA-SHARED")).isPresent();
    assertThat(adapter.findCapa(2L, "CAPA-SHARED")).isPresent();
    assertThat(adapter.findCapa(3L, "CAPA-SHARED")).isEmpty();
    assertThat(adapter.findCapa(2L, "CAPA-SHARED").orElseThrow().tenantId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("撤回投标不得从持久化层复活或延长截止时间")
  void withdrawnBidCannotBeResurrected() {
    BusinessPersistenceAdapter adapter = new BusinessSdkFixture().newAdapter();
    adapter.saveBid(
        Bid.draft(1L, "BID-WITHDRAWN", "RFX-1", "SUP-1", "SRC-1", plusDay()).withdraw());

    assertThatThrownBy(
            () ->
                adapter.saveBid(
                    Bid.draft(1L, "BID-WITHDRAWN", "RFX-1", "SUP-1", null, plusDay()).submit(NOW)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("终态");
  }

  @Test
  @DisplayName("缺失租户上下文写入与查询都 fail-closed")
  void missingTenantFailsClosed() {
    BusinessPersistenceAdapter adapter = new BusinessSdkFixture().newAdapter();
    assertThatThrownBy(
            () ->
                adapter.saveBid(
                    Bid.draft(null, "BID-NO-TENANT", "RFX-1", "SUP-1", "SRC-1", plusDay())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("租户");
    assertThatThrownBy(() -> adapter.findBids(null, "RFX-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("租户");
  }

  private static Capa openCapa(Long tenantId, String capaNo) {
    return Capa.open(tenantId, capaNo, "SUP-1", "交付延误", "alice", plusDay());
  }

  private static Date plusDay() {
    return Date.from(NOW.toInstant().plusSeconds(86_400));
  }
}
