-- ============================================================
-- 0085_synap_claim_policy_plan_quota_config.sql
--
-- 一句话：把「保单 / 计划 / 险种 / 额度配置」从「只在参考实现（pk-tpa-saas）里有」补到
--       synap-claim 真正可配置、可管理、可上屏。
--
-- 背景（为什么必须写这一条，而不是"照设计文档 §9.3 建表"）
-- ----------------------------------------------------------------
-- 参考实现 pk-tpa-saas/tpa-sass-vue3 有三块完整的运营面：
--   ① 保单管理   —— 保单列表 + 保单规则配置页（启用/停用保单责任）
--   ② 计划配置   —— 计划层 / 险种层 / 责任层三层树 + 逐层增删改 + 发布
--   ③ 额度管理   —— 个人额度列表（初始/已用/剩余/冻结/可用）+ 保全记录
-- 而 synap-claim 侧（2026-10-31 实测）：
--   · 保单**无表**：全仓只有 t_claim_case.policy_no / t_quota_account.policy_no 两个
--     裸字符串列（无主数据），PolicyQueryPort 是进程内存 ConcurrentHashMap 桩，
--     且只有 @Profile("dev") 的 seeder 灌两条种子 —— 非 dev 环境查任何保单都返回空，
--     报案前置校验（SC_CLAIM_POLICY_NOT_ACTIVE）在生产**必然拒收**。
--   · 计划**无表**：t_claim_case.plan_id / t_quota_account.plan_id / t_adj_liability.plan_id
--     三个裸 Long 列，无 FK、无实体、无仓储。
--   · 险种**无表**：t_adj_liability.liability_type 是"责任形式"枚举，不是"险种"。
--   · 额度账户**只有读没有写**：QuotaAccount.open(...) 生产零调用，
--     QuotaController 只有 GET，没有开户/调额/减人入口，也没有保全记录。
-- 也就是说：参考实现里运营能自助完成的三件事，在本仓一件都做不了。
--
-- 设计文档 §9.3 早已定义 adj_policy / adj_plan / adj_coverage 三张表
-- （doc/ai-product/synapClaim/03-contract/工程详细设计-落地版.md），
-- 但**从未落地任何一行代码**（全仓 grep 零命中）。本次按该文档口径落地，
-- 并按本仓既有命名规范加 t_ 前缀（与 t_adj_liability / t_claim_case 同族）。
--
-- ─────────────────────────────────────────────────────────────
-- 【本次同时修一个 P0 潜伏缺陷】—— 它不是"顺手"，是本次功能的必要前提
-- ─────────────────────────────────────────────────────────────
-- t_adj_liability 实体声明了 9 列、t_quota_transaction 实体声明了 biz_date，
-- 但这些列**在任何环境都不存在**：
--   · 它们的 DDL 只在 bone-apps/synap-claim/src/main/resources/db/migration/
--     的 V12/V13/V14/V15 里 —— 那是**从未被执行的 Flyway 孤儿通道**
--     （模块无 flyway-core 依赖、无 spring.flyway.* 配置、不在迁移清单），
--     0079_synap_claim_occurrence_dimensions.sql 的头部已把这条通道的真相写明；
--   · scripts/migration/（本仓唯一生效通道）与 bone-init.sql 均**无**这些列。
--
-- 后果（实测，2026-10-31）：
--   SDK 的 INSERT/UPDATE 列清单由**实体元数据**生成
--   （UpsertBuilder.buildMySql → ctx.getTable().getColumns()），
--   不是由 DB 实际列生成。于是任何写这两张表的代码都会：
--       ERROR 1054 (42S22): Unknown column 'invoice_deduct' in 'field list'
--   实测已复现（见下方 §验证）。读侧因为 findActiveByPlanId.sql /
--   findTransactions.sql 是**手写列清单** SQL，恰好绕开了缺列 ⇒
--   **读能过、写必炸**：这正是"跑过了≠做到了"的典型形态 ——
--   迁移只写在孤儿通道里，跑测试（用内存仓储）全绿，落到真库才炸。
--   而"计划配置"这个功能本身就是要写 t_adj_liability（发布责任版本），
--   不补列则该功能一行都跑不起来。
--
-- 本脚本把这些列按 V12-V15 原稿的列定义逐字补进**生效通道**，
-- 并对每列做 information_schema 断言（缺一列即 SIGNAL 失败），
-- 杜绝"脚本跑过了但列没建好"。
--
-- 幂等
-- ----------------------------------------------------------------
-- 逐列 information_schema 探测 + PREPARE 动态 DDL（与 0072/0079 同范式），
-- 可重复执行。建表用 CREATE TABLE IF NOT EXISTS，但**同名异形存量表会静默
-- no-op**，故每张新表在建完后都断言关键列齐全。
--
-- 与 bone-init.sql 的关系（同提交必须一起改，否则新环境仍然不等价）
-- ----------------------------------------------------------------
-- G39 门禁只比**表**级终态，不看列 ⇒ 列缺口是它的盲区（本次缺口就是这样产生的）。
-- 故本脚本与 bone-init.sql 的表定义同提交补齐，使"只灌 init 的新环境"与
-- "已迁移环境"等价。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0085_synap_claim_policy_plan_quota_config.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- 一、补齐 t_adj_liability 缺失列（V12/V13/V14/V15 原稿口径逐字沿用）
-- ─────────────────────────────────────────────────────────────

-- ① invoice_deduct：发票级免赔（GAP②，比例前，对各费用池基数按占比分摊一次绝对额扣减）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN invoice_deduct DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''发票级免赔(GAP②，比例前): 0=不启用'' AFTER liability_limit',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'invoice_deduct');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② adjust_deduct：理算级免赔（GAP⑥，比例后、控额前，对理算金额再做一次绝对额扣减）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN adjust_deduct DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''理算级免赔(GAP⑥，比例后): 0=不启用'' AFTER invoice_deduct',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'adjust_deduct');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ③ daily_limit：日赔付限额（Q-05，同一业务日累计上限；0 = 不启用）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN daily_limit DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''日赔付限额(Q-05): 同一业务日累计上限; 0=不启用'' AFTER adjust_deduct',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'daily_limit');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ④ daily_limit_basis：日窗口口径（NONE / SAME_DAY_INVOICE / SAME_DAY_CLAIM）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN daily_limit_basis VARCHAR(24) NOT NULL DEFAULT ''NONE'' COMMENT ''日窗口口径(Q-05): NONE/SAME_DAY_INVOICE/SAME_DAY_CLAIM'' AFTER daily_limit',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'daily_limit_basis');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑤ limit_mode：适用限定方式（NONE / WHITELIST / BLACKLIST）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN limit_mode VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''适用限定方式: NONE/WHITELIST/BLACKLIST'' AFTER daily_limit_basis',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'limit_mode');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑥⑦ limit_hospital_codes / limit_diagnosis_codes：限定名单（分隔串）
--   名单用分隔串而非关联表：它是责任配置的一部分，须随责任版本一起冻结；
--   立关联表会让名单在版本冻结后被单独改动，破坏"已生效版本不可改"。
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN limit_hospital_codes VARCHAR(1024) NOT NULL DEFAULT '''' COMMENT ''适用限定-医院编码名单(分隔串): 空=不限定'' AFTER limit_mode',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'limit_hospital_codes');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN limit_diagnosis_codes VARCHAR(512) NOT NULL DEFAULT '''' COMMENT ''适用限定-病种编码名单(分隔串): 空=不限定'' AFTER limit_hospital_codes',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'limit_diagnosis_codes');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑧⑨ catalog_policy / catalog_code：目录准入（NONE 不查目录 / MANDATORY 每条明细必须有目录结论）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN catalog_policy VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''目录准入: NONE(不查目录)/MANDATORY(每条明细须有结论)'' AFTER limit_diagnosis_codes',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'catalog_policy');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN catalog_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''目录编码(如 NATIONAL_2024); catalog_policy=NONE 时须为空'' AFTER catalog_policy',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'catalog_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑩ t_adj_liability.coverage_id：责任挂到险种下（参考实现的三层树需要）
--   NULL = 未归类（历史责任）；非空时必须指向 t_adj_coverage 存在的行。
--   不加外键约束：跨聚合外键会让"删险种"变成不可逆操作，而责任版本本身
--   已随版本冻结（历史责任引用的险种可能已下线）——用应用层校验代替。
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN coverage_id BIGINT DEFAULT NULL COMMENT ''所属险种ID(NULL=未归类)'' AFTER plan_id',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'coverage_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑪ t_adj_liability.version：乐观锁（SDK @Version 需要；无此列则并发写退化为后写覆盖）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_adj_liability ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本'' AFTER deleted',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_adj_liability' AND column_name = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑫ t_quota_transaction.biz_date：业务日（日限额按此累计；NULL = 不参与日累计）
--   缺此列 ⇒ 任何写额度流水的代码报 Unknown column 'biz_date'。
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_quota_transaction ADD COLUMN biz_date DATE DEFAULT NULL COMMENT ''业务日(发票日期/赔案受理日): 日限额按此累计; NULL=不参与日累计'' AFTER occurred_at',
  'DO 0') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_quota_transaction' AND column_name = 'biz_date');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑬ t_quota_transaction.txn_type 注释/长度对齐（存量列被 MODIFY 是幂等的）
--   口径补齐 DEDUCTIBLE / DAILY（原先注释只列 SOFT/HARD/PAID/RELEASE，
--   读者会以为 DAILY 不是合法值）。长度放宽到 16 以容纳 DEDUCTIBLE(10) 与 DAILY(5)。
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'DO 0',
  'ALTER TABLE t_quota_transaction MODIFY COLUMN txn_type VARCHAR(16) NOT NULL COMMENT ''SOFT/HARD/PAID/RELEASE/REFUND/DEDUCTIBLE/DAILY''') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_quota_transaction' AND column_name = 'txn_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- 二、新建表：保单 / 计划 / 险种
-- ─────────────────────────────────────────────────────────────

-- ② 保障计划 t_claim_plan
--   一个保单可挂多个计划（参考实现 PlanDTO 带 policyNo 字段，一单一计划多份）；
--   计划是理算的**版本化配置单元**：责任挂在计划下，计划改则责任版本跟着改。
--   UK 用 (tenant_id, policy_no, plan_code)：同一保单下计划编码唯一，
--   不同保单可用同一计划编码（计划编码是保司侧的产品编码，跨保单可复用）。
CREATE TABLE IF NOT EXISTS t_claim_plan (
  id              BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  policy_no       VARCHAR(32)   NOT NULL COMMENT '所属保单号',
  plan_code       VARCHAR(32)   NOT NULL COMMENT '计划编码(保司侧产品编码)',
  plan_name       VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '计划名称',
  plan_limit      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '计划额度上限(0=不限)',
  waiting_period  INT           NOT NULL DEFAULT 0 COMMENT '等待期(天)',
  version_no      VARCHAR(32)   NOT NULL DEFAULT 'v1' COMMENT '计划版本(配置变更即新增)',
  status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/ACTIVE已发布/RETIRED已下线',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by      BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by      BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '修改时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_plan_code (tenant_id, policy_no, plan_code),
  KEY idx_tenant_policy (tenant_id, policy_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保障计划(保单下可多份, 版本化)';

-- ③ 保障项/险种 t_claim_coverage
--   参考实现的三层树：计划层 → 险种层 → 责任层。
--   险种挂在计划下；责任通过 t_adj_liability.coverage_id 指向它。
CREATE TABLE IF NOT EXISTS t_claim_coverage (
  id              BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  plan_id         BIGINT        NOT NULL COMMENT '所属计划ID',
  coverage_code   VARCHAR(32)   NOT NULL COMMENT '险种编码',
  coverage_name   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '险种名称',
  coverage_limit  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '险种额度上限(0=不限)',
  liability_type  VARCHAR(32)   NOT NULL DEFAULT 'REIMBURSEMENT' COMMENT '责任形式: REIMBURSEMENT/FIXED_AMOUNT/ALLOWANCE',
  status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/RETIRED',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by      BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by      BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '修改时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_plan_coverage (tenant_id, plan_id, coverage_code),
  KEY idx_tenant_plan (tenant_id, plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保障项/险种(计划下)';

-- ① 保单 t_claim_policy
--   保单是**被保人身份 + 承保期间 + 规则开关**的聚合根。
--   policy_no 是对外业务号（报案时前端可下拉选择，不再手打）。
--   insured_snapshot 存被保人清单 JSON（假名化ID列表）——与 t_quota_account
--   的 insured_id_hash 口径一致：库里**不存明文证件**，只存服务端 HMAC 假名化值。
--   config_status 对应参考实现的"启用/停用保单责任"三态：
--   INACTIVE 未启用 / ACTIVE 启用中 / PAUSE 停用中。
CREATE TABLE IF NOT EXISTS t_claim_policy (
  id                  BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  policy_no           VARCHAR(32)   NOT NULL COMMENT '保单号(对外业务号)',
  policy_name         VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '保单名称',
  insurer_name        VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '保险公司',
  branch_name         VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '保险分公司',
  insure_name         VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '投保公司(企业客户)',
  biz_identity_code   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '业务主体码(保司/分公司/投保公司/保单四级匹配,集成路由依据)',
  product_type        VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '产品类型(门诊等)',
  effective_start     DATETIME(3)   NOT NULL COMMENT '生效起',
  effective_end       DATETIME(3)   NOT NULL COMMENT '生效止',
  waiting_period      INT           NOT NULL DEFAULT 0 COMMENT '等待期(天)',
  policy_version      VARCHAR(32)   NOT NULL DEFAULT 'v1' COMMENT '保单版本(配置变更即新增)',
  insured_snapshot    JSON         DEFAULT NULL COMMENT '被保人清单(假名化ID数组; 不存明文证件)',
  config_status       VARCHAR(16)   NOT NULL DEFAULT 'INACTIVE' COMMENT '规则开关: INACTIVE未启用/ACTIVE启用中/PAUSE停用中',
  stale_flag          TINYINT       NOT NULL DEFAULT 0 COMMENT '快照过期(1=拒新报案)',
  synced_at           DATETIME(3)   NOT NULL COMMENT '最近同步时间',
  tenant_id           BIGINT        NOT NULL COMMENT '租户',
  created_by          BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at          DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by          BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at          DATETIME(3)   NOT NULL COMMENT '修改时间',
  version             BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted             TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_policy_no (tenant_id, policy_no),
  KEY idx_tenant_policy_status (tenant_id, config_status, effective_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保单(被保人身份+承保期间+规则开关)';

-- ④ 额度保全记录 t_quota_change_log
--   参考实现 PersonalQuotaRecordDialog 的"保全记录"：谁、何时、把哪个被保人的
--   额度从多少改成多少（或减人）。
--   为什么独立成表而不是复用 t_quota_transaction：后者是**理赔占用流水**
--   （append-only，dir/txn_type 受控，守恒校验依赖它），而保全是**配置变更**
--   （改 quota_initial，不影响 claimed/frozen）。两者语义不同，混在一张表里
--   会让"按 txn_type 统计占用"的读侧把保全算进去、把守恒口径算歪。
CREATE TABLE IF NOT EXISTS t_quota_change_log (
  id                BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  quota_account_id  BIGINT        NOT NULL COMMENT '额度账户ID',
  policy_no         VARCHAR(32)   NOT NULL COMMENT '保单号',
  insured_id_hash   CHAR(64)      NOT NULL COMMENT '假名化被保人',
  change_type       VARCHAR(16)   NOT NULL COMMENT 'INIT开户/ADJUST调额/REMOVE减人/RESTORE恢复',
  before_initial    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '变更前初始额度',
  after_initial     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '变更后初始额度',
  operator_id       VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '操作人ID',
  operator_name     VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '操作人姓名',
  remark            VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '变更说明',
  occurred_at       DATETIME(3)   NOT NULL COMMENT '发生时间',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_by        BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by        BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '修改时间',
  deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  KEY idx_tenant_account (tenant_id, quota_account_id, id),
  KEY idx_tenant_policy (tenant_id, policy_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度保全记录(配置变更流水, 与理赔占用流水分表)';

-- ─────────────────────────────────────────────────────────────
-- 三、验收（缺任何一项即 SIGNAL 失败 —— 不允许"脚本跑过了"被误当"结构建好了"）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0085;
DELIMITER $$
CREATE PROCEDURE sp_verify_0085()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    -- ① 补齐的列必须全部就位（本次 P0 修复的核心断言）
    --    「期望列」写成 (表, 列) 两列而非 '表.列' 一列：表名里是下划线不是点，
    --    用 SUBSTRING_INDEX 拆 '表.列' 会因只有一个分隔符而拆不出列名（静默恒不匹配
    --    ⇒ 断言永远判红）。两列写法没有这个歧义。
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 't_adj_liability' AS t, 'invoice_deduct' AS c
        UNION ALL SELECT 't_adj_liability', 'adjust_deduct'
        UNION ALL SELECT 't_adj_liability', 'daily_limit'
        UNION ALL SELECT 't_adj_liability', 'daily_limit_basis'
        UNION ALL SELECT 't_adj_liability', 'limit_mode'
        UNION ALL SELECT 't_adj_liability', 'limit_hospital_codes'
        UNION ALL SELECT 't_adj_liability', 'limit_diagnosis_codes'
        UNION ALL SELECT 't_adj_liability', 'catalog_policy'
        UNION ALL SELECT 't_adj_liability', 'catalog_code'
        UNION ALL SELECT 't_adj_liability', 'coverage_id'
        UNION ALL SELECT 't_adj_liability', 'version'
        UNION ALL SELECT 't_quota_transaction', 'biz_date'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns c2
         WHERE c2.table_schema = DATABASE()
           AND c2.table_name = expected.t
           AND c2.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0085 校验失败：t_adj_liability / t_quota_transaction 仍缺列（写路径会报 Unknown column）';
    END IF;

    -- ② 三张新表必须存在，且必备列齐全（HC-008：tenant_id/created_at/updated_at/deleted）
    SELECT COUNT(*) INTO v_missing FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('t_claim_policy', 't_claim_plan', 't_claim_coverage', 't_quota_change_log');
    IF v_missing <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0085 校验失败：新表未全部建成（policy/plan/coverage/quota_change_log）';
    END IF;

    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 't_claim_policy' AS t UNION ALL SELECT 't_claim_plan'
        UNION ALL SELECT 't_claim_coverage' UNION ALL SELECT 't_quota_change_log'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns c2
         WHERE c2.table_schema = DATABASE() AND c2.table_name = expected.t
           AND c2.column_name IN ('tenant_id', 'created_at', 'updated_at', 'deleted')
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0085 校验失败：新表缺 HC-008 必备列（tenant_id/created_at/updated_at/deleted）';
    END IF;

    -- ③ 关键业务列不得缺失（口径列：保单有效期/开关、计划归属、险种归属、保全前后值）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 't_claim_policy' AS t, 'effective_start' AS c
        UNION ALL SELECT 't_claim_policy', 'effective_end'
        UNION ALL SELECT 't_claim_policy', 'config_status'
        UNION ALL SELECT 't_claim_policy', 'insured_snapshot'
        UNION ALL SELECT 't_claim_plan', 'policy_no'
        UNION ALL SELECT 't_claim_plan', 'plan_code'
        UNION ALL SELECT 't_claim_plan', 'status'
        UNION ALL SELECT 't_claim_coverage', 'plan_id'
        UNION ALL SELECT 't_claim_coverage', 'coverage_code'
        UNION ALL SELECT 't_quota_change_log', 'quota_account_id'
        UNION ALL SELECT 't_quota_change_log', 'change_type'
        UNION ALL SELECT 't_quota_change_log', 'before_initial'
        UNION ALL SELECT 't_quota_change_log', 'after_initial'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns c2
         WHERE c2.table_schema = DATABASE()
           AND c2.table_name = expected.t
           AND c2.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0085 校验失败：新表关键业务列缺失';
    END IF;

    -- ④ 唯一键必须存在（保单号唯一 / 计划编码唯一 / 险种编码唯一）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 't_claim_policy' AS t, 'uk_tenant_policy_no' AS k
        UNION ALL SELECT 't_claim_plan', 'uk_tenant_plan_code'
        UNION ALL SELECT 't_claim_coverage', 'uk_tenant_plan_coverage'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.statistics s
         WHERE s.table_schema = DATABASE()
           AND s.table_name = expected.t
           AND s.index_name = expected.k
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0085 校验失败：新表唯一键缺失（并发下可插入重复保单/计划/险种）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0085();
DROP PROCEDURE IF EXISTS sp_verify_0085;

-- ─────────────────────────────────────────────────────────────
-- 四、验证（手工执行，非脚本自动断言）
-- ─────────────────────────────────────────────────────────────
-- 写路径探针（此前必失败 Unknown column）：
--   INSERT INTO t_adj_liability (id, liability_uuid, liability_version, plan_id,
--     liability_code, liability_type, pay_percent, liability_deduct, liability_limit,
--     invoice_deduct, adjust_deduct, daily_limit, daily_limit_basis,
--     limit_mode, limit_hospital_codes, limit_diagnosis_codes,
--     catalog_policy, catalog_code, social_only, waiting_period, status,
--     tenant_id, created_at, updated_at, deleted)
--   VALUES (999999001,'PROBE','v1',9001,'R99','REIMBURSEMENT',90,0,100000,
--     0,0,0,'NONE','NONE','','','NONE','',1,0,'DRAFT',0,NOW(3),NOW(3),0);
-- 额度流水探针：
--   INSERT INTO t_quota_transaction (id, quota_account_id, claim_id, settlement_no,
--     direction, txn_type, amount, before_amount, after_amount, idempotency_key,
--     occurred_at, biz_date, tenant_id, created_at, updated_at, deleted)
--   VALUES (999999002,933001,0,'PROBE','OCCUPY','DAILY',1,0,0,'PROBE-KEY',
--     NOW(3),'2026-10-31',0,NOW(3),NOW(3),0);
