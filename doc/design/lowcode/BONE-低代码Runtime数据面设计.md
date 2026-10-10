# BONE 低代码 Runtime 数据面设计

> **定位**：模式 B（RUNTIME）低代码场景的**数据读契约 + 副作用动作边界**。  
> **原则**：复用既有 `JdbcRuntimeRecordService` / `RuntimeRecordController`，**不新建平行读通道**；有副作用的动作走显式 Handler（逃逸），HTTP 形状仍通用。  
> **v0.42 修订摘要（先读§0）**：①新增 §0.3「运行时已知能力缺口」诚实性声明（关联/子表/多条件过滤/复合唯一/深分页**全不存在**）；② 关闭 `PUT {status}` 冒充打款的后门（`state_field` 直写保护，**提为 Must**）；③ 实体级权限提为 Must（现状 = 租户内全库 CRUD）；④ Describe 收敛为 **page 级单形态**；⑤ 记录级变更审计提为 Must；⑥ 幂等三缺陷（scope 绑 user / TTL 硬编码 / 软删不可恢复）列入 Must。  
> **基线**：[低代码闭环 PRD](./BONE-低代码最小闭环PRD.md) v0.42 §2.5.9 / §0.6 / UIF-008；OpenAPI [`metadata-runtime-v1.yaml`](../../architecture/openapi/metadata-runtime-v1.yaml) v1.2.1（**待同步 §0.1 新增 Must 行**）；宿主 `bone-metadata-server :9001`。  
> **专题包**：[`README.md`](./README.md)  
> **样例**：费用报销 L0（`t_expense_claim`）+ `confirmPayout`；补货 `t_replenishment` + `receive`。
> **版本**：v0.43（2026-10-10）｜继承 v0.42（state_field 保护 / 实体级权限 / state_transition 引擎 / 回收站 / §0.3 诚实缺口）；本轮补逃逸半事务与补偿、动作输入 params 契约、多租户隔离、写校验失败码、Schema 演进、轻量变更事件。

---

## 0. 结论先行

| 问题 | 定案 |
|------|------|
| 实体是否动态？ | ✅ 路径只有 `{entityCode}`，无业务名词复数资源 |
| 列表/详情怎么读？ | ✅ 通用 Runtime CRUD（**已实现**） |
| **状态字段能被 `PUT` 直改吗？** | ❌ **禁止**（v0.42 提为 **Must**）：实体声明 `state_field` 后，`POST`/`PUT` 直写该字段 → `422 META_RUNTIME_FIELD_PROTECTED`。MVP 不得再以「手工改 ENUM」作为状态推进路径 |
| 动作是否也动态？ | ✅ 目标态 `…/actions/{actionCode}`（**P1 / UIF-008 Should**）。但其中 **`kind=state_transition` 已提为 MVP Must**（纯声明式、成本最低），`kind=escape` 保持 P1 |
| 打款/入库还要手写吗？ | ✅ **Handler 手写**，**URL / 按钮挂载通用** |
| 要不要 `/lowcode/query`？ | ❌ 不要（双真源） |
| 前端怎么知道有哪些按钮？ | ✅ **Describe**（P1）；**只留一种形态**：page 级 `meta.allowedActionsByStatus`（按 status 分组一次算全量，天然防 N+1，且无双真源） |
| **实体级权限** | ❌ MVP 现状 `metadata:runtime:read\|write` = **租户内全库 CRUD**；实体级 `runtime:{entityCode}:{read\|write}` 提为 **Must**（见 §4.6） |
| 关联/子表/多条件过滤能用吗？ | ❌ **不能**（关联 lookup、子表、复合唯一、富过滤 DSL 均不存在）——见 §0.3 缺口清单，**文档与 UI 不得暗示可用** |

> **已纠正裂缝**：禁止 MetaList 默认挂 `/expense-claims`、`/replenishments`。Mode A 手写页可保留自有 Controller。

---

## 0.1 As-Is / Planned 实现矩阵（排期真源）

| 能力 | 状态 | 说明 |
|------|------|------|
| Runtime CRUD（page/get/create/update/delete） | ✅ **As-Is** | `RuntimeRecordController` + `JdbcRuntimeRecordService` |
| `fields` / `sort` / `q` + 列白名单 | ✅ **As-Is** | 契约见 OpenAPI v1.2.1。⚠️ `q` 仅**单条件等值**（见 §0.3） |
| `ETag` / `If-Match` → 412 | 🟡 **As-Is（部分）** | GET/PUT 已有；**DELETE 无 If-Match** → 🔺 补齐 |
| POST `Idempotency-Key`（创建） | ✅ **As-Is** | 但**不强制**（缺失即执行）；scope/TTL 有缺陷（§4.5） |
| **`state_field` 直写保护 + `422 FIELD_PROTECTED`** | 🔺 **Must · 本迭代新增** | 关闭「`PUT {status:PAID}` 冒充打款」后门（§4.5.1） |
| **实体级权限 `runtime:{entityCode}:{read\|write}`** | 🔺 **Must · 本迭代新增** | 现状 `metadata:runtime:write` = **租户内全库 CRUD**（§4.6） |
| **记录级变更审计（who changed status X→Y）** | 🔺 **Must · 本迭代新增** | MVP 允许状态流转 ⇒ 审计是唯一兜底（§7） |
| **`kind=state_transition` 内置引擎** | 🔺 **Must · 本迭代新增** | 读转移矩阵→校验→改 ENUM；**纯声明式，成本最低**，与 escape 解耦 |
| **软删可恢复（回收站视图 + 恢复动作）** | 🔺 **Must · 本迭代新增** | 现软删单向不可逆、探测失败降级物理 DELETE（§0.3） |
| `POST …/actions/{actionCode}`（Invoke · `kind=escape`） | ❌ **Planned · P1** | 含 SPI Handler（§4.7） |
| page 级 `meta.allowedActionsByStatus`（Describe） | ❌ **Planned · P1** | **唯一 Describe 形态**（v0.42 收敛，取消单行端点，§4.3） |
| `RuntimeActionHandler` SPI + 注册表 | ❌ **Planned · P1** | escape 专用，见 §4.8 |
| 权限码 `runtime:{entity}:{action}`（动作级） | ❌ **Planned · P1** | 逃逸上线门禁（§4.5） |
| `filter` JSON（多条件/范围） | ❌ **Planned · v1.0.5 提前** | 原 P2。理由见 §2.6 |
| 批量动作 / 行级 ACL / 列级 FLS | ❌ **Planned · P2** | |

**验收纪律**：MVP Must = 上表 **✅ + 🟡（补齐项）+ 🔺** 行 + 前端消费；**❌ P1/P2 行不得写进本迭代 DoD**。评审演示逃逸用原型弹层或 UIF-004 口述即可。

---

## 0.2 业界对标与取舍（务实）

| 业界能力 | 代表 | Bone 采纳 | 明确不抄 |
|----------|------|-----------|----------|
| 动态对象 CRUD | Salesforce sObject REST、NocoBase Collection、Directus Items | ✅ `{entityCode}/records` | 不全盘上 OData/GraphQL |
| 列投影 / sparse fieldsets | JSON:API `fields[]`、Salesforce `fields=` | ✅ `fields=` + `projectColumns` 同源 | — |
| 简单过滤 | Airtable / Directus filter | ✅ MVP `q=field:value`（**单条件等值**；无全文/多条件/范围，见 §0.3） | MVP 不做公式引擎 |
| 富过滤 DSL | NocoBase JSON filter、Strapi operators | ⏳ **v1.0.5**（原 P2）：`filter` JSON（`$eq/$gt/$in/$and`） | 不做 FilterByFormula |
| **字段级保护（不可直写字段）** | Directus **special fields**（`user_created`/`date_updated`）；Salesforce **restrict picklist** + validation rule | ✅ **Must**：`state_field` 独占写入，直写 → `422 FIELD_PROTECTED`（§4.5.1） | — |
| **实体级授权** | Directus collection permissions；NocoBase 集合权限；Power Platform 表级权限 | ✅ **Must**：`runtime:{entity}:{read\|write}`（§4.6） | MVP 不做行级 filter ACL |
| 对象动作通道 | SF Invocable / Custom Actions；NocoBase `resource:action` | ✅ 统一 `…/actions/{actionCode}` | 不为每个业务开 REST 资源名 |
| 动作发现 | SF Actions describe；NocoBase `allowedActions` meta | ✅ **Describe API · 只留 page 级一种形态**（§4.3） | 前端硬编码按钮列表；**不做双形态** |
| 自定义副作用 | SF `@InvocableMethod` / Apex；NocoBase 自定义 Action | ✅ SPI Handler 注册 | MVP 不做可视化「无代码编排引擎」 |
| 动作级 ACL | NocoBase `posts:update` + filter | ✅ `runtime:{entity}:{action}` 权限码（§4.6，P1） | MVP 不做行级 filter ACL |
| 幂等 | Stripe Idempotency-Key（**scope = account+key，不绑 user**）；SF 外部 ID upsert | ✅ 逃逸动作**强烈建议**带 Key；资金/库存类 **Must 强制** | 不自研通用 saga |
| 乐观锁 | HTTP `If-Match` / ETag | ✅ CRUD + 动作均建议带；**DELETE 须补 If-Match** | 禁止业务层 `FOR UPDATE` |
| 批量动作 | SF Invocable List bulkification | ⏳ P2：`…/actions:batch` 或 body 多 id | MVP 单行 |
| 复合事务 API | SF Composite | ❌ 另立项 | 跨聚合由 Handler 本地事务 |

**一句话取舍**：学 Salesforce/NocoBase/Directus 的「**动态资源 + 可发现动作 + 可插拔实现 + 不可直写字段 + 实体级授权**」；不学「一上来就上完整 Filter/Flow/Bulk 平台」。

---

## 0.3 运行时已知能力缺口（诚实性声明 · 文档与 UI 不得暗示可用）

> **为什么有这一节**：本迭代多份材料（PRD BDD、UI 控件映射、场景表）曾按「运行时已具备关联 / 多条件过滤 / 复合唯一」书写，经 2026-10-10 代码级实测**全部不成立**。缺口本身不是本迭代缺陷，但**「假装有」是**。下表为代码真源口径，与 PRD §1.1.1 G10–G13 一一对应。

| 能力 | 运行时现状（代码真源） | 证据 | 何时 |
|---|---|---|---|
| **关联/引用字段（lookup）** | ❌ **不可用**。`RuntimeFieldColumn` 仅 6 属性（code/type/required/unique/primaryKey/physicalColumn），**无关系/目标实体/聚合**；`JdbcRuntimeRecordService` 全文件无 JOIN / 子查询 / group by | `RuntimeFieldColumn.java:11-23`；`CatalogRuntimeEntityProvider.java:57-69`；`JdbcRuntimeRecordService` | 另立项。这是低代码「像不像样」的分水岭（Airtable / NocoBase 均有），**不进本迭代，但必须承认在缺口清单里** |
| **子表 hasMany / 反向关系 / 聚合** | ❌ 不存在（同上） | 同上 | 另立项 |
| **多条件过滤 / 范围 / 关键词搜索** | ❌ 不存在。`q` 仅解析为**单个** `FilterSpec(field,value)`，渲染为 `` `col` = :v `` **严格等值**；无 LIKE、无 AND/OR、无 `$gt` 等运算符 | `RuntimePageQuery.java:59-70`；`RuntimeQuerySupport.java:56-70` | `filter` JSON → **v1.0.5**（§2.6） |
| **复合唯一约束** | ❌ 不存在。仅单字段 `unique` | `JdbcRuntimeRecordService.java:342-344` | 另立项。「同商品同仓库无未完结单」类规则当前**不可实现** |
| **深分页 / 游标分页** | ❌ 无。`offset=(page-1)*size`，`size` **硬截断 1..200**（无配置项） | `JdbcRuntimeRecordService.java:49-51` | 另立项。性能基线须按 **offset 口径**声明，勿按游标口径 |
| **软删可恢复** | ❌ **单向不可逆**。有 `deleted` 列 → `UPDATE deleted=1`；**列探测失败静默降级为物理 `DELETE`**；**无 undelete 接口** | `JdbcRuntimeRecordService.java:230-254` | **Must**：UI 明示「归档不可恢复」+ 回收站视图 / 恢复动作 |
| **`Idempotency-Key` 强制** | ❌ 不强制（缺失即执行），仅 POST 接入；PUT/DELETE 无幂等 | `RuntimeRecordController.java:91-116` | escape 动作上线前强制（§4.5） |
| **DECIMAL 精度标度不可声明** | ❌ **由类型硬编码**。DDL 生成一律 `DECIMAL(20,6)`（`MetaField.precision` 仅在**存量表导入**时承载 `numeric_precision`，正向建模不可指定 precision/scale）；`meta_field` 无 `numeric_scale` 列；金额 128.50 物理存为 128.500000 | `JdbcPhysicalStructureGatewayAdapter.java:496-500`（`mapToSqlType` 硬编码）；`MetaField.java:40-42`（仅 precision 列） | **另立项（v1.1）**：① `meta_field` 增 `numeric_scale` 列（L3 DDL）+ DDL 生成器读 precision/scale；② 前端 DECIMAL 渲染按 scale 格式化（`formatNumber`）。此前任何材料不得声称「金额按元/分精确显示」，须按 6 位标度如实演示 |

> **写作纪律**：任何材料声称运行时「支持关联字段 / 支持多条件筛选 / 支持复合唯一」时，一律先回本表核对。
>
> **范围锁**：本迭代数据面只补**存量洞**（状态字段保护、权限分级、幂等语义、软删可逆），**不新增运行时功能**——这是「不加功能先补漏」的一次修复，不是范围扩张。

---

## 1. 三层模型（低代码数据面）

```
┌──────────────────────────────────────────────────────────────┐
│  A · Catalog 读（结构）· entity / field / actions[] 元数据     │
├──────────────────────────────────────────────────────────────┤
│  B · Runtime CRUD · 实体动态                                   │
│  /api/v1/runtime/entities/{entityCode}/records[/{id}]        │
├──────────────────────────────────────────────────────────────┤
│  C · Runtime Actions · 实体+动作动态（目标态 · UIF-008）        │
│  GET  …/records/{id}/actions          ← Describe（发现）       │
│  POST …/records/{id}/actions/{code}   ← Invoke（执行）         │
│       ├─ kind=state_transition → 内置引擎                      │
│       └─ kind=escape           → 注册 Handler（手写）          │
└──────────────────────────────────────────────────────────────┘
```

| 层 | 动态键 | 实现 | 事务 |
|----|--------|------|------|
| A | entity / field / action 元数据 | catalog | 只读 |
| B | `{entityCode}` | `JdbcRuntimeRecordService` | 单表行 |
| C | `{entityCode}` + `{actionCode}` | Dispatcher → 内置 / SPI | Handler 自定（可跨聚合） |

**什么动态、什么手写**：

| 维度 | 动态（平台） | 手写（业务） |
|------|--------------|--------------|
| HTTP 路径 / Describe | `entityCode` / `actionCode` | — |
| MetaList 按钮 | catalog + Describe | — |
| 纯状态推进 | 转移矩阵 + 改 ENUM | 无（或极少） |
| 打款/入库 | 注册映射 + 权限码 | **Handler 内**凭证/库存逻辑 |

---

## 2. B 层 · Runtime 读 API（通用 · 契约真源）

### 2.1 端点（As-Is 宿主）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/runtime/entities/{entityCode}/records` | 分页列表 |
| GET | `/api/v1/runtime/entities/{entityCode}/records/{id}` | 详情（`ETag`） |
| POST | 同上集合 | 创建（可选 `Idempotency-Key`） |
| PUT | 同上 `/{id}` | 更新（可选 `If-Match`） |
| DELETE | 同上 `/{id}` | 软删（有 deleted 列时） |

**前置**：实体已发布 + `delivery_mode=RUNTIME`；列与 `meta_field.code` 对齐。  
**权限（MVP）**：`metadata:runtime:read|write`（兼旧码）。P1 可细化为实体级读（§4.6）。

### 2.2 列表查询参数（与 `RuntimePageQuery` 一致）

| 参数 | 格式 | 语义 | 示例（报销） |
|------|------|------|----------------|
| `page` | int ≥1 | 页码 | `1` |
| `size` | 1–200 | 页大小 | `20` |
| `fields` | `code,code,…` | **列投影**（引擎白名单） | `claim_no,title,amount,status` |
| `sort` | `[-|+]field,…` | `-` 降序 | `-expense_date,claim_no` |
| `q` | `field:value` | **单条件等值**（MVP） | `status:APPROVED` |

> ⚠️ MVP 的 `q` **不是**全文搜索，也**不支持**多条件 / 范围 / 运算符。多条件 → **`filter` JSON（v1.0.5，§6）**，文档勿写成已支持。代码真源见 §0.3。
>
> **查询成本（P2 治理）**：实体级 / 租户级无过滤的 `GET records` 是全表扫描面；`size` 已硬截断 1..200（§0.3），但应补**查询超时 / 成本上限**与敏感字段 FLS（§4.7 P2），避免粗权限下批量扫全实体。

### 2.3 响应信封

平台统一：`ApiResponse<PageResult<Map>>` / `ApiResponse<Map>`。  
分页：`records / total / page / size / pages / hasNext / hasPrevious`（字段真源 `PageResult.java`；契约对齐 [`components/PageResult.yaml`](../../../architecture/openapi/components/PageResult.yaml)）。**注意**：`PageResult.getList()` 为 `@Deprecated + @JsonIgnore`——历史文档中的 `list` / `pageNum` / `pageSize` 字段名已在 JSON 序列化层不存在，契约/前端一律以 `records` / `page` / `size` 为准。  
`total` 经全局 Long→String（契约声明见 [`components/PageResult.yaml`](../../../architecture/openapi/components/PageResult.yaml)；机制 = metadata-sdk 自动配置的全局 Long 序列化器，**业务侧禁止注册自定义 ObjectMapper 覆盖**，否则 total 类型静默回退 number）。详情带 `ETag`；更新 `If-Match` → 冲突 412。

### 2.4 错误码（CRUD 子集）

| errorCode | 场景 |
|-----------|------|
| `META_RUNTIME_ENTITY_NOT_FOUND` | 实体未发布/非 RUNTIME |
| `META_RUNTIME_RECORD_NOT_FOUND` | 行不存在 |
| `META_RUNTIME_INVALID_IDENTIFIER` | 非法字段名 |
| `META_RUNTIME_INVALID_QUERY` | `q`/`sort`/`fields` 解析失败 |
| `META_RUNTIME_FIELD_VALIDATION_FAILED` | 字段值校验失败（枚举越界 / 必填缺失 / 类型不符 / 精度溢出）——**服务端强制**，不信任前端 |
| （412） | If-Match 冲突 |

### 2.5 与前端列投影

```
catalog.fields + override ──projectColumns()──► fields=… 
                              ├── MetaList 列（界面/数据同源）
                              └── Runtime GET 只取需要列
```

**单一真源**：`projectColumns`（PRD §0.6.3）。

### 2.6 过滤演进（对标后收敛）

| 阶段 | 形态 | 对标 | 备注 |
|------|------|------|------|
| MVP | `q=status:APPROVED` | 极简 | 已实现语义（**单等值**） |
| **v1.0.5** | `filter={"status":{"$eq":"APPROVED"},"amount":{"$gte":100}}` | NocoBase / Strapi 风格子集 | **白名单运算符**；禁止任意表达式。**提前于 v1.1**：无多条件过滤时，实体一旦超过 10 列即不可用，MVP 落地后立刻会被卡住（详见 §2.6.1） |
| 不做 | FilterByFormula / 嵌入 SQL | Airtable / 裸查询 | 注入与治理成本过高 |

#### 2.6.1 为什么把`filter` 从 P2 提前到 v1.0.5

| 论据 | 说明 |
|---|---|
| **能力现状** | `q` 只能表达「一个字段等于一个值」。没有 AND / OR / 范围 / LIKE，用户无法表达「本部门待审批且金额 > 5000 的报销单」 |
| **产品定位冲突** | 单条件等值是**开发者工具**形态，不是低代码形态。低代码的价值在于让不懂 SQL 的人也能组合条件；当前形态并未兑现该价值 |
| **规模阈值很低** | 配置型实体超过 10 列即失去可用性（每列一个下拉选单条件），远低于 PRD 假设的「字段数十级」（§12.1 A3） |
| **不在 MVP 内** | MVP 有更大的一组欠账（状态字段保护、权限分级），**不在本迭代追加**。提前到 v1.0.5 是「紧随Must 之后立刻补」，不是「加进本迭代」 |

### 2.7 多租户隔离（As-Is · 代码真源）

> 运行时 CRUD 已按 `tenant_id` 隔离（含 `JdbcRuntimeRecordServiceTenantIsolationTest` 实测通过），但本设计此前未显式表述。补齐如下：

| 项 | 约定 |
|----|------|
| 记录归属 | 每条 Runtime 记录携带 `tenant_id`；`JdbcRuntimeRecordService` 所有读写自动注入租户谓词，调用方**无法**跨租户访问他租户数据 |
| 动作上下文 | escape Handler 须沿用调用方 `tenant_id` 上下文，**不得硬编码**租户；跨租户管理操作走独立管控通道 |
| 与权限关系 | 实体级 / 动作级权限码（§4.7）在**租户内**生效；`tenant_id` 谓词是更前置的物理隔离层，二者叠加 |
| 禁止 | 任何 Runtime 查询拼接裸 SQL 跳过租户谓词；Handler 内直连库绕过 SDK |

对标：多租户 SaaS 数据隔离（Salesforce / NocoBase 均按 org/tenant 物理或行级隔离）。

### 2.8 已发布实体的 Schema 演进策略（P2 · 另立项前须先定规则）

> 发布后实体的字段会随业务变化。`align` 加列已有，但**改类型 / 改名 / 删列**的演进规则此前未定义，会导致「in-place 改列把旧数据弄坏」或「删列丢历史」。

| 变更类型 | 约定（对标 Rails migration / Liquibase） |
|---------|------------------------------------------|
| 加列 | 发布时 `align` 加物理列，默认值 / 可空；**无碍旧数据** |
| 改名 | **不 inplace rename**：加新列 + 回填（双写窗口）+ 标记旧列 deprecated；旧列保留一段时间后再物理删除 |
| 类型变更 | 扩精度（如 DECIMAL(10,2)→(14,2)）安全；缩精度 / 改类型（STRING→INTEGER）须**数据迁移脚本**，禁止在线强转 |
| 删列 | 先标记 deprecated（UI 隐藏、写入停写），观察期后物理删除；**禁止直接 DROP** |
| 枚举值增删 | 增枚举值安全；删/改枚举值须评估存量数据，必要时迁移 |

> 原则：**schema 演进向后兼容优先**，不信任「在线改列零风险」。

---

## 3. A 层 · Catalog 读（结构）

低代码 UI **不**从 Runtime 行推断类型：

1. Catalog 取实体 + 字段 +（P1）`actions[]`  
2. `projectColumns` → 列元数据  
3. Runtime GET 只负责行数据  

草稿预览：界面 Tab 可读草稿；**数据 Tab 锁定**（UID-9）。

---

## 4. C 层 · 动态动作（发现 + 执行 + SPI）

### 4.1 Invoke 契约（目标态）

```http
POST /api/v1/runtime/entities/{entityCode}/records/{id}/actions/{actionCode}
Idempotency-Key: <uuid>          # 逃逸类 Should 强制（见 §4.5）
If-Match: "<version>"            # 强烈建议
Content-Type: application/json

{ "payload": { } }               # 可选；state_transition 可空；escape 按 Describe 声明的 params 填充
```

| 参数 | 动态 | 例 |
|------|------|-----|
| `entityCode` | ✅ | `t_expense_claim` |
| `id` | ✅ | 行主键 |
| `actionCode` | ✅ | `submit` / `confirmPayout` / `receive` |

> **输入参数契约（P1）**：`payload` 内的具名参数由 Describe 返回的 `params[]` 定义（§4.3.2）。`state_transition` 通常无需输入；`escape`（如 `reject` 需原因、`confirmPayout` 需渠道）按 `params[]` 的 `code/type/required/enumValues` 渲染表单并填充，前端**禁止**硬编码参数名。

**前端只拼这一套 URL**——换场景换 code，不换路由模板。

### 4.2 动作种类（kind）

| kind | 级别 | 例 | 实现 | 事务边界 |
|------|-----|-----|------|----------|
| `state_transition` | 🔺 **Must** | submit / approve / reject | 平台内置：读转移矩阵 → 校验 → 更新本行 ENUM（可复用 Runtime update） | 单行 |
| `escape` | P1 | confirmPayout / receive | SPI Handler；未注册 → 404 | Handler 本地事务（可跨聚合） |
| `unbound`（可选 · P2） | P2 | 无行 id 的批量导入等 | `POST …/entities/{code}/actions/{action}` | 另议 |

> 对标：Salesforce 区分 standard sObject 更新 vs Invocable；NocoBase 区分 create/update vs 自定义 action。Bone用 **kind** 显式切开，避免「所有动作都当 PUT」。
>
> **为什么把 `state_transition` 单独提为 Must**（v0.42 修正）：它与 `escape` 的成本量级完全不同——transition是**纯声明式**（矩阵写在 catalog 里，平台读矩阵→校验→改一个 ENUM），无需写任何业务代码；`escape` 则必须手写凭证 / 库存逻辑。把两者一起推到 P1，等于**最便宜的那条路也不走**；而留下的「`PUT {status:PAID}`」后门却是最贵的那种债（绕过状态机、审批、幂等、审计）。**先关洞，再谈贵的。**

### 4.3 Describe（动作发现 · P1 · 与 Invoke 同迭代）

对标 Salesforce Actions describe、NocoBase 列表 `meta.allowedActions`。

#### 4.3.1 形态收敛（v0.42 定案）：**只保留 page 级一种**

> **修正的问题**：原设计给了两种形态——列表嵌 `allowedActions` + 单行 `GET …/records/{id}/actions`——只写了「禁止 N+1」的禁令，**未规定何时用哪个**。两个端点 = 两个真源 + 两套契约 + 两套测试 + 两个前端消费路径，本身就是双真源，违反本设计 §0 的核心原则。

**定案**：取消单行 Describe 端点，只保留列表/快照级的 `meta.allowedActionsByStatus`。

| 为什么单形态够用 | 说明 |
|---|---|
| **权限与转移矩阵是行无关的** | 「哪些动作有权限」「从 X 能否到 Y」只取决于 catalog + 用户，不取决于行 |
| **只有「当前 status」是行相关的** | 按 `(entityCode, status)` 分组**一次算全量**：一页 20 行若只有 3 个不同 status，只需 3 次矩阵判定，无需per-row 计算 |
| **N+1 结构性消失** | 「防 N+1」不再依赖纪律约束，而由数据结构本身保证——这是比「禁止」更强的解法 |
| **详情页天然复用** | 详情页即列表的一行：前端已有该行 `status`，直接查本地 `allowedActionsByStatus[status]`，**零额外请求** |

> 对标：NocoBase 只有 `meta.allowedActions` 一种形态。Salesforce虽有独立 Actions describe 端点，但其返回是**行无关的 schema 级描述**，与本节「按当前状态决定按钮」的场景不同，不构成反例。

#### 4.3.2 契约形状（page 级）

```http
GET /api/v1/runtime/entities/{entityCode}/records?fields=…&page=1&size=20
```

```json
{
  "data": {
    "list": [
      { "id": "123", "status": "APPROVED" },
      { "id": "124", "status": "DRAFT" }
    ],
    "meta": {
      "allowedActionsByStatus": {
        "APPROVED": [
          {
            "code": "confirmPayout",
            "label": "确认打款",
            "kind": "escape",
            "enabled": true,
            "confirm": { "required": true, "message": "将生成付款凭证并置为已打款" },
            "permission": "runtime:t_expense_claim:confirmPayout",
            "requiresIdempotencyKey": true,
            "params": [
              { "code": "channel", "type": "enum", "required": true, "enumValues": ["BANK", "ALIPAY"], "label": "打款渠道" },
              { "code": "remark", "type": "string", "required": false, "label": "备注" }
            ]
          }
        ],
        "DRAFT": [
          { "code": "submit", "label": "提交", "kind": "state_transition", "enabled": true }
        ]
      }
    }
  }
}
```

规则：

1. 取 catalog `actions[]` ∩ 当前 `status ∈ from` ∩ 用户权限 ∩（`escape` 已注册 Handler），**按 status 分组**输出。
2. MetaList **禁止**前端写死按钮列表；以 `meta.allowedActionsByStatus[行.status]` 为准。
3. **必须按权限裁剪**（§4.6）：无权限的动作**不出现在**返回中，而不是返回 `enabled:false`——避免泄露「存在但你不能做」的信息。
4. **MVP**：Describe 未上线 → 原型弹层 / UIF-004 演示；URL 形状仍须是 §4.1（禁止业务名词路径）。
5. **详情页不单独请求**：直接复用列表/快照级结果按 status 查表；需要整页动作集合时取 `allowedActionsByStatus` 的所有 value并集（前端一次合并即可）。
6. **输入参数由 `params[]` 驱动**：动作若需调用方输入（如 `reject` 的拒绝原因、`confirmPayout` 的打款渠道），在 action 对象内声明 `params:[{code,type,required,enumValues,label}]`；前端按 `params[]` 动态渲染表单，Invoke `payload` 按 `code` 填充。**未声明的参数禁止前端硬编**（避免「文档与实现双真源」）。

### 4.4 Invoke 结果信封（对标 SF isSuccess / 便于审计）

```json
{
  "data": {
    "record": { "id": "123", "status": "PAID", "version": 4 },
    "action": "confirmPayout",
    "fromStatus": "APPROVED",
    "toStatus": "PAID",
    "sideEffects": [
      { "type": "payment_voucher", "ref": "PV-2026-0042", "label": "已生成付款凭证" }
    ],
    "idempotentReplay": false
  }
}
```

| 字段 | 用途 |
|------|------|
| `record` | 更新后的行（含新 `version` → 新 ETag） |
| `fromStatus` / `toStatus` | 状态机审计 |
| `sideEffects` | **结构化**副作用摘要（`type`/`ref`/`label`），供前端 toast 与人工排查；**不含**敏感明细 |
| `idempotentReplay` | 重复 Key 命中时为 true，HTTP 仍 200 |

> **v0.42 修正（原设计为短码列表 `["payment_voucher_created"]`）**：短码列表是反模式——① 把领域词汇泄漏进通用通道；② **没有可观测价值**，没人能根据一个短码做事或排查；③ 前端只能原样 toast。改为结构化对象后，前端可渲染可读文案，日志可按 `type` 聚合统计。
>
> **边界不变**：本字段是**返回给调用方的摘要**，不是审计记录，也不做事件溯源（§7）。真正的审计走记录级审计与领域事件。

### 4.5 状态字段保护（`state_field`）· **Must · v0.42 新增**

> **这是本迭代最高优先级的一条修复**，因为它关闭的是一个**数据完整性级后门**，不是补功能。

**问题**：`JdbcRuntimeRecordService` 对业务字段无差别放行（唯一约束是 `filterWritable` 过滤系统列）。因此**任何持有 `metadata:runtime:write` 的人都能用 `PUT {status: PAID}` 冒充打款**——绕过状态机、绕过审批、绕过幂等、绕过审计。这正是本设计 §4.10 禁止项第1 条，而 MVP 却曾把它当作正式状态推进路径。

**业界做法**（三处一致， Bone 当前全部缺失）：

| 平台 | 做法 |
|---|---|
| Directus | **special fields**（`user_created`/`date_updated`/`id`）不允许用户直写 |
| Salesforce | **restrict picklist** + validation rule：脱离规则的 save 无法写状态字段 |
| NocoBase | 定义了 transition 的状态字段，通用 update 接口不接受该字段 |

**定案**：

| 项 | 约定 |
|---|---|
| 声明 | catalog 实体标注 `state_field = { field: "status", transitions: [...] }`（DDL **L3**；过渡期可用扩展位零 DDL承载） |
| 写路径 | `POST` / `PUT` 请求体若包含 `state_field` 字段 → **拒绝**：`422 META_RUNTIME_FIELD_PROTECTED`（**不做静默忽略**，静默忽略会让调用方误以为写入成功） |
| 唯一合法入口 | `POST …/actions/{actionCode}`（`kind=state_transition`，Must） |
| 例外 | 仅 `platform.admin` 可直写（用于数据修复 / 迁移），且**强制写审计**（原因入参必填） |
| 与 `filterWritable` 的关系 | `state_field` 加入 `SYSTEM_COLUMNS` 同级的**保护字段**集合（不是系统列——它仍是业务字段，只是不可直写） |

**为什么放Must 而非 P1**：实现只是在写路径多做一次字段判定（几十行），却能把「任何人可审批任何单据」降为「有权限的人走状态机」。**成本极低、风险敞口极大，是本迭代性价比最高的一项。**

### 4.6 幂等与并发 · 逃逸上线门禁

| 规则 | 说明 |
|------|------|
| `Idempotency-Key` | escape + 副作用类：上线前 **Must 强制**；缺失 → 400 `META_RUNTIME_ACTION_IDEMPOTENCY_REQUIRED`（开发宽限期可配，**生产关**；代码真源 `MetadataErrorCodes.RUNTIME_ACTION_IDEMPOTENCY_REQUIRED`） |
| 存储 | 复用既有幂等存储（Key → 首次响应） |
| `If-Match` | 资金/库存类 **Must 带**；冲突 412。**DELETE亦须补 If-Match**（现状缺失，§0.3） |
| Handler 内 | 状态机再校验；同终态重复 → 200 + `idempotentReplay=true`，**不**二次记账 |
| 细权限码 | 资金/库存 escape 上线前 **Must** 具备 `runtime:{entity}:{action}`（不得仅靠 `metadata:runtime:write`） |

对标：Stripe——**钱和库存不允许「点两次出两笔」**。

#### 4.6.1 既有幂等实现的三个缺陷（2026-10-10 代码实测 · Must 修）

| # | 缺陷 | 代码真源 | 后果 | 修法 |
|---|------|---------|------|------|
| 1 | **scope 绑 `user`** | `CatalogIdempotencySupport.java:51-55`，scopeKey = `tenant\|user\|key\|method\|path` |幂等语义应是「同一业务意图」。Stripe 的 scope 是 **account+key，不含 user**。绑 user 后，**同一 Key 被两个用户各用一次会执行两次**——网关重试、用户换标签页、重试代理都会击穿幂等 | scope 去掉 `user`；`user` 改作**payload fingerprint 的一部分**，冲突走既有 409 |
| 2 | **TTL 硬编码 24h、不可配置** | `CatalogIdempotencySupport.java:27` 私有常量 `Duration.ofHours(24)` | 资金类动作 24h 后重放同一 Key 会**二次记账**——幂等记录已过期，而状态机再校验也不足够（若凭证已生成但状态回滚了） | TTL **配置化**；并允许 action 声明「幂等记录**跟随单据生命周期**」而非固定时长 |
| 3 | **软删 + 单字段 unique 的组合坑** | `JdbcRuntimeRecordService.java:342-344`（唯一校验带 `deleted = 0`） | 软删后 key 被释放 → 同一 key 可再建一条；且 runtime **无 undelete 接口** ⇒ 软删记录是**永久不可恢复的孤儿数据**，同时永久占用 key 空间 | Must：回收站视图 + 恢复动作（§0.3）；或至少 UI 明示「归档不可恢复」，禁止让用户以为能恢复 |

> **通用原则**：幂等的正确性依赖 **scope 语义** 与 **保存时长**，二者都比「有没有这个 header」更关键。Stripe 之所以可靠，是因为 scope 与业务语义对齐、且保存时长覆盖了整个业务窗口。

### 4.7 权限模型（对标 NocoBase `resource:action` / Directus collection permissions）

| 阶段 | 权限码 | 说明 |
|------|--------|------|
| **现状（真实风险）** | `metadata:runtime:read\|write` | ⚠️ **= 租户内全库 CRUD**：任何拿到该码的人可读写**本租户所有低代码实体的所有行**，实体间不隔离、行间不隔离。硬编码在 `@PreAuthorize` 注解字符串中，无配置、无实体粒度 |
| **Must（v0.42 提级）** | `runtime:{entityCode}:read` / `runtime:{entityCode}:write` | 实体级读/写分离。实现仅需在现有鉴权点按 `entityCode` 追加一次 `hasAuthority` 判断 + 权限码按 `bone-init.sql` 既有模式注册。**这是「全库 CRUD」与「按实体授权」的分界线** |
| P1 动作通道 | `runtime:{entityCode}:{actionCode}` | 例：`runtime:t_expense_claim:confirmPayout` |
| 逃逸上线 | 上表动作级细码 **Must**（§4.6） | |
| P2 | 行范围（own / filter）+ 列级 FLS（`fields=` 再裁） | 另立项；勿阻塞 UIF-008 |
| P2 演进 | `read\|write` 二分可进一步拆 `runtime:{entity}:create/update/delete` 四码（如「能 update 不能 delete」）；MVP 二分足够，按需细化 | 不阻塞 |

**为什么实体级必须现在做**（原计划归UIF-006 / v1.1）：它是「谁能动哪些数据」这条线上的**唯一一级**——行级、字段级都可以后置，但实体级缺失意味着**任何人都能改所有人的数据**。而它的实现成本是一个鉴权判断，比前端做列显隐裁剪便宜得多，收益大得多。**前端裁剪（UIF-006）可以后端延后，后端实体级授权不能延后。**

Describe / `allowedActions` 必须按权限裁剪，且**无权限的动作不出现**在返回中（§4.3.2 规则 3）。

### 4.8 SPI 合约与部署模型

```java
// 示意 · 落点 application 或 engine SPI，禁 Controller 直调 domain
public interface RuntimeActionHandler {
  String entityCode();
  String actionCode();

  void validate(RuntimeActionContext ctx);

  /** 本地事务内：跨聚合写入 + 更新本行状态 */
  RuntimeActionResult execute(RuntimeActionContext ctx);
}
```

注册：Spring `@Component` → `RuntimeActionRegistry` 键 `(entityCode, actionCode)`。  
冲突：同键双注册 → **启动失败**（fail-fast）。  
命名：类可叫 `ExpenseClaimApplicationService.confirmPayout`；对外只暴露动态 URL。

**部署（P1 定案）**：

| 项 | 约定 |
|----|------|
| 进程 | Handler 与 `RuntimeActionDispatcher` **同进程**加载（挂到 `bone-metadata-server` 或其所扫业务 starter） |
| 归属 | Handler **源码**在业务模块（如 `bone-blueprint` / 未来 expense 模块）；通过 **可选依赖 / Spring Boot starter** 注册进宿主，**不**把凭证/库存表建进 metadata-server |
| 边界 | metadata-server 只提供 Dispatcher + 注册表 SPI；跨聚合写仍走业务仓 SDK Repository |
| 不做 | MVP 不做跨进程 RPC 动作总线（另立项） |

> **边界澄清（消歧）**：上表「同进程 + 不做跨进程 RPC」指 **Dispatcher → Handler 的分派不跨进程**——Handler 经 Spring Boot starter 物理打进 `bone-metadata-server` 同一 JVM。但 **Handler 内部调用其他 Bone 微服务（如支付域、库存域）属于跨服务调用，不受此限**：Handler 通过业务仓 SDK / HTTP 调下游，下游调用本身须自带幂等键（见 §4.8.1）。「不做跨进程 RPC 动作总线」是**不引入独立动作编排中间件**，不是禁止 Handler 访问其他服务。

```
POST …/t_expense_claim/records/123/actions/confirmPayout
        │
        ▼
 RuntimeActionDispatcher
        ├─ 鉴权 permission
        ├─ If-Match / Idempotency-Key
        ├─ catalog 校验 kind/from
        ├─ Registry → ExpenseClaimConfirmPayoutHandler
        │      validate → execute（凭证 + status=PAID）
        └─ 审计：action_code / operator / from→to / sideEffects
```

对称：`…/t_replenishment/…/actions/receive` → `ReplenishmentReceiveHandler`。

### 4.8.1 逃逸半事务与补偿语义（P1 · 财务安全前置）

> **问题**：`confirmPayout` / `receive` 调**外部有副作用系统**（支付网关 / 库存服务），该调用**无法纳入 metadata-server 的本地 DB 事务**。当前 §4.8 注释 `execute` 在「本地事务内」会让开发者误以为「扣款成功 ⇄ 本行状态变更」原子——实际上任一半失败都会留下**中途崩溃态**（钱扣了状态没变 / 状态变了钱没扣）。

**约定（对标 Stripe / Outbox）**：

| 项 | 约定 |
|----|------|
| 外部调用自幂等 | Handler 向下游透传 `Idempotency-Key`（或下游自身的幂等键）；同 Key 重放 ⇒ 下游不二次记账 |
| 状态与副作用解耦 | 先落本地 `pending` 中间态 + 写 **Outbox 事件**（RC-005），异步/同步确认后再推进终态；禁止在 `execute` 内假设「外部调用与本地更新同提交」 |
| 两阶段（资金类） | 可选「预占 → 确认」：先 `RESERVED` 占位，下游确认成功再 `PAID`；超时/失败回 `APPROVED` |
| 失败补偿 | 外部成功 + 本地失败 ⇒ 重试幂等重放（状态机再校验，同终态不二次记账）；本地成功 + 外部失败 ⇒ 补偿回滚 / 告警人工介入 |
| 禁止 | 在 `execute` 本地事务里同步 await 外部支付且期望同回滚；把下游异常吞掉当成成功 |

> 这与 §4.6 幂等是配套的：**幂等保证「点两次只出一笔」，本节能保证「出了一笔但状态没跟上」可被发现与补偿**。二者合起来才闭环资金安全。

### 4.9 Catalog 动作声明（驱动 UI）

```yaml
# 状态字段声明（Must · §4.5）：声明后该字段不可经 POST/PUT 直写
state_field:
  field: status
  type: enum
  enum: [DRAFT, SUBMITTED, APPROVED, PAID, CANCELLED]
  # 转移矩阵由 actions[].from/to 汇总，平台读矩阵校验，无需额外声明
actions:
  - code: submit
    from: [DRAFT]
    to: SUBMITTED
    kind: state_transition
    label: 提交
  - code: approve
    from: [SUBMITTED]
    to: APPROVED
    kind: state_transition
    label: 审批通过
  - code: confirmPayout
    from: [APPROVED]
    to: PAID
    kind: escape
    label: 确认打款
    confirm: true
    requiresIdempotencyKey: true
    # handler 由运行时注册表解析，不写死类名进库（避免重构炸元数据）
```

MetaList：Describe → 按钮；`confirm: true` → 二次确认（原型已演示）。

### 4.10 错误码

| errorCode | HTTP | 场景 | 级别 |
|-----------|------|------|------|
| `META_RUNTIME_FIELD_PROTECTED` | 422 | 直写 `state_field`（§4.5） | 🔺 **Must** |
| `META_RUNTIME_ENTITY_FORBIDDEN` | 403 | 无 `runtime:{entityCode}:{read\|write}`（§4.7） | 🔺 **Must** |
| `META_RUNTIME_ACTION_NOT_FOUND` | 404 | 未声明或未注册 Handler | P1 |
| `META_RUNTIME_ACTION_ILLEGAL_TRANSITION` | 409 | from 不匹配 / 终态再推（代码真源 `MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION`） | 🔺 **Must（transition，已落地）** / P1（escape） |
| `META_RUNTIME_ACTION_IDEMPOTENCY_REQUIRED` | 400 | 逃逸缺 Key（强制模式） | P1 |
| `META_RUNTIME_FIELD_VALIDATION_FAILED` | 422 | 请求体字段值校验失败（同 §2.4，动作 payload 同样适用） | P1 |
| `META_RUNTIME_FORBIDDEN_ACTION` | 403 | 无 `runtime:{entity}:{action}` | P1 |
| （412） | 412 | If-Match（含 DELETE，Must 补齐） | Must |

> **登记纪律**：新增错误码须同步 `MetadataErrorCodes.java`、OpenAPI `metadata-runtime-v1.yaml` 与 PRD §10.3 Playbook 三处，缺一即为「契约漂移」（PRD G1/D5）。

### 4.11 手写边界与禁止项

| 要手写 | 不手写 |
|--------|--------|
| confirmPayout / receive Handler | `/expense-claims`、`/replenishments` 公开资源 |
| 凭证表 / 库存聚合逻辑 | 每个实体一套 Controller |
| Handler 注册（一行） | 前端写死按钮与 URL |

**禁止**：

1. `PUT …/records/{id} {status:PAID}` 冒充打款（**Must 关闭**：§4.5 `state_field` 保护）  
2. 复用订单域 `PaymentApplicationService`  
3. MetaList 默认挂业务名词路径  
4. 业务/应用层 `SELECT … FOR UPDATE`（用 `@Version` / If-Match / 幂等）  
5. 在 metadata-server 内建报销凭证表（域归属错误）  
6. **静默忽略**被拒字段（`state_field` 直写必须显式 422，勿 emulate 静默 drop）  
7. **把软删当可恢复**（无undelete 接口；探测失败会降级为物理 DELETE ⇒ UI 须明示「归档不可恢复」，§0.3）  
8. **宣称运行时支持关联/多条件过滤/复合唯一**（均不存在，§0.3；写文档与 UI 时先核该表）

### 4.12 MVP / v1.0.5 / v1.1 档位

| 档 | 平台 | 业务 | 前端 |
|----|------|------|------|
| **MVP Must** | B 层 CRUD（§0.1 ✅）+ **`state_field` 直写保护** + **实体级权限** + **`state_transition` 内置引擎** + **记录级变更审计** + DELETE补 If-Match + 回收站视图 | 无 escape Handler | 状态徽标 + 合法下一状态按钮；**不再「手工改 ENUM」** |
| **v1.0.5** | `filter` JSON 子集（多条件/范围） | — | 查询区组合条件 |
| **P1 Should** | Invoke（escape）+ **page 级 `meta.allowedActionsByStatus`** + SPI | 样板 escape Handler ×1；§4.6.1 三缺陷修完；§4.6 上线门禁 | UIF-008；按钮只拼动态 URL |
| **P2** | 批量、行/列 ACL | 更多 Handler | — |

> **档位变化的实质**：MVP 不再以「手工改 ENUM」推进状态，而是交付**声明式状态机 + 直写保护 + 审计**三件套。代价是MVP 多一项 `state_field` 元数据声明（§4.9），收益是 MVP 上线当天即不存在「谁都能审批」的洞。

---

## 5. 报销 L0 端到端（全动态）

```
0→1 模板 expense_claim → 发布 t_expense_claim（声明 state_field = status）
1. Catalog 读字段/actions
2. GET  …/records?fields=…&sort=-expense_date
       → meta.allowedActionsByStatus（page 级，MVP 已有 state_transition 部分）
3. POST …/records                     （body 含 status → 422 FIELD_PROTECTED）
4. POST …/actions/submit              （state_transition · Must；内部改 DRAFT→SUBMITTED）
   POST …/actions/approve             （state_transition · Must）
5. P1：POST …/actions/confirmPayout    (+ Idempotency-Key, If-Match) —— escape 手写 Handler
6. GET  列表 → status=PAID
```

换补货：换 `entityCode` + `receive`——**前端胶水共用**。

非法：`PUT {status:PAID}`（MVP 已由 `422 META_RUNTIME_FIELD_PROTECTED` 关闭）；或 MetaList 写死 `/api/v1/expense-claims/...`。

---

## 6. 分期与明确不做

### 6.1 分期

| 阶段 | 内容 | 级别 |
|------|------|------|
| **P0 · Must** | CRUD 契约 / 前端 `fields/sort/q` + 412；**`state_field` 直写保护（422）**；**实体级权限码**；**`state_transition` 内置引擎**；**记录级变更审计**；**DELETE 补 If-Match**；**回收站视图 + 软删不可恢复明示**；**幂等三缺陷修**（scope 去 user / TTL 配置化）；文档叙事统一 | **Must** |
| **v1.0.5** | `filter` JSON 子集（多条件 / 范围） | 紧随 Must |
| **P1 · Should** | Invoke（`kind=escape`）+ page级 `meta.allowedActionsByStatus` + SPI + 动作级权限码 + 动作错误码 + 样板 Handler ×1 | UIF-008 |
| **P2** | 批量动作、行/列 ACL、关联lookup / 子表 / 复合唯一 / 游标分页 | 另立项（§0.3） |
| **另项** | VF-001 编排 / GraphQL / Composite / 跨进程动作总线 | 不进本设计 |

### 6.2 明确不做

| 不做 | 理由 |
|------|------|
| `/api/v1/lowcode/**` | 双真源 |
| 业务名词复数路径作低代码规范 | 不通用 |
| MVP Runtime 内写死打款/入库 | 域边界；SPI |
| 通用 GraphQL / SmartQL / FilterByFormula | 另立项 |
| metadata-server 建报销凭证表 | 业务域归属 |
| 可视化无代码「万能动作编排」当 Must | 用 Handler + 后续 VF-001 |

---

## 7. 可观测与审计

### 7.1 记录级变更审计 · 🔺 Must（v0.42 提级）

| 项 | 约定 |
|---|---|
| **触发** | 任何**受保护字段**变更（`state_field`）、软删、恢复 |
| **内容** | `entityCode, recordId, field, from, to, operatorId, reason, source(api\|admin-fix), traceId` |
| 落点 | **append-only** 审计表（复用既有 `DefaultAuditService` 能力——**实测未接入 metadata-server 写路径**，本迭代接入） |
| 为什么 Must | MVP 交付状态流转能力 ⇒ 必须能回答「谁把哪张单据从 APPROVED改成 PAID」。**没有记录级审计的状态机是不完整的状态机** |
| 例外豁免 | 无。`platform.admin` 直写修复同样留痕，且 `reason` 必填 |

### 7.2 动作级可观测（P1 随escape Invoke）

| 项 | 约定 |
|---|---|
| 结构化日志 | `entityCode, recordId, actionCode, operatorId, from, to, idempotencyKey, replay, latencyMs` |
| 指标 | `runtime_action_total{entity,action,result}` |
| 禁止 | 日志打印 payload 里的账号/金额明文超限（脱敏） |
| P2 变更事件 | 经 Outbox（RC-005）发 `runtime_record_changed`（entityCode/recordId/action/from/to/operator）；供「审批通过→通知」等触发；**不替代审计**（§7.1）与动作 `sideEffects`（§4.4） |

对标：支付类 API 的 audit trail；Salesforce Platform Events / NocoBase hook / Supabase realtime 的轻量变更通知；**不做完整 event sourcing**。`sideEffects` 是返回给调用方的摘要，**不是审计记录**（§4.4）。

---

## 8. 验收判据

| # | 判据 | 级别 |
|---|---|---|
| R1 | CRUD：换 `entityCode` 无需新 Controller | Must |
| R2 | 文档/原型示例含 `{entityCode}`+`{actionCode}`，无业务复数默认路径 | Must |
| R3 | Handler 手写，挂载路径与巡检/报销/补货同一模板 | P1 |
| R4 | `projectColumns` → `fields=` 同源（**跨语言**：须共享黄金样例双向测试，见 PRD §0.6.3） | Must |
| R5 | 能说清：动态=通道，手写=副作用；`meta.allowedActionsByStatus` = 按钮真源 | Must |
| R6 | 逃逸样板：Idempotency-Key + 动作级权限码；重复调用不二次记账 | P1 |
| R7 | §0.1 矩阵：MVP 验收只含 ✅/🟡/🔺，不含 Planned | Must |
| R8 | 业界对照（§0.2）能讲清学什么 / 不学什么 | Must |
| **R9** | **`PUT {status}` 直写被拒 → 422 `FIELD_PROTECTED`；合法入口只有 `POST …/actions/{code}`** | 🔺 **Must** |
| **R10** | **`state_transition` 走通 DRAFT→SUBMITTED→APPROVED；非法转移 409** | 🔺 **Must** |
| **R11** | **无 `runtime:{entity}:write` 的人直调该实体 API → 403 `ENTITY_FORBIDDEN`** | 🔺 **Must** |
| **R12** | **每次 `state_field` 变更/软删可在审计表查到 who/from/to/reason** | 🔺 **Must** |
| **R13** | **DELETE 缺 If-Match 被拒或按无保护处理（须有明确判据）；软删 UI 明示「不可恢复」且有回收站视图** | 🔺 **Must** |
| **R14** | **幂等：同 Key 不同 user 重放 → 幂等命中（不二次执行）；TTL 可配置** | 🔺 **Must** |
| **R15** | **§0.3 缺口表被遵守：无材料宣称运行时支持关联/多条件过滤/复合唯一** | 🔺 **Must** |

---

### 9.1 v0.43 变更记录（2026-10-10 · 方案优化）

> 来源：用户复核诊断（对标业界最佳实践）后要求对合理建议执行优化。本轮**只改文档、不新增运行时功能、不新增 DDL**，与 §0.3「范围锁」一致。

| # | 建议（原优先级） | 落点 | 内容 |
|---|---|---|---|
| 1 | P0-1 部署模型自相矛盾 | §4.8 边界澄清 | 明确 Dispatcher→Handler 同进程（starter 装配）；Handler 内跨服务走 SDK/HTTP 不受限；「不做跨进程 RPC 动作总线」= 不引独立编排中间件 |
| 2 | P0-2 逃逸半事务/补偿 | §4.8.1（新增） | 外部副作用不可纳入本地事务；幂等透传、Outbox 解耦、预占→确认两阶段、失败补偿 |
| 3 | P1-1 动作输入无 params 契约 | §4.1 / §4.3.2 | Invoke payload 按 `params[]` 填充；Describe 返回 `params:[{code,type,required,enumValues,label}]`，前端动态渲染 |
| 4 | P1-3 多租户沉默 | §2.7（新增） | 记录带 `tenant_id`、SDK 注入谓词、Handler 沿用上下文、禁止裸 SQL 跳租户 |
| 5 | P1-4 写校验失败码缺失 | §2.4 / §4.10 | 增 `META_RUNTIME_FIELD_VALIDATION_FAILED`（枚举越界/必填缺失/类型不符/精度溢出），服务端强制 |
| 6 | P1-2 CRUD 四码 | §4.7 | `read\|write` 二分可演进为 `create/update/delete` 四码（P2 按需） |
| 7 | P2-2 变更事件缺位 | §7.2 | 经 Outbox 发 `runtime_record_changed`（P2），不替代审计 |
| 8 | P2-3 Schema 演进 | §2.8（新增） | 加列/改名/改类型/删列/枚举增删的演进规则（向后兼容优先） |
| 9 | P2-5 查询成本 | §2.2 | 补查询超时/成本上限 + FLS 治理注记（size 已截断 1..200） |
| 10 | P0-2 契约 list 字段残留 | OpenAPI / §2.3 | spec 分页字段 `list`→`records`（对齐 `PageResult` 真源；`getList` 本就 `@JsonIgnore`，`list` 从未序列化过）；§2.3 信封同步 |
| 11 | P0-1 DECIMAL 精度未接线 | §0.3 / PRD §1.1.1 / §2.5.9 | 新增缺口 G20：`toSqlType` 硬编码 `DECIMAL(20,6)`，`MetaField.precision` 零引用、`meta_field` 无 scale 列；演示口径改为「界面按 scale=2 渲染（step=0.01），物理存储 (20,6)，显示以界面为准」，不再承诺存储侧二位小数 |

**诊断时实务测、本轮确认无需改的项**：P2-1 过滤提前 v1.0.5（§2.6）、P2-4 游标分页诚实声明（§0.3）、P2-6 权限批量计算（§4.3.1 已结构性消除 N+1）、§0.3 诚实缺口、state_field 保护 / 实体级权限 / state_transition 引擎（v0.42 已含）。

---

**一句话**：学SF/NocoBase/Directus 的动态资源、可发现动作与**不可直写字段**；读走 Runtime CRUD，状态走声明式转移机（Must），副作用走同一 `actions` 通道 + SPI Handler（P1）；Describe / 幂等 / 权限码把「通用」落到可运营，而不是再开一套 `/expense-claims`。
