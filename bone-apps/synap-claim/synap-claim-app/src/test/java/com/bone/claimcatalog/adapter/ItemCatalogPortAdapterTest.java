package com.bone.claimcatalog.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.catalog.ItemCatalogGateway;
import com.bone.claim.application.port.out.CatalogDataPort;
import com.bone.claim.application.port.out.CatalogDataPort.CatalogSnapshot;
import com.bone.claim.application.port.out.ItemCatalogPort;
import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission.Verdict;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** fail-closed 判别力：判不出来一律 UNKNOWN 且 map 非空，绝不折叠成 COVERED/EXCLUDED。 */
class ItemCatalogPortAdapterTest {

  private static final LocalDate DAY = LocalDate.of(2024, 6, 1);

  @SuppressWarnings("unchecked")
  private ObjectProvider<CatalogDataPort> provider(CatalogDataPort port) {
    ObjectProvider<CatalogDataPort> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(port);
    return provider;
  }

  private List<ItemCatalogPort.Item> items() {
    return List.of(
        new ItemCatalogPort.Item(1L, "DRUG_A"),
        new ItemCatalogPort.Item(2L, "DRUG_B"),
        new ItemCatalogPort.Item(3L, " "),
        new ItemCatalogPort.Item(4L, null));
  }

  @Test
  void no_data_port_returns_all_unknown_non_empty_map() {
    ItemCatalogPortAdapter adapter = new ItemCatalogPortAdapter(provider(null));
    Map<Long, Verdict> verdicts = adapter.check("NATIONAL_2024", DAY, items());
    assertThat(verdicts).hasSize(4);
    assertThat(verdicts.values()).allMatch(v -> v == Verdict.UNKNOWN);
  }

  @Test
  void no_effective_version_returns_all_unknown() {
    CatalogDataPort empty = (code, date, itemCodes) -> Optional.empty();
    ItemCatalogPortAdapter adapter = new ItemCatalogPortAdapter(provider(empty));
    Map<Long, Verdict> verdicts = adapter.check("NATIONAL_2024", DAY, items());
    assertThat(verdicts).hasSize(4);
    assertThat(verdicts.values()).allMatch(v -> v == Verdict.UNKNOWN);
  }

  @Test
  void lookup_exception_is_swallowed_to_unknown() {
    CatalogDataPort boom =
        (code, date, itemCodes) -> {
          throw new IllegalStateException("catalog down");
        };
    ItemCatalogPortAdapter adapter = new ItemCatalogPortAdapter(provider(boom));
    Map<Long, Verdict> verdicts = adapter.check("NATIONAL_2024", DAY, items());
    assertThat(verdicts).hasSize(4);
    assertThat(verdicts.values()).allMatch(v -> v == Verdict.UNKNOWN);
  }

  @Test
  void effective_snapshot_passes_through_covered_and_excluded() {
    CatalogDataPort ok =
        (code, date, itemCodes) ->
            Optional.of(new CatalogSnapshot(code, date, Map.of("DRUG_A", Verdict.COVERED)));
    ItemCatalogPortAdapter adapter = new ItemCatalogPortAdapter(provider(ok));
    Map<Long, Verdict> verdicts = adapter.check("NATIONAL_2024", DAY, items());
    assertThat(verdicts.get(1L)).isEqualTo(Verdict.COVERED); // 目录内
    assertThat(verdicts.get(2L)).isEqualTo(Verdict.UNKNOWN); // 未收录，不擅自 EXCLUDED
    assertThat(verdicts.get(3L)).isEqualTo(Verdict.UNKNOWN); // 空编码
    assertThat(verdicts.get(4L)).isEqualTo(Verdict.UNKNOWN); // null 编码
  }

  @Test
  void integrates_with_gateway_without_changing_verdicts() {
    CatalogDataPort ok =
        (code, date, itemCodes) ->
            Optional.of(new CatalogSnapshot(code, date, Map.of("DRUG_A", Verdict.EXCLUDED)));
    ItemCatalogGateway gateway = ItemCatalogGateway.of(new ItemCatalogPortAdapter(provider(ok)));
    Map<Long, Verdict> verdicts = gateway.check("NATIONAL_2024", DAY, items());
    assertThat(verdicts.get(1L)).isEqualTo(Verdict.EXCLUDED);
    assertThat(verdicts.get(2L)).isEqualTo(Verdict.UNKNOWN);
  }
}
