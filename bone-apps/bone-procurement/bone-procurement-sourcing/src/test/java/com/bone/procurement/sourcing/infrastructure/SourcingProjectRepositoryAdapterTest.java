package com.bone.procurement.sourcing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.bone.procurement.sourcing.domain.gateway.SourcingProjectRepository;
import com.bone.procurement.sourcing.domain.model.Rfx;
import com.bone.procurement.sourcing.domain.model.SourcingProject;
import com.bone.procurement.sourcing.domain.repository.RfxSqlRepository;
import com.bone.procurement.sourcing.domain.repository.SourcingProjectSqlRepository;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 寻源 gateway 适配器：经 SDK 仓储读写，验证租户隔离与新实例读回一致。 */
class SourcingProjectRepositoryAdapterTest {

  @Test
  @DisplayName("save/findByProjectNo 委托 SDK 仓储，且按租户隔离")
  void saveAndFindByProjectNoAreTenantScoped() {
    FakeSdkStore<SourcingProject> projectStore = new FakeSdkStore<>();
    FakeSdkStore<Rfx> rfxStore = new FakeSdkStore<>();
    SourcingProjectRepository port = adapter(projectStore, rfxStore);

    Long id =
        port.save(
            SourcingProject.create(
                1L, "PRJ-1", "CAT-1", new BigDecimal("100"), "CNY", "STRATEGY", "owner-1"));
    port.save(
        SourcingProject.create(
            2L, "PRJ-1", "CAT-2", new BigDecimal("200"), "CNY", "STRATEGY", "owner-2"));

    assertThat(port.findByProjectNo(1L, "PRJ-1")).isPresent();
    assertThat(port.findByProjectNo(1L, "PRJ-1").orElseThrow().getId()).isEqualTo(id);
    assertThat(port.findByProjectNo(1L, "PRJ-1").orElseThrow().getCategoryId()).isEqualTo("CAT-1");
    assertThat(port.findByProjectNo(2L, "PRJ-1").orElseThrow().getCategoryId()).isEqualTo("CAT-2");
    assertThat(port.findByProjectNo(1L, "MISSING")).isEmpty();
  }

  @Test
  @DisplayName("saveRfx/findRfxByNo 委托 SDK 仓储，且按租户隔离")
  void saveRfxAndFindRfxByNoAreTenantScoped() {
    FakeSdkStore<SourcingProject> projectStore = new FakeSdkStore<>();
    FakeSdkStore<Rfx> rfxStore = new FakeSdkStore<>();
    SourcingProjectRepository port = adapter(projectStore, rfxStore);

    Date due = new Date(System.currentTimeMillis() + 86400000L);
    Long id = port.saveRfx(Rfx.create(1L, 10L, "RFX-1", "RFP", due));
    port.saveRfx(Rfx.create(2L, 20L, "RFX-1", "RFP", due));

    assertThat(port.findRfxByNo(1L, "RFX-1")).isPresent();
    assertThat(port.findRfxByNo(1L, "RFX-1").orElseThrow().getId()).isEqualTo(id);
    assertThat(port.findRfxByNo(1L, "RFX-1").orElseThrow().getProjectId()).isEqualTo(10L);
    assertThat(port.findRfxByNo(2L, "RFX-1").orElseThrow().getProjectId()).isEqualTo(20L);
    assertThat(port.findRfxByNo(1L, "MISSING")).isEmpty();
  }

  @Test
  @DisplayName("新适配器实例（模拟重启）读回同一份持久化数据，字段一致")
  void newAdapterInstanceReadsBackSameState() {
    FakeSdkStore<SourcingProject> projectStore = new FakeSdkStore<>();
    FakeSdkStore<Rfx> rfxStore = new FakeSdkStore<>();
    SourcingProjectRepository first = adapter(projectStore, rfxStore);

    Long id =
        first.save(
            SourcingProject.create(
                7L, "PRJ-7", "CAT-7", new BigDecimal("777"), "CNY", "STRATEGY", "owner-7"));

    SourcingProjectRepository restarted = adapter(projectStore, rfxStore);
    SourcingProject actual = restarted.findByProjectNo(7L, "PRJ-7").orElseThrow();
    assertThat(actual.getId()).isEqualTo(id);
    assertThat(actual.getTenantId()).isEqualTo(7L);
    assertThat(actual.getProjectNo()).isEqualTo("PRJ-7");
    assertThat(actual.getCategoryId()).isEqualTo("CAT-7");
    assertThat(actual.getBudgetAmount()).isEqualByComparingTo(new BigDecimal("777"));
    assertThat(actual.getCurrency()).isEqualTo("CNY");
    assertThat(actual.getOwnerId()).isEqualTo("owner-7");
  }

  private SourcingProjectRepository adapter(
      FakeSdkStore<SourcingProject> projectStore, FakeSdkStore<Rfx> rfxStore) {
    SourcingProjectSqlRepository projects =
        mock(SourcingProjectSqlRepository.class, CALLS_REAL_METHODS);
    doAnswer(invocation -> projectStore.save(invocation.getArgument(0))).when(projects).save(any());
    doAnswer(invocation -> projectStore.findByCriteria(invocation.getArgument(0)))
        .when(projects)
        .findByCriteria(any());

    RfxSqlRepository rfxes = mock(RfxSqlRepository.class, CALLS_REAL_METHODS);
    doAnswer(invocation -> rfxStore.save(invocation.getArgument(0))).when(rfxes).save(any());
    doAnswer(invocation -> rfxStore.findByCriteria(invocation.getArgument(0)))
        .when(rfxes)
        .findByCriteria(any());

    return new SourcingProjectRepositoryAdapter(projects, rfxes);
  }
}
