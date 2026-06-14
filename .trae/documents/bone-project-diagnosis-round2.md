# Bone 工程诊断与优化方案（第二轮）

## 概要

基于第一轮优化后的代码状态，重新诊断剩余和新发现问题。第一轮已修复：硬编码密码、allow-circular-references、Spotless 绑定、前端 ErrorBoundary/环境化/移除预填admin、ARCH-03 MultipartFile、清理冗余依赖/mybatis残留、studio-generator JPA→JDBC。

---

## 一、剩余问题清单

### P0 — 必须修复

#### 1.1 安全隐患（新发现）

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| SEC-07 | **bone-blueprint 硬编码密码** `password: root` | [bone-blueprint/application.yml:7](file:///Users/renhui.trh/wps/bone/bone-blueprint/src/main/resources/application.yml#L7) | 明文密码，无环境变量 |
| SEC-08 | **bone-metadata-engine 默认密码** `smartmeta_pass` | [application-enterprise.yml:17](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata-engine/enterprise-config/application-enterprise.yml#L17) | 企业级配置含默认密码 |
| SEC-09 | **bone-blueprint JPA 配置残留** | [bone-blueprint/application.yml:9-16](file:///Users/renhui.trh/wps/bone/bone-blueprint/src/main/resources/application.yml#L9-L16) | pom.xml 无 JPA 依赖但 yml 有 JPA 配置，启动会报错 |

#### 1.2 DDD 架构违规（未修复）

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| ARCH-01 | **bone-iam domain.service 依赖 Spring** | 6 个文件使用 `@Service`，1 个使用 `PasswordEncoder` | `domain.service` 包应纯净，`@Service` 和 `PasswordEncoder` 属于框架层 |
| ARCH-02 | **bone-integration domain.service 依赖 Spring** | 3 个文件使用 `@Service` | 同上 |
| ARCH-05 | **bone-metadata-engine enterprise 配置** `allow-bean-definition-overriding: true` | [application-enterprise.yml:11](file:///Users/renhui.trh/wps/bone/bone-engine/bone-metadata-engine/enterprise-config/application-enterprise.yml#L11) | 掩盖 Bean 定义冲突 |

### P1 — 应当修复

#### 1.3 bone-blueprint 工程规范问题

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| BP-01 | **未继承 bone-parent** | [bone-blueprint/pom.xml:7-9](file:///Users/renhui.trh/wps/bone/bone-blueprint/pom.xml#L7-L9) | 独立 groupId/artifactId，不继承统一版本管理 |
| BP-02 | **硬编码 Spring Boot 版本** | [bone-blueprint/pom.xml:17](file:///Users/renhui.trh/wps/bone/bone-blueprint/pom.xml#L17) | `3.2.5` 写死，应继承 parent |
| BP-03 | **JPA 配置但无 JPA 依赖** | application.yml:9-16 | yml 有 `jpa.hibernate` 配置，但 pom.xml 无 JPA 依赖 |
| BP-04 | **RocketMQ 依赖但 mq-enabled=false** | [bone-blueprint/pom.xml:163-167](file:///Users/renhui.trh/wps/bone/bone-blueprint/pom.xml#L163-L167) | Outbox 中继声明但未启用，增加启动负担 |
| BP-05 | **Nacos/OpenFeign 版本硬编码** | [bone-blueprint/pom.xml:152-160](file:///Users/renhui.trh/wps/bone/bone-blueprint/pom.xml#L152-L160) | 应继承 Spring Cloud BOM |

#### 1.4 domain.service 架构修正方案

**当前状态**：9 个 `domain.service` 类使用 `@Service`，1 个使用 `PasswordEncoder`

**bone-iam** (6 个):
- `AuditService` — `@Service`
- `PermissionService` — `@Service`
- `AuthService` — `@Service` + `PasswordEncoder`
- `RoleService` — `@Service`
- `PasswordPolicyValidator` — `@Service`
- `RoleHierarchyResolver` — `@Service`

**bone-integration** (3 个):
- `FlowMonitorService` — `@Service`
- `FlowService` — `@Service`
- `ConnectorService` — `@Service`

**修正策略**：
1. 将带业务逻辑的 `domain.service` 迁移到 `application.service`（符合 DDD §14.3.1 S1/S2/S3）
2. `PasswordEncoder` 通过端口接口解耦（`domain` 层定义接口，`infrastructure` 层实现）
3. 纯工具类（`RoleHierarchyResolver`、`PasswordPolicyValidator`）移除 `@Service`，改为在 `application` 层通过 `new` 调用

### P2 — 建议优化

#### 1.5 其他工程问题

| # | 问题 | 说明 |
|---|------|------|
| MISC-03 | **bone-blueprint 未加入根 pom.xml 聚合** | 根 pom.xml 的 `<modules>` 不含 bone-blueprint |
| MISC-04 | **bone-metadata-engine enterprise 配置过度** | 声明了 GraphQL、Camunda、Drools、Kafka、AI 等但代码中未实现 |
| MISC-05 | **docker-compose 密码仍为默认值** | `bone_root_pass` 等 |

---

## 二、优化执行计划

### 步骤 1：修复 bone-blueprint 安全和配置问题

**文件**：`bone-blueprint/src/main/resources/application.yml`
- 移除硬编码密码 `root`，改为 `${BONE_DB_PASSWORD:}`
- 移除 JPA 配置（pom.xml 无 JPA 依赖）
- 添加 `application-dev.yml` 提供开发环境默认值

**文件**：`bone-blueprint/pom.xml`
- 继承 `bone-parent`
- 移除硬编码版本号
- 移除未使用的 RocketMQ 依赖（mq-enabled=false）

### 步骤 2：修复 bone-metadata-engine enterprise 默认密码

**文件**：`bone-engine/bone-metadata-engine/enterprise-config/application-enterprise.yml`
- `smartmeta_pass` → `${DB_PASSWORD:}`
- 移除 `allow-bean-definition-overriding: true`

### 步骤 3：修复 domain.service 架构违规

**bone-iam**：
1. `AuthService` → `application.service.AuthService`，`PasswordEncoder` 通过端口接口注入
2. `RoleService` → `application.service.RoleService`
3. `PermissionService` → `application.service.PermissionService`
4. `AuditService` → `application.service.AuditService`
5. `RoleHierarchyResolver` — 移除 `@Service`，改为纯工具类
6. `PasswordPolicyValidator` — 移除 `@Service`，改为纯工具类

**bone-integration**：
1. `FlowService` → `application.service.FlowService`
2. `FlowMonitorService` → `application.service.FlowMonitorService`
3. `ConnectorService` → `application.service.ConnectorService`

**新增端口接口**：
- `domain.gateway.PasswordEncoderPort` — 密码编码端口
- `infrastructure.security.SpringPasswordEncoderAdapter` — 适配器实现

### 步骤 4：收紧 ArchUnit 规则

**文件**：各模块 `ArchitectureTest.java`
- 移除 `domainServicesMayUseSpringSecurity` 白名单
- 确保 `domain.service` 不允许依赖 Spring

---

## 三、假设与决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| domain.service 迁移 | 迁移至 application.service | 符合 DDD §14.3.1，domain 层应纯净 |
| 纯工具类处理 | 移除 @Service，改为静态/实例工具 | `RoleHierarchyResolver` 无状态，不需要 Spring 管理 |
| bone-blueprint 是否继承 parent | 是 | 统一版本管理，避免版本漂移 |
| RocketMQ 依赖 | 移除 | mq-enabled=false，引入但不用增加启动负担 |
| bone-metadata-engine enterprise 配置 | 保留但清理默认密码 | 企业配置模板有价值，但不应含默认密码 |

---

## 四、验证步骤

1. `grep -r "password: root\|smartmeta_pass" bone-*/` 应无结果
2. `mvn clean compile -DskipTests=true` 全模块通过
3. `mvn test -pl bone-platform/bone-iam` ArchUnit 通过（收紧规则后）
4. `mvn spotless:check` 全模块通过
5. 前端 `npm run build` 通过
