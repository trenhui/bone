
# 🏗️ Bone 企业级开发平台：Java 工程实践规范

> **"构建可复用的系统，创造可持续的价值。"**  
> **—— 梅山**

---

## 🎯 **平台定位：Build Once, Natively Everywhere**

**Bone** 是一个 **企业级全栈开源原生快速开发平台**，以“一次构建、多端运行”为核心理念，通过 **元数据驱动 + 四大核心引擎** 构建企业级应用的完整技术闭环。

- ✅ **100% 开源免费 · 企业级就绪 · 元数据驱动**
- ✅ **四大引擎协同**：智能元数据引擎、扩展引擎、集成引擎、工作流引擎
- ✅ **极致开发效率**：减少70%重复编码，新功能开发周期从2-3周缩短至3天
- ✅ **多端统一架构**：Web、App、小程序一次配置，多端自动生成

---

## 🎯 **平台架构全景图**

### 📁 **整体项目结构**

```bash
bone/
├── 📦 bone-parent/                     # 父工程 - 统一依赖管理
├── 🔧 bone-core/                       # 核心原子层 - 基础能力
├── ⚙️ bone-engine/                     # 引擎驱动层 - 核心框架
├── 🏢 bone-platform/                   # 平台服务层 - 共享中台
├── 🚀 bone-services/                   # 业务服务层 - 具体实现
├── 📦 bone-starters/                   # Starter集成层 - 自动装配
├── 🔌 bone-sdk/                        # 客户端SDK - 对外集成
├── 🛠️ bone-tools/                      # 开发工具集 - DevOps支持
└── 💡 bone-examples/                   # 示例项目 - 快速上手
```

> **依赖方向**：`core → engine → platform → services`，上层依赖下层，禁止循环依赖。

---

## 📋 **Java 包命名规范（最终版）**

### 🎯 **核心原则**

| 原则 | 说明 |
|------|------|
| **域名反转** | `com.bone.{layer}.{module}.{component}` |
| **层次清晰** | 严格分层，禁止跨层依赖 |
| **职责单一** | 每个包聚焦单一功能 |
| **命名即文档** | 包名自描述，降低学习成本 |

---

## 🔧 **详细包结构与命名方案**

### 1. 📦 `bone-parent` - 父工程

```bash
bone-parent/
└── pom.xml  # 统一版本管理、插件配置、依赖BOM
```

- **作用**：定义 `Java 17+`、`Spring Boot 3.2`、`MyBatis Plus` 等版本。
- **模块化管理**：通过 `<modules>` 统一管理所有子模块。

---

### 2. 🔧 `bone-core` - 核心原子层

> **原子化基础设施，零业务依赖，企业级能力基石**

```bash
bone-core/
├── bone-common/                         # 通用工具包
│   └── src/main/java/com/bone/core/common/
│       ├── constant/                    # 全局常量
│       │   ├── CommonConstants.java     # 通用常量
│       │   ├── SystemConstants.java     # 系统常量
│       │   └── DateConstants.java       # 日期常量
│       ├── util/                        # 工具类
│       │   ├── BeanUtil.java            # Bean操作工具
│       │   ├── DateUtil.java            # 日期处理工具
│       │   ├── StringUtil.java          # 字符串工具
│       │   └── ValidatorUtil.java       # 数据校验工具
│       ├── enums/                       # 通用枚举
│       │   ├── ResultCode.java          # 结果状态码
│       │   ├── StatusEnum.java          # 通用状态枚举
│       │   └── YesNoEnum.java           # 布尔状态枚举
│       ├── exception/                   # 异常体系
│       │   ├── BoneException.java       # 平台基础异常
│       │   ├── BusinessException.java   # 业务异常
│       │   ├── SystemException.java     # 系统异常
│       │   └── GlobalExceptionHandler.java # 全局异常处理
│       └── model/                       # 基础模型
│           ├── BaseEntity.java          # 实体类基类
│           ├── BaseDTO.java             # DTO基类
│           ├── PageQuery.java           # 分页查询模型
│           ├── PageResult.java          # 分页结果模型
│           └── Result.java              # 统一响应模型
│
├── bone-datasource/                     # 数据访问基础
│   └── src/main/java/com/bone/core/datasource/
│       ├── config/                      # 数据源配置
│       │   ├── DataSourceConfig.java    # 主数据源配置
│       │   └── DynamicDataSourceConfig.java # 动态数据源配置
│       ├── dynamic/                     # 动态数据源
│       │   ├── DynamicDataSource.java   # 动态数据源实现
│       │   └── DataSourceContextHolder.java # 数据源上下文
│       ├── tenant/                      # 多租户支持
│       │   ├── TenantContext.java       # 租户上下文
│       │   └── TenantInterceptor.java   # 租户拦截器
│       └── mybatis/                     # MyBatis增强
│           ├── BaseMapper.java          # 通用Mapper接口
│           └── MetaObjectHandler.java   # 元对象处理器
│
├── bone-security/                       # 安全基础设施
│   └── src/main/java/com/bone/core/security/
│       ├── auth/                        # 认证核心
│       │   ├── JwtTokenProvider.java    # JWT令牌生成与验证
│       │   └── SecurityContextHolder.java # 安全上下文
│       ├── crypt/                       # 加密算法
│       │   ├── PasswordEncoder.java     # 密码加密器
│       │   └── AesEncryptor.java        # AES加密工具
│       ├── permission/                  # 权限基础
│       │   ├── PermissionChecker.java   # 权限检查器
│       │   └── DataPermissionInterceptor.java # 数据权限拦截器
│       └── filter/                      # 安全过滤器
│           ├── AuthenticationFilter.java # 认证过滤器
│           └── AuthorizationFilter.java # 授权过滤器
│
├── bone-cache/                          # 缓存抽象层
│   └── src/main/java/com/bone/core/cache/
│       ├── redis/                       # Redis实现
│       │   ├── RedisCache.java          # Redis缓存操作
│       │   └── RedisLock.java           # Redis分布式锁
│       ├── caffeine/                    # Caffeine实现
│       │   └── CaffeineCache.java       # Caffeine缓存操作
│       └── multi/                       # 多级缓存
│           └── MultiLevelCache.java     # 多级缓存实现
│
└── bone-observability/                  # 可观测性基础
    └── src/main/java/com/bone/core/observability/
        ├── logging/                     # 日志规范
        │   ├── LogAspect.java           # 日志切面
        │   └── TraceIdFilter.java       # 链路追踪ID过滤器
        ├── metrics/                     # 指标采集
        │   └── MetricsCollector.java    # 指标收集器
        └── tracing/                     # 链路追踪
            └── TraceContext.java        # 追踪上下文
```

---

### 3. ⚙️ `bone-engine` - 引擎驱动层 ★核心★

> **平台"数字大脑"，驱动全生命周期自动化**

```bash
bone-engine/
├── bone-metadata/                       # 元数据引擎
│   └── src/main/java/com/bone/engine/metadata/
│       ├── model/                       # 元数据模型
│       │   ├── EntityMetadata.java      # 实体元数据
│       │   ├── FieldMetadata.java       # 字段元数据
│       │   └── RelationMetadata.java    # 关系元数据
│       ├── repository/                  # 元数据存储
│       │   └── MetadataRepository.java  # 元数据仓库
│       ├── service/                     # 元数据服务
│       │   ├── MetadataService.java     # 元数据管理服务
│       │   └── CodeGenerator.java       # 代码生成服务
│       ├── generator/                   # 代码生成器
│       │   ├── EntityGenerator.java     # 实体生成器
│       │   ├── ServiceGenerator.java    # 服务生成器
│       │   └── ControllerGenerator.java # 控制器生成器
│       └── runtime/                     # 元数据运行时
│           └── MetadataContext.java     # 元数据上下文
│
├── bone-extension/                      # 扩展引擎
│   └── src/main/java/com/bone/engine/extension/
│       ├── point/                       # 扩展点定义
│       │   ├── ExtensionPoint.java      # 扩展点接口
│       │   └── BusinessExtensionPoint.java # 业务扩展点
│       ├── registry/                    # 扩展注册中心
│       │   ├── ExtensionRegistry.java   # 扩展注册中心
│       │   └── SPIExtensionLoader.java  # SPI扩展加载器
│       ├── plugin/                      # 插件管理
│       │   ├── PluginManager.java       # 插件管理器
│       │   └── PluginDescriptor.java    # 插件描述符
│       └── lifecycle/                   # 生命周期管理
│           ├── ExtensionLifecycle.java  # 扩展生命周期
│           └── PluginLifecycle.java     # 插件生命周期
│
├── bone-integration/                    # 集成引擎
│   └── src/main/java/com/bone/engine/integration/
│       ├── connector/                   # 连接器工厂
│       │   ├── ConnectorFactory.java    # 连接器工厂
│       │   ├── HttpConnector.java       # HTTP连接器
│       │   └── DataSourceConnector.java # 数据源连接器
│       ├── transformer/                 # 数据转换器
│       │   ├── DataTransformer.java     # 数据转换器
│       │   └── FieldMappingTransformer.java # 字段映射转换器
│       └── router/                      # 路由编排器
│           ├── IntegrationRouter.java   # 集成路由器
│           └── RouteExecutor.java       # 路由执行器
│
└── bone-workflow/                       # 工作流引擎
    └── src/main/java/com/bone/engine/workflow/
        ├── bpmn/                        # BPMN2.0支持
        │   ├── BpmnEngine.java          # BPMN引擎
        │   └── BpmnParser.java          # BPMN解析器
        ├── state/                       # 状态机引擎
        │   ├── StateMachine.java        # 状态机
        │   └── StateTransition.java     # 状态转换
        └── rule/                        # 规则引擎
            ├── RuleEngine.java          # 规则引擎
            └── RuleParser.java          # 规则解析器
```

---

### 4. 🏢 `bone-platform` - 业务平台层

> **企业级共享服务中台，可独立部署**

```bash
bone-platform/
├── bone-auth-center/                    # 统一认证中心
│   └── src/main/java/com/bone/platform/auth/
│       ├── oauth2/                      # OAuth2.0实现
│       │   ├── OAuth2Service.java       # OAuth2服务
│       │   └── OAuth2TokenStore.java    # 令牌存储
│       ├── sso/                         # 单点登录
│       │   ├── SsoService.java          # SSO服务
│       │   └── SsoTokenManager.java     # SSO令牌管理
│       ├── mfa/                         # 多因素认证
│       │   └── MultiFactorAuthService.java # 多因素认证服务
│       └── controller/                  # 认证控制器
│           └── AuthController.java      # 认证接口
│
├── bone-account-center/                 # 账户中心
│   └── src/main/java/com/bone/platform/account/
│       ├── user/                        # 用户管理
│       │   ├── UserService.java         # 用户服务
│       │   ├── UserRepository.java      # 用户数据访问
│       │   └── UserController.java      # 用户接口
│       ├── role/                        # 角色管理
│       │   ├── RoleService.java         # 角色服务
│       │   └── RoleRepository.java      # 角色数据访问
│       └── permission/                  # 权限管理
│           ├── PermissionService.java   # 权限服务
│           └── PermissionRepository.java # 权限数据访问
│
└── bone-masterdata/                     # 主数据管理
    └── src/main/java/com/bone/platform/masterdata/
        ├── mdm/                         # 主数据模型
        │   ├── MasterDataModel.java     # 主数据模型
        │   └── ModelService.java        # 模型服务
        ├── quality/                     # 数据质量
        │   └── DataQualityService.java  # 数据质量服务
        └── lineage/                     # 数据血缘
            └── DataLineageService.java  # 数据血缘服务
```

---

### 5. 🚀 `bone-services` - 业务服务层

> **具体业务实现，支持微服务化部署**

```bash
bone-services/
├── bone-admin-service/                  # 管理后台服务
│   └── src/main/java/com/bone/services/admin/
│       ├── system/                      # 系统管理
│       │   ├── SystemService.java       # 系统服务
│       │   └── SystemController.java    # 系统管理接口
│       ├── monitor/                     # 监控中心
│       │   ├── MonitorService.java      # 监控服务
│       │   └── MonitorController.java   # 监控接口
│       └── config/                      # 动态配置
│           ├── ConfigService.java       # 配置服务
│           └── ConfigController.java    # 配置管理接口
│
├── bone-api-gateway/                    # API网关服务
│   └── src/main/java/com/bone/services/gateway/
│       ├── route/                       # 路由规则
│       │   ├── RouteLocator.java        # 路由定位器
│       │   └── DynamicRouteService.java # 动态路由服务
│       ├── filter/                      # 过滤器链
│       │   ├── AuthFilter.java          # 认证过滤器
│       │   └── RateLimitFilter.java     # 限流过滤器
│       └── circuit/                     # 熔断降级
│           ├── CircuitBreaker.java      # 熔断器
│           └── FallbackHandler.java     # 降级处理器
│
└── bone-job-service/                    # 任务调度服务
    └── src/main/java/com/bone/services/job/
        ├── quartz/                      # Quartz集成
        │   └── QuartzJobScheduler.java  # Quartz调度器
        ├── elastic/                     # Elastic-Job
        │   └── ElasticJobScheduler.java # Elastic-Job调度器
        └── scheduler/                   # 调度器
            └── JobScheduler.java        # 任务调度器
```

---

### 6. 📦 `bone-starters` - Starter集成层

> **Spring Boot 自动装配，开箱即用**

```bash
bone-starters/
├── bone-spring-boot-starter/            # 核心Starter
│   └── src/main/java/com/bone/starter/
│       ├── config/                      # 自动配置
│       │   ├── BoneAutoConfiguration.java # 自动配置类
│       │   └── BoneProperties.java      # 配置属性
│       └── condition/                   # 条件装配
│           ├── OnClassCondition.java    # 类存在条件
│           └── OnPropertyCondition.java # 属性条件
│
├── bone-metadata-starter/               # 元数据Starter
│   └── src/main/java/com/bone/starter/metadata/
│       └── MetadataAutoConfiguration.java
│
├── bone-extension-starter/              # 扩展Starter
│   └── src/main/java/com/bone/starter/extension/
│       └── ExtensionAutoConfiguration.java
│
└── bone-security-starter/               # 安全Starter
    └── src/main/java/com/bone/starter/security/
        └── SecurityAutoConfiguration.java
```

---

### 7. 🔌 `bone-sdk` - 客户端SDK

> **对外集成，支持多语言、多平台**

```bash
bone-sdk/
├── bone-java-sdk/                       # Java客户端SDK
│   └── src/main/java/com/bone/sdk/
│       ├── client/                      # API客户端
│       │   ├── BoneClient.java          # SDK入口类
│       │   └── BoneClientBuilder.java   # SDK构建器
│       ├── model/                       # SDK数据模型
│       │   ├── SdkRequest.java          # 请求模型
│       │   └── SdkResponse.java         # 响应模型
│       └── config/                      # SDK配置
│           └── SdkConfig.java           # SDK配置类
│
└── bone-openapi-sdk/                    # OpenAPI SDK
    └── src/main/java/com/bone/sdk/openapi/
        ├── OpenApiClient.java           # OpenAPI客户端
        └── OpenApiConfig.java           # OpenAPI配置
```

---

### 8. 🛠️ `bone-tools` - 开发工具集

> **提升开发效率，支持CI/CD**

```bash
bone-tools/
├── bone-cli/                            # 命令行工具
│   └── src/main/java/com/bone/tools/cli/
│       ├── command/                     # 命令定义
│       │   ├── InitCommand.java         # 初始化命令
│       │   ├── GenerateCommand.java     # 生成命令
│       │   └── DeployCommand.java       # 部署命令
│       └── CliApplication.java          # CLI应用入口
│
└── bone-codegen/                        # 代码生成工具
    └── src/main/java/com/bone/tools/codegen/
        ├── generator/                   # 生成器
        │   ├── EntityGenerator.java     # 实体生成器
        │   ├── ServiceGenerator.java    # 服务生成器
        │   └── ControllerGenerator.java # 控制器生成器
        └── template/                    # 模板管理
            └── TemplateManager.java     # 模板管理器
```

---

## 📜 **Java 工程命名规范**

### 1. 包命名规范

```java
// 标准包名示例
com.bone.core.common.util              // 核心通用工具
com.bone.engine.metadata.model         // 引擎元数据模型
com.bone.platform.auth.oauth2          // 平台认证OAuth2
com.bone.services.admin.system         // 服务管理系统
com.bone.starter.metadata.config       // Starter元数据配置
com.bone.sdk.client                    // SDK客户端
com.bone.tools.cli.command             // 工具CLI命令
```

### 2. 类命名规范

| 类型 | 命名模式 | 示例 |
|------|----------|------|
| 实体类 | `XxxEntity` | `UserEntity` |
| DTO | `XxxDTO` | `UserDTO` |
| VO | `XxxVO` | `UserVO` |
| 服务接口 | `XxxService` | `UserService` |
| 服务实现 | `XxxServiceImpl` | `UserServiceImpl` |
| 控制器 | `XxxController` | `UserController` |
| 仓库接口 | `XxxRepository` | `UserRepository` |
| 配置类 | `XxxConfig` | `DataSourceConfig` |
| 工具类 | `XxxUtil` | `StringUtil` |
| 异常类 | `XxxException` | `BusinessException` |
| 枚举类 | `XxxEnum` | `StatusEnum` |
| 常量类 | `XxxConstants` | `SystemConstants` |
| 事件类 | `XxxEvent` | `UserCreatedEvent` |
| 处理器 | `XxxHandler` | `GlobalExceptionHandler` |

### 3. 方法命名规范

| 操作类型 | 命名模式 | 示例 |
|----------|----------|------|
| 查询单个 | `getXxxById` | `getUserById` |
| 查询列表 | `listXxxs` | `listUsers` |
| 分页查询 | `pageXxxs` | `pageUsers` |
| 创建 | `createXxx` | `createUser` |
| 更新 | `updateXxx` | `updateUser` |
| 删除 | `deleteXxx` | `deleteUser` |
| 验证 | `validateXxx` | `validateUser` |
| 业务处理 | `processXxx` | `processOrder` |
| 执行操作 | `executeXxx` | `executeTask` |

### 4. 变量命名规范

- **类成员变量**：小驼峰，如 `userName`, `createTime`
- **静态常量**：全大写，单词间用下划线分隔，如 `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE`
- **方法参数**：小驼峰，如 `userId`, `orderStatus`
- **局部变量**：小驼峰，如 `userList`, `totalCount`

---

## 🛠️ **Maven 配置示例**

### 父工程 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.bone</groupId>
    <artifactId>bone-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Bone Parent</name>
    <description>Bone Enterprise Development Platform - Parent POM</description>

    <modules>
        <module>bone-core</module>
        <module>bone-engine</module>
        <module>bone-platform</module>
        <module>bone-services</module>
        <module>bone-starters</module>
        <module>bone-sdk</module>
        <module>bone-tools</module>
        <module>bone-examples</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        
        <!-- Bone版本 -->
        <bone.version>1.0.0-SNAPSHOT</bone.version>
        
        <!-- 第三方依赖版本 -->
        <spring-boot.version>3.2.0</spring-boot.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <lombok.version>1.18.30</lombok.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- Bone内部模块依赖管理 -->
            <dependency>
                <groupId>com.bone</groupId>
                <artifactId>bone-common</artifactId>
                <version>${bone.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bone</groupId>
                <artifactId>bone-datasource</artifactId>
                <version>${bone.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bone</groupId>
                <artifactId>bone-metadata</artifactId>
                <version>${bone.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                        <encoding>${project.build.sourceEncoding}</encoding>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### 代码风格配置

```ini
# .editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.java]
indent_style = space
indent_size = 4
```

---

## 🎯 **架构优势总结**

| 优势 | 说明 |
|------|------|
| ✅ **清晰分层** | Core → Engine → Platform → Services，职责明确，易于维护 |
| ✅ **严格依赖** | 上层依赖下层，禁止跨层调用，保障架构稳定性 |
| ✅ **命名即文档** | 包名、类名、方法名高度自描述，降低团队协作成本 |
| ✅ **企业级就绪** | 内置多租户、权限、可观测性、分布式事务等能力 |
| ✅ **极致扩展性** | SPI + 插件化 + 元数据驱动，支持业务快速演进 |
| ✅ **开发效率** | Starter自动装配 + 代码生成，提升开发效率80% |

---

## 🚀 **Bone 的愿景：Base of Next Enterprise**

Bone 不只是一个开发平台，更是 **企业级数字化架构的基础设施**。通过四大引擎协同，构建"**数据-质量-功能-生态**"的技术闭环，助力企业实现：

- 📈 **开发效率提升80%**
- 💰 **集成成本降低60%**
- 🔄 **架构灵活性提升300%**

> **"好的架构，让复杂归于简单；好的开源，让价值自由流动。"**  
> **—— 梅山**

**Bone — Build Once, Natively Everywhere**  
**让企业级开发更简单**