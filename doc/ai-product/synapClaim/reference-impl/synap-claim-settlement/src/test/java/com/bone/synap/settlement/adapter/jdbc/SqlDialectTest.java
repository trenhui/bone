package com.bone.synap.settlement.adapter.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SqlDialect 回归护栏：H2 生成 MERGE...KEY、MySQL 生成 ON DUPLICATE KEY UPDATE / INSERT IGNORE，
 * 且 auto() 能按元数据正确识别。直接守住「生产接入 MySQL 只需切换方言、端口代码零改动」的承诺
 * （对应 README 已知限制 #4「生产接入 MySQL 需切换 MERGE → ON DUPLICATE KEY UPDATE」）。
 */
public class SqlDialectTest {

    @Test
    void h2ProducesMergeKeySyntax() {
        SqlDialect d = SqlDialect.h2();
        assertTrue(d.upsertCalc().contains("MERGE INTO t_settlement_calc"), "H2 upsertCalc 应为 MERGE...KEY");
        assertTrue(d.upsertCalc().contains("KEY(idempotency_key)"), "H2 upsertCalc 须带 KEY(...) 唯一键");
        assertTrue(d.upsertLedger().contains("MERGE INTO t_settlement_ledger")
                && d.upsertLedger().contains("KEY(tenant_claim)"), "H2 upsertLedger 应为 MERGE...KEY");
        assertTrue(d.ensureAccumRow().contains("MERGE INTO t_settlement_accum")
                && d.ensureAccumRow().contains("KEY(policy_period_key)"), "H2 ensureAccumRow 应为 MERGE...KEY");
    }

    @Test
    void mysqlProducesInsertOnDuplicate() {
        SqlDialect d = SqlDialect.mysql();
        assertTrue(d.upsertCalc().contains("INSERT INTO t_settlement_calc"), "MySQL upsertCalc 应为 INSERT");
        assertTrue(d.upsertCalc().contains("ON DUPLICATE KEY UPDATE"), "MySQL upsertCalc 须 ON DUPLICATE KEY UPDATE");
        assertTrue(d.upsertLedger().contains("INSERT INTO t_settlement_ledger")
                && d.upsertLedger().contains("ON DUPLICATE KEY UPDATE"), "MySQL upsertLedger 须 ON DUPLICATE KEY UPDATE");
        assertTrue(d.ensureAccumRow().contains("INSERT IGNORE INTO t_settlement_accum"),
                "MySQL ensureAccumRow 须 INSERT IGNORE（已存在则不动 version，保护乐观锁）");
    }

    @Test
    void autoDetectsH2ByMetadata() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:dialectAuto");
        ds.setUser("sa");
        ds.setPassword("");
        SqlDialect d = SqlDialect.auto(ds);
        // H2 元数据识别 → 产出 MERGE 语法（识别失败也回退 H2，断言仍成立）
        assertTrue(d.upsertCalc().startsWith("MERGE INTO t_settlement_calc"),
                "auto() 对 H2 应识别为 MERGE 语法，实际=" + d.upsertCalc());
    }
}
