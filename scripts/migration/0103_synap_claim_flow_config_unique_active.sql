-- ============================================================
-- 0103_synap_claim_flow_config_unique_active.sql
--
-- 一句话：给 t_claim_flow_config 补「同租户同 flow_key 至多一条生效版本」的
--        数据库级约束（生成列 active_key + 唯一键 uk_tenant_flow_active），
--        并把「多生效行」这个歧义从「靠人记得」变成「库层不可能发生」。
--
-- 背景（缺陷实证，非推断）
-- ----------------------------------------------------------------
-- 0099 建 t_claim_flow_config 时，唯一的唯一键是
--     uk_tenant_flow_version (tenant_id, flow_key, config_version)
-- ——它约束的是**版本**，不是**生效行**。同一 (tenant_id, flow_key) 因此可以合法地
-- 存在多行：历史版本 active_flag=0，当前版本 active_flag=1。0099 只给了普通索引
--     KEY idx_flow (tenant_id, flow_key, active_flag)
-- 普通索引不阻止重复，于是「同一个 flow_key 有两条 active_flag=1」在库层面完全合法。
--
-- 后果不是抽象的：
--   · 语义上「哪条生效」无定义。契约 §6.7 说「生命周期引擎按 flow_key 读取」，
--     但没说读到哪条——两行 active 时「当前配置」是个无解的歧义。
--   · 工程上，原仓储 ClaimFlowConfigRepository.findByFlowKey 用 findOneByCriteria
--     只按 (tenant_id, flow_key) 过滤，**不过滤 active_flag**：命中多行时 SDK 直接抛
--     MultipleResultsException("Expected single result, found: N")。该报错完全看不出
--     是「同 flow_key 有多个版本」造成的，排查成本极高。
--   · 该缺陷在**接线那一刻**才暴露（当前无消费方、活库该表为空），且届时表里
--     往往已经有真实的历史版本数据——不是空表，是"恰好有 2 行"的那种。
--
-- 为什么用生成列，而不是更直觉的写法
-- ----------------------------------------------------------------
-- 直觉写法是「唯一键加上 active_flag」：
--     UNIQUE KEY (tenant_id, flow_key, active_flag)
-- 这是**错的**：它会要求同一个 flow_key 下**所有历史版本（active_flag=0）也只能有一行**，
-- 而版本化配置的本质就是多行历史。改 active_flag=1 激活新版本时，
-- 必须先把旧版本改成别的 active_flag 值才能腾出唯一键位——等于用「把历史版本标成
-- 非历史」来表达「版本推进」，语义被污染。
--
-- MySQL 没有 partial unique index（PostgreSQL 的
--     CREATE UNIQUE INDEX ... WHERE active_flag = 1
-- 才天然支持），业界在 MySQL 上的标准解法是**生成列 + 唯一键**：
--     active_key VARCHAR(64) GENERATED ALWAYS AS
--         (IF(active_flag = 1 AND deleted = 0, flow_key, NULL)) VIRTUAL,
--     UNIQUE KEY uk_tenant_flow_active (tenant_id, active_key)
-- 关键在那个 NULL：MySQL 的唯一索引**不把 NULL 视为重复**，于是
--   · 生效行（active_key = flow_key）⇒ 同 (tenant_id, flow_key) 只能有一条；
--   · 历史行（active_key = NULL）⇒ 可以有任意多条，不受约束。
-- 同时把 deleted 纳入条件：软删的行（deleted=1）不占生效名额——否则「软删旧生效版本
-- 后再激活新版本」会被自己的历史行挡住，而这正是版本推进的常规动作。
--
-- 为什么生成列不进实体
-- ----------------------------------------------------------------
-- SDK 的 SELECT/INSERT 列清单都由**实体元数据**生成（SqlBuilder 取 TableMetadata，
-- 不是 SELECT *），所以生成列既不会被 SELECT 出来、也不会被 INSERT 进去：
--   · 不会被 SELECT ⇒ 实体无该字段也不会错位；
--   · 不会被 INSERT ⇒ 不会触发 MySQL 的 "The value specified for generated column
--     in DEFAULT clause is not allowed"（1067）。
-- 即：表层用生成列表达约束，实体层完全无感。
--
-- 存量脏数据处置
-- ----------------------------------------------------------------
-- 加唯一键前必须先探测：若库里已有同 (tenant_id, flow_key) 的多 active 行，
-- 直接加唯一键会失败（ERROR 1062）。本脚本**不自动删数据**——删哪条是业务决定
-- （哪一版才是真正的当前版本，代码无法推断）。而是：
--   · 探测到脏数据 ⇒ SIGNAL 中止，并打印出具体的 (tenant_id, flow_key, id,
--     config_version) 清单，由 DBA/业务方决定保留哪条、把其余的 active_flag 置 0；
--   · 无脏数据 ⇒ 正常加约束。
-- 这样「加约束」不会顺手毁掉数据，且失败信息直接可执行。
--
-- 幂等 / 执行
-- ----------------------------------------------------------------
--   · 幂等：information_schema 探测生成列与唯一键是否存在，缺才加（MySQL 无
--     ADD COLUMN IF NOT EXISTS，用 0085/0090/0094/0095/0097/0098 同款动态 DDL 范式）；
--   · 终态断言：生成列与唯一键都在位，且存量的「多 active 组数 = 0」才放行；
--   · bone-init.sql 同提交补生成列与唯一键（G39 只比表级终态，列与索引是盲区）。
--
-- 执行（生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0103_synap_claim_flow_config_unique_active.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- ① 加约束前先探测存量脏数据：同 (tenant_id, flow_key) 有多条未删除的 active 行
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_probe_0103;
DELIMITER $$
CREATE PROCEDURE sp_probe_0103()
BEGIN
    DECLARE v_dup INT DEFAULT 0;

    SELECT COUNT(*) INTO v_dup
      FROM (
        SELECT tenant_id, flow_key
          FROM t_claim_flow_config
         WHERE deleted = 0 AND active_flag = 1
         GROUP BY tenant_id, flow_key
        HAVING COUNT(*) > 1
      ) d;
    IF v_dup > 0 THEN
        -- 不自动删数据：保留哪一版是业务决定（代码无法推断哪个 config_version 才是当前版本）。
        -- 把清单**打到 stdout**（完整、可复制），而 MESSAGE_TEXT 只放简短指引——
        -- SIGNAL 的 MESSAGE_TEXT 有 128 字符上限（实测超长报 ERROR 1648
        -- "Data too long for condition item 'MESSAGE_TEXT'"），把完整清单塞进去会让
        -- 报错变成一句被截断的废话，反而更难排查。
        SELECT CONCAT('  · tenant_id=', tenant_id, ', flow_key=', flow_key,
                      ', ids=', ids, ', config_versions=', versions,
                      '  ⇒ 保留正确的一行置 active_flag=1，其余置 0')
          FROM (
            SELECT tenant_id, flow_key,
                   GROUP_CONCAT(id ORDER BY id) AS ids,
                   GROUP_CONCAT(config_version ORDER BY config_version) AS versions
              FROM t_claim_flow_config
             WHERE deleted = 0 AND active_flag = 1
             GROUP BY tenant_id, flow_key
            HAVING COUNT(*) > 1
           ) x
          ORDER BY tenant_id, flow_key;
        SET @probe_msg := CONCAT(
            '0103 中止：t_claim_flow_config 有 ', v_dup,
            ' 组同 flow_key 的多条生效行。加唯一键会失败(1062)，且哪一版才生效属业务决定，'
            '脚本不删数据。处置清单见上方输出。');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @probe_msg;
    END IF;
END$$
DELIMITER ;

CALL sp_probe_0103();
DROP PROCEDURE IF EXISTS sp_probe_0103;

-- ─────────────────────────────────────────────────────────────
-- ② 加生成列 active_key（IF(active_flag=1 AND deleted=0, flow_key, NULL)）
--    VIRTUAL：不落盘，索引由 InnoDB 自行维护；实体层不声明，SDK 的
--    SELECT/INSERT 列清单都取自实体元数据，故既不会被读出也不会被写入。
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_flow_config ADD COLUMN active_key VARCHAR(64) GENERATED ALWAYS AS (IF(active_flag = 1 AND deleted = 0, flow_key, NULL)) VIRTUAL COMMENT ''生效版本唯一键载体(active_flag=1 且未软删时等于 flow_key, 否则 NULL；MySQL 唯一索引不视 NULL 为重复)''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_flow_config'
    AND COLUMN_NAME = 'active_key'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ③ 加唯一键 uk_tenant_flow_active (tenant_id, active_key)
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_flow_config ADD UNIQUE KEY uk_tenant_flow_active (tenant_id, active_key)',
    'DO 0'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_flow_config'
    AND INDEX_NAME = 'uk_tenant_flow_active'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ④ 终态断言：生成列 + 唯一键在位，且存量「多 active 组数 = 0」
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0103;
DELIMITER $$
CREATE PROCEDURE sp_verify_0103()
BEGIN
    DECLARE v_missing INT DEFAULT 0;
    DECLARE v_dup INT DEFAULT 0;

    -- ① 生成列必须存在，且表达式正确（表达式写错 ⇒ 约束形同虚设）
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_claim_flow_config'
       AND COLUMN_NAME = 'active_key'
       AND EXTRA LIKE '%GENERATED%'
       AND GENERATION_EXPRESSION LIKE '%active_flag%'
       AND GENERATION_EXPRESSION LIKE '%flow_key%';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0103 校验失败：生成列 active_key 未就位或表达式不含 active_flag/flow_key（约束形同虚设）';
    END IF;

    -- ② 唯一键必须存在且列序为 (tenant_id, active_key)
    SELECT COUNT(*) INTO v_missing
      FROM (
        SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 't_claim_flow_config'
           AND INDEX_NAME = 'uk_tenant_flow_active'
         GROUP BY INDEX_NAME
       ) i
     WHERE i.cols = 'tenant_id,active_key';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0103 校验失败：唯一键 uk_tenant_flow_active 未就位或列序不为 (tenant_id, active_key)';
    END IF;

    -- ③ 存量不得有多 active 组（约束只能防新增，已有脏行仍需人工处置）
    SELECT COUNT(*) INTO v_dup
      FROM (
        SELECT tenant_id, flow_key
          FROM t_claim_flow_config
         WHERE deleted = 0 AND active_flag = 1
         GROUP BY tenant_id, flow_key
        HAVING COUNT(*) > 1
      ) d;
    IF v_dup > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0103 校验失败：t_claim_flow_config 仍存在同 flow_key 的多条生效行（语义歧义未消除）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0103();
DROP PROCEDURE IF EXISTS sp_verify_0103;

-- ─────────────────────────────────────────────────────────────
-- 五、验证（手工执行，非脚本自动断言）
--
-- ① 生效唯一性探针（第二条 active 必须被拒，ERROR 1062）：
--   INSERT INTO t_claim_flow_config
--     (id, flow_key, config_version, active_flag, tenant_id, created_at, updated_at, version, deleted)
--   VALUES (999999201,'PROBE_FLOW',1,1,0,NOW(3),NOW(3),0,0);
--   INSERT INTO t_claim_flow_config
--     (id, flow_key, config_version, active_flag, tenant_id, created_at, updated_at, version, deleted)
--   VALUES (999999202,'PROBE_FLOW',2,1,0,NOW(3),NOW(3),0,0);   -- ← 必须报 ERROR 1062
--
-- ② 历史版本可共存（active_flag=0 的多行必须被允许）：
--   INSERT INTO t_claim_flow_config
--     (id, flow_key, config_version, active_flag, tenant_id, created_at, updated_at, version, deleted)
--   VALUES (999999203,'PROBE_FLOW2',1,0,0,NOW(3),NOW(3),0,0);
--   INSERT INTO t_claim_flow_config
--     (id, flow_key, config_version, active_flag, tenant_id, created_at, updated_at, version, deleted)
--   VALUES (999999204,'PROBE_FLOW2',2,0,0,NOW(3),NOW(3),0,0);   -- ← 必须成功
--
-- ③ 清理：
--   DELETE FROM t_claim_flow_config WHERE id IN (999999201,999999202,999999203,999999204);
-- ============================================================
