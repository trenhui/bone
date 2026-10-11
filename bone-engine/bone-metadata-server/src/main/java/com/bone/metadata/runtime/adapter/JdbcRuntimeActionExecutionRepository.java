package com.bone.metadata.runtime.adapter;

import com.bone.metadata.runtime.application.RuntimeActionExecutionRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 动作执行日志 JDBC 实现（{@code meta_runtime_action_execution}）。 */
@Repository
public class JdbcRuntimeActionExecutionRepository implements RuntimeActionExecutionRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcRuntimeActionExecutionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<String> findSuccessResultByIdempotency(
      long tenantId, String entityCode, String recordId, String actionCode, String idempotencyKey) {
    if (idempotencyKey == null) {
      return Optional.empty();
    }
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode)
            .addValue("recordId", recordId)
            .addValue("actionCode", actionCode)
            .addValue("idempotencyKey", idempotencyKey);
    String sql =
        "SELECT result_json FROM meta_runtime_action_execution "
            + "WHERE tenant_id = :tenantId AND entity_code = :entityCode AND record_id = :recordId "
            + "AND action_code = :actionCode AND idempotency_key = :idempotencyKey AND status = 1 LIMIT 1";
    List<String> rows = jdbc.queryForList(sql, p, String.class);
    return rows.stream().findFirst();
  }

  @Override
  public void record(
      long tenantId,
      String entityCode,
      String recordId,
      String actionCode,
      String idempotencyKey,
      boolean success,
      String resultJson,
      String operatorId) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode)
            .addValue("recordId", recordId)
            .addValue("actionCode", actionCode)
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("status", success ? 1 : 2)
            .addValue("resultJson", resultJson)
            .addValue("operatorId", operatorId)
            .addValue("createdAt", new Timestamp(System.currentTimeMillis()));
    String sql =
        "INSERT INTO meta_runtime_action_execution "
            + "(tenant_id, entity_code, record_id, action_code, idempotency_key, status, result_json, operator_id, created_at) "
            + "VALUES (:tenantId, :entityCode, :recordId, :actionCode, :idempotencyKey, :status, :resultJson, :operatorId, :createdAt)";
    jdbc.update(sql, p);
  }
}
