> **⚠️ 历史草案（已废止）**  
> Java 包结构与工程组织的**唯一权威**为 [`Bone-DDD-最终实践方案.md`](./Bone-DDD-最终实践方案.md)（§14 包结构）及 [`BONE-总体架构设计方案.md`](./BONE-总体架构设计方案.md)。本文仅作归档参考。

# 🏢 Bone 企业级开发平台：Java 包命名规范（权威最终版）

## 🎯 设计哲学与核心原则

包命名体系是系统架构的具象化表达，遵循**业务驱动、层次清晰、职责单一、命名一致**四大原则，构建可维护、可扩展、易协作的企业级代码组织结构。

```java
/**
 * BONE 包命名核心原则
 * B - Business Domain Centric (业务领域为中心)
 * O - One-way Dependency (单向依赖)
 * N - Naming Consistency (命名一致性)
 * E - Explicit Responsibility (职责明确性)
 */
```

## 🔍 命名基础规范

### 标准命名格式
```java
// 完整格式：com.company.platform.layer[.domain][.subdomain].component
com.bone.framework.common.util                // 框架层通用工具
com.bone.platform.iam.user.domain.model       // 平台层IAM用户领域模型
com.bone.business.order.application.service   // 业务层订单应用服务
```

### 命名风格规则
- **全小写字母**：包名所有字母均为小写
- **单词连接**：多个单词直接连接，不使用下划线或驼峰
- **简洁明确**：每个包名不超过3个单词
- **避免缩写**：除非是广为人知的行业术语（iam, rpc, dto等）
- **业务优先**：业务包名反映业务概念，而非技术实现

## 📦 完整包结构体系

### 1. 框架层 (framework)
提供通用技术能力，与业务无关的基础组件。

```java
com.bone.framework.
├── common/                // 通用核心组件
│   ├── constant/          // 全局常量定义
│   ├── exception/         // 异常体系
│   ├── util/              // 通用工具类
│   ├── model/             // 基础数据模型
│   ├── validator/         // 数据校验
│   └── context/           // 上下文管理
├── web/                   // Web基础组件
│   ├── config/            // Web配置
│   ├── interceptor/       // 请求拦截器
│   ├── filter/            // 过滤器
│   ├── resolver/          // 参数解析器
│   └── advice/            // 控制器增强
├── data/                  // 数据访问基础
│   ├── config/            // 数据配置
│   ├── mapper/            // 基础Mapper
│   ├── transaction/       // 事务管理
│   ├── tenant/            // 多租户支持
│   └── cache/             // 缓存基础
└── security/              // 安全框架
    ├── auth/              // 认证机制
    ├── crypt/             // 加密工具
    ├── permission/        // 权限控制
    └── token/             // 令牌管理
```

### 2. 引擎层 (engine)
平台核心引擎，提供元数据驱动、流程编排等核心能力。

```java
com.bone.engine.
├── metadata/              // 元数据引擎
│   ├── domain/            // 元数据领域模型
│   ├── repository/        // 元数据仓储
│   ├── service/           // 元数据服务
│   ├── builder/           // 元数据构建器
│   └── loader/            // 元数据加载器
├── workflow/              // 工作流引擎
│   ├── definition/        // 流程定义
│   ├── runtime/           // 流程运行时
│   ├── task/              // 任务管理
│   └── listener/          // 流程监听器
├── rule/                  // 规则引擎
│   ├── compiler/          // 规则编译
│   ├── executor/          // 规则执行
│   └── repository/        // 规则仓储
└── extension/             // 扩展引擎
    ├── point/             // 扩展点定义
    ├── registry/          // 扩展注册
    └── loader/            // 扩展加载
```

### 3. 平台层 (platform)
企业级共享服务，为业务层提供基础支撑。

```java
com.bone.platform.
├── iam/                   // 身份权限管理
│   ├── user/              // 用户管理
│   ├── role/              // 角色管理
│   ├── permission/        // 权限管理
│   └── oauth2/            // 认证授权
├── masterdata/            // 主数据管理
│   ├── customer/          // 客户主数据
│   ├── product/           // 产品主数据
│   └── organization/      // 组织主数据
├── notification/          // 通知中心
│   ├── sms/               // 短信通知
│   ├── email/             // 邮件通知
│   └── message/           // 站内消息
└── file/                  // 文件服务
    ├── storage/           // 存储管理
    ├── convert/           // 格式转换
    └── security/          // 文件安全
```

### 4. 业务层 (business)
按领域划分的业务实现，DDD核心载体。

```java
com.bone.business.
├── order/                 // 订单领域
│   ├── domain/            // 领域层
│   │   ├── model/         // 领域模型
│   │   │   ├── aggregate/ // 聚合根
│   │   │   ├── entity/    // 实体
│   │   │   └── value/     // 值对象
│   │   ├── service/       // 领域服务
│   │   ├── event/         // 领域事件
│   │   └── factory/       // 工厂
│   ├── application/       // 应用层
│   │   ├── service/       // 应用服务
│   │   ├── command/       // 命令对象
│   │   ├── query/         // 查询对象
│   │   └── dto/           // 数据传输对象
│   ├── infrastructure/    // 基础设施层
│   │   ├── repository/    // 仓储实现
│   │   ├── mapper/        // 数据映射
│   │   ├── client/        // 外部服务客户端
│   │   └── cache/         // 缓存实现
│   └── interfaces/        // 接口层
│       ├── rest/          // REST接口
│       ├── rpc/           // RPC接口
│       └── event/         // 事件监听
├── payment/               // 支付领域
├── inventory/             // 库存领域
└── customer/              // 客户领域
```

### 5. 应用层 (application)
用户界面与业务编排层，不包含核心业务逻辑。

```java
com.bone.application.
├── admin/                 // 管理后台
│   ├── web/               // Web控制器
│   ├── vo/                // 视图对象
│   ├── config/            // 应用配置
│   └── facade/            // 服务门面
├── portal/                // 门户应用
└── mobile/                // 移动端后端
```

### 6. 网关层 (gateway)
API网关与流量入口。

```java
com.bone.gateway.
├── config/                // 网关配置
├── filter/                // 网关过滤器
├── route/                 // 路由管理
└── handler/               // 处理器
```

### 7. 客户端层 (client)
对外接口与SDK。

```java
com.bone.client.
├── sdk/                   // Java SDK
├── openapi/               // 开放API
└── rpc/                   // RPC客户端
```

### 8. Starter层 (starter)
自动配置模块。

```java
com.bone.starter.
├── common/                // 通用自动配置
├── data/                  // 数据自动配置
├── security/              // 安全自动配置
└── engine/                // 引擎自动配置
```

### 9. 工具层 (tool)
开发与运维工具。

```java
com.bone.tool.
├── codegen/               // 代码生成器
├── cli/                   // 命令行工具
└── validator/             // 代码校验工具
```

## 📌 最佳实践示例

### 领域模型示例
```java
// 订单聚合根
com.bone.business.order.domain.model.aggregate.Order

// 订单项实体
com.bone.business.order.domain.model.entity.OrderItem

// 金额值对象
com.bone.business.order.domain.model.value.MoneyValue
```

### 服务实现示例
```java
// 订单领域服务
com.bone.business.order.domain.service.OrderDomainService

// 订单应用服务
com.bone.business.order.application.service.OrderAppService

// 订单仓储实现
com.bone.business.order.infrastructure.repository.OrderRepositoryImpl
```

### 接口实现示例
```java
// 订单REST接口
com.bone.business.order.interfaces.rest.OrderController

// 用户管理RPC服务
com.bone.platform.iam.user.interfaces.rpc.UserRpcService
```

## 🔒 包依赖约束

1. **单向依赖原则**
   ```
   framework → engine → platform → business → application
                   ↓           ↓
                 starter → client → gateway
   ```

2. **禁止循环依赖**：同一层级模块间不允许循环依赖

3. **包大小控制**：每个包包含5-15个类，超过20个类需拆分

4. **访问控制**：
    - 内部实现类放在`.internal`子包
    - 对外API放在`.api`或`.interfaces`子包

## 📋 实施检查清单

- [ ] 所有包名使用小写字母
- [ ] 包结构反映业务领域
- [ ] 遵循分层架构原则
- [ ] 避免循环依赖
- [ ] 包大小适中
- [ ] 命名一致性检查
- [ ] 内部实现包正确标记

## 🛠️ Maven 多模块配置示例

### 父项目 POM
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.bone</groupId>
    <artifactId>bone-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>bone-framework</module>
        <module>bone-engine</module>
        <module>bone-platform</module>
        <module>bone-business</module>
        <module>bone-application</module>
        <module>bone-gateway</module>
        <module>bone-starter</module>
        <module>bone-client</module>
        <module>bone-tool</module>
    </modules>
</project>
```

### 业务模块 POM 示例
```xml
<project>
    <parent>
        <groupId>com.bone</groupId>
        <artifactId>bone-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>bone-business-order</artifactId>
    <name>Bone Business - Order Management</name>
    
    <dependencies>
        <dependency>
            <groupId>com.bone</groupId>
            <artifactId>bone-platform-iam</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bone</groupId>
            <artifactId>bone-framework-common</artifactId>
        </dependency>
    </dependencies>
</project>
```

> **"好的包结构是系统的骨架，规范的命名是系统的语言。"**

通过严格执行这套命名规范，Bone平台将实现代码的高度一致性和可维护性，降低团队协作成本，提升开发效率，为企业级应用的长期演进奠定坚实基础。