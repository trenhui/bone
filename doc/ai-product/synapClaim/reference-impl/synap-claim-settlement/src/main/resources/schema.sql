-- ============================================================================
-- synap-claim-settlement 持久化 DDL（H2 语法，MySQL 仅需把 CLOB->TEXT、
-- AUTO_INCREMENT/IDENTITY 保持不变即可；MERGE INTO ... KEY 在 MySQL 改为
-- INSERT ... ON DUPLICATE KEY UPDATE）。
--
-- 三值额度模型（ACORD / ISO）：可用 = initial - claim(已赔付) - frozen(预授权冻结)
-- 累计采用乐观锁 version 字段，CAS 失败即 CONFLICT（fail-closed，禁静默覆盖）。
-- ============================================================================

-- 保单+账期 累计用量（含版本号用于乐观锁）
CREATE TABLE t_settlement_accum (
    policy_period_key VARCHAR(255) PRIMARY KEY,   -- tenantId@policyId@periodKey
    deductible_used   DECIMAL(20,6) NOT NULL DEFAULT 0,
    limit_used       DECIMAL(20,6) NOT NULL DEFAULT 0,
    liability_used   DECIMAL(20,6) NOT NULL DEFAULT 0,
    frozen           DECIMAL(20,6) NOT NULL DEFAULT 0,
    version          BIGINT        NOT NULL DEFAULT 0
);

-- 单案件累计占用（按 claimId upsert，重算时以新值替换旧值而非累加）
CREATE TABLE t_settlement_accum_claim (
    claim_key     VARCHAR(255) PRIMARY KEY,        -- policy_period_key#claimId
    deduct_delta  DECIMAL(20,6) NOT NULL DEFAULT 0,
    limit_delta   DECIMAL(20,6) NOT NULL DEFAULT 0,
    liab_delta    DECIMAL(20,6) NOT NULL DEFAULT 0
);

-- 预授权冻结（三值额度 frozen 维度）
CREATE TABLE t_settlement_frozen (
    auth_key VARCHAR(255) PRIMARY KEY,            -- policy_period_key#authId
    amount   DECIMAL(20,6) NOT NULL DEFAULT 0
);

-- 幂等结果存储（CAS 租约 + 按 claimId 查最新结论，支撑重算冲正）
CREATE TABLE t_settlement_calc (
    seq               BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key   VARCHAR(512) NOT NULL UNIQUE,
    claim_id          VARCHAR(255),
    tenant_id         VARCHAR(255),
    policy_id         VARCHAR(255),
    period_key        VARCHAR(255),
    net_payout        DECIMAL(20,6),
    status            VARCHAR(20),
    annual_deductible_applied DECIMAL(20,6),
    annual_limit_applied      DECIMAL(20,6),
    liability_applied         DECIMAL(20,6),
    third_party_applied       DECIMAL(20,6),
    snapshot_version BIGINT,
    trace_id          VARCHAR(255),
    denied            SMALLINT,
    deny_reasons      VARCHAR(2000)
);

-- 幂等租约表（并发同键仅一个请求可取得执行权）
CREATE TABLE t_settlement_lease (
    idempotency_key VARCHAR(512) PRIMARY KEY,
    owner           VARCHAR(255),
    expire_at       BIGINT
);

-- 直付预授权生命周期（hold/capture/void/TTL）
CREATE TABLE t_preauth (
    auth_id     VARCHAR(255) PRIMARY KEY,
    tenant_id   VARCHAR(255),
    claim_id    VARCHAR(255),
    amount      DECIMAL(20,6),
    status      VARCHAR(20),   -- HELD / CAPTURED / VOIDED
    remaining   DECIMAL(20,6),
    created_at  BIGINT,
    ttl_millis  BIGINT
);

-- C11 信任额度预留（按 tenant#claim 维度）
CREATE TABLE t_settlement_ledger (
    tenant_claim VARCHAR(512) PRIMARY KEY,
    reserved     DECIMAL(20,6)
);

-- C4 快照审计
CREATE TABLE t_settlement_trace (
    trace_id       VARCHAR(255) PRIMARY KEY,
    claim_id       VARCHAR(255),
    snapshot_json  VARCHAR(8192)
);

-- 领域事件 Outbox（最终一致，对账/后督订阅）
CREATE TABLE t_settlement_outbox (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id  VARCHAR(255),
    event     VARCHAR(512),
    published SMALLINT DEFAULT 0
);
