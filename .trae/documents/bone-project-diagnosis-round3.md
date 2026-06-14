# Bone 项目诊断报告 — 第三轮

> 基于前两轮修复后的代码库现状，继续诊断剩余和新发现的问题。

---

## 一、当前状态总结

前两轮已完成以下修复：
- 硬编码密码清理（6+ 文件）
- JWT 弱密钥清理（4 文件）
- `allow-circular-references` / `allow-bean-definition-overriding` 清理
- Spotless 绑定 validate 生命周期
- 前端微应用入口环境变量化 + ErrorBoundary
- MultipartFile 从 domain 层移除
- 残余 mybatis 配置清理
- studio-generator JPA→JDBC 迁移
- bone-blueprint 修复（parent POM、密码、JPA 配置）
- bone-metadata-engine 默认密码清理
- domain.service 迁移至 application.service（bone-iam 4 个 + bone-integration 3 个）
- PasswordEncoderPort + SpringPasswordEncoderAdapter 端口/适配器模式

---

## 二、诊断发现的问题（按优先级排序）

### P0-1：旧 domain.service 文件未删除 — 运行时 Bean 冲突风险

**严重性**：Critical  
**影响**：Spring 容器启动时可能因同名 Bean 冲突导致应用无法启动

**现状**：
- **bone-iam** `domain/service/` 下 6 个旧文件仍存在：
  - `AuthService.java` — @Deprecated，@Service 已注释，但仍 import `PasswordEncoder`
  - `RoleService.java` — @Deprecated，@Service 已注释
  - `PermissionService.java` — @Deprecated，@Service 已注释
  - `AuditService.java` — @Deprecated，@Service 已注释
  - `RoleHierarchyResolver.java` — **仍有 `@Service` 注解！** 与 DomainUtilityConfig 中的 @Bean 注册冲突
  - `PasswordPolicyValidator.java` — **仍有 `@Component` 注解！** 与 DomainUtilityConfig 中的 @Bean 注册冲突
- **bone-integration** `domain/service/` 下 3 个旧文件仍存在：
  - `FlowService.java` — **仍有 `@Service` 注解！** 与 application.service.FlowService 冲突
  - `FlowMonitorService.java` — **仍有 `@Service` 注解！** 与 application.service.FlowMonitorService 冲突
  - `ConnectorService.java` — **仍有 `@Service` 注解！** 与 application.service.ConnectorService 冲突

**修复方案**：
1. 删除 bone-iam `domain/service/` 下全部 6 个旧文件
2. 删除 bone-integration `domain/service/` 下全部 3 个旧文件
3. 删除 `DomainUtilityConfig.java`（因 RoleHierarchyResolver 和 PasswordPolicyValidator 不再需要手动注册）

**注意**：RoleHierarchyResolver 和 PasswordPolicyValidator 需要重新处理（见 P0-2）

---

### P0-2：RoleHierarchyResolver 使用 QueryBuilder — 违反 DDD 读侧 DSL 禁令

**严重性**：High  
**影响**：违反 DDD §P0-5 规则，domain 层禁止使用 QueryBuilder/FluentQuery

**现状**：
- `RoleHierarchyResolver` 在 `domain.service` 包中使用了 `QueryBuilder.from(Role.class).where(Role::getId).in(...).list()`
- 按 DDD 规范，QueryBuilder 是读侧 DSL，只能在 QueryHandler 中使用（标注 `@ReadSideOnly`）
- 该类当前仍有 `@Service` 注解

**修复方案**：
- 将 `RoleHierarchyResolver` 迁移至 `application.service` 包，保留 `@Service` 注解
- 或者：将 `resolveClosure` 方法改为接受 `Map<Long, Role>` 参数（由调用方通过 Repository 查询后传入），使该类变为纯领域工具

**推荐**：迁移至 `application.service`，因为该方法的核心逻辑是批量查询父角色，属于应用层编排

---

### P0-3：PasswordPolicyValidator 仍在 domain.service 中带 @Component

**严重性**：High  
**影响**：domain 层不应有 Spring 注解

**现状**：
- `PasswordPolicyValidator` 在 `domain.service` 包中，有 `@Component` 注解
- 该类是纯工具类，无任何外部依赖（仅依赖 java 标准库和 bone-core 异常类）
- DomainUtilityConfig 手动注册了 @Bean，与 @Component 注解重复注册

**修复方案**：
- 将 `PasswordPolicyValidator` 移至 `domain.model.valueobject` 或 `common.util` 包
- 移除 `@Component` 注解（已由 DomainUtilityConfig 注册）
- 或更优方案：移至 `application.service`，标注 `@Service`，删除 DomainUtilityConfig

---

### P0-4：FlowController 直接注入 domain.repository — 违反 DDD 分层

**严重性**：High  
**影响**：Controller 直接依赖 domain.repository，违反 DDD §14.3.2

**现状**：
```java
// FlowController.java
private final IntegrationFlowRepository flowRepository;  // domain.repository
private final FlowService flowService;                     // application.service ✓
```
- Controller 中 `flowRepository.findById(id)` 和 `flowRepository.save(flow)` 直接操作仓储
- 应通过 CommandHandler 或 QueryHandler 编排

**修复方案**：
1. 将 `detail` 接口的查询逻辑提取到 QueryHandler
2. 将 `activate`/`deactivate`/`delete` 接口的写逻辑提取到 CommandHandler
3. Controller 只注入 Handler，不再直接注入 Repository

---

### P0-5：bone-extension-studio JPA 配置残留

**严重性**：Medium  
**影响**：application.yml 有 JPA/Hibernate 配置，但 pom.xml 无 JPA 依赖

**现状**：
```yaml
# bone-extension-studio application.yml
jpa:
  database-platform: org.hibernate.dialect.H2Dialect
  hibernate:
    ddl-auto: none
  show-sql: false
```
- pom.xml 中无 `spring-boot-starter-data-jpa` 依赖
- 该模块使用 `bone-metadata-sdk` 的自定义注解（`@Table`, `@Column` 等），不依赖 JPA
- 与 studio-generator 之前的问题完全相同

**修复方案**：
1. 删除 application.yml 中的 `jpa:` 配置块
2. 如需 DDL 初始化，改用 `spring.sql.init` 配置

---

### P0-6：bone-extension-studio 安全配置问题

**严重性**：Medium  
**影响**：H2 控制台暴露安全风险

**现状**：
```yaml
h2:
  console:
    enabled: true
    settings:
      web-allow-others: true  # 允许远程访问 H2 控制台
```
- `web-allow-others: true` 允许任何远程 IP 访问 H2 控制台
- 生产环境若误启用 H2，将暴露数据库

**修复方案**：
1. 将 `web-allow-others` 改为 `false` 或通过环境变量控制
2. 将 `h2.console.enabled` 改为 `${H2_CONSOLE_ENABLED:false}`

---

### P0-7：MinIO 默认凭证

**严重性**：Medium  
**影响**：默认 MinIO 凭证 `minioadmin/minioadmin` 是公开已知的管理员凭证

**现状**：
```yaml
# bone-iam application.yml
minio:
  access-key: ${BONE_MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${BONE_MINIO_SECRET_KEY:minioadmin}
```

**修复方案**：
- 移除默认值，改为 `${BONE_MINIO_ACCESS_KEY:}` 和 `${BONE_MINIO_SECRET_KEY:}`
- 在 `application-dev.yml` 中提供开发环境默认值

---

### P0-8：JWT 默认密钥仍为开发值

**严重性**：Medium  
**影响**：JWT 密钥 `dev-only-secret-key-minimum-32-bytes-long` 是公开已知的

**现状**：
- `bone-iam application.yml`: `secret-key: ${BONE_IAM_JWT_SECRET_KEY:${BONE_JWT_SECRET:dev-only-secret-key-minimum-32-bytes-long}}`
- `bone-extension-studio application.yml`: `secret-key: ${BONE_IAM_JWT_SECRET:${BONE_JWT_SECRET:dev-only-secret-key-minimum-32-bytes-long}}`

**修复方案**：
- 主配置文件中移除默认值：`${BONE_IAM_JWT_SECRET_KEY:${BONE_JWT_SECRET:}}`
- 在 `application-dev.yml` 中提供开发环境默认值（并添加注释说明仅限开发使用）

---

### P0-9：ArchUnit 规则需要更新

**严重性**：Medium  
**影响**：domain.service 迁移后，ArchUnit 白名单规则需要收紧

**现状**：
- **bone-iam ArchitectureTest** 有 3 个需要修改的规则：
  1. `domainServicesMayUseSpringSecurity` — 允许 domain.service 使用 Spring，迁移后应删除
  2. `domainServiceShouldResideInDomain` — 强制 *Service 在 domain 包，迁移后应删除或改为检查 application.service
  3. `domainCoreShouldOnlyDependOnAllowedPackages` — 排除了 domain.service，迁移后应移除排除

**修复方案**：
1. 删除 `domainServicesMayUseSpringSecurity` 规则
2. 删除 `domainServiceShouldResideInDomain` 规则（或改为允许 application.service）
3. 修改 `domainCoreShouldOnlyDependOnAllowedPackages`，移除 `resideOutsideOfPackage("..domain.service..")` 排除条件
4. 收紧 ArchUnit freeze 基线（删除旧 domain.service 的冻结记录）

---

### P0-10：bone-extension-studio pom.xml 版本管理问题

**严重性**：Low  
**影响**：版本未从 parent POM 继承

**现状**：
- `spring.boot.version` 和 `lombok.version` 属性已在 parent POM 中管理
- `jjwt.version` 硬编码为 `0.12.6`

**修复方案**：
1. 移除 `spring.boot.version` 和 `lombok.version` 属性（从 parent 继承）
2. 将 `jjwt.version` 提升到 parent POM 的 dependencyManagement 中

---

### P0-11：bone-metadata-engine 企业配置 JPA 问题

**严重性**：Low（需确认）  
**影响**：企业配置中有 JPA/Hibernate 配置，但该模块可能确实使用 JPA

**现状**：
```yaml
# application-enterprise.yml
jpa:
  open-in-view: false
  hibernate:
    ddl-auto: validate
  properties:
    hibernate:
      dialect: org.hibernate.dialect.PostgreSQLDialect
```
- 该模块是企业级配置，可能确实需要 JPA（PostgreSQL + Hibernate）
- 需要确认该模块的 pom.xml 是否有 JPA 依赖

**修复方案**：
- 如果该模块确实使用 JPA：保留配置，但确保 pom.xml 有对应依赖
- 如果不使用 JPA：删除 JPA 配置块（与 studio-generator 相同处理）

---

## 三、优化执行计划

### 步骤 1：删除旧 domain.service 文件 + 重新安置工具类

**操作**：
1. 将 `RoleHierarchyResolver` 从 `domain.service` 迁移至 `application.service`（保留 @Service，改用 Repository 替代 QueryBuilder）
2. 将 `PasswordPolicyValidator` 从 `domain.service` 迁移至 `application.service`（保留 @Service）
3. 删除 `DomainUtilityConfig.java`（不再需要手动注册）
4. 删除 bone-iam `domain/service/` 下全部 6 个旧文件
5. 删除 bone-integration `domain/service/` 下全部 3 个旧文件
6. 更新所有 import 引用

**涉及文件**：
- 删除：`bone-iam/domain/service/AuthService.java`
- 删除：`bone-iam/domain/service/RoleService.java`
- 删除：`bone-iam/domain/service/PermissionService.java`
- 删除：`bone-iam/domain/service/AuditService.java`
- 删除：`bone-iam/domain/service/RoleHierarchyResolver.java`
- 删除：`bone-iam/domain/service/PasswordPolicyValidator.java`
- 删除：`bone-iam/application/config/DomainUtilityConfig.java`
- 删除：`bone-integration/domain/service/FlowService.java`
- 删除：`bone-integration/domain/service/FlowMonitorService.java`
- 删除：`bone-integration/domain/service/ConnectorService.java`
- 新增：`bone-iam/application/service/RoleHierarchyResolver.java`（从 domain.service 迁移，改用 Criteria 替代 QueryBuilder）
- 新增：`bone-iam/application/service/PasswordPolicyValidator.java`（从 domain.service 迁移）
- 修改：所有引用旧 domain.service 的 import 语句

### 步骤 2：修复 FlowController 分层违规

**操作**：
1. 创建 `FlowDetailQueryHandler` 处理流程详情查询
2. 创建 `ActivateFlowCommand` + `ActivateFlowHandler`
3. 创建 `DeactivateFlowCommand` + `DeactivateFlowHandler`
4. 创建 `DeleteFlowCommand` + `DeleteFlowHandler`
5. 修改 FlowController 只注入 Handler

**涉及文件**：
- 新增：`bone-integration/application/query/handler/FlowDetailQueryHandler.java`
- 新增：`bone-integration/application/command/cmd/ActivateFlowCommand.java`
- 新增：`bone-integration/application/command/cmd/DeactivateFlowCommand.java`
- 新增：`bone-integration/application/command/cmd/DeleteFlowCommand.java`
- 新增：`bone-integration/application/command/handler/ActivateFlowHandler.java`
- 新增：`bone-integration/application/command/handler/DeactivateFlowHandler.java`
- 新增：`bone-integration/application/command/handler/DeleteFlowHandler.java`
- 修改：`bone-integration/adapter/web/controller/FlowController.java`

### 步骤 3：清理 bone-extension-studio 配置

**操作**：
1. 删除 application.yml 中的 `jpa:` 配置块
2. 修复 H2 控制台安全配置
3. 清理 pom.xml 版本属性

**涉及文件**：
- 修改：`bone-extension-studio/src/main/resources/application.yml`
- 修改：`bone-extension-studio/pom.xml`

### 步骤 4：安全配置加固

**操作**：
1. bone-iam：MinIO 凭证移除默认值，创建 application-dev.yml
2. bone-iam + bone-extension-studio：JWT 密钥移除默认值，在 dev profile 中提供

**涉及文件**：
- 修改：`bone-iam/src/main/resources/application.yml`
- 修改/新增：`bone-iam/src/main/resources/application-dev.yml`
- 修改：`bone-extension-studio/src/main/resources/application.yml`
- 修改/新增：`bone-extension-studio/src/main/resources/application-dev.yml`

### 步骤 5：收紧 ArchUnit 规则

**操作**：
1. 删除 bone-iam ArchitectureTest 中的 `domainServicesMayUseSpringSecurity` 规则
2. 删除 bone-iam ArchitectureTest 中的 `domainServiceShouldResideInDomain` 规则
3. 修改 bone-iam ArchitectureTest 中的 `domainCoreShouldOnlyDependOnAllowedPackages`，移除 domain.service 排除
4. 收紧 ArchUnit freeze 基线

**涉及文件**：
- 修改：`bone-iam/src/test/java/com/bone/iam/architecture/ArchitectureTest.java`

### 步骤 6：验证

**操作**：
1. `mvn spotless:apply` — 格式化
2. `mvn clean compile -DskipTests=true` — 全量编译
3. 安全检查：`grep -r "password: root\|minioadmin\|dev-only-secret-key" --include="*.yml"`
4. ArchUnit 测试：`mvn test -pl bone-platform/bone-iam,bone-platform/bone-integration -Dtest="*ArchitectureTest"`

---

## 四、问题汇总表

| # | 问题 | 严重性 | 模块 | 状态 |
|---|------|--------|------|------|
| P0-1 | 旧 domain.service 文件未删除 | Critical | bone-iam, bone-integration | 待修复 |
| P0-2 | RoleHierarchyResolver 使用 QueryBuilder | High | bone-iam | 待修复 |
| P0-3 | PasswordPolicyValidator 在 domain 层带 @Component | High | bone-iam | 待修复 |
| P0-4 | FlowController 直接注入 domain.repository | High | bone-integration | 待修复 |
| P0-5 | bone-extension-studio JPA 配置残留 | Medium | bone-extension-studio | 待修复 |
| P0-6 | H2 控制台 web-allow-others: true | Medium | bone-extension-studio | 待修复 |
| P0-7 | MinIO 默认凭证 minioadmin | Medium | bone-iam | 待修复 |
| P0-8 | JWT 默认密钥 dev-only-secret-key | Medium | bone-iam, bone-extension-studio | 待修复 |
| P0-9 | ArchUnit 规则需要更新 | Medium | bone-iam | 待修复 |
| P0-10 | pom.xml 版本属性未继承 | Low | bone-extension-studio | 待修复 |
| P0-11 | bone-metadata-engine JPA 配置待确认 | Low | bone-metadata-engine | 待确认 |
