package com.bone.metadata.sdk.query.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.bone.core.annotation.Id;
import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ADR-0029 补口：FluentQuery（DSL）通道租户注入契约测试。
 *
 * <p>修复前 DSL 通道（SqlBuilder）完全不注入租户过滤、也不失败关闭——租户可跨租户读全表（实测租户 1001 经 {@code pageByStatus}
 * 看到平台行）。本套测试锁定与 Criteria 通道（{@code TenantFilterInjectorTest}）一致的四条不变量： 可信上下文优先 / caller EQ 兜底 /
 * 无兜底失败关闭 / 非租户表永不注入。
 */
class FluentQueryTenantScopeTest {

  private SqlExecutor executor;
  private final AtomicReference<CompiledQuery> lastQuery = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    executor = Mockito.mock(SqlExecutor.class);
    doAnswer(
            inv -> {
              lastQuery.set(inv.getArgument(0));
              return List.of();
            })
        .when(executor)
        .queryList(any(), any());
    doAnswer(
            inv -> {
              lastQuery.set(inv.getArgument(0));
              return List.of();
            })
        .when(executor)
        .querySingle(any(), any());
    doAnswer(
            inv -> {
              lastQuery.set(inv.getArgument(0));
              return 0L;
            })
        .when(executor)
        .count(any());
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  // ===== 可信上下文优先 =====

  @Test
  void injectsTenantFromContext_withCallerConditionsParensAnded() {
    TenantContext.setTenantId(42L);

    new DefaultFluentQuery<>(TenantEntity.class, executor)
        .where(TenantEntity::getName)
        .eq("alpha")
        .list();

    CompiledQuery q = lastQuery.get();
    assertTrue(
        q.getSql().contains("(t.name = :p1) AND t.tenant_id = :p2"),
        "caller 条件须括号包裹再 AND 租户条件，实际: " + q.getSql());
    assertEquals("alpha", q.getParameters().get("p1"));
    assertEquals(42L, q.getParameters().get("p2"));
  }

  @Test
  void injectsTenantFromContext_whenNoCallerConditions() {
    TenantContext.setTenantId(42L);

    new DefaultFluentQuery<>(TenantEntity.class, executor).list();

    CompiledQuery q = lastQuery.get();
    assertTrue(q.getSql().contains(" WHERE t.tenant_id = :p1"), "实际: " + q.getSql());
    assertEquals(42L, q.getParameters().get("p1"));
  }

  /** caller 用 OR 分组时，租户条件必须在括号外——否则 OR 链会击穿隔离。 */
  @Test
  void orGroupCannotBreachTenantIsolation() {
    TenantContext.setTenantId(42L);

    new DefaultFluentQuery<>(TenantEntity.class, executor)
        .where(TenantEntity::getName)
        .eq("a")
        .or(TenantEntity::getName)
        .eq("b")
        .list();

    CompiledQuery q = lastQuery.get();
    assertTrue(
        q.getSql().contains("(t.name = :p1 OR t.name = :p2) AND t.tenant_id = :p3"),
        "实际: " + q.getSql());
    assertEquals(42L, q.getParameters().get("p3"));
  }

  // ===== caller EQ 兜底（无上下文，如后台任务链路） =====

  @Test
  void callerTenantEqFallsBack_whenContextNull() {
    TenantContext.clear();

    new DefaultFluentQuery<>(TenantEntity.class, executor)
        .where(TenantEntity::getTenantId)
        .eq(99L)
        .list();

    CompiledQuery q = lastQuery.get();
    assertTrue(q.getSql().contains("t.tenant_id = :p2"), "实际: " + q.getSql());
    assertEquals(99L, q.getParameters().get("p2"));
  }

  // ===== 失败关闭 =====

  @Test
  void failClosed_whenTenantScopedAndNoContextAndNoCallerTenant() {
    TenantContext.clear();

    assertThrows(
        MissingTenantContextException.class,
        () ->
            new DefaultFluentQuery<>(TenantEntity.class, executor)
                .where(TenantEntity::getName)
                .eq("x")
                .list());
    assertNull(lastQuery.get(), "失败关闭时不得执行任何 SQL");
  }

  @Test
  void failClosed_onCountPath() {
    TenantContext.clear();

    assertThrows(
        MissingTenantContextException.class,
        () -> new DefaultFluentQuery<>(TenantEntity.class, executor).count());
  }

  // ===== 非租户表永不注入（如 DomainTemplate 这类全局目录） =====

  @Test
  void neverInjectsOrThrows_forNonTenantEntity() {
    TenantContext.clear();

    new DefaultFluentQuery<>(PlainEntity.class, executor)
        .where(PlainEntity::getName)
        .eq("global")
        .list();

    CompiledQuery q = lastQuery.get();
    assertFalse(q.getSql().contains("tenant_id"), "实际: " + q.getSql());
    assertTrue(q.getSql().contains("t.name = :p1"));
  }

  // ===== COUNT 通道同语义 =====

  @Test
  void countPathInjectsTenantToo() {
    TenantContext.setTenantId(7L);

    new DefaultFluentQuery<>(TenantEntity.class, executor)
        .where(TenantEntity::getName)
        .eq("x")
        .count();

    CompiledQuery q = lastQuery.get();
    assertTrue(q.getSql().startsWith("SELECT COUNT(*)"), "实际: " + q.getSql());
    assertTrue(q.getSql().contains("(name = :p1) AND tenant_id = :p2"), "实际: " + q.getSql());
    assertEquals(7L, q.getParameters().get("p2"));
  }

  @Test
  void contextWins_overCallerTenantEq() {
    TenantContext.setTenantId(42L);

    new DefaultFluentQuery<>(TenantEntity.class, executor)
        .where(TenantEntity::getTenantId)
        .eq(7L)
        .list();

    CompiledQuery q = lastQuery.get();
    // caller 值留在 SQL（p1=7）但注入值来自可信上下文（p2=42）：两条件 AND 后恒空 = 失败安全
    assertEquals(7L, q.getParameters().get("p1"));
    assertEquals(42L, q.getParameters().get("p2"));
  }

  // ===== 测试实体 =====

  @Getter
  @Setter
  @NoArgsConstructor
  @Table("dsl_tenant_entity")
  public static class TenantEntity {
    @Id private Long id;
    private String name;
    private Long tenantId;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @Table("dsl_plain_entity")
  public static class PlainEntity {
    @Id private Long id;
    private String name;
  }
}
