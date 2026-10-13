package com.bone.metadata.runtime.adapter;

import com.bone.metadata.runtime.application.RuntimeActionExecutionRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 动作执行日志 JDBC 实现（{@code meta_runtime_action_execution}）。
 *
 * <p>P0-2：执行行先以 {@code status=3 (PENDING)} 落库（begin），成功 / 失败 / 孤儿再转终态， 且仅在 {@code status=3}
 * 时允许转移（双重 mark 不产生脏覆盖）。生成主键经 {@link GeneratedKeyHolder} 回取（MySQL 池化连接下 {@code LAST_INSERT_ID()}
 * 不可靠）。
 */
@Repository
public class JdbcRuntimeActionExecutionRepository implements RuntimeActionExecutionRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcRuntimeActionExecutionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<String, Object> findByIdempotency(
      long tenantId, String entityCode, String recordId, String actionCode, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return null;
    }
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode)
            .addValue("recordId", recordId)
            .addValue("actionCode", actionCode)
            .addValue("idempotencyKey", idempotencyKey);
    String sql =
        "SELECT status, result_json FROM meta_runtime_action_execution "
            + "WHERE tenant_id = :tenantId AND entity_code = :entityCode AND record_id = :recordId "
            + "AND action_code = :actionCode AND idempotency_key = :idempotencyKey "
            + "ORDER BY id DESC LIMIT 1";
    List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public List<Map<String, Object>> findByRecord(long tenantId, String entityCode, String recordId) {
    if (recordId == null || recordId.isBlank()) {
      return List.of();
    }
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode)
            .addValue("recordId", recordId);
    String sql =
        "SELECT id, action_code, status, result_json, operator_id, created_at "
            + "FROM meta_runtime_action_execution "
            + "WHERE tenant_id = :tenantId AND entity_code = :entityCode AND record_id = :recordId "
            + "ORDER BY id ASC";
    return jdbc.queryForList(sql, p);
  }

  @Override
  public long begin(
      long tenantId,
      String entityCode,
      String recordId,
      String actionCode,
      String idempotencyKey,
      String operatorId,
      Map<String, Object> params) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("entityCode", entityCode)
            .addValue("recordId", recordId)
            .addValue("actionCode", actionCode)
            .addValue(
                "idempotencyKey",
                (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey)
            .addValue("status", STATUS_PENDING)
            .addValue("resultJson", null)
            .addValue("operatorId", operatorId)
            .addValue("createdAt", new Timestamp(System.currentTimeMillis()));
    String sql =
        "INSERT INTO meta_runtime_action_execution "
            + "(tenant_id, entity_code, record_id, action_code, idempotency_key, status, result_json, operator_id, created_at) "
            + "VALUES (:tenantId, :entityCode, :recordId, :actionCode, :idempotencyKey, :status, :resultJson, :operatorId, :createdAt)";
    KeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(sql, p, kh, new String[] {"id"});
    Number key = kh.getKey();
    return key == null ? 0L : key.longValue();
  }

  @Override
  public void markSucceeded(long executionId, String resultJson) {
    transition(executionId, STATUS_SUCCEEDED, truncate(resultJson));
  }

  @Override
  public void markFailed(long executionId, String errorSummary) {
    transition(executionId, STATUS_FAILED, truncate(errorSummary));
  }

  @Override
  public void markOrphaned(long executionId, String note) {
    transition(executionId, STATUS_ORPHANED, truncate(note));
  }

  /** 仅允许 PENDING → 终态的单向转移：防止同一次执行的重复 mark 产生脏覆盖。 */
  private void transition(long executionId, long status, String resultJson) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("executionId", executionId)
            .addValue("status", status)
            .addValue("resultJson", resultJson);
    jdbc.update(
        "UPDATE meta_runtime_action_execution SET status = :status, result_json = :resultJson "
            + "WHERE id = :executionId AND status = 3",
        p);
  }

  private static String truncate(String s) {
    if (s == null) {
      return null;
    }
    return s.length() <= 2000 ? s : s.substring(0, 2000);
  }
}
