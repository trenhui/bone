package com.bone.metadata.runtime.adapter;

import com.bone.metadata.runtime.application.RuntimeActionDefRepository;
import com.bone.metadata.runtime.domain.ActionParamSpec;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 动作定义 JDBC 读取（{@code meta_entity_action_def}）。 */
@Repository
public class JdbcRuntimeActionDefRepository implements RuntimeActionDefRepository {

  private static final String BASE =
      "SELECT id, entity_code, action_code, label, from_status, to_status, status_field, "
          + "is_escape, handler_bean, params_schema, permission_code, requires_idempotency, sort_order "
          + "FROM meta_entity_action_def "
          + "WHERE deleted = 0 AND tenant_id = :tenantId AND entity_code = :entityCode";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcRuntimeActionDefRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<RuntimeActionDef> findByEntityAndCode(
      long tenantId, String entityCode, String actionCode) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode)
            .addValue("actionCode", actionCode);
    List<RuntimeActionDef> list =
        jdbc.query(BASE + " AND action_code = :actionCode ORDER BY sort_order", p, this::mapRow);
    return list.stream().findFirst();
  }

  @Override
  public List<RuntimeActionDef> listByEntity(long tenantId, String entityCode) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode);
    return jdbc.query(BASE + " ORDER BY sort_order", p, this::mapRow);
  }

  @Override
  public List<RuntimeActionDef> listByEntityAndFromStatus(
      long tenantId, String entityCode, String fromStatus) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode)
            .addValue("fromStatus", fromStatus);
    return jdbc.query(
        BASE + " AND (from_status = :fromStatus OR from_status IS NULL) ORDER BY sort_order",
        p,
        this::mapRow);
  }

  private RuntimeActionDef mapRow(ResultSet rs, int rowNum) throws SQLException {
    List<ActionParamSpec> params = List.of();
    String schema = rs.getString("params_schema");
    if (schema != null && !schema.isBlank()) {
      try {
        List<Map<String, Object>> raw =
            objectMapper.readValue(schema, new TypeReference<List<Map<String, Object>>>() {});
        params =
            raw.stream()
                .map(
                    m ->
                        new ActionParamSpec(
                            (String) m.get("name"),
                            (String) m.get("type"),
                            Boolean.TRUE.equals(m.get("required")),
                            castValues(m.get("values")),
                            (String) m.get("label"),
                            toDouble(m.get("min")),
                            toDouble(m.get("max")),
                            (String) m.get("pattern")))
                .toList();
      } catch (Exception ignored) {
        params = List.of();
      }
    }
    return new RuntimeActionDef(
        rs.getLong("id"),
        rs.getString("entity_code"),
        rs.getString("action_code"),
        rs.getString("label"),
        rs.getString("from_status"),
        rs.getString("to_status"),
        rs.getString("status_field"),
        rs.getInt("is_escape") == 1,
        rs.getString("handler_bean"),
        params,
        rs.getString("permission_code"),
        rs.getInt("requires_idempotency") == 1,
        rs.getInt("sort_order"));
  }

  @SuppressWarnings("unchecked")
  private static List<String> castValues(Object v) {
    if (v instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  private static Double toDouble(Object v) {
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    if (v instanceof String s && !s.isBlank()) {
      try {
        return Double.parseDouble(s);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
