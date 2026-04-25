# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 项目概述

**Bone** - 企业级全栈开源原生快速开发平台

- 核心理念：Build Once, Natively Everywhere
- 四大引擎：智能元数据引擎 + 企业主数据平台 + ExtPoint 扩展引擎 + 集成引擎
- 代码生成：配置即代码，减少70%重复编码

---

## 项目架构

### 模块结构

```
bone/
├── bone-parent/          # 父 POM，依赖版本管理
├── bone-framework/       # 框架层（web/security/core/datasource）
├── bone-engine/          # 引擎层（metadata/masterdata/extension/integration）
├── bone-platform/        # 平台模块（iam/system/audit）
├── bone-business/        # 业务模块（order/inventory）
├── bone-blueprint/       # DDD 架构蓝图（最佳实践参考）
├── bone-sdk/             # SDK 客户端
├── bone-tool/            # 工具集（codegen）
└── bone-frontend/        # 前端 Monorepo（React 18 + Ant Design 5）
    ├── apps/bone-shell/     # 主应用入口
    ├── apps/bone-iam-app/   # IAM 子应用
    ├── apps/bone-system-app/ # 系统管理子应用
    ├── packages/core/       # 核心工具库
    └── packages/ui/         # UI 组件库
```

### 后端 DDD 架构规范

```
com.bone.{module}/
├── adapter/              # 适配层
│   ├── web/controller/   # REST API 控制器
│   └── rpc/              # RPC 接口
├── application/          # 应用层
│   ├── service/          # 应用服务
│   ├── dto/              # 数据传输对象
│   └── converter/        # 转换器
├── domain/               # 领域层
│   ├── entity/           # 领域实体
│   ├── repository/       # 仓储接口（仅定义，不实现）
│   ├── enums/            # 枚举
│   ├── exception/        # 领域异常
│   └── service/          # 领域服务
└── infrastructure/       # 基础设施层
    ├── persistence/      # 仓储实现 + MyBatis Mapper
    └── config/           # 配置类
```

**关键约束**：
- Repository 接口定义在 domain，实现放在 infrastructure/persistence
- 依赖注入使用构造函数，final 修饰依赖
- 禁止返回 null，使用 Optional&lt;T&gt;
- 异常使用 BusinessException，错误码统一管理

---

## 常用命令

### 后端构建与测试

```bash
# 全量编译（首次必须执行）
mvn clean install -DskipTests

# 编译单个模块
mvn compile -pl bone-blueprint

# 全量测试
mvn test

# 单个模块测试
mvn test -pl bone-blueprint

# 单个测试类
mvn test -pl bone-blueprint -Dtest=OrderServiceTest

# 单个测试方法
mvn test -pl bone-blueprint -Dtest=OrderServiceTest#testCreateOrder

# 查看测试覆盖率报告（JaCoCo）
open bone-blueprint/target/site/jacoco/index.html

# 打包跳过测试
mvn package -DskipTests
```

### 前端构建与测试

```bash
# 进入前端目录
cd bone-frontend

# 安装依赖
npm install

# 启动开发服务（默认启动 shell 应用）
npm run dev

# 全量构建
npm run build

# 代码检查
npm run lint

# 单元测试
npm run test

# 测试覆盖率
npm run test:coverage
```

### 数据库

```bash
# 初始化脚本（MySQL 8.x）
mysql -u root -p < bone-init.sql

# 数据库默认配置
URL: jdbc:mysql://localhost:3306/bone
User: root
Password: (根据环境配置)
```

---

## 技术栈与版本约束

| 层级 | 技术栈 | 版本要求 |
|------|--------|----------|
| **JDK** | Java | ≥ 17 |
| **后端框架** | Spring Boot | 3.2.x |
| **ORM** | MyBatis Plus | 最新稳定版 |
| **测试** | JUnit 5 + JaCoCo | 覆盖率 > 80% |
| **前端框架** | React | ≥ 18 |
| **UI** | Ant Design | 5.x |
| **语言** | TypeScript | ≥ 5.2 |
| **构建工具** | Vite | ≥ 4.x |
| **包管理** | npm workspaces | Monorepo |
| **数据库** | MySQL | ≥ 8.0 |

---

## Agentic Engineering 工作流

### 核心机制：契约驱动开发

```
/plan → 生成 Contract.yaml (L1/L2) → /build → 生成代码 → /test → 验证 → /ship → 创建 PR
```

### 四个标准命令

| 命令 | 作用 | 产出 | 确认人 |
|------|------|------|--------|
| `/plan` | 规划功能，分析复杂度 | Contract.yaml（L1/L2） | 架构师（强制） |
| `/build` | 基于契约生成代码 + 单元测试 | 代码 + 单元测试 | AI 自检 |
| `/test` | 集成测试 + 契约一致性验证 | 测试报告 | AI（L2 需人工确认） |
| `/ship` | Guardian 安全审查 + 质量门禁 | PR + 双轨度量 | Guardian Agent |

### Ralph Loop 自动迭代

```bash
/ralph-loop "功能需求描述"
# 自动执行 plan → build → test → ship 循环，直到完成
```

### 契约分级

| 级别 | 适用场景 | 特性 |
|------|----------|------|
| **L1** | 简单 CRUD、列表查询 | 数据模型 + API 契约 + 基本 guardrails |
| **L2** | 复杂业务、版本管理、工作流 | L1 全部 + domain_rules（带 severity） + 严格质量门禁 |

### 分级自愈策略

| 级别 | 错误类型 | 处理方式 |
|------|----------|----------|
| **L1** | 编译错误、语法错误、缺少 import | 自动修复（最多3次重试） |
| **L2-A** | 单元测试失败、Mock 配置、断言顺序 | 自动修复（最多2次重试） |
| **L2-B** | API 变更、Schema 变更、核心逻辑变更 | 暂停，人工确认 |
| **L3** | 契约违背、业务规则违反 | 阻断，上报 |
| **L4** | 安全红线（硬编码密钥、SQL 注入风险等） | 立即阻断报警 |

---

## 核心红线（CI 强制拦截）

1. **禁止修改 `generated/` 目录**下的自动生成代码
2. **测试覆盖率 < 80% 禁止合并**（L2 功能 ≥ 85%）
3. **禁止返回 `null`**，使用 `Optional<T>`
4. **Contract 所有者必须与 PR 作者一致**（Contract 锁机制）
5. **禁止硬编码密钥、密码、Token 等敏感信息**
6. **禁止直接推送 main/master 分支**，所有变更通过 PR

---

## Commit 规范

```
type(scope): description

type: feat|fix|docs|refactor|test|chore
scope: 模块名称（可选）
description: 小写开头，不超过 50 字符
```

**示例**：
```
feat(blueprint): add order crud api
fix(metadata): fix entity version increment bug
test(iam): add permission service unit tests
```

**Agentic 度量信息**（自动附加）：
```
# Agentic: L1 attempts=3, L1 successes=3, build_time=8min
```

---

## 常见问题

### 编译失败？
```bash
# 确保在根目录执行过全量编译
mvn clean install -DskipTests
# 然后单独编译目标模块
mvn compile -pl your-module
```

### 前端依赖问题？
```bash
cd bone-frontend
rm -rf node_modules package-lock.json
npm install
```

### 数据库连接问题？
检查 `application.yml` 中的 datasource 配置，或设置环境变量：
```bash
export BONE_DB_URL=jdbc:mysql://localhost:3306/bone
export BONE_DB_USERNAME=root
export BONE_DB_PASSWORD=your-password
```

---

## 分支命名规范

```
feature/{feature-name}@{owner}
fix/{bug-description}@{owner}
hotfix/{critical-issue}@{owner}
experiment/{experiment-name}@{owner}
```

**示例**：`feature/order-versioning@zhang`, `fix/permission-bug@li`

---

## 契约锁机制

当 Contract.yaml 中设置 `meta.lock: true` 时：
- 只有 `meta.owner` 指定的用户可以修改此契约
- 其他人提交的 PR 会被 CI 自动阻断
- 适用于大型重构或核心功能

---

## 质量门禁

| 指标 | L1 阈值 | L2 阈值 |
|------|---------|---------|
| 单元测试覆盖率 | ≥ 80% | ≥ 85% |
| 最大圈复杂度 | ≤ 10 | ≤ 8 |
| 方法最大行数 | ≤ 50 | ≤ 40 |
| 类最大行数 | ≤ 500 | ≤ 400 |
| API 契约一致性 | 100% | 100% |
