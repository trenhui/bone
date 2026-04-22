# Bone System 模块

## 概述

Bone System 是 Bone 平台的系统管理模块，提供系统配置、监控告警、日志管理等核心功能。

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

遵循 Bone Blueprint v4.0 架构规范，采用六边形架构 + DDD + CQRS 模式：

```
com.bone.system
├── adapter/              # 适配器层
│   └── web/
│       └── controller/  # REST控制器
├── application/         # 应用层
│   ├── command/         # 命令模型和处理器
│   └── query/           # 查询模型和处理器
├── domain/              # 领域层
│   ├── model/           # 聚合根和值对象
│   ├── repository/      # 仓储接口
│   └── service/         # 领域服务
├── infrastructure/      # 基础设施层
│   └── config/          # 配置类
└── common/              # 通用组件
    ├── exception/       # 异常类
    └── result/          # 响应结果
```

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
| POST | /api/system/config | 创建配置 |
| PUT | /api/system/config | 更新配置 |
| DELETE | /api/system/config/{id} | 删除配置 |
| GET | /api/system/config/{id} | 获取配置详情 |
| GET | /api/system/config/key/{key} | 根据键获取配置 |
| GET | /api/system/config/page | 分页查询配置 |

### 告警管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/system/alert/rules | 创建告警规则 |
| PUT | /api/system/alert/rules | 更新告警规则 |
| POST | /api/system/alert/rules/{id}/enable | 启用告警规则 |
| POST | /api/system/alert/rules/{id}/disable | 禁用告警规则 |
| DELETE | /api/system/alert/rules/{id} | 删除告警规则 |
| GET | /api/system/alert/rules/{id} | 获取告警规则详情 |
| GET | /api/system/alert/rules/page | 分页查询告警规则 |
| POST | /api/system/alert/events | 创建告警事件 |
| POST | /api/system/alert/events/{id}/resolve | 解决告警事件 |
| GET | /api/system/alert/events/{id} | 获取告警事件详情 |
| GET | /api/system/alert/events/page | 分页查询告警事件 |

### 日志管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/system/logs | 创建日志 |
| GET | /api/system/logs/{id} | 获取日志详情 |
| GET | /api/system/logs/page | 分页查询日志 |

### 系统管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/system/health | 获取系统健康状态 |
| GET | /api/system/info | 获取系统信息 |
| GET | /api/system/metrics | 获取系统指标 |

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

- 遵循 Bone Blueprint v4.0 架构规范
- 领域层零框架依赖
- 采用 CQRS 模式分离读写
- 使用 Lombok 简化代码（仅允许 @Getter）
- 使用 MapStruct 进行对象映射

## 测试

```bash
# 运行单元测试
./mvnw test

# 运行架构测试
./mvnw test -Dtest=ArchitectureTests
```

## 许可证

Bone System 是 Bone 平台的一部分，遵循相应的开源许可协议。
