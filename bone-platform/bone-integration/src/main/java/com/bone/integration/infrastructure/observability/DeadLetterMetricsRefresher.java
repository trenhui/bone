package com.bone.integration.infrastructure.observability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 周期性刷新死信积压 Gauge（INT-10 · Bone-可观测性规范 §4.2.1）。 */
@Component
@RequiredArgsConstructor
public class DeadLetterMetricsRefresher {

    private final JdbcTemplate jdbcTemplate;
    private final IntegrationExecutionMetrics metrics;

    @Scheduled(fixedDelayString = "${bone.integration.metrics.dead-letter-refresh-ms:60000}")
    public void refresh() {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT tenant_id AS tenantId, COUNT(*) AS cnt
                        FROM int_dead_letter
                        WHERE deleted = 0 AND status IN ('PENDING', 'RETRYING')
                        GROUP BY tenant_id
                        """);
        Map<Long, Long> pending = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long tenantId = ((Number) row.get("tenantId")).longValue();
            long count = ((Number) row.get("cnt")).longValue();
            pending.put(tenantId, count);
        }
        metrics.refreshDeadLetterGauges(pending);
    }
}
