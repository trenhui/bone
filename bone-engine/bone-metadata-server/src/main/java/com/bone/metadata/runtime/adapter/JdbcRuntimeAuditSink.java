package com.bone.metadata.runtime.adapter;

import com.bone.metadata.engine.runtime.RuntimeAuditEntry;
import com.bone.metadata.engine.runtime.RuntimeAuditSink;
import java.sql.Timestamp;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 记录级变更审计落库（RC-009 · Must，Runtime 数据面设计 §7.1）。
 *
 * <p><b>append-only</b>：本实现只 INSERT，不提供 UPDATE / DELETE——审计表的价值在于「事后可回溯」， 一旦允许改写，它就退化成一份可被抹掉的普通业务表。
 *
 * <p><b>为什么与动作执行日志并存</b>：{@code meta_runtime_action_execution} 记录「动作被调用」及其副作用结果；
 * 本表记录「受保护字段的每一次取值变化」（含软删 / 恢复 / 管理员修复）。前者回答「动作跑没跑」， 后者回答「谁把哪张单据从 APPROVED 改成 PAID」——缺后者，状态机就是不完整的。
 */
@Repository
public class JdbcRuntimeAuditSink implements RuntimeAuditSink {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcRuntimeAuditSink(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void record(RuntimeAuditEntry entry) {
    if (entry == null) {
      return;
    }
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("tenantId", entry.tenantId())
            .addValue("entityCode", entry.entityCode())
            .addValue("recordId", entry.recordId())
            .addValue("fieldCode", entry.field())
            .addValue("fromValue", truncate(entry.fromValue()))
            .addValue("toValue", truncate(entry.toValue()))
            .addValue("operatorId", entry.operatorId())
            .addValue("reason", truncate(entry.reason()))
            .addValue("source", entry.source())
            .addValue("actionCode", entry.actionCode())
            .addValue("traceId", truncate(MDC.get("traceId")))
            .addValue("createdAt", new Timestamp(System.currentTimeMillis()));
    jdbc.update(
        "INSERT INTO meta_runtime_record_audit "
            + "(tenant_id, entity_code, record_id, field_code, from_value, to_value, "
            + " operator_id, reason, source, action_code, trace_id, created_at) "
            + "VALUES (:tenantId, :entityCode, :recordId, :fieldCode, :fromValue, :toValue, "
            + ":operatorId, :reason, :source, :actionCode, :traceId, :createdAt)",
        p);
  }

  /** 审计列定长 512：超长值截断而非报错（审计可用性优先于完整性，避免长文本把审计写崩）。 */
  private static String truncate(String v) {
    if (v == null) {
      return null;
    }
    return v.length() <= 512 ? v : v.substring(0, 512);
  }
}
