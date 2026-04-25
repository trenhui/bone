# Bone Agentic Engineering OS v3.1 — 终局可执行方案（含完整配置）

> **版本**：v3.1 Final | **更新日期**：2026-04-24 | **适用范围**：50–100人研发团队（Java 21 + React 18）  
> **核心目标**：CRUD 功能 **30 分钟交付**、测试覆盖率 **≥80% 自动保障**、安全漏洞 **85% 前置拦截**、契约违背率 **<5%**、**夜间无人值守自动迭代**

## 目录

1. [核心理念与范式转变](#一核心理念与范式转变)
2. [终局架构（3层极简模型）](#二终局架构3层极简模型)
3. [工程化目录结构（可直接复制）](#三工程化目录结构可直接复制)
4. [核心机制（6大支柱）](#四核心机制6大支柱)
5. [标准化 Commands（5个命令）](#五标准化-commands5个命令)
6. [多 Agent 并行开发实战](#六多-agent-并行开发实战)
7. [完整配置文件（直接复制使用）](#七完整配置文件直接复制使用)
8. [CI/CD 分层集成](#八cicd-分层集成)
9. [效能度量与双轨制](#九效能度量与双轨制)
10. [12周渐进式落地路线图](#十12周渐进式落地路线图)
11. [24小时极速上手方案](#十一24小时极速上手方案)
12. [故障排查指南](#十二故障排查指南)
13. [VP/CTO 汇报总结](#十三vpcto-汇报总结)

---

## 一、核心理念与范式转变

### 1.1 核心理念

**你不是在用 AI 写代码，你是在运营一支 7×24 小时自主运行的 AI 开发团队。**

| 旧范式（打字者） | 新范式（指挥官） |
|---|---|
| 在对话框里打字聊天 | 设计 Agent 团队的组织架构与信息流 |
| 等 AI 回复 → 看结果 → 再输入 | 设定任务 → Ralph Loop 自主迭代直到完成 |
| 一个会话做所有事 | 并行会话，每个 Agent 专注一个子任务 |
| 担心 AI 犯错 | 建立自动化纠错闭环（Hooks + CI 门禁） |
| 手动编码 | 意图 → 契约 → AI 执行 → CI 裁决 |
| 规范靠文档和 CR | 规范固化在 Contract 和 CI 门禁 |
| 协作靠沟通协调 | 协作靠 Git 分支主权 + 契约接口 |
| 流程不可见难度量 | Commit Message 展示 + Checkpoint 真实度量 |

### 1.2 终局公式

> **5 Commands** + **分层 Contract** + **Git 分支主权** + **CI 分层裁决** + **切片执行** + **Ralph Loop** = **可治理的 AI 原生 SDLC**

### 1.3 三个关键认知升级

| 升级点 | 旧认知 | 新认知 | 为什么 |
|---|---|---|---|
| **Ralph Loop** | AI 做一次就停 | AI 必须反复尝试直到输出 `DONE` | Boris Cherny 30天合并259个PR的核心机制 |
| **Agent Teams** | 单 Agent 串行 | 多 Agent 并行，各锁文件范围 | 10个 Opus 同时工作，效率碾压人类团队 |
| **Hooks 自动化** | AI 改完我手动检查 | AI 改完 → 自动格式化 → 自动测试 → 自动提交 | 真正做到“下达指令 → 去睡觉 → 回来 PR 已创建” |

---

## 二、终局架构（3层极简模型）

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
│  特性：分层YAML + 状态分离 + Git版本化 + 契约锁(Git CODEOWNERS)   │
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

### 2.1 执行层级图（Ralph Loop vs 切片）

```
Ralph Loop（命令级自动迭代）
├── 第1轮
│   ├── /plan       ← 生成契约（人工确认或自动）
│   ├── /build      ← 切片执行（内部自愈）
│   │   ├── Phase 1: API层 → L1自愈
│   │   ├── Phase 2: Service层 → L1自愈
│   │   ├── Phase 3: Mapper层 → L1自愈
│   │   └── Phase 4: 单元测试 → L1-L2A自愈
│   ├── /test       ← L2分级（L2-B可暂停）
│   └── /ship       ← 创建PR
├── 第2轮（如有失败）
│   └── 重新执行 /build → /test → /ship
└── ...
```

**关键澄清**：
- ✅ Ralph Loop 的迭代单位是**完整命令序列**（/plan → /build → /test → /ship）
- ✅ 切片执行是 `/build` 内部的**防上下文爆炸机制**，不触发 Ralph Loop 的迭代决策
- ✅ 二者**串行嵌套**：Ralph Loop 每轮调用 /build，/build 内部执行切片自愈

---

## 三、工程化目录结构（可直接复制）

```
bone/                                       # Gitee 仓库根目录
├── CLAUDE.md                               # 团队宪法（根上下文）
├── .gitignore
├── pom.xml                                 # 父 Maven 配置（多模块）
│
├── .claude/                                # ⭐ AI 配置根目录
│   ├── settings.json                       # 全局设置（权限/自愈/模型路由）
│   ├── commands/                           # 标准化命令定义（5个）
│   │   ├── plan.md
│   │   ├── build.md
│   │   ├── test.md
│   │   ├── ship.md
│   │   └── ralph-loop.md
│   ├── agents/                             # Agent 角色定义
│   │   ├── architect.md
│   │   ├── engineer.md
│   │   ├── guardian.md
│   │   └── skill-profiles/                 # 动态技能包
│   │       ├── backend-java.md
│   │       ├── frontend-react.md
│   │       └── db-migration.md
│   ├── skills-library/                     # 全局原子技能（版本化）
│   │   ├── code-localization/v2.0/SKILL.md
│   │   ├── editing/v2.1/SKILL.md
│   │   ├── test-gen/v1.8/SKILL.md
│   │   ├── code-review/v2.0/SKILL.md
│   │   └── owasp-security/v2026.1/SKILL.md
│   ├── contracts/                          # 任务契约
│   │   ├── {feature}.yaml
│   │   ├── {feature}.check.yaml
│   │   └── {feature}.local.yaml
│   ├── hooks/                              # Hooks 自动化
│   │   └── hooks.json
│   ├── state/                              # 会话状态
│   │   └── {feature}/
│   │       ├── checkpoint.json
│   │       └── events.jsonl
│   ├── logs/                               # 决策日志
│   ├── playbook/                           # 工程手册
│   │   ├── 24h-onboarding.md
│   │   ├── troubleshooting.md
│   │   └── best-practices.md
│   └── archive/                            # 契约归档（30天自动迁移）
│
├── .github/
│   ├── CODEOWNERS                          # 契约责任人管理
│   └── workflows/
│       ├── agentic-guard.yml               # 主 CI 流水线
│       └── nightly-trigger.yml             # 夜间任务触发器（触发外部 Runner）
│
├── scripts/
│   ├── nightly-ralph.sh                    # 本地 Runner 脚本
│   ├── analyze-metrics.sh
│   ├── contract2openapi.py
│   └── security-check.sh
│
├── bone-metadata/                          # 元数据引擎模块
├── bone-masterdata/                        # 主数据平台模块
├── bone-extpoint/                          # 扩展点引擎模块
├── bone-integration/                       # 集成引擎模块
├── bone-common/                            # 公共模块
├── bone-admin/                             # 管理后台
├── bone-ui/                                # React 前端
└── docs/
    ├── design/
    └── decisions/
```

---

## 四、核心机制（6大支柱）

### 4.1 分支主权 + Git CODEOWNERS

**分支命名规范**：

```bash
feature/metadata-entity-crud@alex
```

**契约所有权（不再内嵌 owner）**：

```yaml
# .claude/contracts/metadata-entity-crud.yaml
meta:
  feature: metadata-entity-crud
  level: L1
  template: crud-base
  # 注意：不再包含 owner 和 lock 字段
```

**Git CODEOWNERS 管理**：

```bash
# .github/CODEOWNERS
.claude/contracts/metadata-entity-crud.yaml @alex
.claude/contracts/metadata-versioning.yaml @alex
.claude/contracts/dashboard-feature.yaml @zhang

# 核心模块
bone-metadata/ @backend-team
bone-ui/ @frontend-team
```

**CI 校验**（见 8.1 节）。

### 4.2 分层任务契约系统

```
.claude/contracts/
├── {feature}.yaml          # 核心契约（L1/L2）
├── {feature}.check.yaml    # 动态检查点（AI自动更新）
└── {feature}.local.yaml    # 本地覆盖（Git忽略）
```

#### Level 1 最小契约（CRUD，30秒生成）

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
      query: {page, size, keyword}
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

#### Level 2 完整契约（复杂业务，5分钟生成）

```yaml
meta:
  feature: metadata-versioning
  level: L2
  based_on: "commit-a1b2c3d"
  executable:
    enabled: true
    schema_id: "bone.metadata.versioning.v1"

mission: "为元数据实体增加版本管理功能，支持版本对比和回滚"

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
    desc: "删除操作需二次确认（软删除 vs 硬删除）"
    severity: L2
  - id: RULE-005
    desc: "批量操作影响行数超过 1000 需预警"
    severity: L2

guardrails:
  coverage: ">85%"
  max_cyclomatic: 8
  block_patterns: ["**/security/*", "generated/**", "**/rollback/**"]
```

### 4.3 原子技能体系（3 Agent + 技能包）

| Agent | 模型 | 职责 | 技能包 |
|---|---|---|---|
| **@architect** | Opus | 设计、生成契约 | 默认 |
| **@engineer** | Sonnet | 代码实现、自愈 | backend-java / frontend-react |
| **@guardian** | Sonnet | 审查、阻断 | owasp-security |

### 4.4 切片执行机制

**后端切片**（`/build` 内部）：

| 切片 | 任务 | 自愈级别 |
|------|------|----------|
| Phase 1 | API 层（Controller + DTO） | L1 |
| Phase 2 | Service 层 | L1 |
| Phase 3 | Mapper 层 | L1 |
| Phase 4 | 单元测试 | L1-L2A |

**前端切片**：

| 切片 | 任务 | 自愈级别 |
|------|------|----------|
| Phase 1 | 类型定义 | L1 |
| Phase 2 | API 服务 | L1 |
| Phase 3 | Hooks | L1 |
| Phase 4 | 组件 | L1-L2A |
| Phase 5 | 样式 | L1 |
| Phase 6 | 单元测试 | L1-L2A |

### 4.5 分级自愈策略（完整定义）

| 级别 | 场景 | 策略 | 判定来源 |
|---|---|---|---|
| **L1** | 编译错误、缺少 import、格式问题 | 自动修复，最多3次 | `severity: L1` |
| **L2-A** | 测试断言、Mock 补充、空指针 | 自动修复，最多2次 | **内置模式匹配**（非 domain_rules） |
| **L2-B** | 业务逻辑修改、状态变更、L2规则违背 | 暂停确认，超时10分钟降级 | **`severity: L2`** |
| **L3** | 核心规则违背（版本号、不可删除历史） | 阻断，不修复 | `severity: L3` |
| **L4** | 安全红线（SQL注入、XSS、硬编码密钥） | 立即阻断+报警 | 内置 OWASP 规则 |

**L2-B 超时降级**：见 7.2 节 `auto_fix_policy.L2_confirm`。

### 4.6 Ralph Wiggum Loop（自动迭代引擎）

**核心原理**：不让 AI 提前“下班”，强制反复尝试直到任务真正完成并输出 `DONE`。

**命令格式**：

```bash
/ralph-loop "任务描述" \
  --completion-promise "DONE" \
  --max-iterations 30 \
  --min-iterations 5 \
  --convergence-threshold 2% \
  --convergence-metric "coverage" \
  --checkpoint-interval 5 \
  --auto-commit true
```

**收敛判定**：
- 达到 `min_iterations` 后开始检查
- 连续 2 次迭代变化率 < `convergence_threshold` 则判定收敛，停止迭代
- 防止无限优化（如覆盖率 80% → 81% → 80.5%...）

---

## 五、标准化 Commands（5个命令）

| 命令 | 职责 | 确认人 |
|---|---|---|
| `/plan` | 生成分层契约 | 架构师（强制） |
| `/build` | 生成代码 + 单元测试 | AI 自检 |
| `/test` | 集成测试 + 契约验证 | AI（L2-B需人工） |
| `/ship` | 审查 + 创建 PR | Guardian |
| `/ralph-loop` | 自动迭代到完成 | 人类设定后离开 |

### 快速上手

```bash
git checkout -b feature/your-feature@yourname
claude "/plan 你的功能描述"
claude "/build"
claude "/test"
claude "/ship"
```

---

## 六、多 Agent 并行开发

### 6.1 Agent Teams 架构

| 角色 | 模型 | 职责 | 文件锁范围 |
|---|---|---|---|
| Team Lead | Opus | 协调、规划、分配任务 | `orchestrator/` |
| 实现 Agent | Sonnet | 实现具体功能 | 按模块分配 |
| 代码搜索 | Haiku | 语义搜索代码库 | 只读 |
| QA Agent | Opus | 审查、找问题 | `review/` |

### 6.2 使用场景决策树

| 任务类型 | 适用模式 | 说明 |
|----------|----------|------|
| 单一功能（CRUD、Bug修复） | **Ralph Loop** | 单 Agent 自动迭代 |
| 多角色协作（后端+前端+测试） | **Agent Teams** | 多 Agent 并行 |
| 复杂项目（多模块+深度迭代） | **混合模式** | Agent Teams 分配子任务，每个子任务内用 Ralph Loop |

### 6.3 并行 Code Review 示例

```bash
# 启动 3 个 Agent 独立审查
claude "创建 Agent 团队 review PR #142，同时启动：
- 安全 Agent：专注认证、注入、XSS
- 性能 Agent：检查 N+1 查询、React 重渲染
- 覆盖率 Agent：验证边界情况
各自独立审查，输出三份报告"
```

---

## 七、完整配置文件（直接复制使用）

### 7.1 团队宪法：`CLAUDE.md`

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

### 7.2 全局设置：`.claude/settings.json`

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

### 7.3 Agent 定义

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

### 7.4 React 技能包：`.claude/agents/skill-profiles/frontend-react.md`

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

### 7.5 Hooks 配置：`.claude/hooks/hooks.json`

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

### 7.6 Git 所有权：`.github/CODEOWNERS`

```bash
# 契约责任人
.claude/contracts/metadata-entity-crud.yaml @alex
.claude/contracts/metadata-versioning.yaml @alex
.claude/contracts/dashboard-feature.yaml @zhang

# 核心模块
bone-metadata/ @backend-team
bone-ui/ @frontend-team
```

### 7.7 辅助脚本

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

## 八、CI/CD 分层集成

### 8.1 主 CI 流水线：`.github/workflows/agentic-guard.yml`

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

### 8.2 夜间任务本地 Runner：`scripts/nightly-ralph.sh`

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
curl -X POST -H "Content-Type: application/json" -d '{"text":"Nightly Ralph Loop finished"}' $SLACK_WEBHOOK_URL || true
```

**crontab 配置**：`0 0 * * * /home/agentic/bone/scripts/nightly-ralph.sh >> /var/log/ralph.log 2>&1`

---

## 九、效能度量与双轨制

### 9.1 Checkpoint 格式：`.claude/state/metadata-entity-crud/checkpoint.json`

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
    "l2b_count": 0,
    "l3_count": 0,
    "l4_count": 0,
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

### 9.2 双轨度量

| 数据类型 | 存储位置 | 可见性 |
|----------|----------|--------|
| 功能描述、主要变更 | Git Commit | 公开 |
| 耗时、自愈次数、覆盖率 | Checkpoint（本地） | 私有 |

---

## 十、12周渐进式落地路线图

| 周次 | 阶段 | 关键动作 | 成功标准 |
|---|---|---|---|
| W1-2 | 影子模式 | 新功能用 Agentic 流程 | 生成第一份 Contract |
| W3-4 | 试点模式 | 1个 Squad 全面切换 | CRUD <40min |
| W5-6 | Ralph Loop | 夜间无人值守 | 完成率 ≥70% |
| W7-8 | 扩张模式 | 覆盖所有模块 | 4模块全覆盖 |
| W9-10 | Agent Teams | 多 Agent 并行 | 效率 ↑50% |
| W11-12 | 固化模式 | 100% Agentic 覆盖 | 新功能覆盖率 100% |

---

## 十一、24小时极速上手方案

| 时段 | 内容 | 产出 |
|---|---|---|
| 9:00-12:00 | 基础配置 + Ralph Loop | 跑通第一个自动迭代任务 |
| 13:00-18:00 | 多 Agent 并行 | 3个Agent并行Review |
| 19:00-22:00 | 生产级工作流 | 完整全栈功能交付 |

---

## 十二、故障排查指南

| 问题 | 解决方案 |
|------|----------|
| 契约锁冲突 | 检查 `.github/CODEOWNERS`，联系 owner |
| L3 阻断 | 审查 domain_rules，调整实现 |
| L4 报警 | 立即修复，通知安全团队 |
| Ralph Loop 超时 | 增加 max_iterations 或调整收敛阈值 |
| 覆盖率不足 | 补充单元测试 |

---

## 十三、VP/CTO 汇报总结

### Bone Agentic Engineering OS v3.1

**一句话定位**：让 1 个架构师 + AI 团队 = 传统 5-8 人 Squad 的产出。

| 优化点 | 方案 | 量化收益 |
|---|---|---|
| 契约分层 | L1 30秒 / L2 5分钟 | 契约冲突率 <5% |
| Ralph Loop | 自动迭代 + 收敛判定 | 夜间任务完成率 ≥70% |
| 3 Agent+技能包 | 分工明确 | 认知负载 ↓60% |
| 分级自愈 + 降级 | L1自动/L2智能/L3阻断 | 效率 ↑2倍 |
| Git CODEOWNERS | 合并冗余 owner | 减少元数据 30% |

**最终价值**：

> **一个人 + 5 个命令 + Ralph Loop = 一支 7×24 小时自主运行的 AI 开发团队。**

---

**版本**：v3.1 Final  
**技术栈**：Java 21 + Spring Boot 3.5.x + React 18 + TypeScript 5.8  
**适用**：Bone 项目及 50-100 人研发团队  
**状态**：✅ 已完成业界最佳实践优化，可直接落地