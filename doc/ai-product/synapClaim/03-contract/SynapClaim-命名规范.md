---
doc_id: contract.naming
role: ssot
currency: current
---

# SynapClaim（智能理赔系统）命名规范

> **文档性质**：SynapClaim 系统级命名**真源**，适用于前端路由、菜单、权限资源标识及与平台 API 规范的映射。
> **更新日期**：2026-11-22
> **上位规范**：[Bone-API-规范.md](../../../architecture/Bone-API-规范.md)（HTTP 契约，优先级更高）、[bone-前端架构.md](../../../architecture/bone-前端架构.md)、[Bone-API-规范.md §9.2](../../../architecture/Bone-API-规范.md#92-权限-scopeopenapi--iam)（权限 Scope 格式）
> **机器载体**：`scripts/check-sclaim-naming.py`（`--self-test` 负向探针 + `--check` 主判据，接入 `scripts/check.sh` 与 `scripts/ci-check.sh`）
> **API 域注册**：本文所述 `claims` / `claim-config` / `claim-ops` / `review` / `internal` 已登记于 [Bone-API-规范 §2.3 域注册表](../../../architecture/Bone-API-规范.md#23-域domain注册表)；API 路径模板以该规范为准。

---

## 1. 适用范围与真源优先级

| 层 | 本规范是否适用 | 约束来源 |
|---|---|---|
| 系统名 / 缩写 | ✅ 强制 | 本文 §2 |
| 浏览器页面 URL（新窗口独立页、深链） | ✅ 强制 | 本文 §3–§5 |
| 菜单项名称、分组、图标 | ✅ 强制 | 本文 §6 |
| 权限资源标识（`claim:{资源}:{动作}`） | ✅ 强制 | 本文 §6，格式沿用平台 [API 规范 §9.2](../../../architecture/Bone-API-规范.md#92-权限-scopeopenapi--iam) |
| 错误码前缀 | ✅ 强制 | `SC_CLAIM_*`，真源为 [错误码登记 §SC_](../../../architecture/Bone-错误码登记.md#sc_-synap-claim-智能理赔平台)（本文只引用，不复述台账） |
| 后端 HTTP API 路径 | ⚠️ **不适用结构改造**，只做跨层映射 | [Bone-API-规范 §2.1/§2.2](../../../architecture/Bone-API-规范.md#21-路径模板强制) 强制 `/api/v1/{domain}/...`，见本文 §7 |
| 数据库表名、DDL | ❌ 不适用 | [数据库开发规范](../../../architecture/数据库开发规范.md) |

**真源优先级**：平台规范 > 本文 > 模块详设 > 代码实现。本文与平台规范冲突时以平台规范为准；若确需偏离，须走 ADR 并在 [Bone-API-规范 §13.2 迁移登记](../../../architecture/Bone-API-规范.md#132-废弃与迁移) 留痕。

---

## 2. 系统命名

| 项 | 值 | 说明 |
|---|---|---|
| 产品名 | **SynapClaim** | 展示给用户/文档的完整名称 |
| 中文名 | **智能理赔系统** | 菜单、页面标题、日志描述使用 |
| 系统缩写 | **`sclaim`** | 全小写，用于 URL 前缀、命名常量、跨层映射的**唯一**系统级标识 |
| 服务 ID | `synap-claim` | 网关 route id 沿用 `synap-claim*`，不在本文改动范围 |

**为什么是 `sclaim` 而不是其它候选**：

| 候选 | 裁定 | 理由 |
|---|---|---|
| `standalone` | ❌ 不采用 | 描述的是「渲染形态」而非业务系统；无法作为系统级标识贯穿路由/权限/API |
| `claim` | ❌ 不采用 | 过于宽泛，与赔案模块同名，无法区分系统层与业务模块层 |
| `sc` | ❌ 不采用 | 过短，脱离上下文不可读，易与 `system config` 等缩写碰撞 |
| `synclaim` / `aiclaim` / `iclaim` | ❌ 不采用 | 与产品名 `SynapClaim` 不完全对应，引入第二套命名 |
| **`sclaim`** | ✅ 采用 | SynapClaim 的品牌关联完整、比 `synclaim` 短、比 `sc` 可读，不绑定单一业务模块 |

---

## 3. 浏览器页面 URL 规范

### 3.1 路径模板（强制）

```
/sclaim/{module}/{page}?{query}
```

| 段 | 规则 |
|---|---|
| 系统前缀 | **恒为 `sclaim`**，不得出现第二套系统缩写 |
| `{module}` | 小写英文业务名，取 §4 注册表的值；**不二次缩写** |
| `{page}` | 取 §5 页面标识注册表的值（`list` / `detail` / `create` / `edit` / §5 登记的专用页面） |
| 全路径 | 全小写；多词以短横线分隔；禁止拼音、驼峰、下划线、尾斜杠 |
| `{query}` | 只承载**资源标识与视图状态**（如 `claimId`、`policyNo`、`tab`），不承载模块/页面语义 |

### 3.2 规范化示例

| 业务功能 | 规范 URL | 状态 |
|---|---|---|
| 案件详情（新窗口） | `/sclaim/claim/detail?claimId=...&tab=overview` | ✅ 已落地 |
| 保单详情（新窗口） | `/sclaim/policy/detail?policyNo=...` | ✅ 已落地 |
| 赔案列表 | `/sclaim/claim/list` | 📋 规划 |
| 核赔工作台 | `/sclaim/review/list` | 📋 规划 |
| 理赔规则列表 | `/sclaim/rule/list` | 📋 规划 |
| OCR 识别 | `/sclaim/ocr/recognition` | 📋 规划（专用页面标识，见 §5） |
| 任务管理 | `/sclaim/task/list` | 📋 规划 |
| 智能体管理 | `/sclaim/agent/list` | 📋 规划 |
| 模型管理 | `/sclaim/model/list` | 📋 规划 |

> **「规划」的含义**：模块名已在本规范登记，后续新增页面须直接复用，不得另起缩写；未落地的路径不代表已存在可访问页面（避免「配了不生效」）。

### 3.3 禁止事项

1. **不保留任何兼容别名**。已废止 `/standalone`、`/claim-detail`，旧 URL 直接失效，防止「同一语义两处配置源」。
2. **不在路径里放视图态词**（如 `detail` 之外的 `view` / `show` / `page`）；页面标识只允许 §5 注册表的值。
3. **不把资源 ID 塞进 path 段**。当前独立页通过 query 传 `claimId` / `policyNo`：微应用内嵌态受 qiankun `HashRouter` 约束，独立页用 `BrowserRouter` 直读 `window.location.search`（见 [sclaimRoutes.ts](../../../../bone-frontend/apps/bone-claim-app/src/workbench/sclaimRoutes.ts)）。若将来统一改为资源化路径（`/sclaim/claim/{id}`），须作为独立 ADR 评估全量路由契约，不得在本次规范下悄悄变体。
4. **深链构造只走唯一真源**：`sclaimRoutes.ts` 的 `buildClaimDetailHref` / `buildPolicyDetailHref`；组件不得自行拼接 URL 字符串。

### 3.4 双 Router 分流（实现约束，非规范选择）

| 场景 | Router | 地址栏形态 | 参数来源 |
|---|---|---|---|
| 微应用内嵌（shell 菜单） | `HashRouter`（历史事故：误用 BrowserRouter 致空白页） | `/claim#/case?claimId=...` | `useSearchParams`（hash 内 query） |
| 新窗口独立页 | `BrowserRouter` | `/sclaim/claim/detail?claimId=...` | `window.location.search`（经 `standaloneSearch()`） |

两者并存是 qiankun 微前端的历史约束，不是双真源；`sclaimRoutes.ts` 只负责「浏览器地址栏 pathname」这一层。

---

## 4. 模块注册表

| 模块标识 | 中文名 | 落地状态 | 说明 |
|---|---|---|---|
| `claim` | 赔案 | ✅ 已落地（详情） | 赔案主干、报案、理算、结案 |
| `policy` | 保单 | ✅ 已落地（详情） | 保单配置与详情 |
| `review` | 核赔/后督 | 📋 规划 | 核赔、抽检、后督工作台 |
| `rule` | 理赔规则 | 📋 规划 | 规则配置与命中分析 |
| `ocr` | OCR 识别 | 📋 规划 | 单证识别与可信解析 |
| `task` | 任务 | 📋 规划 | 任务中心、待办 |
| `agent` | 智能体 | 📋 规划 | AI 智能体管理 |
| `model` | 模型 | 📋 规划 | 模型与版本管理 |

**新增模块流程**：先在本表登记（含中文名、状态、职责边界）→ 再改 `sclaimRoutes.ts` 的 `SCLAIM_MODULE` → 再新增页面。顺序不可颠倒，否则规范文档与代码真源漂移。

---

## 5. 页面标识注册表

| 页面标识 | 语义 | 示例 |
|---|---|---|
| `list` | 列表/工作台 | `/sclaim/claim/list` |
| `detail` | 详情 | `/sclaim/claim/detail?claimId=...` |
| `create` | 新建 | `/sclaim/rule/create` |
| `edit` | 编辑 | `/sclaim/rule/edit?ruleId=...` |
| `recognition` | OCR 识别（专用） | `/sclaim/ocr/recognition` |

除已登记的专用页面外，新增页面标识须先登记本表；禁止使用 `view` / `info` / `show` 等同义多种写法。

---

## 6. 菜单与权限资源标识

### 6.1 菜单

- 侧栏分组名使用 §4 的中文名，不使用缩写。
- 菜单项 `key` 与对应页面 URL 的 `{module}` 一致；新增菜单项须同步 `claimMenuGroups.ts` 与其单测。
- 菜单种子 SQL 中的 `permission` 字段必须引用真实存在的权限码（见 §6.2），不得造「配了不生效」的空权限。

### 6.2 权限资源标识

格式沿用平台 [API 规范 §9.2](../../../architecture/Bone-API-规范.md#92-权限-scopeopenapi--iam) 的 `{域}:{资源}:{动作}`：

```
claim:{资源}:{动作}
```

| 资源 | 常见动作 | 说明 |
|---|---|---|
| `claims` | `read` / `write` | 赔案主干 |
| `policy` | `read` / `write` | 保单 |
| `review` | `read` / `write` | 核赔/后督 |
| `settlement` | `write` | 理算结案 |
| `quota` | `read` / `write` | 额度池 |
| `inspection` | `read` / `write` | 抽检 |
| `mapping` | `dispose` | 编码映射 |

> 权限资源名**独立于浏览器模块名**，但同一业务对象须保持语义对应（赔案 = `claim` 模块 / `claims` 资源）。禁止为同一对象引入第二套资源名。

### 6.3 错误码

- 业务错误码前缀 **`SC_CLAIM_*`**，真源是 [Bone-错误码登记 §SC_](../../../architecture/Bone-错误码登记.md#sc_-synap-claim-智能理赔平台)。
- 本文只规定「前缀来自系统名缩写 + 域名」这一命名原则；具体码值、HTTP 状态、双语译文一律以台账为准，禁止在本文或前端代码里另立副本。
- 新增错误码三处必齐：`ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS` + 前端 `zh-CN`/`en-US` 语言包；机检见台账 §第 3 步登记的 `doc/ai-product/synapClaim/tools/_check_error_code_i18n.py`。

---

## 7. 跨层命名映射（API 路径裁定）

### 7.1 裁定：API 不改为 `/api/sclaim/...`

**建议中「API 统一纳入 `/api/sclaim` 前缀」不采纳。** 理由（均为可复核事实）：

1. **违反平台强制真源**：[Bone-API-规范 §2.1](../../../architecture/Bone-API-规范.md#21-路径模板强制) 定义唯一路径模板 `/api/v1/{domain}/{resource-collection}`，§2.2 明确「新接口**必须** `/api/v1/...`」「无版本别名已废止」。`sclaim` 属于 `{domain}` 层级的候选值，不能取代 `/api/v1` 这个版本命名空间。
2. **两类 URL 职责不同**：浏览器 URL 是**页面导航**（面向人、可收藏、随 UI 重构变化）；HTTP API 是**资源契约**（面向机器、有版本与破坏性变更约束）。「统一」应体现在**业务标识一一对应**，而非结构完全相同。
3. **爆炸半径不可接受**：现网理赔 API 有 5 个前缀（`/api/v1/claims`、`claim-config`、`claim-ops`、`review`、`internal`）、**40 个 Controller / 188 个端点**；网关按前缀白名单转发（4 条 route 全指向 `synap-claim:8087`）；前端 service 分散在多个 `createApiClient('/api/v1/...')`；E2E `impact-map.json` 固化了 156 条理赔端点，另有约 20 个脚本硬编码路径。一次性改名会同时触碰契约、网关、前端、E2E，且属于破坏性变更（需 v2 + Sunset），无法「按最新规范直接切换」。
4. **反例已经存在**：`/api/v1/internal/**` 未登记网关白名单（直连 8087 可达、经网关 404），说明前缀体系本身还有缺口；此时再引入第六套例外只会放大不一致。

> 若未来确需推进 API 层变更，必须作为**独立议题 + ADR**：平台规范先行登记 → 网关双前缀过渡（带 Sunset）→ Controller/前端/E2E 分批迁移 → 契约 diff。不得夹带在页面 URL 规范里完成。

### 7.2 统一方式：语义映射表（本规范提供）

「避免前端路由和后端接口各自形成一套命名体系」通过**模块标识一一对应 + 单一真源**实现，而不是强行让两者路径同形：

| 业务模块 | 浏览器路由模块 | API domain（平台强制 `/api/v1/{domain}`） | 权限资源 | 错误码前缀 |
|---|---|---|---|---|
| 赔案 | `claim` | `claims` | `claims` | `SC_CLAIM_` |
| 保单 | `policy` | `claim-config`（子资源 `policies`） | `policy` | `SC_CLAIM_` |
| 核赔/后督 | `review` | `review` | `review` | `SC_CLAIM_` |
| AI 治理/运营 | （无独立页面模块，归 `claim`） | `claim-ops` | `claims` / `eval` | `SC_CLAIM_` |
| 规则/配置 | `rule` | `claim-config` | `mapping` 等 | `SC_CLAIM_` |
| 内部运维 | 不对外暴露页面 | `internal`（网关白名单待补） | 不对外授权 | `SC_CLAIM_` |

**约束**：
- 新增理赔业务对象时，**先在本表登记**四个标识（浏览器模块 / API domain / 权限资源 / 错误码前缀），再写代码；只登记其中一个就开工视为违规。
- API domain 的新增/变更遵循 [Bone-API-规范 §2.3 域注册表](../../../architecture/Bone-API-规范.md#23-域domain注册表)；本文不复制该表，仅在 §7.2 维护理赔系统内部映射。
- 机检 `check-sclaim-naming.py` 保证：后端/网关**不得**出现 `/api/sclaim` 字面量（防止有人绕过 §7.1 裁定引入第六套前缀）。

---

## 8. 机器门禁

| 判据 | 阻断内容 | 执行路径 |
|---|---|---|
| G1 系统缩写与路径构造 | `sclaimRoutes.ts` 的 `SCLAIM_SYSTEM` 必须为 `sclaim`；路径必须由 `buildSclaimPath` 拼成 `/sclaim/{module}/{page}` | `check-sclaim-naming.py --check` |
| G2 无遗留别名 | 源码中不得出现 `/standalone`、`/claim-detail` 字面量（测试中的反例断言白名单） | 同上 |
| G3 文档-代码一致 | `sclaimRoutes.ts` 的每个已落地模块都必须在本文 §4 注册表登记 | 同上 |
| G4 API 裁定不漂移 | 后端 Java / 网关配置 / 前端 service 中不得出现 `/api/sclaim` 字面量 | 同上 |
| 负向探针 | 上述四类各注入一条反例，必须判红；否则门禁本身恒绿不可信 | `check-sclaim-naming.py --self-test` |

接入：
- `scripts/check.sh` G0 段（pre-commit 阻断，本地与 AI 代提交两条路径）
- `scripts/ci-check.sh` G0 段（CI 全量，覆盖绕过本地提交的变更）

> **禁止**用 `--no-verify` 绕过；门禁判据变更须同步改本文与脚本，且重新跑 `--self-test` 证明仍能变红。

---

## 9. 新增理赔页面的落地检查清单

- [ ] 系统前缀为 `/sclaim`，模块与页面分别取自 §4 / §5 注册表（未登记先登记）
- [ ] URL 无 hash（独立页）、无兼容别名、无拼音/驼峰、query 只放资源标识
- [ ] 深链通过 `sclaimRoutes.ts` 构造，组件不自行拼 URL
- [ ] 菜单名、权限码、错误码前缀与 §6 / §7.2 映射表一致
- [ ] API 路径保持 `/api/v1/{domain}/...`，新增 domain 先在 [Bone-API-规范 §2.3](../../../architecture/Bone-API-规范.md#23-域domain注册表) 登记
- [ ] 跑 `python3 scripts/check-sclaim-naming.py --self-test && python3 scripts/check-sclaim-naming.py --check`
- [ ] 前端 typecheck + 单测 + 独立页 E2E 探针（无菜单/无顶栏/无 hash）

---

## 10. 修订记录

| 日期 | 说明 |
|---|---|
| 2026-11-22 | 初版。确立 `sclaim` 系统缩写与 `/{sclaim}/{module}/{page}` 浏览器 URL 规范；登记模块/页面/权限/错误码映射；裁定 API 保持 `/api/v1`（附理由与独立议题路径）；新增机检门禁并接入 check.sh / ci-check.sh |
| 2026-11-22 | 迁入 synapClaim 文档族 L3 契约层（`03-contract/`），登记 `docs.manifest.json`（id `contract.naming`）、补 front-matter、重生成 AI-INDEX；文档内跨目录链接按新位置重写；门禁 `DOC_REL` 与全仓引用同步更新 |

---

## 11. 决策摘要

本规范只解决一类问题：**SynapClaim 的系统级标识在浏览器 URL、菜单、权限码、错误码、跨层映射之间各自为政**。核心决策三条：系统缩写唯一取 `sclaim`；浏览器 URL 统一 `/{sclaim}/{module}/{page}`（资源标识只走 query）；后端 API 路径维持平台强制的 `/api/v1/{domain}`，以语义映射表而非路径同形达成跨层一致。裁决细节见 §2、§3、§7。

## 12. 不做清单

- 不做兼容别名：`/standalone`、`/claim-detail` 直接失效，不保留重定向或双真源。
- 不改造后端 API 路径为 `/api/sclaim/...`：那是破坏性变更，须独立 ADR（见 §7.1）。
- 不把资源 ID 塞进 path 段、不引入 `view`/`info`/`show` 等页面标识同义词（见 §3.3、§5）。
- 不复述错误码台账、数据库规范、权限 Scope 格式的正文——只引用各自真源（见 §1、§6.3）。

## 13. 风险与回退

- **风险**：模块/页面注册表是代码与文档的双真源，若新增模块先写代码后登记，G3 判据会当场判红（这是设计意图，不是缺陷）。回退路径是先补 §4/§5 登记再改 `sclaimRoutes.ts`。
- **风险**：文档迁入文档族后受 `_check_doc_structure.py` 约束（L3/ssot/reference 必含四节）；本文件 §11–§14 即为此治理要求而设，不可删除。
- **风险**：跨目录相对链接在文档再次移动时会失效；M6 路径判据会判红，须按新位置重算 `../../../` 层级（当前见 §11 迁移记录）。
- **回退**：若 `sclaim` 缩写后续被平台级规范否决，须走 ADR 并在 [Bone-API-规范 §13.2 迁移登记](../../../architecture/Bone-API-规范.md#132-废弃与迁移) 留痕，再全量替换真源与门禁常量，禁止单独改代码绕过规范。

## 14. 待决策

- 资源化页面路径（`/sclaim/claim/{id}`）是否替代 query 传参：需评估全量路由契约与 qiankun 双 Router 影响，当前维持 query（见 §3.3）。
- `/api/v1/internal/**` 网关前缀白名单缺口是否补齐：属 API 治理议题，不在本规范范围（见 §7.1 反例）。
- 模块注册表中「规划」模块的启用时序：由各模块详设落地时逐项转「已落地」，本规范只提供登记入口。
