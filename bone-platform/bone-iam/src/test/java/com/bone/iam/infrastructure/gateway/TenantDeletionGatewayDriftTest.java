package com.bone.iam.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 租户离场清退的「登记漂移」守卫（联调实测缺陷回归）。
 *
 * <p><b>缺陷：</b>{@code mdm_record_version} / {@code mdm_record_category} 是 {@code mdm_record} 的子表（只有
 * {@code record_id}，<b>没有 {@code tenant_id}</b>），却被直接登记进 {@code TENANT_TABLES} 走 {@code DELETE ...
 * WHERE tenant_id = ?}。结果：
 *
 * <ul>
 *   <li>SQL 抛 {@code SQLSyntaxErrorException: Unknown column 'tenant_id' in 'where clause'}；
 *   <li>方法带 {@code @Transactional} ⇒ 整批回滚，前 60+ 张表的清退全部作废；
 *   <li>{@code DELETE /api/v1/iam/tenants/{id}} 恒返回 <b>500</b>，UI「删除租户」完全不可用。
 * </ul>
 *
 * <p><b>修复后契约：</b>
 *
 * <ol>
 *   <li>子表走 JOIN 父表清退（与既有的 {@code iam_role_permission} 同口径），且必须在父表被清掉之前执行；
 *   <li>逐表探测 {@code tenant_id} 列：缺列则跳过并 WARN —— 让单个漂移只降级为「该表未清退 + 告警」， 而不是把整个租户删除功能打挂。
 * </ol>
 *
 * <p><b>负向探针：</b>把 {@code hasTenantIdColumn} 的跳过分支删掉（改为无条件 {@code jdbcTemplate.update("DELETE FROM
 * " + table + ...)}），{@link #skipsTableWithoutTenantIdColumn()} 必须变红。
 */
@ExtendWith(MockitoExtension.class)
class TenantDeletionGatewayDriftTest {

  @Mock NamedParameterJdbcTemplate jdbcTemplate;
  @Mock JdbcTemplate plainJdbcTemplate;

  @InjectMocks TenantDeletionGatewayAdapter adapter;

  /** 让所有表都「有 tenant_id」，除了指定的漂移表。 */
  private void stubHasColumn(List<String> withoutTenantId) {
    when(jdbcTemplate.queryForObject(
            anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
        .thenAnswer(
            inv -> {
              MapSqlParameterSource p = inv.getArgument(1);
              Object t = p != null ? p.getValue("tableName") : null;
              return withoutTenantId.contains(String.valueOf(t)) ? 0 : 1;
            });
  }

  @Test
  void purgesChildTablesViaJoinOnTenantScopedParent() {
    stubHasColumn(List.of());
    when(jdbcTemplate.getJdbcTemplate()).thenReturn(plainJdbcTemplate);

    adapter.purgeTenantData(42L);

    ArgumentCaptor<String> sqls = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
        .update(sqls.capture(), any(MapSqlParameterSource.class));
    List<String> all = sqls.getAllValues();

    // 两个 mdm 子表必须走 JOIN 父表清退，而不是 WHERE tenant_id
    assertThat(all)
        .anyMatch(s -> s.contains("mdm_record_version") && s.contains("INNER JOIN mdm_record"));
    assertThat(all)
        .anyMatch(s -> s.contains("mdm_record_category") && s.contains("INNER JOIN mdm_record"));
    assertThat(all).anyMatch(s -> s.contains("iam_role_permission") && s.contains("INNER JOIN"));
  }

  @Test
  void skipsTableWithoutTenantIdColumn() {
    stubHasColumn(List.of("mdm_drifted_table"));
    when(jdbcTemplate.getJdbcTemplate()).thenReturn(plainJdbcTemplate);

    adapter.purgeTenantData(7L);

    // 漂移表不能被直删（否则 Unknown column 'tenant_id' ⇒ 整个租户删除 500）
    verify(jdbcTemplate, never())
        .update(contains("mdm_drifted_table"), any(MapSqlParameterSource.class));
  }

  /** 有 tenant_id 的表仍要被正常清退：韧性改造不能把正常路径一起短路掉。 */
  @Test
  void stillPurgesTablesThatHaveTenantId() {
    stubHasColumn(List.of("mdm_drifted_table"));
    when(jdbcTemplate.getJdbcTemplate()).thenReturn(plainJdbcTemplate);

    adapter.purgeTenantData(7L);

    verify(jdbcTemplate, times(1))
        .update(
            eq("DELETE FROM iam_account WHERE tenant_id = :tenantId"),
            any(MapSqlParameterSource.class));
    verify(jdbcTemplate, times(1))
        .update(
            eq("DELETE FROM iam_role WHERE tenant_id = :tenantId"),
            any(MapSqlParameterSource.class));
  }

  /** FOREIGN_KEY_CHECKS 必须成对：关闭后一定要恢复，否则会污染连接池里这条连接。 */
  @Test
  void foreignKeyChecksRestored() {
    stubHasColumn(List.of());
    when(jdbcTemplate.getJdbcTemplate()).thenReturn(plainJdbcTemplate);

    adapter.purgeTenantData(7L);

    verify(plainJdbcTemplate).execute("SET FOREIGN_KEY_CHECKS = 0");
    verify(plainJdbcTemplate).execute("SET FOREIGN_KEY_CHECKS = 1");
  }
}
