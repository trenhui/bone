package com.bone.claim.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.ItemCatalogPort;
import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 目录端口未接线时的行为契约。
 *
 * <p>这里断言的是<b>方向</b>：没接线必须表现为"没结论"（{@code UNKNOWN}，进而退补件），
 * 而不是"全部在目录内"（进而照全额赔）。后者错得安静，且与"目录里确实全都在"在数据上完全无法区分。
 */
@DisplayName("目录网关：没接线 = 没结论，不退化成放行")
class ItemCatalogGatewayTest {

  private static final List<ItemCatalogPort.Item> ITEMS =
      List.of(new ItemCatalogPort.Item(1L, "M001"), new ItemCatalogPort.Item(2L, "M002"));

  @Test
  @DisplayName("端口未接线：返回空 map（引擎逐条按 UNKNOWN fail-closed）")
  void unwiredReturnsEmpty() {
    ItemCatalogGateway gateway = ItemCatalogGateway.unwired();

    assertThat(gateway.check("NATIONAL_2024", LocalDate.parse("2026-10-01"), ITEMS)).isEmpty();
    assertThat(gateway.wired()).isFalse();
  }

  @Test
  @DisplayName("端口实现返回 null：同样按「没结论」处理，不抛 NPE 污染整条理算链")
  void nullResultTreatedAsEmpty() {
    ItemCatalogGateway gateway = ItemCatalogGateway.of((catalogCode, businessDate, items) -> null);

    assertThat(gateway.check("NATIONAL_2024", LocalDate.parse("2026-10-01"), ITEMS)).isEmpty();
    assertThat(gateway.wired()).isTrue();
  }

  @Test
  @DisplayName("端口已接线：结论原样透传（含 EXCLUDED，不做任何一侧的改写）")
  void wiredPassesThrough() {
    ItemCatalogGateway gateway =
        ItemCatalogGateway.of(
            (catalogCode, businessDate, items) ->
                Map.of(
                    1L, CatalogAdmission.Verdict.COVERED,
                    2L, CatalogAdmission.Verdict.EXCLUDED));

    assertThat(gateway.check("NATIONAL_2024", LocalDate.parse("2026-10-01"), ITEMS))
        .containsEntry(1L, CatalogAdmission.Verdict.COVERED)
        .containsEntry(2L, CatalogAdmission.Verdict.EXCLUDED);
  }
}
