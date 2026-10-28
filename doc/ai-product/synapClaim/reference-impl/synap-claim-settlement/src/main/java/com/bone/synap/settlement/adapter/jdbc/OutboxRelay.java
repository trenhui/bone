package com.bone.synap.settlement.adapter.jdbc;

import com.bone.synap.settlement.port.Ports;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * 事务发件箱中继（Transactional Outbox Relay，业界标准最终一致模式）。
 *
 * 理算引擎在 {@code settle()} 中经 {@link Ports.OutboxPort} 把领域事件写入本地
 * {@code t_settlement_outbox} 表（与理算结论同库同事务，保证「写结论」与「发事件」原子）；
 * 本中继轮询其中未投递（published=0）的事件，经 {@link Ports.EventPublisher} 真正发往下游
 * （消息中间件 / 对账 / 后督订阅），成功后置 published=1；连续失败超过 maxAttempts 的事件
 * 移入死信表 {@code t_settlement_outbox_dlq} —— **绝不静默丢弃**（fail-closed，防假绿）。
 *
 * 设计要点（与引擎解耦）：本类由部署侧调度（定时任务 / 消息驱动），引擎只负责写 outbox，
 * 投递与重试完全外置，符合六边形「端口防腐」。幂等：at-least-once 语义下同一事件可能被重复
 * 投递，EventPublisher 须以 claimId + event 作为幂等键去重（下游消费幂等）。
 *
 * 使用的均为标准 SQL（UPDATE/INSERT/DELETE + LIMIT ?），H2/MySQL 通用，无需方言切换。
 */
public final class OutboxRelay {

    private final DataSource ds;
    private final Ports.EventPublisher publisher;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(DataSource ds, Ports.EventPublisher publisher) {
        this(ds, publisher, 100, 5);
    }

    public OutboxRelay(DataSource ds, Ports.EventPublisher publisher, int batchSize, int maxAttempts) {
        this.ds = ds;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** 单次轮询：投递一批未发布事件。返回本次投递 / 死信 / 暂未死信(仍待重试)计数。 */
    public RelayResult relayOnce() throws SQLException {
        int published = 0, deadLettered = 0, failed = 0;
        try (Connection c = ds.getConnection()) {
            // 取一批未发布事件（按 id 升序，先进先出、可重入）
            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT id, claim_id, event FROM t_settlement_outbox WHERE published = 0 ORDER BY id ASC LIMIT ?")) {
                sel.setInt(1, batchSize);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong(1);
                        String claimId = rs.getString(2);
                        String event = rs.getString(3);
                        try {
                            publisher.publish(claimId, event);
                            markPublished(c, id);
                            published++;
                        } catch (Exception ex) {
                            int attempts = bumpAttempts(c, id, truncate(ex.getMessage()));
                            if (attempts >= maxAttempts) {
                                moveToDlq(c, id, claimId, event, truncate(ex.getMessage()), attempts);
                                deadLettered++;
                            } else {
                                failed++;
                            }
                        }
                    }
                }
            }
        }
        return new RelayResult(published, deadLettered, failed);
    }

    private void markPublished(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE t_settlement_outbox SET published = 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private int bumpAttempts(Connection c, long id, String err) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE t_settlement_outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?")) {
            ps.setString(1, err);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT attempts FROM t_settlement_outbox WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void moveToDlq(Connection c, long id, String claimId, String event, String err, int attempts) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO t_settlement_outbox_dlq (claim_id, event, last_error, attempts, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, claimId);
            ps.setString(2, event);
            ps.setString(3, err);
            ps.setInt(4, attempts);
            ps.setLong(5, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM t_settlement_outbox WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1024 ? s.substring(0, 1024) : s;
    }

    /** 单次轮询结果（投递 / 死信 / 仍待重试计数）。 */
    public static final class RelayResult {
        public final int published;
        public final int deadLettered;
        public final int failed;

        public RelayResult(int published, int deadLettered, int failed) {
            this.published = published;
            this.deadLettered = deadLettered;
            this.failed = failed;
        }

        @Override
        public String toString() {
            return "RelayResult{published=" + published
                    + " deadLettered=" + deadLettered + " failed=" + failed + "}";
        }
    }
}
