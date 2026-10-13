package com.bone.metadata.catalog.infrastructure.gateway;

import com.bone.metadata.catalog.domain.gateway.UiOverlayStore;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code meta_ui_overlay} 的 JDBC 适配器（迁移 0042）。
 *
 * <p>UPSERT 语义与 {@link JdbcActionDefMaterializeAdapter} 同口径：唯一键 {@code
 * uk_overlay_tenant_user_entity} 不含 deleted，故「reset 后再保存」走 {@code deleted = 0} 复活而非撞键——reset
 * 不是物理删除的承诺在这里成立。
 */
@Component
public class JdbcUiOverlayAdapter implements UiOverlayStore {

  private static final String SELECT_JSON =
      "SELECT overlay_json FROM meta_ui_overlay "
          + "WHERE tenant_id = ? AND user_id = ? AND entity_code = ? AND deleted = 0";

  private static final String UPSERT =
      "INSERT INTO meta_ui_overlay (tenant_id, user_id, entity_code, overlay_json, created_at, updated_at, deleted) "
          + "VALUES (?,?,?,?,NOW(3),NOW(3),0) "
          + "ON DUPLICATE KEY UPDATE overlay_json = VALUES(overlay_json), updated_at = NOW(3), deleted = 0";

  private static final String SOFT_DELETE =
      "UPDATE meta_ui_overlay SET deleted = 1, updated_at = NOW(3) "
          + "WHERE tenant_id = ? AND user_id = ? AND entity_code = ? AND deleted = 0";

  private final JdbcTemplate jdbcTemplate;

  public JdbcUiOverlayAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<String> findOverlayJson(long tenantId, long userId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return Optional.empty();
    }
    return jdbcTemplate
        .query(
            SELECT_JSON, (rs, rowNum) -> rs.getString("overlay_json"), tenantId, userId, entityCode)
        .stream()
        .findFirst();
  }

  @Override
  public void saveOverlayJson(long tenantId, long userId, String entityCode, String overlayJson) {
    if (entityCode == null
        || entityCode.isBlank()
        || overlayJson == null
        || overlayJson.isBlank()) {
      return;
    }
    jdbcTemplate.update(UPSERT, tenantId, userId, entityCode, overlayJson);
  }

  @Override
  public void clearOverlay(long tenantId, long userId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return;
    }
    jdbcTemplate.update(SOFT_DELETE, tenantId, userId, entityCode);
  }
}
