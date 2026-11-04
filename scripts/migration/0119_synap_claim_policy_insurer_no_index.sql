-- ============================================================
-- 0119_synap_claim_policy_insurer_no_index.sql
--
-- 一句话：给 t_claim_policy 补 (tenant_id, insurer_policy_no) 组合索引，支撑"按保司保单号收单"的查询。
--
-- 背景
-- ----------------------------------------------------------------
-- 0117 加了 insurer_policy_no 列，0119 之前该列没有任何索引。而收单前置校验现在允许
-- 「只传保司保单号」（保司来单/团险成员卡面拿不到平台号），每次报案都会执行
--   WHERE insurer_policy_no = ? AND config_status = 'ACTIVE' AND stale_flag = 0
--     AND effective_start <= NOW(3) AND effective_end >= NOW(3)
-- 无索引时是全表扫描：保单行数随租户与年度增长，单次报案要多扫一遍全表，
-- 且发生在收单主链路（高并发、有超时预算）上。
--
-- 为什么是 (tenant_id, insurer_policy_no) 这个列序
-- ----------------------------------------------------------------
-- 租户条件由 SDK 的 /*bone:tenant*/ 注入且**恒存在**，放最左列才能让索引在
-- "同租户内按保司号查"这一真实形态上生效；反过来 (insurer_policy_no, tenant_id)
-- 在跨租户同号（不同保司复用同一号段）时会把索引退化成范围扫描。
-- 不把 config_status/日期列并进索引：它们是低基数/范围谓词，收益有限而索引体积与
-- 写入成本上升；保单表是读多写少的配置面表，两列组合已足够把扫描收敛到个位数行。
--
-- 为什么不加唯一约束
-- ----------------------------------------------------------------
-- 保司号在多保司/团险场景下**本来就不唯一**（一张团险保单可按成员/计划拆成多张平台
-- 保单，续保也可能沿用同号）。加唯一键会让合法的多保单结构无法落库；收单侧的多命中
-- 由应用层按被保人收敛并 fail-closed（SC_CLAIM_POLICY_AMBIGUOUS），不靠数据库兜。
--
-- 幂等
-- ----------------------------------------------------------------
-- MySQL 的 ADD INDEX 无 IF NOT EXISTS，information_schema.statistics 判定 + 动态 SQL；
-- 末尾断言索引存在且列序为 (tenant_id, insurer_policy_no)（列序错了等于没建对索引）。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、建索引（幂等）
-- ─────────────────────────────────────────────────────────────
SET @ddl_0119_1 := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_claim_policy'
        AND index_name = 'idx_tenant_insurer_policy_no') = 0,
    'ALTER TABLE `t_claim_policy` ADD INDEX `idx_tenant_insurer_policy_no` (`tenant_id`, `insurer_policy_no`)',
    'DO 0');
PREPARE s1 FROM @ddl_0119_1; EXECUTE s1; DEALLOCATE PREPARE s1;

-- ─────────────────────────────────────────────────────────────
-- 二、终态断言（索引必须在，且列序必须是 tenant_id → insurer_policy_no）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_verify_0119$$
CREATE PROCEDURE sp_verify_0119()
BEGIN
    DECLARE v_cols VARCHAR(128) DEFAULT NULL;

    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
      INTO v_cols
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_policy'
       AND index_name = 'idx_tenant_insurer_policy_no';

    IF v_cols IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0119 校验失败：idx_tenant_insurer_policy_no 索引缺失';
    END IF;

    IF v_cols <> 'tenant_id,insurer_policy_no' THEN
        SET @msg_0119 := CONCAT(
            '0119 fail: index col order is ', v_cols,
            ', expected tenant_id,insurer_policy_no');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg_0119;
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0119();
DROP PROCEDURE IF EXISTS sp_verify_0119;
