# 🏛️ Bone-Blueprint v7.0 工程规范（工业级最佳实践·完全自洽版）

## 企业级 DDD + CQRS + 六边形架构 + Bone Metadata SDK 原生集成

> **定位**：可执行架构协议，元数据零配置，通用组件下沉 bone-core
> **适用规模**：3–20 人团队 / 单体到模块化演进系统
> **核心目标**：结构即规则、命名即语义、依赖即约束，开发者想犯错都难
> **生效日期**：2026-04-22
> **文档版本**：v7.0 | 核心改进：bone-core/bone-utils/bone-security 框架能力全面纳入规范，实体继承体系标准化，多租户隔离机制明确

---

## 0️⃣ 一句话定义

> **Bone-Blueprint = DDD + CQRS + Hexagonal + Bone Metadata SDK + 封闭式边界约束 + 双 ID 模型**

---

## 0️⃣ bone-framework 核心能力概览

### 0.1 模块职责矩阵

| 模块 | 核心职责 | 关键类 |
|------|---------|--------|
| **bone-core** | 领域模型基类、实体基类、DTO基类、统一响应、分页模型、租户上下文、异常体系、ID生成策略、反射工具 | `AggregateRoot`、`AbstractEntity`、`TenantAbstractEntity`、`ApiResponse`、`PageResult`、`TenantContext`、`DomainException`、`DistributedIdGenerator`、`ReflectionUtil` |
| **bone-utils** | 通用工具（当前为骨架，依赖 bone-core） | — |
| **bone-security** | 认证授权、JWT令牌、密码加密、用户模型、权限检查 | `JwtTokenProvider`、`PasswordEncoder`、`User`、`SecurityContextHolder`、`PermissionChecker` |

### 0.2 实体继承体系（必须严格遵循）

```
Entity<ID>                          # 主键基类（@Id + @GeneratedValue）
    ↑
AbstractEntity<ID>                 # 审计字段 + 软删除（Auditable + SoftDeletable + @Deleted）
    ↑
TenantAbstractEntity<ID>           # 多租户字段（Tenantable：tenantId）
```

**继承选择规则**：
- **单租户系统**：继承 `AbstractEntity<ID>`
- **多租户系统**：继承 `TenantAbstractEntity<ID>`
- **聚合根**：继承 `AggregateRoot<ID>`（继承自 `Entity<ID>`）

### 0.3 聚合根设计规范

```java
// 聚合根必须继承 AggregateRoot，聚合根的ID类型为业务ID（如 UserId 值对象）
public class User extends AggregateRoot<UserId> {
    private UserId id;          // 业务 ID（对应数据库 biz_id）
    private Long dbId;          // 数据库自增主键（仅供 SDK 内部使用）
    // ... 其他字段
}

// 实体（非聚合根）必须继承 AbstractEntity 或 TenantAbstractEntity
public class OrderItem extends AbstractEntity<Long> {
    // ...
}
```

### 0.4 关键框架组件使用规范

#### TenantContext（多租户上下文）
```java
// 设置当前线程租户ID（自动传递到子线程）
TenantContext.setTenantId(tenantId);

// 获取当前线程租户ID
Long tenantId = TenantContext.getTenantId();

// 清除上下文（防止内存泄漏）
TenantContext.clear();
```

#### DistributedIdGenerator（分布式ID生成）
```java
// 初始化雪花算法（应用启动时调用一次）
DistributedIdGenerator.initialize(datacenterId, workerId, epoch);

// 生成 UUID（无横线小写）
String uuid = DistributedIdGenerator.generateUuid();

// 生成雪花ID（字符串格式）
String snowflakeId = DistributedIdGenerator.generateSnowflakeId();

// 生成业务流水号（带日期前缀）
String bizNo = DistributedIdGenerator.generateBusinessNumber("ORDER");
```

#### ApiResponse（统一响应）
```java
// 成功响应
return ApiResponse.success(data);
return ApiResponse.success("操作成功", data);
return ApiResponse.success();

// 错误响应
return ApiResponse.error("业务错误");
return ApiResponse.error(ResultCode.VALIDATION_ERROR);
return ApiResponse.error(500, "系统异常");
```

#### PageResult（分页结果）
```java
// 构造分页结果
PageResult<UserDto> page = PageResult.of(records, totalCount, pageNum, pageSize);
PageResult<UserDto> page = PageResult.of(records, totalCount, pageParam);

// 判断空
if (page.isEmpty()) { ... }

// 获取偏移量
int offset = page.getOffset();
```

#### ReflectionUtil（反射工具）
```java
// 获取带有指定注解的接口类型
Class<? extends Annotation> annotationType = ReflectionUtil.getInterfaceByAnnotation(targetClass, MyAnnotation.class);

// 创建实例、调用方法、访问字段等（详见工具类API）
```

### 0.5 异常体系使用规范

| 异常类 | 使用场景 | 抛出层级 |
|--------|---------|---------|
| `DomainException` | 领域层业务规则违反 | domain 层 |
| `BizException` | 应用层业务错误（参数非法、状态冲突） | application 层 |
| `ServiceException` | 通用服务异常 | infrastructure 层 |
| `SystemException` | 系统级异常（IO、配置等） | infrastructure 层 |
| `NotFoundException` | 资源不存在 | application 层 |
| `InvalidRequestException` | 请求参数校验失败 | adapter 层 |

---

## 1️⃣ 架构第一性原则（不可违反）

### 1.1 四大铁律

**① 领域唯一性（Domain First）**
- `domain` 是唯一业务真相来源
- 不依赖任何框架（Spring/MyBatis/JPA ❌）
- **允许有限使用 Lombok**：
  - **domain 层**：仅限 `@Getter` 和私有构造器注解（如 `@NoArgsConstructor(access = PRIVATE)`），严禁 `@Setter` 和 `@Data`，以保护聚合根封装性
  - **application / infrastructure 层**：允许 `@RequiredArgsConstructor` 减少依赖注入样板代码
  - **值对象**：允许 `@Value`（不可变类）替代 record，根据团队偏好选择
  - **测试代码**：允许 `@Builder` 方便对象构建

**② 依赖方向唯一性**
```
adapter → application → domain ← infrastructure
```
- ✔ 只能向内依赖
- ❌ 禁止任何反向 import

**③ CQRS 物理隔离**
- Command：写模型，必须经过 domain，使用 SDK 动态代理 Repository
- Query：读模型，禁止 **domain 依赖读模型**，读模型可引用 domain 值对象，复杂查询隔离在 infrastructure

**④ 外部系统隔离（ACL）**
- 所有外部依赖通过 `infrastructure` 网关实现
- domain 不感知任何外部系统

### 1.2 为什么融合六边形与整洁架构？

| 维度 | 六边形架构 | 整洁架构 | **融合方案** |
|------|-----------|---------|-------------|
| 边界定义 | 端口（Port）与适配器（Adapter） | 同心圆分层 | 入站适配器用 `adapter`，出站实现用 `infrastructure` |
| 依赖方向 | 外层依赖内层端口 | 源代码依赖必须指向内部 | 强制 `domain` 零依赖，`application` 只依赖 `domain` |
| 可读性 | 适配器概念直观 | 用例/实体命名通用 | 包名直接表达职责：`adapter.web`、`application.command`、`domain.model` |

---

## 2️⃣ 标准工程结构（最终版）

```text
src/main/java/com/bone/blueprint/
├── BoneBlueprintApplication.java
│
├── adapter/                                    # 入站适配器层
│   ├── web/
│   │   ├── controller/
│   │   │   └── UserController.java
│   │   ├── dto/
│   │   │   ├── req/
│   │   │   │   ├── CreateUserReq.java
│   │   │   │   ├── UpdateUserReq.java
│   │   │   │   └── UserPageReq.java
│   │   │   └── resp/
│   │   │       ├── UserDetailResp.java
│   │   │       └── UserPageResp.java
│   │   └── converter/
│   │       └── UserWebConverter.java
│   ├── rpc/
│   │   └── UserRpcProvider.java
│   ├── mq/
│   │   └── UserRegisteredListener.java
│   └── schedule/
│       └── SyncUserStatusJob.java
│
├── application/                                # 应用层
│   ├── command/
│   │   ├── cmd/
│   │   │   ├── CreateUserCmd.java
│   │   │   ├── DisableUserCmd.java
│   │   │   └── ChangePasswordCmd.java
│   │   └── handler/
│   │       ├── CreateUserHandler.java
│   │       ├── DisableUserHandler.java
│   │       └── ChangePasswordHandler.java
│   ├── query/
│   │   ├── qry/
│   │   │   ├── UserPageQry.java
│   │   │   └── UserByIdQry.java
│   │   ├── handler/
│   │   │   ├── UserPageQueryHandler.java
│   │   │   └── UserDetailQueryHandler.java
│   │   └── dto/
│   │       ├── UserDTO.java
│   │       └── UserPageResult.java
│   └── event/
│       └── UserRegisteredHandler.java
│
├── domain/                                     # 领域层（零依赖，仅依赖 bone-core）
│   ├── model/                                  # 领域模型 - **按业务分组** ✅
│   │   └── user/                               # 用户领域分组 - 所有用户相关代码在一起
│   │       ├── User.java                      # 聚合根，继承 bone-core 的 AggregateRoot
│   │       ├── vo/
│   │       │   ├── Username.java
│   │       │   ├── Password.java
│   │       │   ├── Email.java
│   │       │   └── UserId.java
│   │       └── event/
│   │           ├── UserRegisteredEvent.java    # 实现 bone-core 的 DomainEvent
│   │           └── UserDisabledEvent.java
│   ├── repository/                             # 仓储接口（端口）
│   │   └── UserRepository.java
│   ├── client/                                 # 防腐层接口（ACL）- 外部系统依赖端口
│   │   └── SmsClient.java
│   └── service/                                # 领域服务 - 按业务分组
│       └── user/
│           └── UserUniquenessChecker.java
│
├── infrastructure/                             # 基础设施层
│   ├── external/
│   │   └── AliyunSmsClientImpl.java           # ACL 实现
│   └── config/
│       ├── BoneMetadataConfig.java            # SDK 初始化（如有需要）
│       └── PasswordEncoderImpl.java
```

### 2.1 关键精简说明

| 已删除项 | 原因 |
|----------|------|
| `infrastructure/persistence/` 整个目录 | Bone Metadata SDK 完全接管持久化，无需 metadata、mapper、converter、po |
| `domain/shared/` 整个包 | `AggregateRoot`、`DomainEvent`、`DomainException` 已下沉至 **bone-core** |
| `UserRepositoryImpl`、`UserPO`、`UserPersistenceConverter` | SDK 动态代理直接操作领域对象，零实现代码 |
| `UserQueryMapper` 等读模型 Mapper | 读模型优先使用 SDK QueryBuilder 直查，复杂场景可在 `infrastructure` 中保留原生 Mapper（按需） |
| `domain/gateway/` | 拆分为 `domain/repository/` 和 `domain/client/`，语义更清晰 |

### 2.2 包分组原则

**✅ 按业务分组（推荐，当前实践）**
```
domain/model/user/          用户领域所有内容（聚合根、vo、event）
domain/model/order/         订单领域所有内容（聚合根、vo、event）
```
**优点**：修改用户功能时，所有相关代码都在一个目录，不用跨多个目录跳跃，导航方便，业务边界清晰。

**❌ 按类型分组（不推荐）**
```
domain/model/entities/       所有实体放一起
domain/model/vo/             所有值对象放一起
domain/model/events/         所有事件放一起
```
**缺点**：修改用户功能需要打开三个目录，业务碎片化，不方便。

### 2.3 基础组件依赖

```xml
<!-- bone-core: 提供 AggregateRoot、DomainEvent、DomainException 等 -->
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-core</artifactId>
    <version>${bone-core.version}</version>
</dependency>

<!-- bone-metadata-sdk: 元数据驱动持久化框架 -->
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-metadata-sdk</artifactId>
    <version>${bone-metadata-sdk.version}</version>
</dependency>
```

---

## 3️⃣ 各层职责（不可交叉）

| 层级 | 职责 | 禁止 |
|------|------|------|
| **adapter** | 协议转换、DTO校验、请求路由 | 业务逻辑、调用repository、直接访问infrastructure |
| **application** | 用例编排、事务边界、领域调用协调、CQRS路由 | 包含业务规则、直接操作数据库 |
| **domain** | 业务规则、聚合根、值对象、领域事件 | 框架依赖、SQL/MQ/RPC、DTO/PO |
| **infrastructure** | 外部服务实现（ACL）、SDK配置、特殊场景手动 Repository 实现 | 包含业务逻辑 |
| **common** | 应用特有工具类、常量 | 通用异常/响应/工具应该下沉 `bone-core` | 包含业务含义 |

---

## 4️⃣ 写链路（Command Side）完整实现

### 4.1 聚合根设计原则

**核心法则（DDD 第一性原理）：**
- 一个**聚合** = 一个**事务一致性边界** + 一个**不变量整体**
- **一个聚合只能有一个聚合根**，聚合根 ID 是唯一外部访问入口
- 聚合内部所有修改必须通过聚合根方法，保证不变量不被破坏
- 聚合之间只能通过 ID 引用，不能直接引用对象，避免耦合

**大小原则：**
- 优先**小聚合**，不要追求大而全，一个聚合对应一个独立业务能力
- 只有业务上**总是一起修改**，才放在同一个聚合

```java
package com.bone.blueprint.domain.model.order;

import com.bone.core.domain.AggregateRoot;
import com.bone.blueprint.domain.model.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.model.order.vo.OrderId;
import com.bone.blueprint.domain.model.order.vo.OrderItem;
import com.bone.core.exception.DomainException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Order extends AggregateRoot<OrderId> {
    private OrderId id;
    private Long customerId;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal totalAmount;
    private OrderStatus status;

    public static Order create(OrderId id, Long customerId) {
        Order order = new Order();
        order.id = id;
        order.customerId = customerId;
        order.status = OrderStatus.CREATED;
        order.totalAmount = BigDecimal.ZERO;
        order.addDomainEvent(new OrderCreatedEvent(order));
        return order;
    }

    // 只有聚合根本身能修改聚合，保护不变量：总金额 = 订单项金额之和
    public void addItem(OrderItem item) {
        items.add(item);
        recalculateTotal(); // 自动维护不变量
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 仅供 SDK 回填 ID 使用
    void setId(OrderId id) { this.id = id; }
}
```

**设计原则补充：**
> **工厂方法：** 构造器私有，提供公有的静态工厂方法 `create()`，负责 invariants 验证和领域事件添加，保证只有合法的聚合才能被创建。
>
> **聚合识别经验法则：**
> 1. 找**业务不变量**：哪些数据必须一起保持一致性 → 同一个聚合
> 2. 找**事务一致性**：一次修改必须原子更新哪些对象 → 同一个聚合
> 3. **小聚合优先**：大聚合拆小，只要不需要一致修改就分开
> 4. 按**业务能力**找聚合，不是按分层找

### 4.2 值对象设计原则

**三大核心特征：**
1. **不可变（Immutability）**：创建后不能修改，所有属性 `final`（Java record 天生满足）
2. **自验证（Self-Validation）**：创建时验证不变量，不合法的值对象无法存在
3. **属性相等（Value Equality）**：两个值对象如果所有属性相同，就是同一个，**不需要 ID**

```java
package com.bone.blueprint.domain.model.user.vo;

import com.bone.core.exception.DomainException;

public record Username(String value) {
    public Username {
        if (value == null || value.isBlank()) {
            throw new DomainException("用户名不能为空");
        }
        if (value.length() < 3 || value.length() > 50) {
            throw new DomainException("用户名长度必须在 3-50 字符之间");
        }
    }

    public static Username of(String value) {
        return new Username(value);
    }
}
```

**常见误区：**
- ❌ 错误：给 `Username` 也加 `UsernameId`，退化成实体
- ❌ 错误：使用原始 `String`/`Long`，失去类型安全和自验证
- ✅ 正确：所有 ID 都包装成值对象，业务概念也包装成值对象

**最佳实践：**
- ID 必须包装（`UserId`/`OrderId`/`ProductId`）
- 业务概念包装（`Email`/`Username`/`Money`/`Address`/`PhoneNumber`）

### 4.3 领域事件设计原则

**核心原则：**
- 领域事件代表**过去发生的事情**，必须用**过去式**命名：`OrderCreatedEvent` ✅，`CreateOrderEvent` ❌
- 聚合根通过 `addDomainEvent()` 添加事件，**由基础设施在事务提交后异步发送**
- 订阅者在 `application.event` 包，符合依赖方向（domain 不依赖 application）
- 事件包含所有必要信息，订阅者不需要重新查询（推荐）

**事务一致性（Outbox 模式）：**
1. 业务数据保存 + 事件保存 **在同一个事务**
2. 独立任务/中间件 从 Outbox 读取事件发送
3. 保证**至少投递一次**，实现最终一致性

```java
package com.bone.blueprint.domain.model.order.event;

import com.bone.core.domain.DomainEvent;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.vo.OrderId;
import com.bone.blueprint.domain.model.user.vo.CustomerId;

public record OrderCreatedEvent(OrderId orderId, CustomerId customerId) implements DomainEvent {
    public OrderCreatedEvent(Order order) {
        this(order.getId(), order.getCustomerId());
    }
}
```

**订阅者示例（application.event）：**
```java
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler {
    private final NotificationService notificationService;

    @TransactionalEventListener
    public void handle(OrderCreatedEvent event) {
        // 发送通知短信，不影响主事务
        notificationService.sendOrderConfirmation(event.orderId());
    }
}
```

### 4.4 领域模型（纯净，继承 bone-core 抽象，允许 Lombok @Getter）

**最佳实践：**
- **ID 永远使用值对象包装**，不使用原始类型 `Long`/`String`
- 值对象自验证不变量
- 聚合根保持纯净，不依赖任何框架注解

```java
package com.bone.blueprint.domain.model.user;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.blueprint.domain.model.user.vo.UserId;
import com.bone.blueprint.domain.model.user.vo.Username;
import com.bone.blueprint.domain.model.user.vo.Password;
import com.bone.blueprint.domain.model.user.vo.Email;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends AggregateRoot<UserId> {
    private UserId id;
    private Username username;
    private Password password;
    private Email email;
    private UserStatus status;

    public static User register(Username username, Password password, Email email) {
        User user = new User();
        user.username = username;
        user.password = password;
        user.email = email;
        user.status = UserStatus.ENABLED;
        user.addDomainEvent(new UserRegisteredEvent(user));
        return user;
    }

    public void disable() {
        if (this.status == UserStatus.DISABLED) {
            throw new DomainException("用户已处于禁用状态");
        }
        this.status = UserStatus.DISABLED;
        addDomainEvent(new UserDisabledEvent(this.id));
    }

    // 仅供 SDK 回填 ID 使用
    void setId(UserId id) { this.id = id; }
}
```

### 4.5 仓储接口（仅声明，零实现）

**位置：** 仓储接口定义在 `domain.repository`，属于端口（Port），符合六边形架构  
**实现：** 由 Bone Metadata SDK 动态代理自动实现，不需要手写

```java
package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.vo.UserId;
import com.bone.metadata.sdk.Repository;

public interface UserRepository extends Repository<User, UserId> {
    boolean existsByUsername(String username);
    // 其他自定义查询方法...
}
```

### 4.6 防腐层接口（ACL - 反向接口）

**什么时候需要：** 当 domain 需要调用**外部系统**时，在 `domain.client` 定义接口，符合**依赖反转原则**

- domain 定义接口（需求抽象），不依赖具体实现
- 具体实现在 `infrastructure.external`，适配外部服务
- 这样 domain 保持纯净，不感知外部实现，替换方便

示例：
```java
// domain/client/SmsClient.java - 领域定义接口（端口）
package com.bone.blueprint.domain.client;

public interface SmsClient {
    void sendVerificationCode(String phone, String code);
}
```

```java
// infrastructure/external/AliyunSmsClientImpl.java - 基础设施实现（适配器）
package com.bone.blueprint.infrastructure.external;

import com.bone.blueprint.domain.client.SmsClient;
import com.aliyuncs.dysmsapi20170525.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AliyunSmsClientImpl implements SmsClient {
    private final Client aliyunClient;

    @Override
    public void sendVerificationCode(String phone, String code) {
        // 调用阿里云 SDK 发送短信
    }
}
```

### 4.7 命令处理器（注入动态代理）

```java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.domain.repository.UserRepository;
import com.bone.blueprint.domain.model.user.vo.*;
import com.bone.blueprint.domain.model.user.User;
import com.bone.core.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateUserHandler {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long handle(CreateUserCmd cmd) {
        Username username = Username.of(cmd.getUsername());
        Email email = Email.of(cmd.getEmail());
        Password password = Password.of(cmd.getPassword(), passwordEncoder);

        if (userRepository.existsByUsername(username)) {
            throw new DomainException("用户名已存在");
        }

        User user = User.register(username, password, email);
        userRepository.save(user);   // SDK 动态代理执行 INSERT
        return user.getId().getValue();
    }
}
```

---

## 5️⃣ 读链路（Query Side）实现

### 5.1 包位置原则

| 层级 | 位置 |
|------|------|
| 查询参数 | `application.query.qry` |
| 查询结果 DTO | `application.query.dto` |
| 查询处理器 | `application.query.handler` |
| 复杂查询 Mapper | `infrastructure.persistence.mapper`（必须隔离于 domain） |

依赖方向正确：`adapter → application → infrastructure`，domain 不被读模型污染。

### 5.2 简单查询 - 优先使用 QueryBuilder

优先使用 Bone Metadata SDK 的 `QueryBuilder` 直接查询并映射 DTO，零手写代码。

```java
package com.bone.blueprint.application.query.handler;

import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.blueprint.application.query.dto.UserDTO;
import com.bone.blueprint.application.query.qry.UserPageQry;
import com.bone.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<UserDTO> handle(UserPageQry qry) {
        return QueryBuilder.from(User.class)
                .where("username").like(qry.getKeyword())
                .or("email").like(qry.getKeyword())
                .where("status").eq(qry.getStatus())
                .orderBy("id", "desc")
                .page(qry.getPageNum(), qry.getPageSize())
                .mapTo(UserDTO.class);
    }
}
```

### 5.3 复杂查询扩展

若需极端性能优化或复杂报表查询，可在 `infrastructure` 中添加原生 MyBatis Mapper，必须隔离于 domain。

---

## 6️⃣ 启动类配置

```java
package com.bone.blueprint;

import com.bone.metadata.sdk.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")
public class BoneBlueprintApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoneBlueprintApplication.class, args);
    }
}
```

---

## 7️⃣ 命名体系（唯一标准）

| 层级 | 类型 | 命名格式 | 示例 |
|------|------|----------|------|
| `adapter.web.controller` | 控制器 | `{Domain}Controller` | `UserController` |
| `adapter.web.dto.req` | 请求DTO | `{Verb}{Domain}Req` | `CreateUserReq` |
| `adapter.web.dto.resp` | 响应DTO | `{Domain}{Action}Resp` | `UserDetailResp` |
| `adapter.web.converter` | 转换器 | `{Domain}WebConverter` | `UserWebConverter` |
| `application.command.cmd` | 命令对象 | `{Verb}{Domain}Cmd` | `CreateUserCmd` |
| `application.command.handler` | 命令处理器 | `{Verb}{Domain}Handler` | `CreateUserHandler` |
| `application.query.qry` | 查询对象 | `{Domain}{Action}Qry` | `UserPageQry` |
| `application.query.handler` | 查询处理器 | `{Domain}{Action}Handler` | `UserPageHandler` |
| `application.query.dto` | 查询结果 | `{Domain}DTO` | `UserDTO` |

> **命名一致性原则**：Command 使用 `Cmd` 缩写，Query 使用 `Qry` 缩写，保持一致。
| `domain.model` | 聚合根 | `{Domain}` | `User` |
| `domain.model.vo` | 值对象 | `{BusinessConcept}` | `Username` |
| `domain.model.event` | 领域事件 | `{Domain}{PastVerb}Event` | `UserRegisteredEvent` |
| `domain.repository` | 仓储接口 | `{Aggregate}Repository` | `UserRepository` |
| `domain.client` | 防腐层接口 | `{ExternalSystem}Client` | `SmsClient` |
| `infrastructure.external` | ACL实现 | `{Provider}{Service}Impl` | `AliyunSmsClientImpl` |

---

## 8️⃣ 依赖约束（编译期控制）

```
adapter → application → domain ← infrastructure (SDK)
           ↓
      bone-core (基础抽象)
```

### 8.1 ArchUnit 核心规则

完整规则集合，从机制保证架构不腐化：

```java
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.bone.yourmodule")
public class ArchitectureTest {

    @ArchTest
    static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter..",
                        "..application..",
                        "..infrastructure.."
                );
        rule.check(classes);
    }

    @ArchTest
    static void domainLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "java..",
                        "com.bone.core..",
                        "lombok..",
                        "org.springframework.lang.."
                );
        rule.check(classes);
    }

    @ArchTest
    static void domainLayerShouldNotDependOnFrameworkAnnotations(JavaClasses classes) {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "javax.persistence..",
                        "org.mybatis..",
                        "java.sql.."
                ).check(classes);
    }

    @ArchTest
    static void domainLayerShouldOnlyAllowSafeLombokAnnotations(JavaClasses classes) {
        // 禁止 @Setter 和 @Data，保护封装性
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAnnotatedWith(Setter.class)
                .orShould().beAnnotatedWith(Data.class)
                .check(classes);
    }

    @ArchTest
    static void domainServiceShouldResideInDomain(JavaClasses classes) {
        classes()
                .that().haveNameMatching(".*Service")
                .should().resideInAPackage("..domain..")
                .check(classes);
    }

    @ArchTest
    static void repositoryInterfacesShouldBeInDomain(JavaClasses classes) {
        classes()
                .that().haveNameMatching(".*Repository")
                .should().resideInAPackage("..domain..")
                .check(classes);
    }

    @ArchTest
    static void repositoryImplementationsShouldBeInInfrastructure(JavaClasses classes) {
        classes()
                .that().haveNameMatching(".*RepositoryImpl")
                .should().resideInAPackage("..infrastructure..")
                .check(classes);
    }

    @ArchTest
    static void applicationLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        classes()
                .that().resideInAPackage("..application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "..application..",
                        "..common..",
                        "com.bone.core..",
                        "lombok..",
                        "java..",
                        "org.springframework..",
                        "org.slf4j.."
                );
        classes.check(classes);
    }

    @ArchTest
    static void adapterLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        classes()
                .that().resideInAPackage("..adapter..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter..",
                        "..application..",
                        "..common..",
                        "com.bone.core..",
                        "lombok..",
                        "java..",
                        "org.springframework..",
                        "org.slf4j.."
                );
        classes.check(classes);
    }
}
```

---

## 9️⃣ 事务模型

| 位置 | 规则 | 理由 |
|------|------|------|
| `application.command.handler` | **必须** `@Transactional` | 一个命令一个事务，事务边界在这里 |
| `application.query.handler` | `@Transactional(readOnly = true)` | 优化数据库连接，提升性能 |
| `domain` / `infrastructure` | **严禁**事务注解 | 领域模型不感知事务，事务由应用层控制 |

**最佳实践：**
- 一个 Command Handler 对应一个事务，粒度适中
- 不要在领域服务上加事务，不要在基础设施上加事务（除非特殊需要）
- 聚合根修改完成后保存，整个操作在一个事务内原子性完成

---

## 🔟 异常处理分层规范

| 异常类型 | 抛出层级 | 使用场景 | HTTP 状态 |
|----------|----------|----------|-----------|
| `DomainException` | **domain** | **所有**业务规则冲突（值对象校验、业务规则违反） | 400 |
| `NotFoundException` | `application` / `adapter` | 请求的资源不存在 | 404 |
| `SystemException` | `infrastructure` | 数据库连接失败、外部服务不可用、IO错误 | 500 |

> **设计原则**：所有业务规则检查都应该在领域层完成，异常也应该在领域层抛出。application 只做编排，不包含业务规则。
> **删除冗余**：不需要 `BusinessException`，避免分类模糊。

全局异常处理器统一转换为 `ApiResponse<T>` 格式。

---

## 1️⃣1️⃣ 强制红线（CI 阻断级）

| ❌ 绝对禁止 | ✅ 正确做法 |
|------------|------------|
| 项目内定义 `AggregateRoot`、`DomainEvent` | 统一使用 `bone-core` |
| 手动编写 `RepositoryImpl` 或 `PO` | 接口继承 `Repository`，SDK 动态代理 |
| 创建 `persistence` 目录及其子包 | 完全信任 SDK 元数据映射 |
| `domain` 对象使用 `@Data` 或 `@Setter` | 仅允许 `@Getter` 和私有构造器注解 |
| `domain` 对象加持久化注解 | 领域对象保持纯净 |
| 查询操作调用 `domain` 仓储 | 读模型使用 QueryBuilder 或独立 Mapper |

---

## 1️⃣2️⃣ 测试体系

| 层级 | 目标 | 工具 | 覆盖率要求 |
|------|------|------|------------|
| Domain 单元测试 | 规则验证 | JUnit5 + AssertJ | ≥90% |
| Application 集成测试 | 用例验证 | `@SpringBootTest` + TestContainers | 核心用例 100% |
| Architecture 测试 | 依赖规则 | ArchUnit | CI 必须通过 |

### 领域层单元测试示例

```java
@Test
void username_should_reject_invalid_format() {
    assertThatThrownBy(() -> Username.of("ab"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("用户名长度3-50字符");
}
```

### 应用层 Mock 测试示例

```java
@ExtendWith(MockitoExtension.class)
class CreateUserHandlerTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks CreateUserHandler handler;

    @Test
    void should_create_user_successfully() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        // ...
    }
}
```

---

## 1️⃣3️⃣ 演进路径

| 阶段 | 触发条件 | 演进动作 |
|------|----------|----------|
| **v1 单体** | 项目启动 | 四层架构 + SDK 动态代理，零持久化代码 |
| **v2 模块化** | 多限界上下文 | 按 `modules/{context}` 分包，SDK 分别扫描 |
| **v3 事件化** | 最终一致性需求 | Outbox + MQ + Projection |
| **v4 分布式** | 微服务拆分 | 独立部署，事件网格 |

---

## 🧠 最终架构本质

> **Domain 定义规则，bone-core 提供骨架，SDK 负责持久化，业务代码只写接口。**

---

## 📋 团队落地 Checklist

### 项目初始化
- [ ] 引入 `bone-core` 和 `bone-metadata-sdk` 依赖
- [ ] 启动类添加 `@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")`
- [ ] 创建四层包结构（无 `persistence` 目录）
- [ ] 配置 ArchUnit 规则（允许 Lombok @Getter，禁止 @Setter/@Data）
- [ ] 配置全局异常处理器和 `ApiResponse<T>`

### 每个领域模块开发
- [ ] 定义值对象替代原始类型
- [ ] 聚合根充血，继承 `bone-core` 的 `AggregateRoot`，使用 `@Getter` 和私有构造器
- [ ] 仓储接口定义在 `domain.repository`，继承 SDK `Repository`
- [ ] 防腐层接口定义在 `domain.client`
- [ ] 命令/查询对象与处理器按命名规范创建
- [ ] 读操作优先使用 SDK `QueryBuilder`
- [ ] 编写领域层单元测试

### Code Review 核心关注点
- [ ] 是否还有 `RepositoryImpl`、`PO`、`Converter` 等冗余类？
- [ ] `domain` 包是否无 Spring/MyBatis 注解？
- [ ] `domain` 是否误用了 `@Setter` 或 `@Data`？
- [ ] 读写是否分离？
- [ ] 事务是否在 `application.command.handler`？

---

## 核心口诀

> **架构层面**：六边形定边界，整洁控依赖，CQRS 分读写，SDK 代持久，bone-core 垫基石。  
> **编码层面**：值对象自验证，聚合根充血，命令动词化，查询扁平化，事务在应用。  
> **命名层面**：Adapter 适协议，Handler 管用例，Repository 仅接口，SDK 自动成。

---

**本规范即日起作为团队 Bone-Blueprint 工程落地的唯一标准。**  
**所有新项目必须遵循，存量项目应在迭代中逐步对齐。**

*文档版本：v4.2 | 生效日期：2026-04-22 | 维护团队：架构组*  
*更新内容：基于业界最佳实践优化，补充聚合根设计、值对象完整特征、领域事件发布订阅、包分组原则说明、ACL 防腐层完整示例*
