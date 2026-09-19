package com.bone.metadata.sdk.query.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ADR-0029：可信租户过滤注入器契约测试（租户识别 / 失败关闭 / 逃生舱）。 */
class TenantFilterInjectorTest {

  private static final ColumnMetadata PK =
      new ColumnMetadata(
          "id", "id", Long.class, true, false, false, false, false, false, null, null, null);
  private static final ColumnMetadata TENANT =
      new ColumnMetadata(
          "tenant_id",
          "tenantId",
          Long.class,
          false,
          false,
          false,
          false,
          false,
          false,
          null,
          null,
          null);
  private static final ColumnMetadata SOFT_DEL =
      new ColumnMetadata(
          "deleted",
          "deleted",
          Boolean.class,
          false,
          false,
          false,
          false,
          false,
          true,
          null,
          null,
          null);

  private static TableMetadata tenantTable() {
    return new TableMetadata("t_order", List.of(PK, TENANT, SOFT_DEL));
  }

  private static TableMetadata nonTenantTable() {
    return new TableMetadata("t_platform", List.of(PK, SOFT_DEL));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void injectsTrustedTenantFromContext_forTenantScopedTable() {
    TenantContext.setTenantId(42L);
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    Criteria<?> criteria = Criteria.create();

    TenantFilterInjector.inject(where, params, tenantTable(), criteria, true);

    assertEquals(1, where.size());
    assertTrue(where.get(0).contains("m.tenant_id = :" + TenantFilterInjector.PARAM));
    assertEquals(42L, params.get(TenantFilterInjector.PARAM));
  }

  @Test
  void failClosed_whenTenantScopedAndContextNull() {
    TenantContext.clear();
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();

    assertThrows(
        MissingTenantContextException.class,
        () -> TenantFilterInjector.inject(where, params, tenantTable(), Criteria.create(), true));
    assertTrue(where.isEmpty());
  }

  @Test
  void escapeHatch_disablesInjectionEvenWhenContextNull() {
    TenantContext.clear();
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    Criteria<?> criteria = Criteria.create().disableTenantFilter();

    TenantFilterInjector.inject(where, params, tenantTable(), criteria, true);

    assertTrue(where.isEmpty());
    assertFalse(params.containsKey(TenantFilterInjector.PARAM));
  }

  @Test
  void neverInjects_forNonTenantTable() {
    TenantContext.clear();
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();

    TenantFilterInjector.inject(where, params, nonTenantTable(), Criteria.create(), true);

    assertTrue(where.isEmpty());
    assertFalse(params.containsKey(TenantFilterInjector.PARAM));
  }

  @Test
  void resolveInsertTenantValue_prefersContext_overEntity() {
    TenantContext.setTenantId(7L);
    var entity = new ObjectWithTenant(99L);

    Object resolved = TenantFilterInjector.resolveInsertTenantValue(tenantTable(), entity);

    assertEquals(7L, resolved);
  }

  @Test
  void resolveInsertTenantValue_fallsBackToEntity_whenContextNull() {
    TenantContext.clear();
    var entity = new ObjectWithTenant(99L);

    Object resolved = TenantFilterInjector.resolveInsertTenantValue(tenantTable(), entity);

    assertEquals(99L, resolved);
  }

  @Test
  void callerTenantEqFallback_whenContextNull() {
    TenantContext.clear();
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    Criteria<?> criteria = Criteria.create().eq("tenantId", 99L);

    TenantFilterInjector.inject(where, params, tenantTable(), criteria, true);

    assertEquals(1, where.size());
    assertTrue(where.get(0).contains("m.tenant_id = :" + TenantFilterInjector.PARAM));
    assertEquals(99L, params.get(TenantFilterInjector.PARAM));
  }

  @Test
  void contextWins_overCallerTenantEq_andWarns() {
    TenantContext.setTenantId(42L);
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    Criteria<?> criteria = Criteria.create().eq("tenantId", 7L);

    TenantFilterInjector.inject(where, params, tenantTable(), criteria, true);

    assertEquals(1, where.size());
    assertEquals(42L, params.get(TenantFilterInjector.PARAM));
  }

  @Test
  void failClosed_whenNoContextAndNoCallerTenant() {
    TenantContext.clear();
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    // caller 仅带 status 条件，未限定租户 → 上下文为空 → 失败关闭
    Criteria<?> criteria = Criteria.create().eq("status", "PENDING");

    assertThrows(
        MissingTenantContextException.class,
        () -> TenantFilterInjector.inject(where, params, tenantTable(), criteria, true));
    assertTrue(where.isEmpty());
  }

  /** 仅用于读取 tenantId 字段的最小载体。 */
  static class ObjectWithTenant {
    @SuppressWarnings("unused")
    private final Long tenantId;

    ObjectWithTenant(Long tenantId) {
      this.tenantId = tenantId;
    }
  }
}
