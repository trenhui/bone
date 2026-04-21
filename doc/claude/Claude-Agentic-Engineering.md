
一句话定义：以 Contract 为核心、Git 为状态机、CI 为裁判的 AI 原生软件工程操作系统。  
适用规模：50–100 人研发团队（5–8 个 Squad）  
实际项目：Bone 全栈开源平台  
核心目标：CRUD 功能 30 分钟交付、测试覆盖率 ≥80% 自动保障、安全漏洞 85% 前置拦截、契约违背率 <5%


一、核心理念与终局架构

1.1 核心理念

Agentic Engineering ≠ AI 写代码，而是 AI 执行软件工程流程。

传统开发
Bone Agentic OS
人工设计→编码→测试→审查
意图 → 契约 → AI 执行 → CI 裁决
规范靠文档和 CR
规范固化在 Contract 和 CI 门禁中
协作靠沟通协调
协作靠 Git 分支主权和契约接口
流程不可见难度量
Commit Message 展示 + Checkpoint 真实度量

终局公式：
Commands(4个) + Contract(分层 YAML) + Git(分支+契约锁) + CI(分层裁决) + 切片执行 = 可治理的 AI 原生 SDLC

1.2 终局架构（极简 3 层）

┌─────────────────────────────────────────────┐
│  L1: COMMAND INTERFACE（命令层）⭐唯一入口   │
│  /plan → /build → /test → /ship            │
│  （支持 --dry-run / --explain 调试）        │
├─────────────────────────────────────────────┤
│  L2: CONTRACT LAYER（契约层）⭐唯一真理源    │
│  分层 YAML（L1/L2）+ 状态分离 + 模板继承     │
│  Git 天然版本化 + Contract 锁机制            │
├─────────────────────────────────────────────┤
│  L3: EXECUTION & GOVERNANCE（执行与治理层）  │
│  5 大原子技能（版本化）+ 3 个 Agents + 技能包 │
│  切片执行 + L1-L4 分级自愈（规则显式映射）    │
│  CI 分层裁决 + Checkpoint 双写度量            │
└─────────────────────────────────────────────┘

关键收敛（防过度设计）：
- ❌ 删除并行子会话 → 采用切片执行（每次只处理一个子阶段，避免上下文爆炸）
- ❌ 删除契约版本链 v1/v2 → 用 Git commit 历史替代
- ❌ 删除 Registry 状态服务 → 用分支命名 + Checkpoint 文件替代
- ❌ 删除 CI 自动修复 → CI 只读审查，修复仅在本地进行
- ✅ 新增 Contract 分层：L1 最小契约（30 秒生成）保证效率，L2 完整契约保证质量
- ✅ 新增 L2 智能分级：低风险自动修复，高风险人工确认
- ✅ 新增度量双轨制：Commit Message 展示 + Checkpoint 真实数据
- ✅ 新增 Contract 锁：防止多人同时修改同一契约


二、Bone 项目工程化目录（可直接复制）

bone/                                       # Gitee 仓库根目录
├── CLAUDE.md                               # 团队宪法
├── .claude/
│   ├── settings.json                       # 权限 + 分级自愈策略
│   ├── commands/                           # 标准化命令（4 个）
│   │   ├── plan.md
│   │   ├── build.md
│   │   ├── test.md
│   │   └── ship.md
│   ├── agents/                             # 3 个核心 Agent
│   │   ├── architect.md
│   │   ├── engineer.md
│   │   ├── guardian.md
│   │   └── skills/                         # 技能包（按需加载）
│   │       ├── backend-java.md             # Java 后端技能包
│   │       └── frontend-vue.md             # Vue 前端技能包
│   ├── skills/                             # 版本化原子技能
│   │   ├── code-localization/v2.0/SKILL.md
│   │   ├── editing/v2.1/SKILL.md
│   │   ├── test-gen/v1.8/SKILL.md
│   │   ├── issue-reproduction/v1.5/SKILL.md
│   │   ├── code-review/v2.0/SKILL.md
│   │   └── owasp-security/SKILL.md         # 安全技能（外部引入）
│   ├── contracts/                          # 任务契约（分层）
│   │   ├── metadata-entity-crud.yaml       # L1 契约示例（元数据实体 CRUD）
│   │   ├── metadata-entity-crud.check.yaml # 动态检查点
│   │   └── metadata-entity-crud.local.yaml # 本地覆盖（Git 忽略）
│   ├── state/                              # 会话状态
│   │   └── metadata-entity-crud/
│   │       ├── checkpoint.json             # 真实 metrics
│   │       └── events.jsonl                # 事件流
│   ├── logs/                               # 决策日志
│   │   ├── build-trace.log
│   │   └── decision.log
│   └── archive/                            # 契约归档
├── .github/workflows/
│   └── agentic-guard.yml                   # CI 只读审查流水线
├── scripts/
│   └── analyze-metrics.sh                  # 月度效能分析
├── bone-metadata/                          # 元数据引擎模块（Bone 核心）
├── bone-masterdata/                        # 主数据平台模块
├── bone-extpoint/                          # 扩展点引擎模块
├── bone-integration/                       # 集成引擎模块
├── bone-common/                            # 公共模块
├── bone-admin/                             # 管理后台示例
├── bone-ui/                                # Vue3 前端
└── docs/
├── design/                             # 设计文档归档
└── decisions/                          # 架构决策记录（ADRs）


三、核心机制（5 大支柱）

3.1 分支主权 + Contract 锁（Git-Native 协作）

用 Git 解决协作，用 Draft PR 替代手动后缀：

# 标准开发分支（开发者主权）
feature/metadata-entity-crud@alex

# 状态由 GitHub/Gitee Draft PR 自动管理
# - Draft PR = 开发中（CI 轻量检查）
# - Ready for Review = 待审查（触发完整 Guardian 流水线）

Contract 锁机制：
# .claude/contracts/metadata-entity-crud.yaml
meta:
feature: metadata-entity-crud
owner: alex                 # 契约所有者
lock: true                  # 锁定后他人不可修改

CI 校验：
if contract.owner != PR.author → 阻断并提示 "契约被锁定，请联系 owner 协调"

3.2 分层任务契约系统（Contract-First）

核心升级：静态约束与动态状态分离

.claude/contracts/
├── metadata-entity-crud.yaml          # 核心契约（只读，人工确认后锁定）
├── metadata-entity-crud.check.yaml    # 动态检查点（AI 自动更新）
└── metadata-entity-crud.local.yaml    # 本地覆盖（不入 Git）

Level 1：最小契约（CRUD 场景，30 秒生成）

# .claude/contracts/metadata-entity-crud.yaml
meta:
feature: metadata-entity-crud
owner: alex
level: L1
template: crud-base

mission: 为 bone-metadata 模块增加元数据实体的 CRUD 管理功能

api_contract:
endpoints:
- method: POST
path: /api/metadata/entities
request: {entityCode, entityName, tableName, fields: [...]}
response: {id, entityCode, entityName}
- method: GET
path: /api/metadata/entities
response: {list: [{id, entityCode, entityName}]}
- method: PUT
path: /api/metadata/entities/{id}
request: {entityName, tableName, fields}
response: {id, entityCode, entityName}
- method: DELETE
path: /api/metadata/entities/{id}
response: {success: true}

Level 2：完整契约（复杂功能，5 分钟生成）

# .claude/contracts/metadata-versioning.yaml（版本管理功能）
meta:
feature: metadata-versioning
owner: alex
level: L2
based_on: commit-a1b2c3d

mission: 为元数据实体增加版本管理功能，支持版本对比和回滚

api_contract:
endpoints:
- method: GET
path: /api/metadata/entities/{id}/versions
response: {versions: [{versionNo, changeLog, createdAt}]}
- method: POST
path: /api/metadata/entities/{id}/rollback/{versionNo}
response: {success: true, newVersion: 3}

domain_rules:
- id: RULE-001
  desc: "版本号必须从 1 开始递增"
  check: "version 字段初始值为 1，每次更新 +1"
  severity: L3
- id: RULE-002
  desc: "回滚时创建新版本快照，不可删除历史"
  check: "rollback 操作必须插入新版本记录，不可覆盖"
  severity: L3
- id: RULE-003
  desc: "字段变更需记录 diff"
  check: "对比新旧字段，生成 change_log JSON"
  severity: L1                 # 可自动修复

guardrails:
block_patterns: ["**/security/*", "generated/**"]
coverage: ">80%"
max_cyclomatic: 10

3.3 原子技能体系（版本化 + 技能包扩展）

3 个 Agent + 动态技能包，避免 Prompt 爆炸：

Agent
职责
技能包
@architect
设计 + 契约生成
默认
@engineer
代码实现 + 自愈
backend-java / frontend-vue / db-migration
@guardian
审查 + 阻断
默认 + owasp-security

技能包版本管理：
skills/
├── editing/
│   ├── v1.0/          # 稳定版
│   ├── v1.1/          # 测试版
│   └── latest -> v1.0 # 软链接

3.4 切片执行机制（防上下文爆炸）

# 自动切片模式（默认）
claude "/build"  # 自动按 phase 切片执行

# 手动指定切片（调试用）
claude "/build --phase=api"

3.5 分级自愈策略（L2 智能分级 + 规则显式映射）

级别
场景
策略
判定来源
L1
编译错误 / 可自动修复的业务规则
自动修复，最多 3 次
domain_rules[severity=L1]
L2-A
测试断言修正 / mock 补充
自动修复
内置模式匹配
L2-B
修改业务逻辑 / 高风险修复
生成建议，暂停确认
domain_rules[severity=L2]
L3
业务逻辑违背契约
禁止修复，阻断
domain_rules[severity=L3]
L4
安全红线
立即阻断 + 报警
内置安全规则


四、标准化 Commands（职责清晰，边界明确）

命令
职责
L1/L2 范围
/plan
生成分层契约，初始化 Checkpoint
-
/build
生成代码 + 单元测试 + 编译
L1 自愈
/test
集成测试 + 契约一致性 + 覆盖率门禁
L2 确认
/ship
Guardian 审查 + 双轨度量 + 创建 PR
-

关键区别：
- /build 失败 → 自动修复（单元测试失败通常是代码问题）
- /test 失败 → 人工确认（集成测试失败通常涉及环境/配置问题）

4.1 /plan（规划阶段）

# 标准模式（为 bone-metadata 模块开发元数据实体 CRUD）
claude "/plan 为 bone-metadata 模块增加元数据实体的 CRUD 管理功能"

# 调试模式
claude "/plan 为 bone-metadata 模块增加元数据实体的 CRUD 管理功能 --dry-run"

执行：
1. 调用 code-localization 技能扫描 bone-metadata 现有代码结构
2. 智能判断契约级别（CRUD → L1，版本管理 → L2）并生成契约
3. 初始化 Checkpoint → .claude/state/metadata-entity-crud/checkpoint.json
4. 暂停，等待人类确认后进入 /build

4.2 /build（构建阶段）

claude "/build"                 # 自动切片
claude "/build --phase=api"     # 手动指定切片

切片执行：
1. 读取 contract.yaml + checkpoint.json
2. 按 checkpoint.next_phase 执行当前切片（api → service → mapper → test）
3. 每个切片完成后执行 mvn compile -pl bone-metadata，触发 L1 自愈
4. 全部切片完成后执行 mvn test -pl bone-metadata（单元测试），触发 L2 智能自愈

4.3 /test（测试阶段）

claude "/test"

执行：
1. 运行集成测试（Testcontainers + MySQL）
2. 覆盖率检查（<80% 阻断）
3. 契约一致性校验
4. 更新 checkpoint.phase = "review"

4.4 /ship（交付阶段）

claude "/ship"

执行：
1. Guardian 审查（安全 + 质量）
2. 生成双轨度量数据（Commit Message 展示 + Checkpoint 真实数据）
3. Git 提交 + 自动创建 PR 到 Gitee


五、核心配置（可直接复制使用）

5.1 团队宪法：CLAUDE.md

# Bone Agentic Engineering 团队宪法 v2.0

## 🎯 核心理念
人定义意图，AI 负责实现，契约保证质量，CI 担任裁判。

## 🏗️ 技术栈（约束而非锁定）
- 后端：Java ≥21 + Spring Boot 3.5.x（阻断 4.x）+ MySQL Connector 9.3.0（严格锁定）
- 前端：Vue ≥3.5 + Element Plus ≥2.13 + TypeScript ≥5.8
- 质量：JUnit 5 + Testcontainers + JaCoCo（覆盖率 >80%）

## 🔄 标准工作流（4 个命令，禁止绕过）
| 命令 | 作用 | 产出 | 确认人 |
|:---|:---|:---|:---|
| `/plan` | 规划功能 | Contract.yaml（L1/L2） | 架构师（强制） |
| `/build` | 生成代码 + 单元测试 | 代码 + 单元测试 | AI 自检 |
| `/test` | 集成测试 + 契约验证 | 测试报告 | AI（L2 确认） |
| `/ship` | 交付审查 | PR + 双轨度量 | Guardian |

## ⚠️ 核心红线（CI 强制拦截）
1. 禁止修改 `generated/`
2. 测试覆盖率 < 80% 禁止合并
3. 禁止返回 `null`（用 `Optional`）
4. Contract 所有者必须与 PR 作者一致（Contract 锁）

## 🚀 5 分钟上手（以 bone-metadata 为例）
```bash
git checkout -b feature/metadata-entity-crud@yourname
claude "/plan 为 bone-metadata 模块增加元数据实体的 CRUD 管理功能"
claude "/build"
claude "/test"
claude "/ship"

### 5.2 全局设置：`.claude/settings.json`

```json
{
  "autoApprove": true,
  "permissions": {
    "allow": ["Bash(mvn clean compile)", "Bash(mvn test)", "Bash(git diff)", "Read", "Edit"],
    "deny": ["Bash(rm -rf *)", "Bash(git push --force)", "Edit(**/security/*)", "Edit(generated/**)"]
  },
  "auto_fix_policy": {
    "L1": { "auto": true, "max_attempts": 3 },
    "L2_auto": { "auto": true, "patterns": ["assertion", "NullPointer", "mock"] },
    "L2_confirm": { "auto": false, "patterns": ["domain_rules", "state"] },
    "L3": { "auto": false, "block": true },
    "L4": { "auto": false, "block": true }
  },
  "model_routing": {
    "architect": "claude-opus-4",
    "engineer": "claude-sonnet-4",
    "guardian": "claude-sonnet-4"
  },
  "skill_versions": {
    "editing": "v2.1",
    "test-gen": "v1.8"
  },
  "temperature": 0.2
}

5.3 Agent 定义（3 个角色 + 技能包）

@architect.md：
---
name: architect
description: 负责系统设计、API 契约生成、数据建模
model: claude-opus-4
---

## 职责
1. 需求澄清与领域分析
2. 生成分层契约（L1/L2）
3. 定义扩展点与架构边界

## 约束
- 不写实现代码
- 必须声明 api_contract

@engineer.md（支持技能包）：
---
name: engineer
description: 基于契约生成前后端代码，执行分级自愈
model: claude-sonnet-4
skills_profile: ${SKILLS_PROFILE:-backend-java}
---

## 职责
1. 读取 contract.yaml，按 api_contract 生成代码
2. 执行 L1/L2 自愈循环
3. 遵守 guardrails

## 红线
- 禁止修改 generated/
- 禁止返回 null
- L3/L4 问题立即停止并上报

@guardian.md：
---
name: guardian
description: 最终质量审查，CI 只读模式执行
model: claude-sonnet-4
---

## 职责
1. 安全扫描（OWASP Top 10）
2. 契约一致性校验
3. 输出结构化审查报告

## 输出格式
- Critical：立即阻断
- High：需确认
- Medium/Low：建议


六、CI/CD 集成（分层策略）

# .github/workflows/agentic-guard.yml（Gitee Actions 兼容）
name: Agentic Guard
on: [pull_request]

jobs:
  quick-check:
    runs-on: ubuntu-latest
    steps:
      - run: mvn lint -pl bone-metadata
      - run: test -f .claude/contracts/*.yaml || exit 1

  ai-deep-review:
    if: github.event.pull_request.draft == true
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Guardian 深度审查
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          npx -y @anthropic-ai/claude-code --read-only "
          @guardian 审查本次 PR 安全与契约一致性
          "

  quality-gates:
    needs: quick-check
    runs-on: ubuntu-latest
    steps:
      - run: mvn clean test -pl bone-metadata
      - run: |
          COVERAGE=$(cat bone-metadata/target/site/jacoco/index.html | grep -o 'Total[^%]*%')
          [ "$COVERAGE" -ge 80 ] || exit 1


七、效能度量（双轨制 + 隐私分离）

指标
展示来源（公开）
真实来源（本地）
功能交付周期
Commit Message（估算）
Checkpoint build_time_real
L1 自愈成功率
-
Checkpoint l1_attempts
测试覆盖率
Commit Message
JaCoCo 报告

Checkpoint 双写策略：
- 运行时：.claude/state/{feature}/checkpoint.json
- 归档：Git Commit Message（永久记录）
- 敏感数据（Owner、耗时）仅存本地，不上传
  
  
八、12 周渐进式落地路线图（Bone 项目适配）

周次
阶段
关键动作
Bone 项目具体目标
W1-2
影子模式
新功能用 Agentic 流程，旧功能保持原流程
为 bone-metadata 的 1 个新接口生成 Contract，并行对比人工开发耗时
W3-5
试点模式
1 个 Squad 全面切换；重点优化 Prompt
完成 bone-metadata 元数据实体 CRUD 全流程，平均耗时 <40min
W6-8
扩张模式
逐个 Squad 迁移，建立 AET 虚拟团队
扩展至 bone-masterdata、bone-extpoint 模块
W9-12
固化模式
剩余流程纳入体系；月度复盘
所有 Bone 四大引擎新功能均走 Agentic 流程

AET（Agent Enablement Team）虚拟团队
- 组成：2-3 人，各 Squad 轮值，每季度轮换
- 职责：维护技能库、处理 L3/L4 阻断、培训新人、更新技术栈
- 产出：技能库版本、月度效能报告、ADR 文档
  
  
九、汇报级总结（VP / CTO 汇报专用）

Bone Agentic Engineering OS vFinal 是一套基于 Bone 真实项目（Gitee）验证的 AI 原生软件工程操作系统。核心优化包括：

1. 契约分层与状态分离：L1 最小契约 30 秒生成（如元数据实体 CRUD），L2 完整契约 5 分钟建模（如版本管理），动态检查点独立管理，避免 Git 冲突。
2. 3 Agent + 技能包架构：保持对外认知简单，内部通过版本化技能包扩展（如 backend-java、frontend-vue），避免 Prompt 爆炸。
3. 精准分级自愈：L1/L2/L3 与 domain_rules 显式映射，L2 智能拆分为自动/确认两级，效率提升 2 倍。
4. CI 分层策略：轻量检查每次执行，深度审查仅在 Draft PR 触发，平衡安全与成本。
5. 12 周渐进双轨落地：影子模式先行，AET 虚拟团队保障长期支持，适配 Bone 四大引擎（metadata/masterdata/extpoint/integration）的渐进式覆盖。
  
方案可在 12 周内平滑落地于 Bone 项目，是 50–100 人研发团队实现 AI 原生转型的最优路径。