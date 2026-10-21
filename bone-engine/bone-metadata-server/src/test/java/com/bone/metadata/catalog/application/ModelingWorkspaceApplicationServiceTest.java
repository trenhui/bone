package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.dto.ModelingWorkspaceDTO;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.iam.IamApplicationRef;
import com.bone.metadata.catalog.domain.model.iam.IamModuleRef;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.repository.IamApplicationRepository;
import com.bone.metadata.catalog.domain.repository.IamModuleRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 建模工作台聚合应用服务单测（P0-1）。
 *
 * <p>固化的契约：① 三项统计与 recentDrafts 走同一 pageEntities 读模型（与业务对象页同口径， 禁两套计数逻辑）；② 应用→模块一次查全后分组（禁每应用一次查询的
 * N+1 复活）；③ 孤儿模块（appId 无对应应用）不虚构分组。
 */
@ExtendWith(MockitoExtension.class)
class ModelingWorkspaceApplicationServiceTest {

  private static final long TENANT = 7L;

  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private IamApplicationRepository iamApplicationRepository;
  @Mock private IamModuleRepository iamModuleRepository;
  @Mock private TenantProvider tenantProvider;

  @InjectMocks private ModelingWorkspaceApplicationService service;

  private static MetaEntity draft(long id, String code) {
    return MetaEntity.create(
        id, TENANT, code, code, code + "显示名", null, "t_" + code, 0, 1, null, 100L, null);
  }

  private static IamApplicationRef appRef(long id, String name) {
    IamApplicationRef ref = new IamApplicationRef();
    ReflectionTestUtils.setField(ref, "id", id);
    ReflectionTestUtils.setField(ref, "tenantId", TENANT);
    ReflectionTestUtils.setField(ref, "name", name);
    return ref;
  }

  private static IamModuleRef moduleRef(long id, long appId, String name) {
    IamModuleRef ref = new IamModuleRef();
    ReflectionTestUtils.setField(ref, "id", id);
    ReflectionTestUtils.setField(ref, "tenantId", TENANT);
    ReflectionTestUtils.setField(ref, "appId", appId);
    ReflectionTestUtils.setField(ref, "name", name);
    ReflectionTestUtils.setField(ref, "code", name);
    return ref;
  }

  @Test
  @DisplayName("聚合返回：统计 + 前6草稿 + 应用→模块分组，全部经读模型方法一次查全")
  void loadWorkspace_shouldAggregateStatsDraftsAndAppModules() {
    when(tenantProvider.currentTenantId()).thenReturn(TENANT);
    when(metaEntityRepository.pageEntities(TENANT, null, null, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 42L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 0, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 5L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 1, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 22L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 0, null, null, null, 1, 6))
        .thenReturn(
            PageResult.of(
                List.of(draft(1L, "expense_reimbursement"), draft(2L, "ec_order")), 5L, 1, 6));
    when(iamApplicationRepository.listByTenant(TENANT))
        .thenReturn(List.of(appRef(10L, "费用报销"), appRef(20L, "电商")));
    when(iamModuleRepository.listByTenant(TENANT))
        .thenReturn(
            List.of(
                moduleRef(100L, 10L, "报销单"),
                moduleRef(101L, 10L, "借款单"),
                moduleRef(200L, 20L, "订单")));

    ModelingWorkspaceDTO dto = service.loadWorkspace();

    assertThat(dto.entityTotal()).isEqualTo(42L);
    assertThat(dto.draftTotal()).isEqualTo(5L);
    assertThat(dto.publishedTotal()).isEqualTo(22L);
    assertThat(dto.recentDrafts())
        .extracting(MetaEntityDTO::getCode)
        .containsExactly("expense_reimbursement", "ec_order");
    assertThat(dto.appModules()).hasSize(2);
    assertThat(dto.appModules().get(0).appName()).isEqualTo("费用报销");
    assertThat(dto.appModules().get(0).modules())
        .extracting(ModelingWorkspaceDTO.ModuleItemDTO::name)
        .containsExactly("报销单", "借款单");
    assertThat(dto.appModules().get(1).modules())
        .extracting(ModelingWorkspaceDTO.ModuleItemDTO::id)
        .containsExactly(200L);

    // 同口径契约：统计必须走 pageEntities（与业务对象页同一读模型），不得另起 COUNT 口径
    verify(metaEntityRepository).pageEntities(TENANT, null, null, null, null, null, 1, 1);
    verify(metaEntityRepository).pageEntities(TENANT, null, 0, null, null, null, 1, 1);
    verify(metaEntityRepository).pageEntities(TENANT, null, 1, null, null, null, 1, 1);
    verify(metaEntityRepository)
        .pageEntities(eq(TENANT), eq(null), eq(0), eq(null), eq(null), eq(null), eq(1), eq(6));
    // 一次查全契约：应用与模块各一次，禁 per-app 循环查询
    verify(iamApplicationRepository).listByTenant(TENANT);
    verify(iamModuleRepository).listByTenant(TENANT);
  }

  @Test
  @DisplayName("孤儿模块（appId 无对应应用）不虚构分组——IAM 数据漂移不得在本端被放大成假结构")
  void orphanModule_shouldNotInventAppGroup() {
    when(tenantProvider.currentTenantId()).thenReturn(TENANT);
    when(metaEntityRepository.pageEntities(TENANT, null, null, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 0, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 1, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 0, null, null, null, 1, 6))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 6));
    when(iamApplicationRepository.listByTenant(TENANT)).thenReturn(List.of(appRef(10L, "仅此一个应用")));
    when(iamModuleRepository.listByTenant(TENANT))
        .thenReturn(List.of(moduleRef(100L, 10L, "报销单"), moduleRef(999L, 888L, "幽灵模块")));

    ModelingWorkspaceDTO dto = service.loadWorkspace();

    assertThat(dto.appModules()).hasSize(1);
    assertThat(dto.appModules().get(0).modules())
        .extracting(ModelingWorkspaceDTO.ModuleItemDTO::id)
        .containsExactly(100L);
  }

  @Test
  @DisplayName("无应用无实体：空统计 + 空结构，不抛异常（首次使用的空态路径）")
  void emptyTenant_shouldReturnZeroedAggregate() {
    when(tenantProvider.currentTenantId()).thenReturn(TENANT);
    when(metaEntityRepository.pageEntities(TENANT, null, null, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 0, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 1, null, null, null, 1, 1))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 1));
    when(metaEntityRepository.pageEntities(TENANT, null, 0, null, null, null, 1, 6))
        .thenReturn(PageResult.of(List.of(), 0L, 1, 6));
    when(iamApplicationRepository.listByTenant(TENANT)).thenReturn(List.of());
    when(iamModuleRepository.listByTenant(TENANT)).thenReturn(List.of());

    ModelingWorkspaceDTO dto = service.loadWorkspace();

    assertThat(dto.entityTotal()).isZero();
    assertThat(dto.draftTotal()).isZero();
    assertThat(dto.publishedTotal()).isZero();
    assertThat(dto.recentDrafts()).isEmpty();
    assertThat(dto.appModules()).isEmpty();
  }
}
