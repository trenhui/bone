package com.bone.metadata.catalog.infrastructure.gateway;

import com.bone.metadata.catalog.domain.gateway.ActionDefMaterializePort;
import com.bone.metadata.catalog.domain.gateway.ActionDefSnapshot;
import com.bone.metadata.catalog.domain.gateway.ActionDefSpec;
import com.bone.metadata.catalog.domain.gateway.UiSchemaRefCheckPort;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateAction;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 模板动作 → 实体动作定义的 JDBC 展开适配器（迁移 0040）。
 *
 * <p>写 {@code meta_entity_action_def}：名字空间按<b>新实体</b>重写，尤其 {@code permission_code} 必须为 {@code
 * runtime:{entityCode}:{actionCode}}：
 *
 * <ul>
 *   <li>非逃逸动作（状态流转）由 {@code runtime:*:write} 通配覆盖，登记细码不影响可用性；
 *   <li>逃逸动作（有资金/外部副作用，如确认打款）<b>必须命中动作级细码</b>，通配 write 不覆盖（Runtime 数据面设计 §4.6）。新实体未显式授权前会
 *       403，并给出「该授予什么码」——这是有意的 fail-closed， 不能在初始化时偷偷放宽。
 * </ul>
 *
 * <p>UPSERT：以 {@code uk_action_tenant_entity_code(tenant_id, entity_code, action_code)} 为准，
 * 重复实例化同一实体不会撞唯一键而中断整个建模流程。
 *
 * <p>同时实现 {@link UiSchemaRefCheckPort} 的只读动作码查询（同表 {@code meta_entity_action_def}、 同豁免依据，不另起 JDBC
 * 文件，防 HC-006 门禁新增面）。
 */
@Component
public class JdbcActionDefMaterializeAdapter
    implements ActionDefMaterializePort, UiSchemaRefCheckPort {

  private static final String UPSERT =
      "INSERT INTO meta_entity_action_def "
          + "(tenant_id, entity_code, action_code, label, from_status, to_status, status_field, "
          + " is_escape, handler_bean, params_schema, permission_code, requires_idempotency, sort_order, "
          + " is_entry, created_at, updated_at, deleted, version) "
          + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3),0,0) "
          + "ON DUPLICATE KEY UPDATE "
          + "label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status), "
          + "status_field = VALUES(status_field), is_escape = VALUES(is_escape), "
          + "handler_bean = VALUES(handler_bean), params_schema = VALUES(params_schema), "
          + "permission_code = VALUES(permission_code), "
          + "requires_idempotency = VALUES(requires_idempotency), sort_order = VALUES(sort_order), "
          + "is_entry = VALUES(is_entry), "
          + "updated_at = NOW(3), version = version + 1, deleted = 0";

  private static final String SOFT_DELETE_BY_ENTITY =
      "UPDATE meta_entity_action_def SET deleted = 1, updated_at = NOW(3) "
          + "WHERE tenant_id = ? AND entity_code = ? AND deleted = 0";

  private static final String SELECT_ESCAPE_ACTIONS =
      "SELECT action_code, label, permission_code FROM meta_entity_action_def "
          + "WHERE tenant_id = ? AND entity_code = ? AND is_escape = 1 AND deleted = 0 "
          + "ORDER BY sort_order ASC, id ASC";

  private static final String SELECT_ACTION_CODES =
      "SELECT action_code FROM meta_entity_action_def "
          + "WHERE tenant_id = ? AND entity_code = ? AND deleted = 0";

  private static final String SELECT_ALL_ACTIONS =
      "SELECT action_code, label, from_status, to_status, status_field, is_escape, handler_bean, "
          + "params_schema, requires_idempotency, sort_order, is_entry "
          + "FROM meta_entity_action_def "
          + "WHERE tenant_id = ? AND entity_code = ? AND deleted = 0 "
          + "ORDER BY sort_order ASC, id ASC";

  private static final String UPSERT_ONE =
      "INSERT INTO meta_entity_action_def "
          + "(tenant_id, entity_code, action_code, label, from_status, to_status, status_field, "
          + " is_escape, handler_bean, params_schema, permission_code, requires_idempotency, sort_order, "
          + " is_entry, created_at, updated_at, deleted, version) "
          + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3),0,0) "
          + "ON DUPLICATE KEY UPDATE "
          + "label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status), "
          + "status_field = VALUES(status_field), is_escape = VALUES(is_escape), "
          + "handler_bean = VALUES(handler_bean), params_schema = VALUES(params_schema), "
          // permission_code 必须一起刷新（2026-10-28 联调实测漏项）：动作级细码形状是
          // runtime:{entityCode}:{actionCode}，实体 code 一旦变更（如 0052 的
          // expense_claim → expense_reimbursement 改名），不刷新就会永久停留在旧码上——
          // 于是「按新码授权」看起来配了却不生效，而 PLATFORM_ADMIN（admin）会对动作级判定
          // 早退，任何人用超管都测不出问题。缺这一行 = 静默越权/静默 403 两种事故同时可能。
          + "permission_code = VALUES(permission_code), "
          + "requires_idempotency = VALUES(requires_idempotency), sort_order = VALUES(sort_order), "
          + "is_entry = VALUES(is_entry), "
          + "updated_at = NOW(3), version = version + 1, deleted = 0";

  private static final String SOFT_DELETE_ONE =
      "UPDATE meta_entity_action_def SET deleted = 1, updated_at = NOW(3) "
          + "WHERE tenant_id = ? AND entity_code = ? AND action_code = ? AND deleted = 0";

  private final JdbcTemplate jdbcTemplate;

  public JdbcActionDefMaterializeAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public int materialize(long tenantId, String entityCode, List<MetaModelTemplateAction> actions) {
    if (actions == null || actions.isEmpty()) {
      return 0;
    }
    List<MetaModelTemplateAction> list = List.copyOf(actions);
    int[] counts =
        jdbcTemplate.batchUpdate(
            UPSERT,
            new BatchPreparedStatementSetter() {
              @Override
              public void setValues(PreparedStatement ps, int i) throws SQLException {
                MetaModelTemplateAction a = list.get(i);
                ps.setLong(1, tenantId);
                ps.setString(2, entityCode);
                ps.setString(3, a.getActionCode());
                ps.setString(4, a.getLabel());
                ps.setString(5, a.getFromStatus());
                ps.setString(6, a.getToStatus());
                ps.setString(7, a.getStatusField() == null ? "status" : a.getStatusField());
                ps.setInt(8, Boolean.TRUE.equals(a.getEscape()) ? 1 : 0);
                ps.setString(9, a.getHandlerBean());
                ps.setString(10, a.getParamsSchema());
                ps.setString(11, permissionCodeOf(entityCode, a));
                ps.setInt(12, Boolean.TRUE.equals(a.getRequiresIdempotency()) ? 1 : 0);
                ps.setInt(13, a.getSortOrder());
                ps.setInt(14, Boolean.TRUE.equals(a.getEntry()) ? 1 : 0);
              }

              @Override
              public int getBatchSize() {
                return list.size();
              }
            });
    int total = 0;
    for (int c : counts) {
      total += c;
    }
    return total;
  }

  /** 动作级细粒度权限码形状：{@code runtime:{entityCode}:{actionCode}}（RuntimeAccessGuard §4.7）。 */
  private static String permissionCodeOf(String entityCode, MetaModelTemplateAction a) {
    return "runtime:" + entityCode + ":" + a.getActionCode();
  }

  @Override
  public int deleteByEntity(long tenantId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return 0;
    }
    return jdbcTemplate.update(SOFT_DELETE_BY_ENTITY, tenantId, entityCode);
  }

  @Override
  public List<ActionDefSnapshot> listEscapeActions(long tenantId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return List.of();
    }
    // 只取逃逸动作：非逃逸（状态流转）由实体级 write / 通配 write 覆盖，无逐动作补权必要；
    // deleted=0 与 UPSERT 的复活语义对齐——软删定义不进入 onboarding 清单。
    return jdbcTemplate.query(
        SELECT_ESCAPE_ACTIONS,
        (rs, rowNum) ->
            new ActionDefSnapshot(
                rs.getString("action_code"),
                rs.getString("label"),
                rs.getString("permission_code")),
        tenantId,
        entityCode);
  }

  /**
   * 界面 schema 引用检查的动作码集合（UI-REF-02）。
   *
   * <p>catalog 不反向依赖 runtime 界读仓储，依赖方向保持 application → domain ← infrastructure。 软删行显式排除。
   */
  @Override
  public Set<String> actionCodesOf(long tenantId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbcTemplate.queryForList(SELECT_ACTION_CODES, String.class, tenantId, entityCode));
  }

  @Override
  public List<ActionDefSpec> listByEntity(long tenantId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return List.of();
    }
    return jdbcTemplate.query(
        SELECT_ALL_ACTIONS,
        (rs, rowNum) ->
            new ActionDefSpec(
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
                rs.getInt("is_entry") == 1),
        tenantId,
        entityCode);
  }

  @Override
  public int upsert(long tenantId, String entityCode, ActionDefSpec spec) {
    // permission_code 只按命名空间推导后写入，不接受外部指定（见端口 javadoc）。
    return jdbcTemplate.update(
        UPSERT_ONE,
        tenantId,
        entityCode,
        spec.actionCode(),
        spec.label(),
        spec.fromStatus(),
        spec.toStatus(),
        spec.statusField() == null ? ActionDefSpec.DEFAULT_STATUS_FIELD : spec.statusField(),
        Boolean.TRUE.equals(spec.escape()) ? 1 : 0,
        spec.handlerBean(),
        spec.paramsSchema(),
        spec.permissionCodeOf(entityCode),
        Boolean.TRUE.equals(spec.requiresIdempotency()) ? 1 : 0,
        spec.sortOrder() == null ? 0 : spec.sortOrder(),
        Boolean.TRUE.equals(spec.entry()) ? 1 : 0);
  }

  @Override
  public int softDeleteOne(long tenantId, String entityCode, String actionCode) {
    if (entityCode == null || entityCode.isBlank() || actionCode == null || actionCode.isBlank()) {
      return 0;
    }
    return jdbcTemplate.update(SOFT_DELETE_ONE, tenantId, entityCode, actionCode);
  }
}
