# Bone Integration Module

## 模块概述

集成管理模块是 BONE 平台的核心能力之一，负责系统间的集成和流程编排，是实现企业集成能力的关键模块。

**核心功能**：
- 连接器管理：支持与外部系统的连接和配置
- 流程编排：可视化的流程设计和管理
- 流程监控：监控流程执行状态和日志
- 集成模板：提供常用集成场景的模板

## 技术栈

- Java 17+
- Spring Boot 3.2+
- Spring Security 6.2+
- Apache Camel 4.0+
- RocketMQ 5.1+
- Redis 7.0+
- bone-core：提供领域模型基础组件
- bone-metadata-sdk：元数据驱动的持久化框架

## 工程结构

```
bone-integration/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── bone/
│   │   │           └── integration/
│   │   │               ├── IntegrationApplication.java  # 应用入口
│   │   │               ├── adapter/               # 入站适配器层
│   │   │               │   ├── web/               # Web适配器
│   │   │               │   └── schedule/          # 定时任务
│   │   │               ├── application/            # 应用层
│   │   │               │   ├── command/           # 命令
│   │   │               │   ├── query/             # 查询
│   │   │               │   └── event/             # 事件处理器
│   │   │               ├── domain/                # 领域层
│   │   │               │   ├── model/             # 领域模型
│   │   │               │   ├── repository/       # 仓储接口
│   │   │               │   ├── client/           # 防腐层接口
│   │   │               │   └── service/          # 领域服务
│   │   │               ├── infrastructure/       # 基础设施层
│   │   │               │   ├── external/         # 外部服务实现
│   │   │               │   └── config/           # 配置
│   │   │               └── common/               # 通用组件
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   └── test/            # 测试代码
├── pom.xml             # Maven配置
└── README.md           # 模块说明
```

## 快速开始

### 1. 环境准备

- JDK 17 或更高版本
- Maven 3.6+ 
- MySQL 8.0+ 
- Redis 7.0+ 
- RocketMQ 5.1+ 

### 2. 配置修改

修改 `src/main/resources/application.yml` 文件，配置数据库连接、Redis、RocketMQ等信息。

### 3. 启动应用

```bash
# 编译打包
mvn clean package

# 运行应用
java -jar target/bone-integration-1.0.0.jar
```

### 4. 访问API文档

启动后，可通过以下地址访问Swagger API文档：

```
http://localhost:8085/api/swagger-ui.html
```

## API接口

### 连接器管理

- `POST /api/integration/connectors` - 创建连接器
- `PUT /api/integration/connectors/{id}` - 更新连接器
- `GET /api/integration/connectors` - 获取连接器列表
- `GET /api/integration/connectors/{id}` - 获取连接器详情
- `DELETE /api/integration/connectors/{id}` - 删除连接器
- `POST /api/integration/connectors/{id}/test` - 测试连接器
- `POST /api/integration/connectors/{id}/enable` - 启用连接器
- `POST /api/integration/connectors/{id}/disable` - 禁用连接器

### 流程管理

- `POST /api/integration/flows` - 创建流程
- `PUT /api/integration/flows/{id}` - 更新流程
- `GET /api/integration/flows` - 获取流程列表
- `GET /api/integration/flows/{id}` - 获取流程详情
- `DELETE /api/integration/flows/{id}` - 删除流程
- `POST /api/integration/flows/{id}/activate` - 激活流程
- `POST /api/integration/flows/{id}/deactivate` - 停用流程

### 监控管理

- `POST /api/integration/executions` - 执行流程
- `GET /api/integration/executions` - 获取执行记录列表
- `GET /api/integration/executions/{id}` - 获取执行记录详情
- `POST /api/integration/executions/{id}/retry` - 重试执行
- `GET /api/integration/statistics` - 获取流程执行统计

## 开发规范

- 遵循 Bone-Blueprint v4.0 工程规范
- 采用 DDD + CQRS + 六边形架构
- 领域层零依赖，仅依赖 bone-core
- 应用层负责用例编排和事务管理
- 适配器层负责协议转换和请求路由
- 基础设施层负责外部服务实现和配置

## 注意事项

- 连接器配置中的敏感信息（如密码）应加密存储
- 流程执行采用异步方式，避免阻塞主线程
- 执行日志保留天数可通过配置调整
- 生产环境应配置合适的告警机制

## 维护指南

- 定期清理过期的执行日志
- 监控连接器的连接状态
- 优化流程执行性能
- 及时更新外部系统的连接配置
