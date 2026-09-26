package com.bone.iam.infrastructure.gateway;

import com.bone.iam.domain.gateway.TenantDeletionGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
          "sys_log",
          "sys_dict",
          "sys_schedule_task",
          "sys_alert_rule",
          "sys_alert_event",
          // bone-masterdata
          "mdm_entity",
          "mdm_field",
          "mdm_record",
          "mdm_record_version",
          "mdm_category",
          "mdm_record_category",
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

  @Override
  @Transactional
  public void purgeTenantData(long tenantId) {
    MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);

    // iam_role_permission 无 tenant_id，随主表 iam_role 经 JOIN 清除（by-design：只经租户作用域主表访问）
    jdbcTemplate.update(
        """
                DELETE rp FROM iam_role_permission rp
                INNER JOIN iam_role r ON r.id = rp.role_id
                WHERE r.tenant_id = :tenantId
                """,
        params);

    // 跨模块租户隔离表统一按 tenant_id 清除。整批置于 FOREIGN_KEY_CHECKS=0 下执行，
    // 避免 70+ 表间外键顺序耦合导致部分删除失败（租户内父子表同批清除，无悬空引用）。
    // @Transactional 保证 SET 与全部 DELETE 在同一连接/事务内生效。
    jdbcTemplate.getJdbcTemplate().execute("SET FOREIGN_KEY_CHECKS = 0");
    try {
      for (String table : TENANT_TABLES) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE tenant_id = :tenantId", params);
      }
    } finally {
      jdbcTemplate.getJdbcTemplate().execute("SET FOREIGN_KEY_CHECKS = 1");
    }
  }
}
