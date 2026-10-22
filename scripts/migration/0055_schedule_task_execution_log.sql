-- ============================================================
-- 0055_schedule_task_execution_log.sql
-- 定时任务执行记录表 sys_schedule_task_log（业界标配：RuoYi / XXL-Job 的 task log）
--
-- 为什么：
--   sys_schedule_task.last_run_at / next_run_at 只是「当前状态」——跑没跑过、下次何时跑；
--   执行历史（每次何时触发、成功还是失败、耗时多久、失败消息是什么）没有落点，
--   「任务悄悄失败了」只能去翻服务器日志，租户侧零可观测。
--   本表让每次执行（CRON 触发 / 手动触发）落一行流水，失败可追溯、历史可查。
--
-- 模型取舍：
--   - task_name/handler 是「执行时快照」：任务改名后历史记录仍可读（不随主表 update 漂移）；
--   - 无 updated_at / deleted 列：append-only 流水，无更新与软删语义，保留期清理即物理删
--     （领域侧 @PhysicalDelete，同 sys_log）；
--   - 查询形态固定为「按 task_id 倒序分页」，故索引即 (task_id, created_at)。
--
-- 依据：系统管模块功能诊断与优化报告 §四-2（定时任务缺执行日志与下次执行时间；
--       nextRunAt/lastRunAt 已落地，本脚本补齐执行记录）
-- 前置：bone-init.sql 已含本表；本脚本负责存量环境补齐（幂等，可重复执行）。
-- ============================================================

-- ---------- 阶段 1：建表（Expand；已存在则跳过）----------
CREATE TABLE IF NOT EXISTS sys_schedule_task_log (
    id                  BIGINT          NOT NULL COMMENT '记录主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    task_id             BIGINT          NOT NULL COMMENT '定时任务ID',
    task_name           VARCHAR(100)    NOT NULL COMMENT '执行时任务名称快照',
    handler             VARCHAR(255)    DEFAULT NULL COMMENT '执行时处理器bean名快照',
    trigger_type        VARCHAR(20)     NOT NULL DEFAULT 'CRON' COMMENT '触发方式（CRON/MANUAL）',
    run_status          VARCHAR(20)     NOT NULL COMMENT '执行结果（SUCCESS/FAILED）',
    started_at          DATETIME(3)     NOT NULL COMMENT '开始时间',
    finished_at         DATETIME(3)     DEFAULT NULL COMMENT '结束时间',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '耗时（毫秒）',
    message             VARCHAR(1000)   DEFAULT NULL COMMENT '失败异常摘要（成功为空）',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sched_log_task (task_id, created_at),
    KEY idx_sched_log_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务执行记录';
