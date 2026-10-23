package com.bone.workflow.starter.outbox;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.persistence.WfSql;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 状态投影发件箱仓储（{@code wf_status_outbox}）。
 *
 * <p>租户纪律同实例仓储：引擎 API 不带 tenantId，由 {@link TenantContext} 补齐，缺失即失败关闭。
 */
public class WorkflowStatusOutboxRepository {

  /** 抢占超时阈值：超过该时长仍未落地的 CLAIMED 行视为抢占副本崩溃遗留，可被其它副本重新抢占补执行。 */
  private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

  private final NamedParameterJdbcTemplate jdbc;

  public WorkflowStatusOutboxRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public void insert(StatusIntent intent) {
    jdbc.update(
        WfSql.INSERT_STATUS_OUTBOX,
        params()
            .addValue("id", intent.id())
            .addValue("instanceId", intent.instanceId())
            .addValue("entityCode", intent.entityCode())
            .addValue("recordId", intent.recordId())
            .addValue("intent", intent.intent())
            .addValue("actionCode", intent.actionCode())
            .addValue("paramsJson", intent.paramsJson())
            .addValue("now", Timestamp.from(Instant.now())));
  }

  /**
   * 原子抢占一条意图：把 NEW / 到期的 FAILED / 超时的 CLAIMED 改成 CLAIMED，返回是否抢到。
   *
   * <p><b>多副本部署必須</b>：Bone 多副本下两个副本同时 poll 同一条意图会重复执行；只有 {@code claim} 成功的副本才执行， 其余副本拿到 false
   * 立即跳过——业务动作（approve/reject/…）不会被调两次。单副本崩溃在 {@code execute} 与 {@code markDone} 之间时，该行停留在
   * CLAIMED，超过 {@link #CLAIM_TIMEOUT} 后由本方法重新抢占补执行。
   */
  public boolean claim(String intentId) {
    Instant now = Instant.now();
    int updated =
        jdbc.update(
            WfSql.CLAIM_STATUS_INTENT,
            params()
                .addValue("id", intentId)
                .addValue("now", Timestamp.from(now))
                .addValue("staleCutoff", Timestamp.from(now.minus(CLAIM_TIMEOUT))));
    return updated > 0;
  }

  /**
   * 取到期意图（NEW 或已到退避时刻的 FAILED 或超时的 CLAIMED），按 id 升序。
   *
   * <p><b>跨租户查询</b>：中继跑在无 TenantContext 的调度线程，租户由行内 {@code tenant_id} 携带， 由 {@code
   * StatusProjectionRelay} 逐条重建上下文后再执行/落标。本方法因此不要求租户上下文。
   */
  public List<StatusIntent> pollDue(int limit) {
    Instant now = Instant.now();
    return jdbc.query(
        WfSql.SELECT_DUE_STATUS_INTENTS,
        new MapSqlParameterSource()
            .addValue("now", Timestamp.from(now))
            .addValue("staleCutoff", Timestamp.from(now.minus(CLAIM_TIMEOUT)))
            .addValue("limit", limit),
        (rs, n) -> map(rs));
  }

  public void markDone(String intentId) {
    jdbc.update(
        WfSql.MARK_STATUS_INTENT_DONE,
        params().addValue("id", intentId).addValue("now", Timestamp.from(Instant.now())));
  }

  /**
   * 标记失败并排定下次重试；返回是否真的排上了（false = 已达重试上限，不再排期）。
   *
   * <p>达上限后留在 FAILED 交由对账/人工介入——不能无限重试，否则一个永久失败的动作会让中继空转。
   */
  public boolean markFailed(String intentId, int retryCount, int maxRetries, Instant nextRetryAt) {
    return jdbc.update(
            WfSql.MARK_STATUS_INTENT_FAILED,
            params()
                .addValue("id", intentId)
                .addValue("nextRetryAt", nextRetryAt == null ? null : Timestamp.from(nextRetryAt))
                .addValue("maxRetries", maxRetries)
                .addValue("now", Timestamp.from(Instant.now())))
        > 0;
  }

  /**
   * 终态实例中缺少已落地投影的（意图丢失，需补投）。
   *
   * <p>同 {@link #pollDue(int)}：跨租户，租户由行内 {@code tenant_id} 携带。
   */
  public List<StatusIntent> findTerminalMissingProjection(int limit) {
    return jdbc.query(
        WfSql.SELECT_TERMINAL_INSTANCES_MISSING_PROJECTION,
        new MapSqlParameterSource().addValue("limit", limit),
        (rs, n) ->
            new StatusIntent(
                rs.getString("id"),
                rs.getString("id"),
                rs.getString("entity_code"),
                rs.getString("record_id"),
                rs.getLong("tenant_id"),
                rs.getString("phase"),
                actionCodeOf(rs.getString("phase")),
                null,
                "NEW",
                0,
                null));
  }

  private String actionCodeOf(String phase) {
    return switch (phase) {
      case "APPROVED" -> "approve";
      case "REJECTED" -> "reject";
      case "WITHDRAWN" -> "withdraw";
      default -> null; // SUSPENDED / RESUMED 无对应业务动作
    };
  }

  private MapSqlParameterSource params() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流发件箱缺少租户上下文（TenantContext）——拒绝在未知租户下读写 wf_status_outbox");
    }
    return new MapSqlParameterSource("tenantId", tenantId);
  }

  private StatusIntent map(ResultSet rs) throws java.sql.SQLException {
    Timestamp next = rs.getTimestamp("next_retry_at");
    return new StatusIntent(
        rs.getString("id"),
        rs.getString("instance_id"),
        rs.getString("entity_code"),
        rs.getString("record_id"),
        rs.getLong("tenant_id"),
        rs.getString("intent"),
        rs.getString("action_code"),
        rs.getString("params_json"),
        rs.getString("state"),
        rs.getInt("retry_count"),
        next == null ? null : next.toInstant());
  }
}
