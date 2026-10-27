package com.bone.synap.settlement.adapter.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 持久化方言：消除 H2 专有 {@code MERGE ... KEY(...)} 与 MySQL 生产语法之间的差异。
 *
 * <p>理算端口有 3 处"不存在则插入、存在则更新/忽略"的语义（结论存储、信任额度台账、
 * 累计行占位），H2 用 {@code MERGE ... KEY}，MySQL 用 {@code INSERT ... ON DUPLICATE KEY UPDATE} /
 * {@code INSERT IGNORE}。把方言收敛到本接口后，引擎与端口代码零改动即可在 H2（仿真）与
 * MySQL（E2E）之间切换——这是业界"一套逻辑、按库生成 SQL"的标准做法。
 *
 * <p>测试期可用 H2 的 {@code MODE=MySQL} 兼容模式跑通 MySQL 方言 SQL（见 JdbcPortsMySqlCompatTest），
 * 以离线方式验证生产 SQL 正确性；真实 MySQL 实例压测为独立待办（需 MySQL 可达）。
 */
public interface SqlDialect {

    /** 结论存储 upsert（幂等键唯一）。 */
    String upsertCalc();

    /** 信任额度台账 upsert。 */
    String upsertLedger();

    /** 累计行占位：仅当行不存在时插入 key 列，已存在则绝不触碰 version（否则破坏乐观锁）。 */
    String ensureAccumRow();

    /** 依据 DataSource 元数据自动识别（H2 / MySQL），识别失败回退 H2。 */
    static SqlDialect auto(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("mysql")) return mysql();
            return h2();
        } catch (SQLException ex) {
            return h2();
        }
    }

    static SqlDialect h2() { return H2.INSTANCE; }

    static SqlDialect mysql() { return MySql.INSTANCE; }

    /** H2：原生 MERGE ... KEY。 */
    final class H2 implements SqlDialect {
        private static final H2 INSTANCE = new H2();
        @Override public String upsertCalc() {
            return "MERGE INTO t_settlement_calc (idempotency_key, claim_id, tenant_id, policy_id, period_key, "
                    + "net_payout, status, annual_deductible_applied, annual_limit_applied, "
                    + "liability_applied, third_party_applied, snapshot_version, trace_id, denied, deny_reasons) "
                    + "KEY(idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        }
        @Override public String upsertLedger() {
            return "MERGE INTO t_settlement_ledger (tenant_claim, reserved) KEY(tenant_claim) VALUES (?, ?)";
        }
        @Override public String ensureAccumRow() {
            return "MERGE INTO t_settlement_accum (policy_period_key) KEY(policy_period_key) VALUES (?)";
        }
    }

    /** MySQL：ON DUPLICATE KEY UPDATE / INSERT IGNORE。 */
    final class MySql implements SqlDialect {
        private static final MySql INSTANCE = new MySql();
        @Override public String upsertCalc() {
            return "INSERT INTO t_settlement_calc (idempotency_key, claim_id, tenant_id, policy_id, period_key, "
                    + "net_payout, status, annual_deductible_applied, annual_limit_applied, "
                    + "liability_applied, third_party_applied, snapshot_version, trace_id, denied, deny_reasons) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "claim_id=VALUES(claim_id), tenant_id=VALUES(tenant_id), policy_id=VALUES(policy_id), "
                    + "period_key=VALUES(period_key), net_payout=VALUES(net_payout), status=VALUES(status), "
                    + "annual_deductible_applied=VALUES(annual_deductible_applied), "
                    + "annual_limit_applied=VALUES(annual_limit_applied), "
                    + "liability_applied=VALUES(liability_applied), "
                    + "third_party_applied=VALUES(third_party_applied), "
                    + "snapshot_version=VALUES(snapshot_version), trace_id=VALUES(trace_id), "
                    + "denied=VALUES(denied), deny_reasons=VALUES(deny_reasons)";
        }
        @Override public String upsertLedger() {
            return "INSERT INTO t_settlement_ledger (tenant_claim, reserved) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE reserved=VALUES(reserved)";
        }
        @Override public String ensureAccumRow() {
            // 仅插 key 列，已存在则忽略（不触碰 version）——H2 的 INSERT IGNORE 在 MySQL 模式下亦被 H2 支持
            return "INSERT IGNORE INTO t_settlement_accum (policy_period_key) VALUES (?)";
        }
    }
}
