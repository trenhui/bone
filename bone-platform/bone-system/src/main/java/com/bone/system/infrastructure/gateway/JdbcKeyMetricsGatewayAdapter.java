package com.bone.system.infrastructure.gateway;

import com.bone.system.domain.gateway.KeyMetricsGateway;
import com.bone.system.domain.model.console.KeyMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 直接对核心表做 {@code COUNT(*)} 的关键指标网关。
 *
 * <p>原则：<b>graceful degradation</b> —— 任一表缺失或查询抛错时把该字段计 0 并 DEBUG， 但绝不让概览整体失败。等价于业界 Operational
 * Dashboard 的"best-effort"模式。
 *
 * <p>查询参考表：
 *
 * <ul>
 *   <li>{@code iam_account}（活跃账号 = {@code deleted=0}）
 *   <li>{@code meta_entity}（{@code deleted=0}）
 *   <li>{@code int_flow}（{@code deleted=0}）
 *   <li>{@code exts_plugin_version where is_active=1}（激活插件数）
 * </ul>
 *
 * <p>JVM 线程读取自 Micrometer（与 ResourceUsage 共享）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcKeyMetricsGatewayAdapter implements KeyMetricsGateway {

  private final JdbcTemplate jdbcTemplate;
  private final MeterRegistry meterRegistry;

  @Override
  public KeyMetrics collect() {
    return KeyMetrics.builder()
        .userCount(safeCount("SELECT COUNT(*) FROM iam_account WHERE deleted = 0", "iam_account"))
        .entityCount(safeCount("SELECT COUNT(*) FROM meta_entity WHERE deleted = 0", "meta_entity"))
        .integrationFlowCount(
            safeCount("SELECT COUNT(*) FROM int_flow WHERE deleted = 0", "int_flow"))
        .extensionPluginCount(
            safeCount(
                "SELECT COUNT(*) FROM exts_plugin_version WHERE is_active = 1",
                "exts_plugin_version"))
        .orderCount(0)
        .transactionAmount(0)
        .jvmThreadsLive((long) gauge("jvm.threads.live"))
        .jvmThreadsDaemon((long) gauge("jvm.threads.daemon"))
        .updatedAt(Instant.now())
        .build();
  }

  private long safeCount(String sql, String tableHint) {
    try {
      Long v = jdbcTemplate.queryForObject(sql, Long.class);
      return v == null ? 0 : v;
    } catch (Exception e) {
      log.debug("[console.keyMetrics] count failed on {}: {}", tableHint, e.getMessage());
      return 0;
    }
  }

  private double gauge(String name) {
    try {
      return meterRegistry.get(name).gauge().value();
    } catch (Exception e) {
      return 0;
    }
  }
}
