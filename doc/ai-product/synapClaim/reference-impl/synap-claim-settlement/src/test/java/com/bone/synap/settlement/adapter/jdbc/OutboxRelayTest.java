package com.bone.synap.settlement.adapter.jdbc;

import com.bone.synap.settlement.port.Ports;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OutboxRelay 防假绿测试：覆盖健康投递 / 连续失败死信 / 已投递不重投 三条路径。
 * 全部走 H2 内存库，验证事务发件箱「写结论与发事件原子、投递最终一致、失败可补偿」的契约。
 */
public class OutboxRelayTest {

    private DataSource memDs() {
        // 每个测试方法独立内存库，避免 DB_CLOSE_DELAY=-1 下跨方法表已存在冲突
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:outboxRelay_" + UUID.randomUUID().toString().replace('-', '_') + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private void insert(DataSource ds, String claimId, String event) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO t_settlement_outbox (claim_id, event, published) VALUES (?,?,0)")) {
            ps.setString(1, claimId);
            ps.setString(2, event);
            ps.executeUpdate();
        }
    }

    private int unpublished(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM t_settlement_outbox WHERE published = 0")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int dlqCount(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM t_settlement_outbox_dlq")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 健康路径：未发布事件经中继投递成功后置 published=1，发布者被调用对应次数。 */
    @Test
    void relaysUnpublishedEventsAndMarksPublished() throws Exception {
        DataSource ds = memDs();
        JdbcBackedPorts.initSchema(ds);
        List<String> delivered = new ArrayList<>();
        Ports.EventPublisher pub = (claimId, event) -> delivered.add(claimId + "|" + event);

        insert(ds, "C1", "SETTLED");
        insert(ds, "C2", "REJECTED");
        insert(ds, "C3", "SETTLED");

        OutboxRelay relay = new OutboxRelay(ds, pub);
        OutboxRelay.RelayResult r = relay.relayOnce();

        assertEquals(3, r.published);
        assertEquals(0, r.deadLettered);
        assertEquals(3, delivered.size());
        assertEquals(0, unpublished(ds));
        assertEquals(0, dlqCount(ds));
    }

    /** 发布者持续失败：超过 maxAttempts 后事件沉淀死信表，绝不无限重试 / 静默丢失。 */
    @Test
    void failuresExhaustToDeadLetter() throws Exception {
        DataSource ds = memDs();
        JdbcBackedPorts.initSchema(ds);
        Ports.EventPublisher failing = (claimId, event) -> {
            throw new RuntimeException("broker down");
        };

        insert(ds, "C1", "SETTLED");
        insert(ds, "C2", "SETTLED");

        OutboxRelay relay = new OutboxRelay(ds, failing, 100, 3);
        int totalDead = 0;
        for (int i = 0; i < 5; i++) totalDead += relay.relayOnce().deadLettered;

        assertEquals(2, totalDead, "连续失败事件应全部进入死信");
        assertEquals(0, unpublished(ds), "死信后 outbox 中不应残留未发布事件");
        assertEquals(2, dlqCount(ds));
    }

    /** 幂等：已发布事件再次轮询不再投递（at-least-once 下发布者不被重复调用）。 */
    @Test
    void alreadyPublishedNotRedelivered() throws Exception {
        DataSource ds = memDs();
        JdbcBackedPorts.initSchema(ds);
        AtomicInteger calls = new AtomicInteger();
        Ports.EventPublisher pub = (claimId, event) -> calls.incrementAndGet();

        insert(ds, "C1", "SETTLED");
        OutboxRelay relay = new OutboxRelay(ds, pub);
        relay.relayOnce();
        OutboxRelay.RelayResult r2 = relay.relayOnce();

        assertEquals(1, calls.get());
        assertEquals(0, r2.published);
    }
}
