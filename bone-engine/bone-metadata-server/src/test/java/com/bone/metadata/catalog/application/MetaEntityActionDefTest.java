package com.bone.metadata.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.UpsertActionDefCommand;
import com.bone.metadata.catalog.common.CatalogErrorCodes;
import com.bone.metadata.catalog.domain.gateway.ActionDefMaterializePort;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 动作定义建模入口（{@code /entities/{id}/action-defs}）的守卫与副作用测试。
 *
 * <p><b>判别力</b>：每个用例都锚定一条「删掉实现会变红」的行为——逃逸动作缺 handlerBean/幂等必须拒、软删不存在必须 404、 写后必须 evict runtime
 * 缓存（否则配了状态机在 TTL 内不生效）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetaEntityActionDefTest {

  @Mock private MetaEntityRepositoryFixture fixture;

  // 占位：真实依赖用不到（只测动作定义路径），保留字段以显式表达「不依赖物理结构/字段仓储」
  interface MetaEntityRepositoryFixture {}

  @Mock
  private com.bone.metadata.catalog.domain.repository.MetaEntityRepository metaEntityRepository;

  @Mock private com.bone.metadata.catalog.domain.repository.MetaFieldRepository metaFieldRepository;

  @Mock
  private com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository
      relationRepository;

  @Mock private com.bone.metadata.catalog.domain.service.IamModuleValidator iamModuleValidator;
  @Mock private com.bone.metadata.catalog.domain.gateway.TenantProvider tenantProvider;

  @Mock
  private com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway
      physicalStructureGateway;

  @Mock private com.bone.metadata.catalog.domain.gateway.CurrentUserProvider currentUserProvider;
  @Mock private com.bone.metadata.runtime.RuntimeEntityCacheEvictor cacheEvictor;
  @Mock private ActionDefMaterializePort actionDefMaterializePort;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private UiSchemaApplicationService uiSchemaApplicationService;

  private MetaEntityApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaEntityApplicationService(
            metaEntityRepository,
            metaFieldRepository,
            relationRepository,
            iamModuleValidator,
            tenantProvider,
            physicalStructureGateway,
            null,
            currentUserProvider,
            java.util.Optional.of(cacheEvictor),
            actionDefMaterializePort,
            domainEventPublisher,
            null,
            uiSchemaApplicationService);
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(metaEntityRepository.findById(9L)).thenReturn(entity());
  }

  private MetaEntity entity() {
    MetaEntity e =
        MetaEntity.create(
            9L, 1L, "报销单", "expense_claim", "报销单", null, "t_expense_claim", 1, 1, null, 58L, null);
    return e;
  }

  private UpsertActionDefCommand cmd(
      String actionCode, Boolean escape, String handler, Boolean idem) {
    UpsertActionDefCommand c = new UpsertActionDefCommand();
    c.setActionCode(actionCode);
    c.setLabel("动作 " + actionCode);
    c.setFromStatus("DRAFT");
    c.setToStatus("SUBMITTED");
    c.setEscape(escape);
    c.setHandlerBean(handler);
    c.setRequiresIdempotency(idem);
    c.setSortOrder(1);
    return c;
  }

  // ==================== 正常路径 ====================

  @Test
  @DisplayName("非逃逸动作：写入后必须 evict runtime 缓存（stateField 由动作定义推导，不 evict 则 TTL 内不生效）")
  void upsert_shouldWriteAndEvictCache() {
    service.upsertActionDef(9L, cmd("submit", false, null, false));

    ArgumentCaptor<com.bone.metadata.catalog.domain.gateway.ActionDefSpec> captor =
        ArgumentCaptor.forClass(com.bone.metadata.catalog.domain.gateway.ActionDefSpec.class);
    verify(actionDefMaterializePort).upsert(eq(1L), eq("expense_claim"), captor.capture());
    assertEquals("submit", captor.getValue().actionCode());
    verify(cacheEvictor).evict("expense_claim", 1L);
  }

  @Test
  @DisplayName("逃逸动作齐全（handlerBean + 幂等）→ 放行")
  void upsert_escapeActionWithHandlerAndIdempotency_shouldPass() {
    service.upsertActionDef(
        9L, cmd("confirmPayout", true, "expenseClaimConfirmPayoutHandler", true));
    verify(actionDefMaterializePort).upsert(eq(1L), eq("expense_claim"), any());
  }

  @Test
  @DisplayName("列出动作定义：按实体 code + 租户委派端口")
  void list_shouldDelegate() {
    when(actionDefMaterializePort.listByEntity(1L, "expense_claim")).thenReturn(List.of());
    assertEquals(0, service.listActionDefs(9L).size());
    verify(actionDefMaterializePort).listByEntity(1L, "expense_claim");
  }

  @Test
  @DisplayName("软删存在的动作 → evict 缓存（删掉最后一条会让 stateField 退化为 null，绕过状态机）")
  void delete_shouldEvictCache() {
    when(actionDefMaterializePort.softDeleteOne(1L, "expense_claim", "submit")).thenReturn(1);
    service.deleteActionDef(9L, "submit");
    verify(cacheEvictor).evict("expense_claim", 1L);
  }

  @Test
  @DisplayName("入口动作标记（0041）：entry 原样透传到 ActionDefSpec（全环机器靠它解死锁）")
  void upsert_entryFlag_passesToSpec() {
    UpsertActionDefCommand c = cmd("close", false, null, false);
    c.setFromStatus("OPEN");
    c.setEntry(true);
    service.upsertActionDef(9L, c);

    ArgumentCaptor<com.bone.metadata.catalog.domain.gateway.ActionDefSpec> captor =
        ArgumentCaptor.forClass(com.bone.metadata.catalog.domain.gateway.ActionDefSpec.class);
    verify(actionDefMaterializePort).upsert(eq(1L), eq("expense_claim"), captor.capture());
    assertEquals(Boolean.TRUE, captor.getValue().entry(), "entry 不得在服务层被吞");
  }

  @Test
  @DisplayName("入口动作缺 fromStatus → 400 ACTION_DEF_INVALID（空白源态的入口标记只会静默失效，必须显式报错）")
  void upsert_entryWithoutFromStatus_shouldReject() {
    UpsertActionDefCommand c = cmd("close", false, null, false);
    c.setFromStatus(null);
    c.setEntry(true);
    BizException e = assertThrows(BizException.class, () -> service.upsertActionDef(9L, c));
    assertEquals(CatalogErrorCodes.ACTION_DEF_INVALID, e.getErrorCode());
    verify(actionDefMaterializePort, never()).upsert(anyLong(), anyString(), any());
  }

  // ==================== 负向：守卫必须有牙齿 ====================

  @Test
  @DisplayName("逃逸动作缺 handlerBean → 400 ACTION_DEF_INVALID，且不得写库")
  void upsert_escapeWithoutHandler_shouldReject() {
    BizException e =
        assertThrows(
            BizException.class, () -> service.upsertActionDef(9L, cmd("pay", true, null, true)));
    assertEquals(CatalogErrorCodes.ACTION_DEF_INVALID, e.getErrorCode());
    assertEquals(400, e.getCode());
    verify(actionDefMaterializePort, never()).upsert(anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("逃逸动作未声明幂等 → 400（防重复打款；不自动替用户补）")
  void upsert_escapeWithoutIdempotency_shouldReject() {
    BizException e =
        assertThrows(
            BizException.class,
            () -> service.upsertActionDef(9L, cmd("pay", true, "someHandler", false)));
    assertEquals(CatalogErrorCodes.ACTION_DEF_INVALID, e.getErrorCode());
    assertEquals(400, e.getCode());
    verify(actionDefMaterializePort, never()).upsert(anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("软删不存在的动作 → 404 ACTION_DEF_NOT_FOUND")
  void delete_nonExisting_shouldReturn404() {
    when(actionDefMaterializePort.softDeleteOne(1L, "expense_claim", "ghost")).thenReturn(0);
    BizException e = assertThrows(BizException.class, () -> service.deleteActionDef(9L, "ghost"));
    assertEquals(CatalogErrorCodes.ACTION_DEF_NOT_FOUND, e.getErrorCode());
    assertEquals(404, e.getCode());
  }

  @Test
  @DisplayName("实体不存在 → 404（不得对不存在的实体建动作定义）")
  void upsert_entityNotFound_shouldReturn404() {
    when(metaEntityRepository.findById(9L)).thenReturn(null);
    BizException e =
        assertThrows(
            BizException.class,
            () -> service.upsertActionDef(9L, cmd("submit", false, null, false)));
    assertEquals(CatalogErrorCodes.ENTITY_NOT_FOUND, e.getErrorCode());
    assertEquals(404, e.getCode());
    verify(actionDefMaterializePort, never()).upsert(anyLong(), anyString(), any());
  }
}
