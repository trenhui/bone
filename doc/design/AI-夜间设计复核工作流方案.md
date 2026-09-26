# AI 夜间设计复核工作流方案

> 文档版本：v1.0 · 生成日期：2026-09-26 · 适用范围：Bone 工程所有模块设计文档（`doc/design/**`）

---

## 一、背景与目标

Bone 工程已积累 10+ 模块的详细设计方案，覆盖后端 DDD 分层、多租户隔离、微前端契约等硬约束。随着项目迭代，设计文档可能逐步偏离业界最佳实践（如错误码 i18n 对称缺失、集成事件未实现 IntegrationEnvelope、Repository 版本检查遗漏等），且跨模块契约随迭代存在走样风险。

**目标**：建立一套 **AI 每晚定时执行** 的设计复核 → 优化 → 实现闭环流程，对标业界最佳实践，逐步完善各模块设计方案并落地代码，最终实现前后联调通过。

---

## 二、方案对比与选择理由

| 方案 | 描述 | 优点 | 致命问题 |
|------|------|------|----------|
| 一次性全模块 | 单次任务扫描所有 10+ 模块设计 | 全局视角 | 上下文爆炸（每个模块设计 1-3 md，累计 20+ 文件），AI 只能输出泛泛结论，跨模块引用依赖幻觉 |
| 纯单模块裸跑 | 每晚只 review 一个模块，不做全局锚定 | 每模块能挖深到细节 | 改到第 5 个模块时，第 2 个模块的 API 契约已被改烂，联调必炸；且无法检测跨模块硬约束违反 |
| **全局锚定 + 单模块渐进（本方案）** | 1 次全局拓扑扫描产出边界契约锚定文件，之后每晚单模块 review 时必须先读锚定文件 | 兼顾深度与全局边界，跨模块契约可控 | 需额外维护锚定文件（一次性产出） |

### 本方案的工作流拓扑

```
┌─────────────────────────────────────────────────────────────────┐
│                    一次性全局锚定任务                              │
│  扫描所有设计文档 → 产出 _global-contracts.yaml                   │
│  （标记 Comet 活跃模块、跨模块依赖、API 契约、硬约束覆盖）          │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│               每晚 23:01 定时任务（循环执行）                      │
│                                                                 │
│  ┌─ 启动锚定 ──────────────────────────────────────────────┐   │
│  │ 读 _global-contracts.yaml + _review-status.yaml           │   │
│  │ 跳过 has_active_comet_change=true 的模块                  │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           ▼                                     │
│  ┌─ 模块深挖 Review ──────────────────────────────────────┐   │
│  │ 对标业界最佳实践 + Bone 硬约束 HC-001~HC-008              │   │
│  │ 输出 review-report.md（阻断级 / 建议级 / 参考级）          │   │
│  │ 设计优化稿追加到原设计文档 v2 章节                          │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           ▼                                     │
│  ┌─ 人工卡点 ──────────────────────────────────────────────┐   │
│  │ AI 绝不自动写代码                                          │   │
│  │ 人工确认 review-report.md → touch _review-approved.yaml   │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           ▼                                     │
│  ┌─ 实现闭环 ──────────────────────────────────────────────┐   │
│  │ 检测到 _review-approved.yaml → 写代码 + 联调               │   │
│  │ 后端：DDD 分层 + spotless:apply + ./scripts/check.sh       │   │
│  │ 前端：npm run lint + build + i18n 对称验证                 │   │
│  │ 联调：gateway 路由 + API 契约 + 微前端 token 注入           │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           ▼                                     │
│  ┌─ 更新进度 ──────────────────────────────────────────────┐   │
│  │ 写 _review-status.yaml → 标记 done                        │   │
│  │ next_run 指向下一个 pending 模块                            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  🔴 周末保护：周六日只做 review，不做实现（防 AI 写坏没人回滚）      │
│  🔴 Comet 保护：has_active_comet_change=true 的模块一律跳过        │
│  🔴 进度保护：中断时标记 interrupted，下一晚从断点续                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、前置依赖

### 3.1 硬约束基线文件（必读）

| 文件 | 作用 | 加载时机 |
|------|------|----------|
| `AGENTS.md` | §一 的 6 条工程约束（分层依赖 / 持久化唯一 / 入站边界 / 统一响应 / 租户与审计 / 格式）。**注意：AGENTS.md 是薄引用入口，不含 HC 表**，HC 定义见下一行的 §G-1.7 | 所有 review 任务启动时必读 |
| `doc/architecture/Bone-DDD-最终实践方案.md` | DDD 架构基线、多租户设计、**HC-001~HC-008 的定义与实测状态（§G-1.7，锚点 `#hc-hard-constraints`）** | 所有 review 任务启动时必读 |
| `doc/agents/03-架构分层规范.md` | 分层依赖规则 | 后端模块 review 时加载 |
| `doc/agents/05-数据库与安全.md` | 持久化约束、安全规则 | 后端模块 review 时加载 |
| `doc/agents/01-项目概览与模块结构.md` | 模块定位、目录约定 | 前端模块 review 时加载 |
| `.comet/config.yaml` + `.comet/state.json`（`current-change.json` **不存在**） | 识别 Comet 活跃 change（`activeChange` / `phase` / 归属模块） | 全局锚定时 + 每晚启动时 |

### 3.2 进度跟踪文件（手动创建）

**路径**：`doc/design/_review-status.yaml`

**初始化模板**（首次跑全局锚定前手动创建，模块清单从全局锚定产出物同步）：

```yaml
# 由 01-global-anchor 派生，模块清单与顺序严格取 _global-contracts.yaml 的 modules 数组
# 历史教训：手写映射会错（曾把 bone-gateway 映射到「1. 控制台」、bone-file 映射到「5. 扩展」）
anchor_ref: "doc/design/_global-contracts.yaml"
updated_at: "2026-09-26T23:40:00+08:00"
current_module: null
next_run: "bone-iam"        # 取 modules 数组中第一个 pending 且 has_active_comet_change=false 的模块
modules:
  - name: "bone-iam"
    role: "product"
    design_doc: "doc/design/modules/6. IAM账号权限管理模块详细设计方案.md"
    status: "pending"       # pending | reviewing | design_ready | approved | implementing | done | interrupted | skip | blocked
    last_reviewed_at: null
    implemented_at: null
    report_path: null
    has_active_comet_change: false
  # ... 其余模块按锚定文件顺序；无设计稿的模块（bone-gateway / bone-file / bone-notification / 前端微应用）design_doc: null
removed_modules: []         # 与锚定清单不一致的旧映射移入此处并写原因
warnings: []
```

### 3.3 人工卡点标记文件

**路径**：`doc/design/modules/<模块名>/_review-approved.yaml`

**作用**：空文件即可。AI 检测到此文件存在时，认为人工已确认 review-report.md，可以进入实现阶段。不存在则 AI 退出，等待人工处理。

---

## 四、全局锚定任务（一次性，只需执行 1 次）

### 4.1 触发方式

手动执行（不是定时任务）。在终端中：

```bash
# 方式 A：如果有 AI CLI
/path/to/ai-cli --prompt-file <本方案第五节的全局锚定提示词> --workdir /Users/renhui.trh/wps/bone

# 方式 B：直接在 TRAE 中把第五节的提示词喂给 AI
```

### 4.2 执行步骤

1. 枚举 `doc/design/**/*.md` 所有文件，提取模块清单
2. 逐一读取每个模块的设计文档，提取：
   - 核心聚合根、Repository 接口
   - 对外暴露的 REST/RPC API 签名
   - 发布/订阅的 IntegrationEvent 清单
   - 共享的 DTO / ErrorCode 常量引用
   - 硬约束 HC-001~HC-008 的**代码判据与证据**（取值仅 `implemented | violated | partial | unknown | n/a`；定义取自 §G-1.7，禁止在锚定文件里复写 `Active/Manual/Planned` 之类的门禁状态词）
3. 读取 `.comet/config.yaml` + `.comet/state.json`（若缺失则记 warning，不要当作"无活跃 change"），标记哪些模块已有活跃 Comet 变更
4. 用 Grep 交叉验证 API 签名、事件类名是否与代码实现一致
5. 写入锚定文件 `doc/design/_global-contracts.yaml`

### 4.3 产出格式

```yaml
generated_at: "2026-09-26T23:30:00"
hard_constraints_ref: "doc/architecture/Bone-DDD-最终实践方案.md"
warnings:
  - "doc/design/modules/xxx.md 引用路径错误，已修正"
modules:
  - name: "bone-iam"
    design_doc: "doc/design/modules/"
    dependencies: ["bone-integration", "bone-metadata"]
    exposed_apis: ["/api/v1/iam/login", "/api/v1/iam/accounts/{id}"]   # 实测值；示例曾出现虚构的 /api/iam/auth/verify，实际不存在
    published_events: ["UserCreatedIntegrationEvent"]
    subscribed_events: ["TenantCreatedIntegrationEvent"]
    shared_dtos: ["UserDTO", "TenantContext"]
    error_codes_used: ["IAM_LOGIN_FAILED", "COMMON_FORBIDDEN"]
    hc_coverage:
      # 取值口径：implemented | violated | partial | unknown | n/a
      # 编号语义 MUST 取自 §G-1.7（本示例即为正确语义，勿再沿用旧版误标）
      HC-001: implemented   # 禁 MyBatis-Plus / JPA / Hibernate / MyBatis
      HC-002: implemented   # domain 不依赖 adapter / application / infrastructure
      HC-003: violated      # Controller 必须返回 ApiResponse<T> / PageResult<T>
      HC-004: violated      # 禁硬编码密钥 / 密码 / Token（gitleaks 本地载体）
      HC-005: implemented   # 测试覆盖率门槛（父 POM 默认 + 模块覆盖）
      HC-006: violated      # 数据库访问必须走 bone-metadata-sdk Repository
      HC-007: implemented   # PR 的 OpenAPI spec 不得引入 breaking change
      HC-008: partial       # 新增表必备列：tenant_id / created_at / updated_at / deleted
    hc_evidence:
      HC-003: "8 个 Controller 中 7 个返回 ResponseEntity（文件:行 需逐条给出）"
      HC-004: "adapter/web/controller/AuthController.java:34 硬编码 password"
    # 注：错误码 i18n 对称、微前端契约、入站边界、DDL 同步 都是**独立审查维度**，
    #     不得借用 HC 编号表达（旧版示例曾把它们写成 HC-004/005/007/008，已废弃）
    has_active_comet_change: false
  # ... 其余模块按相同结构
cross_module_contracts:
  - between: ["bone-iam", "bone-gateway"]
    api_contract: "iam 暴露 /api/iam/auth/verify 供 gateway 调用"
    shared_dtos: ["TokenClaim"]
    constraint_risk: "gateway 直接依赖 iam 的 domain service，违反分层（adapter 不可直注 domain）"
comet_active_changes:
  - workflow: "native"
    change_id: "chg-payment-v2"
    module: "bone-payment"
    skip_design_review: true
```

### 4.4 约束

- **只读扫描**：不得修改任何文件（除了写锚定文件）
- **精确匹配**：API 签名、事件类名必须用 Grep 交叉验证代码实现
- **HC 覆盖必须精确**：取值只能是 `implemented | violated | partial | unknown | n/a`，每条都要带可复现证据（命令 + 结果 + `文件:行`）；`violated` 在 review 中一律按阻断级处理，`unknown` 不得当作合规
- **禁止复写门禁状态**：HC 与门禁的实测状态真源是 `doc/architecture/gate-state.json`（由脚本渲染）；本流程产出只写"本模块代码判据 + 证据"，不得写 `Active / Manual / Planned`

---

## 五、每晚定时任务（23:01 循环执行）

### 5.1 定时配置

推荐两种方式二选一：

| 方式 | 配置 | 优缺点 |
|------|------|--------|
| 本机 cron + AI CLI | `01 23 * * * /path/to/ai-cli --prompt-file <本方案第六节的定时提示词> --workdir /Users/renhui.trh/wps/bone >> /var/log/nightly-review.log 2>&1` | 简单直接，但依赖本机常开；日志本地持久化 |
| 飞书审批流触发（推荐） | 每晚 23:01 自动创建 AI 审批任务，手动点确认后执行 | 灵活可暂停，有审计日志，审批失败可立即终止 |

### 5.2 执行流程（四步闭环）

#### 第一步：启动锚定

```
读 _global-contracts.yaml → 获取全局边界、跨模块契约、Comet 活跃模块清单
读 _review-status.yaml → 找到 status=pending 且 has_active_comet_change=false 的第一个模块
                        → 如果全部 done，输出"所有模块已复核完毕"后退出
                        → 如果昨晚有 interrupted 模块，从中断点恢复
```

#### 第二步：模块深挖 Review

**输入依赖加载规则**：

| 模块类型 | 必读文件 |
|----------|----------|
| 后端模块 | AGENTS.md §一 + doc/agents/03 + doc/agents/05 + 本模块 design 目录所有 md |
| 前端微应用 | AGENTS.md §一 + doc/agents/01 + 本模块 design 目录所有 md + 前端 package.json |

**对标审查维度**：

##### 后端设计维度

| 维度 | 对标标准 | 检查项 |
|------|----------|--------|
| DDD 分层 | Evans DDD + 阿里《后端设计规约》 | adapter 是否直注 domain service？application 是否持有 integration 契约？domain 是否有框架依赖？ |
| 多租户隔离 | SaaS 多租户设计模式（共享库共享表） | tenantId 是否必填？@TenantScope 注解是否正确？*AllTenants 方法是否有白名单？ |
| 持久化约束 | Bone 硬约束 HC-001 / HC-006 | 是否用了 MyBatis-Plus/JPA/Hibernate？数据库访问是否走 bone-metadata-sdk Repository？ |
| 并发安全 | 乐观锁最佳实践 | 聚合根是否有 version 字段？Repository save 是否有 saveWithVersionCheck？ |
| 集成事件 | 领域事件驱动设计 | 是否实现 IntegrationEnvelope？Outbox 是否在同一本地事务？幂等表是否设计？ |
| 错误码 i18n | 业界实践（i18n 对称）+ `scripts/check-i18n-sync.py` + `doc/architecture/Bone-错误码登记.md` | 是否用常量类（非裸字符串）？是否使用 COMMON_/IAM_/MD_/SYS_ 前缀？zh-CN / en-US 是否对称？ |
| 可观测性 | Micrometer + Prometheus 最佳实践 | 是否有 HealthIndicator？Resilience4j 是否配置了明确注解点？Actuator 是否暴露 health,info,metrics,prometheus？ |

##### 前端设计维度

| 维度 | 对标标准 | 检查项 |
|------|----------|--------|
| 微前端契约 | qiankun 最佳实践 + `doc/architecture/bone-前端架构.md`（**注意：不是 HC-007**，HC-007 指 OpenAPI breaking change） | 是否用 window.__BONE_TOKEN__？BONE_LOCALE_CHANGE 是否监听？Shell 是否启动顺序正确？ |
| i18n | React i18next 最佳实践 | zh-CN / en-US 是否对称？dayjs 是否有 utc/timezone 插件？Intl.NumberFormat 是否同步？ |
| 组件规范 | Arco Design Pro 企业级规范 | Table/Form/Modal 是否用 Pro 组件？Space/Grid 是否统一？空状态/加载态/错误态是否覆盖？ |
| 路由守卫 | 微前端路由权限设计 | 是否有统一的鉴权拦截？微应用独立运行时是否有 localStorage fallback？ |

**输出格式**：写入 `doc/design/modules/<模块名>/review-report.md`

```markdown
# <模块名> 设计方案复核报告
> 执行时间：2026-09-26T23:15:00
> 对标基线：`_global-contracts.yaml#hc_semantics`（HC-001~HC-008，取值 implemented/violated/partial/unknown/n/a）+ Evans DDD + 本项目前端规范
> 全局契约引用：doc/design/_global-contracts.yaml · bone-iam → bone-gateway API 契约

## 一、阻断级问题（硬约束违反，必须先改设计才能写代码）
| # | 问题描述 | 违反的硬约束/业界标准 | 修复建议 |
|---|----------|----------------------|----------|
| 1 | IamUserRepository.save() 用了 REQUIRES_NEW | 业界实践 + E-5.2（Outbox 必须与聚合保存同本地事务；**HC 无此编号，勿写 HC-009**） | 移除 REQUIRES_NEW，用 Default 传播级别，Outbox 写入与聚合 save 同事务 |
| 2 | IAM 模块暴露的 IamUserQueryService 被 Gateway 的 adapter 直注 | AGENTS.md §一.3 入站边界 + ADR-0028（**不是 HC-004**，HC-004 指硬编码密钥） | Gateway 通过 IAM 的 API 包或 RPC 接口调用，不得直注 domain 层 |

## 二、建议级优化（可优化的软约束，不影响实现但提升质量）
| # | 当前设计 | 业界对标 | 优化方案 |
|---|----------|----------|----------|
| 1 | 错误码新增 IAM_INTERNAL_ERROR | 应复用 COMMON_INTERNAL_ERROR，错误码避免模块内重复定义 | 删除 IAM_INTERNAL_ERROR，统一引用 CommonErrorCodes.COMMON_INTERNAL_ERROR |
| 2 | TokenDTO 同时存在于 bone-iam 和 bone-gateway 的 dto 包 | 共享 DTO 应放到 api 包或 shared 模块 | 将 TokenClaim 移至 bone-iam-api 包，Gateway 依赖此包 |

## 三、参考级对标（业界亮点，可酌情采纳）
| # | 业界实践 | 当前方案差距 | 建议 |
|---|----------|--------------|------|
| 1 | 阿里云 SaaS 多租户对 tenant_id 字段自动建索引 | Bone 设计稿未提及索引策略 | 建议在 DDL 中为所有业务表的 tenant_id 字段添加 idx_tenant_id 索引 |

## 四、优化后的设计改进稿
> 直接修改原设计文档（在文末追加 `## v2 优化稿` 章节，**不得覆盖原稿**）

（此处为设计稿 v2 的完整内容，含修复后的 Repository 签名、API 契约、错误码清单等）

## 五、实现计划
### 后端文件级清单
- [ ] domain/repository/IamUserRepository.java — 移除 REQUIRES_NEW，增加 saveWithVersionCheck
- [ ] application/service/IamUserApplicationService.java — 重构入口
- [ ] domain/event/UserCreatedIntegrationEvent.java — 实现 IntegrationEnvelope
- [ ] ...

### 前端文件级清单（如适用）
- [ ] pages/login/index.tsx — 实现 BONE_LOCALE_CHANGE 监听
- [ ] ...

### 联调前置条件
- bone-gateway 路由已配置 /api/iam/** 转发
- IAM API 包版本已发布（mvn install）
- ...
```

#### 第三步：实现闭环（仅当设计报告被人工确认后执行）

**前置卡点检查**：
```
检测 doc/design/modules/<模块名>/_review-approved.yaml 是否存在
├── 不存在 → 输出"设计报告待人工确认，等待 _review-approved.yaml" → 退出
└── 存在 → 进入实现阶段
```

**后端实现步骤**：
1. 按 `review-report.md` 第五节的文件级清单逐一写代码，严格遵循 DDD 分层
2. 执行 `mvn spotless:apply`（强制格式化）
3. 执行 `./scripts/check.sh`（全量质量门禁，含 ArchUnit、spotless、单测）
4. 跨模块 API 变更时，同步更新 `_global-contracts.yaml`
5. 执行 `git diff` 验证完整性（防外部进程静默删除）

**前端实现步骤**：
1. 按 UI 设计稿写组件，确保 dayjs 有 utc/timezone 插件
2. 执行 `npm run lint` + `npm run build`
3. 执行 CI 同款脚本验证 i18n 键对称
4. 验证 BONE_LOCALE_CHANGE 监听 + dayjs locale 同步

**联调步骤**：
1. 启动 bone-gateway + 本模块 + 依赖模块
2. 用 curl/Postman 验证 API 契约（对照 `_global-contracts.yaml` 的 `exposed_apis`）
3. 微前端联调：注入 `window.__BONE_TOKEN__`，验证路由 + 权限 + i18n 切换
4. 记录联调结果到 `review-report.md` 末尾的 `## 六、联调验证结果` 章节

**提交规范**：
- Commit message 格式：`[nightly-review][<模块名>] <改动摘要>`
- 推送到 feature 分支，**不直接合 main**
- 手动创建 PR，等待 Review 门禁

#### 第四步：更新进度

写入 `doc/design/_review-status.yaml`：

```yaml
updated_at: "2026-09-27T00:15:00"
current_module: null
next_run: "bone-iam"
modules:
  - name: "bone-gateway"
    status: "done"
    last_reviewed_at: "2026-09-26T23:15:00"
    implemented_at: "2026-09-27T00:05:00"
    report_path: "doc/design/modules/review-report-gateway.md"
    has_active_comet_change: false
  - name: "bone-iam"
    status: "pending"
    has_active_comet_change: false
  # ...
warnings:
  - "bone-payment 有活跃 Comet change，跳过"
```

---

## 六、每晚定时任务提示词（可直接喂给 AI）

> 以下为每晚定时执行的完整提示词，建议存为独立文件 `scripts/prompts/nightly-design-review.md`，让 AI CLI 直接读取。

```markdown
# 【定时任务】Bone 工程单模块设计复核 + 优化 + 实现闭环

## 执行时间约束
- 当前时间：每晚 23:01
- 单次执行最大 token 预算：**80% 用于 review，20% 用于结论**；到预算上限立即停，标记 `status: interrupted` 并写入 `interrupted_at`，下一晚从断点续
- 周末保护：周六日只执行第二步（Review）和第四步（更新进度），不执行第三步（实现）

## 第一步：启动锚定（必须先做，不得跳过）
1. 读取 `doc/design/_global-contracts.yaml` — 获取全局边界、跨模块契约、Comet 活跃模块清单、HC 覆盖基线
2. 读取 `doc/design/_review-status.yaml` — 找到 `status: pending` 且 `has_active_comet_change: false` 的第一个模块
   - 如果所有模块都是 `done` 或 `skip`，输出"所有模块已复核完毕"后退出
   - 如果存在 `status: interrupted` 且 `interrupted_at` 在 48 小时内的模块，从中断点恢复（加载上次未完成的 review-report.md）
3. 读取本模块 design 目录下所有 md 文件

## 第二步：模块深挖 Review

### 2.1 硬约束加载（必读）
- `AGENTS.md` §一 全部 **6 条**工程约束（分层依赖 / 持久化唯一 / 入站边界 / 统一响应 / 租户与审计 / 格式）
- HC-001~HC-008 的**定义与判据**取自 `doc/architecture/Bone-DDD-最终实践方案.md` §G-1.7 与锚定文件 `hc_semantics`（AGENTS.md 不含 HC 表；门禁实测状态见 `doc/architecture/gate-state.json`）
- 如果是后端模块：加载 `doc/agents/03-架构分层规范.md` + `doc/agents/05-数据库与安全.md`
- 如果是前端微应用：加载 `doc/agents/01-项目概览与模块结构.md`
- `doc/architecture/Bone-DDD-最终实践方案.md`

### 2.2 对标审查（按第五节定义的后端/前端维度逐一检查）
- 每个硬约束违反必须标为 **阻断级**，不得降级为建议级
- 跨模块契约偏离必须引用 `_global-contracts.yaml` 作为依据
- 所有检查项必须有 Grep 验证（API 签名、事件类名、Repository 接口），不得凭空判断

### 2.3 产出 review-report.md
格式严格遵循第五节 `review-report.md` 模板，包含：
- 阻断级问题表（必须逐条修复）
- 建议级优化表（可采纳）
- 参考级对标表（业界亮点，酌情）
- 优化后的设计改进稿（追加到原设计文档 v2 章节，**不得覆盖原稿**）
- 实现计划（文件级清单 + 联调前置条件）

### 2.4 特殊处理
- 如果 token 预算耗尽，立即停止 review，标记 `interrupted` 状态，写入当前已完成到哪一步
- 如果发现设计文档本身缺失或引用路径错误，在 review-report.md 的 warnings 章节记录，并更新 `_global-contracts.yaml` 的 warnings

## 第三步：实现闭环（仅当日非周末且人工已确认）

### 3.1 前置卡点检查
检测 `doc/design/modules/<模块名>/_review-approved.yaml` 是否存在
- 不存在 → 输出"设计报告待人工确认，请在飞书群 review 后 touch 空文件" → 退出
- 存在 → 继续

### 3.2 代码实现
- **后端**：按 v2 设计稿写代码，DDD 分层，立即执行 `mvn spotless:apply` + `./scripts/check.sh`
- **前端**：按 v2 UI 稿写组件，立即执行 `npm run lint` + `npm run build` + i18n 对称验证
- **联调**：启动依赖服务 + 本模块，按 `_global-contracts.yaml` 验证 API 契约，微前端验证 token 注入 + 路由 + i18n

### 3.3 提交
- `git diff` 验证完整性
- Commit message：`[nightly-review][<模块名>] <改动摘要>`
- 推送到 feature 分支，不直接合 main

## 第四步：更新进度

修改 `doc/design/_review-status.yaml`，更新当前模块的 status、时间戳、report_path，设置 `next_run` 为下一个 pending 模块。

## 不可违反的约束（违反即停止执行）
- 绝不自欺欺人：硬约束违反必须标为阻断级，不能降级
- 绝不绕过 Comet：`has_active_comet_change: true` 的模块一律跳过
- 绝不写代码超过设计：设计稿没覆盖的边界场景，必须回到设计阶段补稿
- 绝不压缩阻断级问题的描述来塞更多内容
- 绝不执行 token 预算超 80% 的 review
```

---

## 七、落地清单（按顺序执行）

| # | 动作 | 责任人 | 预期产出 | 完成标准 |
|---|------|--------|----------|----------|
| 1 | 手动创建 `doc/design/_review-status.yaml` | 人工 | 初始化进度文件 | 包含所有模块清单，状态均为 pending |
| 2 | 手动触发全局锚定任务（用第四节提示词） | AI + 人工 | `_global-contracts.yaml` + 更新后的 `_review-status.yaml` | HC 覆盖精确匹配代码实现；Comet 活跃模块已标记 |
| 3 | 配置每晚 23:01 定时任务（推荐飞书审批流） | 人工 | 可自动触发的定时任务 | 手动测跑 1 次成功 |
| 4 | 第一晚执行（bone-gateway） | AI | review-report-gateway.md | 人工 review 报告，touch `_review-approved.yaml` |
| 5 | 后续每晚循环执行 | AI 自动 | 逐模块 review + 实现 | 周末保护生效；Comet 保护生效 |
| 6 | 联调全部模块 | AI + 人工 | 前后端联调通过 | 所有 API 契约符合 `_global-contracts.yaml` |

---

## 八、失败保护与恢复策略

| 场景 | 触发条件 | 恢复策略 |
|------|----------|----------|
| Token 预算耗尽 | review 过程中超过 80% 预算 | `_review-status.yaml` 标记 `status: interrupted` + `interrupted_at`，下一晚从中断点续 |
| AI 幻觉产出错误结论 | review 报告中的事实与代码不符 | 人工检查 review-report.md，修正后 touch `_review-approved.yaml` 覆盖错误结论 |
| 外部进程静默删除代码 | 执行 `git diff` 发现关键更改丢失 | 从上一晚的 feature 分支 commit 恢复 |
| Comet change 与夜间任务冲突 | 某模块同时有 Comet change | `_global-contracts.yaml` 中 `has_active_comet_change: true` 的模块自动跳过 |
| 紧急上线需要暂停夜间任务 | 人工手动跳过 | `_review-status.yaml` 中手动设 `status: skip`，下一晚自动跳过 |
| 定时任务服务器宕机 | 本机 cron 未执行 | 切换到飞书审批流方式（推荐配置中已包含） |
