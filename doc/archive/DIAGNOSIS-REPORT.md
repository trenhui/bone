# Bone 工程诊断报告

> 生成时间：2026-06-14 | 基于文档、代码、配置全面审查

---

## 已完成的优化（基于业界最佳实践）

### ✅ 安全问题（P0）- 已优化

| 优化项 | 修改文件 | 说明 |
|--------|----------|------|
| Request DTO 添加校验注解 | `CreateAccountReq.java` | 添加 `@NotBlank`、`@Size`、`@Email` |
| Request DTO 添加校验注解 | `UpdateAccountReq.java` | 添加 `@Email`、`@Size` |
| Request DTO 添加校验注解 | `LoginReq.java` | 添加 `@NotBlank` |
| 新增 ResetPasswordReq | `ResetPasswordReq.java` | 替代直接使用 Command 作为入参 |
| JWT 密钥 fail-fast | `JwtConfig.java` | 生产环境禁止使用默认密钥，长度 < 32 拒绝启动 |
| CORS 配置外部化 | `SecurityConfig.java` | 通过环境变量注入，支持动态配置 |
| 创建 .env.example | `.env.example` | 提供环境变量模板，.env 已在 .gitignore |
| MinIO 配置外部化 | `MinioStorageClientImpl.java` | 通过环境变量注入凭证，实现 exists/delete 方法 |
| 新增 MinIO 配置 | `application.yml` | 添加 `bone.minio.*` 配置项 |

### ✅ 架构合规（P0）- 已优化

| 优化项 | 修改文件 | 说明 |
|--------|----------|------|
| Controller 入参改用 Req | `AccountController.java` | `resetPassword` 方法使用 `ResetPasswordReq` |
| Converter 添加转换方法 | `AccountWebConverter.java` | 添加 `toResetPasswordCommand` 方法 |
| QueryBuilder 改用 Repository | `AccountRoleBindingService.java` | 使用 `findByCriteria` 替代 `QueryBuilder` |
| Controller 入参改用 Req | `TenantController.java` | 使用 `CreateTenantReq`、`UpdateTenantReq`、`UpdateTenantQuotaReq` |
| 新增 TenantWebConverter | `TenantWebConverter.java` | 转换 Req 到 Command |
| Controller 入参改用 Req | `RoleController.java` | 使用 `UpdateRoleReq`、`AssignPermissionReq` |
| 新增 Req 类 | `UpdateRoleReq.java`、`AssignPermissionReq.java` | 替代直接使用 Command |
| 更新 RoleWebConverter | `RoleWebConverter.java` | 添加 `toUpdateRoleCommand`、`toAssignPermissionCommand` |
| QueryBuilder 改用 Repository | `TenantQuotaEnforcer.java` | 使用 `countByCriteria` 替代 `QueryBuilder` |

### ✅ 文档不一致（P1）- 已优化

| 优化项 | 修改文件 | 说明 |
|--------|----------|------|
| docker-compose 端口统一 | `docker-compose.yml` | bone-iam 端口改为 8081，与 application.yml 一致 |
| application.yml 增强 | `application.yml` | Redis、Server 端口支持环境变量，添加 CORS 配置 |

### ✅ 代码质量（P2）- 已优化

| 优化项 | 修改文件 | 说明 |
|--------|----------|------|
| 异常处理符合 RFC 7807 | `IamExceptionHandler.java` | 添加 ProblemDetail 结构，包含 traceId、errors |
| 配置外部化 | `application.yml` | 数据库、Redis 端口支持环境变量注入 |

### ✅ Flyway 依赖移除（P1）- 已优化

| 优化项 | 修改文件 | 说明 |
|--------|----------|------|
| 移除 Flyway 依赖 | `bone-iam/pom.xml` | 删除 flyway-core 和 flyway-mysql 依赖 |
| 移除 Flyway 依赖 | `bone-masterdata/pom.xml` | 删除 flyway-core 和 flyway-mysql 依赖 |
| 移除 Flyway 依赖 | `bone-system/pom.xml` | 删除 flyway-core 和 flyway-mysql 依赖 |
| 移除 Flyway 配置 | `bone-iam/application.yml` | 删除 `spring.flyway.enabled: false` |
| 移除 Flyway 配置 | `bone-masterdata/application.yml` | 删除 `spring.flyway.enabled: false` |
| 移除 Flyway 配置 | `bone-system/application.yml` | 删除 `spring.flyway.enabled: false` |

---

## 待优化项（需进一步评估）

### P1 - 测试覆盖

| 问题 | 建议 |
|------|------|
| IAM 模块测试覆盖率 ~16% | 为所有 CommandHandler 补充单元测试，目标行覆盖 ≥70% |
| 无契约测试 | 引入 OpenAPI diff 检测破坏性变更 |

### P1 - 配置管理

| 问题 | 建议 |
|------|------|
| 数据源重复配置 | 统一 `spring.datasource` 和 `bone.metadata.embedded.datasource` |

### P2 - 代码质量

| 问题 | 建议 |
|------|------|
| Account 审计字段手动赋值 | 利用基类自动填充或 @PrePersist |
| 缺少 OpenAPI 文档 | 引入 springdoc-openapi，添加 @Operation 注解 |
| Converter 手写样板 | 引入 MapStruct（parent POM 已声明 1.5.5） |

### P2 - DevOps

| 问题 | 建议 |
|------|------|
| 缺少 CI/CD 流水线 | 建立 GitHub Actions / GitLab CI |
| Debug 代码残留 | `DatabaseFixController`、`DatabaseDebugTool` 生产应禁用 |

---

## 一、安全问题（P0）

### 1.1 密码明文硬编码

**位置**：`bone-platform/bone-iam/src/main/resources/application.yml:15`

```yaml
password: ${BONE_DB_PASSWORD:mysql123}
```

**风险**：数据库密码明文提交到 Git 仓库，任何有代码权限的人都能看到。

**优化建议**：
- `.env` 加入 `.gitignore`，`.env.example` 只保留占位符
- 引入配置中心（Nacos），敏感配置通过 Secret 管理
- 生产环境使用 Vault 或 K8s Secret，禁止镜像内含密钥

---

### 1.2 JWT 默认密钥可预测

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/config/JwtConfig.java:14`

```java
private String secretKey = "change-me-change-me-change-me-change-me-32bytes";
```

**风险**：攻击者可利用已知密钥伪造任意用户 Token。

**优化建议**：启动时若检测到默认密钥且 `spring.profiles.active=prod`，直接 `fail-fast` 拒绝启动。

---

### 1.3 CORS 配置过于宽泛

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/config/SecurityConfig.java:77`

```java
config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:3003"));
```

**风险**：生产环境需改为域名白名单，当前硬编码 localhost。

**优化建议**：CORS 配置通过环境变量注入 `allowedOrigins`，生产环境禁止 `*`。

---

### 1.4 密码缺少 @Valid 校验

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/dto/req/CreateAccountReq.java:6`

```java
@Data
public class CreateAccountReq {
    private String username;
    private String password;
    // 无任何校验注解
}
```

**风险**：可创建空密码、超短密码的账号。

**优化建议**：所有 Request DTO 加入 JSR 380 校验注解（`@NotBlank`, `@Size(min=8)`, `@Email`）。

---

## 二、架构合规性问题（P0）

### 2.1 Command 直接作为 Controller 入参

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AccountController.java:99`

```java
public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordCommand cmd)
```

**违反**：`ResetPasswordCommand` 是 `application` 层对象，不应暴露到 HTTP 层。

**优化建议**：所有 Controller 入参应使用 `adapter.web.dto.req.*Req`，通过 Converter 转换为 Command。

---

### 2.2 Converter 手写样板代码过多

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/converter/AccountWebConverter.java`

```java
public CreateAccountCommand toCreateAccountCommand(CreateAccountReq req) {
    CreateAccountCommand cmd = new CreateAccountCommand();
    cmd.setUsername(req.getUsername());
    cmd.setPassword(req.getPassword());
    cmd.setEmail(req.getEmail());
    // ... 每个字段手动 set
}
```

**问题**：与 Lombok `@Mapper` 或 MapStruct 相比冗余且易漏字段。

**优化建议**：引入 MapStruct（项目已在 `bone-parent` 声明了 `mapstruct 1.5.5`），减少手写转换。

---

### 2.3 QueryBuilder 用于读侧

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/application/service/AccountRoleBindingService.java:53`

```java
return QueryBuilder.from(AccountRole.class)
        .where(AccountRole::getAccountId)
        .eq(accountId)
        .list()
```

**违反**：根据 DDD 规范（§14.5），`QueryBuilder` 标记为 `@ReadSideOnly`，禁止在 CommandHandler 中使用。

**优化建议**：将 `listRoleIds` 方法移至 QueryHandler，或改用 Repository 的 `findByCriteria` 方法。

---

## 三、测试覆盖不足（P1）

### 3.1 测试数量严重不足

**现状**：IAM 模块（最成熟的模块）仅有：

| 目录 | 测试文件数 |
|------|-----------|
| `application/command/handler/` | 4 个 |
| `domain/service/` | 1 个 |
| `infrastructure/security/` | 待确认 |
| `adapter/web/` | 待确认 |

25 个 CommandHandler 中仅 4 个有测试，覆盖率约 **16%**。

**优化建议**：
- **短期**：为所有 CommandHandler 补充单元测试（目标行覆盖 ≥70%）
- **中期**：引入 Testcontainers 做集成测试
- **长期**：CI 门禁 JaCoCo 行覆盖 ≥70%，分支 ≥60%

---

### 3.2 无契约测试

**现状**：API 规范文档定义了完整的 OpenAPI 契约，但代码中无 `openapi-diff`、Pact 等契约测试。

**优化建议**：每个服务维护 OpenAPI YAML，CI 中用 `openapi-diff` 检测破坏性变更。

---

## 四、文档与代码不一致（P1）

### 4.1 端口不一致

| 来源 | bone-iam 端口 |
|------|--------------|
| `application.yml:24` | 8081 |
| `docker-compose.yml:42` | 8080 |
| `AGENTS.md §9.3` | 8081 |
| `CODE_WIKI.md §6.4` | 8080 |

**优化建议**：统一以 `application.yml` 为准（8081），更新所有文档。

---

### 4.2 ApiResponse 成功码不一致

| 来源 | 成功 code |
|------|-----------|
| `Bone-API-规范.md §3.1` | 200（规范目标） |
| `ApiResponse.java:46` | `ResultCode.SUCCESS.getCode()` |
| `IamExceptionHandler.java:31` | 使用 `ex.getCode()` 直传 |

**现状**：`ResultCode.SUCCESS` 的值需确认。若仍为 `0`，则与 API 规范的 `code=200` 目标不一致，过渡期至 2026-09-01。

**优化建议**：确认 `ResultCode.SUCCESS.getCode()` = 200，或在过渡期内保持兼容。

---

### 4.3 文档引用的文件路径已过时

`CODE_WIKI.md` 中大量引用了 `/Users/renhui.trh/创业项目/智能理赔/deep-claim/` 的绝对路径，这是开发机本地路径，不应出现在项目文档中。

**优化建议**：替换为相对路径（如 `bone-framework/bone-core/...`）。

---

## 五、配置管理问题（P1）

### 5.1 Flyway 启用但实际未使用

**现状**：`bone-iam/pom.xml:118-125` 引入了 Flyway 依赖，但 `application.yml:11` 设置 `flyway.enabled: false`，数据库初始化依赖手动执行 `bone-init.sql`。

**优化建议**：二选一：
- 若选择 Flyway：启用并编写版本化迁移脚本
- 若选择 SQL 手动管理：移除 Flyway 依赖，减少不必要的 JAR 包

---

### 5.2 Redis 密码未外置

**现状**：`application.yml:20` 中 `password: ${BONE_REDIS_PASSWORD:}` 默认为空。

**优化建议**：开发环境可为空，但生产环境 `fail-fast` 校验。

---

### 5.3 数据源重复配置

**现状**：`application.yml` 中 `spring.datasource`（第12-16行）和 `bone.metadata.embedded.datasource`（第48-52行）配置了**同一数据库**的两套连接参数。

**优化建议**：统一数据源配置，避免维护两份相同连接信息。

---

## 六、代码质量问题（P2）

### 6.1 Account 聚合根审计字段手动赋值

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/domain/account/Account.java:63-64`

```java
account.createdAt = LocalDateTime.now();
account.updatedAt = LocalDateTime.now();
```

**问题**：`TenantAggregateRoot` 基类可能已提供审计字段自动填充。

**优化建议**：利用 `AbstractEntity` / `TenantAbstractEntity` 的审计字段自动填充机制，或通过 bone-metadata-sdk 的仓储拦截器统一处理。

---

### 6.2 IamExceptionHandler 不符合 API 规范

**位置**：`bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/config/IamExceptionHandler.java:31`

```java
return ResponseEntity.status(httpStatus).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
```

**问题**：错误响应缺少 `ProblemDetail` 结构（`type`, `title`, `detail`, `errorCode`, `traceId`），不符合 `Bone-API-规范.md §4.2`。

**优化建议**：实现 RFC 7807 `ProblemDetail` 信封，包含 `errorCode`（稳定字符串码）和 `traceId`。

---

### 6.3 缺少 OpenAPI 文档

**现状**：`bone-iam` 模块的 `pom.xml` 未引入 `springdoc-openapi` 依赖，Controller 上无 `@Operation`、`@Tag` 注解。

**优化建议**：
1. 引入 `springdoc-openapi-starter-webmvc-ui`
2. 为所有 Controller 添加 `@Tag` 和 `@Operation` 注解
3. 生成 OpenAPI YAML 纳入 CI 校验

---

## 七、DevOps 与部署问题（P2）

### 7.1 缺少 Dockerfile

**现状**：`docker-compose.yml` 引用 `docker/Dockerfile`，但需确认 `docker/` 目录是否存在。

---

### 7.2 缺少 CI/CD 流水线

**现状**：未发现 `.github/workflows/` 或 `.gitlab-ci.yml`。

**优化建议**：至少建立：
1. PR 门禁：编译 + 单元测试 + Spotless 格式检查
2. 主干门禁：全量构建 + ArchUnit + 静态分析
3. 发布门禁：镜像构建 + 安全扫描 + 部署测试环境

---

### 7.3 Spring Boot Admin 未启用

**现状**：`bone-parent/pom.xml` 声明了 `spring-boot-admin-starter-client`，但各模块未实际引入该依赖。

---

## 优化优先级总结

| 优先级 | 问题类别 | 优化项 | 预期收益 |
|--------|----------|--------|----------|
| **P0** | 安全 | 密码/密钥外置、JWT fail-fast、CORS收紧 | 防止凭据泄露 |
| **P0** | 架构 | Controller 入参用 Req、Command 不暴露到 adapter | DDD 合规 |
| **P1** | 测试 | CommandHandler 覆盖率 ≥70%、契约测试 | 代码可靠性 |
| **P1** | 文档 | 端口/路径/错误码对齐 | 减少认知负担 |
| **P1** | 配置 | Flyway 取舍、数据源统一、Redis fail-fast | 运维效率 |
| **P2** | 代码 | MapStruct、审计字段自动填充、ProblemDetail | 开发效率 |
| **P2** | DevOps | CI/CD、Dockerfile、OpenAPI 生成 | 持续交付 |

---

## 核心原则

**先堵安全漏洞（P0），再修架构合规（P0），然后补测试和文档（P1），最后优化代码质量和 DevOps（P2）。**
