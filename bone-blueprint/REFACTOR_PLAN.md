# Bone-Blueprint 工程重构计划

## 重构目标

按照《Bone-Blueprint-DDD规范v1.0》的要求，重构bone-blueprint工程，使其符合最新的DDD+CQRS+六边形架构规范。

## 重构范围

- 包结构重构
- 代码结构重构
- 命名规范调整
- 依赖关系调整
- 架构约束验证

## 重构步骤

### 1. 包结构重构

#### 1.1 移除不符合规范的包
- [x] 移除 `com.bone.blueprint.common` 包（规范要求下沉到bone-core）
- [x] 移除 `com.bone.blueprint.domain.client` 包（规范要求使用domain/gateway）

#### 1.2 调整包结构
- [x] 调整 `domain/model` 包，按业务分组
- [x] 调整 `domain/repository` 包，移除按业务分组的子包
- [x] 调整 `application` 包，确保命令和查询分离
- [x] 调整 `adapter` 包，添加 `assembler` 包
- [x] 调整 `infrastructure` 包，添加 `gateway`、`security`、`query/native` 包

### 2. 代码结构重构

#### 2.1 领域层重构
- [x] 重构 `User` 聚合根，使用业务ID（UUID）
- [x] 重构 `UserRepository`，移除查询方法
- [x] 重构 `UserDomainService`，移除Spring注解
- [x] 添加 `domain/gateway` 包，定义防腐层接口
- [x] 添加 `domain/model/user/vo` 包，移动值对象
- [x] 添加 `domain/model/user/event` 包，添加领域事件

#### 2.2 应用层重构
- [x] 重构命令对象，使用正确的命名规范
- [x] 重构命令处理器，使用正确的命名规范
- [x] 重构查询对象，使用正确的命名规范
- [x] 重构查询处理器，使用正确的命名规范
- [x] 重构查询DTO，使用正确的命名规范

#### 2.3 适配器层重构
- [x] 重构 `UserController`，使用正确的命名规范
- [x] 添加 `UserAssembler`，处理DTO转换

#### 2.4 基础设施层重构
- [x] 添加 `DomainServiceConfiguration`，注册领域服务
- [x] 添加 `BoneMetadataConfiguration`，配置元数据SDK
- [x] 添加 `JwtTokenProvider`，实现安全功能
- [x] 添加 `UserNativeQueryRepository`，处理复杂查询

### 3. 命名规范调整

#### 3.1 包命名
- [x] 确保所有包名符合规范

#### 3.2 类命名
- [x] 确保所有类名符合规范

#### 3.3 方法命名
- [x] 确保所有方法名符合规范

### 4. 依赖关系调整

#### 4.1 依赖方向
- [x] 确保依赖方向为：adapter → application → domain ← infrastructure

#### 4.2 领域层依赖
- [x] 确保领域层不依赖任何框架

#### 4.3 应用层依赖
- [x] 确保应用层只依赖领域层和基础设施层的接口

### 5. 架构约束验证

#### 5.1 ArchUnit 规则
- [x] 确保ArchUnit规则符合规范

#### 5.2 事务模型
- [x] 确保事务在application.command.handler中

#### 5.3 异常处理
- [x] 确保异常处理符合规范

## 重构前后对比

### 重构前

```
src/main/java/com/bone/blueprint/
├── BoneBlueprintApplication.java
├── adapter/
│   └── web/
│       └── controller/
│           └── UserController.java
├── application/
│   ├── command/
│   │   ├── cmd/
│   │   │   ├── UserCreateCmd.java
│   │   │   └── UserUpdateCmd.java
│   │   └── handler/
│   │       ├── UserCreateCmdHandler.java
│   │       └── UserUpdateCmdHandler.java
│   └── query/
│       ├── dto/
│       │   └── UserQueryResp.java
│       ├── handler/
│       │   └── UserQueryHandler.java
│       └── qry/
│           ├── UserPageQuery.java
│           └── UserQuery.java
├── common/
│   ├── exception/
│   │   └── BusinessException.java
│   └── result/
│       ├── ApiResponse.java
│       └── PageResult.java
├── domain/
│   ├── client/
│   │   └── UserClient.java
│   ├── model/
│   │   ├── dashboard/
│   │   │   ├── Dashboard.java
│   │   │   ├── QuickAccess.java
│   │   │   ├── TodoItem.java
│   │   │   └── Widget.java
│   │   ├── iam/
│   │   │   ├── Permission.java
│   │   │   ├── Role.java
│   │   │   └── User.java
│   │   ├── masterdata/
│   │   │   ├── MasterDataEntity.java
│   │   │   ├── MasterDataField.java
│   │   │   └── MasterDataRecord.java
│   │   └── user/
│   │       └── User.java
│   ├── repository/
│   │   ├── dashboard/
│   │   │   ├── DashboardRepository.java
│   │   │   ├── QuickAccessRepository.java
│   │   │   ├── TodoItemRepository.java
│   │   │   └── WidgetRepository.java
│   │   ├── iam/
│   │   │   ├── PermissionRepository.java
│   │   │   ├── RoleRepository.java
│   │   │   └── UserRepository.java
│   │   ├── masterdata/
│   │   │   ├── MasterDataEntityRepository.java
│   │   │   ├── MasterDataFieldRepository.java
│   │   │   └── MasterDataRecordRepository.java
│   │   └── UserRepository.java
│   └── service/
│       └── UserDomainService.java
├── infrastructure/
│   ├── config/
│   │   └── SecurityConfig.java
│   └── external/
│       └── UserClientImpl.java
└── BoneBlueprintApplication.java
```

### 重构后

```
src/main/java/com/bone/blueprint/
├── BoneBlueprintApplication.java
├── adapter/
│   ├── web/
│   │   ├── controller/
│   │   │   └── UserController.java
│   │   ├── dto/
│   │   │   ├── req/
│   │   │   │   ├── CreateUserRequest.java
│   │   │   │   └── UpdateUserRequest.java
│   │   │   └── resp/
│   │   │       ├── UserDetailResponse.java
│   │   │       └── UserPageResponse.java
│   │   └── assembler/
│   │       └── UserAssembler.java
├── application/
│   ├── command/
│   │   ├── cmd/
│   │   │   ├── CreateUserCommand.java
│   │   │   └── UpdateUserCommand.java
│   │   └── handler/
│   │       ├── CreateUserCommandHandler.java
│   │       └── UpdateUserCommandHandler.java
│   ├── query/
│   │   ├── qry/
│   │   │   ├── UserPageQuery.java
│   │   │   └── UserDetailQuery.java
│   │   ├── handler/
│   │   │   ├── UserPageQueryHandler.java
│   │   │   └── UserDetailQueryHandler.java
│   │   └── dto/
│   │       └── UserDto.java
│   └── event/
│       └── UserRegisteredEventHandler.java
├── domain/
│   ├── model/
│   │   └── user/
│   │       ├── User.java
│   │       ├── vo/
│   │       │   ├── UserId.java
│   │       │   ├── Username.java
│   │       │   ├── Password.java
│   │       │   └── UserStatus.java
│   │       └── event/
│   │           └── UserRegisteredEvent.java
│   ├── repository/
│   │   └── UserRepository.java
│   ├── gateway/
│   │   └── PasswordEncoder.java
│   └── service/
│       └── user/
│           └── UserUniquenessChecker.java
├── infrastructure/
│   ├── gateway/
│   │   └── BCryptPasswordEncoderImpl.java
│   ├── security/
│   │   └── JwtTokenProvider.java
│   ├── query/
│   │   └── native/
│   │       └── UserNativeQueryRepository.java
│   └── config/
│       ├── BoneMetadataConfiguration.java
│       └── DomainServiceConfiguration.java
└── resources/
    └── sql/
        └── user/
            └── findUsersWithRole.sql
```

## 重构注意事项

1. **业务ID**：使用UUID作为聚合根ID，禁止使用数据库自增ID
2. **仓储接口**：只保留save、remove、findById方法，禁止添加查询方法
3. **领域服务**：移除Spring注解，在DomainServiceConfiguration中注册
4. **查询结果**：使用DTO或Projection，禁止返回领域实体
5. **依赖方向**：确保依赖方向为adapter → application → domain ← infrastructure
6. **命名规范**：确保所有包、类、方法名符合规范
7. **架构约束**：确保ArchUnit规则符合规范

## 重构验证

1. **代码编译**：确保代码编译通过
2. **ArchUnit测试**：确保ArchUnit测试通过
3. **功能测试**：确保核心功能正常运行
4. **代码质量**：确保代码质量符合规范

## 重构完成标准

1. 工程结构符合DDD规范
2. 代码命名符合规范
3. 依赖关系符合规范
4. 架构约束符合规范
5. 功能测试通过
6. 代码质量符合规范

---

**重构开始时间**：2026-04-22
**重构完成时间**：2026-04-22
**重构负责人**：AI Assistant