# Bone System 模块

## 概述

Bone System 是 Bone 平台的系统管理模块，提供系统配置、监控告警、日志管理等核心功能。

## 上下文与边界

限界上下文 **System**（通用域，P-2.1）：平台配置、运维日志与监控。

| 维度 | 内容 |
|---|---|
| 通用语言 | `SystemConfig`、`SysDict`、`AlertRule`、`AlertRecord`、`SystemLog`、`ScheduleTask`、`ConsoleOverview` |
| 负责 | 配置键的生命周期与快照导出 / 恢复；字典项维护；告警规则与告警记录；日志落库、查询与导出；定时任务注册；控制台概览聚合 |
| **不负责** | 身份与权限（IAM）、主数据治理（MasterData）、集成流程编排（Integration）——本模块不持有、也不跨库写这些上下文的表 |
| 上游 | 无（通用域，被所有应用上下文消费） |
| 下游关系 | 对所有应用上下文提供 OHS / Published Language：配置、日志与监控 API（P-2.4）；下游只消费公开技术契约，不反向依赖本模块 domain |
| 表所有权 | `sys_config`、`sys_dict`、`sys_alert_rule`、`sys_alert_record`、`sys_log`、`sys_schedule_task` 的唯一写 Owner 是本模块 |

## 技术栈

- Java 17+
- Spring Boot 3.2+
- Bone Core (领域驱动设计核心库)
- Bone Metadata SDK (元数据驱动持久化)
- Bone Notification (告警通知服务)
- H2/MySQL (数据库)
- Swagger/OpenAPI 3 (API文档)
- Prometheus (监控指标)

## 模块架构

遵循《Bone-DDD-最终实践方案》E-10 包结构 + ADR-0028（Application Service First）：四层分包，
入口统一为语义化 `*ApplicationService`，不预生成 `command/handler` / `query/handler` 空目录。

```
com.bone.system
├── adapter/web/
│   ├── controller/            # HTTP 入站：只做协议转换与统一响应，不含业务判断
│   ├── assembler/             # *Req/*Resp ↔ *Command/*Dto（MapStruct）
│   └── dto/{request,response}/
├── application/
│   ├── ConfigApplicationService          # 配置：读写同一入口
│   ├── DictApplicationService
│   ├── AlertApplicationService           # 告警规则 + 告警记录（记录由规则推导）
│   ├── SystemLogApplicationService
│   ├── ScheduleTaskApplicationService
│   ├── ConsoleApplicationService         # 控制台概览：组合 3 个 domain gateway
│   ├── ConfigSnapshotApplicationService  # 批量 upsert，事务粒度与单条 CRUD 不同
│   ├── LogExportApplicationService       # 读结果 → CSV，供 HTTP 与定时归档复用
│   ├── command/                          # 写用例输入（*Command，平铺不建子包）
│   ├── port/out/                         # 出站端口（统一 *Port 后缀）
│   └── query/{dto,qry}/                  # 应用投影（*Dto）与读用例输入（*Query）
├── domain/
│   ├── config/ dict/ alert/ log/ schedule/   # 按聚合平铺（值对象 → vo/，事件 → event/）
│   ├── console/                          # 控制台读侧值对象（不可变，非聚合）
│   ├── gateway/                          # 外部业务事实端口（服务健康/资源/关键指标）
│   └── repository/                       # 写侧 + 本聚合读（ADR-0030）
├── infrastructure/
│   ├── event/                            # SpringDomainEventPublisher（写路径 publishFrom 的承载）
│   ├── gateway/                          # domain/gateway 的实现（*GatewayAdapter）
│   ├── scheduler/                        # ScheduleTaskSchedulerPort 的实现
│   ├── config/ security/ observability/
└── common/                               # SystemErrorCodes + SystemErrors（码 → HTTP 状态唯一配对）
```

**domain 分组形态**：采用 `domain/{aggregate}` 平铺（E-10 允许的两种形态之一），聚合根、值对象、事件按聚合归组；
不再保留 `domain/model/{aggregate|entity|valueobject|event}` 角色分组，也不允许两套形态并存。

**读侧取数落点**：本聚合读（分页 / 按业务键）声明在 `domain/repository` 的 `default` 方法里，`Criteria`
能表达的单键查询用它，跨列 OR 用 `QueryBuilder`（`Criteria.or(Consumer)` 会把 OR 组当等值条件拼接，是 SDK
已知缺陷，修复前不使用）；application 不再拼任何读侧 DSL。跨聚合组合读出现时按 E-4.2 新建
`application/query/port` + `infrastructure/query`。

## 核心功能

### 1. 系统配置

- 配置项管理（增删改查）
- 配置键值对存储
- 配置类型分类（系统级、服务级、功能级）
- 敏感配置加密
- 配置变更历史

### 2. 监控告警

- 告警规则管理
- 告警级别配置（严重、警告、信息）
- 告警事件记录
- 多渠道通知（邮件、短信、钉钉、企业微信）
- 告警规则启用/禁用

### 3. 日志管理

- 系统日志记录
- 日志级别控制（ERROR、WARN、INFO、DEBUG、TRACE）
- 日志搜索和过滤
- 日志分页查询

### 4. 系统监控

- 健康检查
- JVM 指标监控
- 系统信息查看

## 快速开始

### 1. 启动应用

```bash
# 开发环境
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 生产环境
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### 2. 访问 API 文档

启动后访问：http://localhost:8083/swagger-ui.html

### 3. H2 控制台

开发环境访问：http://localhost:8083/h2-console

JDBC URL: `jdbc:h2:mem:systemdb`

## API 接口

### 系统配置

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/system/config | 创建配置 |
| PUT | /api/v1/system/config | 更新配置 |
| DELETE | /api/v1/system/config/{id} | 删除配置 |
| GET | /api/v1/system/config/{id} | 获取配置详情 |
| GET | /api/v1/system/config/key/{key} | 根据键获取配置 |
| GET | /api/v1/system/config/page | 分页查询配置 |

### 告警管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/system/alert/rules | 创建告警规则 |
| PUT | /api/v1/system/alert/rules | 更新告警规则 |
| POST | /api/v1/system/alert/rules/{id}/enable | 启用告警规则 |
| POST | /api/v1/system/alert/rules/{id}/disable | 禁用告警规则 |
| DELETE | /api/v1/system/alert/rules/{id} | 删除告警规则 |
| GET | /api/v1/system/alert/rules/{id} | 获取告警规则详情 |
| GET | /api/v1/system/alert/rules/page | 分页查询告警规则 |
| POST | /api/v1/system/alert/events | 创建告警事件 |
| POST | /api/v1/system/alert/events/{id}/resolve | 解决告警事件 |
| GET | /api/v1/system/alert/events/{id} | 获取告警事件详情 |
| GET | /api/v1/system/alert/events/page | 分页查询告警事件 |

### 日志管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/system/logs | 创建日志 |
| GET | /api/v1/system/logs/{id} | 获取日志详情 |
| GET | /api/v1/system/logs/page | 分页查询日志 |

### 系统管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/system/health | 获取系统健康状态 |
| GET | /api/v1/system/info | 获取系统信息 |
| GET | /api/v1/system/metrics | 获取系统指标 |

## 配置说明

### 应用配置

```yaml
bone:
  system:
    log-retention-days: 180  # 日志保留天数
```

### 数据库配置

默认使用 H2 内存数据库，生产环境建议使用 MySQL。

## 监控指标

系统自动暴露 Prometheus 指标，访问路径：`/actuator/prometheus`

关键指标：
- JVM 内存使用
- JVM 线程数
- HTTP 请求统计
- 自定义业务指标

## 开发规范

- 依赖方向 `adapter → application → domain ← infrastructure`；domain 零框架依赖（E-10.1）
- **一个用例一个入口边界**（CORE-04）：新用例默认加 `*ApplicationService` 方法，不新建 Handler；
  出现独立路由 / 异步 / 多入口时按 E-3.7 决策树判断，禁止 `Controller → Handler → 同义 Service` 套娃
- 写路径固定四步：加载聚合 → 调用领域行为 → `repository.save(agg)` → `domainEventPublisher.publishFrom(agg)`；
  缺 `publishFrom` 等于把事件丢掉，编译与单测都不会报错
- 失败一律走 `SystemErrors.of(SystemErrorCodes.XXX, 上下文)`，**不在抛出点手写 HTTP 状态数字**；
  领域异常（`DomainException`）在应用层翻译成 4xx，任其冒泡会被兜底成 500
- 持久化只用 `bone-metadata-sdk`：禁 MyBatis / JPA / JdbcTemplate；SQL 模板真源是外置 `resources/sql/**`
- 使用 Lombok（`@Getter` / `@Builder` / `@RequiredArgsConstructor`）与 MapStruct（adapter 装配）

## 测试

```bash
# 运行单元测试
./mvnw test

# 运行架构测试
./mvnw test -Dtest=ArchitectureTests
```

## 许可证

Bone System 是 Bone 平台的一部分，遵循相应的开源许可协议。
