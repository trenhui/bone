# 🏗️ Bone 企业级开发平台：Java 工程实践

## 🎯 **核心设计理念**

### 1.1 包命名哲学
```java
/**
 * BONE 包命名原则
 * B - Business Domain First (业务领域优先)
 * O - Organized Hierarchy (组织化层次)
 * N - Naming Consistency (命名一致性) 
 * E - Explicit Meaning (明确含义)
 */
```

### 1.2 设计原则
- **领域驱动**：包结构反映业务领域，而非技术实现
- **分层架构**：严格分层，单向依赖，禁止循环依赖
- **单一职责**：每个包聚焦单一功能或业务概念
- **命名一致**：相同概念使用相同命名模式
- **可扩展性**：支持模块化扩展和业务演进

---

## 📁 **平台架构全景图**

### 2.1 整体项目结构
```bash
com.bone
├── framework/           # 框架层 - 技术基础设施
├── engine/             # 引擎层 - 平台核心能力  
├── platform/           # 平台层 - 企业共享服务
├── business/           # 业务层 - 领域业务实现
├── gateway/            # 网关层 - 流量入口
├── starter/            # Starter层 - 自动配置
├── client/             # 客户端层 - SDK集成
└── tool/               # 工具层 - 开发支持
```

---

## 🔧 **框架层 (framework) **  
提供通用技术能力，与业务无关的基础组件。
### 3.1 通用核心包结构
```java
com.bone.framework.
├── common/                          # 通用核心组件
│   ├── constant/                    # 全局常量
│   │   ├── CommonConstants.java              # 通用常量
│   │   ├── SystemConstants.java              # 系统常量
│   │   ├── DateConstants.java                # 日期常量
│   │   ├── RegexConstants.java               # 正则常量
│   │   ├── CacheConstants.java               # 缓存常量
│   │   └── SecurityConstants.java            # 安全常量
│   ├── util/                        # 工具类
│   │   ├── BeanUtil.java                     # Bean操作工具
│   │   ├── DateUtil.java                     # 日期处理工具
│   │   ├── StringUtil.java                   # 字符串工具
│   │   ├── ValidateUtil.java                 # 数据校验工具
│   │   ├── JsonUtil.java                     # JSON处理工具
│   │   ├── CollectionUtil.java               # 集合工具
│   │   ├── FileUtil.java                     # 文件操作工具
│   │   └── StreamUtil.java                   # 流处理工具
│   ├── model/                       # 基础模型
│   │   ├── BaseEntity.java                   # 实体基类
│   │   ├── BaseDTO.java                      # DTO基类
│   │   ├── BaseVO.java                       # VO基类
│   │   ├── PageQuery.java                    # 分页查询参数
│   │   ├── PageResult.java                   # 分页结果
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
│   ├── context/                     # 上下文
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

### 3.2 数据访问包结构
```java
com.bone.framework.
├── data/                            # 数据访问框架
│   ├── config/                      # 数据源配置
│   │   ├── DataSourceConfig.java             # 主数据源配置
│   │   ├── MybatisConfig.java               # MyBatis配置
│   │   ├── TransactionConfig.java           # 事务配置
│   │   ├── JpaConfig.java                   # JPA配置
│   │   └── MultipleDataSourceConfig.java    # 多数据源配置
│   ├── mybatis/                     # MyBatis增强
│   │   ├── BaseMapper.java                   # 通用Mapper接口
│   │   ├── ExtendMapper.java                 # 扩展Mapper接口
│   │   ├── BatchMapper.java                  # 批量操作Mapper
│   │   ├── MetaObjectHandler.java            # 元对象处理器
│   │   ├── PaginationInterceptor.java        # 分页拦截器
│   │   └── DataPermissionInterceptor.java    # 数据权限拦截器
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
│   │   ├── TenantIdHandler.java              # 租户ID处理器
│   │   └── TenantSchemaManager.java          # 租户Schema管理器
│   ├── audit/                       # 数据审计
│   │   ├── AuditAspect.java                  # 审计切面
│   │   ├── AuditListener.java                # 审计监听器
│   │   ├── AuditLog.java                     # 审计日志实体
│   │   ├── AuditService.java                 # 审计服务
│   │   └── AuditAutoConfiguration.java       # 审计自动配置
│   └── cache/                       # 数据缓存
│       ├── RedisCache.java                   # Redis缓存实现
│       ├── LocalCache.java                   # 本地缓存实现
│       ├── MultiLevelCache.java              # 多级缓存
│       ├── CacheManager.java                 # 缓存管理器
│       └── CacheConfig.java                  # 缓存配置
```

### 3.3 Web框架包结构
```java
com.bone.framework.
├── web/                             # Web框架组件
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
│   │   ├── TenantInterceptor.java           # 租户拦截器
│   │   └── RateLimitInterceptor.java        # 限流拦截器
│   ├── filter/                      # 过滤器
│   │   ├── TraceFilter.java                 # 链路追踪过滤器
│   │   ├── XssFilter.java                   # XSS过滤过滤器
│   │   ├── RequestWrapper.java              # 请求包装器
│   │   ├── ResponseWrapper.java             # 响应包装器
│   │   └── CharacterEncodingFilter.java     # 字符编码过滤器
│   ├── resolver/                    # 参数解析器
│   │   ├── PageableResolver.java            # 分页参数解析器
│   │   ├── SortResolver.java                # 排序参数解析器
│   │   └── CustomArgumentResolver.java      # 自定义参数解析器
│   ├── advice/                      # 控制器增强
│   │   ├── ResponseAdvice.java              # 响应结果增强
│   │   ├── ExceptionAdvice.java             # 异常处理增强
│   │   └── ValidatorAdvice.java             # 参数校验增强
│   └── converter/                   # 类型转换器
│       ├── DateConverter.java               # 日期转换器
│       ├── EnumConverter.java               # 枚举转换器
│       └── CustomConverter.java             # 自定义转换器
```

### 3.4 安全框架包结构
```java
com.bone.framework.
├── security/                        # 安全框架
│   ├── config/                      # 安全配置
│   │   ├── SecurityConfig.java               # 安全主配置
│   │   ├── JwtConfig.java                    # JWT配置
│   │   ├── OAuth2Config.java                 # OAuth2配置
│   │   ├── CorsSecurityConfig.java           # 安全跨域配置
│   │   └── MethodSecurityConfig.java         # 方法安全配置
│   ├── auth/                        # 认证核心
│   │   ├── JwtTokenProvider.java             # JWT令牌提供者
│   │   ├── TokenService.java                 # 令牌服务
│   │   ├── AuthenticationService.java        # 认证服务
│   │   ├── LoginService.java                 # 登录服务
│   │   ├── LogoutService.java                # 登出服务
│   │   └── TokenStore.java                   # 令牌存储
│   ├── crypt/                       # 加密算法
│   │   ├── PasswordEncoder.java              # 密码编码器
│   │   ├── AesEncryptor.java                 # AES加密器
│   │   ├── RsaEncryptor.java                 # RSA加密器
│   │   ├── HashUtil.java                     # 哈希工具
│   │   └── KeyGenerator.java                 # 密钥生成器
│   ├── permission/                  # 权限管理
│   │   ├── PermissionService.java            # 权限服务
│   │   ├── DataPermissionHandler.java        # 数据权限处理器
│   │   ├── PermissionExpression.java         # 权限表达式
│   │   ├── RolePermissionService.java        # 角色权限服务
│   │   └── PermissionCache.java              # 权限缓存
│   ├── filter/                      # 安全过滤器
│   │   ├── JwtAuthenticationFilter.java      # JWT认证过滤器
│   │   ├── AuthorizationFilter.java          # 授权过滤器
│   │   ├── SecurityContextFilter.java        # 安全上下文过滤器
│   │   ├── CsrfFilter.java                   # CSRF防护过滤器
│   │   └── SecurityExceptionFilter.java      # 安全异常过滤器
│   └── context/                     # 安全上下文
│       ├── SecurityContextHolder.java        # 安全上下文持有者
│       ├── UserPrincipal.java                # 用户主体
│       ├── Authentication.java               # 认证信息
│       ├── GrantedAuthority.java             # 授予权限
│       └── SecurityUtils.java                # 安全工具类
```

---

## ⚙️ **引擎层 (engine) **
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
│   │   │   ├── MetadataAggregate.java        # 元数据聚合根
│   │   │   ├── MetadataId.java               # 元数据ID值对象
│   │   │   └── MetadataVersion.java          # 元数据版本值对象
│   │   ├── service/                 # 领域服务
│   │   │   ├── MetadataDomainService.java    # 元数据领域服务
│   │   │   ├── ValidationService.java        # 验证服务
│   │   │   ├── MetadataLifecycleService.java # 元数据生命周期服务
│   │   │   └── MetadataQueryService.java     # 元数据查询服务
│   │   ├── factory/                 # 工厂类
│   │   │   ├── MetadataFactory.java          # 元数据工厂
│   │   │   ├── EntityFactory.java            # 实体工厂
│   │   │   └── FieldFactory.java             # 字段工厂
│   │   ├── repository/              # 仓储接口
│   │   │   ├── MetadataRepository.java       # 元数据仓储
│   │   │   ├── EntityRepository.java         # 实体仓储
│   │   │   └── FieldRepository.java          # 字段仓储
│   │   └── event/                   # 领域事件
│   │       ├── MetadataCreatedEvent.java     # 元数据创建事件
│   │       ├── MetadataUpdatedEvent.java     # 元数据更新事件
│   │       ├── MetadataDeletedEvent.java     # 元数据删除事件
│   │       └── MetadataEventHandler.java     # 元数据事件处理器
│   ├── application/                 # 应用层
│   │   ├── service/                 # 应用服务
│   │   │   ├── MetadataAppService.java       # 元数据应用服务
│   │   │   ├── CodeGeneratorService.java     # 代码生成服务
│   │   │   ├── SchemaSyncService.java        # 模式同步服务
│   │   │   └── MetadataImportService.java    # 元数据导入服务
│   │   ├── command/                 # 命令对象
│   │   │   ├── CreateEntityCommand.java      # 创建实体命令
│   │   │   ├── UpdateFieldCommand.java       # 更新字段命令
│   │   │   ├── GenerateCodeCommand.java      # 生成代码命令
│   │   │   ├── SyncSchemaCommand.java        # 同步模式命令
│   │   │   └── ImportMetadataCommand.java    # 导入元数据命令
│   │   ├── query/                   # 查询对象
│   │   │   ├── EntityQuery.java              # 实体查询
│   │   │   ├── FieldQuery.java               # 字段查询
│   │   │   ├── MetadataQuery.java            # 元数据查询
│   │   │   └── SchemaQuery.java              # 模式查询
│   │   └── dto/                     # 数据传输对象
│   │       ├── EntityDTO.java                # 实体DTO
│   │       ├── FieldDTO.java                 # 字段DTO
│   │       ├── MetadataDTO.java              # 元数据DTO
│   │       ├── CodeGenResultDTO.java         # 代码生成结果DTO
│   │       └── SchemaSyncResultDTO.java      # 模式同步结果DTO
│   ├── infrastructure/              # 基础设施层
│   │   ├── repository/              # 仓储实现
│   │   │   ├── MetadataRepositoryImpl.java   # 元数据仓储实现
│   │   │   ├── EntityRepositoryImpl.java     # 实体仓储实现
│   │   │   └── FieldRepositoryImpl.java      # 字段仓储实现
│   │   ├── persistence/             # 持久化
│   │   │   ├── EntityMetadataMapper.java     # 实体元数据Mapper
│   │   │   ├── FieldMetadataMapper.java      # 字段元数据Mapper
│   │   │   ├── RelationMetadataMapper.java   # 关系元数据Mapper
│   │   │   └── MetadataHistoryMapper.java    # 元数据历史Mapper
│   │   ├── client/                  # 客户端
│   │   │   ├── DatabaseClient.java           # 数据库客户端
│   │   │   ├── FileSystemClient.java         # 文件系统客户端
│   │   │   └── GitClient.java                # Git客户端
│   │   └── cache/                   # 缓存
│   │       ├── MetadataCache.java            # 元数据缓存
│   │       ├── EntityCache.java              # 实体缓存
│   │       └── SchemaCache.java              # 模式缓存
│   └── interfaces/                  # 接口层
│       ├── rest/                    # REST接口
│       │   ├── MetadataController.java       # 元数据控制器
│       │   ├── EntityController.java         # 实体控制器
│       │   ├── FieldController.java          # 字段控制器
│       │   └── SchemaController.java         # 模式控制器
│       ├── rpc/                     # RPC接口
│       │   ├── MetadataRpcService.java       # 元数据RPC服务
│       │   └── SchemaRpcService.java         # 模式RPC服务
│       └── event/                   # 事件监听
│           ├── ApplicationStartupListener.java # 应用启动监听器
│           └── DatabaseChangeListener.java   # 数据库变更监听器
```

### 4.2 工作流引擎
```java
com.bone.engine.
├── workflow/                        # 工作流引擎
│   ├── domain/                      # 领域层
│   │   ├── model/                   # 工作流模型
│   │   │   ├── ProcessDefinition.java        # 流程定义
│   │   │   ├── ProcessInstance.java          # 流程实例
│   │   │   ├── TaskInstance.java             # 任务实例
│   │   │   ├── WorkflowNode.java             # 工作流节点
│   │   │   └── WorkflowTransition.java       # 工作流转
│   │   └── service/                 # 领域服务
│   │       ├── WorkflowDomainService.java    # 工作流领域服务
│   │       └── TaskDomainService.java        # 任务领域服务
│   ├── application/                 # 应用层
│   │   ├── service/                 # 应用服务
│   │   │   ├── ProcessAppService.java        # 流程应用服务
│   │   │   └── TaskAppService.java           # 任务应用服务
│   │   └── command/                 # 命令对象
│   │       ├── StartProcessCommand.java      # 启动流程命令
│   │       ├── CompleteTaskCommand.java      # 完成任务命令
│   │       └── SuspendProcessCommand.java    # 挂起流程命令
│   └── interfaces/                  # 接口层
│       └── rest/                    # REST接口
│           ├── ProcessController.java        # 流程控制器
│           └── TaskController.java           # 任务控制器
```

---

## 🏢 **平台层 (platform) **
企业级共享服务，为业务层提供基础支撑。
### 5.1 IAM服务
```java
com.bone.platform.
├── iam/                             # 身份权限管理
│   ├── user/                        # 用户管理
│   │   ├── domain/                  # 领域层
│   │   │   ├── model/               # 领域模型
│   │   │   │   ├── User.java                 # 用户聚合根
│   │   │   │   ├── UserProfile.java          # 用户档案实体
│   │   │   │   ├── UserId.java               # 用户ID值对象
│   │   │   │   ├── Username.java             # 用户名值对象
│   │   │   │   ├── Email.java                # 邮箱值对象
│   │   │   │   ├── Phone.java                # 手机号值对象
│   │   │   │   └── Password.java             # 密码值对象
│   │   │   ├── service/             # 领域服务
│   │   │   │   ├── UserDomainService.java    # 用户领域服务
│   │   │   │   ├── UserValidationService.java # 用户验证服务
│   │   │   │   └── UserSecurityService.java  # 用户安全服务
│   │   │   ├── factory/             # 工厂类
│   │   │   │   └── UserFactory.java          # 用户工厂
│   │   │   ├── repository/          # 仓储接口
│   │   │   │   └── UserRepository.java       # 用户仓储
│   │   │   └── event/               # 领域事件
│   │   │       ├── UserCreatedEvent.java     # 用户创建事件
│   │   │       ├── UserUpdatedEvent.java     # 用户更新事件
│   │   │       ├── UserDeletedEvent.java     # 用户删除事件
│   │   │       └── PasswordChangedEvent.java # 密码修改事件
│   │   ├── application/             # 应用层
│   │   │   ├── service/             # 应用服务
│   │   │   │   ├── UserAppService.java       # 用户应用服务
│   │   │   │   ├── UserProfileService.java   # 用户档案服务
│   │   │   │   └── UserSecurityAppService.java # 用户安全应用服务
│   │   │   ├── command/             # 命令对象
│   │   │   │   ├── CreateUserCommand.java    # 创建用户命令
│   │   │   │   ├── UpdateUserCommand.java    # 更新用户命令
│   │   │   │   ├── ChangePasswordCommand.java # 修改密码命令
│   │   │   │   ├── ResetPasswordCommand.java # 重置密码命令
│   │   │   │   └── UpdateProfileCommand.java # 更新档案命令
│   │   │   ├── query/               # 查询对象
│   │   │   │   ├── UserQuery.java            # 用户查询
│   │   │   │   ├── UserProfileQuery.java     # 用户档案查询
│   │   │   │   └── UserSearchQuery.java      # 用户搜索查询
│   │   │   └── dto/                 # 数据传输对象
│   │   │       ├── UserDTO.java              # 用户DTO
│   │   │       ├── UserProfileDTO.java       # 用户档案DTO
│   │   │       ├── UserCreateVO.java         # 用户创建VO
│   │   │       ├── UserDetailVO.java         # 用户详情VO
│   │   │       └── UserSimpleVO.java         # 用户简略VO
│   │   ├── infrastructure/          # 基础设施层
│   │   │   ├── repository/          # 仓储实现
│   │   │   │   └── UserRepositoryImpl.java   # 用户仓储实现
│   │   │   ├── persistence/         # 持久化
│   │   │   │   ├── UserMapper.java           # 用户Mapper
│   │   │   │   └── UserProfileMapper.java    # 用户档案Mapper
│   │   │   └── cache/               # 缓存
│   │   │       └── UserCache.java            # 用户缓存
│   │   └── interfaces/              # 接口层
│   │       ├── rest/                # REST接口
│   │       │   ├── UserController.java       # 用户控制器
│   │       │   └── UserProfileController.java # 用户档案控制器
│   │       ├── rpc/                 # RPC接口
│   │       │   └── UserRpcService.java       # 用户RPC服务
│   │       └── event/               # 事件监听
│   │           └── UserEventListener.java   # 用户事件监听器
│   ├── role/                        # 角色管理
│   │   ├── domain/                  # 领域层
│   │   │   ├── model/               # 领域模型
│   │   │   │   ├── Role.java                 # 角色实体
│   │   │   │   └── RoleId.java               # 角色ID值对象
│   │   │   └── service/             # 领域服务
│   │   │       └── RoleDomainService.java    # 角色领域服务
│   │   └── application/             # 应用层
│   │       ├── service/             # 应用服务
│   │       │   └── RoleAppService.java       # 角色应用服务
│   │       └── command/             # 命令对象
│   │           ├── CreateRoleCommand.java    # 创建角色命令
│   │           └── AssignRoleCommand.java    # 分配角色命令
│   ├── permission/                  # 权限管理
│   │   ├── domain/                  # 领域层
│   │   │   ├── model/               # 领域模型
│   │   │   │   ├── Permission.java           # 权限实体
│   │   │   │   └── PermissionCode.java       # 权限编码值对象
│   │   │   └── service/             # 领域服务
│   │   │       └── PermissionDomainService.java # 权限领域服务
│   │   └── application/             # 应用层
│   │       └── service/             # 应用服务
│   │           └── PermissionAppService.java # 权限应用服务
│   └── oauth2/                      # OAuth2认证
│       ├── service/                 # 服务层
│       │   ├── OAuth2Service.java            # OAuth2服务
│       │   └── ClientDetailsService.java     # 客户端详情服务
│       └── config/                  # 配置层
│           └── OAuth2ServerConfig.java       # OAuth2服务端配置
```

### 5.2 主数据服务
```java
com.bone.platform.
├── masterdata/                       # 主数据管理
│   ├── domain/                       # 领域层
│   │   ├── model/                    # 领域模型
│   │   │   ├── Dictionary.java                # 数据字典
│   │   │   ├── DictionaryItem.java            # 字典项
│   │   │   └── Region.java                    # 行政区划
│   │   └── service/                  # 领域服务
│   │       └── MasterDataDomainService.java  # 主数据领域服务
│   ├── application/                  # 应用层
│   │   ├── service/                  # 应用服务
│   │   │   ├── DictionaryAppService.java     # 字典应用服务
│   │   │   └── RegionAppService.java         # 区域应用服务
│   │   └── command/                  # 命令对象
│   │       ├── CreateDictionaryCommand.java  # 创建字典命令
│   │       └── UpdateDictionaryCommand.java  # 更新字典命令
│   └── interfaces/                   # 接口层
│       └── rest/                     # REST接口
│           ├── DictionaryController.java     # 字典控制器
│           └── RegionController.java         # 区域控制器
```

---

## 🚀 **业务层 (business) **
按领域划分的业务实现，DDD 核心载体。
### 6.1 订单领域包结构
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
│   │   │   │   ├── OrderAddress.java         # 订单地址实体
│   │   │   │   └── OrderPayment.java         # 订单支付实体
│   │   │   └── value/               # 值对象
│   │   │       ├── OrderNo.java              # 订单号值对象
│   │   │       ├── OrderStatus.java          # 订单状态值对象
│   │   │       ├── MoneyValue.java           # 金额值对象
│   │   │       ├── AddressValue.java         # 地址值对象
│   │   │       └── PaymentInfo.java          # 支付信息值对象
│   │   ├── service/                 # 领域服务
│   │   │   ├── OrderDomainService.java       # 订单领域服务
│   │   │   ├── PricingService.java           # 定价服务
│   │   │   ├── InventoryService.java         # 库存服务
│   │   │   ├── ShippingService.java          # 配送服务
│   │   │   └── PaymentService.java           # 支付服务
│   │   ├── repository/              # 仓储接口
│   │   │   ├── OrderRepository.java          # 订单仓储
│   │   │   └── OrderItemRepository.java      # 订单项仓储
│   │   └── event/                   # 领域事件
│   │       ├── OrderCreatedEvent.java        # 订单创建事件
│   │       ├── OrderPaidEvent.java           # 订单支付事件
│   │       ├── OrderShippedEvent.java        # 订单发货事件
│   │       ├── OrderCompletedEvent.java      # 订单完成事件
│   │       └── OrderCancelledEvent.java      # 订单取消事件
│   ├── application/                 # 应用层
│   │   ├── service/                 # 应用服务
│   │   │   ├── OrderAppService.java          # 订单应用服务
│   │   │   ├── OrderQueryService.java        # 订单查询服务
│   │   │   └── OrderProcessService.java      # 订单处理服务
│   │   ├── command/                 # 命令对象
│   │   │   ├── CreateOrderCommand.java       # 创建订单命令
│   │   │   ├── CancelOrderCommand.java       # 取消订单命令
│   │   │   ├── PayOrderCommand.java          # 支付订单命令
│   │   │   ├── ShipOrderCommand.java         # 发货订单命令
│   │   │   └── CompleteOrderCommand.java     # 完成订单命令
│   │   ├── query/                   # 查询对象
│   │   │   ├── OrderQuery.java               # 订单查询
│   │   │   ├── OrderItemQuery.java           # 订单项查询
│   │   │   └── OrderStatisticsQuery.java     # 订单统计查询
│   │   └── dto/                     # 数据传输对象
│   │       ├── OrderDTO.java                 # 订单DTO
│   │       ├── OrderItemDTO.java             # 订单项DTO
│   │       ├── OrderCreateVO.java            # 订单创建VO
│   │       ├── OrderDetailVO.java            # 订单详情VO
│   │       └── OrderStatisticsVO.java        # 订单统计VO
│   ├── infrastructure/              # 基础设施层
│   │   ├── repository/              # 仓储实现
│   │   │   ├── OrderRepositoryImpl.java      # 订单仓储实现
│   │   │   └── OrderItemRepositoryImpl.java  # 订单项仓储实现
│   │   ├── persistence/             # 持久化
│   │   │   ├── OrderMapper.java              # 订单Mapper
│   │   │   └── OrderItemMapper.java          # 订单项Mapper
│   │   ├── client/                  # 客户端
│   │   │   ├── ProductClient.java            # 商品服务客户端
│   │   │   ├── PaymentClient.java            # 支付服务客户端
│   │   │   ├── InventoryClient.java          # 库存服务客户端
│   │   │   └── ShippingClient.java           # 配送服务客户端
│   │   └── cache/                   # 缓存
│   │       └── OrderCache.java               # 订单缓存
│   └── interfaces/                  # 接口层
│       ├── rest/                    # REST接口
│       │   └── OrderController.java          # 订单控制器
│       ├── rpc/                     # RPC接口
│       │   └── OrderRpcService.java          # 订单RPC服务
│       └── event/                   # 事件监听
│           ├── PaymentSuccessEventListener.java # 支付成功事件监听
│           └── InventoryUpdateEventListener.java # 库存更新事件监听
```

### 6.2 产品领域包
```java
com.bone.business.
├── product/                          # 产品领域
│   ├── domain/                       # 领域层
│   │   ├── model/                    # 领域模型
│   │   │   ├── Product.java                   # 产品聚合根
│   │   │   ├── ProductSku.java                # 产品SKU
│   │   │   ├── ProductCategory.java           # 产品分类
│   │   │   └── ProductInventory.java          # 产品库存
│   │   └── service/                  # 领域服务
│   │       └── ProductDomainService.java     # 产品领域服务
│   ├── application/                  # 应用层
│   │   ├── service/                  # 应用服务
│   │   │   └── ProductAppService.java        # 产品应用服务
│   │   └── command/                  # 命令对象
│   │       ├── CreateProductCommand.java     # 创建产品命令
│   │       └── UpdateProductCommand.java     # 更新产品命令
│   └── interfaces/                   # 接口层
│       └── rest/                     # REST接口
│           └── ProductController.java        # 产品控制器
```

---

## 🎯 **包命名最佳实践总结**

### 7.1 核心命名规则
| 规则 | 说明 | 良好示例 | 不良示例 |
|------|------|----------|----------|
| **域名反转** | com.company.platform | `com.bone.framework` | `bone.framework` |
| **层次清晰** | layer.domain.component | `com.bone.business.order.domain` | `com.bone.order.business` |
| **职责单一** | 每个包聚焦单一功能 | `.service`, `.repository` | `.service.repository` |
| **命名一致** | 相同概念使用相同命名 | `*Repository`, `*Service` | `*Repo`, `*Svc` |

### 7.2 包依赖规范
```java
// 允许的依赖方向（单向依赖）
framework → engine → platform → business
    ↓          ↓         ↓         ↓
  starter → client ← gateway ← interfaces

// 禁止的依赖（循环依赖）
business → framework  // 禁止
platform → business   // 禁止
```

### 7.3 特殊包命名约定
```java
// 内部实现包（不对外暴露）
.internal.*              // 内部实现细节
.impl.*                  // 接口实现类

// 特定技术实现包
.jdbc.*                  // JDBC相关实现
.redis.*                 // Redis相关实现
.kafka.*                 // Kafka相关实现

// 适配器包
.adapter.*               // 适配器模式实现
```

### 7.4 包大小控制原则
- **适中规模**：每个包包含5-15个类文件
- **功能内聚**：相关功能放在同一包内
- **避免过大**：超过20个类考虑拆分
- **避免过小**：少于3个类考虑合并

### 7.5 实施检查清单
- [ ] 所有包名使用小写字母
- [ ] 包结构反映业务领域
- [ ] 遵循分层架构原则
- [ ] 避免循环依赖
- [ ] 包大小适中
- [ ] 命名一致性检查
- [ ] 内部实现包正确标记

> **"优秀的包命名是系统架构的蓝图，清晰的包结构是团队协作的基石。规范的包命名让代码自文档化，让系统更易理解和维护。"**

**Bone — 让企业级开发包结构更清晰、更规范、更易维护**