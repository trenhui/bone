-- =============================================================================
-- 0060_synap_claim_invoice_nullable.sql
--
-- 目的（2026-10-25 联调收尾）：修正 t_claim_case.uk_tenant_invoice 对"未附发票"的误伤。
--
-- 缺陷证据
-- ----------------------
-- 全链路 E2E 第二次报案即失败：
--   Duplicate entry '0---0' for key 't_claim_case.uk_tenant_invoice'
-- 拆解该键值：tenant_id=0、invoice_code=''、invoice_no=''、deleted=0。
-- 即发票号为空串的草稿态赔案被视为"同一张发票"，第二笔报案必然撞键——
-- 报案是高频入口，此约束等于把"未附票投保人"锁死为全租户仅一笔。
--
-- 修复取向
-- ----------------------
-- 把"未附发票"表达为 SQL 的 NULL，而不是空串：
--   · 唯一索引在 SQL 标准下把多个 NULL 视为互不相等 → 草稿态可无限共存；
--   · 一旦写入真实发票号，租户级防重复赔付（R-FRAUD-07）照旧由同一索引保证；
--   · 未引入生成列/函数索引，MySQL 5.7 与 8.0、TiDB 行为一致，无版本绑定。
-- 应用侧同步：ClaimCase#draft 以 normalizeInvoicePart 将空白折叠为 null。
--
-- 执行（本地/测试库直接应用；生产 DDL 属 L3，大表须 DBA 走 gh-ost/pt-osc）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0060_synap_claim_invoice_nullable.sql
--
-- 幂等性：MODIFY 重复执行结果一致；UPDATE 二次执行匹配 0 行。可安全重跑。
-- 新装环境：bone-init.sql 与 0058 已直接落地最终列定义，本文件退化为 no-op。
-- =============================================================================

USE bone;

ALTER TABLE t_claim_case
  MODIFY invoice_code VARCHAR(32) DEFAULT NULL COMMENT '发票代码(草稿未附票为NULL，NULL不占用唯一键)',
  MODIFY invoice_no   VARCHAR(64) DEFAULT NULL COMMENT '发票号码(与代码组合防重复赔付，NULL不占用唯一键)';

-- 存量归一：把历史空串折叠为 NULL，释放被草稿误占的发票键
UPDATE t_claim_case SET invoice_code = NULL WHERE invoice_code = '';
UPDATE t_claim_case SET invoice_no   = NULL WHERE invoice_no   = '';
