-- ============================================================
-- 0054_sys_log_tenant_time_idx.sql
-- sys_log 补复合索引 (tenant_id, created_at)
--
-- 为什么：
--   日志分页的固定形态是「租户作用域 + 时间范围 + created_at 倒序」
--   （SystemLogRepository#pageByCondition；ADR-0029 租户失败关闭 ⇒ 每条查询都带 tenant_id 谓词）。
--   既有 idx_sys_log_time(created_at) 只能先按时间窗扫描再回表过滤租户；多租户数据量上来后，
--   单列时间索引的扫描行数随「全租户时间窗数据」增长，与「本租户」无关的流量全落在扫描成本上。
--   复合索引让「租户 ∩ 时间窗」直接定位，且索引本身按 created_at 有序，倒序分页免 filesort。
--
-- 为什么不替换 idx_sys_log_time：跨租户保留期清理 Job
--   （SystemLogRepository#purgeOlderThanAllTenants，显式 disableTenantFilter）
--   查的是纯 created_at 范围，单列索引是它的主场，删了会让清理全表扫描。
--
-- 依据：系统管模块功能诊断与优化报告 §四-5（日志时间范围超万台查询性能）
-- 前置：bone-init.sql 已含该复合索引；本脚本负责存量环境补齐（幂等，可重复执行）。
-- ============================================================

-- ---------- 阶段 1：补齐复合索引（幂等：information_schema.statistics 探测）----------
-- 本 MySQL 构建不支持 CREATE INDEX IF NOT EXISTS（同 0013 记载），故走探测 + PREPARE 分支。
SET @idx_sys_log_tenant_time = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_log' AND index_name = 'idx_sys_log_tenant_time'
);
SET @sql_sys_log_tenant_time = IF(@idx_sys_log_tenant_time = 0,
  'CREATE INDEX idx_sys_log_tenant_time ON sys_log (tenant_id, created_at)',
  'SELECT 1');
PREPARE st_sys_log_tenant_time FROM @sql_sys_log_tenant_time;
EXECUTE st_sys_log_tenant_time;
DEALLOCATE PREPARE st_sys_log_tenant_time;
