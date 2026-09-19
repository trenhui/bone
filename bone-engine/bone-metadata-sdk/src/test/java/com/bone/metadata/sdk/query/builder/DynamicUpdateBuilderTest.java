package com.bone.metadata.sdk.query.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.context.DynamicUpdateContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 写路径（{@code save} / {@code update}）的租户护栏契约测试。
 *
 * <p>与读路径的关键差异：本构建器<b>没有</b> caller-EQ 兜底，也<b>不认</b> {@code Criteria#disableTenantFilter()}——{@link
 * TenantContext} 缺失一律失败关闭。因此定时任务 / Outbox 中继等异步入口 必须先声明租户（{@code
 * com.bone.core.tenant.context.TenantContextRunner}），否则整轮写操作抛异常回滚。
 */
class DynamicUpdateBuilderTest {

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
  private static final ColumnMetadata STATUS =
      new ColumnMetadata(
          "status",
          "status",
          String.class,
          false,
          false,
          false,
          false,
          false,
          false,
          null,
          null,
          null);
  private static final ColumnMetadata VERSION =
      new ColumnMetadata(
          "version",
          "version",
          Long.class,
          false,
          true,
          true,
          false,
          false,
          false,
          null,
          null,
          null);

  private final DynamicUpdateBuilder builder = new DynamicUpdateBuilder();

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void usesTrustedContext_notEntityField_whenTheyDisagree() {
    // 上下文 99 ≠ 实体字段 42：写路径的租户只能来自可信上下文——实体字段不是授权来源。
    // （若两者同值，断言无法区分"取上下文"与"取实体字段"，会形成假绿。）
    TenantContext.setTenantId(99L);

    CompiledQuery query =
        builder.build(new DynamicUpdateContext(tenantTable(), new Row(7L, 42L, "PAID")));

    assertTrue(query.getSql().contains("WHERE id = :id AND tenant_id = :_sdk_tenant_id"));
    assertEquals(99L, query.getParameters().get(TenantFilterInjector.PARAM));
    // SET 子句照常写实体状态（42），WHERE 用上下文（99）——两者互不污染
    assertEquals(42L, query.getParameters().get("tenant_id"));
  }

  @Test
  void failClosed_whenTenantContextMissing() {
    TenantContext.clear();

    assertThrows(
        MissingTenantContextException.class,
        () -> builder.build(new DynamicUpdateContext(tenantTable(), new Row(7L, 42L, "PAID"))));
  }

  @Test
  void failClosed_forNonNumericTenantCode() {
    // 非数值型租户编码（如 "system"）无法转 Long ⇒ 写路径同样失败关闭，不会退化为无租户 UPDATE
    TenantContext.setTenantId("system");

    assertThrows(
        MissingTenantContextException.class,
        () -> builder.build(new DynamicUpdateContext(tenantTable(), new Row(7L, 42L, "PAID"))));
  }

  @Test
  void nonTenantScopedTable_needsNoContext() {
    TenantContext.clear();
    TableMetadata plain = new TableMetadata("t_platform", List.of(PK, STATUS));

    CompiledQuery query = builder.build(new DynamicUpdateContext(plain, new Row(7L, null, "OK")));

    assertTrue(query.getSql().contains("WHERE id = :id"));
    assertFalse(query.getSql().contains("tenant_id"));
  }

  @Test
  void versionedTable_bumpsVersionAndGuardsWithOldValue() {
    TenantContext.setTenantId(99L);

    CompiledQuery query =
        builder.build(
            new DynamicUpdateContext(versionedTable(), new VersionedRow(7L, 42L, "PAID", 3L)));

    // SET：version 自增（不是逐字段赋值 version = :version）
    assertTrue(query.getSql().contains("version = version + 1"));
    assertFalse(query.getSql().contains("version = :version"));
    // WHERE：携带实体加载时的旧值
    assertTrue(query.getSql().contains("AND version = :" + DynamicUpdateBuilder.VERSION_PARAM));
    assertEquals(3L, query.getParameters().get(DynamicUpdateBuilder.VERSION_PARAM));
    // 租户护栏不受影响
    assertEquals(99L, query.getParameters().get(TenantFilterInjector.PARAM));
  }

  @Test
  void versionedTable_excludesVersionFromPerFieldSetParameters() {
    TenantContext.setTenantId(99L);

    CompiledQuery query =
        builder.build(
            new DynamicUpdateContext(versionedTable(), new VersionedRow(7L, 42L, "PAID", 5L)));

    // per-field SET 不再把 version 当作普通列塞进参数表（避免 SET version = :version 与自增冲突）
    assertFalse(query.getParameters().containsKey("version"));
    assertEquals(5L, query.getParameters().get(DynamicUpdateBuilder.VERSION_PARAM));
  }

  @Test
  void nonVersionedTable_omitsVersionClause() {
    TenantContext.setTenantId(99L);

    CompiledQuery query =
        builder.build(new DynamicUpdateContext(tenantTable(), new Row(7L, 42L, "PAID")));

    assertFalse(query.getSql().contains("version"));
  }

  private static TableMetadata tenantTable() {
    return new TableMetadata("t_order", List.of(PK, TENANT, STATUS));
  }

  private static TableMetadata versionedTable() {
    return new TableMetadata("t_order", List.of(PK, TENANT, STATUS, VERSION));
  }

  /** 最小可更新实体：字段名必须与列元数据的 {@code fieldName} 对齐（构建器按字段反射取值）。 */
  @SuppressWarnings("unused")
  static class Row {
    private final Long id;
    private final Long tenantId;
    private final String status;

    Row(Long id, Long tenantId, String status) {
      this.id = id;
      this.tenantId = tenantId;
      this.status = status;
    }
  }

  /** 带 {@code @Version} 语义（version 列）的实体：字段名 {@code version} 与列元数据对齐。 */
  @SuppressWarnings("unused")
  static class VersionedRow {
    private final Long id;
    private final Long tenantId;
    private final String status;
    private final Long version;

    VersionedRow(Long id, Long tenantId, String status, Long version) {
      this.id = id;
      this.tenantId = tenantId;
      this.status = status;
      this.version = version;
    }
  }
}
