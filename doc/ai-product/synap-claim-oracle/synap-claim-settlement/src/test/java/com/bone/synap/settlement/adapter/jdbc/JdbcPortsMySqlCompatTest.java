package com.bone.synap.settlement.adapter.jdbc;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.EngineFlags;
import com.bone.synap.settlement.engine.SettlementEngine;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MySQL 方言真实执行回归：用 H2 的 {@code MODE=MySQL} 兼容模式，实际执行 {@link SqlDialect#mysql()} 生成的
 * 生产 SQL（upsertCalc / upsertLedger / ensureAccumRow 的 ON DUPLICATE KEY UPDATE 形式），
 * 证明"生产接入 MySQL 只需切换方言、端口代码零改动"不是空话——SQL 真能跑通，而非仅字符串匹配。
 *
 * <p>这补上了 {@link SqlDialectTest} 只断言 SQL 文本、不执行的缺口（防"文档宣称 X ≠ 代码做了 X"漂移）。
 * 真实 MySQL 实例回归仍建议生产接入时补一轮，但本测试已离线证明生产 SQL 在 MySQL 语义下可执行。
 */
public class JdbcPortsMySqlCompatTest {

    private static final Money BIG = Money.of(1_000_000);

    private static DataSource mysqlModeDs(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        // MODE=MySQL：让 H2 接受并执行 MySQL 方言 SQL（含 ON DUPLICATE KEY UPDATE、AUTO_INCREMENT）
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static SettlementEngine mysqlEngine(DataSource ds) throws Exception {
        JdbcBackedPorts.initSchema(ds); // schema.sql 在 MySQL 模式下建表（AUTO_INCREMENT/DECIMAL 均兼容）
        JdbcBackedPorts p = new JdbcBackedPorts(ds, SqlDialect.mysql()); // 强制 MySQL 方言
        return new SettlementEngine(p, p, p, p, p, p, p, EngineFlags.allOn());
    }

    private static FormulaSnapshot reimbSnap(long liabilityLimit) {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), BIG, Money.ZERO, Money.ZERO, null));
        return new FormulaSnapshot("L1", "报销型责任", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                Money.of(liabilityLimit), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
    }

    private static ClaimContext ctx(String claimId, String policyId, Money fee,
                                   Money planRem, Money annualDed, Money annualLim) {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", fee);
        return new ClaimContext(claimId, "T1", "P1", policyId, "2026", f, planRem, annualDed, annualLim, List.of());
    }

    private static Money col(DataSource ds, String sql, String key) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.of(rs.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    @Test
    void mysqlUpsertSqlExecutesAndAccumulates() throws Exception {
        DataSource ds = mysqlModeDs("mysqlCompat");
        SettlementEngine e = mysqlEngine(ds);
        FormulaSnapshot snap = reimbSnap(5000);

        IndemnityCalculation c1 = e.settle(ctx("C1", "POL", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);
        IndemnityCalculation c2 = e.settle(ctx("C2", "POL", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);

        assertEquals(0, Money.of(4000).cmp(c1.netPayout), "首案应使用满额 4000（MySQL 方言下）");
        assertEquals(0, Money.of(1000).cmp(c2.netPayout), "次案仅余 1000 责任限额（MySQL 方言下）");

        // 累计落库（ensureAccumRow 的 ON DUPLICATE KEY UPDATE no-op + commitUsage 的 UPDATE 均真实执行）
        assertEquals(0, Money.of(5000).cmp(
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026")),
                "责任限额累计应在 MySQL 方言下持久化为 5000");

        // 幂等重算：upsertCalc 的 ON DUPLICATE KEY UPDATE 真实更新而非报错（证明生产 SQL 可执行）
        IndemnityCalculation c1Again = e.settle(ctx("C1", "POL", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);
        assertEquals(0, c1.netPayout.cmp(c1Again.netPayout), "幂等应返回相同结论（MySQL 方言 upsert 更新路径）");
        assertEquals(0, Money.of(5000).cmp(
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026")),
                "重算不应双计累计（MySQL 方言下）");
    }

    @Test
    void mysqlReversalExecutes() throws Exception {
        DataSource ds = mysqlModeDs("mysqlRev");
        SettlementEngine e = mysqlEngine(ds);
        FormulaSnapshot snap = reimbSnap(10000);
        IndemnityCalculation c = e.settle(ctx("C-REV", "POL", Money.of(3000), BIG, Money.ZERO, Money.ZERO), snap);
        assertEquals(0, Money.of(3000).cmp(c.netPayout), "首案赔付 3000（MySQL 方言下）");
        assertEquals(0, Money.of(3000).cmp(
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026")),
                "累计占用应为 3000（MySQL 方言下）");

        e.reverseSettlement(c);
        assertEquals(0, Money.ZERO.cmp(
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026")),
                "冲正后累计占用应归零（MySQL 方言下）");
    }
}
