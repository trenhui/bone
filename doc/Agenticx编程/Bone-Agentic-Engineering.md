# Bone Agentic Engineering（统一指南）

> **版本**：v3.1（合并版） | **维护**：`doc/Agenticx编程/`  
> **仓库落地**：命令与 Agent 定义以 **`.claude/`** 为准；项目约束以根目录 **`CLAUDE.md`**、**`AGENTS.md`** 为准；DDD 分层见 **`doc/architecture/Bone-DDD-最终实践方案.md`**。

本文档由以下材料合并去重：`Bone-Agentic-Engineering-编程规范`（v3.0 方法论）、`Bone-Agentic-Engineering-编程配置`（v3.1 可落地配置）、`doc/claude` 下最佳实践摘要。历史草稿 `编程配置0.1` 已废止。

## v3.1 相对 v3.0 的关键变更

| 变更项 | 说明 |
|--------|------|
| 契约 owner | 契约 YAML 内不再维护 `owner`/`lock`；改由 **`.github/CODEOWNERS`** 约束 |
| L2 分级 | `domain_rules` 支持 `severity: L2`；L2-B 超时 10 分钟可降级 `safe-default` |
| Ralph Loop | 增加 `--min-iterations`、`--convergence-threshold`，避免无效迭代 |
| 前端覆盖率 | Vitest JSON 报告解析（替代不稳定文本解析） |
| 夜间任务 | 推荐本机 `tmux + crontab`，而非在 CI 内直接跑长时 Agent |
| 契约可执行化 | `meta.executable` 支持未来导出 OpenAPI |

## 目录

1. [概述与核心理念](#一概述与核心理念)
2. [终局架构](#二终局架构)
3. [工程化目录结构](#三工程化目录结构)
4. [核心机制详解](#四核心机制详解)
5. [标准化 Commands](#五标准化-commands)
6. [多 Agent 并行开发](#六多-agent-并行开发)
7. [可落地配置与契约示例](#七可落地配置与契约示例)
8. [CI/CD 流水线](#八cicd-流水线)
9. [效能度量体系](#九效能度量体系)
10. [12 周落地路线图](#十12周落地路线图)
11. [24 小时极速上手](#十一24小时极速上手)
12. [故障排查指南](#十二故障排查指南)
13. [VP/CTO 汇报总结](#十三vpcto-汇报总结)
14. [术语表](#十四术语表)

---

## 一、概述与核心理念

### 1.1 系统定位

**Bone Agentic Engineering OS** 是一套基于 Anthropic Claude Code 最佳实践、Boris Cherny Ralph Loop 方法论、以及企业级软件工程规范构建的 **AI 原生软件工程操作系统**。

### 1.2 核心公式

```
5 Commands + 分层 Contract + Git 分支锁 + CI 分层裁决 + 切片执行 + Ralph Loop = 可治理的 AI 原生 SDLC
```

### 1.3 范式转变

| 维度 | 传统开发 | Bone Agentic OS |
|------|----------|-----------------|
| 编码方式 | 手动编码 | 意图 → 契约 → AI 执行 → CI 裁决 |
| 规范管理 | 文档 + Code Review | 固化在 Contract + CI 门禁 |
| 团队协作 | 会议 + 沟通协调 | Git 分支主权 + 契约接口 |
| 流程度量 | 不可见、难量化 | Commit Message + Checkpoint 双轨度量 |
| 迭代方式 | 人工驱动 | Ralph Loop 自动迭代到完成 |

### 1.4 核心目标

| 指标 | 目标值 |
|------|--------|
| CRUD 功能交付 | ≤30 分钟 |
| 测试覆盖率 | ≥80%（L2 ≥85%） |
| 安全漏洞前置拦截 | ≥85% |
| 契约违背率 | <5% |
| 夜间任务完成率 | ≥70% |


## 二、终局架构

### 2.1 三层架构图

```
┌─────────────────────────────────────────────────────────────────┐
│  L1: COMMAND INTERFACE（命令层）⭐唯一入口                        │
│                                                                   │
│  ┌──────┐    ┌───────┐    ┌──────┐    ┌──────┐    ┌────────────┐ │
│  │/plan │ → │/build │ → │/test │ → │/ship │ → │/ralph-loop │ │
│  └──────┘    └───────┘    └──────┘    └──────┘    └────────────┘ │
│      │           │           │           │              │        │
│      ▼           ▼           ▼           ▼              ▼        │
│  生成契约    生成代码     集成测试      交付PR       自动迭代      │
├─────────────────────────────────────────────────────────────────┤
│  L2: CONTRACT LAYER（契约层）⭐唯一真理源                         │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  .claude/contracts/                                         │ │
│  │  ├── {feature}.yaml        # 核心契约（L1:30秒/L2:5分钟）    │ │
│  │  ├── {feature}.check.yaml  # 动态检查点（AI自动更新）        │ │
│  │  └── {feature}.local.yaml  # 本地覆盖（Git忽略）            │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  特性：分层YAML + 状态分离 + Git版本化 + Contract锁               │
├─────────────────────────────────────────────────────────────────┤
│  L3: EXECUTION & GOVERNANCE（执行与治理层）                       │
│                                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ @architect  │  │ @engineer   │  │ @guardian   │              │
│  │   (Opus)    │  │  (Sonnet)   │  │  (Sonnet)   │              │
│  │  设计+契约  │  │  代码+自愈  │  │  审查+阻断  │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  技能包：backend-java | frontend-react | db-migration       │ │
│  │  机制：切片执行 | L1-L4分级自愈 | Hooks自动化 | CI分层裁决   │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流图

```
用户意图
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  /plan                                                          │
│  • 扫描代码库 → 判断复杂度 → 生成契约 → 初始化Checkpoint         │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  /build                                                         │
│  • 切片1: API层 → 编译检查(L1自愈)                               │
│  • 切片2: Service层 → 编译检查(L1自愈)                           │
│  • 切片3: Mapper层 → 编译检查(L1自愈)                            │
│  • 切片4: 单元测试 → 测试检查(L1-L2A自愈)                        │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  /test                                                          │
│  • 集成测试 → 覆盖率检查 → 契约一致性 → L2-B暂停确认              │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  /ship                                                          │
│  • Guardian审查 → 双轨度量 → 自动创建PR                          │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
  PR Created ✅
```


## 三、工程化目录结构

### 3.1 完整目录树

```
bone/                                       # Gitee 仓库根目录
│
├── CLAUDE.md                               # 团队宪法（根上下文）
├── README.md                               # 项目说明
├── .gitignore                              # Git 忽略配置
├── pom.xml                                 # 父 Maven 配置（多模块）
│
├── .claude/                                # ⭐ AI 配置根目录
│   ├── settings.json                       # 全局设置（权限/自愈/模型路由）
│   │
│   ├── commands/                           # 标准化命令定义
│   │   ├── plan.md                         # `/plan` 命令
│   │   ├── build.md                        # `/build` 命令
│   │   ├── test.md                         # `/test` 命令
│   │   ├── ship.md                         # `/ship` 命令
│   │   └── ralph-loop.md                   # `/ralph-loop` 命令
│   │
│   ├── agents/                             # Agent 角色定义
│   │   ├── architect.md                    # 架构师 Agent
│   │   ├── engineer.md                     # 工程师 Agent
│   │   ├── guardian.md                     # 守护者 Agent
│   │   │
│   │   └── skills/                         # 动态技能包
│   │       ├── backend-java.md             # Java 后端技能包
│   │       ├── frontend-react.md           # React 前端技能包
│   │       └── db-migration.md             # 数据库迁移技能包
│   │
│   ├── skills/                             # 版本化原子技能（全局）
│   │   ├── code-localization/
│   │   │   ├── v1.0/SKILL.md
│   │   │   └── v2.0/SKILL.md
│   │   ├── editing/
│   │   │   ├── v1.0/SKILL.md
│   │   │   └── v2.1/SKILL.md
│   │   ├── test-gen/
│   │   │   ├── v1.0/SKILL.md
│   │   │   └── v1.8/SKILL.md
│   │   ├── code-review/
│   │   │   ├── v1.0/SKILL.md
│   │   │   └── v2.0/SKILL.md
│   │   └── owasp-security/
│   │       └── v2026.1/SKILL.md
│   │
│   ├── contracts/                          # 任务契约
│   │   ├── {feature-name}.yaml             # 核心契约
│   │   ├── {feature-name}.check.yaml       # 动态检查点
│   │   └── {feature-name}.local.yaml       # 本地覆盖
│   │
│   ├── hooks/                              # Hooks 自动化
│   │   └── hooks.json                      # Pre/Post/Stop 配置
│   │
│   ├── state/                              # 会话状态（按 feature）
│   │   └── {feature-name}/
│   │       ├── checkpoint.json             # 真实 metrics
│   │       └── events.jsonl                # 事件流
│   │
│   ├── logs/                               # 决策日志
│   │   ├── build-trace.log
│   │   ├── decision.log
│   │   └── ralph-loop.log
│   │
│   └── archive/                            # 契约归档
│       └── {feature-name}.yaml.backup
│
├── .github/workflows/                      # CI/CD 配置
│   ├── agentic-guard.yml                   # 主 CI 流水线
│   └── nightly-ralph.yml                   # 夜间任务调度
│
├── scripts/                                # 工具脚本
│   ├── analyze-metrics.sh                  # 月度效能分析
│   ├── setup-hooks.sh                      # Hooks 安装脚本
│   └── validate-contract.sh                # 契约验证脚本
│
├── bone-metadata/                          # 元数据引擎模块
│   ├── pom.xml
│   ├── src/main/java/...
│   └── src/test/java/...
│
├── bone-masterdata/                        # 主数据平台模块
│   ├── pom.xml
│   └── src/...
│
├── bone-extpoint/                          # 扩展点引擎模块
│   ├── pom.xml
│   └── src/...
│
├── bone-integration/                       # 集成引擎模块
│   ├── pom.xml
│   └── src/...
│
├── bone-common/                            # 公共模块
│   ├── pom.xml
│   └── src/...
│
├── bone-admin/                             # 管理后台
│   ├── pom.xml
│   └── src/...
│
├── bone-ui/                                # React 前端
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── components/
│       ├── pages/
│       ├── hooks/
│       ├── stores/
│       ├── services/
│       ├── types/
│       └── utils/
│
└── docs/                                   # 文档
    ├── design/                             # 设计文档
    ├── decisions/                          # ADR 决策记录
    └── agentic-playbook/                   # Agentic 工程手册
        ├── 24h-onboarding.md
        ├── troubleshooting.md
        └── best-practices.md
```

### 3.2 .gitignore 配置

```gitignore
# Bone Agentic OS 专用忽略规则

# AI 状态文件（包含敏感度量数据）
.claude/state/
.claude/logs/
.claude/archive/

# 本地覆盖契约
.claude/contracts/*.local.yaml

# 运行时生成文件
generated/
*.log
*.tmp

# 构建产物
target/
node_modules/
dist/
build/
.coverage/

# IDE
.idea/
*.iml
.vscode/
.DS_Store

# 环境变量
.env
.env.local
.env.*.local

# 测试报告
test-output/
jacoco-report/
coverage/
```


## 四、核心机制详解

### 4.1 分支主权 + Contract 锁

#### 分支命名规范

```bash
# 格式：feature/{功能描述}@{开发者标识}
feature/metadata-entity-crud@alex
feature/user-auth-module@zhang
fix/security-vulnerability@wang

# 热修复分支
hotfix/critical-bug@alex

# 实验性分支
experiment/new-architecture@alex
```

#### Contract 锁完整配置

```yaml
# .claude/contracts/metadata-entity-crud.yaml
meta:
  feature: metadata-entity-crud
  owner: alex
  lock: true
  lock_reason: "大型重构，避免冲突"
  created_at: "2026-04-23T10:00:00Z"
  updated_at: "2026-04-23T15:30:00Z"
  based_on: "commit-a1b2c3d4e5f6"
  
# 分支基础信息
branch:
  name: "feature/metadata-entity-crud@alex"
  base: "develop"
  draft_pr: true
```

#### CI 锁检查逻辑

```bash
# 伪代码
if contract.meta.lock == true:
    if PR.author != contract.meta.owner:
        error = f"❌ 契约已被 {contract.meta.owner} 锁定"
        error += f"锁定原因：{contract.meta.lock_reason}"
        exit 1
```

### 4.2 契约分层详细说明

#### L1 契约完整示例（元数据实体 CRUD）

```yaml
# .claude/contracts/metadata-entity-crud.yaml
meta:
  feature: metadata-entity-crud
  owner: alex
  level: L1
  template: crud-base
  lock: true
  
mission: "为 bone-metadata 模块增加元数据实体的完整 CRUD 管理功能"

# 数据模型
data_model:
  entity_name: "MetadataEntity"
  table_name: "metadata_entity"
  fields:
    - name: id
      type: Long
      primary: true
      auto_increment: true
    - name: entityCode
      type: String
      length: 64
      nullable: false
      unique: true
    - name: entityName
      type: String
      length: 128
      nullable: false
    - name: tableName
      type: String
      length: 64
      nullable: false
    - name: fields
      type: JSON
      nullable: true
    - name: status
      type: Integer
      default: 1
    - name: createdAt
      type: LocalDateTime
      auto: "creation"
    - name: updatedAt
      type: LocalDateTime
      auto: "update"

# API 契约
api_contract:
  base_path: "/api/metadata/entities"
  
  endpoints:
    # 分页查询
    - method: GET
      path: ""
      query:
        page: { type: int, default: 1, min: 1 }
        size: { type: int, default: 20, min: 1, max: 100 }
        keyword: { type: string, required: false }
        status: { type: int, required: false }
      response:
        status: 200
        body:
          code: 200
          message: "success"
          data:
            list: [{ id, entityCode, entityName, status, createdAt }]
            total: long
            page: int
            size: int

    # 根据 ID 查询
    - method: GET
      path: "/{id}"
      path_params:
        id: { type: long }
      response:
        status: 200
        body:
          code: 200
          data:
            id: long
            entityCode: string
            entityName: string
            tableName: string
            fields: array
            status: int
            createdAt: datetime
            updatedAt: datetime

    # 创建
    - method: POST
      path: ""
      request:
        entityCode: { type: string, required: true, pattern: "^[A-Z][A-Z0-9_]*$" }
        entityName: { type: string, required: true, maxLength: 128 }
        tableName: { type: string, required: true }
        fields: { type: array, required: false }
      response:
        status: 201
        body:
          code: 201
          data:
            id: long
            entityCode: string
            entityName: string

    # 更新
    - method: PUT
      path: "/{id}"
      path_params:
        id: { type: long }
      request:
        entityName: { type: string, required: true }
        tableName: { type: string, required: true }
        fields: { type: array, required: false }
      response:
        status: 200
        body:
          code: 200
          data:
            id: long
            entityCode: string
            entityName: string
            updatedAt: datetime

    # 删除（软删除）
    - method: DELETE
      path: "/{id}"
      path_params:
        id: { type: long }
      response:
        status: 200
        body:
          code: 200
          message: "删除成功"
          data: { success: true }

# 质量门禁
guardrails:
  coverage: ">80%"
  max_cyclomatic: 10
  max_method_lines: 50
  max_class_lines: 500
  
  block_patterns:
    - "**/security/*"
    - "generated/**"
    - "**/test/resources/**"
  
  allow_patterns:
    - "bone-metadata/**"
  
  naming_convention:
    controller: "*Controller"
    service: "*Service"
    mapper: "*Mapper"
    dto: "*DTO"
```

#### L2 契约完整示例（元数据版本管理）

```yaml
# .claude/contracts/metadata-versioning.yaml
meta:
  feature: metadata-versioning
  owner: alex
  level: L2
  based_on: "commit-a1b2c3d4e5f6"
  lock: true
  
mission: "为元数据实体增加版本管理功能，支持版本对比和回滚"

# 数据模型扩展
data_model:
  additional_tables:
    - name: metadata_entity_version
      fields:
        - name: id
          type: Long
          primary: true
        - name: entityId
          type: Long
          foreign_key: "metadata_entity.id"
        - name: versionNo
          type: Integer
          nullable: false
        - name: snapshot
          type: JSON
          nullable: false
        - name: changeLog
          type: Text
          nullable: true
        - name: operator
          type: String
          nullable: false
        - name: createdAt
          type: LocalDateTime

# API 契约
api_contract:
  base_path: "/api/metadata/entities"
  
  endpoints:
    # 获取版本列表
    - method: GET
      path: "/{id}/versions"
      path_params:
        id: { type: long }
      query:
        page: { type: int, default: 1 }
        size: { type: int, default: 20 }
      response:
        status: 200
        data:
          versions:
            - versionNo: int
              changeLog: string
              operator: string
              createdAt: datetime

    # 获取版本详情
    - method: GET
      path: "/{id}/versions/{versionNo}"
      path_params:
        id: { type: long }
        versionNo: { type: int }
      response:
        status: 200
        data:
          versionNo: int
          snapshot: object
          changeLog: string
          operator: string
          createdAt: datetime

    # 版本对比
    - method: GET
      path: "/{id}/versions/compare"
      query:
        fromVersion: { type: int, required: true }
        toVersion: { type: int, required: true }
      response:
        status: 200
        data:
          differences:
            - field: string
              from: any
              to: any
              changeType: "ADDED|MODIFIED|DELETED"

    # 回滚到指定版本
    - method: POST
      path: "/{id}/rollback/{versionNo}"
      path_params:
        id: { type: long }
        versionNo: { type: int }
      request:
        reason: { type: string, required: true }
      response:
        status: 200
        data:
          success: boolean
          newVersion: int
          message: string

# 业务规则
domain_rules:
  - id: RULE-001
    name: "版本号递增规则"
    desc: "版本号必须从 1 开始递增，每次变更 +1"
    check: |
      newVersion == latestVersion + 1
    severity: L3
    auto_fix: false
    
  - id: RULE-002
    name: "回滚不可删除历史"
    desc: "回滚时创建新版本快照，不可删除或覆盖历史版本"
    check: |
      rollback 操作必须 INSERT 新记录，不能 DELETE 或 UPDATE 历史
    severity: L3
    auto_fix: false
    
  - id: RULE-003
    name: "字段变更记录 diff"
    desc: "每次版本变更必须记录 change_log，对比前后字段差异"
    check: |
      changeLog 字段必须有内容且包含字段差异
    severity: L1
    auto_fix: true
    fix_pattern: "自动生成字段差异 JSON"

  - id: RULE-004
    name: "操作人记录"
    desc: "所有版本操作必须记录操作人"
    check: |
      operator 字段不能为空
    severity: L2
    auto_fix: false

# 质量门禁（L2 更严格）
guardrails:
  coverage: ">85%"
  max_cyclomatic: 8
  max_method_lines: 40
  max_class_lines: 400
  
  block_patterns:
    - "**/security/*"
    - "generated/**"
    - "**/rollback/**"  # 回滚操作额外保护
```

### 4.3 分级自愈详细策略

#### 自愈级别决策树

```
                    ┌─────────────────┐
                    │  检测到问题      │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
        ┌─────────┐   ┌─────────┐   ┌─────────┐
        │ 编译问题 │   │ 测试问题 │   │ 逻辑问题 │
        └────┬────┘   └────┬────┘   └────┬────┘
             │             │             │
             ▼             ▼             ▼
        ┌─────────┐   ┌─────────┐   ┌─────────┐
        │   L1    │   │  L2-A   │   │  L2-B   │
        │ 自动修复 │   │ 自动修复 │   │ 暂停确认 │
        │ 最多3次  │   │ 最多2次  │   │         │
        └─────────┘   └─────────┘   └─────────┘

        ┌─────────┐   ┌─────────┐
        │ 规则违背 │   │ 安全红线 │
        └────┬────┘   └────┬────┘
             │             │
             ▼             ▼
        ┌─────────┐   ┌─────────┐
        │   L3    │   │   L4    │
        │ 阻断上报 │   │ 立即报警 │
        │ 不修复   │   │ 通知安全 │
        └─────────┘   └─────────┘
```

#### L1 自动修复详细规则

| 错误类型 | 检测方式 | 修复动作 | 最大尝试 |
|----------|----------|----------|----------|
| 缺少 import | 编译错误 `cannot find symbol` | 自动推断类全限定名并添加 import | 3 |
| 语法错误 | 编译错误 `syntax error` | 根据上下文修正语法 | 2 |
| 类型不匹配 | `incompatible types` | 自动类型转换或修正变量类型 | 2 |
| 缺少分号 | 编译错误 `; expected` | 在语句末尾添加分号 | 1 |
| 未使用的变量 | 编译警告 | 删除或使用 `@SuppressWarnings` | 1 |
| 格式问题 | Prettier/ESLint | 自动格式化 | 1 |
| TS 类型错误 | TypeScript 编译 | 修正类型定义或添加类型断言 | 2 |

#### L2-A 自动修复详细规则

| 错误类型 | 检测方式 | 修复动作 | 最大尝试 |
|----------|----------|----------|----------|
| 断言参数顺序错误 | JUnit `AssertionFailedError` | 交换 expected/actual 位置 | 2 |
| 缺少 Mock 配置 | Mockito `MissingMethodInvocation` | 添加 `when().thenReturn()` | 2 |
| 空指针异常 | `NullPointerException` | 添加 null 检查或 `Optional` | 2 |
| Mock 参数匹配错误 | `ArgumentMismatch` | 修正 `any()` 或具体参数 | 2 |
| 测试超时 | 测试超时失败 | 增加超时时间或优化代码 | 1 |

#### L2-B 暂停确认详细规则

| 场景 | 检测方式 | 确认问题 | 建议选项 |
|------|----------|----------|----------|
| API 变更 | 检测到现有接口签名变化 | 是否破坏向后兼容？ | [ ] 新增版本 [ ] 拒绝变更 [ ] 接受 |
| 数据库 Schema 变更 | 检测到 DDL 变化 | 是否需要数据迁移？ | [ ] 生成迁移脚本 [ ] 拒绝 |
| 业务逻辑变更 | 检测到核心逻辑修改 | 影响哪些下游？ | [ ] 继续 [ ] 回滚 [ ] 扩大影响分析 |
| 依赖升级 | 检测到 pom.xml/package.json 变更 | 是否经过兼容性测试？ | [ ] 执行兼容测试 [ ] 拒绝 |

#### L3 阻断规则

| 规则类型 | 示例 | 阻断消息 |
|----------|------|----------|
| 契约违背 | API 路径与契约不一致 | `❌ 契约违背: 期望 POST /api/.../entities，实际 POST /api/.../ent` |
| 核心业务规则 | 版本号不递增 | `❌ 阻断: 版本号必须递增 (RULE-001)` |
| 不可变性违反 | 尝试删除历史版本 | `❌ 阻断: 历史版本不可删除 (RULE-002)` |

#### L4 安全红线

| 安全规则 | 检测模式 | 动作 |
|----------|----------|------|
| SQL 注入 | `Statement.execute`、字符串拼接 SQL | 立即阻断 + 报警安全团队 |
| XSS 风险 | `dangerouslySetInnerHTML` 使用未消毒 | 立即阻断 |
| 硬编码密钥 | `password = "xxx"`、`secret = "xxx"` | 立即阻断 |
| 越权风险 | 缺少权限检查的敏感操作 | 阻断 + 标记 High |
| 日志注入 | 用户输入直接写入日志 | 阻断 |

### 4.4 切片执行详细机制

#### 后端切片配置

```yaml
# .claude/contracts/slice-config.yaml
slices:
  backend:
    - id: api
      name: "API 层"
      files: ["**/controller/**/*.java", "**/dto/**/*.java"]
      dependencies: []
      check_command: "mvn compile -pl {module}"
      auto_fix_level: L1
      
    - id: service
      name: "Service 层"
      files: ["**/service/**/*.java"]
      dependencies: ["api"]
      check_command: "mvn compile -pl {module}"
      auto_fix_level: L1
      
    - id: mapper
      name: "Mapper 层"
      files: ["**/mapper/**/*.java", "**/repository/**/*.java", "src/main/resources/mapper/**/*.xml"]
      dependencies: ["api", "service"]
      check_command: "mvn compile -pl {module}"
      auto_fix_level: L1
      
    - id: test
      name: "单元测试"
      files: ["src/test/java/**/*.java"]
      dependencies: ["api", "service", "mapper"]
      check_command: "mvn test -pl {module}"
      auto_fix_level: L2-A

  frontend:
    - id: types
      name: "类型定义"
      files: ["src/types/**/*.ts", "**/*.types.ts"]
      dependencies: []
      check_command: "npm run type-check"
      auto_fix_level: L1
      
    - id: api
      name: "API 服务层"
      files: ["src/services/**/*.ts", "src/api/**/*.ts"]
      dependencies: ["types"]
      check_command: "npm run type-check"
      auto_fix_level: L1
      
    - id: hooks
      name: "Hooks 层"
      files: ["src/hooks/**/*.ts", "src/hooks/**/*.tsx"]
      dependencies: ["types", "api"]
      check_command: "npm run test -- --run"
      auto_fix_level: L1
      
    - id: components
      name: "组件层"
      files: ["src/components/**/*.tsx", "src/pages/**/*.tsx"]
      dependencies: ["types", "api", "hooks"]
      check_command: "npm run test -- --run"
      auto_fix_level: L1-L2A
      
    - id: styles
      name: "样式层"
      files: ["**/*.css", "**/*.scss", "**/*.module.css"]
      dependencies: ["components"]
      check_command: "npm run lint:css"
      auto_fix_level: L1
```

### 4.5 Ralph Loop 详细配置

```bash
/ralph-loop "任务描述" \
  --completion-promise "AGENTIC_DONE" \
  --max-iterations 30 \
  --min-iterations 1 \
  --checkpoint-interval 5 \
  --checkpoint-path ".claude/state/{feature}/checkpoint.json" \
  --auto-commit true \
  --auto-commit-message "ralph-loop: {iteration}/{max} - {status}" \
  --notify "slack://#agentic-alerts" \
  --notify-on-completion true \
  --notify-on-failure true \
  --timeout-minutes 120 \
  --retry-on-failure true \
  --retry-delay-seconds 30
```

#### Ralph Loop 迭代流程图

```
┌─────────────────────────────────────────────────────────────────┐
│  Start Ralph Loop                                               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Iteration 1                                                 ││
│  │ • Execute /plan → Generate Contract                         ││
│  │ • Execute /build → Generate Code + Unit Test                ││
│  │ • Execute /test → Integration Test                          ││
│  │ • Check: DONE? → No → Save checkpoint → Continue            ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Iteration 2                                                 ││
│  │ • Detect issues from previous iteration                     ││
│  │ • Apply L1/L2-A auto-fix                                    ││
│  │ • Re-execute /build → /test                                 ││
│  │ • Check: DONE? → No → Save checkpoint → Continue            ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Iteration N                                                 ││
│  │ • All tests pass                                            ││
│  │ • Coverage ≥ threshold                                      ││
│  │ • Contract consistent                                       ││
│  │ • Output: AGENTIC_DONE                                      ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ /ship                                                       ││
│  │ • Guardian review                                           ││
│  │ • Create PR                                                 ││
│  │ • Notify completion                                         ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```



## 五、标准化 Commands

**唯一入口**：`/plan` → `/build` → `/test` → `/ship`；复杂/夜间任务增加 `/ralph-loop`。

| 命令 | 作用 | 产出 | 确认人 |
|------|------|------|--------|
| `/plan` | 扫描代码库、判级 L1/L2、生成分层契约 | `Contract.yaml` + `checkpoint.json` | 架构师（强制） |
| `/build` | 切片生成代码与单元测试 | 代码 + 单测 | AI 自检（L1/L2-A 自愈） |
| `/test` | 集成测试、覆盖率、契约一致性 | 测试报告 | AI（L2-B 需人工或超时降级） |
| `/ship` | Guardian 审查、度量双写、创建 Draft PR | PR | Guardian / CI |
| `/ralph-loop` | 自动迭代直至 `completion-promise` 或收敛 | Draft PR / checkpoint | 人类设定后离开 |

命令的完整执行步骤、参数与示例见仓库：

- `.claude/commands/plan.md`
- `.claude/commands/build.md`
- `.claude/commands/test.md`
- `.claude/commands/ship.md`
- `.claude/commands/ralph-loop.md`

**分级自愈（摘要）**：L1 编译/格式/import（自动，≤3 次）；L2-A 单测/Mock（自动，≤2 次）；L2-B API/Schema/核心逻辑（暂停或 10 分钟超时降级）；L3 契约违背（阻断）；L4 安全红线（阻断并升级）。

---

## 六、多 Agent 并行开发

### 6.1 Agent Teams 启用配置

```json
// .claude/settings.json
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  },
  "agent_teams": {
    "enabled": true,
    "max_parallel_agents": 10,
    "communication_protocol": "SendMessage+TaskUpdate",
    "default_timeout_minutes": 60,
    "auto_merge_on_completion": false
  }
}
```

### 6.2 多 Agent 编排脚本

**文件路径**：`scripts/agent-orchestrator.sh`

```bash
#!/bin/bash
# Agent Teams 编排脚本

FEATURE=$1
AGENTS=${2:-3}

echo "启动 Agent Teams for $FEATURE"

# 创建 worktree 分支
for i in $(seq 1 $AGENTS); do
    git worktree add ../${FEATURE}-agent-$i feature/${FEATURE}@agent-$i
done

# 在不同终端启动 Agent
for i in $(seq 1 $AGENTS); do
    tmux new-window -n "agent-$i" "cd ../${FEATURE}-agent-$i && claude --agent team-lead"
done

echo "✅ 已启动 $AGENTS 个 Agent，使用 'tmux a -t agent-orchestrator' 查看"
```


## 七、可落地配置与契约示例

> 下列内容与仓库 **`.claude/`** 对齐，用于离线阅读与复制；若与仓库文件冲突，**以 `.claude/` 与 `CLAUDE.md` 为准**。

### 7.1 推荐目录结构

```
bone/
├── CLAUDE.md
├── .gitignore
├── pom.xml
├── .claude/
│   ├── settings.json
│   ├── commands/
│   │   ├── plan.md
│   │   ├── build.md
│   │   ├── test.md
│   │   ├── ship.md
│   │   └── ralph-loop.md
│   ├── agents/
│   │   ├── architect.md
│   │   ├── engineer.md
│   │   ├── guardian.md
│   │   └── skill-profiles/
│   │       ├── backend-java.md
│   │       ├── frontend-react.md
│   │       └── db-migration.md
│   ├── skills-library/
│   │   ├── code-localization/v2.0/SKILL.md
│   │   ├── editing/v2.1/SKILL.md
│   │   ├── test-gen/v1.8/SKILL.md
│   │   ├── code-review/v2.0/SKILL.md
│   │   └── owasp-security/v2026.1/SKILL.md
│   ├── contracts/
│   │   └── (示例文件)
│   ├── hooks/
│   │   └── hooks.json
│   ├── state/
│   ├── logs/
│   └── playbook/
│       ├── 24h-onboarding.md
│       └── troubleshooting.md
├── .github/
│   ├── CODEOWNERS
│   └── workflows/
│       ├── agentic-guard.yml
│       └── nightly-trigger.yml
├── scripts/
│   ├── nightly-ralph.sh
│   ├── security-check.sh
│   ├── auto-format.sh
│   ├── auto-commit.sh
│   ├── contract2openapi.py
│   └── analyze-metrics.sh
├── bone-metadata/
├── bone-masterdata/
├── bone-extpoint/
├── bone-integration/
├── bone-common/
├── bone-admin/
├── bone-ui/
│   ├── package.json
│   ├── vitest.config.ts
│   └── src/
└── docs/
    ├── design/
    └── decisions/
```

---

## 三、核心配置文件（完整内容）

### 3.1 团队宪法：`CLAUDE.md`

```markdown
# Bone Agentic Engineering 团队宪法 v3.1

## 🎯 核心理念
人定义意图，AI 负责实现，契约保证质量，CI 担任裁判，Ralph Loop 保障 7×24 小时交付。

## 🏗️ 技术栈（锁定版本）
- 后端：Java 21 + Spring Boot 3.5.x + MySQL Connector 9.3.0 + MyBatis Plus 3.5.x
- 前端：React 18.2 + TypeScript 5.8 + Ant Design 5.x + Vite 5.x
- 测试：JUnit 5 + Testcontainers + JaCoCo (后端)，Vitest + React Testing Library (前端)
- 覆盖率门禁：≥80% (L2 功能 ≥85%)

## 🔄 标准工作流（5 个命令，禁止绕过）
| 命令 | 作用 | 产出 | 确认人 |
|------|------|------|--------|
| `/plan` | 生成分层契约 | Contract.yaml | 架构师（强制） |
| `/build` | 生成代码 + 单元测试 | 代码 + 单元测试 | AI 自检 |
| `/test` | 集成测试 + 契约验证 | 测试报告 | AI（L2-B 需人工） |
| `/ship` | 审查 + 创建 PR | PR | Guardian |
| `/ralph-loop` | 自动迭代到完成 | Draft PR | 人类设定后离开 |

## ⚠️ 核心红线（CI 强制拦截）
1. 禁止修改 `generated/` 目录
2. 测试覆盖率 < 80% 禁止合并（L2 < 85%）
3. 禁止返回 `null`（后端 `Optional`，前端 `undefined`）
4. 契约所有者由 `.github/CODEOWNERS` 定义，PR 作者必须匹配
5. L3/L4 问题未解决禁止 `/ship`
6. React 中禁止 `dangerouslySetInnerHTML` 未经消毒（L4）

## 🚀 5 分钟上手
```bash
git checkout -b feature/your-feature@yourname
claude "/plan 你的功能描述"
claude "/build"
claude "/test"
claude "/ship"
```

## 🌙 夜间模式
- 复杂任务必须使用 `/ralph-loop`，设置 `--min-iterations 5 --convergence-threshold 2%`
- 每天早上执行 `claude "/status"` 查看进展
```

### 3.2 全局设置：`.claude/settings.json`

```json
{
  "autoApprove": true,
  "verbose": true,
  "permissions": {
    "allow": [
      "Bash(mvn clean compile)",
      "Bash(mvn test)",
      "Bash(mvn verify)",
      "Bash(mvn spotless:apply)",
      "Bash(npm run build)",
      "Bash(npm run test)",
      "Bash(npm run lint)",
      "Bash(git diff)",
      "Bash(git add)",
      "Bash(git commit)",
      "Bash(git push origin feature/*)",
      "Read",
      "Edit",
      "Write"
    ],
    "deny": [
      "Bash(rm -rf *)",
      "Bash(git push --force)",
      "Bash(git push origin main)",
      "Edit(**/security/*)",
      "Edit(generated/**)",
      "Edit(.github/CODEOWNERS)"
    ]
  },
  "auto_fix_policy": {
    "L1": { "auto": true, "max_attempts": 3, "patterns": ["compile", "import", "format"] },
    "L2_auto": { "auto": true, "max_attempts": 2, "patterns": ["assertion", "NullPointer", "mock"] },
    "L2_confirm": {
      "auto": false,
      "timeout_minutes": 10,
      "fallback_strategy": "safe-default",
      "fallback_log": ".claude/logs/l2b-fallback.log"
    },
    "L3": { "auto": false, "block": true, "alert": true },
    "L4": { "auto": false, "block": true, "alert": true, "escalate": "security-team" }
  },
  "model_routing": {
    "architect": "claude-opus-4-20250514",
    "engineer": "claude-sonnet-4-20250514",
    "guardian": "claude-sonnet-4-20250514",
    "search": "claude-haiku-4-20250514",
    "team_lead": "claude-opus-4-20250514"
  },
  "skill_versions": {
    "code-localization": "v2.0",
    "editing": "v2.1",
    "test-gen": "v1.8",
    "code-review": "v2.0",
    "owasp-security": "v2026.1"
  },
  "ralph_loop": {
    "default_max_iterations": 30,
    "default_min_iterations": 5,
    "default_completion_promise": "DONE",
    "convergence_threshold": 2.0,
    "convergence_metric": "coverage",
    "auto_commit_interval": 5,
    "checkpoint_save_interval": 5
  },
  "agent_teams": {
    "enabled": true,
    "max_parallel_agents": 5,
    "communication_protocol": "SendMessage+TaskUpdate"
  },
  "temperature": 0.2,
  "hooks_enabled": true,
  "hooks_path": ".claude/hooks/hooks.json"
}
```

### 3.3 命令定义（五个命令，精简核心）

#### `.claude/commands/plan.md`

```markdown
---
description: "生成分层契约，初始化 Checkpoint"
---
执行流程：
1. 调用 code-localization 技能扫描项目结构
2. 判断复杂度：CRUD→L1，版本管理/工作流→L2
3. 生成契约文件 .claude/contracts/{feature}.yaml（L1 模板见示例）
4. 初始化 .claude/state/{feature}/checkpoint.json
5. 等待人工确认后进入 /build
```

#### `.claude/commands/build.md`

```markdown
---
description: "基于契约生成代码，切片执行"
---
执行流程：
1. 读取 contract.yaml 和 checkpoint.json
2. 按 Phase 顺序生成：api → service → mapper → test（后端）
3. 每个 Phase 后运行 mvn compile 触发 L1 自愈
4. 全部完成后运行 mvn test 触发 L2-A 自愈
5. 更新 checkpoint
```

#### `.claude/commands/test.md`

```markdown
---
description: "集成测试 + 契约验证"
---
执行流程：
1. 运行集成测试（Testcontainers）
2. 根据 contract.api_contract 校验 API 一致性
3. 检查覆盖率（json 报告）
4. L2-A 自动修复，L2-B 暂停等待人工（超时 10 分钟自动 safe-default）
5. 更新 checkpoint.phase = "review"
```

#### `.claude/commands/ship.md`

```markdown
---
description: "Guardian 审查 + 创建 PR"
---
执行流程：
1. Guardian 安全扫描 + 契约最终校验
2. 生成双轨度量（Git commit 展示，Checkpoint 本地保存）
3. git add && git commit -m "[Agentic] {type}: {description}"
4. gh pr create --draft --label agentic-generated
```

#### `.claude/commands/ralph-loop.md`

```markdown
---
description: "自动迭代直到完成"
arguments:
  - name: completion-promise
    default: "DONE"
  - name: max-iterations
    default: 30
  - name: min-iterations
    default: 5
  - name: convergence-threshold
    default: 2%
---
执行流程：
基于 Ralph Loop 引擎，反复执行 /plan → /build → /test → /ship 直到满足以下条件之一：
- 输出 completion-promise 字符串
- 迭代次数达到 max-iterations
- 连续 2 次迭代指标变化 < convergence-threshold（收敛）
```

### 3.4 Agent 定义

#### `.claude/agents/architect.md`

```markdown
---
name: architect
model: claude-opus-4-20250514
temperature: 0.1
---
职责：生成契约，不写实现代码。
输出必须包含 meta (feature, level)、api_contract、guardrails。
L2 契约必须包含 domain_rules，其中 severity 为 L1/L2/L3。
```

#### `.claude/agents/engineer.md`

```markdown
---
name: engineer
model: claude-sonnet-4-20250514
temperature: 0.2
skills_profile: ${SKILLS_PROFILE:-backend-java}
---
职责：根据 contract.yaml 生成代码，执行分级自愈，遵守文件锁。
红线：禁止修改 generated/、禁止返回 null、L3/L4 立即停止。
```

#### `.claude/agents/guardian.md`

```markdown
---
name: guardian
model: claude-sonnet-4-20250514
temperature: 0.1
---
职责：安全扫描（OWASP）、契约一致性校验、输出结构化审查报告。
阻断条件：Critical 问题、契约严重违背、覆盖率未达标。
```

### 3.5 React 技能包：`.claude/agents/skill-profiles/frontend-react.md`

```markdown
---
name: frontend-react
version: v1.0
---
技术栈：React 18.2, TypeScript 5.8, Ant Design 5.x, Vite, Vitest, React Testing Library。
规范：函数组件 + Hooks，禁止 Class Component；服务端状态用 React Query；客户端状态用 Zustand。
测试：Vitest + Testing Library，覆盖率 ≥80%。
红线：禁止 any 类型；禁止直接调用 axios（必须通过 services 层）。
```

### 3.6 Hooks 配置：`.claude/hooks/hooks.json`

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "name": "security-check",
        "matcher": "Bash",
        "hook": { "type": "command", "command": "scripts/security-check.sh \"$TOOL_INPUT\"" }
      }
    ],
    "PostToolUse": [
      {
        "name": "auto-format",
        "matcher": "Edit|Write",
        "hook": { "type": "command", "command": "scripts/auto-format.sh \"$TOOL_OUTPUT_FILE\"" }
      }
    ],
    "Stop": [
      {
        "name": "auto-commit",
        "hook": { "type": "command", "command": "scripts/auto-commit.sh" }
      }
    ]
  }
}
```

### 3.7 Git 所有权：`.github/CODEOWNERS`

```bash
# 契约责任人
.claude/contracts/metadata-entity-crud.yaml @alex
.claude/contracts/metadata-versioning.yaml @alex
.claude/contracts/dashboard-feature.yaml @zhang

# 核心模块
bone-metadata/ @backend-team
bone-ui/ @frontend-team
```

### 3.8 CI 流水线：`.github/workflows/agentic-guard.yml`

```yaml
name: Agentic Guard
on:
  pull_request:
    types: [opened, synchronize, ready_for_review]

jobs:
  quick-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Contract Ownership Check
        run: |
          FEATURE=$(echo "${{ github.head_ref }}" | sed 's/feature\///' | sed 's/@.*//')
          CONTRACT=".claude/contracts/${FEATURE}.yaml"
          if [ -f "$CONTRACT" ]; then
            OWNER=$(grep "$CONTRACT" .github/CODEOWNERS | awk '{print $2}' | sed 's/@//')
            AUTHOR="${{ github.event.pull_request.user.login }}"
            if [ -n "$OWNER" ] && [ "$OWNER" != "$AUTHOR" ]; then
              echo "::error::Contract owned by @$OWNER"
              exit 1
            fi
          fi
      - name: Java Lint
        run: mvn spotless:check
      - name: React Lint
        run: cd bone-ui && npm ci && npm run lint
  quality-gates:
    needs: quick-check
    if: github.event.pull_request.draft == false
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Backend Tests & Coverage
        run: mvn clean verify -pl bone-metadata
      - name: Frontend Tests & Coverage (JSON)
        run: |
          cd bone-ui
          npm run test:coverage -- --reporter=json --outputFile=coverage/coverage.json
          COVERAGE=$(node -e "const c=require('./coverage/coverage.json'); console.log(c.total.lines.pct)")
          if (( $(echo "$COVERAGE < 80" | bc -l) )); then exit 1; fi
```

### 3.9 本地夜间 Runner：`scripts/nightly-ralph.sh`

```bash
#!/bin/bash
export ANTHROPIC_API_KEY=$(cat /etc/claude/api-key)
cd /home/agentic/bone
tmux new-session -d -s nightly-ralph
tmux send-keys -t nightly-ralph \
  "claude '/ralph-loop \"执行夜间任务：健康检查、依赖更新、性能回归测试\" \
    --completion-promise NIGHTLY_DONE \
    --max-iterations 30 \
    --min-iterations 5 \
    --convergence-threshold 2%' " Enter
timeout 360m tmux wait-for nightly-ralph
curl -X POST -H "Content-Type: application/json" -d '{"text":"Nightly Ralph Loop finished"}' $SLACK_WEBHOOK_URL
```

**crontab 配置**：`0 0 * * * /home/agentic/bone/scripts/nightly-ralph.sh >> /var/log/ralph.log 2>&1`

### 3.10 辅助脚本

#### `scripts/security-check.sh`

```bash
#!/bin/bash
INPUT="$1"
if echo "$INPUT" | grep -qE "rm -rf /|rm -rf \*|git push --force|git push origin main"; then
  echo "❌ 危险命令被阻止"
  exit 1
fi
exit 0
```

#### `scripts/auto-format.sh`

```bash
#!/bin/bash
FILE="$1"
if [[ "$FILE" =~ \.java$ ]]; then
  mvn spotless:apply -q
elif [[ "$FILE" =~ \.(ts|tsx)$ ]]; then
  npx prettier --write "$FILE"
  npx eslint --fix "$FILE"
fi
```

#### `scripts/auto-commit.sh`

```bash
#!/bin/bash
if [[ -n $(git status --porcelain) ]]; then
  git add .
  git commit -m "auto: AI completed task - $(date +%Y%m%d-%H%M%S)"
fi
```

---

## 四、契约示例（可直接复制）

### L1 契约：`metadata-entity-crud.yaml`

```yaml
meta:
  feature: metadata-entity-crud
  level: L1
  template: crud-base
  executable:
    enabled: true
    schema_id: "bone.metadata.entity.v1"
    api_spec: "openapi3"

mission: "为 bone-metadata 模块增加元数据实体的 CRUD 管理功能"

api_contract:
  endpoints:
    - method: POST
      path: /api/metadata/entities
      request: {entityCode, entityName, tableName, fields}
      response: {id, entityCode, entityName}
      status: 201
    - method: GET
      path: /api/metadata/entities
      query: {page: int, size: int, keyword: string?}
      response:
        projection: "MetadataEntityListView"
        fields: [id, entityCode, entityName, status, createdAt]
        pagination: {type: offset, max_size: 100}
    - method: PUT
      path: /api/metadata/entities/{id}
      request: {entityName, tableName, fields}
      response: {id, entityCode, entityName}
    - method: DELETE
      path: /api/metadata/entities/{id}
      response: {success: true}

guardrails:
  coverage: ">80%"
  max_cyclomatic: 10
  block_patterns: ["**/security/*", "generated/**"]
```

### L2 契约：`metadata-versioning.yaml`

```yaml
meta:
  feature: metadata-versioning
  level: L2
  based_on: "commit-a1b2c3d"
  executable:
    enabled: true
    schema_id: "bone.metadata.versioning.v1"

mission: "元数据实体版本管理（版本对比、回滚）"

api_contract:
  endpoints:
    - method: GET
      path: /api/metadata/entities/{id}/versions
      response: {versions: [{versionNo, changeLog, createdAt}]}
    - method: POST
      path: /api/metadata/entities/{id}/rollback/{versionNo}
      request: {reason}
      response: {success, newVersion}

domain_rules:
  - id: RULE-001
    desc: "版本号必须从 1 开始递增"
    severity: L3
  - id: RULE-002
    desc: "回滚时创建新版本快照，不可删除历史"
    severity: L3
  - id: RULE-003
    desc: "字段变更需记录 diff"
    severity: L1
  - id: RULE-004
    desc: "删除操作需二次确认"
    severity: L2
  - id: RULE-005
    desc: "批量回滚超 100 条需预警"
    severity: L2

guardrails:
  coverage: ">85%"
  max_cyclomatic: 8
  block_patterns: ["**/rollback/**"]
```

---

## 五、Checkpoint 示例

`.claude/state/metadata-entity-crud/checkpoint.json`：

```json
{
  "feature": "metadata-entity-crud",
  "owner": "alex",
  "level": "L1",
  "status": "shipped",
  "phases": ["plan", "build", "test", "ship"],
  "current_phase": "ship",
  "timeline": {
    "plan_start": "2026-04-24T10:00:00Z",
    "plan_end": "2026-04-24T10:00:28Z",
    "build_end": "2026-04-24T10:15:52Z",
    "test_end": "2026-04-24T10:21:45Z",
    "ship_end": "2026-04-24T10:24:00Z"
  },
  "metrics": {
    "plan_time_sec": 28,
    "build_time_sec": 892,
    "test_time_sec": 345,
    "ship_time_sec": 120,
    "l1_attempts": 5,
    "l1_success": 5,
    "l2a_attempts": 1,
    "l2a_success": 1,
    "coverage_actual": 83.5,
    "ralph_loop_iterations": 0,
    "convergence_detected": false
  },
  "pr": {
    "url": "https://gitee.com/bone/bone/pulls/142",
    "number": 142
  }
}
```

---

## 六、落地检查清单

| 步骤 | 任务 | 验证方式 |
|------|------|----------|
| 1 | 创建上述目录结构，复制所有配置文件 | `ls .claude` 看到必要子目录 |
| 2 | 安装 Claude Code、tmux | `claude --version` |
| 3 | 配置 Anthropic API Key | `echo $ANTHROPIC_API_KEY` |
| 4 | 创建 `.github/CODEOWNERS` 并至少分配一个契约 | 运行 CI 中的 ownership check |
| 5 | 配置 nightly cron（可选） | `crontab -l` |
| 6 | 试点一个 L1 功能，执行 `/plan → /build → /test → /ship` | PR 自动创建且 CI 通过 |
| 7 | 试点一个 Ralph Loop 夜间任务 | 第二天早上收到完成通知 |

---
## 八、CI/CD 流水线

### 8.1 主 CI 流水线

**文件路径**：`.github/workflows/agentic-guard.yml`

```yaml
name: Agentic Guard

on:
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]
  workflow_dispatch:

env:
  JAVA_VERSION: '21'
  NODE_VERSION: '20'

jobs:
  # ==================== 第一层：轻量检查 ====================
  quick-check:
    name: "🔍 Quick Check"
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
      
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: 'bone-ui/package-lock.json'
      
      - name: Contract Lock Check
        run: |
          FEATURE=$(echo "${{ github.head_ref }}" | sed 's/feature\///' | sed 's/@.*//')
          if [ -f ".claude/contracts/${FEATURE}.yaml" ]; then
            OWNER=$(yq e '.meta.owner' ".claude/contracts/${FEATURE}.yaml")
            LOCK=$(yq e '.meta.lock' ".claude/contracts/${FEATURE}.yaml")
            AUTHOR="${{ github.event.pull_request.user.login }}"
            if [ "$LOCK" == "true" ] && [ "$OWNER" != "$AUTHOR" ]; then
              echo "::error::❌ Contract locked by @$OWNER"
              exit 1
            fi
          fi
      
      - name: Java Lint
        run: mvn spotless:check -q
      
      - name: React Lint
        run: |
          cd bone-ui
          npm ci
          npm run lint
      
      - name: Type Check
        run: |
          cd bone-ui
          npm run type-check

  # ==================== 第二层：AI 深度审查 ====================
  ai-deep-review:
    name: "🤖 AI Deep Review"
    runs-on: ubuntu-latest
    needs: quick-check
    if: github.event.pull_request.draft == true
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
      
      - name: Install Claude Code
        run: npm install -g @anthropic-ai/claude-code
      
      - name: Guardian Review
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          claude --read-only "
          @guardian 审查本次 PR：
          1. OWASP Top 10 安全扫描
          2. 契约一致性校验
          3. 代码复杂度分析
          输出结构化报告
          "
      
      - name: Comment Review
        uses: actions/github-script@v7
        with:
          script: |
            const review = fs.readFileSync('guardian-report.md', 'utf8');
            github.rest.issues.createComment({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number,
              body: review
            });

  # ==================== 第三层：质量门禁 ====================
  quality-gates:
    name: "✅ Quality Gates"
    runs-on: ubuntu-latest
    needs: quick-check
    if: github.event.pull_request.draft == false
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
      
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: 'bone-ui/package-lock.json'
      
      # 后端质量门禁
      - name: Backend Tests
        run: mvn clean verify -pl bone-metadata -P integration-test
      
      - name: Backend Coverage Check
        run: |
          COVERAGE=$(cat bone-metadata/target/site/jacoco/index.html | grep -o 'Total[^%]*%' | head -1 | grep -o '[0-9]*\.[0-9]*')
          THRESHOLD=80
          if (( $(echo "$COVERAGE < $THRESHOLD" | bc -l) )); then
            echo "::error::Backend coverage ${COVERAGE}% < ${THRESHOLD}%"
            exit 1
          fi
          echo "✅ Backend coverage: ${COVERAGE}%"
      
      # 前端质量门禁
      - name: Frontend Tests
        run: |
          cd bone-ui
          npm ci
          npm run test:coverage
      
      - name: Frontend Coverage Check
        run: |
          cd bone-ui
          COVERAGE=$(cat coverage/lcov-report/index.html | grep -o '[0-9]*\.[0-9]*%' | head -1 | tr -d '%')
          THRESHOLD=80
          if (( $(echo "$COVERAGE < $THRESHOLD" | bc -l) )); then
            echo "::error::Frontend coverage ${COVERAGE}% < ${THRESHOLD}%"
            exit 1
          fi
          echo "✅ Frontend coverage: ${COVERAGE}%"
```

### 8.2 夜间任务调度

**文件路径**：`.github/workflows/nightly-ralph.yml`

```yaml
name: Nightly Ralph Loop

on:
  schedule:
    - cron: '0 0 * * *'  # 每天 UTC 0:00 (北京时间 8:00)
  workflow_dispatch:

jobs:
  nightly-build:
    name: "🌙 Nightly Auto Build"
    runs-on: ubuntu-latest
    timeout-minutes: 480  # 8小时
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Install Claude Code
        run: npm install -g @anthropic-ai/claude-code
      
      - name: Run Ralph Loop
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          claude "/ralph-loop '执行夜间任务队列：
          1. 处理所有标记为 "nightly" 的 Issue
          2. 执行代码库健康检查
          3. 生成每日效能报告
          输出 NIGHTLY_DONE' \
            --completion-promise 'NIGHTLY_DONE' \
            --max-iterations 100 \
            --auto-commit true"
      
      - name: Slack Notification
        if: always()
        uses: slackapi/slack-github-action@v1
        with:
          payload: |
            {
              "text": "🌙 Nightly Ralph Loop Completed: ${{ job.status }}"
            }
```

### 8.3 每日效能分析

**文件路径**：`scripts/analyze-metrics.sh`

```bash
#!/bin/bash
# 月度效能分析脚本

OUTPUT_DIR="reports"
mkdir -p "$OUTPUT_DIR"

echo "# Bone Agentic OS 效能报告" > "$OUTPUT_DIR/metrics.md"
echo "生成时间: $(date)" >> "$OUTPUT_DIR/metrics.md"
echo "" >> "$OUTPUT_DIR/metrics.md"

# 统计功能交付周期
echo "## 功能交付周期" >> "$OUTPUT_DIR/metrics.md"
echo "| Feature | 规划 | 构建 | 测试 | 总计 | Ralph Loop |" >> "$OUTPUT_DIR/metrics.md"
echo "|---------|------|------|------|------|------------|" >> "$OUTPUT_DIR/metrics.md"

for checkpoint in .claude/state/*/checkpoint.json; do
    if [ -f "$checkpoint" ]; then
        FEATURE=$(basename $(dirname "$checkpoint"))
        PLAN_TIME=$(jq -r '.metrics.plan_time_sec // 0' "$checkpoint")
        BUILD_TIME=$(jq -r '.metrics.build_time_sec // 0' "$checkpoint")
        TEST_TIME=$(jq -r '.metrics.test_time_sec // 0' "$checkpoint")
        RALPH_ITER=$(jq -r '.metrics.ralph_loop_iterations // 0' "$checkpoint")
        TOTAL=$((PLAN_TIME + BUILD_TIME + TEST_TIME))
        echo "| $FEATURE | ${PLAN_TIME}s | ${BUILD_TIME}s | ${TEST_TIME}s | ${TOTAL}s | $RALPH_ITER |" >> "$OUTPUT_DIR/metrics.md"
    fi
done

# 统计自愈成功率
echo "" >> "$OUTPUT_DIR/metrics.md"
echo "## 自愈统计" >> "$OUTPUT_DIR/metrics.md"
echo "| 级别 | 尝试次数 | 成功次数 | 成功率 |" >> "$OUTPUT_DIR/metrics.md"

L1_ATTEMPTS=$(jq -s 'map(.metrics.l1_attempts // 0) | add' .claude/state/*/checkpoint.json 2>/dev/null || echo 0)
L1_SUCCESS=$(jq -s 'map(.metrics.l1_success // 0) | add' .claude/state/*/checkpoint.json 2>/dev/null || echo 0)

if [ "$L1_ATTEMPTS" -gt 0 ]; then
    L1_RATE=$(echo "scale=2; $L1_SUCCESS * 100 / $L1_ATTEMPTS" | bc)
else
    L1_RATE=0
fi

echo "| L1 | $L1_ATTEMPTS | $L1_SUCCESS | ${L1_RATE}% |" >> "$OUTPUT_DIR/metrics.md"

echo "" >> "$OUTPUT_DIR/metrics.md"
echo "## 汇总" >> "$OUTPUT_DIR/metrics.md"
echo "- 总功能数: $(ls -d .claude/state/* 2>/dev/null | wc -l)" >> "$OUTPUT_DIR/metrics.md"
echo "- 平均交付周期: TODO" >> "$OUTPUT_DIR/metrics.md"
echo "- 平均覆盖率: TODO" >> "$OUTPUT_DIR/metrics.md"

cat "$OUTPUT_DIR/metrics.md"
```


## 九、效能度量体系

### 9.1 Checkpoint 完整格式

**文件路径**：`.claude/state/{feature}/checkpoint.json`

```json
{
  "version": "3.0",
  "feature": "metadata-entity-crud",
  "owner": "alex",
  "level": "L1",
  
  "status": "shipped",
  "phases": ["plan", "build", "test", "ship"],
  "current_phase": "ship",
  
  "timeline": {
    "plan_start": "2026-04-23T10:00:00Z",
    "plan_end": "2026-04-23T10:00:28Z",
    "build_start": "2026-04-23T10:01:00Z",
    "build_end": "2026-04-23T10:15:52Z",
    "test_start": "2026-04-23T10:16:00Z",
    "test_end": "2026-04-23T10:21:45Z",
    "ship_start": "2026-04-23T10:22:00Z",
    "ship_end": "2026-04-23T10:24:00Z"
  },
  
  "metrics": {
    "plan_time_sec": 28,
    "build_time_sec": 892,
    "test_time_sec": 345,
    "ship_time_sec": 120,
    "total_time_sec": 1385,
    
    "l1_attempts": 5,
    "l1_success": 5,
    "l2a_attempts": 1,
    "l2a_success": 1,
    "l2b_count": 0,
    "l3_count": 0,
    "l4_count": 0,
    
    "coverage_actual": 83.5,
    "complexity_max": 7,
    "test_count_unit": 245,
    "test_count_integration": 89,
    
    "ralph_loop_iterations": 0,
    "ralph_loop_auto_commits": 0
  },
  
  "pr": {
    "url": "https://gitee.com/bone/bone/pulls/142",
    "number": 142,
    "created_at": "2026-04-23T10:24:00Z",
    "merged_at": null
  },
  
  "privacy": "local-only"
}
```

### 9.2 度量双轨制设计

| 数据类型 | 存储位置 | 可见性 | 用途 |
|----------|----------|--------|------|
| 功能描述 | Git Commit | 公开 | 变更记录 |
| 主要变更点 | Git Commit | 公开 | Code Review |
| 契约摘要 | Git Commit | 公开 | 文档归档 |
| 耗时数据 | Checkpoint（本地） | 私有 | 效能分析 |
| 自愈次数 | Checkpoint（本地） | 私有 | 技能优化 |
| 个人标识 | Checkpoint（本地） | 私有 | 隐私保护 |


## 十、12周落地路线图

### 10.1 详细里程碑

| 周次 | 阶段 | 关键任务 | 交付物 | 验收标准 |
|------|------|----------|--------|----------|
| **W1** | 准备 | 环境搭建、团队培训 | 环境就绪 | Claude Code 可用 |
| **W2** | 影子 | 选择1个简单功能并行 | 第一份 Contract | 契约生成 < 1分钟 |
| **W3** | 试点 | 1个 Squad 切换 | 流程文档 | CRUD < 40分钟 |
| **W4** | 优化 | 技能包调优 | 技能包 v1.0 | 自愈成功率 > 80% |
| **W5** | Ralph | 引入夜间模式 | 夜间任务 | 首次夜间任务成功 |
| **W6** | 推广 | 推广到2个 Squad | 培训材料 | 覆盖率 ≥80% |
| **W7** | 扩张 | 覆盖所有模块 | 技能包 v1.5 | 4模块全覆盖 |
| **W8** | 并行 | Agent Teams 试点 | 并行脚本 | 效率提升 ≥30% |
| **W9** | 优化 | 效能分析 | 效能报告 | 识别瓶颈 |
| **W10** | 固化 | 流程固化 | 标准操作手册 | 新功能 100% Agentic |
| **W11** | 度量 | 建立度量体系 | 仪表盘 | 可观测性完整 |
| **W12** | 总结 | 项目总结、规划 | 总结报告 | 12周目标达成 |

### 10.2 AET 虚拟团队职责

**AET（Agent Enablement Team）**
- **规模**：2-3 人轮值，每季度轮换
- **职责**：
  1. 维护技能包（每周更新）
  2. 处理 L3/L4 阻断（SLA 2小时）
  3. 新人培训（每月一次）
  4. 效能分析（每月报告）
  5. 技术栈演进（季度评估）


## 十一、24小时极速上手

### 11.1 学习路径

| 时段 | 内容 | 时长 | 产出 |
|------|------|------|------|
| 09:00-10:00 | 环境安装 + 配置 | 1h | Claude Code 可用 |
| 10:00-11:00 | 理解四命令 | 1h | 跑通 /plan |
| 11:00-12:00 | 第一个 Ralph Loop | 1h | 自动迭代任务 |
| 13:00-14:00 | 契约编写 | 1h | 独立写 L1 契约 |
| 14:00-15:00 | 切片执行 | 1h | 观察 build 切片 |
| 15:00-16:00 | 自愈体验 | 1h | 观察 L1 自动修复 |
| 16:00-17:00 | Agent Teams | 1h | 多 Agent 并行 |
| 17:00-18:00 | Hooks 配置 | 1h | 自动化闭环 |
| 19:00-21:00 | 完整功能实战 | 2h | Todo 全栈应用 |
| 21:00-22:00 | 总结复习 | 1h | 能力清单自检 |

### 11.2 能力清单

| 能力项 | 掌握标准 | 自检 |
|--------|----------|------|
| /plan 生成契约 | 独立生成 L1/L2 契约 | ☐ |
| /build 切片执行 | 理解各切片顺序 | ☐ |
| /test 集成测试 | 理解 L2 分级 | ☐ |
| /ship 交付 | 自动创建 PR | ☐ |
| Ralph Loop | 迭代到 DONE | ☐ |
| Agent Teams | 3+ Agent 并行 | ☐ |
| Hooks | 自动格式化+测试 | ☐ |
| Contract 锁 | 配置 owner/lock | ☐ |


## 十二、故障排查指南

### 12.1 常见问题

| 问题 | 现象 | 解决方案 |
|------|------|----------|
| 契约锁冲突 | CI 阻断，"Contract locked" | 联系 owner 解锁或协调 |
| L3 阻断 | 业务规则违背 | 审查契约规则，调整代码 |
| L4 报警 | 安全红线触发 | 立即修复，通知安全团队 |
| Ralph Loop 超时 | 超过 max-iterations | 调整阈值或人工介入 |
| Agent Teams 冲突 | 文件锁冲突 | 调整文件锁范围 |
| 覆盖率不足 | CI 失败 | 补充单元测试 |
| 编译失败 | L1 自愈失败 | 检查技能包版本 |

### 12.2 恢复流程

```bash
# 1. 查看 checkpoint 状态
cat .claude/state/{feature}/checkpoint.json

# 2. 从特定阶段恢复
claude "/build --phase=api"

# 3. 跳过问题继续
claude "/test --skip-coverage"

# 4. 回退到上一个 checkpoint
git checkout .claude/state/{feature}/checkpoint.json.backup
```


## 十三、VP/CTO 汇报总结

### Bone Agentic Engineering OS v3.0

**一句话定位**：让 1 个架构师 + AI 团队 = 传统 5-8 人 Squad 的产出。

### 核心价值

| 维度 | 传统开发 | Bone Agentic OS | 提升 |
|------|----------|-----------------|------|
| CRUD 交付 | 2-4 小时 | ≤30 分钟 | **4-8x** |
| 测试覆盖率 | 人工保障 | 自动 ≥80% | **质量保障** |
| 代码审查 | 人工，有偏见 | 3 Agent 并行 | **无偏见** |
| 夜间工作 | 不可行 | 全自动 | **24h 交付** |
| 团队规模 | 5-8人/Squad | 1人+AI | **效率 5-8x** |

### 技术栈

- **后端**：Java 21 + Spring Boot 3.5.x
- **前端**：React 18 + TypeScript 5.8 + Ant Design 5.x
- **AI**：Claude Code + Ralph Loop + Agent Teams

### 落地承诺

- **12 周**平滑落地
- **AET 虚拟团队**长期支持
- **100%** 新功能 Agentic 覆盖

### 最终价值

> **一个人 + 5 个命令 + Ralph Loop = 一支 7×24 小时自主运行的 AI 开发团队。**



## 十四、术语表

| 术语 | 定义 |
|------|------|
| **Contract** | 任务契约：需求、API、`domain_rules`、护栏 |
| **Checkpoint** | `.claude/state/{feature}/checkpoint.json`，记录阶段与真实度量 |
| **Slice Execution** | 切片执行：每次只完成一个 Phase，控制上下文 |
| **Gradual Healing** | 分级自愈：L1～L4 与错误类型映射 |
| **Dual-track Metrics** | 双轨度量：Commit Message 展示 + Checkpoint 本地留存 |
| **Ralph Loop** | 自动迭代直至完成承诺或收敛阈值 |

---

**维护说明**：原 `doc/claude/` 目录已合并入本文档；配置类重复段落以 §七 与 `.claude/` 为准。
