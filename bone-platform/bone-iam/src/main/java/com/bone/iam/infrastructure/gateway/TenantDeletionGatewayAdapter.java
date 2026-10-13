package com.bone.iam.infrastructure.gateway;

import com.bone.iam.domain.gateway.TenantDeletionGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按租户清理全部租户作用域表（无 DB 外键，顺序手动编排）。实现 {@code domain/gateway/TenantDeletionGateway}（E-13.3）。
 *
 * <p>清单数据源：多租户数据隔离方案设计 §3 矩阵 + {@code bone-init.sql} 含 {@code tenant_id} 的表。 一律用 {@code WHERE
 * tenant_id = :tenantId}，平台 0 值行（平台/全局表、模板层、种子层）自然不受影响。
 *
 * <p>明确排除：平台/全局表（{@code iam_tenant}/{@code iam_permission}、{@code mdm_*} 平台模板层、 {@code
 * meta_model_template*}）、跨租户中继表（{@code int_outbox}）、blueprint 样板（{@code t_order}/{@code bp_*}）、
 * 待核实/零引用表（{@code cnsl_*}、{@code gen_type_mapping}）。新增租户作用域表须同步登记到此清单（门禁见 §8 项 9）。
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TenantDeletionGatewayAdapter implements TenantDeletionGateway {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  /** 全部租户隔离表（按模块分组，顺序不影响正确性——见 {@link #purgeTenantData} 的 FK 关闭策略）。 */
  private static final List<String> TENANT_TABLES =
      List.of(
          // bone-iam
          "iam_account",
          "iam_dept",
          "iam_role",
          "iam_menu",
          "bone_application",
          "bone_module",
          "bone_app_permission",
          "iam_account_role",
          "iam_audit_log",
          "iam_audit_settings",
          "iam_policy",
          "iam_refresh_token",
          // bone-system
          "sys_config",
          // 2026-10-03 补登：以下 5 张是 sys_config / sys_dict 的子表，含 tenant_id 且有活跃实体
          // （@Table 分别映射到 ConfigHistory / SysDictHierarchy / SysDictItem /
          // SysDictItemText / SysDictType），此前只登了父表 sys_config、sys_dict，
          // 租户离场会残留这些子表数据。门禁 scripts/check-tenant-deletion-coverage.py 报出。
          "sys_config_history",
          "sys_log",
          "sys_dict",
          "sys_dict_type",
          "sys_dict_hierarchy",
          "sys_dict_item",
          "sys_dict_item_text",
          "sys_schedule_task",
          "sys_alert_rule",
          "sys_alert_event",
          // bone-masterdata
          "mdm_entity",
          "mdm_field",
          "mdm_record",
          // mdm_record_version / mdm_record_category 是 mdm_record 的**子表**（只有 record_id，无
          // tenant_id），
          // 不能按 tenant_id 直删——它们走下面的 JOIN 子表清退分支，见 CHILD_TABLE_PURGE_SQL。
          "mdm_category",
          "mdm_steward",
          "mdm_steward_scope",
          "mdm_entity_subscription",
          "mdm_qcheck_task",
          "mdm_qcheck_detail",
          "mdm_qcheck_report",
          "mdm_quality_issue",
          "mdm_model_drift",
          "mdm_feedback",
          "mdm_reference_value_tenant",
          "md_lineage",
          "md_standard",
          "md_quality_rule",
          "md_entity",
          "md_field",
          "md_record",
          // bone-integration
          "int_flow",
          "int_flow_node",
          "int_flow_connection",
          "int_connector",
          "int_execution_log",
          "int_dead_letter",
          "int_template",
          // bone-metadata
          "meta_entity",
          "meta_field",
          "meta_entity_relation",
          "meta_code_template",
          // bone-generator
          "gen_table_metadata",
          "gen_column_metadata",
          "gen_generation_task",
          "gen_code_generation_history",
          "gen_code_template",
          "gen_data_source",
          // bone-extension
          "exts_extension_point",
          "exts_extension_impl",
          "exts_plugin_execution_log",
          "exts_audit_log",
          "exts_plugin_version",
          // bone-notification（R9：站内信按租户隔离，用户 PII）
          "ntf_message");

  /**
   * 无 {@code tenant_id} 的<strong>子表</strong>：只能挂靠租户作用域父表经 JOIN 清退。
   *
   * <p>{@code iam_role_permission} 是最早的一个（挂 {@code iam_role}）；{@code mdm_record_version} 与 {@code
   * mdm_record_category} 同形（只有 {@code record_id}，挂 {@code mdm_record}）—— 它们曾被直接写进 {@link
   * #TENANT_TABLES} 走 {@code WHERE tenant_id = ?}，而库里根本没有该列， 于是 <b>整个租户删除
   * 500</b>（SQLSyntaxErrorException: Unknown column 'tenant_id'）， 且因 {@code @Transactional} 全批回滚 ⇒
   * 租户永远删不掉。
   *
   * <p>必须在父表（{@link #TENANT_TABLES}）被清掉<strong>之前</strong>执行，否则 JOIN 不到任何父行。
   */
  private static final List<String> CHILD_TABLE_PURGE_SQL =
      List.of(
          "DELETE rp FROM iam_role_permission rp INNER JOIN iam_role r ON r.id = rp.role_id"
              + " WHERE r.tenant_id = :tenantId",
          "DELETE rv FROM mdm_record_version rv INNER JOIN mdm_record r ON r.id = rv.record_id"
              + " WHERE r.tenant_id = :tenantId",
          "DELETE rc FROM mdm_record_category rc INNER JOIN mdm_record r ON r.id = rc.record_id"
              + " WHERE r.tenant_id = :tenantId");

  @Override
  @Transactional
  public void purgeTenantData(long tenantId) {
    MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);

    // 1) 先清子表（父行还在，JOIN 得出来）
    for (String sql : CHILD_TABLE_PURGE_SQL) {
      jdbcTemplate.update(sql, params);
    }

    // 2) 跨模块租户隔离表统一按 tenant_id 清除。整批置于 FOREIGN_KEY_CHECKS=0 下执行，
    // 避免 70+ 表间外键顺序耦合导致部分删除失败（租户内父子表同批清除，无悬空引用）。
    // @Transactional 保证 SET 与全部 DELETE 在同一连接/事务内生效。
    jdbcTemplate.getJdbcTemplate().execute("SET FOREIGN_KEY_CHECKS = 0");
    try {
      for (String table : TENANT_TABLES) {
        if (!hasTenantIdColumn(table)) {
          // 韧性：清退清单是手写常量，跨模块 68 张表必然会有登记漂移（DDL 门禁只比对
          // bone-init.sql，迁移脚本建的表扫不到）。缺列就跳过并告警——让「租户删除」这个
          // 功能仍可用（其余 66 张表正常清退），而不是整条链路 500。
          // 遗留数据的可见性由 scripts/check-tenant-deletion-coverage.py 与这里 WARN 日志兜底。
          log.warn("[tenant-purge] 跳过表 {}：无 tenant_id 列，无法按租户清退（请修正 TENANT_TABLES 登记）", table);
          continue;
        }
        jdbcTemplate.update("DELETE FROM " + table + " WHERE tenant_id = :tenantId", params);
      }
    } finally {
      jdbcTemplate.getJdbcTemplate().execute("SET FOREIGN_KEY_CHECKS = 1");
    }
  }

  /** 探测目标表是否真的有 tenant_id 列（information_schema 点查，零数据扫描）。 */
  private boolean hasTenantIdColumn(String table) {
    Integer n =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = :tableName"
                + " AND column_name = 'tenant_id'",
            new MapSqlParameterSource("tableName", table),
            Integer.class);
    return n != null && n > 0;
  }
}
