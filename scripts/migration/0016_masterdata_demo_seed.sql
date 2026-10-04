-- ============================================================
-- 0016_masterdata_demo_seed.sql
-- 主数据演示种子：给「客户 CUSTOMER」实体补齐字段与多级分类树
--
-- 背景（2026-10-02 主数据分类管理实测）：
--   分类管理页（前端已收口为「下拉选模型 + 自动加载」）在开发库里始终是空树，原因不在前端：
--   bone-init.sql 里 mdm_* 五张表（mdm_entity / mdm_field / mdm_category / mdm_record /
--   mdm_record_category）一条 INSERT 都没有——全仓种子只覆盖 iam_* / sys_* / meta_* / gen_*。
--   新建库跑完 init.sql 后「实体 0 个」，前端连下拉都是空的，功能不可达。
--
--   而开发库里其实**已经有** CUSTOMER 实体（雪花 ID，由订单/支付蓝图 E2E 链路创建，
--   带 3 个字段 customer_code/customer_name/level_code 与 17 条记录），唯独没有分类。
--   本脚本因此不造新实体，而是**就地补齐**：实体已存在则只补字段与分类，缺实体才创建。
--
-- 关键设计：实体 id 一律用子查询按 entity_code 解析，绝不硬编码。
--   第一版曾把 id 写死 910000000000000101 + 阶段1 用 NOT EXISTS 判存在——两个条件自相矛盾：
--   开发库里 CUSTOMER 已存在 → 阶段 1 正确跳过 → 阶段 2/3/4 全指向那个不存在的
--   910000000000000101 → 字段/分类/记录静默插 0 条，只有阶段 5 的校验能看出来。
--   「按业务码定位」与「按主键定位」必须用同一个来源，否则幂等逻辑会自我抵消。
--
-- 策略（全程幂等，可重复执行）：
--   阶段 0：备份（IF NOT EXISTS，绝不先 DROP，见 0001 教训）。
--   阶段 1：实体。按 entity_code 判存在性；已存在则不重复插入、不覆盖用户改动。
--   阶段 2：字段。id 用号段自增，master_data_entity_id 走子查询；按 (实体, field_code) 判存在。
--   阶段 3：分类。含两级树（战略客户 > 华东/华南），父 id 走子查询，不硬编码。
--   阶段 4：记录。current_data 的键与阶段 2 的 field code 严格一致。
--   阶段 5：校验（分类数 / 父子关系自检 / 记录数），**校验不通过用 SIGNAL 报错**，
--           避免像第一版那样「插 0 条但整体 EXIT 0」被误判为成功。
--
-- id 号段：910000000000000101~910000000000000199。
--   沿用 0009/0015 的预置号段风格（910000000000000001 起），刻意避开 0~999 与雪花号段，
--   且与 gen_code_template 的 910000000000000001~012 不重叠。
--
-- 依据：
--   bone-init.sql:1557 mdm_entity / :1716 mdm_field / :1632 mdm_category / :1588 mdm_record
--   MasterDataEntity.GovernanceTier（L1 轻量=不开版本控制与审批流，见 MasterDataEntity.java:201）
--   MasterDataField 值类型校验（STRING / NUMBER / BOOLEAN / DATE，见 MasterDataField.java:125）
--
-- 前置：bone-init.sql 已执行（表已建）。本脚本幂等，可重复执行。
-- 权限：仅需 mdm_* 四表的 INSERT / SELECT 与 CREATE TABLE（备份用）；无需 SUPER。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p <bone_db> < 0016_masterdata_demo_seed.sql
-- ============================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_mdm_seed_0016_entity   AS SELECT * FROM mdm_entity;
CREATE TABLE IF NOT EXISTS bak_mdm_seed_0016_field    AS SELECT * FROM mdm_field;
CREATE TABLE IF NOT EXISTS bak_mdm_seed_0016_category AS SELECT * FROM mdm_category;
CREATE TABLE IF NOT EXISTS bak_mdm_seed_0016_record   AS SELECT * FROM mdm_record;

-- ---------- 阶段 1：演示实体（客户 CUSTOMER，L1 轻量治理） ----------
-- 仅当库中完全没有 CUSTOMER 时才创建；已存在（E2E 链路建的）则原样保留。
INSERT INTO mdm_entity
    (id, tenant_id, entity_code, entity_name, description, domain_code,
     governance_tier, is_versioning, workflow_enabled, status, created_by)
SELECT 910000000000000101, 0, 'CUSTOMER', '客户主数据', '演示用客户实体：覆盖分类树 / 字段建模 / 记录管理三条链路', 'CUSTOMER',
       'L1', 0, 0, 'PUBLISHED', 1
WHERE NOT EXISTS (
    SELECT 1 FROM mdm_entity WHERE tenant_id = 0 AND entity_code = 'CUSTOMER'
);

SET @eid := (SELECT id FROM mdm_entity WHERE tenant_id = 0 AND entity_code = 'CUSTOMER' LIMIT 1);

-- ---------- 阶段 2：字段（9 个，覆盖四种合法值类型 + 两个区间型值域） ----------
-- master_data_entity_id 走 @eid（而非硬编码），因此对「已存在的实体」同样生效。
-- 刻意不用 INSERT IGNORE：它会把主键冲突与真实错误一并静默吞掉（本脚本第一版就因此
-- 「插 0 条却整体 EXIT 0」）。这里用 NOT EXISTS 显式判存在，唯一键冲突会直接报错。
INSERT INTO mdm_field
    (id, tenant_id, master_data_entity_id, name, code, type, length, required,
     default_value, description, sort_order, source_type, enabled, min_value, max_value, created_by)
SELECT 910000000000000111 + s.seq, 0, @eid, s.name, s.code, s.type, s.len, s.req,
       s.def_val, s.descr, s.sort_no, 'TENANT', 1, s.min_v, s.max_v, 1
FROM (
              SELECT 0  AS seq, '客户编码'    AS name, 'customer_code'  AS code, 'STRING'  AS type,  32  AS len, 1 AS req, NULL AS def_val, '业务唯一编码' AS descr, 1 AS sort_no, NULL AS min_v, NULL AS max_v
    UNION ALL SELECT 1,  '客户名称',    'customer_name',  'STRING',  128, 1, NULL, '客户全称', 2, NULL, NULL
    UNION ALL SELECT 2,  '客户等级',    'level_code',     'STRING',  16,  1, 'NORMAL', '对应参考数据值域 CREDIT_LEVEL', 3, NULL, NULL
    UNION ALL SELECT 3,  '统一社会信用代码','credit_code', 'STRING',  32,  0, NULL, '开户行/工商登记信息', 4, NULL, NULL
    UNION ALL SELECT 4,  '信用额度',    'credit_limit',   'NUMBER',  NULL, 0, '0',     '区间型值域演示：下限 0，不允许负额度', 5, 0.000000, 10000000.000000
    UNION ALL SELECT 5,  '折扣率',      'discount_rate',  'NUMBER',  NULL, 0, '1',     '区间型值域演示：上限 1，禁止 >1 的折扣', 6, 0.000000, 1.000000
    UNION ALL SELECT 6,  '是否重点客户','is_key',         'BOOLEAN', NULL, 0, 'false', '演示 BOOLEAN 类型', 7, NULL, NULL
    UNION ALL SELECT 7,  '签约日期',    'sign_date',      'DATE',    NULL, 0, NULL,    '演示 DATE 类型', 8, NULL, NULL
    UNION ALL SELECT 8,  '客户状态',    'cust_status',    'STRING',  16,  1, 'ACTIVE','ACTIVE/INACTIVE', 9, NULL, NULL
) s
WHERE @eid IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mdm_field f
      WHERE f.tenant_id = 0 AND f.master_data_entity_id = @eid AND f.code = s.code
  );

-- ---------- 阶段 3：分类树（两级：战略客户 > 华东 / 华南） ----------
-- 父分类 id 用子查询取，不硬编码：父不存在则本级不插入（不会挂到错误父节点下）。
INSERT INTO mdm_category
    (id, tenant_id, mdm_entity_id, name, code, description, parent_category_id, level, sort_order, created_by)
SELECT 910000000000000121, 0, @eid, '战略客户', 'STRATEGIC', '重点维护的战略级客户', NULL, 0, 1, 1
WHERE @eid IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mdm_category c
      WHERE c.tenant_id = 0 AND c.mdm_entity_id = @eid AND c.code = 'STRATEGIC' AND c.deleted = 0
  );

INSERT INTO mdm_category
    (id, tenant_id, mdm_entity_id, name, code, description, parent_category_id, level, sort_order, created_by)
SELECT s.id, 0, @eid, s.name, s.code, s.descr,
       (SELECT c.id FROM mdm_category c
         WHERE c.tenant_id = 0 AND c.mdm_entity_id = @eid AND c.code = 'STRATEGIC' AND c.deleted = 0
         LIMIT 1),
       1, s.sort_no, 1
FROM (
              SELECT 910000000000000122 AS id, '华东战略客户' AS name, 'STRATEGIC_EAST' AS code, '华东区域战略客户' AS descr, 1 AS sort_no
    UNION ALL SELECT 910000000000000123, '华南战略客户', 'STRATEGIC_SOUTH', '华南区域战略客户', 2
) s
WHERE @eid IS NOT NULL
  AND EXISTS (SELECT 1 FROM mdm_category c
               WHERE c.tenant_id = 0 AND c.mdm_entity_id = @eid AND c.code = 'STRATEGIC' AND c.deleted = 0)
  AND NOT EXISTS (
      SELECT 1 FROM mdm_category c
      WHERE c.tenant_id = 0 AND c.mdm_entity_id = @eid AND c.code = s.code AND c.deleted = 0
  );

-- ---------- 阶段 4：记录（current_data 的键与阶段 2 的 field code 严格一致） ----------
INSERT INTO mdm_record
    (id, tenant_id, mdm_entity_id, record_code, display_name, current_data, status, version_number, is_current, created_by)
SELECT 910000000000000131, 0, @eid, 'CUST-DEMO-0001', '上海示例科技有限公司',
       JSON_OBJECT(
           'customer_code', 'CUST-DEMO-0001',
           'customer_name', '上海示例科技有限公司',
           'level_code',    'VIP',
           'credit_code',   '91310000MA1FL0000X',
           'credit_limit',  5000000.00,
           'discount_rate', 0.95,
           'is_key',        true,
           'sign_date',     '2024-03-15',
           'cust_status',   'ACTIVE'
       ),
       'PUBLISHED', 1, 1, 1
WHERE @eid IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mdm_record
      WHERE tenant_id = 0 AND mdm_entity_id = @eid AND record_code = 'CUST-DEMO-0001' AND deleted = 0
  );

INSERT INTO mdm_record
    (id, tenant_id, mdm_entity_id, record_code, display_name, current_data, status, version_number, is_current, created_by)
SELECT 910000000000000132, 0, @eid, 'CUST-DEMO-0002', '深圳示例贸易有限公司',
       JSON_OBJECT(
           'customer_code', 'CUST-DEMO-0002',
           'customer_name', '深圳示例贸易有限公司',
           'level_code',    'NORMAL',
           'credit_code',   '91440300MA5EXAMPLE',
           'credit_limit',  300000.00,
           'discount_rate', 1.00,
           'is_key',        false,
           'sign_date',     '2025-07-01',
           'cust_status',   'ACTIVE'
       ),
       'PUBLISHED', 1, 1, 1
WHERE @eid IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mdm_record
      WHERE tenant_id = 0 AND mdm_entity_id = @eid AND record_code = 'CUST-DEMO-0002' AND deleted = 0
  );

-- ---------- 阶段 5：校验 ----------
-- 用 SIGNAL 让「插 0 条」不再静默通过：第一版的教训就是整体 EXIT 0 却什么都没插进去。
-- 注意：期望值只针对**本脚本的种子集合**（3 条分类 / 9 个字段 code / 2 条种子记录），
-- 不校验实体下的总数——开发库的 CUSTOMER 可能已有 E2E 链路造的字段与记录。
SET @cat_cnt := (SELECT COUNT(*) FROM mdm_category
                 WHERE mdm_entity_id = @eid AND deleted = 0
                   AND code IN ('STRATEGIC', 'STRATEGIC_EAST', 'STRATEGIC_SOUTH'));
SET @fld_cnt := (SELECT COUNT(*) FROM mdm_field
                 WHERE master_data_entity_id = @eid AND deleted = 0
                   AND code IN ('customer_code', 'customer_name', 'level_code', 'credit_code',
                                'credit_limit', 'discount_rate', 'is_key', 'sign_date', 'cust_status'));
SET @rec_cnt := (SELECT COUNT(*) FROM mdm_record
                 WHERE mdm_entity_id = @eid AND deleted = 0
                   AND record_code IN ('CUST-DEMO-0001', 'CUST-DEMO-0002'));

SELECT 'entity'   AS obj, @eid     AS actual, 1 AS expected
UNION ALL SELECT 'field',    @fld_cnt, 9
UNION ALL SELECT 'category', @cat_cnt, 3
UNION ALL SELECT 'record',   @rec_cnt, 2;

-- 分类树父子关系自检：level=1 的两条其 parent 必须指向 level=0 的 STRATEGIC
SELECT c.code AS child, c.level, p.code AS parent, p.level AS parent_level
FROM mdm_category c
LEFT JOIN mdm_category p ON p.id = c.parent_category_id
WHERE c.mdm_entity_id = @eid AND c.deleted = 0
ORDER BY c.level, c.sort_order;

DROP PROCEDURE IF EXISTS chk_mdm_seed_0016;
DELIMITER $$
CREATE PROCEDURE chk_mdm_seed_0016()
BEGIN
    DECLARE v_eid BIGINT;
    DECLARE v_cat INT DEFAULT 0;
    DECLARE v_fld INT DEFAULT 0;
    DECLARE v_rec INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    SELECT id INTO v_eid FROM mdm_entity WHERE tenant_id = 0 AND entity_code = 'CUSTOMER' LIMIT 1;
    IF v_eid IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '0016 校验失败：未找到 CUSTOMER 实体';
    END IF;
    SELECT COUNT(*) INTO v_cat FROM mdm_category
    WHERE mdm_entity_id = v_eid AND deleted = 0
      AND code IN ('STRATEGIC', 'STRATEGIC_EAST', 'STRATEGIC_SOUTH');
    IF v_cat <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '0016 校验失败：种子分类应为 3 条，检查阶段 3';
    END IF;
    SELECT COUNT(*) INTO v_fld FROM mdm_field
    WHERE master_data_entity_id = v_eid AND deleted = 0
      AND code IN ('customer_code', 'customer_name', 'level_code', 'credit_code',
                   'credit_limit', 'discount_rate', 'is_key', 'sign_date', 'cust_status');
    IF v_fld <> 9 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '0016 校验失败：种子字段应为 9 个，检查阶段 2';
    END IF;
    SELECT COUNT(*) INTO v_rec FROM mdm_record
    WHERE mdm_entity_id = v_eid AND deleted = 0
      AND record_code IN ('CUST-DEMO-0001', 'CUST-DEMO-0002');
    IF v_rec <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '0016 校验失败：种子记录应为 2 条，检查阶段 4';
    END IF;
    -- 孤儿分类：level>0 但父节点不存在
    SELECT COUNT(*) INTO v_orphan
    FROM mdm_category c
    LEFT JOIN mdm_category p ON p.id = c.parent_category_id
    WHERE c.mdm_entity_id = v_eid AND c.deleted = 0 AND c.level > 0 AND p.id IS NULL;
    IF v_orphan > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '0016 校验失败：存在父节点缺失的孤儿分类';
    END IF;
END$$
DELIMITER ;
CALL chk_mdm_seed_0016();
DROP PROCEDURE IF EXISTS chk_mdm_seed_0016;
