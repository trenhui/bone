# Tasks: 系统字典与定时任务

## 1. 字典聚合
- [x] 新增 `SysDict` 聚合（`@Table("sys_dict")`，type/typeName/code/label/value/sort/status）+ `DictType` 值对象
- [x] 新增 `SysDictRepository` 空接口（@EnableSqlRepositories 自动实现）
- [x] 新增 `DictCommandHandler`（create/update/delete）+ `DictQueryHandler`（byType/byTypeAndCode/page）
- [x] 新增 `DictController`（`/api/v1/system/dicts`）

## 2. 定时任务
- [x] 新增 `ScheduleTask` 聚合（`@Table("sys_schedule_task")`，name/cron/handler/status/lastRunAt/nextRunAt）+ `TaskStatus` 枚举
- [x] 新增 `ScheduleTaskRepository` 空接口
- [x] 实现动态调度器 `TaskSchedulerRegistry`（ThreadPoolTaskScheduler 编程式注册 Cron）+ `TaskHandler` 接口 + `LogCleanTaskHandler` 示例
- [x] 新增 `ScheduleTaskCommandHandler`（create/update/delete/toggle，启停管理 ScheduledFuture）
- [x] 新增 `ScheduleTaskController`（`/api/v1/system/schedule-tasks`）

## 3. 前端页面
- [x] `shared-types` 追加 `SysDict`/`ScheduleTask` 类型
- [x] `system-app/src/services/api.ts` 追加 `dictApi` + `scheduleTaskApi`
- [x] 新增 `DictManagement.tsx`（类型分组 + antd Table + Modal 表单 CRUD）
- [x] 新增 `ScheduleTaskManagement.tsx`（启停 Switch + CRON 编辑 + 分页）
- [x] App.tsx 注册 `/dict` `/schedule` 路由 + shell system 分组菜单项

## 4. 校验
- [x] `mvn spotless:apply` + ArchUnit 通过（bone-system 18/18）
- [x] 前端 tsc 通过（新页面/菜单无错误）
