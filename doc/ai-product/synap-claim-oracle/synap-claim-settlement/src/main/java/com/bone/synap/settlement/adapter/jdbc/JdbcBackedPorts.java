package com.bone.synap.settlement.adapter.jdbc;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.AdjustmentLine;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.port.Ports;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生产级 JDBC 端口实现（六边形防腐层）。
 *
 * 直接实现 {@link Ports} 定义的 7 个端口接口，使理算引擎可挂载到真实数据库，
 * 并通过 DDL（见 resources/schema.sql）保证：
 *   - 跨案累计的乐观锁（version 字段 + CAS）：并发提交版本不符即 {@code CONFLICT}（fail-closed）；
 *   - 按 claimId upsert 的累计占用：重算以新值替换旧值，不累加（诊断 N2）；
 *   - 冲正（reverse）：回退累计占用 + 删除案件占用行（诊断 N8）；
 *   - 预授权冻结（frozen 维度）计入三值额度，TTL 生命周期（诊断 N3 + 亿保即时结算）；
 *   - 幂等 CAS 租约：同键并发仅一个请求可取执行权（诊断 N7）。
 *
 * 采用纯 JDBC（javax.sql.DataSource），不依赖任何框架，可直接嵌入 Bone 服务或独立运行。
 * MySQL 仅需把 schema.sql 中的 MERGE INTO ... KEY 改为 INSERT ... ON DUPLICATE KEY UPDATE。
 */
public final class JdbcBackedPorts implements Ports.LedgerPort, Ports.TracePort, Ports.CatalogPort,
        Ports.OutboxPort, Ports.AccumulationPort, Ports.SettlementStorePort, Ports.PreAuthPort {

    private final DataSource ds;
    private final SqlDialect dialect;

    /** 自动识别方言（H2 / MySQL）。 */
    public JdbcBackedPorts(DataSource ds) {
        this(ds, SqlDialect.auto(ds));
    }

    /** 显式指定方言（测试期可强制 MySql 以在 H2 MySQL 模式校验生产 SQL）。 */
    public JdbcBackedPorts(DataSource ds, SqlDialect dialect) {
        this.ds = ds;
        this.dialect = dialect;
    }

    /** 在目标库执行 schema.sql（测试/首次部署用）。 */
    public static void initSchema(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection()) {
            String sql;
            try {
                sql = new String(JdbcBackedPorts.class.getClassLoader()
                        .getResourceAsStream("schema.sql").readAllBytes());
            } catch (java.io.IOException ex) {
                throw new RuntimeException("无法读取 schema.sql", ex);
            }
            for (String stmt : sql.split(";")) {
                String s = stmt.strip();
                if (s.isEmpty()) continue;
                try (PreparedStatement ps = c.prepareStatement(s)) {
                    ps.execute();
                }
            }
        }
    }

    // ============================ AccumulationPort ============================

    @Override
    public Ports.AccUsage getUsage(String tenantScopedPolicyId, String periodKey) {
        String pk = pk(tenantScopedPolicyId, periodKey);
        try (Connection c = ds.getConnection()) {
            ensureAccumRow(c, pk);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT deductible_used, limit_used, liability_used, frozen, version " +
                            "FROM t_settlement_accum WHERE policy_period_key = ?")) {
                ps.setString(1, pk);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Ports.AccUsage(
                                Money.of(rs.getBigDecimal(1)), Money.of(rs.getBigDecimal(2)),
                                Money.of(rs.getBigDecimal(3)), Money.of(rs.getBigDecimal(4)),
                                rs.getLong(5));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("getUsage failed", ex);
        }
        return Ports.AccUsage.empty();
    }

    @Override
    public Ports.CommitResult commitUsage(String tenantScopedPolicyId, String periodKey, String claimId,
                                          Money deductibleDelta, Money limitDelta, Money liabilityDelta,
                                          long expectedVersion) {
        String pk = pk(tenantScopedPolicyId, periodKey);
        String claimKey = pk + "#" + claimId;
        Connection c = null;
        try {
            c = ds.getConnection();
            c.setAutoCommit(false);
            ensureAccumRow(c, pk);
            AccumRow a = selectAccumForUpdate(c, pk);
            Deltas old = selectClaim(c, claimKey);
            if (old != null) {
                if (old.equals(nz(deductibleDelta), nz(limitDelta), nz(liabilityDelta))) {
                    c.commit();
                    return Ports.CommitResult.DUPLICATE;
                }
                Money baseD = a.deductibleUsed.subtract(old.d).clampNonNegative();
                Money baseL = a.limitUsed.subtract(old.l).clampNonNegative();
                Money baseLi = a.liabilityUsed.subtract(old.li).clampNonNegative();
                long nv = a.version + 1;
                int u = updateAccum(c, pk, baseD, baseL, baseLi, a.frozen, nv, a.version);
                if (u == 0) { c.rollback(); return Ports.CommitResult.CONFLICT; }
                updateClaim(c, claimKey, deductibleDelta, limitDelta, liabilityDelta);
                c.commit();
                return Ports.CommitResult.COMMITTED;
            } else {
                long nv = a.version + 1;
                int u = updateAccum(c, pk,
                        a.deductibleUsed.add(nz(deductibleDelta)),
                        a.limitUsed.add(nz(limitDelta)),
                        a.liabilityUsed.add(nz(liabilityDelta)),
                        a.frozen, nv, expectedVersion);
                if (u == 0) { c.rollback(); return Ports.CommitResult.CONFLICT; }
                insertClaim(c, claimKey, deductibleDelta, limitDelta, liabilityDelta);
                c.commit();
                return Ports.CommitResult.COMMITTED;
            }
        } catch (SQLException ex) {
            safeRollback(c);
            throw new RuntimeException("commitUsage failed", ex);
        } finally {
            safeClose(c);
        }
    }

    @Override
    public void reverse(String tenantScopedPolicyId, String periodKey, String claimId,
                       Money deductibleDelta, Money limitDelta, Money liabilityDelta) {
        String pk = pk(tenantScopedPolicyId, periodKey);
        String claimKey = pk + "#" + claimId;
        Connection c = null;
        try {
            c = ds.getConnection();
            c.setAutoCommit(false);
            ensureAccumRow(c, pk);
            AccumRow a = selectAccumForUpdate(c, pk);
            Money nd = a.deductibleUsed.subtract(nz(deductibleDelta)).clampNonNegative();
            Money nl = a.limitUsed.subtract(nz(limitDelta)).clampNonNegative();
            Money nli = a.liabilityUsed.subtract(nz(liabilityDelta)).clampNonNegative();
            updateAccum(c, pk, nd, nl, nli, a.frozen, a.version + 1, a.version);
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM t_settlement_accum_claim WHERE claim_key = ?")) {
                ps.setString(1, claimKey);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            safeRollback(c);
            throw new RuntimeException("reverse failed", ex);
        } finally {
            safeClose(c);
        }
    }

    @Override
    public Ports.CommitResult commitFrozen(String tenantScopedPolicyId, String periodKey, String authId, Money amount) {
        String pk = pk(tenantScopedPolicyId, periodKey);
        String authKey = pk + "#" + authId;
        Connection c = null;
        try {
            c = ds.getConnection();
            c.setAutoCommit(false);
            ensureAccumRow(c, pk);
            AccumRow a = selectAccumForUpdate(c, pk);
            if (selectFrozen(c, authKey) != null) { c.commit(); return Ports.CommitResult.DUPLICATE; }
            Money nf = a.frozen.add(nz(amount));
            updateAccum(c, pk, a.deductibleUsed, a.limitUsed, a.liabilityUsed, nf, a.version + 1, a.version);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO t_settlement_frozen (auth_key, amount) VALUES (?, ?)")) {
                ps.setString(1, authKey);
                ps.setBigDecimal(2, nz(amount).raw());
                ps.executeUpdate();
            }
            c.commit();
            return Ports.CommitResult.COMMITTED;
        } catch (SQLException ex) {
            safeRollback(c);
            throw new RuntimeException("commitFrozen failed", ex);
        } finally {
            safeClose(c);
        }
    }

    @Override
    public void releaseFrozen(String tenantScopedPolicyId, String periodKey, String authId) {
        String pk = pk(tenantScopedPolicyId, periodKey);
        String authKey = pk + "#" + authId;
        Connection c = null;
        try {
            c = ds.getConnection();
            c.setAutoCommit(false);
            Money amt = selectFrozen(c, authKey);
            if (amt == null) { c.commit(); return; }
            ensureAccumRow(c, pk);
            AccumRow a = selectAccumForUpdate(c, pk);
            Money nf = a.frozen.subtract(amt).clampNonNegative();
            updateAccum(c, pk, a.deductibleUsed, a.limitUsed, a.liabilityUsed, nf, a.version + 1, a.version);
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM t_settlement_frozen WHERE auth_key = ?")) {
                ps.setString(1, authKey);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            safeRollback(c);
            throw new RuntimeException("releaseFrozen failed", ex);
        } finally {
            safeClose(c);
        }
    }

    // ============================ SettlementStorePort ============================

    @Override
    public IndemnityCalculation find(String idempotencyKey) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT claim_id, tenant_id, policy_id, period_key, net_payout, status, " +
                             "annual_deductible_applied, annual_limit_applied, liability_applied, " +
                             "third_party_applied, snapshot_version, trace_id, denied, deny_reasons " +
                             "FROM t_settlement_calc WHERE idempotency_key = ?")) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return CalcCodec.decode(rs);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("store.find failed", ex);
        }
        return null;
    }

    @Override
    public void save(String idempotencyKey, IndemnityCalculation calc) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(dialect.upsertCalc())) {
            ps.setString(1, idempotencyKey);
            ps.setString(2, calc.claimId);
            ps.setString(3, calc.tenantId);
            ps.setString(4, calc.policyId);
            ps.setString(5, calc.periodKey);
            ps.setBigDecimal(6, nz(calc.netPayout).raw());
            ps.setString(7, calc.status.name());
            ps.setBigDecimal(8, nz(calc.annualDeductibleApplied).raw());
            ps.setBigDecimal(9, nz(calc.annualLimitApplied).raw());
            ps.setBigDecimal(10, nz(calc.liabilityApplied).raw());
            ps.setBigDecimal(11, nz(calc.thirdPartyApplied).raw());
            ps.setLong(12, calc.snapshotVersion);
            ps.setString(13, calc.traceId);
            ps.setShort(14, (short) (calc.denied ? 1 : 0));
            ps.setString(15, String.join("|", calc.denyReasons));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("store.save failed", ex);
        }
    }

    @Override
    public boolean tryBegin(String idempotencyKey) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO t_settlement_lease (idempotency_key, owner, expire_at) VALUES (?, ?, ?)")) {
            ps.setString(1, idempotencyKey);
            ps.setString(2, "ENGINE");
            ps.setLong(3, System.currentTimeMillis() + 5 * 60_000L);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            return false; // 唯一约束冲突 = 已有在途请求
        }
    }

    @Override
    public IndemnityCalculation findLatestByClaim(String claimId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT claim_id, tenant_id, policy_id, period_key, net_payout, status, " +
                             "annual_deductible_applied, annual_limit_applied, liability_applied, " +
                             "third_party_applied, snapshot_version, trace_id, denied, deny_reasons " +
                             "FROM t_settlement_calc WHERE claim_id = ? ORDER BY seq DESC LIMIT 1")) {
            ps.setString(1, claimId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return CalcCodec.decode(rs);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("findLatestByClaim failed", ex);
        }
        return null;
    }

    // ============================ PreAuthPort ============================

    @Override
    public String hold(String tenantId, String claimId, Money amount, long ttlMillis) {
        String authId = "AUTH-" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO t_preauth (auth_id, tenant_id, claim_id, amount, status, remaining, created_at, ttl_millis) " +
                             "VALUES (?,?,?,?, 'HELD', ?, ?, ?)")) {
            ps.setString(1, authId);
            ps.setString(2, tenantId);
            ps.setString(3, claimId);
            ps.setBigDecimal(4, nz(amount).raw());
            ps.setBigDecimal(5, nz(amount).raw());
            ps.setLong(6, System.currentTimeMillis());
            ps.setLong(7, ttlMillis);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("preAuth.hold failed", ex);
        }
        return authId;
    }

    @Override
    public Money capture(String authId, Money amount) {
        try (Connection c = ds.getConnection()) {
            PreAuthRow r = selectPreAuth(c, authId);
            if (r == null || !"HELD".equals(r.status)) return Money.ZERO;
            Money taken = amount.min(r.remaining);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE t_preauth SET status = 'CAPTURED', remaining = remaining - ? " +
                            "WHERE auth_id = ? AND status = 'HELD'")) {
                ps.setBigDecimal(1, taken.raw());
                ps.setString(2, authId);
                ps.executeUpdate();
            }
            return taken;
        } catch (SQLException ex) {
            throw new RuntimeException("preAuth.capture failed", ex);
        }
    }

    @Override
    public void voidAuth(String authId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE t_preauth SET status = 'VOIDED', remaining = 0 WHERE auth_id = ? AND status = 'HELD'")) {
            ps.setString(1, authId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("preAuth.void failed", ex);
        }
    }

    @Override
    public boolean isExpired(String authId) {
        PreAuthRow r = selectPreAuthSilently(authId);
        if (r == null) return true;
        if (!"HELD".equals(r.status)) return true;
        return System.currentTimeMillis() > r.createdAt + r.ttlMillis;
    }

    @Override
    public Money held(String authId) {
        PreAuthRow r = selectPreAuthSilently(authId);
        if (r == null || !"HELD".equals(r.status)) return Money.ZERO;
        return r.remaining;
    }

    // ============================ LedgerPort / TracePort / CatalogPort / OutboxPort ============================

    @Override
    public void reserve(String tenantId, String claimId, Money amount) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(dialect.upsertLedger())) {
            ps.setString(1, tenantId + "#" + claimId);
            ps.setBigDecimal(2, nz(amount).raw());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("ledger.reserve failed", ex);
        }
    }

    @Override
    public Money remaining(String tenantId, String claimId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT reserved FROM t_settlement_ledger WHERE tenant_claim = ?")) {
            ps.setString(1, tenantId + "#" + claimId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Money.of(rs.getBigDecimal(1));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("ledger.remaining failed", ex);
        }
        return Money.ZERO;
    }

    @Override
    public String record(FormulaSnapshot snap, String claimId) {
        String traceId = "TRACE-" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO t_settlement_trace (trace_id, claim_id, snapshot_json) VALUES (?,?,?)")) {
            ps.setString(1, traceId);
            ps.setString(2, claimId);
            ps.setString(3, snap.liabilityId + "@" + snap.snapshotVersion);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("trace.record failed", ex);
        }
        return traceId;
    }

    @Override
    public String resolve(String itemCode) {
        return "CAT:" + itemCode; // 真实实现绑定 C6 受控 RAG / 主数据目录编码服务
    }

    @Override
    public void publish(String claimId, String event) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO t_settlement_outbox (claim_id, event, published) VALUES (?,?,0)")) {
            ps.setString(1, claimId);
            ps.setString(2, event);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("outbox.publish failed", ex);
        }
    }

    // ============================ 内部工具 ============================

    private static String pk(String key, String period) { return key + "@" + period; }

    private static Money nz(Money m) { return m == null ? Money.ZERO : m; }

    private void ensureAccumRow(Connection c, String pk) throws SQLException {
        // 仅当行不存在时插入（其余列取 DEFAULT 0）；已存在则不动 version，否则会破坏乐观锁。
        try (PreparedStatement ps = c.prepareStatement(dialect.ensureAccumRow())) {
            ps.setString(1, pk);
            ps.executeUpdate();
        }
    }

    private AccumRow selectAccumForUpdate(Connection c, String pk) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT deductible_used, limit_used, liability_used, frozen, version " +
                        "FROM t_settlement_accum WHERE policy_period_key = ? FOR UPDATE")) {
            ps.setString(1, pk);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new AccumRow(Money.of(rs.getBigDecimal(1)), Money.of(rs.getBigDecimal(2)),
                        Money.of(rs.getBigDecimal(3)), Money.of(rs.getBigDecimal(4)), rs.getLong(5));
            }
        }
        return new AccumRow(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0);
    }

    private int updateAccum(Connection c, String pk, Money d, Money l, Money li, Money f, long nv, long expectedV)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE t_settlement_accum SET deductible_used=?, limit_used=?, liability_used=?, " +
                        "frozen=?, version=? WHERE policy_period_key=? AND version=?")) {
            ps.setBigDecimal(1, d.raw());
            ps.setBigDecimal(2, l.raw());
            ps.setBigDecimal(3, li.raw());
            ps.setBigDecimal(4, f.raw());
            ps.setLong(5, nv);
            ps.setString(6, pk);
            ps.setLong(7, expectedV);
            return ps.executeUpdate();
        }
    }

    private Deltas selectClaim(Connection c, String claimKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT deduct_delta, limit_delta, liab_delta FROM t_settlement_accum_claim WHERE claim_key = ?")) {
            ps.setString(1, claimKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Deltas(Money.of(rs.getBigDecimal(1)),
                        Money.of(rs.getBigDecimal(2)), Money.of(rs.getBigDecimal(3)));
            }
        }
        return null;
    }

    private void insertClaim(Connection c, String claimKey, Money d, Money l, Money li) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO t_settlement_accum_claim (claim_key, deduct_delta, limit_delta, liab_delta) VALUES (?,?,?,?)")) {
            ps.setString(1, claimKey);
            ps.setBigDecimal(2, nz(d).raw());
            ps.setBigDecimal(3, nz(l).raw());
            ps.setBigDecimal(4, nz(li).raw());
            ps.executeUpdate();
        }
    }

    private void updateClaim(Connection c, String claimKey, Money d, Money l, Money li) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE t_settlement_accum_claim SET deduct_delta=?, limit_delta=?, liab_delta=? WHERE claim_key=?")) {
            ps.setBigDecimal(1, nz(d).raw());
            ps.setBigDecimal(2, nz(l).raw());
            ps.setBigDecimal(3, nz(li).raw());
            ps.setString(4, claimKey);
            ps.executeUpdate();
        }
    }

    private Money selectFrozen(Connection c, String authKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT amount FROM t_settlement_frozen WHERE auth_key = ?")) {
            ps.setString(1, authKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Money.of(rs.getBigDecimal(1));
            }
        }
        return null;
    }

    private PreAuthRow selectPreAuth(Connection c, String authId) throws SQLException {
        return doSelectPreAuth(c, authId);
    }

    private PreAuthRow selectPreAuthSilently(String authId) {
        try (Connection c = ds.getConnection()) {
            return doSelectPreAuth(c, authId);
        } catch (SQLException ex) {
            return null;
        }
    }

    private PreAuthRow doSelectPreAuth(Connection c, String authId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status, remaining, created_at, ttl_millis FROM t_preauth WHERE auth_id = ?")) {
            ps.setString(1, authId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new PreAuthRow(rs.getString(1), Money.of(rs.getBigDecimal(2)),
                        rs.getLong(3), rs.getLong(4));
            }
        }
        return null;
    }

    private static void safeRollback(Connection c) {
        if (c != null) try { c.rollback(); } catch (SQLException ignore) { }
    }

    private static void safeClose(Connection c) {
        if (c != null) try { c.close(); } catch (SQLException ignore) { }
    }

    // ============================ 行记录载体 ============================

    private static final class AccumRow {
        final Money deductibleUsed, limitUsed, liabilityUsed, frozen;
        final long version;
        AccumRow(Money d, Money l, Money li, Money f, long v) {
            this.deductibleUsed = d; this.limitUsed = l; this.liabilityUsed = li; this.frozen = f; this.version = v;
        }
    }

    private static final class Deltas {
        final Money d, l, li;
        Deltas(Money d, Money l, Money li) { this.d = d; this.l = l; this.li = li; }
        boolean equals(Money od, Money ol, Money oli) {
            return d.cmp(od) == 0 && l.cmp(ol) == 0 && li.cmp(oli) == 0;
        }
    }

    private static final class PreAuthRow {
        final String status; final Money remaining; final long createdAt, ttlMillis;
        PreAuthRow(String s, Money r, long c, long t) { this.status = s; this.remaining = r; this.createdAt = c; this.ttlMillis = t; }
    }

    /** 结论序列化载体：仅持久化冲正/幂等所需的聚合字段（行级明细不入库，按需从快照复算）。 */
    private static final class CalcCodec {
        static IndemnityCalculation decode(ResultSet rs) throws SQLException {
            List<String> deny = new ArrayList<>();
            String dr = rs.getString(14);
            if (dr != null && !dr.isEmpty()) for (String s : dr.split("\\|")) deny.add(s);
            Map<String, List<String>> ruleTrace = new LinkedHashMap<>();
            String status = rs.getString(6);
            IndemnityCalculation.SettlementStatus st = IndemnityCalculation.SettlementStatus.valueOf(status);
            return IndemnityCalculation.of(
                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    new ArrayList<AdjustmentLine>(),
                    Money.of(rs.getBigDecimal(5)),
                    Money.of(rs.getBigDecimal(7)), Money.of(rs.getBigDecimal(8)), Money.of(rs.getBigDecimal(9)),
                    ruleTrace, "", rs.getLong(11), rs.getString(12),
                    st, rs.getShort(13) == 1, deny, Money.of(rs.getBigDecimal(10)));
        }
    }
}
