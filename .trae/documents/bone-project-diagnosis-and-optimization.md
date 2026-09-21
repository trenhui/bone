# Bone 工程诊断与优化方案

## 概要

基于对 Bone 项目整体文档和代码的深度分析，从**架构合规性、安全性、工程实践、前端质量、基础设施**五个维度诊断问题，并给出基于业务最佳实践的优化建议。

---

## 一、现状分析

### 项目整体评估

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | ★★★★☆ | DDD 分层规范文档完善，ArchUnit 守卫到位，但 domain.service 存在框架依赖 |
| 安全性 | ★★☆☆☆ | 多处硬编码密码/密钥，JWT 默认弱密钥，token 存 localStorage |
| 工程实践 | ★★★☆☆ | CI/CD 有但覆盖不全，Flyway 声明未启用，Spotless 未绑定生命周期 |
| 前端质量 | ★★★☆☆ | 微前端架构完整，但 Shell 过度膨胀，共享包利用率低 |
| 基础设施 | ★★☆☆☆ | Docker/Compose 不完整，无 K8s 清单，无 CD 流水线 |

---

## 二、诊断问题清单

### P0 — 必须修复（安全/合规风险）

#### 2.1 安全隐患

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| SEC-01 | **数据库密码硬编码默认值** `mysql123` | [bone-iam/application.yml:15](file:///Users/renhui.trh/wps/bone/bone-platform/bone-iam/src/main/resources/application.yml#L15) | 开发环境泄露即生产泄露 |
| SEC-02 | **JWT 密钥硬编码弱默认值** `change-me-change-me-change-me-change-me-32bytes` | [bone-iam/application.yml:55](file:///Users/renhui.trh/wps/bone/bone-platform/bone-iam/src/main/resources/application.yml#L55) | 可伪造任意用户 token |
| SEC-03 | **默认管理员密码** `123456` | [bone-init.sql:209-211](file:///Users/renhui.trh/wps/bone/bone-init.sql#L209-L211) | 初始部署后未强制改密 |
| SEC-04 | **JWT Token 存 localStorage** | [bone-shell/App.tsx:312](file:///Users/renhui.trh/wps/bone/bone-frontend/apps/bone-shell/src/App.tsx#L312) | XSS 攻击可窃取 token |
| SEC-05 | **Spring 循环依赖允许** `allow-circular-refs: true` | [bone-iam/application.yml:3](file:///Users/renhui.trh/wps/bone/bone-platform/bone-iam/src/main/resources/application.yml#L3) | 掩盖设计缺陷，可能导致不可预测行为 |
| SEC-06 | **docker-compose 默认密码** `bone_root_pass` | [docker-compose.yml:8](file:///Users/renhui.trh/wps/bone/docker-compose.yml#L8) | 容器化部署默认弱密码 |

#### 2.2 DDD 架构违规

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| ARCH-01 | **domain.service 导入 Spring @Service** | [bone-iam/domain/service/AuthService.java:12](file:///Users/renhui.trh/wps/bone/bone-platform/bone-iam/src/main/java/com/bone/iam/domain/service/AuthService.java#L12) 等 7 处 | 领域层耦合框架，违反 DDD 纯净原则 |
| ARCH-02 | **domain.service 导入 Spring Security PasswordEncoder** | [bone-iam/domain/service/AuthService.java:11](file:///Users/renhui.trh/wps/bone/bone-platform/bone-iam/src/main/java/com/bone/iam/domain/service/AuthService.java#L11) | 领域服务不应依赖基础设施组件 |
| ARCH-03 | **domain.gateway 导入 Spring Web MultipartFile** | [bone-masterdata/domain/gateway/MasterDataExcelImportPort.java:5](file:///Users/renhui.trh/wps/bone/bone-platform/bone-masterdata/src/main/java/com/bone/masterdata/domain/gateway/MasterDataExcelImportPort.java#L5) | 领域层依赖 Web 框架 |
| ARCH-04 | **ArchUnit 允许 domain.service 依赖 Spring** | [bone-iam/ArchitectureTest.java:95-110](file:///Users/renhui.trh/wps/bone/bone-platform/bone-iam/src/test/java/com/bone/iam/architecture/ArchitectureTest.java#L95-L110) | 架构测试为违规开了白名单，而非强制合规 |

**说明**：ArchUnit 测试中 `domainServicesMayUseSpringSecurity` 规则显式允许 `domain.service` 使用 `org.springframework.stereotype` 和 `org.springframework.security`，这是对现状的妥协而非正确设计。按 DDD 规范，`AuthService`/`RoleService` 等应属于 `application` 层，`PasswordEncoder` 应通过接口注入。

---

### P1 — 应当修复（工程质量/可维护性）

#### 2.3 数据库迁移策略缺失

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| DB-01 | **Flyway 声明但未启用** `flyway.enabled: false` | [bone-iam/application.yml:11](file:///Users/renhui.trh/wps/bone/bone-platform/bone-iam/src/main/resources/application.yml#L11) | 无法增量迁移，生产环境全量重建风险极高 |
| DB-02 | **bone-init.sql 使用 DROP + CREATE** | [bone-init.sql:22-31](file:///Users/renhui.trh/wps/bone/bone-init.sql#L22-L31) | 每次初始化丢失数据，不适合生产 |
| DB-03 | **db/migration 目录仅含 README** | `bone-iam/src/main/resources/db/migration/README.md` | 无实际迁移脚本 |
| DB-04 | **md_entity/md_record 兼容表冗余** | [bone-init.sql:1145-1194](file:///Users/renhui.trh/wps/bone/bone-init.sql#L1145-L1194) | 同一业务概念两套表结构，增加维护负担 |

#### 2.4 代码质量工具未生效

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| QUAL-01 | **Spotless 未绑定 Maven 生命周期** | [bone-parent/pom.xml:388-400](file:///Users/renhui.trh/wps/bone/bone-parent/pom.xml#L388-L400) | 仅在 `pluginManagement` 中定义，需手动 `mvn spotless:apply` |
| QUAL-02 | **Checkstyle/PMD/SpotBugs 仅部分模块配置** | 各子模块 pom.xml 不一致 | 质量门禁不统一 |
| QUAL-03 | **JaCoCo 覆盖率门槛未全局强制** | 仅部分模块配置 | 测试覆盖率无法全局保障 |

#### 2.5 测试覆盖不足

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| TEST-01 | **ArchUnit 仅 4 个模块** | bone-iam、bone-system、bone-masterdata、bone-integration | bone-extension-engine、bone-metadata-sdk、studio-generator 无架构守卫 |
| TEST-02 | **集成测试偏少** | 各模块 src/test/ | 多数模块仅 Controller 层单元测试 |
| TEST-03 | **前端无测试** | bone-frontend/ | 无 Vitest 配置或测试文件 |

#### 2.6 前端架构问题

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| FE-01 | **bone-shell App.tsx 790+ 行** | [bone-shell/App.tsx](file:///Users/renhui.trh/wps/bone/bone-frontend/apps/bone-shell/src/App.tsx) | 登录、布局、菜单、微应用注册、主题全在一个文件 |
| FE-02 | **微应用入口硬编码 localhost** | [bone-shell/App.tsx:193-242](file:///Users/renhui.trh/wps/bone/bone-frontend/apps/bone-shell/src/App.tsx#L193-L242) | 无法适配不同环境 |
| FE-03 | **共享包利用率低** | packages/shared-* | 各微应用可能直接使用 antd/axios 而非共享封装 |
| FE-04 | **subapp-viewport 单容器** | [bone-shell/App.tsx:607-613](file:///Users/renhui.trh/wps/bone/bone-frontend/apps/bone-shell/src/App.tsx#L607-L613) | 所有微应用共享一个挂载点，路由切换时全量卸载/重载 |
| FE-05 | **登录页预填 admin 用户名** | [bone-shell/App.tsx:633-636](file:///Users/renhui.trh/wps/bone/bone-frontend/apps/bone-shell/src/App.tsx#L633-L636) | 安全风险，暴露默认账号 |
| FE-06 | **无 React Error Boundary** | bone-shell | 微应用加载失败时无优雅降级 |

---

### P2 — 建议优化（效率/规范提升）

#### 2.7 基础设施不完整

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| INFRA-01 | **docker-compose 仅 3 个服务** | [docker-compose.yml](file:///Users/renhui.trh/wps/bone/docker-compose.yml) | 缺少 bone-integration、bone-gateway、metadata-server、extension-studio、studio-generator |
| INFRA-02 | **CI 仅构建 3 个 Docker 镜像** | [ci.yml:116-119](file:///Users/renhui.trh/wps/bone/.github/workflows/ci.yml#L116-L119) | 与 docker-compose 同，覆盖不全 |
| INFRA-03 | **无 CD 流水线** | .github/workflows/ | 仅有 CI，无自动部署 |
| INFRA-04 | **无 K8s 清单** | 项目根目录 | 菜单有"K8s 部署"入口但无实现 |
| INFRA-05 | **Dockerfile 路径硬编码** | [docker/Dockerfile:34](file:///Users/renhui.trh/wps/bone/docker/Dockerfile#L34) | 仅支持 `bone-platform/` 下的模块 |

#### 2.8 文档与代码不一致

| # | 问题 | 影响 |
|---|------|------|
| DOC-01 | AGENTS.md 声称"无 Dockerfile"，实际已有 | 文档与实际不符 |
| DOC-02 | AGENTS.md 声称"无 CI/CD"，实际已有 GitHub Actions | 文档与实际不符 |
| DOC-03 | 技术栈声称使用 SA-Token，实际 IAM 使用自研 JWT | 认证方案不一致 |
| DOC-04 | 技术栈声称 RocketMQ/Seata，代码中未见实际集成 | 文档超前于实现 |

#### 2.9 其他工程问题

| # | 问题 | 影响 |
|---|------|------|
| MISC-01 | **JPA + MyBatis 混用** | 增加认知负担，ORM 策略不统一 |
| MISC-02 | **bone-parent 中定义了 `bone-auth`/`bone-codegen` 依赖但模块已不存在** | 依赖管理冗余 |
| MISC-03 | **版本号统一 1.0.0** | 无语义化版本，无法区分模块发布节奏 |

---

## 三、优化建议

### 3.1 安全加固（P0）

#### SEC-01~03: 消除硬编码密码

**方案**：所有敏感配置使用环境变量注入，移除默认值或改为启动时强制检查。

```yaml
# 修改前
password: ${BONE_DB_PASSWORD:mysql123}
secret-key: ${BONE_IAM_JWT_SECRET_KEY:change-me-change-me-change-me-change-me-32bytes}

# 修改后
password: ${BONE_DB_PASSWORD}  # 无默认值，缺失时启动失败
secret-key: ${BONE_IAM_JWT_SECRET_KEY}  # 启动时校验长度 >= 32 字节
```

**额外措施**：
- 添加 `@ConfigurationProperties` 校验：JWT 密钥长度不足时拒绝启动
- bone-init.sql 中 admin 初始密码改为 BCrypt 加密的强密码，首次登录强制改密
- 添加 Spring Boot `ApplicationRunner` 检测默认密码并告警

#### SEC-04: Token 存储优化

**方案**：改用 HttpOnly Cookie 存储 JWT，前端仅存储非敏感用户信息。

```
1. 后端 Login API 设置 HttpOnly + Secure + SameSite=Strict Cookie
2. 前端移除 localStorage.setItem('token', ...)
3. Axios 配置 withCredentials: true
4. 保留 localStorage 仅存 username 等非敏感展示信息
```

#### SEC-05: 消除循环依赖

**方案**：逐个排查循环依赖并重构，移除 `allow-circular-references: true`。

### 3.2 DDD 架构修正（P0）

#### ARCH-01~04: domain.service 去框架化

**方案**：分两步走——

**第一步（短期）**：将 `domain.service` 中的 Spring 依赖服务迁移到 `application` 层

```
com.bone.iam.domain.service.AuthService       → com.bone.iam.application.service.AuthService
com.bone.iam.domain.service.RoleService       → com.bone.iam.application.service.RoleService
com.bone.iam.domain.service.PermissionService  → com.bone.iam.application.service.PermissionService
com.bone.iam.domain.service.AuditService      → com.bone.iam.application.service.AuditService
```

**第二步（中期）**：引入端口接口解耦

```java
// domain 层定义端口
public interface PasswordEncoderPort {
    String encode(CharSequence rawPassword);
    boolean matches(CharSequence rawPassword, String encodedPassword);
}

// infrastructure 层实现
@Component
public class PasswordEncoderPortAdapter implements PasswordEncoderPort {
    private final PasswordEncoder delegate = new BCryptPasswordEncoder();
    // ...
}
```

**ARCH-03 修复**：`MasterDataExcelImportPort` 应使用 `InputStream` 替代 `MultipartFile`

```java
// 修改前
void importExcel(MultipartFile file);
// 修改后
void importExcel(InputStream data, String originalFilename);
```

**ARCH-04 修复**：收紧 ArchUnit 规则，移除 `domainServicesMayUseSpringSecurity` 白名单

### 3.3 数据库迁移策略（P1）

#### DB-01~03: 启用 Flyway

**方案**：

1. 将 `bone-init.sql` 拆分为 Flyway 迁移脚本
2. 命名规范：`V1.0.0__create_iam_tables.sql`、`V1.0.1__create_system_tables.sql`...
3. 启用 Flyway：`flyway.enabled: true`
4. 保留 `bone-init.sql` 仅用于全新开发环境快速搭建
5. 添加 `V1.0.0__init_data.sql` 包含初始数据（admin 账号等）

#### DB-04: 消除兼容表

**方案**：制定迁移计划，将 `md_entity`/`md_record` 数据迁移至 `mdm_entity`/`mdm_record`，然后删除兼容表。

### 3.4 代码质量工具生效（P1）

#### QUAL-01: Spotless 绑定生命周期

```xml
<!-- bone-parent/pom.xml 中添加 -->
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### QUAL-02~03: 统一质量门禁

在 `bone-parent/pom.xml` 的 `<plugins>` 中全局绑定 Checkstyle + JaCoCo，各模块继承即可。

### 3.5 前端架构优化（P1）

#### FE-01: Shell 拆分

将 `App.tsx` 拆分为：

```
src/
├── App.tsx              # 顶层 Provider + Router
├── auth/
│   ├── LoginPage.tsx    # 登录页
│   └── Authorized.tsx   # 路由守卫
├── layout/
│   ├── MainLayout.tsx   # 主布局
│   ├── Sidebar.tsx      # 侧边栏
│   └── Header.tsx       # 顶栏
├── micro/
│   ├── registerApps.ts  # 微应用注册配置
│   └── MicroAppContainer.tsx  # 微应用容器 + ErrorBoundary
├── theme/
│   └── ThemeProvider.tsx # 主题管理
└── config/
    └── menuConfig.ts    # 菜单配置
```

#### FE-02: 微应用配置环境化

```typescript
// config/microApps.ts
const MICRO_APP_REGISTRY = [
  { name: 'bone-iam-app', entry: import.meta.env.VITE_IAM_APP_ENTRY || '//localhost:3003', ... },
  // ...
];
```

#### FE-04: 微应用容器优化

每个微应用路由使用独立容器，并添加 ErrorBoundary：

```tsx
<Route path="/iam/*" element={<MicroAppContainer name="bone-iam-app" />} />
<Route path="/metadata/*" element={<MicroAppContainer name="bone-metadata-app" />} />
```

#### FE-06: 添加 ErrorBoundary

```tsx
class MicroAppErrorBoundary extends React.Component {
  state = { hasError: false };
  static getDerivedStateFromError() { return { hasError: true }; }
  render() {
    if (this.state.hasError) {
      return <Result status="error" title="微应用加载失败" extra={<Button onClick={() => window.location.reload()}>刷新重试</Button>} />;
    }
    return this.props.children;
  }
}
```

### 3.6 基础设施补全（P2）

#### INFRA-01~02: 完善 Docker/CI 覆盖

- docker-compose 添加所有后端服务
- CI matrix 扩展到所有可独立部署的模块
- 添加前端 Docker 构建和 Nginx 配置

#### INFRA-03: 添加 CD 流水线

```yaml
# .github/workflows/cd.yml
deploy-staging:
  needs: docker-build
  runs-on: ubuntu-latest
  steps:
    - name: Deploy to Staging
      run: kubectl apply -f k8s/ -n bone-staging
```

#### INFRA-05: Dockerfile 通用化

修改 COPY 路径逻辑，支持 `bone-engine/` 下的模块。

### 3.7 文档同步（P2）

- 更新 AGENTS.md：反映 Docker/CI 已存在
- 标注 SA-Token vs JWT 的实际使用情况
- 标注 RocketMQ/Seata 为"规划中"而非"已集成"

### 3.8 测试补全（P1）

| 优先级 | 行动 |
|--------|------|
| P1 | 为 bone-extension-engine、bone-metadata-sdk、studio-generator 添加 ArchUnit 测试 |
| P1 | 为各模块核心 CommandHandler/QueryHandler 添加单元测试 |
| P2 | 添加关键业务流程的集成测试（使用 Testcontainers） |
| P2 | 前端添加 Vitest 配置和关键组件测试 |

---

## 四、实施路线图

### 阶段 1：安全加固（1-2 周）

1. 移除所有配置文件中的默认密码/密钥
2. JWT 密钥启动校验
3. 首次登录强制改密
4. Token 存储改 HttpOnly Cookie
5. 移除 `allow-circular-references`

### 阶段 2：架构合规（2-3 周）

1. domain.service 迁移至 application 层
2. 引入端口接口解耦 PasswordEncoder
3. 修复 MasterDataExcelImportPort
4. 收紧 ArchUnit 规则
5. Spotless 绑定 validate 阶段

### 阶段 3：工程基础（2-3 周）

1. 启用 Flyway，拆分迁移脚本
2. 消除 md_entity/md_record 兼容表
3. Shell 前端拆分
4. 微应用配置环境化
5. 添加 ErrorBoundary
6. 补全 ArchUnit 测试

### 阶段 4：基础设施（2-3 周）

1. 完善 docker-compose
2. 扩展 CI 覆盖
3. 添加 CD 流水线
4. 更新文档与代码一致性

---

## 五、假设与决策

| 决策点 | 当前选择 | 备注 |
|--------|----------|------|
| domain.service 是否迁移 | 建议迁移至 application 层 | 需评估对现有 CommandHandler 的调用链影响 |
| JPA + MyBatis 混用 | 建议统一为 MyBatis（主数据路径）+ JPA（简单 CRUD） | 不建议强行统一，但需明确使用边界 |
| Flyway 启用时机 | 建议阶段 3 | 需先确保现有数据可迁移 |
| 前端状态管理 | 建议评估是否真的需要 Redux Toolkit | 当前 Shell 未使用，微应用各自管理即可 |
| SA-Token vs JWT | 建议明确选择一个 | 当前 IAM 用自研 JWT，SA-Token 仅在 parent pom 声明 |

---

## 六、验证步骤

1. **安全验证**：`grep -r "mysql123\|change-me" bone-platform/` 应无结果
2. **架构验证**：`mvn test -pl bone-iam` ArchUnit 测试通过（收紧规则后）
3. **格式化验证**：`mvn spotless:check` 全模块通过
4. **前端验证**：`npm run lint` + `npm run build` 全微应用通过
5. **Docker 验证**：`docker-compose up` 所有服务健康启动
6. **CI 验证**：GitHub Actions 全阶段绿色
