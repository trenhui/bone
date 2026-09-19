package com.bone.metadata.sdk.sql.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import org.junit.jupiter.api.Test;

class TenantSqlRewriterTest {

  private static final Long TENANT = 42L;

  @Test
  void auto_withMarker_replacesAnchorWithAliasedColumn() {
    String sql =
        "SELECT o.id AS order_id FROM t_order o WHERE o.id = :orderId /*bone:tenant*/ AND o.deleted = 0";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "o.tenant_id", TENANT, false);
    assertThat(out).contains("o.tenant_id = :__boneTenantId__").doesNotContain("/*bone:tenant*/");
    // 锚点位置：在 o.id = :orderId 之后、AND o.deleted = 0 之前
    assertThat(out.indexOf("o.tenant_id = :__boneTenantId__"))
        .isGreaterThan(out.indexOf("o.id = :orderId"))
        .isLessThan(out.indexOf("o.deleted = 0"));
  }

  @Test
  void auto_withMarkerAndSoftDelete_appendsDeletedClause() {
    String sql = "SELECT id FROM t_order WHERE id = :id /*bone:tenant*/";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, true);
    assertThat(out).contains("tenant_id = :__boneTenantId__ AND deleted = 0");
  }

  @Test
  void auto_withoutMarker_simpleWhere_appendsAnd() {
    String sql = "SELECT id FROM t_order WHERE id = :orderId";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false);
    assertThat(out)
        .isEqualTo("SELECT id FROM t_order WHERE id = :orderId AND tenant_id = :__boneTenantId__");
  }

  @Test
  void auto_withoutMarker_whereFollowedByOrderBy_appendsBeforeOrderBy() {
    String sql = "SELECT id FROM t_order WHERE id = :orderId ORDER BY created_at DESC";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false);
    assertThat(out)
        .contains("WHERE id = :orderId AND tenant_id = :__boneTenantId__ ORDER BY created_at DESC");
  }

  @Test
  void auto_withoutMarker_noWhereSingleTable_injectsWhereAtEnd() {
    String sql = "SELECT id FROM t_order";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false);
    assertThat(out).isEqualTo("SELECT id FROM t_order WHERE tenant_id = :__boneTenantId__");
  }

  @Test
  void auto_withoutMarker_noWhereSingleTable_withOrderBy_injectsBeforeOrderBy() {
    String sql = "SELECT id FROM t_order ORDER BY id";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false);
    assertThat(out).contains("FROM t_order WHERE tenant_id = :__boneTenantId__ ORDER BY id");
  }

  @Test
  void auto_withoutMarker_joinWithoutAnchor_throws() {
    // 无 WHERE 的 JOIN 查询：别名歧义，无法安全注入，必须要求作者补锚点。
    String sql = "SELECT o.id FROM t_order o JOIN t_order_item oi ON o.id = oi.order_id";
    assertThatThrownBy(
            () -> TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/*bone:tenant*/");
  }

  @Test
  void auto_joinWithMarkerAndWhere_injectsSafely() {
    // JOIN + 显式锚点：锚点位置即作者指定的租户列归属，安全。
    String sql =
        "SELECT o.id FROM t_order o JOIN t_order_item oi ON o.id = oi.order_id "
            + "WHERE o.id = :orderId /*bone:tenant*/ AND o.deleted = 0";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "o.tenant_id", TENANT, false);
    assertThat(out).contains("o.tenant_id = :__boneTenantId__").doesNotContain("/*bone:tenant*/");
    assertThat(out.indexOf("o.tenant_id = :__boneTenantId__"))
        .isGreaterThan(out.indexOf("o.id = :orderId"))
        .isLessThan(out.indexOf("o.deleted = 0"));
  }

  @Test
  void auto_joinWithWhereButNoAnchor_throws() {
    // JOIN 后即使有 WHERE（作为最后子句），仍因别名歧义无法安全定位租户列，必须补锚点。
    String sql =
        "SELECT o.id FROM t_order o JOIN t_order_item oi ON o.id = oi.order_id WHERE o.id = :orderId";
    assertThatThrownBy(
            () -> TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/*bone:tenant*/");
  }

  @Test
  void auto_nullTenantId_failsClosed() {
    String sql = "SELECT id FROM t_order WHERE id = :orderId";
    assertThatThrownBy(
            () -> TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", null, false))
        .isInstanceOf(MissingTenantContextException.class);
  }

  @Test
  void auto_withoutMarker_unionWithWhereInFirstArm_throwsInsteadOfPartialInjection() {
    // 反例：只过滤第一臂即为跨租户泄漏（第二臂完全不过滤），必须失败关闭。
    String sql = "SELECT id FROM t_order WHERE status = :status UNION SELECT id FROM t_order_item";
    assertThatThrownBy(
            () -> TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNION")
        .hasMessageContaining("/*bone:tenant*/");
  }

  @Test
  void auto_withoutMarker_unionWithoutWhere_throws() {
    String sql = "SELECT id FROM t_order UNION SELECT id FROM t_order_item";
    assertThatThrownBy(
            () -> TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNION");
  }

  @Test
  void auto_withoutMarker_derivedTable_throws() {
    String sql = "SELECT x.id FROM (SELECT id FROM t_order) x WHERE x.id = :id";
    assertThatThrownBy(
            () -> TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/*bone:tenant*/");
  }

  @Test
  void auto_unionWithMarker_injectsOnlyAtAnchor() {
    // 正例：作者显式锚点后不再走启发式，UNION 也可安全注入（每条 SELECT 各自带锚点）。
    String sql =
        "SELECT id FROM t_order WHERE status = :status /*bone:tenant*/ "
            + "UNION SELECT id FROM t_order_item WHERE order_id = :id /*bone:tenant*/";
    String out = TenantSqlRewriter.rewrite(sql, TenantScopeMode.AUTO, "tenant_id", TENANT, false);
    assertThat(out).doesNotContain("/*bone:tenant*/");
    assertThat(out.split("tenant_id = :__boneTenantId__", -1)).hasSize(3);
  }

  @Test
  void manual_unchanged() {
    String sql = "SELECT id FROM t_order WHERE id = :orderId AND tenant_id = :tenantId";
    assertThat(TenantSqlRewriter.rewrite(sql, TenantScopeMode.MANUAL, "tenant_id", TENANT, false))
        .isEqualTo(sql);
  }

  @Test
  void all_unchanged() {
    String sql = "SELECT tenant_id, id FROM t_order WHERE deleted = 0 AND status = 'CREATED'";
    assertThat(TenantSqlRewriter.rewrite(sql, TenantScopeMode.ALL, "tenant_id", TENANT, false))
        .isEqualTo(sql);
  }

  @Test
  void bypass_unchanged() {
    String sql = "SELECT id FROM t_order";
    assertThat(TenantSqlRewriter.rewrite(sql, TenantScopeMode.BYPASS, "tenant_id", null, false))
        .isEqualTo(sql);
  }
}
