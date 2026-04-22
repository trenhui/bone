# Bone 项目 Code Wiki

## 1. 项目概述

Bone 是一个企业级全栈开源原生快速开发平台，以"Build Once, Natively Everywhere"为理念，通过元数据驱动开发，实现一次构建多端运行的能力。

### 核心价值

- **开发效率革命性提升**：元数据驱动开发，减少70%重复编码工作
- **智能全栈生成**：一键生成前后端代码、API文档、数据库脚本与单元测试
- **多端统一架构**：一次配置自动适配Web管理端、移动端（App）、小程序
- **企业级能力开箱即用**：RBAC权限体系、分布式事务保障、全链路可观测

## 2. 整体架构

Bone 采用模块化架构设计，以"数据-质量-功能-生态"为核心逻辑，构建四大引擎协同体系，形成企业级应用开发的完整技术闭环。

### 架构层次

```
bone-framework/     # 核心框架层，提供基础组件和工具
bone-engine/        # 核心引擎层，包含四大核心引擎
bone-business/      # 业务模块层，实现具体业务逻辑
bone-frontend/      # 前端应用层，多端适配
```

### 四大核心引擎

1. **智能元数据引擎（Smart Metadata Engine）**：平台的"数字大脑"，驱动应用全生命周期自动化
2. **企业主数据平台（Master Data Platform）**：构建企业唯一可信数据源，消除数据孤岛
3. **ExtPoint 扩展引擎（Extension Engine）**：打造可生长的插件化架构，平衡稳定性与灵活性
4. **集成引擎（Integration Engine）**：打破系统壁垒，构建统一企业数字生态

### 引擎协同流程

```
智能元数据引擎 → 定义数据骨架与基础业务规则（回答"是什么"）
        ↓
企业主数据平台 → 确保核心数据一致可信（保障"数据质量"）
        ↓  
ExtPoint扩展引擎 → 注入个性化业务逻辑（解决"怎么做"）
        ↓
   集成引擎 → 连接外部系统与数据生态（实现"和谁交互"）
        ↓
   企业级数字化平台（支撑全场景业务运行）
```

## 3. 主要模块职责

### 3.1 bone-framework

核心框架层，提供基础组件和工具，是整个平台的技术基础。

| 子模块 | 职责 | 关键组件 |
|--------|------|----------|
| bone-core | 核心基础组件 | 实体定义、异常处理、模型类、工具类 |
| bone-datasource | 数据源管理 | 动态数据源、租户隔离 |
| bone-security | 安全框架 | 认证授权、加密工具、权限管理 |
| bone-utils | 工具类库 | ID生成、JSON处理、异常工具 |
| bone-web | Web相关 | 全局异常处理 |

### 3.2 bone-engine

核心引擎层，实现平台的核心能力。

| 子模块 | 职责 | 关键组件 |
|--------|------|----------|
| bone-extension-engine | 扩展引擎 | 扩展点定义、插件管理、生命周期管理 |
| bone-integration | 集成引擎 | 多协议兼容、流程编排、数据转换 |
| bone-metadata | 元数据引擎 | 元数据定义、处理、验证 |
| bone-metadata-sdk | 元数据SDK | 元数据服务、查询构建、扩展机制 |
| bone-procurement | 采购业务示例 | 采购订单管理、审批流程 |
| bone-smartmeta | 智能元数据 | 智能元数据处理、业务规则引擎 |
| bone-workflow | 工作流引擎 | 流程定义、执行、监控 |

### 3.3 bone-business

业务模块层，实现具体业务逻辑。

| 子模块 | 职责 | 关键组件 |
|--------|------|----------|
| bone-admin | 管理后台 | 系统管理、用户管理、权限管理 |
| bone-trade | 交易模块 | 订单管理、支付处理、物流跟踪 |

### 3.4 bone-frontend

前端应用层，实现多端适配。

| 子模块 | 职责 | 技术栈 |
|--------|------|----------|
| apps/main | 主应用 | Vue 3.4 + Vite 5.4 + Element Plus 2.8 |
| apps/sub-app-1 | 子应用1 | Vue 3.4 + TypeScript 5.5 |
| apps/sub-app-2 | 子应用2 | Vue 3.4 + TypeScript 5.5 |
| packages/core | 核心包 | 事件总线、微前端运行时 |

## 4. 关键类与函数说明

### 4.1 核心框架类

#### 4.1.1 bone-core

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| AbstractEntity | 实体基类 | - | [AbstractEntity.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-core/src/main/java/com/bone/core/domain/entity/AbstractEntity.java) |
| ApiResponse | API响应模型 | success(), error() | [ApiResponse.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-core/src/main/java/com/bone/core/model/ApiResponse.java) |
| BizException | 业务异常 | - | [BizException.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-core/src/main/java/com/bone/core/exception/BizException.java) |
| DistributedIdGenerator | 分布式ID生成器 | generate() | [DistributedIdGenerator.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-core/src/main/java/com/bone/core/util/DistributedIdGenerator.java) |

#### 4.1.2 bone-datasource

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| DataSourceContextHolder | 数据源上下文 | setDataSource(), getDataSource() | [DataSourceContextHolder.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-datasource/src/main/java/com/bone/core/datasource/dynamic/DataSourceContextHolder.java) |
| DynamicDataSource | 动态数据源 | determineCurrentLookupKey() | [DynamicDataSource.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-datasource/src/main/java/com/bone/core/datasource/dynamic/DynamicDataSource.java) |

#### 4.1.3 bone-security

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| JwtTokenProvider | JWT令牌提供者 | generateToken(), validateToken() | [JwtTokenProvider.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-security/src/main/java/com/bone/core/security/auth/JwtTokenProvider.java) |
| PermissionChecker | 权限检查器 | checkPermission() | [PermissionChecker.java](file:///Users/renhui.trh/wps/bone/bone-framework/bone-security/src/main/java/com/bone/core/security/permission/PermissionChecker.java) |

### 4.2 核心引擎类

#### 4.2.1 bone-extension-engine

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| ExtensionPoint | 扩展点注解 | - | [ExtensionPoint.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-extension-engine/bone-extension-sdk/src/main/resources/META-INF/services/com.bone.engine.extension.api.annotation.ExtensionPoint) |

#### 4.2.2 bone-integration

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| CamelRouteController | Camel路由控制器 | createRoute(), updateRoute() | [CamelRouteController.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-integration/src/main/java/com/bone/integration/interfaces/CamelRouteController.java) |
| DynamicRouteManager | 动态路由管理器 | addRoute(), removeRoute() | [DynamicRouteManager.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-integration/src/main/java/com/bone/integration/route/DynamicRouteManager.java) |
| RuleEngine | 规则引擎 | execute() | [RuleEngine.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-integration/src/main/java/com/bone/integration/rule/RuleEngine.java) |

#### 4.2.3 bone-metadata

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| MetadataController | 元数据控制器 | createMetadata(), updateMetadata() | [MetadataController.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata/src/main/java/com/bone/metadata/controller/MetadataController.java) |
| EntityMetadata | 实体元数据模型 | - | [EntityMetadata.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata/src/main/java/org/bone/engine/metadata/model/EntityMetadata.java) |
| MetadataService | 元数据服务 | getMetadata(), saveMetadata() | [MetadataService.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata/src/main/java/org/bone/engine/metadata/service/MetadataService.java) |

#### 4.2.4 bone-metadata-sdk

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| BaseRepository | 基础仓库 | findById(), save() | [BaseRepository.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/BaseRepository.java) |
| Repository | 仓库接口 | - | [Repository.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/Repository.java) |
| SqlBuilder | SQL构建器 | build() | [SqlBuilder.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/query/SqlBuilder.java) |

### 4.3 业务模块类

#### 4.3.1 bone-procurement

| 类名 | 说明 | 关键方法 | 路径 |
|------|------|----------|------|
| PurchaseOrderController | 采购订单控制器 | createOrder(), updateOrder() | [PurchaseOrderController.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-procurement/src/main/java/com/bone/procurement/controller/PurchaseOrderController.java) |
| PurchaseOrder | 采购订单实体 | - | [PurchaseOrder.java](file:///Users/renhui.trh/wps/bone/bone-engine/bone-procurement/src/main/java/com/bone/procurement/entity/PurchaseOrder.java) |

## 5. 依赖关系

### 5.1 模块依赖

```
bone-parent
├── bone-framework
│   ├── bone-core
│   ├── bone-datasource
│   ├── bone-security
│   ├── bone-utils
│   └── bone-web
├── bone-engine
│   ├── bone-extension-engine
│   ├── bone-integration
│   ├── bone-metadata
│   ├── bone-metadata-sdk
│   ├── bone-procurement
│   ├── bone-smartmeta
│   └── bone-workflow
├── bone-platform
├── bone-business
│   ├── bone-admin
│   └── bone-trade
├── bone-sdk
└── bone-tool
```

### 5.2 技术依赖

| 技术 | 版本 | 用途 | 路径 |
|------|------|------|------|
| Spring Boot | 3.2 | 微服务框架 | [pom.xml](file:///Users/renhui.trh/wps/bone/pom.xml) |
| Spring Cloud | 2023 | 服务治理 | [pom.xml](file:///Users/renhui.trh/wps/bone/pom.xml) |
| MyBatis Plus | - | 数据持久化 | [pom.xml](file:///Users/renhui.trh/wps/bone/pom.xml) |
| Redis | 7.2 | 缓存方案 | [pom.xml](file:///Users/renhui.trh/wps/bone/pom.xml) |
| Spring Security | 6.2 | 安全框架 | [pom.xml](file:///Users/renhui.trh/wps/bone/pom.xml) |
| Vue | 3.4 | 前端框架 | [package.json](file:///Users/renhui.trh/wps/bone/bone-frontend/package.json) |
| TypeScript | 5.5 | 类型安全 | [package.json](file:///Users/renhui.trh/wps/bone/bone-frontend/package.json) |

## 6. 项目运行方式

### 6.1 后端运行

```bash
# 1. 克隆项目
git clone https://gitee.com/meishan315/bone.git

# 2. 编译项目（需JDK 17+、Maven 3.8+）
cd bone && mvn clean install

# 3. 启动管理后台
java -jar bone-admin/target/bone-admin.jar
```

### 6.2 前端运行

```bash
# 1. 进入前端目录
cd bone-frontend

# 2. 安装依赖
npm install

# 3. 启动开发服务器
npm run dev
```

### 6.3 访问信息

- 管理后台：[http://localhost:8080](http://localhost:8080)
- 默认账号：`admin` / `123456`（首次登录建议修改密码）
- API文档：[http://localhost:8080/doc.html](http://localhost:8080/doc.html)（基于Swagger生成，支持在线调试）

## 7. 开发指南

### 7.1 核心概念

- **元数据**：描述数据的数据，是Bone平台的核心驱动
- **扩展点**：在核心流程中预设的可定制化接口
- **集成流**：连接外部系统的业务流程
- **主数据**：企业核心业务数据的唯一可信来源

### 7.2 开发流程

1. **定义元数据**：通过元数据引擎定义业务实体和关系
2. **配置业务规则**：使用规则引擎配置业务逻辑
3. **扩展定制**：通过扩展引擎注入个性化逻辑
4. **系统集成**：使用集成引擎连接外部系统
5. **前端生成**：基于元数据自动生成前端代码

### 7.3 最佳实践

- **元数据设计**：优先设计清晰的元数据模型，确保数据结构合理
- **扩展点使用**：合理使用扩展点，避免过度定制导致维护困难
- **集成配置**：使用可视化配置工具编排集成流程，减少代码编写
- **性能优化**：合理使用缓存，优化数据库查询，确保系统性能

## 8. 部署与运维

### 8.1 部署方式

- **单机部署**：适合开发和测试环境
- **集群部署**：适合生产环境，通过负载均衡提高可用性
- **容器化部署**：使用Docker和Kubernetes实现弹性伸缩

### 8.2 监控与告警

- **全链路追踪**：基于SkyWalking实现链路追踪
- **性能监控**：监控系统性能指标，如响应时间、吞吐量
- **异常告警**：配置异常告警机制，及时发现和处理问题

### 8.3 日志管理

- **集中式日志**：使用ELK Stack集中管理日志
- **日志分级**：根据不同环境配置合适的日志级别
- **日志分析**：定期分析日志，发现系统问题和优化机会

## 9. 版本管理

### 9.1 版本号规则

采用语义化版本号：`MAJOR.MINOR.PATCH`

- **MAJOR**：不兼容的API变更
- **MINOR**：向后兼容的功能添加
- **PATCH**：向后兼容的bug修复

### 9.2 发布流程

1. **开发阶段**：在feature分支开发新功能
2. **测试阶段**：在test分支进行测试
3. **发布阶段**：合并到master分支并发布

## 10. 总结与亮点回顾

Bone 平台通过四大核心引擎的协同工作，为企业提供了一套完整的数字化转型解决方案：

- **元数据驱动**：实现了业务与技术的解耦，让业务人员参与模型设计
- **多端适配**：一次配置自动生成多端代码，减少重复开发
- **插件化架构**：通过扩展引擎实现系统的灵活定制
- **标准化集成**：通过集成引擎实现异构系统的高效对接
- **企业级能力**：内置RBAC权限体系、分布式事务、全链路可观测等企业级特性

Bone 平台的设计理念是"构建可复用的系统，创造可持续的价值"，通过标准化和自动化，大幅提升开发效率，降低维护成本，为企业数字化转型提供了强有力的技术支撑。

---

*本文档基于Bone项目的当前状态生成，随着项目的演进，内容可能会有所变化。*