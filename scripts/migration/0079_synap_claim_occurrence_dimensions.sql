-- ============================================================
-- 0079_synap_claim_occurrence_dimensions.sql
--
-- 一句话：把 synap-claim 的 Flyway 孤儿脚本 V10 / V11 落到**本仓唯一生效的迁移通道**。
--
-- 背景（为什么必须写这一条，而不是"照 V10/V11 执行即可"）
-- ----------------------------------------------------------------
-- synap-claim 自带 `src/main/resources/db/migration/V1..V11`，但它是一条**从未被执行的通道**：
--   · `bone-apps/synap-claim/pom.xml` 无 `flyway-core` 依赖；
--   · `bone-apps/synap-claim/src/main/resources/application.yml` 无 `spring.flyway.*` 配置；
--   · 服务启动日志无 Flyway 字样（2026-10-30 实测，含 8087 / 8097 两个实例）；
--   · `scripts/migration/migration-manifest.json` 里没有对应条目。
-- 即：这 11 个文件在仓库里"看起来是迁移"，实际对任何环境都不产生结构。它们不是 DDL 真源，
-- 只是把 V10/V11 的**原文**保留了语义说明，本脚本才是落地件。
--
-- 后果（不修会怎样）
-- ----------------------------------------------------------------
-- `ClaimCase` / `ClaimItem` 实体映射的列只由 V10 建立，于是当前代码下
--   GET /api/v1/claims（列表）、GET /api/v1/claims/{id}（详情）一律 500：
--       java.sql.SQLSyntaxErrorException: Unknown column 'm.invoice_date' in 'field list'
-- 这不是"边缘功能不可用"，而是**理赔模块主链路整体不可用**，且报错发生在 SQL 层，
-- 前端只表现为"页面空白/加载失败"，没有任何线索指向"少跑了一次 DDL"。
--
-- 本次落地内容（列定义逐字沿用 V10/V11 原稿，含注释）
-- ----------------------------------------------------------------
--   t_claim_case          + invoice_date / hospital_code / diagnosis_code   （发票级「次」维度）
--   t_claim_item          + department                                     （行级「次」维度）
--   claim_decision_execution + consumption_reason / consumed_at            （决策结论消费结果）
--
-- 为什么发票日期/医院/病种在案件上、科室在明细上（V10 原稿口径，不得改）
-- ----------------------------------------------------------------
--   赔案与发票 1:1（uk_tenant_invoice 限定单一发票号），一次就诊一个开票日期、一家医院、
--   一个主要诊断 ⇒ 放案件上；放明细会引入"同案多开票日期"这种 schema 允许、业务非法
--   的一致性裂缝，且逐行重复存同一个值。
--   一张发票可跨科室（同日多科就诊合并结算）⇒ 科室是行级事实，必须落在明细上；这也正是
--   "同院同科室"这一档能把一次就诊拆成多「次」的唯一维度。
--   历史数据不回填、不猜测：三列用 NULL / 空串缺省，缺失时能否降级由
--   OccurrenceGrouper#requireDimensions 按"可否证单票"决定（可证 ⇒ 降级并留痕；不可证 ⇒ 拒绝）。
--
-- 为什么 V11 两列允许 NULL（V11 原稿口径）
-- ----------------------------------------------------------------
--   NULL = 本次执行没有自动通过结论可消费（SUGGEST_REJECT / MANUAL_REQUIRED / NEED_MORE_EVIDENCE）；
--   非 NULL 才表示"消费动作被判定过"（APPLIED 成功，其余是受控跳过）。不用空串缺省，是为了让
--   "没有消费动作"与"消费了但原因未知"可区分——后者不该出现，一旦出现即数据缺陷，可被查询发现。
--   原因码取决于**执行当时**的开关与单笔上限，事后用当前配置重算得到的是"现在重跑会怎样"，
--   与"当时为什么没放款"必然分叉 ⇒ 按事实落库，不推导。
--
-- 幂等
-- ----------------------------------------------------------------
-- 逐列 information_schema 探测 + PREPARE 动态 DDL（与 0066 同范式），可重复执行。
-- 末尾断言 6 列齐全，缺任何一列即 SIGNAL 失败——**不允许"脚本跑过了"被误当"列建好了"**
-- （0072 头部记的正是同一种事故：脚本执行成功但结构没到，直到运行期才炸）。
--
-- 与 bone-init.sql 的关系（同提交必须一起改，否则新环境仍然不等价）
-- ----------------------------------------------------------------
-- G39 门禁只比**表**级终态，不看列 ⇒ 列缺口是它的盲区（本次缺口就是这样产生的）。
-- 故本脚本与 bone-init.sql 的三张表定义同提交补齐，使"只灌 init 的新环境"与"已迁移环境"等价。
-- ============================================================

-- ---------- ① t_claim_case.invoice_date ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_case ADD COLUMN invoice_date DATE DEFAULT NULL COMMENT ''发票开票日期(「次」分组维度；历史案件为 NULL，不得用出险时间顶替)''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_case' AND COLUMN_NAME = 'invoice_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ② t_claim_case.hospital_code ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_case ADD COLUMN hospital_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''医院编码(「次」分组维度；按编码比对，不按名称——名称可改，编码是身份)''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_case' AND COLUMN_NAME = 'hospital_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ③ t_claim_case.diagnosis_code ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_case ADD COLUMN diagnosis_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''病种/诊断编码(「次」分组维度；一次就诊一个主要诊断)''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_case' AND COLUMN_NAME = 'diagnosis_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ④ t_claim_item.department ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_item ADD COLUMN department VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''就诊科室(行级「次」分组维度；一张发票可跨科室，故必须落在明细上)''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_item' AND COLUMN_NAME = 'department'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑤ claim_decision_execution.consumption_reason ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE claim_decision_execution ADD COLUMN consumption_reason VARCHAR(32) DEFAULT NULL COMMENT ''自动通过结论的消费结果(APPLIED/AUTO_APPROVE_DISABLED/CAP_EXCEEDED/ALREADY_SETTLED); NULL=结论非自动通过或尚未消费'' AFTER duration_ms',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_decision_execution' AND COLUMN_NAME = 'consumption_reason'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ⑥ claim_decision_execution.consumed_at ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE claim_decision_execution ADD COLUMN consumed_at DATETIME(3) DEFAULT NULL COMMENT ''结论消费(或受控跳过)的判定时间'' AFTER consumption_reason',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_decision_execution' AND COLUMN_NAME = 'consumed_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 终态断言：6 列必须齐全 ----------
DROP PROCEDURE IF EXISTS `tmp_assert_0079`;
DELIMITER $$
CREATE PROCEDURE `tmp_assert_0079`()
BEGIN
    DECLARE v_missing INT;

    SELECT COUNT(*) INTO v_missing
      FROM (
        SELECT 't_claim_case' AS t, 'invoice_date' AS c       UNION ALL
        SELECT 't_claim_case', 'hospital_code'                UNION ALL
        SELECT 't_claim_case', 'diagnosis_code'               UNION ALL
        SELECT 't_claim_item', 'department'                   UNION ALL
        SELECT 'claim_decision_execution', 'consumption_reason' UNION ALL
        SELECT 'claim_decision_execution', 'consumed_at'
      ) AS expected
     WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = expected.t
           AND col.column_name = expected.c
     );

    IF v_missing <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0079 校验失败：synap-claim 「次」分组维度/决策消费列未补齐（理赔列表将 500）';
    END IF;
END$$
DELIMITER ;
CALL `tmp_assert_0079`();
DROP PROCEDURE IF EXISTS `tmp_assert_0079`;
