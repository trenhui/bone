-- V24: 调度单写者锁表（P1-2，2026-11-12 落地）。
-- 多副本部署下 5 个 @Scheduled Job（对账 / 超期关单 / 后督抽检 / SLA 物化 / outbox 中继）
-- 此前仅由 bone.claim.schedule.enabled 总开关控制，每个副本都会并发跑，产生重复副作用
-- （重复对账、重复自动关单、重复派发）。建 t_schedule_lock，由 ScheduleLockService 以
-- 「唯一键 lock_name 插入 + @Version 更新」在 DB 层实现跨副本互斥，零外部依赖、不改业务行为、不触碰资金。
-- 本表为 synap-claim 模块内 Flyway 迁移（src/main/resources/db/migration），不在平台迁移清单
-- scripts/migration 内；与生效通道 scripts/migration/* 的 DDL 治理口径相互独立。

CREATE TABLE t_schedule_lock (
  id           BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  lock_name    VARCHAR(128) NOT NULL COMMENT '调度锁名称(全局唯一, 如 schedule:reconciliation)',
  locked_until BIGINT      NOT NULL COMMENT '锁持有截止(epoch millis); 到期即失效由他副本接管',
  locked_at    BIGINT      NOT NULL COMMENT '锁获取时间(epoch millis)',
  locked_by    VARCHAR(128) NOT NULL COMMENT '持锁实例标识(本实例 UUID / K8s pod 名)',
  version      BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本(SDK @Version 维护)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_lock_name (lock_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '调度单写者锁(多副本仅一个实例执行定时任务)';
