package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.CodeMappingApplicationService.MappingHealthView;
import com.bone.claim.application.CodeMappingApplicationService.MappingView;
import com.bone.claim.application.CodeMappingApplicationService.PageView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.mapping.CodeMapping;
import com.bone.claim.domain.model.mapping.MappingSource;
import com.bone.claim.domain.model.mapping.MappingType;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.mapping.MatchRule;
import com.bone.claim.domain.model.reference.RefHospital;
import com.bone.claim.domain.repository.CodeMappingRepository;
import com.bone.claim.domain.repository.FieldBindingRepository;
import com.bone.claim.domain.repository.InsurerCodeItemRepository;
import com.bone.claim.domain.repository.MappingMissRepository;
import com.bone.claim.domain.repository.RefDiseaseRepository;
import com.bone.claim.domain.repository.RefDrugRepository;
import com.bone.claim.domain.repository.RefFeeCategoryRepository;
import com.bone.claim.domain.repository.RefHospitalRepository;
import com.bone.claim.domain.repository.StdCodeAliasRepository;
import com.bone.claim.domain.repository.StdCodeSplitRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 管理面查询增强（task 2.1）：标准名称回填 / 历史展开 / 健康度统计。
 *
 * <p>三类判据：
 *
 * <ul>
 *   <li>标准名称由目录回填，目录缺项显示空串（幽灵码可见信号）；
 *   <li>includeHistory=false 时仓储收到 ACTIVE 过滤语义（以查询参数断言，变异杀：删 includeHistory 分支即红）；
 *   <li>健康度覆盖率 = distinct 标准码 / 目录总数；CERT_TYPE 分母未知返回 -1，不算出荒谬覆盖率。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MappingManagementQueryTest {

  @Mock CodeMappingRepository codeMappingRepository;
  @Mock MappingMissRepository mappingMissRepository;
  @Mock StdCodeAliasRepository stdCodeAliasRepository;
  @Mock StdCodeSplitRepository stdCodeSplitRepository;
  @Mock com.bone.claim.domain.repository.ClaimInsurerRepository claimInsurerRepository;
  @Mock InsurerCodeItemRepository insurerCodeItemRepository;
  @Mock FieldBindingRepository fieldBindingRepository;
  @Mock RefHospitalRepository refHospitalRepository;
  @Mock RefDrugRepository refDrugRepository;
  @Mock RefDiseaseRepository refDiseaseRepository;
  @Mock RefFeeCategoryRepository refFeeCategoryRepository;
  @Mock TenantPort tenantPort;

  @InjectMocks CodeMappingApplicationService service;

  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  private CodeMapping activeMapping(long id, String itemCode, String stdCode) {
    return CodeMapping.of(
        id,
        1L,
        "YCBX",
        MasterDataDomain.HOSPITAL,
        itemCode,
        "",
        stdCode,
        MappingType.EXACT,
        MatchRule.MANUAL,
        null,
        null,
        MappingSource.MANUAL,
        "B1",
        NOW,
        NOW);
  }

  @Test
  void listMappings_backfillsStandardNameFromCatalog() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(codeMappingRepository.findPage(
            anyLong(),
            anyString(),
            anyString(),
            nullable(String.class),
            anyBoolean(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of(activeMapping(1L, "XHYY", "H001")));
    RefHospital hospital = org.mockito.Mockito.mock(RefHospital.class);
    when(hospital.getHospitalName()).thenReturn("协和医院");
    when(refHospitalRepository.findByCodes(anyLong(), anyList())).thenReturn(List.of(hospital));
    when(codeMappingRepository.countPage(
            anyLong(), anyString(), anyString(), nullable(String.class), anyBoolean()))
        .thenReturn(1L);

    PageView<MappingView> view = service.listMappings("YCBX", "HOSPITAL", null, false, 1, 10);

    assertThat(view.records().get(0).standardName()).isEqualTo("协和医院");
  }

  @Test
  void listMappings_unknownStandardCode_getsEmptyName() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(codeMappingRepository.findPage(
            anyLong(),
            anyString(),
            anyString(),
            nullable(String.class),
            anyBoolean(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of(activeMapping(1L, "GHOST", "H404")));
    when(refHospitalRepository.findByCodes(anyLong(), anyList())).thenReturn(List.of());
    when(codeMappingRepository.countPage(
            anyLong(), anyString(), anyString(), nullable(String.class), anyBoolean()))
        .thenReturn(1L);

    PageView<MappingView> view = service.listMappings("YCBX", "HOSPITAL", null, false, 1, 10);

    assertThat(view.records().get(0).standardName()).isEmpty();
  }

  @Test
  void listMappings_forwardsWithoutActiveFilter_whenHistoryRequested() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(codeMappingRepository.findPage(
            anyLong(),
            anyString(),
            anyString(),
            nullable(String.class),
            anyBoolean(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of());
    when(codeMappingRepository.countPage(
            anyLong(), anyString(), anyString(), nullable(String.class), anyBoolean()))
        .thenReturn(0L);

    service.listMappings("YCBX", "HOSPITAL", "XH", true, 1, 10);

    verify(codeMappingRepository).findPage(1L, "YCBX", "HOSPITAL", "XH", true, 1, 10);
  }

  @Test
  void mappingHealth_coverageIsDistinctStandardCodesOverCatalog() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(refHospitalRepository.countAll(1L)).thenReturn(10L);
    when(codeMappingRepository.findActiveByInsurerDomain(1L, "YCBX", "HOSPITAL"))
        .thenReturn(
            List.of(
                activeMapping(1L, "A", "H001"),
                activeMapping(2L, "B", "H001"), // 同标准码重复映射，distinct 后只算一次
                activeMapping(3L, "C", "H002")));
    when(mappingMissRepository.countOpen(1L, "YCBX", "HOSPITAL")).thenReturn(3L);
    when(mappingMissRepository.countFirstSeenSince(
            org.mockito.ArgumentMatchers.eq(1L), anyString(), anyString(), any(Instant.class)))
        .thenReturn(2L);

    MappingHealthView health = service.mappingHealth("YCBX", "HOSPITAL");

    assertThat(health.catalogTotal()).isEqualTo(10L);
    assertThat(health.mappedStandardCodes()).isEqualTo(2L);
    assertThat(health.coverage()).isEqualTo(0.2);
    assertThat(health.openMissCount()).isEqualTo(3L);
    assertThat(health.recentMissCount()).isEqualTo(2L);
  }

  @Test
  void mappingHealth_certTypeDenominatorUnknown_returnsNegativeCoverage() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(mappingMissRepository.countOpen(1L, "YCBX", "CERT_TYPE")).thenReturn(1L);
    when(mappingMissRepository.countFirstSeenSince(
            org.mockito.ArgumentMatchers.eq(1L), anyString(), anyString(), any(Instant.class)))
        .thenReturn(0L);

    MappingHealthView health = service.mappingHealth("YCBX", "CERT_TYPE");

    assertThat(health.catalogTotal()).isEqualTo(-1L);
    assertThat(health.coverage()).isNegative();
    assertThat(health.openMissCount()).isEqualTo(1L);
  }
}
