-- ============================================================
-- 0073_seed_claim_reference_data.sql
--
-- 背景（2026-10-28/29 全功能实测确诊）
-- --------------------------------
-- dev 联调保单（E2E-POLICY-0001 / planId 9001，DevPolicySnapshotSeeder 内存桩）只解决
-- 了「报案前置校验」一跳；往下的两跳各缺一块引用数据，链路即断：
--
--   1. 理算（A6）前置「计划下有生效责任」——t_adj_liability 全库为空（tenant 1001 的
--      存量额度账户引用的 liability_uuid 在责任表里也找不到对应行），执行理算必 422
--      SC_CLAIM_LIABILITY_NOT_FOUND；
--   2. 资金占额（A7）/ 额度查询（A5）按 (insured_id_hash, policy_no, plan_id,
--      quota_year=当年, quota_type='PERSONAL', liability_uuid='') 定位账户
--      （QuotaAccountRepository/findAccount.sql），tenant 0 无账户即 404
--      SC_CLAIM_QUOTA_NOT_FOUND。
--
-- 一期无责任/额度的管理端（无 Controller 写入口），引用数据走 SQL 种子是与
-- 0037/0058 同款的合法路径；替换为真实承保/精算系统后由其同步。
--
-- 口径
-- --------------------------------
-- · 责任：plan 9001 / R01 门诊报销 / 90% / 免赔 0 / 上限 10 万 / 仅社保内 / 无等待期；
-- · 额度：被保人 e2e-insured-hash-0001（与 dev 保单桩的 insuredIdHash 一致），
--   PERSONAL / ANNUAL?否——类型口径以查询为准 quota_type='PERSONAL'（findAccount.sql 实参），
--   年度 = 当年（动态计算，跨年重跑自动补当年账户）；
-- · tenant 0（admin 所在平台租户）：UI 探针以 admin 登录，案件全部落在 tenant 0，
--   种子必须同租户，否则「数据在、看不见」。
--
-- 幂等
-- --------------------------------
-- 按 (tenant_id, plan_id, liability_uuid) / (tenant_id, insured_id_hash, policy_no,
-- plan_id, quota_year) 判存在，存在即跳过；末尾 SIGNAL 校验两者齐全。
-- ============================================================

-- ---------- 1. plan 9001 生效责任 ----------
INSERT INTO t_adj_liability
  (id, liability_uuid, liability_version, plan_id, liability_code, liability_type,
   pay_percent, liability_deduct, liability_limit, social_only, waiting_period, status,
   tenant_id, created_by, created_at, updated_by, updated_at, deleted)
SELECT 9151001, 'LIAB-E2E-9001-0001', 'v1', 9001, 'R01', 'REIMBURSEMENT',
       90.00, 0.00, 100000.00, 0, 0, 'ACTIVE',
       0, 1, NOW(3), 1, NOW(3), 0
WHERE NOT EXISTS (
  SELECT 1 FROM t_adj_liability
   WHERE tenant_id = 0 AND plan_id = 9001 AND liability_uuid = 'LIAB-E2E-9001-0001' AND deleted = 0
);

-- ---------- 2. 被保人额度账户（当年；跨年重跑自动补新年度） ----------
INSERT INTO t_quota_account
  (id, insured_id_hash, policy_no, plan_id, quota_year, quota_type, liability_uuid,
   quota_initial, quota_claimed, quota_frozen, deductible_used, cap_used, insured_state,
   tenant_id, created_by, created_at, updated_by, updated_at, version, deleted)
SELECT 9151002, 'e2e-insured-hash-0001', 'E2E-POLICY-0001', 9001, YEAR(CURDATE()), 'PERSONAL', '',
       100000.00, 0.00, 0.00, 0.00, 0.00, 1,
       0, 1, NOW(3), 1, NOW(3), 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM t_quota_account
   WHERE tenant_id = 0 AND insured_id_hash = 'e2e-insured-hash-0001'
     AND policy_no = 'E2E-POLICY-0001' AND plan_id = 9001
     AND quota_year = YEAR(CURDATE()) AND quota_type = 'PERSONAL' AND deleted = 0
);

-- ---------- 3. 校验（SIGNAL 防假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0073;
DELIMITER $$
CREATE PROCEDURE sp_verify_0073()
BEGIN
    DECLARE v_liab INT DEFAULT 0;
    DECLARE v_quota INT DEFAULT 0;

    SELECT COUNT(*) INTO v_liab FROM t_adj_liability
     WHERE tenant_id = 0 AND plan_id = 9001 AND status = 'ACTIVE' AND deleted = 0;
    IF v_liab = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0073 校验失败：plan 9001 无生效责任（tenant 0）';
    END IF;

    SELECT COUNT(*) INTO v_quota FROM t_quota_account
     WHERE tenant_id = 0 AND insured_id_hash = 'e2e-insured-hash-0001'
       AND policy_no = 'E2E-POLICY-0001' AND plan_id = 9001
       AND quota_year = YEAR(CURDATE()) AND quota_type = 'PERSONAL' AND deleted = 0;
    IF v_quota = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0073 校验失败：被保人额度账户缺失（tenant 0 / 当年 / PERSONAL）';
    END IF;

    SELECT v_liab AS active_liabilities, v_quota AS quota_accounts;
END$$
DELIMITER ;

CALL sp_verify_0073();
DROP PROCEDURE IF EXISTS sp_verify_0073;
