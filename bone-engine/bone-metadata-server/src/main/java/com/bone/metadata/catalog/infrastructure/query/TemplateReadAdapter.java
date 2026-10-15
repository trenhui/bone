package com.bone.metadata.catalog.infrastructure.query;

import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.gateway.TemplateUiSnapshot;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateAction;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateField;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 平台模型模板只读适配器（E-4.2：{@code infrastructure/query}）。
 *
 * <p>模板表 {@code meta_model_template(+_field)} 是平台层资产（{@code tenant_id = 0}），目录读取天然跨租户， 用
 * JdbcTemplate 直查并固定 {@code tenant_id = 0} + {@code deleted = 0} 谓词——不走 SDK 租户仓储， 也就不触发「全租户入口仅限
 * adapter.schedule」门禁（该门禁拦的是域仓储读模型的越权通道，见 ADR-0031 §3）。 只读：本适配器不提供任何写路径。
 */
@Component
public class TemplateReadAdapter implements TemplateReadPort {

  private static final Logger log = LoggerFactory.getLogger(TemplateReadAdapter.class);

  private static final RowMapper<MetaModelTemplate> TEMPLATE_MAPPER =
      (rs, i) ->
          MetaModelTemplate.reconstitute(
              rs.getLong("id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getString("description"),
              rs.getString("category"),
              rs.getString("current_version"),
              rs.getInt("status"),
              rs.getInt("delivery_mode"));

  // W1a T2：SELECT 与 RowMapper 必须带上 enum_values / validation_rules，
  // 否则实例化时 tf.getEnumValues() 恒为 null —— 场景模板的枚举状态机在第一步就丢（PRD §0.6.2）。
  private static final RowMapper<MetaModelTemplateField> FIELD_MAPPER =
      (rs, i) ->
          MetaModelTemplateField.create(
              rs.getLong("id"),
              rs.getLong("tenant_id"),
              rs.getLong("template_id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getString("display_name"),
              rs.getString("field_type"),
              (Integer) rs.getObject("length"),
              rs.getBoolean("is_required"),
              rs.getBoolean("is_unique"),
              rs.getString("default_value"),
              rs.getString("comment"),
              rs.getInt("sort_order"),
              rs.getString("enum_values"),
              rs.getString("validation_rules"));

  private final JdbcTemplate jdbcTemplate;

  public TemplateReadAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<MetaModelTemplate> listPlatformTemplates(String keyword) {
    String base =
        "SELECT id, code, name, description, category, current_version, status, delivery_mode"
            + " FROM meta_model_template WHERE tenant_id = 0 AND deleted = 0";
    if (StringUtils.hasText(keyword)) {
      String like = "%" + keyword.trim() + "%";
      return jdbcTemplate.query(
          base + " AND (code LIKE ? OR name LIKE ?) ORDER BY code", TEMPLATE_MAPPER, like, like);
    }
    return jdbcTemplate.query(base + " ORDER BY code", TEMPLATE_MAPPER);
  }

  @Override
  public Optional<MetaModelTemplate> findPlatformTemplate(Long templateId) {
    List<MetaModelTemplate> list =
        jdbcTemplate.query(
            "SELECT id, code, name, description, category, current_version, status, delivery_mode"
                + " FROM meta_model_template WHERE tenant_id = 0 AND deleted = 0 AND id = ?",
            TEMPLATE_MAPPER,
            templateId);
    return list.stream().findFirst();
  }

  @Override
  public List<MetaModelTemplateField> fieldsOf(Long templateId) {
    return jdbcTemplate.query(
        "SELECT id, tenant_id, template_id, code, name, display_name, field_type, length,"
            + " is_required, is_unique, default_value, comment, sort_order,"
            + " enum_values, validation_rules"
            + " FROM meta_model_template_field WHERE tenant_id = 0 AND deleted = 0 AND template_id = ?"
            + " ORDER BY sort_order",
        FIELD_MAPPER,
        templateId);
  }

  @Override
  public List<MetaModelTemplateAction> actionsOf(Long templateId) {
    return jdbcTemplate.query(
        "SELECT id, tenant_id, template_id, action_code, label, from_status, to_status,"
            + " status_field, is_escape, handler_bean, params_schema, requires_idempotency,"
            + " sort_order, is_entry"
            + " FROM meta_model_template_action WHERE tenant_id = 0 AND deleted = 0 AND template_id = ?"
            + " ORDER BY sort_order",
        ACTION_MAPPER,
        templateId);
  }

  /** 模板动作行映射（迁移 0040）：字段与 meta_entity_action_def 一一对应。 */
  private static final RowMapper<MetaModelTemplateAction> ACTION_MAPPER =
      (rs, i) ->
          MetaModelTemplateAction.create(
              rs.getLong("id"),
              rs.getLong("tenant_id"),
              rs.getLong("template_id"),
              rs.getString("action_code"),
              rs.getString("label"),
              rs.getString("from_status"),
              rs.getString("to_status"),
              rs.getString("status_field"),
              rs.getInt("is_escape") == 1,
              rs.getString("handler_bean"),
              rs.getString("params_schema"),
              rs.getInt("requires_idempotency") == 1,
              rs.getInt("sort_order"),
              rs.getInt("is_entry") == 1);

  @Override
  public Optional<TemplateUiSnapshot> findUiSnapshotByCode(String templateCode) {
    if (templateCode == null || templateCode.isBlank()) {
      return Optional.empty();
    }
    try {
      List<TemplateUiSnapshot> list =
          jdbcTemplate.query(
              "SELECT ui_schema_version, ui_schema_json FROM meta_model_template"
                  + " WHERE tenant_id = 0 AND deleted = 0 AND code = ?",
              (rs, i) ->
                  new TemplateUiSnapshot(
                      rs.getString("ui_schema_version"), rs.getString("ui_schema_json")),
              templateCode);
      return list.stream().findFirst();
    } catch (BadSqlGrammarException ex) {
      // 0046 未执行（列不存在）：资产未补齐即 disabled，不炸错（技术方案 §5.2 前置诚实门）
      log.warn("[ui-schema] 模板界面列缺失（0046 未执行），跟随入口保持 disabled: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> findUiHistorySnapshot(String templateCode, String version) {
    if (templateCode == null || templateCode.isBlank() || version == null || version.isBlank()) {
      return Optional.empty();
    }
    try {
      List<String> list =
          jdbcTemplate.queryForList(
              "SELECT schema_json FROM meta_model_template_ui_history"
                  + " WHERE tenant_id = 0 AND template_code = ? AND version = ?",
              String.class,
              templateCode,
              version);
      return list.stream().findFirst().filter(s -> !s.isBlank());
    } catch (BadSqlGrammarException ex) {
      log.warn("[ui-schema] 模板界面历史表缺失（0046 未执行），降级为两路归并: {}", ex.getMessage());
      return Optional.empty();
    }
  }
}
