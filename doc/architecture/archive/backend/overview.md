> **⚠️ 历史草案（已废止）**  
> Java 包结构与工程组织的**唯一权威**为 [`Bone-DDD-最终实践方案.md`](./Bone-DDD-最终实践方案.md)（§14 包结构）及 [`BONE-总体架构设计方案.md`](./BONE-总体架构设计方案.md)。本文仅作归档参考。

# 🏗️ Bone 企业级开发平台：Java工程实践

## 🎯 **核心设计理念与架构原则**

### 1.1 BONE 设计哲学
```java
/**
 * BONE 架构核心原则
 * B - Business Domain Driven (业务领域驱动)
 * O - Organized Layered Architecture (组织化分层架构)
 * N - Naming Convention Consistency (命名规范一致性)
 * E - Explicit Module Boundaries (明确模块边界)
 */
```

### 1.2 核心设计原则
- **领域驱动设计**：包结构深度反映业务领域模型
- **分层架构**：严格的分层依赖，单向数据流
- **模块化设计**：高内聚、低耦合的模块划分
- **一致性命名**：统一的命名模式和规范
- **可扩展性**：支持业务和技术的平滑演进

---

## 📐 **架构全景图**

### 2.1 java工程结构
```bash
com.bone
├── framework/          # 框架层 - 技术基础设施与通用组件
├── engine/             # 引擎层 - 平台核心能力与业务引擎  
├── platform/           # 平台层 - 企业级共享服务
├── business/           # 业务层 - 领域业务实现(DDD)
├── gateway/            # 网关层 - 统一入口与流量治理
├── starter/            # Starter层 - 自动配置与便捷集成
├── client/             # 客户端层 - 多端SDK与API
└── tool/               # 工具层 - 开发运维支持
```

### 2.2 依赖流向规范
```java
// ✅ 允许的依赖方向
framework ← engine ← platform ← business
    ↑         ↑         ↑         ↑           
  starter → client → gateway → interfaces

// ❌ 严格禁止的依赖
business → framework    // 业务层不能依赖框架实现
platform → business     // 平台服务不能依赖具体业务
application → domain    // 应用层不能绕过应用服务直接访问领域
```

---

## 🔧 **框架层 (framework) - 技术基础设施**
提供通用技术能力，与业务无关的基础组件。
### 3.1 通用核心组件
```java
com.bone.framework.
├── common/                          # 通用核心
│   ├── constant/                    # 全局常量定义
│   │   ├── CommonConstants.java              # 通用常量
│   │   ├── SystemConstants.java              # 系统常量
│   │   ├── DateConstants.java                # 日期时间常量
│   │   ├── RegexConstants.java               # 正则表达式常量
│   │   ├── CacheConstants.java               # 缓存常量
│   │   └── SecurityConstants.java            # 安全常量
│   ├── util/                        # 工具类库
│   │   ├── BeanUtil.java                     # Bean操作工具
│   │   ├── DateUtil.java                     # 日期处理工具
│   │   ├── StringUtil.java                   # 字符串工具
│   │   ├── ValidateUtil.java                 # 数据校验工具
│   │   ├── JsonUtil.java                     # JSON处理工具
│   │   ├── CollectionUtil.java               # 集合工具
│   │   ├── FileUtil.java                     # 文件操作工具
│   │   └── StreamUtil.java                   # 流处理工具
│   ├── model/                       # 基础数据模型
│   │   ├── BaseEntity.java                   # 实体基类
│   │   ├── BaseDTO.java                      # DTO基类
│   │   ├── BaseVO.java                       # VO基类
│   │   ├── PageQuery.java                    # 分页查询参数
│   │   ├── PageResult.java                   # 分页结果包装
│   │   ├── SortQuery.java                    # 排序参数
│   │   └── Result.java                       # 统一响应结果
│   ├── exception/                   # 异常体系
│   │   ├── BaseException.java                # 基础异常
│   │   ├── BusinessException.java            # 业务异常
│   │   ├── SystemException.java              # 系统异常
│   │   ├── ValidationException.java          # 验证异常
│   │   ├── AuthenticationException.java      # 认证异常
│   │   ├── AuthorizationException.java       # 授权异常
│   │   └── GlobalExceptionHandler.java       # 全局异常处理
│   ├── enums/                       # 通用枚举
│   │   ├── ResultCode.java                   # 结果状态码
│   │   ├── StatusEnum.java                   # 状态枚举
│   │   ├── YesNoEnum.java                    # 是否枚举
│   │   ├── DeleteEnum.java                   # 删除状态枚举
│   │   ├── GenderEnum.java                   # 性别枚举
│   │   └── CommonEnum.java                   # 通用枚举接口
│   ├── context/                     # 上下文管理
│   │   ├── RequestContext.java               # 请求上下文
│   │   ├── UserContext.java                  # 用户上下文
│   │   ├── TenantContext.java                # 租户上下文
│   │   ├── SecurityContext.java              # 安全上下文
│   │   └── TraceContext.java                 # 链路追踪上下文
│   └── validator/                   # 数据校验
│       ├── CustomValidator.java              # 自定义校验器
│       ├── GroupValidator.java               # 分组校验
│       └── ValidationUtil.java               # 校验工具
```

### 3.2 数据源
```java
com.bone.framework.
├── datasource/                            # 数据访问
│   ├── config/                      # 数据源配置
│   │   ├── DataSourceConfig.java             # 主数据源配置
│   │   ├── TransactionConfig.java           # 事务配置
│   │   └── MultipleDataSourceConfig.java    # 多数据源配置
│   ├── dynamic/                     # 动态数据源
│   │   ├── DynamicDataSource.java            # 动态数据源实现
│   │   ├── DataSourceHolder.java             # 数据源持有者
│   │   ├── DynamicDataSourceConfig.java      # 动态数据源配置
│   │   ├── DataSourceSelector.java           # 数据源选择器
│   │   └── DataSourceHealthChecker.java      # 数据源健康检查
│   ├── tenant/                      # 多租户支持
│   │   ├── TenantInterceptor.java            # 租户拦截器
│   │   ├── TenantContext.java                # 租户上下文
│   │   ├── TenantDataSourceRouter.java       # 租户数据源路由
│   │   └── TenantIdHandler.java              # 租户ID处理器
│   └── cache/                       # 数据缓存
│       ├── RedisCache.java                   # Redis缓存实现
│       ├── LocalCache.java                   # 本地缓存实现
│       ├── MultiLevelCache.java              # 多级缓存
│       └── CacheManager.java                 # 缓存管理器
```

### 3.3 Web框架组件
```java
com.bone.framework.
├── web/                             # Web框架
│   ├── config/                      # Web配置
│   │   ├── WebConfig.java                    # Web通用配置
│   │   ├── CorsConfig.java                  # 跨域配置
│   │   ├── SwaggerConfig.java               # API文档配置
│   │   ├── JacksonConfig.java               # JSON序列化配置
│   │   └── MessageConverterConfig.java      # 消息转换器配置
│   ├── interceptor/                 # 拦截器
│   │   ├── LogInterceptor.java              # 日志拦截器
│   │   ├── AuthInterceptor.java             # 认证拦截器
│   │   ├── PermissionInterceptor.java       # 权限拦截器
│   │   └── RateLimitInterceptor.java        # 限流拦截器
│   ├── filter/                      # 过滤器
│   │   ├── TraceFilter.java                 # 链路追踪过滤器
│   │   ├── XssFilter.java                   # XSS过滤过滤器
│   │   └── CharacterEncodingFilter.java     # 字符编码过滤器
│   ├── resolver/                    # 参数解析器
│   │   ├── PageableResolver.java            # 分页参数解析器
│   │   └── SortResolver.java                # 排序参数解析器
│   └── advice/                      # 控制器增强
│       ├── ResponseAdvice.java              # 响应结果增强
│       └── ExceptionAdvice.java             # 异常处理增强
```

### 3.4 安全框架
```java
com.bone.framework.
├── security/                        # 安全框架
│   ├── config/                      # 安全配置
│   │   ├── SecurityConfig.java               # 安全主配置
│   │   ├── JwtConfig.java                    # JWT配置
│   │   └── OAuth2Config.java                 # OAuth2配置
│   ├── auth/                        # 认证核心
│   │   ├── JwtTokenProvider.java             # JWT令牌提供者
│   │   ├── TokenService.java                 # 令牌服务
│   │   └── AuthenticationService.java        # 认证服务
│   ├── permission/                  # 权限管理
│   │   ├── PermissionService.java            # 权限服务
│   │   └── DataPermissionHandler.java        # 数据权限处理器
│   └── filter/                      # 安全过滤器
│       ├── JwtAuthenticationFilter.java      # JWT认证过滤器
│       └── AuthorizationFilter.java          # 授权过滤器
```

---

## ⚙️ **引擎层 (engine) - 平台核心能力**
平台核心引擎，提供元数据驱动、流程编排等核心能力。
### 4.1 元数据引擎
```java
com.bone.engine.
├── metadata/                        # 元数据引擎
│   ├── domain/                      # 领域层
│   │   ├── model/                   # 元数据模型
│   │   │   ├── EntityMetadata.java           # 实体元数据
│   │   │   ├── FieldMetadata.java            # 字段元数据
│   │   │   ├── RelationMetadata.java         # 关系元数据
│   │   │   └── MetadataAggregate.java        # 元数据聚合根
│   │   ├── service/                 # 领域服务
│   │   │   ├── MetadataDomainService.java    # 元数据领域服务
│   │   │   └── ValidationService.java        # 验证服务
│   │   └── repository/              # 仓储接口
│   │       └── MetadataRepository.java       # 元数据仓储
│   ├── application/                 # 应用层
│   │   ├── service/                 # 应用服务
│   │   │   ├── MetadataAppService.java       # 元数据应用服务
│   │   │   └── CodeGeneratorService.java     # 代码生成服务
│   │   ├── command/                 # 命令对象
│   │   │   ├── CreateEntityCommand.java      # 创建实体命令
│   │   │   └── UpdateFieldCommand.java       # 更新字段命令
│   │   └── dto/                     # 数据传输对象
│   │       ├── EntityDTO.java                # 实体DTO
│   │       └── MetadataDTO.java              # 元数据DTO
│   └── interfaces/                  # 接口层
│       └── rest/                    # REST接口
│           └── MetadataController.java       # 元数据控制器
```

### 4.2 工作流引擎
```java
com.bone.engine.
├── workflow/                        # 工作流引擎
│   ├── domain/                      # 领域层
│   │   ├── model/                   # 工作流模型
│   │   │   ├── ProcessDefinition.java        # 流程定义
│   │   │   ├── ProcessInstance.java          # 流程实例
│   │   │   └── TaskInstance.java             # 任务实例
│   │   └── service/                 # 领域服务
│   │       └── WorkflowDomainService.java    # 工作流领域服务
│   ├── application/                 # 应用层
│   │   ├── service/                 # 应用服务
│   │   │   └── ProcessAppService.java        # 流程应用服务
│   │   └── command/                 # 命令对象
│   │       ├── StartProcessCommand.java      # 启动流程命令
│   │       └── CompleteTaskCommand.java      # 完成任务命令
│   └── interfaces/                  # 接口层
│       └── rest/                    # REST接口
│           └── ProcessController.java        # 流程控制器
```

---

## 🏢 **平台层 (platform) - 企业共享服务**
企业级共享服务，为业务层提供基础支撑。
### 5.1 身份权限管理(IAM)
```java
com.bone.platform.
├── iam/                             # 身份权限管理
│   ├── user/                        # 用户管理
│   │   ├── domain/                  # 领域层
│   │   │   ├── model/               # 领域模型
│   │   │   │   ├── User.java                 # 用户聚合根
│   │   │   │   ├── UserProfile.java          # 用户档案实体
│   │   │   │   └── UserId.java               # 用户ID值对象
│   │   │   ├── service/             # 领域服务
│   │   │   │   └── UserDomainService.java    # 用户领域服务
│   │   │   └── repository/          # 仓储接口
│   │   │       └── UserRepository.java       # 用户仓储
│   │   ├── application/             # 应用层
│   │   │   ├── service/             # 应用服务
│   │   │   │   └── UserAppService.java       # 用户应用服务
│   │   │   ├── command/             # 命令对象
│   │   │   │   ├── CreateUserCommand.java    # 创建用户命令
│   │   │   │   └── UpdateUserCommand.java    # 更新用户命令
│   │   │   └── dto/                 # 数据传输对象
│   │   │       ├── UserDTO.java              # 用户DTO
│   │   │       └── UserDetailVO.java         # 用户详情VO
│   │   └── interfaces/              # 接口层
│   │       └── rest/                # REST接口
│   │           └── UserController.java       # 用户控制器
│   ├── role/                        # 角色管理
│   │   ├── domain/                  # 领域层
│   │   │   ├── model/               # 领域模型
│   │   │   │   └── Role.java                 # 角色实体
│   │   │   └── service/             # 领域服务
│   │   │       └── RoleDomainService.java    # 角色领域服务
│   │   └── application/             # 应用层
│   │       └── service/             # 应用服务
│   │           └── RoleAppService.java       # 角色应用服务
│   └── permission/                  # 权限管理
│       ├── domain/                  # 领域层
│       │   ├── model/               # 领域模型
│       │   │   └── Permission.java           # 权限实体
│       │   └── service/             # 领域服务
│       │       └── PermissionDomainService.java # 权限领域服务
│       └── application/             # 应用层
│           └── service/             # 应用服务
│               └── PermissionAppService.java # 权限应用服务

```

### 5.2 主数据服务
```java
com.bone.platform.
├── masterdata/                       # 主数据管理
│   ├── domain/                       # 领域层
│   │   ├── model/                    # 领域模型
│   │   │   ├── Dictionary.java                # 数据字典
│   │   │   └── DictionaryItem.java            # 字典项
│   │   └── service/                  # 领域服务
│   │       └── MasterDataDomainService.java  # 主数据领域服务
│   ├── application/                  # 应用层
│   │   ├── service/                  # 应用服务
│   │   │   └── DictionaryAppService.java     # 字典应用服务
│   │   └── command/                  # 命令对象
│   │       └── CreateDictionaryCommand.java  # 创建字典命令
│   └── interfaces/                   # 接口层
│       └── rest/                     # REST接口
│           └── DictionaryController.java     # 字典控制器
```
### 5.3 通知服务
```java
com.bone.platform.
├── notification/          // 通知中心
│   ├── sms/               // 短信通知
│   ├── email/             // 邮件通知
│   └── message/           // 站内消息

```

### 5.4 文件服务
```java
com.bone.platform.
├── file/               // 文件服务
    ├── storage/           // 存储管理
    ├── convert/           // 格式转换
    └── security/          // 文件安全
```
---

## 🚀 **业务层 (business) - 领域驱动实现**
按领域划分的业务实现，DDD核心载体。
### 6.1 订单领域（完整DDD示例）
```java
com.bone.business.
├── order/                           # 订单领域
│   ├── domain/                      # 领域层
│   │   ├── model/                   # 领域模型
│   │   │   ├── aggregate/           # 聚合根
│   │   │   │   ├── Order.java                # 订单聚合根
│   │   │   │   └── OrderFactory.java         # 订单工厂
│   │   │   ├── entity/              # 实体
│   │   │   │   ├── OrderItem.java            # 订单项实体
│   │   │   │   └── OrderAddress.java         # 订单地址实体
│   │   │   └── value/               # 值对象
│   │   │       ├── OrderNo.java              # 订单号值对象
│   │   │       ├── OrderStatus.java          # 订单状态值对象
│   │   │       └── MoneyValue.java           # 金额值对象
│   │   ├── service/                 # 领域服务
│   │   │   ├── OrderDomainService.java       # 订单领域服务
│   │   │   ├── PricingService.java           # 定价服务
│   │   │   └── InventoryService.java         # 库存服务
│   │   ├── repository/              # 仓储接口
│   │   │   └── OrderRepository.java          # 订单仓储
│   │   └── event/                   # 领域事件
│   │       ├── OrderCreatedEvent.java        # 订单创建事件
│   │       ├── OrderPaidEvent.java           # 订单支付事件
│   │       └── OrderCompletedEvent.java      # 订单完成事件
│   ├── application/                 # 应用层
│   │   ├── service/                 # 应用服务
│   │   │   ├── OrderAppService.java          # 订单应用服务
│   │   │   └── OrderQueryService.java        # 订单查询服务
│   │   ├── command/                 # 命令对象
│   │   │   ├── CreateOrderCommand.java       # 创建订单命令
│   │   │   ├── CancelOrderCommand.java       # 取消订单命令
│   │   │   └── PayOrderCommand.java          # 支付订单命令
│   │   ├── query/                   # 查询对象
│   │   │   └── OrderQuery.java               # 订单查询
│   │   └── dto/                     # 数据传输对象
│   │       ├── OrderDTO.java                 # 订单DTO
│   │       ├── OrderCreateVO.java            # 订单创建VO
│   │       └── OrderDetailVO.java            # 订单详情VO
│   ├── infrastructure/              # 基础设施层
│   │   ├── repository/              # 仓储实现
│   │   │   └── OrderRepositoryImpl.java      # 订单仓储实现
│   │   ├── persistence/             # 持久化
│   │   │   └── OrderMapper.java              # 订单Mapper
│   │   └── client/                  # 客户端
│   │       ├── ProductClient.java            # 商品服务客户端
│   │       └── PaymentClient.java            # 支付服务客户端
│   └── interfaces/                  # 接口层
│       ├── rest/                    # REST接口
│       │   └── OrderController.java          # 订单控制器
│       └── event/                   # 事件监听
│           └── PaymentSuccessEventListener.java # 支付成功事件监听
```

### 7. 网关层 (gateway)
API网关与流量入口。
```java
com.bone.gateway.
├── config/                // 网关配置
├── filter/                // 网关过滤器
├── route/                 // 路由管理
└── handler/               // 处理器
```

### 8. 客户端层 (client)
对外接口与SDK。

```java
com.bone.client.
├── sdk/                   // Java SDK
├── openapi/               // 开放API
└── rpc/                   // RPC客户端
```

### 9. Starter层 (starter)
自动配置模块。

```java
com.bone.starter.
├── common/                // 通用自动配置
├── data/                  // 数据自动配置
├── security/              // 安全自动配置
└── engine/                // 引擎自动配置
```

### 10. 工具层 (tool)
开发与运维工具。

```java
com.bone.tool.
├── codegen/               // 代码生成器
├── cli/                   // 命令行工具
└── validator/             // 代码校验工具
```

---

## 🎯 **包命名最佳实践与规范**

### 11.1 核心命名规则矩阵
| 规则类别 | 规范要求 | 良好示例 | 不良示例 |
|---------|---------|----------|----------|
| **域名规范** | 反向域名，全小写 | `com.bone.framework` | `Bone.Framework` |
| **层次结构** | 分层明确，业务优先 | `business.order.domain` | `order.business.domain` |
| **职责单一** | 每个包单一职责 | `.service`, `.repository` | `.service.repository` |
| **命名一致** | 相同概念统一命名 | `*Repository`, `*Service` | `*Repo`, `*Svc` |
| **避免缩写** | 使用完整单词 | `Configuration` | `Config` |

### 11.2 包依赖治理规范
```java
// ✅ 允许的依赖流向
framework → engine → platform → business → application
    ↑         ↑         ↑         ↑           ↑
  starter → client → gateway → interfaces → facade

// ❌ 严格禁止的循环依赖
// 1. 横向循环依赖
business.order → business.product  // 禁止
platform.iam → platform.masterdata // 禁止

// 2. 逆向依赖
business → framework               // 禁止
application → domain               // 禁止

// 3. 跨层直接依赖
interfaces → infrastructure        // 禁止
application → persistence          // 禁止
```

### 11.3 特殊包命名约定
```java
// 内部实现包（对外隐藏）
.internal.*                      // 内部实现细节
.impl.*                          // 接口实现类

// 技术特定实现
.jdbc.*                          // JDBC相关实现
.redis.*                         // Redis相关实现
.kafka.*                         // Kafka相关实现

// 适配器与扩展
.adapter.*                       // 适配器模式实现
.extension.*                     // 扩展点实现
.spi.*                           // 服务提供者接口
```

### 11.4 包大小与复杂度控制
- **适中规模**：每个包5-15个类文件
- **功能内聚**：相关功能集中同一包
- **拆分阈值**：超过20个类考虑拆分
- **合并条件**：少于3个类考虑合并
- **深度控制**：包层级不超过5层

---

## 📋 **实施指南与检查清单**

### 12.1 开发团队检查清单
- [ ] 所有包名使用全小写字母
- [ ] 包结构准确反映业务领域
- [ ] 严格遵循分层架构原则
- [ ] 无循环依赖和逆向依赖
- [ ] 包大小适中，功能内聚
- [ ] 命名一致性检查通过
- [ ] 内部实现包正确标记
- [ ] 依赖关系单向流动

### 12.2 代码审查要点
```java
// ✅ 符合规范的示例
com.bone.business.order.application.service.OrderAppService
com.bone.platform.iam.user.domain.model.User
com.bone.framework.common.util.StringUtil

// ❌ 需要重构的示例
com.bone.OrderService                   // 缺少层次
com.bone.business.service.OrderServiceImpl  // 技术实现暴露
com.bone.framework.utils.StringUtils    // 命名不一致
```

### 12.3 Maven多模块配置
```xml
<!-- 父项目POM -->
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.bone</groupId>
    <artifactId>bone-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>bone-framework-common</module>
        <module>bone-framework-data</module>
        <module>bone-framework-web</module>
        <module>bone-framework-security</module>
        <module>bone-engine-metadata</module>
        <module>bone-engine-workflow</module>
        <module>bone-platform-iam</module>
        <module>bone-platform-masterdata</module>
        <module>bone-business-order</module>
        <module>bone-business-product</module>
        <module>bone-business-admin</module>
        <module>bone-gateway</module>
        <module>bone-starter</module>
        <module>bone-client-sdk</module>
        <module>bone-codegen</module>
    </modules>
</project>
```

### 12.4 持续集成检查
```yaml
# CI/CD流水线中的包规范检查
- name: Check Package Naming
  run: |
    # 检查包名格式
    ./scripts/check-package-naming.sh
    # 检查循环依赖
    ./scripts/check-dependency-cycle.sh
    # 检查命名一致性
    ./scripts/check-naming-consistency.sh
```

---

> **"优秀的包结构是系统架构的骨架，规范的命名是团队协作的语言。Bone平台的包命名规范，让每一行代码都讲述清晰的业务故事。"**

**Bone — 构建清晰、规范、可演进的企业级Java应用架构**
基于DDD设计理念和分层架构原则，结合业界最佳实践，为企业级Java项目开发提供了从技术基础设施到业务领域实现的完整指导方案。