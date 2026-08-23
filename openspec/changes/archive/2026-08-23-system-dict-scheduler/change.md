# Change: 系统字典与定时任务

**Type**: Feature（共享基座）
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段1 T3）。基于扫描：system 后端 45%、前端 95%，但后端缺字典管理与定时任务，字典是主数据"数据标准"基座、定时任务是审计清理/质量检查共享能力。
**Depends on**: 无

## Intent
补齐系统管理模块的数据字典（Dict）与定时任务（ScheduleTask）调度能力，使其成为平台级共享基座。

## Scope (In)
- `bone-system` 后端：新增 `Dict` 聚合（类型/键值）+ `ScheduleTask` 聚合（CRON/启停）+ Handler + controller；基于 Spring Scheduler 实现启停。
- `bone-system-app` 前端：字典管理页、定时任务管理页，替换现有留位。

## Scope (Out / Non-Goals)
- 不做分布式任务调度（如 XXL-JOB/Quartz 集群）。
- 不做可视化任务编排引擎。

## Assumptions
- `bone-system` 与 `bone-iam` 共享 `bone-core` 基类与 TenantContext。
- 前端 system-app 已有配置/日志/监控页，仅新增两个模块页。

## Acceptance Criteria
- [ ] Dict CRUD 可用（类型分组 + 键值）。
- [ ] ScheduleTask 启停/CRON 管理可用，任务真实执行。
- [ ] 前端字典/任务页接后端 API 替换留位。
- [ ] ArchUnit 通过、spotless 格式一致。
