# ${table.tableComment!'实体'}（${table.customEntityName}）接口说明

> 由代码生成器基于表 `${table.originalTableName}` 生成，随模板变更会重新产出，**不要手工改**——
> 要改请改模板，否则下次生成会被覆盖回去。

## 基本信息

| 项 | 值 |
|---|---|
| 物理表 | `${table.originalTableName}` |
| 聚合根 | `${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName}` |
| 应用服务 | `${table.customEntityName}ApplicationService` |
| REST 前缀 | `${apiPrefix}/${utils.toResourceSegment(table.customEntityName)}` |

## 接口清单

| 方法 | 路径 | 说明 | 成功状态 |
|---|---|---|---|
| POST | `${apiPrefix}/${utils.toResourceSegment(table.customEntityName)}` | 创建${table.tableComment!'实体'} | 201 |
| PUT | `${apiPrefix}/${utils.toResourceSegment(table.customEntityName)}/{id}` | 更新${table.tableComment!'实体'} | 200 |
| DELETE | `${apiPrefix}/${utils.toResourceSegment(table.customEntityName)}/{id}` | 删除${table.tableComment!'实体'} | 200 |
| GET | `${apiPrefix}/${utils.toResourceSegment(table.customEntityName)}/{id}` | 查询详情 | 200 |
| GET | `${apiPrefix}/${utils.toResourceSegment(table.customEntityName)}` | 分页查询 | 200 |

## 业务错误码

| 状态 | errorCode | 含义 |
|---|---|---|
| 404 | `${moduleName?upper_case}_${table.customEntityName?upper_case}_NOT_FOUND` | ${table.tableComment!'实体'}不存在 |
| 409 | `${moduleName?upper_case}_${table.customEntityName?upper_case}_CONFLICT` | 乐观锁冲突（并发修改，可重试） |

## 字段

| 字段 | 类型 | 说明 | 必填 |
|---|---|---|---|
<#list businessColumns as column>
| `${column.fieldName}` | `${column.javaType}` | ${column.comment} | ${column.nullable?then('否', '是')} |
</#list>
| `id` | `Long` | 主键（生成器预分配，ADR-0019） | — |
| `tenantId` | `Long` | 租户（由 SDK 从 TenantContext 补正，ADR-0029） | — |
| `version` | `Long` | 乐观锁版本 | — |
| `createdAt` / `updatedAt` | `Instant` | 审计时间 | — |
