---
archived-with: 2026-08-23-system-dict-scheduler
status: final
---
# Design Doc: 系统字典与定时任务

## 1. 背景与目标

`bone-system`（Java 17，DDD 分层完整）已有配置/告警/日志/控制台聚合，缺字典与定时任务。字典是主数据「数据标准」落地基座；定时任务是审计清理/质量检查共享调度。前端 system-app 已有留位页待替换为真实 API。

目标：补齐 `SysDict` 聚合（类型分组 + 键值 CRUD）与 `ScheduleTask` 聚合（CRON 启停 + 真实执行），打通 `/api/v1/system/dicts` 与 `/api/v1/system/schedule-tasks`。

## 2. 关键决策

### Decision 1：字典聚合 SysDict（对齐 SystemConfig 范式）

> ⚠️ **本节已过时（v1 扁平模型）**。字典已重构为「类型 + 项」两级模型，支持 ENUM / LIST / CASCADE
> 三类值域、枚举绑定与同步、级联树、内置保护、租户覆盖、进程内缓存与导入导出。
> **真源见 [`doc/design/modules/7a. 数据字典模块详细设计方案.md`](../../../doc/design/modules/7a.%20数据字典模块详细设计方案.md)**；
> 本节的 `SysDict` 单表与 `/api/v1/system/dicts` 已下线（迁移脚本 `scripts/migration/0006_dict_two_level.sql`）。

<details><summary>v1 原始决策（仅作历史留档）</summary>

- `SysDict extends AggregateRoot<Long>`，`@Table("sys_dict")`，`@Id @GeneratedValue(DISTRIBUTED_ID)`，`@NoArgsConstructor(access=PROTECTED)`。
- 字段：`type`(字典类型)、`typeName`、`code`、`label`、`value`、`sort`、`status`、`createdAt`、`updatedAt`。
- 值对象：`DictType(String value)`（构造校验非空）。
- `SysDictRepository` 空接口（`extends Repository<SysDict, Long>`，`@EnableSqlRepositories` 自动实现）。
- Handler：`DictCommandHandler`（create/update/delete）+ `DictQueryHandler`（byType 分组、byTypeAndCode、page）。
- Controller：`DictController`（`SYSTEM_V1 + "/dicts"`，POST/PUT/DELETE/GET byType/GET byTypeAndCode/GET page），用 `ApiResponse` + `PageResult`。

</details>

### Decision 2：定时任务聚合 ScheduleTask + 动态调度
- `ScheduleTask extends AggregateRoot<Long>`，`@Table("sys_schedule_task")`。
- 字段：`name`、`cron`、`handler`（Spring bean 名）、`status`(启用/停用)、`lastRunAt`、`nextRunAt`、`createdAt`、`updatedAt`。
- `ScheduleTaskRepository` 空接口。
- **调度器**：`TaskSchedulerRegistry`（`ThreadPoolTaskScheduler` 编程式注册 Cron）。`@Component`，`@EnableScheduling` 开启；注册 `TaskScheduler` bean。启动时加载所有 `status=ENABLED` 任务，用 `scheduler.schedule(task, cronTrigger)` 注册 `ScheduledFuture`；启停通过 add/remove `ScheduledFuture`。
- **执行目标**：统一 `TaskHandler` 接口（`void run(String taskName)`），`ApplicationContext.getBean(handler)` 调用。内置一个示例 `LogCleanTaskHandler`（打印执行日志 + 记录 lastRunAt）。任务执行失败记日志（SystemLog 或 slf4j）。
- Handler：`ScheduleTaskCommandHandler`（create/update/delete/toggle，启停时同步注册/注销 ScheduledFuture）。
- Controller：`ScheduleTaskController`（`SYSTEM_V1 + "/schedule-tasks"`，POST/PUT/DELETE/GET page/GET/{id}）。

### Decision 3：前端 system-app
- 新增 `dictApi.ts`/`scheduleTaskApi.ts`（接真实 API）。
- 新增 `DictManagement.tsx`（类型分组 + 键值表格）、`ScheduleTaskManagement.tsx`（CRON 编辑 + 启停 Switch），替换留位页。

## 3. 目录结构（新增）

```
bone-system/src/main/java/com/bone/system/
  domain/dict/SysDict.java                      # 字典聚合
  domain/dict/vo/DictType.java                  # 字典类型值对象
  domain/schedule/ScheduleTask.java             # 定时任务聚合
  domain/schedule/vo/TaskStatus.java            # 启用/停用枚举
  domain/repository/SysDictRepository.java
  domain/repository/ScheduleTaskRepository.java
  application/command/cmd/{CreateDictCommand,UpdateDictCommand,CreateScheduleTaskCommand,UpdateScheduleTaskCommand,ToggleScheduleTaskCommand,DeleteDictCommand,DeleteScheduleTaskCommand}.java
  application/command/handler/{DictCommandHandler,ScheduleTaskCommandHandler}.java
  application/query/dto/{DictDTO,ScheduleTaskDTO}.java
  application/query/qry/{DictPageQuery,DictByTypeQuery,ScheduleTaskPageQuery}.java
  application/query/handler/{DictQueryHandler,ScheduleTaskQueryHandler}.java
  infrastructure/scheduler/TaskSchedulerRegistry.java   # 动态 Cron 调度器
  infrastructure/scheduler/TaskHandler.java             # 统一执行目标接口
  infrastructure/scheduler/LogCleanTaskHandler.java     # 示例任务
  adapter/web/controller/{DictController,ScheduleTaskController}.java
  adapter/web/converter/{DictWebConverter,ScheduleTaskWebConverter}.java
  adapter/web/dto/req/{CreateDictReq,UpdateDictReq,CreateScheduleTaskReq,UpdateScheduleTaskReq,ToggleScheduleTaskReq}.java
  adapter/web/dto/resp/{DictResp,ScheduleTaskResp}.java
```

## 4. 风险

- 动态 Cron 注册需线程池隔离（`ThreadPoolTaskScheduler`），避免阻塞主调度。
- 应用重启后需从 DB 重新加载启用任务（`ApplicationReadyEvent` 触发注册）。
- 任务执行失败需记录日志，避免静默失败。

## 5. 验收对照

- Dict CRUD + 按类型分组查询可用。
- ScheduleTask 启停/CRON 管理可用，启用任务按 CRON 真实执行。
- 前端字典/任务页接真实 API。
- `mvn spotless:apply` + ArchUnit 通过。
