-- ============================================================
-- 0086_seed_claim_config_reference_data.sql
--
-- 一句话：把 E2E 联调所需的保单/计划参考数据落到**表**里（此前只在 dev profile 的
--       进程内存桩里，重启即失、且非 dev 环境根本没有）。
--
-- 背景（为什么必须写这一条）
-- ----------------------------------------------------------------
-- 0085 把 PolicyQueryPort 的实现从「进程内存 ConcurrentHashMap + @Profile("dev") seeder」
-- 换成「读 t_claim_policy 表」——因为内存桩导致**生产环境报案必然被拒**
-- （非 dev 环境查任何保单都返回空 ⇒ 恒抛 SC_CLAIM_POLICY_NOT_ACTIVE 422）。
-- 换实现后，dev seeder 失去了存在意义（它灌的是内存 Map，而 Map 已不存在），
-- 于是 E2E 用的保单必须以**数据**形式存在，否则 scripts/e2e/* 全链路探针立刻失效。
--
-- 与 0073 的关系（口径一致，不是新发明）
-- ----------------------------------------------------------------
-- 0073_seed_claim_reference_data.sql 已经用同样的方式种过两份参考数据：
--   ① t_adj_liability（plan_id=9001，LIAB-E2E-9001-0001，ACTIVE）
--   ② t_quota_account（E2E-POLICY-0001 / plan 9001 / insured e2e-insured-hash-0001）
-- 本脚本补齐**同一组口径**缺失的第三块：保单与计划本体。
-- 关键联动：被保人清单里的 hash 必须与 0073 种的额度账户的 insured_id_hash **完全一致**
-- （e2e-insured-hash-0001），否则报案前置校验会因"被保人不匹配"（403）被拒，
-- 而 0073 种的额度账户就成了没人能用的孤儿数据。
--
-- 两张保单
-- ----------------------------------------------------------------
--   E2E-POLICY-0001    生效 2020-01-01 ~ 2030-12-31，规则 ACTIVE  → 正常链路
--   E2E-POLICY-EXPIRED 生效 2020-01-01 ~ 2020-12-31，规则 ACTIVE  → 已满期，验证"拒收"路径
--                      （已满期 ≠ 未启用：拒收原因是期间不在，而非开关关闭——
--                       两者要分别覆盖，故这条也置 ACTIVE）
--
-- 幂等
-- ----------------------------------------------------------------
-- 按业务唯一键判存在（不重复插入、不覆盖已有数据）：保单按 policy_no，
-- 计划按 (policy_no, plan_code)。已存在则跳过。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0086_seed_claim_config_reference_data.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- 一、计划（先于保单：保单引用计划，故先落计划行）
-- ─────────────────────────────────────────────────────────────
INSERT INTO t_claim_plan
    (id, policy_no, plan_code, plan_name, plan_limit, waiting_period, version_no,
     status, tenant_id, created_by, created_at, updated_by, updated_at, version, deleted)
SELECT 9001, 'E2E-POLICY-0001', 'E2E-PLAN-9001', 'E2E 门诊计划', 100000.00, 0, 'v1',
       'ACTIVE', 0, NULL, NOW(3), NULL, NOW(3), 0, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM t_claim_plan
     WHERE tenant_id = 0 AND policy_no = 'E2E-POLICY-0001' AND plan_code = 'E2E-PLAN-9001'
 );

INSERT INTO t_claim_plan
    (id, policy_no, plan_code, plan_name, plan_limit, waiting_period, version_no,
     status, tenant_id, created_by, created_at, updated_by, updated_at, version, deleted)
SELECT 9002, 'E2E-POLICY-EXPIRED', 'E2E-PLAN-9002', 'E2E 已过期计划', 100000.00, 0, 'v1',
       'ACTIVE', 0, NULL, NOW(3), NULL, NOW(3), 0, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM t_claim_plan
     WHERE tenant_id = 0 AND policy_no = 'E2E-POLICY-EXPIRED' AND plan_code = 'E2E-PLAN-9002'
 );

-- ─────────────────────────────────────────────────────────────
-- 二、保单（insured_snapshot 的 hash 必须与 0073 的额度账户一致）
-- ─────────────────────────────────────────────────────────────
INSERT INTO t_claim_policy
    (id, policy_no, policy_name, insurer_name, branch_name, insure_name, biz_identity_code,
     product_type, effective_start, effective_end, waiting_period, policy_version,
     insured_snapshot, config_status, stale_flag, synced_at,
     tenant_id, created_by, created_at, updated_by, updated_at, version, deleted)
SELECT 9151003, 'E2E-POLICY-0001', 'E2E 团险保单', '测试保司', '测试分公司', '测试投保公司', 'E2E-BIZ-001',
       'OUTPATIENT', '2020-01-01 00:00:00.000', '2030-12-31 23:59:59.000', 0, 'v1',
       '["e2e-insured-hash-0001"]', 'ACTIVE', 0, NOW(3),
       0, NULL, NOW(3), NULL, NOW(3), 0, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM t_claim_policy WHERE tenant_id = 0 AND policy_no = 'E2E-POLICY-0001'
 );

INSERT INTO t_claim_policy
    (id, policy_no, policy_name, insurer_name, branch_name, insure_name, biz_identity_code,
     product_type, effective_start, effective_end, waiting_period, policy_version,
     insured_snapshot, config_status, stale_flag, synced_at,
     tenant_id, created_by, created_at, updated_by, updated_at, version, deleted)
SELECT 9151004, 'E2E-POLICY-EXPIRED', 'E2E 已过期团险保单', '测试保司', '测试分公司', '测试投保公司', 'E2E-BIZ-002',
       'OUTPATIENT', '2020-01-01 00:00:00.000', '2020-12-31 23:59:59.000', 0, 'v1',
       '["e2e-insured-hash-0001"]', 'ACTIVE', 0, NOW(3),
       0, NULL, NOW(3), NULL, NOW(3), 0, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM t_claim_policy WHERE tenant_id = 0 AND policy_no = 'E2E-POLICY-EXPIRED'
 );

-- ─────────────────────────────────────────────────────────────
-- 三、验收
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0086;
DELIMITER $$
CREATE PROCEDURE sp_verify_0086()
BEGIN
    DECLARE v_policy INT DEFAULT 0;
    DECLARE v_plan   INT DEFAULT 0;

    -- ① 两张保单必须都在（缺一张 ⇒ 对应的 E2E 路径直接断）
    SELECT COUNT(*) INTO v_policy FROM t_claim_policy
     WHERE tenant_id = 0
       AND policy_no IN ('E2E-POLICY-0001', 'E2E-POLICY-EXPIRED')
       AND deleted = 0;
    IF v_policy <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0086 校验失败：E2E 保单未全部就位（0001 / EXPIRED）';
    END IF;

    -- ② 两个计划必须都在
    SELECT COUNT(*) INTO v_plan FROM t_claim_plan
     WHERE tenant_id = 0
       AND plan_code IN ('E2E-PLAN-9001', 'E2E-PLAN-9002')
       AND deleted = 0;
    IF v_plan <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0086 校验失败：E2E 计划未全部就位（9001 / 9002）';
    END IF;

    -- ③ 被保人清单必须与 0073 种的额度账户一致（否则报案会被 403 被保人不匹配拦掉，
    --    而额度账户成了没人能用的孤儿数据）
    SELECT COUNT(*) INTO v_policy FROM t_claim_policy p
     WHERE p.tenant_id = 0
       AND p.policy_no = 'E2E-POLICY-0001'
       AND p.insured_snapshot LIKE '%e2e-insured-hash-0001%'
       AND EXISTS (
            SELECT 1 FROM t_quota_account q
             WHERE q.tenant_id = 0
               AND q.policy_no = 'E2E-POLICY-0001'
               AND q.insured_id_hash = 'e2e-insured-hash-0001'
               AND q.deleted = 0
       );
    IF v_policy = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0086 校验失败：保单被保人清单与 0073 额度账户的 insured_id_hash 不一致';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0086();
DROP PROCEDURE IF EXISTS sp_verify_0086;
