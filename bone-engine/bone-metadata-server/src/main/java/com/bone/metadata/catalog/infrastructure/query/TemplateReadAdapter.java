package com.bone.metadata.catalog.infrastructure.query;

import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateField;
import java.util.List;
import java.util.Optional;
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

  private static final RowMapper<MetaModelTemplate> TEMPLATE_MAPPER =
      (rs, i) ->
          MetaModelTemplate.reconstitute(
              rs.getLong("id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getString("description"),
              rs.getString("category"),
              rs.getString("current_version"),
              rs.getInt("status"));

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
              rs.getInt("sort_order"));

  private final JdbcTemplate jdbcTemplate;

  public TemplateReadAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<MetaModelTemplate> listPlatformTemplates(String keyword) {
    String base =
        "SELECT id, code, name, description, category, current_version, status FROM meta_model_template"
            + " WHERE tenant_id = 0 AND deleted = 0";
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
            "SELECT id, code, name, description, category, current_version, status FROM meta_model_template"
                + " WHERE tenant_id = 0 AND deleted = 0 AND id = ?",
            TEMPLATE_MAPPER,
            templateId);
    return list.stream().findFirst();
  }

  @Override
  public List<MetaModelTemplateField> fieldsOf(Long templateId) {
    return jdbcTemplate.query(
        "SELECT id, tenant_id, template_id, code, name, display_name, field_type, length,"
            + " is_required, is_unique, default_value, comment, sort_order"
            + " FROM meta_model_template_field WHERE tenant_id = 0 AND deleted = 0 AND template_id = ?"
            + " ORDER BY sort_order",
        FIELD_MAPPER,
        templateId);
  }
}
