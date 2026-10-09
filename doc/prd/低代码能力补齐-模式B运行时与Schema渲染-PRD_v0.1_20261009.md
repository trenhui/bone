# BONE 低代码能力补齐（模式 B 运行时 + Schema 渲染）PRD

> **一句话定义**：不新增独立低代码模块，沿元数据双模式路径补齐低代码能力——后端补齐 `bone-metadata-engine` 运行时（模式 B）的查询与并发控制契约，前端在 `meta_*` catalog 之上提供表单/列表 Schema 渲染，使配置型实体"建模即可用页面"。

---

## 文档元信息

| 项目 | 内容 |
|------|------|
| 需求名称 | 低代码能力补齐：模式 B 运行时 + Schema 渲染 |
| 产品 | BONE 企业级全栈开源快速开发平台 |
| 文档版本 | v0.1 |
| 文档状态 | Draft |
| 产品经理 | [TBD: 由需求提出方指定] |
| 技术负责人 | [TBD: 由模块 owner 指定] |
| 关联模块 | bone-metadata-engine、bone-metadata-server（:9001）、bone-metadata-sdk、bone-metadata-app（:3004）、studio-generator（仅受接口影响） |
| 目标发布 | [TBD: 待排期确认] |

---

## 修订记录

| 版本 | 日期 | 修订人 | 核心变更 | 状态 |
|------|------|--------|----------|------|
| v0.1 | 2026-10-09 | — | 初稿：基于"不新增独立低代码模块、沿双模式路径补齐"的评审结论；补充 §5 设计与交互（UI） | Draft |

---

## 1. 背景与目标

### 1.1 问题陈述与变更动机

**当前局限（As-Is，实查详设口径 ✅/⏳/💡）**：

- 模式 B 运行时仅交付 **CRUD v1**（`/api/v1/runtime/**`，`JdbcRuntimeRecordService`），**列裁剪 / 过滤 / 排序 / `If-Match` 属 ⏳ Target——契约已声明但未实现**（[元数据管理模块详细设计方案](../design/modules/2.%20元数据管理模块详细设计方案.md)）。
- 前端只有**建模 UI**（`bone-metadata-app` :3004 管理 catalog 与扩展字段），建模完成后没有"列表页 + 表单页"的渲染能力，配置型实体仍需走模式 A 生成代码或手写页面。
- 表达式 / 业务规则 / SmartQL 的引擎库能力已具备（SmartMeta `ExpressionEngineTest`、`BusinessRuleEngineTest`），但 **HTTP 暴露仅为 💡 Vision 分阶段规划**。

**不做的代价**：模式 B 宣称的"标准 CRUD 无需生成 Java Controller"无法闭环到可操作界面，双模式交付中"运营台、配置型实体、快速试错"场景（[元数据能力-实现映射与竞品对照 §1.3](../design/modules/元数据能力-实现映射与竞品对照.md)）停留在数据面，无法与 Directus / Hasura 类产品形成完整对照。

**为什么是这个时间点**：双模式交付是产品已写入的定位（主 PRD §4.4、§市场机会），模式 A 主线已 ✅，模式 B 数据面（CRUD v1）已 ✅；补齐契约与渲染层是既有路线的收口，而非新方向立项。**明确不新增第四个引擎或独立"低代码模块"**——engine 与 generator 是同一元模型上的两种消费方式，职责边界不变。

### 1.2 目标与成功标准

| 目标 | 可验证指标 | 优先级 |
|------|------------|--------|
| G1 运行时查询契约补齐 | `delivery_mode=RUNTIME` 实体支持过滤/排序/列裁剪/分页，全部通过 `metadata-runtime-v1.yaml` 契约测试 | P0 |
| G2 写路径并发安全 | 更新请求带 `If-Match` 版本协商，冲突返回 412（或契约声明的等价语义），与聚合根乐观锁语义一致 | P0 |
| G3 建模即页面 | 一个已发布 RUNTIME 实体，仅通过 Schema 配置（0 代码）在前端渲染出可用的列表页与表单页，完成增删改查 | P0 |
| G4 校验一致 | 前端 Schema 校验与后端 engine 校验规则同源；错误经 `META_` 前缀 + `ProblemDetail.errorCode` 返回，前端可 i18n 展示 | P1 |
| G5 零回归 | 模式 A（generator）与已有 CRUD v1 调用方无破坏性变更 | P0 |

> 指标口径：G3 的"0 代码"指不新增 Java 源码与前端页面源码；配置操作步数基线为**预估/待验证**（见 §12 假设 A2）。

### 1.3 与平台能力的关系

| 关联 | 说明 |
|------|------|
| PRD 章节 | 主 PRD §4.4 元数据；市场切入点（低代码/元数据驱动） |
| 双模式定位（单一真源） | [元数据能力-实现映射与竞品对照 §1.3](../design/modules/元数据能力-实现映射与竞品对照.md)：模式 B = engine + `/api/v1/runtime/**`，与 generator 并存不替代 |
| 架构约束 | [Bone-DDD-最终实践方案](../architecture/Bone-DDD-最终实践方案.md) HC 门禁、[Bone-API-规范](../architecture/Bone-API-规范.md)、[数据库开发规范](../architecture/数据库开发规范.md) |
| 模块详设 | [2. 元数据管理模块详细设计方案](../design/modules/2.%20元数据管理模块详细设计方案.md) §5.3.1（runtime）、§9（SmartMeta 引擎） |
| DDL 真源 | `bone-init.sql` §4 `meta_*` 五表；ddl-auto 全环境 `validate` |

---

## 2. 用户与场景

### 2.1 用户角色

| 用户类型 | 特征描述 | 核心诉求 |
|---------|---------|---------|
| 应用管理员 / 配置人员 | 在运营台配置业务对象，不写代码 | 建模后立刻有可用页面，改配置即生效 |
| 业务人员（租户内） | 使用最终列表/表单完成日常录入与查询 | 页面加载快、校验提示清晰、多语言可读 |
| 开发者 | 处理复杂领域逻辑、信创审计场景 | 简单对象走模式 B，复杂对象保留模式 A 逃逸舱，边界清晰 |
| 集成/运维 | 调用与维护运行时 API | 契约稳定（OpenAPI）、错误码规范、指标可采集 |

### 2.2 典型使用场景

| 场景 | 用户操作 | 系统行为 | 结果 |
|------|---------|---------|------|
| 配置型实体快速上线 | 在 `bone-metadata-app` 建模实体并发布为 RUNTIME → 配置列表/表单 Schema | engine 按 `meta_*` 提供 `/api/v1/runtime/**` 动态 CRUD，前端按 Schema 渲染 | 不写代码得到可操作页面 |
| 列表精准查询 | 用户设置过滤条件、排序、选择显示列 | runtime 接口解析 filter/sort/fields 参数，走 sdk `Repository`/`FluentQuery` 执行 | 返回裁剪后的分页数据 |
| 并发编辑保护 | 两名用户先后编辑同一记录 | 后提交者请求携带过期 `If-Match` 版本 → 412 + 冲突提示 | 不发生静默覆盖（丢失更新） |
| 校验失败反馈 | 必填/格式/唯一性校验失败 | 后端返回 `META_` 前缀 `ProblemDetail.errorCode` | 前端按 errorCode i18n 展示 |

### 2.3 用户故事

1. 作为配置人员，我希望实体发布为 RUNTIME 后直接配置列表与表单 Schema，以便不投入开发资源即可上线业务页面。
2. 作为配置人员，我希望列表支持过滤、排序、列显隐配置，以便不同角色看到不同的数据视图。
3. 作为业务人员，我希望表单提交时前后端校验规则一致，以便错误提示准确且不会提交脏数据。
4. 作为业务人员，我希望并发编辑冲突时收到明确提示而不是数据被覆盖，以便安全地协作录入。
5. 作为开发者，我希望 runtime API 契约（OpenAPI）完整且向后兼容，以便集成方平滑升级。
6. 作为开发者，我希望模式 A 生成路径不受影响，以便信创审计与复杂领域场景继续走源码交付。
7. 作为租户管理员，我希望 Schema 配置与运行数据严格按 `tenant_id` 隔离，以便多租户数据不互见。
8. 作为集成/运维，我希望 runtime 查询与写入暴露 Prometheus 指标且错误码稳定，以便监控告警可落地。
9. 作为前端维护者，我希望 Schema 渲染复用现有微前端、i18n 与主题体系，以便不引入第二套基建。
10. 作为架构师，我希望新增存储与 API 经过 ArchUnit 与 DDL 门禁，以便分层与硬约束不被低代码能力绕过。

---

## 3. 范围

### 3.1 In Scope（本次变更范围）

- 模式 B 运行时 **查询能力补齐**：过滤 / 排序 / 列裁剪 / 分页（⏳ 契约已声明）。
- 模式 B 写路径 **并发控制**：`If-Match` 版本协商（⏳ 契约已声明）。
- **列表 Schema** 的定义、存储、渲染（含列、过滤器、操作）。
- **表单 Schema** 的定义、存储、渲染（含控件类型、布局、校验规则）。
- Schema 校验与 engine 校验同源联动，错误码遵循 `META_` 前缀与 `ProblemDetail.errorCode`。
- 相关 OpenAPI（`metadata-runtime-v1.yaml`）与错误码登记同步。

### 3.2 Out of Scope（明确不做）

- ❌ **不新增独立"低代码功能模块"/第四个引擎**——能力全部落在既有三模块（sdk 数据面 / server 控制面 / engine 计算面）内。
- ❌ BPM / 工作流引擎、可视化流程编排。
- ❌ 全应用装配器、页面级自由拖拽画布（本版仅 Schema 驱动的标准列表/表单）。
- ❌ 替代模式 A：generator 的 DDD 骨架、集成桩、信创导出等"逃逸舱"能力不变。
- ❌ SmartQL / 规则 / 投影查询的完整 HTTP 暴露（💡 Vision，见 §11 迭代规划）。

### 3.3 依赖与前置

- `meta_*` catalog REST 已实现（As-Is ✅）；RUNTIME 实体发布链路可用。
- engine 库已具备表达式/规则注册能力（SmartMeta，为 P2 预留，非本版阻塞）。
- 新增 DDL（Schema 存储，若确认）属 **L3：需架构师审批**，且以 `bone-init.sql` 为唯一真源。
- `bone-metadata-app`（:3004）已有建模 UI，可作为 Schema 配置入口承载方（归属见 §12 待决策 D1）。

---

## 4. 功能需求（MoSCoW）

| ID | 需求描述 | 优先级 | 验收标准（检查项） |
|----|----------|--------|--------------------------------------|
| FR-01 | runtime 查询参数补齐：`filter` / `sort` / `fields`（列裁剪）/ 分页，覆盖 `delivery_mode=RUNTIME` 实体 | Must | Given RUNTIME 实体，When 带上述参数请求 `/api/v1/runtime/**`，Then 返回符合 `metadata-runtime-v1.yaml` 的结果；契约测试全绿；所有查询经 sdk 数据面执行（engine 内无自有 JDBC 读写） |
| FR-02 | 写路径并发控制：更新携带 `If-Match`（或契约声明的等价版本头），版本不匹配拒绝写入 | Must | Given 记录 version=N，When 两条更新先后到达且第一条已提交，Then 第二条返回 412（契约语义）且数据不被覆盖；审计字段正常落库 |
| FR-03 | 列表 Schema：定义列（字段/标题/可见性）、过滤器、行操作，前端按 Schema 渲染列表页并调用 runtime API | Must | Given 已发布 RUNTIME 实体 + 列表 Schema，When 用户打开页面，Then 渲染出列与过滤器；过滤/排序/翻页联动生效；**0 新增页面源码** |
| FR-04 | 表单 Schema：定义字段控件、布局、必填/格式/取值约束，渲染新建/编辑表单并提交 runtime API | Must | Given 表单 Schema，When 提交合法数据，Then 记录创建/更新成功且 `tenant_id`/审计字段正确；非法数据被拦截 |
| FR-05 | 校验同源：Schema 校验规则同步到后端（engine 侧执行），失败返回 `META_` 前缀错误码 | Should | Given 必填字段为空提交，Then 后端拒绝且返回 `ProblemDetail.errorCode`（`META_` 前缀）；前端凭 errorCode 做 zh-CN/en-US 展示 |
| FR-06 | Schema 与实体变更联动：实体字段删除/改类型时对引用它的 Schema 给出阻断或显式失效提示，不产生静默渲染错误 | Should | Given Schema 引用字段被删除，When 保存实体变更，Then 系统列出受影响 Schema 并要求确认；已失效 Schema 打开时给出明确错误态 |
| FR-07 | Schema 草稿/发布状态与变更审计：配置可先草稿预览、发布后生效，变更记录可追溯 | Could | Given 配置人员修改 Schema，When 未发布时线上渲染不变；发布后生效且有变更记录 |
| FR-08 | 门禁与可观测：新代码通过 ArchUnit 分层规则、错误码入登记、runtime 暴露 metrics（micrometer-prometheus） | Must | `./scripts/check.sh` 通过；ArchUnit 规则全绿；指标可在 Prometheus 抓取端点出现 |

**优先级定义**：Must = 阻断上线；Should = 核心体验；Could = 锦上添花。

### FR-01 主流程（代表性详述）

**前置条件**：实体 `delivery_mode=RUNTIME` 且已发布；调用方已认证。

**主流程**：
1. 前端/调用方携带 `filter`/`sort`/`fields`/分页参数请求 runtime API。
2. engine 解析参数并按 `meta_*` 元模型校验字段合法性（非法字段 → `META_` 错误码）。
3. engine 编排 → sdk `Repository`/`FluentQuery` 执行（**engine 内禁止再实现一套 JDBC**，详设目标态约束）。
4. 按契约返回分页结果（`ApiResponse` + `PageResult`，见 Bone-API-规范 §3.3）。

**异常流程**：
- 非法过滤字段 / 语法错误 → 400 + `META_` 校验类错误码，不触达数据库全表扫描。
- 租户越权 / 跨租户标识 → 拒绝并按 COMMON_ 语义返回（`COMMON_FORBIDDEN`）。
- 数据库超时 → 断路/超时降级，返回可行动错误（见 §6 可用性）。

**边界条件与约束**：
- 单页分页上限沿用 runtime v1 既有契约（以 `metadata-runtime-v1.yaml` 为准，不另立规则）。
- 过滤/排序仅允许作用于实体已声明字段（含扩展字段三模式：预留列/JSON/EAV 中可查询者；JSON/EAV 的可过滤范围以 sdk 能力为准）。

---

## 5. 设计与交互（UI）

> 设计标准真源：[BONE 前端 UI 规范](../architecture/frontend/frontend-ui-spec.md)；组件选型、设计令牌与主题以其 §1.1/§1.2 为准，本节只定义本需求涉及的界面、控件与交互状态。

### 5.1 界面构成

| 界面 | 作用 | 承载方 |
|------|------|--------|
| 列表 Schema 配置 | 配置列（字段/标题/可见性/顺序）、过滤器、行操作 | Schema 配置入口（归属见 §12 待决策 D1） |
| 表单 Schema 配置 | 选择字段 → 设定控件类型、必填、占位、校验规则、布局分组 | 同上 |
| 渲染列表页 | 按列表 Schema 渲染（ProTable），调用 `/api/v1/runtime/**` 展示数据 | 运行态页面（建议与配置态同应用，见 D1） |
| 渲染表单页 | 按表单 Schema 渲染（ProForm），支持新建 / 编辑 / 只读查看三种模式 | 同上 |

**组件基线**：一律使用 Ant Design 5 + `@ant-design/pro-components`（ProTable、ProForm），**禁止引入第二套平行组件库**；设计令牌取自 `packages/ui/design-system`，经 `ConfigProvider` 注入（含 `props.themeMode`）。

### 5.2 控件类型映射（v1 范围）

| Schema 控件 | Ant Design 控件 | 说明 |
|------|------|------|
| text / textarea | Input / Input.TextArea | 长度校验来自字段定义 |
| number | InputNumber | 精度、范围来自字段定义 |
| date / datetime | DatePicker（showTime） | dayjs + utc/timezone 插件 |
| select（单/多） | Select / mode="multiple" | 选项来自字段枚举或字典 |
| boolean | Switch / Checkbox | |
| 未支持类型 | — | 显式提示"该控件暂不支持"并给出字段名，**不静默渲染为空**（配合 FR-06） |

### 5.3 关键交互与状态

| 状态 / 场景 | 交互行为 |
|------|------|
| 加载 | 页面骨架屏；提交按钮 loading 态（ui-spec §7.3） |
| 空态 | 列表无数据 → 引导"暂无数据 + 新建"；配置态无 Schema → 引导添加第一个字段 |
| 校验失败 | 字段级红框 + 顶部汇总提示；按 `META_` 错误码查 zh-CN/en-US 语言包（errorCode 优先于原始 message 回退） |
| 并发冲突（If-Match 412） | 非破坏式提示（Modal）："记录已被他人修改"，提供「重新加载」与「查看差异」入口，**不静默丢弃用户输入** |
| 字段失效（FR-06） | 受影响控件标红 + 说明文案，阻止发布而非打开即白屏 |
| 越权 / 无权限 | 按 `COMMON_FORBIDDEN` 统一无权限态文案 |
| 主题 / 多语言切换 | 跟随 Shell `themeMode` 与 `BONE_LOCALE_CHANGE`；暗色主题令牌取 dark 值，无需刷新页面 |

**无障碍**：WCAG 2.1 AA——筛选与表单键盘可达、焦点可见、错误提示与字段关联（`aria-describedby`）、操作有 loading/成功/失败三态反馈。

### 5.4 UI 验收标准

- [ ] 同一 Schema 在亮 / 暗主题下渲染正常，切换主题无需刷新。
- [ ] zh-CN / en-US 切换后，字段标题、校验文案、错误码文案全部可读（语言包对称，受 CI 同步检查守护）。
- [ ] 业务页无自建 Button / Table 等平行组件（代码评审检查项）。
- [ ] 412 冲突、校验失败、空态、加载四类状态均有明确视觉反馈。
- [ ] 列表筛选、翻页、表单提交操作键盘可达，焦点顺序合理。

### 5.5 设计稿

[TBD: 待设计师提供 Schema 配置界面与渲染页的设计稿链接；评审前可先以 §5.1–§5.2 表格作为低保真基线]

---

## 6. 非功能需求

| 类别 | 要求 |
|------|------|
| 性能 | runtime 列表查询 P99 ≤ [TBD: 基线待实测后定，建议 ≤ 500ms @ 单页 20 行、常规并发]；Schema 渲染首屏 ≤ 1s |
| 安全 | 认证沿用现有 IAM/JWT；所有读写按 `tenant_id` 隔离（禁直访 TenantContext，走 TenantProvider）；Schema 配置变更属敏感操作需审计 |
| 可用性 | runtime API 单点失败不拖垮平台其他能力；错误提示可行动；能力降级优先于报错退出 |
| 可观测 | 日志/指标/追踪三件套对齐现有模块；`health,info,metrics,prometheus` 端点一致配置；`META_` 错误码入 [Bone-错误码登记](../architecture/Bone-错误码登记.md) |
| 数据一致性 | Schema 存储与发布走本地事务；若涉及 Outbox，遵循"与业务聚合同事务、可靠存储优先"约束 |
| 测试 | 覆盖率门槛唯一真源为 [Bone-DDD G-1.7 HC-005](../architecture/Bone-DDD-最终实践方案.md)；本地自检 `./scripts/check.sh` 必须通过（P5） |

---

## 7. 接口与数据（契约级变更）

### 7.1 API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST 等 | `/api/v1/runtime/**` | **扩展**：新增 `filter`/`sort`/`fields` 查询参数与 `If-Match` 写协商；**向后兼容**——旧调用（无新参数）行为不变 |
| 待定 | Schema CRUD 路径 | [TBD: Schema 配置 API 的前缀归属——建议作为 catalog 控制面延伸（`/api/v1/metadata/...` 子资源）而非顶层 `/fields`，避免与扩展字段动作式路径冲突，见对照 §1.2 API 路径约定] |
| 不变 | `/api/v1/generator/**` | 模式 A 契约不受影响 |

> OpenAPI 真源：[doc/architecture/openapi/metadata-runtime-v1.yaml](../architecture/openapi/metadata-runtime-v1.yaml)；本 PRD 仅写契约级意图，字段级定义以 OpenAPI 评审为准。

### 7.2 数据模型

- **提案**：新增 Schema 存储（如 `meta_ui_schema` 或作为 `meta_entity` 关联子表）——[TBD: 表名与归属由技术方案定，需架构师 L3 审批]。
- 必须满足：`tenant_id` + `biz_identity_code`（或适用的业务标识）、审计字段、`version` 列（乐观锁）；DDL 仅进 `bone-init.sql`，所有环境 `ddl-auto=validate`。
- 不改动既有 `meta_*` 五表语义；如需加列，走 DDL 变更流程（L3）。

---

## 8. 向后兼容性

| 维度 | 策略 |
|------|------|
| 老数据 | 既有 RUNTIME 实体与记录零迁移；`delivery_mode` 语义不变（0=GENERATIVE / 1=RUNTIME） |
| API 兼容 | `/api/v1/runtime/**` **仅新增**可选参数与可选 `If-Match`；无参数请求按 v1 行为返回。`If-Match` 缺省行为 [TBD: 由技术方案明确——建议宽限兼容期后强制，避免破坏现存调用方] |
| 老用户迁移 | 渐进切换：Schema 渲染页与既有手写页可并存，按实体逐个切换，无强制升级 |
| 模式 A | generator 链路（CATALOG_SNAPSHOT 消费）不受影响；不删除任何生成能力 |
| 废弃时间表 | 本版无废弃项 |

---

## 9. 测试策略

### 9.1 测试原则

- 测试外部行为与契约（OpenAPI），不测实现细节；优先复用既有测试基础设施（ArchUnit、契约测试、JaCoCo 门禁），高层级覆盖优先。

### 9.2 测试范围

| 模块/功能 | 测试类型 | 覆盖要点 | 参考（Prior Art） |
|----------|---------|---------|------------------|
| runtime 查询参数 | 集成 + 契约 | filter/sort/fields/分页正确性、非法字段拒绝、租户隔离 | `metadata-runtime-v1.yaml` 契约测试 |
| If-Match 并发 | 集成 | 并发更新冲突 → 412、不覆盖、DomainEvents/审计正确 | 既有乐观锁（`saveWithVersionCheck`）相关测试 |
| Schema 校验联动 | 单元 + 集成 | 同一规则前后端一致；`META_` 错误码返回结构 | SmartMeta `ExpressionEngineTest` / `BusinessRuleEngineTest` |
| Schema 渲染 | 前端组件/E2E | 列表渲染、表单提交、错误态/空态、zh-CN/en-US | 既有微应用测试（见 doc/agents/04 §6.2） |
| 分层门禁 | 静态 | ArchUnit 分层、包归属、TenantProvider 使用 | `ArchitectureTest.java` + `FreezingArchRule` |

### 9.3 测试验收标准

- [ ] 核心场景（建模 → 配置 Schema → 列表/表单增删改查）有 E2E 覆盖。
- [ ] 并发冲突、非法参数、越权租户三类异常路径均有对应测试。
- [ ] 新 API 契约测试通过 OpenAPI 校验；`./scripts/check.sh` 通过。
- [ ] 测试可脱离外部依赖独立运行（无真实外部系统依赖）。

---

## 10. 发布与风险

### 10.1 发布策略

- **配置开关**：Schema 渲染入口与新查询参数按功能开关灰度（默认关闭，逐租户/逐实体开启）；所有条件装配 `matchIfMissing=false` 显式启用。
- **灰度**：先内部演示租户 → 单租户 RUNTIME 实体试点 → 扩大。
- **回滚**：关闭开关即回退到既有 CRUD v1 行为；新增 API 参数为可选，回滚无数据迁移负担。
- **发布检查清单**：Feature Flag 就绪；监控大盘与告警（`META_` 错误率、runtime P99）就绪；OpenAPI 与错误码登记已更新；`bone-init.sql` 与代码同批提交（含依赖文件成组提交）。

### 10.2 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 动态查询被构造为慢查询/全表扫描 | 高 | 过滤字段白名单（仅已声明字段）、分页上限、超时与断路；上线后以 metrics 为准调优 |
| Schema 绕过 DDD 硬约束（租户/审计/乐观锁） | 高 | 校验与写入统一走 sdk 数据面；ArchUnit + 门禁测试拦截；评审 checklist |
| `If-Match` 强制导致存量调用方破坏 | 中 | 先宽限期（缺省放行 + 告警），再强制；见 §8 TBD |
| 实体 Schema 字段失配导致渲染失败 | 中 | FR-06 变更联动阻断 + 显式失效态 |
| 范围蔓延（拖拽画布/流程引擎被顺带提出） | 中 | §3.2 Out of Scope 为评审基线，新增诉求走新迭代 PRD |
| 焦点稀释：偏离"开发者工作空间"定位 | 中 | 本 PRD 仅补双模式闭环，不改变 BONE X Studio 定位（详设 §1.1） |

---

## 11. 迭代规划

### 11.1 MVP 范围（第一个可交付版本）

**目标**：验证"配置型实体 0 代码得到可用列表/表单页面"这一核心假设。

| 功能 | 是否在 MVP | 说明 |
|------|-----------|------|
| FR-01 查询补齐 | ✅ | 数据面是渲染的前提 |
| FR-02 If-Match | ✅ | 写安全不可后补 |
| FR-03 列表 Schema | ✅ | |
| FR-04 表单 Schema | ✅ | |
| FR-05 校验同源 | ✅ | 基础必填/格式即可 |
| FR-06 变更联动 | ❌ | v1.1 |
| FR-07 草稿/发布 | ❌ | v1.1 |
| FR-08 门禁可观测 | ✅ | 随各 FR 交付 |

**MVP 验收 Checklist**：
- [ ] 一个新建 RUNTIME 实体，仅配置 Schema 完成列表+表单全流程，0 新增源码文件。
- [ ] 并发编辑冲突被拦截且前端有明确提示。
- [ ] `./scripts/check.sh` 与 ArchUnit 全绿；契约测试通过。

### 11.2 后续迭代与扩展点预留

| 迭代 | 核心交付物 | 备注 |
|------|----------|------|
| v1.1 | FR-06 变更联动、FR-07 草稿/发布、Schema 版本历史 | catalog 详设 §5.1 版本历史（P1）可衔接 |
| v1.2 | 规则/表达式 HTTP 暴露（部分 💡 Vision 项） | 引擎库已有能力，按分阶段暴露 |
| v2.0 | SmartQL / 投影查询、页面级布局扩展 | 需独立迭代 PRD 评估 |

**为下版本预留的接口/扩展点**：Schema 模型预留 `schemaVersion` 与类型判别字段；runtime 查询参数预留扩展位（未知参数按契约容忍策略处理）；表达式/规则注册表沿用 SmartMeta 既有注册机制，不另起炉灶。

---

## 12. 假设与待决策

### 12.1 假设（假设即风险）

| 假设 | 验证方式 | 如果不成立怎么办 |
|------|----------|------------------|
| A1：详设标注的 ⏳ 项（列裁剪/过滤/排序/If-Match）"契约已声明"意味着 OpenAPI 中已有定义，实现即闭环 | 实查 `metadata-runtime-v1.yaml` | 若契约未声明，先补契约评审再开发 |
| A2：配置型实体的典型字段规模在数十级，Schema 渲染性能无压力 | 试点实体压测 | 超大实体场景裁剪渲染或延后支持 |
| A3：Schema 存储新增表可获 L3 架构审批 | 方案评审 | 备选：复用 JSON 列/扩展字段能力存 Schema，避免 DDL |
| A4：`bone-metadata-app`（:3004）可承载 Schema 配置 UI | 前端架构确认 | 迁至运营台微应用（见 D1） |

### 12.2 未知问题与待决策

| # | 问题 | 提出人 | 需要谁决策 | 截止日期 |
|---|------|--------|------------|----------|
| D1 | Schema 配置与渲染 UI 归属哪个微应用（metadata-app 内新增 vs 运营台） | 本 PRD | 前端架构 | 方案评审前 |
| D2 | Schema 存储方案（新表 vs JSON 列 vs 复用扩展字段） | 本 PRD | 架构师（L3） | 技术方案评审前 |
| D3 | `If-Match` 缺省行为：宽限期长度与强制时间点 | 本 PRD | 技术负责人 | 契约评审时 |
| D4 | 目标发布时间与灰度租户范围 | [TBD: 需求提出方] | 产品 | 排期会 |

---

## 13. 附录

### 13.1 术语

| 术语 | 说明 |
|------|------|
| 模式 A · 生成式 | catalog → studio-generator → 源码进 Git，走 CI/CD |
| 模式 B · 运行时 | catalog → bone-metadata-engine → `/api/v1/runtime/**` 解释执行，不生成业务 Controller |
| delivery_mode | 实体字段：0=GENERATIVE / 1=RUNTIME（DB 为 TINYINT，API 用枚举名） |
| Schema 渲染 | 前端按 JSON Schema 配置渲染列表/表单页，本 PRD 的"低代码"呈现层 |
| 扩展字段 | 实体动态扩展属性，SDK 三模式（预留列默认/JSON/EAV）；与 catalog 建模字段（`meta_field`）不同 |
| SmartMeta | 引擎的表达式/业务规则能力（`bone-metadata-engine` §9） |

### 13.2 参考文档

| 文档 | 用途 |
|------|------|
| [BONE产品需求文档正式版.md](./BONE产品需求文档正式版.md) | 平台范围与 NFR 基线（§4.4 元数据） |
| [元数据能力-实现映射与竞品对照.md](../design/modules/元数据能力-实现映射与竞品对照.md) | 双模式交付单一真源（§1.3） |
| [2. 元数据管理模块详细设计方案.md](../design/modules/2.%20元数据管理模块详细设计方案.md) | runtime As-Is/Target/Vision 口径、§5.3.1、§9 |
| [doc/architecture/](../architecture/README.md) | 架构与 DDD 门禁、错误码登记、OpenAPI |
| [doc/design/modules/](../design/modules/README.md) | 模块详设 |
| [06-AI协作与编码准则](../agents/06-AI协作与编码准则.md) | 交付流程 P1–P8、自检清单 |

---

**审批**（可选）

| 角色 | 审批人 | 日期 | 状态 |
|------|--------|------|------|
| 产品 | | | |
| 技术 | | | |
