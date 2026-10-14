# BONE 业务对象界面 JSON 协议设计（v1.0 · 详细方案）

> 位置：`doc/design/lowcode/ui/ui-protocol/BONE-业务对象界面JSON协议设计.md`
> 配套：`bone-ui-schema.schema.json`（本目录，JSON Schema 真源）+ `../../cases/ui-schema-samples/expense-claim-ui-schema.example.json`（报销实例）
> 基线：PRD v0.50 / Runtime 数据面 v0.49 / `费用报销系统-元数据建模方案` L0+L1 / 现状 `InterfaceTab.tsx`（纯列投影 `UiOverlay`）+ `columnProjection.ts`
> 参考借鉴（仅思路，不抄实现）：`/创业项目/code/pk-tpa-saas/tpa-sass-vue3` 的 `RenderEngine`（Page/Form/Block/Table 组件树、linkageRule/submitRule 双轨、Base/Exclusive 两层继承、processPage 列表配置）

---

## 0. 一句话与设计纪律

**从「静态数据表建模」走向「业务对象建模」，但保持"声明式轻量"**：补表达力（流程/富类型/规则/布局/权限引用），**不建画布、不建公式引擎、不建第二数据模型**。

| 纪律 | 含义 |
|---|---|
| 单真源 | 字段定义永远在 `meta_field`；`UiSchema` 只用 `fieldCode` **引用**，禁止重复声明 type/length/required（否则就是平台一贯反对的"第二数据模型"变体） |
| 引用不复制 | 动作引用 `actionCode`（`meta_entity_action_def` 真源）、行策略引用 `policyCode`（`row_policies` 真源）；UI 只声明"哪里挂、怎么显"，不重写 WHERE/流转 |
| 表格即编辑器 | 状态机转移矩阵、条件构建器一律用表格/表单行编辑，**不做拖拽画布**（与 `InterfaceTab` "catalog 投影+轻覆盖"哲学一致） |
| MVP 封顶 | linkage 只做显隐/必填/只读/赋值；submit 只做比较+求和；公式引擎、或签会签、BPMN 全都不进 v1 |

---

## 1. 为什么是这个协议（缺口 → 协议项映射）

| 报销真实需求 | 现状缺口 | 协议对应项 | 业界/参考 |
|---|---|---|---|
| 报销单 30-50 字段要分区（基本信息/行程明细/发票/审批） | 表单按 sortOrder 直排 | `sections[]`（FieldSet/Block） | Salesforce Page Layout；TPA `Block/FieldSet + body[]` |
| 明细子表、引用成本中心/类别、发票附件、多币种 | 向导选不到 REFERENCE/CHILD_TABLE/ATTACHMENT/MONEY | `controlKind` 正式类型入口（G10 运行时早已支持，建模补入口） | Dynamics Money/Lookup/File；TPA `Table + Upload + SelectDrop` |
| 明细合计=申报金额、结束≥开始、专票→税号必填、单笔限额 | 只能数值 min/max | `linkageRules[]` + `submitRules[]` | TPA `useLinkageRule/useSubmitRule`；Salesforce 验证规则；Power Apps Business Rules |
| DRAFT→SUBMITTED→APPROVING→APPROVED/PAID/REJECTED 按钮 | 后端动作完整但建模只能读 | `actionBar[]` 引用 `actionCode` + 状态门控 | TPA `processPage`；Kintone 进程管理 |
| 本人看自己、经理看本部门、财务看全部 | `rowPolicies` 后端强制执行但前端 0 引用 | `list.dataScope.policyCode` 引用 + `tabs/search` | TPA `dataRange/tabConditionList/searchFieldList`；Dynamics 安全角色 |
| 模板要一次打出父单+子表+引用+附件+动作流+行策略 | 模板只含字段 | `uiSchema` 进模板资产包（base），实例做 exclusive 轻覆盖 | TPA Base 模板 vs Exclusive 主体覆盖 |

TPA 借鉴点（思路级）与取舍见 §7。

---

## 2. 协议总览（三层）

```
meta_entity / meta_field / meta_relation / meta_entity_action_def / row_policies
  —— 结构+行为+治理真源（已有，不动）
        │ 引用（code / fieldCode / actionCode / policyCode）
        ▼
BoneUiSchema（新增，有版本，可发布快照）
  views.list / views.form / views.detail + sections + controls + rules + actionBar
        │ 纯函数解释
        ▼
<BoneDynamicList/> <BoneDynamicForm/>（新增，antd 映射 + 规则 hooks）
```

- **存量升级路径**：当前 `UiOverlay`（显隐/改名/列序/密度，`UI_OVERLAY_VERSION=2`，localStorage）是 `BoneUiSchema.views.list.columns` 的子集。v1 前端先做"读新写新、读旧兼容"：有 `uiSchema` 用新协议，无则回落 `projectColumns(fields, overlay)`，行为不变。
- **后端首版职责**：只存（CRUD + 版本校验 + 发布时打快照），**不在后端跑 linkage/submit**（前端跑；提交时后端仍跑已有 `RuntimeWriteValidator` + `ExpenseClaimWriteHook` + 行策略强制，安全底线不后移）。

---

## 3. JSON 协议（规范说明，Schema 以 `bone-ui-schema.schema.json` 为准）

### 3.1 顶层

```json
{
  "schemaVersion": 1,
  "entityCode": "t_expense_claim",
  "displayModes": ["create", "edit", "view"],
  "views": { "list": {}, "form": {}, "detail": {} },
  "dictionaries": {}
}
```

- `schemaVersion`：整数，不兼容变更 +1；渲染器遇到未知大版本**显式降级提示**（沿用 PRD §5.5 禁静默），不猜渲染。
- `entityCode`：绑定对象；一个实体**一份** `uiSchema`（草稿态可改，发布时随实体快照冻结，见 §5）。
- `dictionaries`：枚举/码表覆写只允许**引用**字段 `enumValues`，不允许在 UI 层新造码表。

### 3.2 `views.list`（借鉴 TPA `processPage.getProcessListPage`）

| 字段 | 含义 | 对标 TPA |
|---|---|---|
| `columns[]` | `{fieldCode, visible, width, pinned?, format?}`；`fieldCode` 必须是 `meta_field.code` 或 `items(CHILD_TABLE 声明)`；MONEY 允许 `format:{currencyField}` | `table.fieldSortTypeList` 简化版 |
| `searchFields[]` | 搜索栏字段 code 列表（报销：部门/状态/金额区间，`filter` JSON 已在 v1.0.5 就位，这里只声明"搜哪些"） | `searchFieldList + enableSearch` |
| `tabs[]` | `{title, fieldCode, value}`（如 按状态分 Tab：待我审批/我提交的/全部） | `tabConditionList` |
| `dataScope` | `{policyCode}` —— **只引用**后端的 `row_policies` code（如 `owner_or_approver`），WHERE 仍后端强制 | `dataRange`（1全局/2按账号/3按字段）的声明式替代 |
| `batchActions[]` | 批量动作 `actionCode` 引用（首版只允许已发布 action） | — |

### 3.3 `views.form` / `views.detail`（借鉴 TPA `Page.body[] → Form → Block → Control`）

```json
"form": {
  "sections": [
    { "id": "sec_base", "title": "基本信息",
      "controls": [{ "fieldCode": "title" }, { "fieldCode": "category_id" }] },
    { "id": "sec_travel", "title": "行程与金额",
      "controls": [{ "fieldCode": "amount" }, { "fieldCode": "expense_date" }] },
    { "id": "sec_items", "title": "费用明细",
      "controls": [{ "fieldCode": "items", "controlKind": "CHILD_TABLE" }] },
    { "id": "sec_files", "title": "发票影像",
      "controls": [{ "fieldCode": "attachment", "controlKind": "ATTACHMENT" }] }
  ]
}
```

- `sections[]` 即分区（TPA `Block/FieldSet`），只有 `id/title/controls`，**无坐标、无画布**；顺序即渲染顺序。
- `controls[]` 项：`{fieldCode, controlKind?, labelOverride?, placeholder?, readonly?, visible?, props?}`（visible 为静态默认可见性，缺省 true；与联动 then[].visible 配合实现先隐后显）。
  - `controlKind` 默认按字段类型自动映射（§3.4），仅富类型需显式写（`REFERENCE/CHILD_TABLE/ATTACHMENT/MONEY`）。
  - `props` 为受控白名单（见 Schema `additionalProperties:false` 的分类型 props），不开放任意透传（防"JSON 走私第二模型"重演）。
- `detail` 与 `form` 同形，渲染器按 `displayMode=view` 只读渲染（对标 TPA `DisplayModeEnum EDIT/VIEW/CONFIG/PREVIEW`，Bone 取子集 `create/edit/view`）。

### 3.4 控件注册表（Bone 类型 → antd 映射）

| controlKind / 字段类型 | 渲染 | 关键 props（白名单） |
|---|---|---|
| STRING / TEXT | Input / TextArea | `maxLength, rows` |
| INTEGER / LONG / DECIMAL | InputNumber | `min, max, precision`（MONEY 精度见下） |
| MONEY（新增建模类型，DDL 走 L3） | InputNumber + 币种 Select（双列同行） | `currencyField: code, precision: 2`（存储 DECIMAL(20,6)，界面按 scale=2，沿用 G20 口径） |
| BOOLEAN | Switch | — |
| DATE / DATETIME | DatePicker（showTime）；日期不带时区（跨时区场景由租户层约定，协议不隐式换算） | — |
| ENUM | Select | 选项**只读**字段 `enumValues`，UI 不得重写 |
| REFERENCE | 远程下拉（`lookup-options` + `labelField` 翻译值） | `targetEntity, labelField, valueField:id` |
| CHILD_TABLE | 内嵌 Table + 弹窗行编辑（整组替换事务 `replaceChildren`） | `childEntity, columns[], summary?` |
| ATTACHMENT | 清单式上传列表（JSON name/url/size；真上传另立项，走 OSS 时换 `uploadApi`） | `maxCount:10, maxSizeMB:10`（沿用 G1 口径） |

未支持类型沿用现有纪律：显式降级 Alert（`isRenderSupported`），禁静默空渲染。

### 3.5 `linkageRules[]`（行内联动：条件 → 改属性/赋值；借鉴 TPA `useLinkageRule`）

```json
{ "when": { "fieldCode": "invoice_type", "operator": "eq", "value": "SPECIAL" },
  "then": [{ "fieldCode": "tax_no", "required": true, "visible": true }] }
```

- `operator` 全集复用 TPA `OperatorEnum`：`isNull/isNotNull/in/notIn/eq/neq/gt/gte/lt/lte/between/earlierThan/laterThan`；渲染器按控件类型收敛可选符（复用 `ComponentTypeValidOperators` 思想：文本无大小比较，日期用早晚，数字全量）。
- `value` 支持固定值与字段引用：`{valueType: "fixed"|"field", value / fieldCode}`（对标 TPA `ValueTypeEnum` + `getComparedValue`）。
- `then` 允许：`visible / required / readonly / disabled / notice / tier / value`（赋值仅 `eq/null` 两算子，抄 TPA `excuteRule` 的保守口径，防联动写爆；`notice` 黄标提示、`tier` 档位显隐见 §10.2）。
- 执行点：字段值变更即时重算 + 提交前重放一次；循环联动（A→B→A）渲染器按"单轮收敛、次轮只读提示"处理并打 warn 日志。

### 3.6 `submitRules[]`（提交校验：跨字段/父子汇总；借鉴 TPA `useSubmitRule`）

```json
{ "name": "明细合计一致", "function": "sum",
  "fields": ["items.amount"], "operator": "eq",
  "compare": { "valueType": "field", "fieldCode": "amount" },
  "message": "费用明细合计必须等于申报金额" }
```

- `function` 命名断言白名单：`sum | count | max | min | compare | balance | unique | consistency | completeness`（`compare` 取 `fields[0]` 为左值；`balance/unique/consistency/completeness` 带 `params{tolerance,minReasons,scope}`。**不开自定义函数名**，与 TPA `functionName` 自由字符串不同——封"公式引擎"口）。
- `operator` 同 §3.5 比较符子集；`compare` 固定值/字段引用二选一。
- `verifyType` 沿用 TPA 思想：`ERROR` 阻断提交 / `WARNING` 弹确认后可提交；`message` 必填（无裸"校验失败"）。
- 可选 `when` 生效前置（与 linkage 的 `when` 同形状；如拒赔完整性仅 `status=REJECTING` 时跑，缺省全量跑——串联验证实证：无 `when` 则条件型规则误伤正常提交）。
- 与后端关系：前端先拦（体验），后端 `RuntimeWriteValidator` 的 min/max + `ExpenseClaimWriteHook` 照旧强制（安全），两者文案对齐但**以后端为准**。

### 3.7 `actionBar[]`（状态机动作挂载；只引用不定义）

```json
{ "actionCode": "submit", "visibleWhen": { "fieldCode": "status", "operator": "eq", "value": "DRAFT" } }
```

- `actionCode` 必须在 `meta_entity_action_def` 已发布；`params_schema` 由后端 `validateParams` 统一校验，前端按 schema 自动出入参表单（打款 `channel/note` 即此链路，v0.47 已接线）。
- `visibleWhen` 复用 linkage 的 `when` 形状（最常用就是"状态门控"）；权限码（`permissionCode`）与幂等由后端 `RuntimeActionController` 强制，前端只做显隐，不做鉴权判断。

### 3.8 版本、校验与发布

- 草稿态 `uiSchema` 随实体草稿编辑；发布时与实体结构打**同一快照**（`PublishPreview` 增加"界面 diff"区块：新增 section/规则数，只读摘要）。
- 结构变更影响：在途单据影响分析（P2 衔接项）首版只做"字段被 UI 引用后删除/改名 → 发布校验 ERROR 并给定位"，不做全量数据迁移分析。
- `validationRules`（字段级 JSON）与 `submitRules`（对象级）并存：前者管单字段，后者管跨字段/跨行；建模界面把前者从"JSON 文本框"升级为"条件构建器"（P0-3），两者共用同一套 operator/value 词表，防两套话术。

---

## 4. 动态渲染（前端）

### 4.1 组件

- `<BoneDynamicForm schema entity fields actions onSubmit/>`：`sections → controls` 映射 antd；内部 `useBoneLinkage(rules, form)`（对标 TPA `useLinkageRule`：checkRule→excuteRule→propertyStore 更新）+ `useBoneSubmit(rules)`（对标 `useSubmitRule`：factory.execute→compare→messages）。
- `<BoneDynamicList schema records/>`：columns/search/tabs/actionBar；行级策略不改变查询形状，只透传 `operator`（后端按 `policyCode` 加 WHERE，前端**不拼**）。
- 渲染器是**纯函数+hooks**，无远端拉取、无缓存写；数据读写统一走现有 runtime records/children/lookup/actions 通道（Runtime 数据面设计 §2/§4），不新开业务 endpoint。

### 4.2 渲染流程（与 TPA `Render/Page/Form/Table` 对应）

1. 取结构（`meta_field` 全量）+ 取 `uiSchema`（有则用，无则回落 `projectColumns` 默认单区表单）；
2. 按 `displayMode`（create/edit/view）决定控件 disabled 态；
3. 字段值变更 → 跑 `linkageRules`（属性层进内存 store，值层回写 form）→ 受影响控件重渲染；
4. 点动作 → 查 `actionBar` 门控 → 按 `params_schema` 弹参 → 调 actions API → 按 `toStatus` 刷状态；
5. 点提交 → 跑 `submitRules`（ERROR 阻断 / WARNING 二次确认）→ 调 records/children API（后端再强制一次）。

---

## 5. 存储与后端（概要，具体 DDL 走 L3 审批）

- 新聚合 `UiSchemaDef`（`TenantAbstractEntity` 子类，带 `tenantId`+审计；`entityId` 唯一约束防双活）：
  `id / tenant_id / entity_id / schema_json(MEDIUMTEXT，存 BoneUiSchema) / schema_version / updated_by`。
- 持久化唯一走 `bone-metadata-sdk`（`@EnableSqlRepositories`），禁 MyBatis-Plus/JPA（HC-001/HC-006）；分层 `adapter→application→domain←infrastructure`，Controller 只注 `*ApplicationService`、返回 `ApiResponse`（HC-002/HC-003 遵行）。
- 接口（Modeling 侧，与 Runtime 侧 `listActions` 只读对称补齐）：
  `GET/PUT /api/v1/metadata/entities/{id}/ui-schema`（幂等全量替换 + `schemaVersion` 乐观锁，`@Version`，禁 `FOR UPDATE`，遵 ADR-0031）+ `POST .../ui-schema/validate`（引用完整性：fieldCode/actionCode/policyCode 存在性）。
- 模板资产：`meta_template` 实例化时把模板 `baseUiSchema` 拷贝为实体的 `exclusiveUiSchema`（TPA Base→Exclusive 思想的轻量版：只拷一份快照，之后各自演进，不做持续继承链，防"改模板惊动全租户"）。
- 并发：汇总字段（父单总额）仍服务端在明细事务内单点重算（`ChildAmountAggregator` 雏形转正），`uiSchema.summary` 只声明"显示哪"，不声明"怎么算"——汇总值是单写者，不是并发争用点（ADR-0031 口径）。

---

## 6. 报销实例走读（`../../cases/ui-schema-samples/expense-claim-ui-schema.example.json`）

- 4 区：基本信息（title/category_id/applicant/expense_date）/ 行程与金额（amount + currency/行程日期）/ 费用明细（`items` CHILD_TABLE：日期/类别/金额/备注 + `summary:{sum(amount)→amount}` 显示）/ 发票影像（`attachment` ATTACHMENT）/ 审批（approver_id REFERENCE + status 只读）。
- 3 联动：专票→税号必填；`expense_end earlierThan expense_start` 禁止（ERROR）；金额>5000 → 审批区高亮+必填审批人。
- 2 提交：`sum(items.amount) eq amount`；单行 `amount gte 0.01`（与后端 min:0.01 同值）。
- 动作条：`submit(DRAFT→SUBMITTED) / approve / reject / confirmPayout(APPROVED→PAID，带 params_schema channel/note)`，各带状态门控。
- 列表：`tabs[待我审批/我提交的/全部] + search[部门/状态/金额区间] + dataScope{policyCode: owner_or_approver}`。

---

## 7. TPA 借鉴清单（抄什么、改什么、不抄什么）

| TPA 资产 | 借鉴（思路） | Bone 的改法（为什么） |
|---|---|---|
| `Page/Form/Block/Table + body[]` 组件树 | 三级嵌套 + `body[]` 递归 | 压成两级 `sections.controls`；TPA 的 Form/Block/MainBlock/FieldSet 四层对报销够用但偏重，Bone 首版两级即可（简单优先 §11.12） |
| `DisplayModeEnum EDIT/VIEW/CONFIG/PREVIEW` | 显示模式驱动同一 schema 多态渲染 | 取 `create/edit/view` 子集；`config` 是建模态不进运行时渲染 |
| `OperatorEnum + ComponentTypeValidOperators + ValueTypeEnum(fixed/field)` | 操作符全集 + 按控件收敛 + 固定/引用二态 | 全盘借鉴（已在 §3.5/3.6 落字）；比较器按类型分文件（TPA `comparators/`）思想保留 |
| `useLinkageRule(check→execute→propertyStore)` | 条件→改属性/赋值双通道 | 借鉴流程；赋值收敛到 `eq/null`；属性收敛到显隐/必填/只读/禁用/提示/档位/赋值七动作（防范围膨胀） |
| `useSubmitRule(factory.execute→compare→messages)` + `verifyType` | 提交期函数校验 + 错误文案 | `function` 收敛九函数白名单（§3.6），不开自由函数名（封公式引擎）；`verifyType` 保留 ERROR/WARNING |
| `processPage(tabConditionList/searchFieldList/dataRange/pageHead)` | 列表页也是协议（Tab/搜索/数据范围/页头） | `tabs/search/dataScope.policyCode` 对应落字；`dataRange` 数字枚举改成引用 `policyCode`（Bone 行策略已是 WHERE 真源，不再另造一套数字） |
| Base 模板 vs Exclusive 主体覆盖 | 模板与实例分离 | 轻量版：实例化拷快照、之后断开继承（TPA 的持续 pull 规则 `pullRulesFromBase` 不抄——多租户下模板变更惊动面不可控） |
| `Table(initApi/submitApi/deleteApi/fieldSortTypeList/summary/aggregate)` | 子表自带数据契约+汇总 | `init/submit/delete` 不进协议（Bone 统一走 runtime 通道，不允许每表自挂 API）；`summary` 只声明显示，计算在服务端单点重算 |
| 不抄 | GET 做写操作（`createExclusivePage/updateStatus` 等用 GET）、`convertPercentageForCalculation` 这类业务硬编码进通用引擎、专属字段无限分叉 | 前者违反 Bone API 规范；中者违反"报销 hook 不进通用引擎"的既有注释纪律；后者用白名单 props 封口 |

---

## 8. 落地顺序（与 P0/P1 对齐，只列协议相关）

1. **协议冻结**：本设计 + `bone-ui-schema.schema.json` 评审通过（L2 双人 Review；Schema 变更走 ADR 增补，不随手改）。
2. **P0-1 渲染器**：`<BoneDynamicForm/List>` + linkage/submit hooks + 回落 `projectColumns`（不 block 现有界面 Tab）。
3. **P0-2 建模 Tab**：结构 Tab 旁新增「界面（协议）」：分区编辑 + 控件 props 白名单表单 + linkage/submit 条件构建器（表格行编辑）+ actionBar 门控表；`validationRules` JSON 文本框同步替换为条件构建器。
4. **P0-3 服务端化**：`UiSchemaDef` + Modeling CRUD/validate + 发布快照 + 模板资产打包（base 快照）。
5. **P1**：MONEY 建模类型（DDL，L3 审批）、行策略选择器（引用口径）、汇总显示、在途影响分析（删除被引用字段阻断发布）。

## 9. 验收（可验证口径）

- 报销实例 JSON 能被 Schema 校验通过，且在无后端情况下渲染出 4 区表单（含子表行增删、附件清单占位、动作条按状态显隐）。
- 专票→税号必填、结束早于开始阻断、明细合计≠申报金额阻断，三条在前端可复现，后端各有对应强制（422 或行策略 404）。
- 无 `uiSchema` 的老实体界面行为与今天完全一致（回落投影，截图 diff 为空）。
- 新增协议字段不产生新的 `meta_field` 行（查库验证：单真源）。

---

## 附：文件索引

- Schema 真源：`./bone-ui-schema.schema.json`（v1.1，向后兼容 v1.0）
- 实例样板：`../../cases/ui-schema-samples/expense-claim-ui-schema.example.json`（报销 v1.0）+ v1.1 五场景：
  `claim-outpatient-fasttrack-ui-schema.example.json`（医疗门诊快赔）·
  `procurement-review-report-ui-schema.example.json`（采购审查报告）·
  `equity-research-card-ui-schema.example.json`（股票研究卡片）·
  `erp-purchase-order-ui-schema.example.json`（ERP 采购单）·
  `crm-opportunity-ui-schema.example.json`（CRM 商机）
- 现状证据：`bone-frontend/apps/bone-metadata-app/src/components/InterfaceTab.tsx`、`src/utils/columnProjection.ts`、`src/pages/EntityDetail.tsx`
- TPA 参考：`RenderEngine/{base/{Page,Form,Block,Table},hooks/useLinkageRule.js,useSubmitRule.js,modules/{linkageRule,submitRule}}`、`api/{page,processPage,field,model}.ts`、`enums/rule/*`
- 场景输入：`doc/ai-product/synapClaim`（Rulebook R1.0 + MVP 接口契约）·`doc/ai-product/procuraAI`（原型交互设计 + D3 接口契约）·`doc/ai-product/synapEquity`（产品方案 v0.15：研究卡片/假设面板/模块 S 策略纪律）

---

## 10. 通用性复核与 v1.1 增量（2026-10-13：synapClaim / procuraAI / synapEquity + ERP/CRM）

> 结论先行：**v1.0 通过、v1.1 补齐**。v1.0 的"引用不复制 + 白名单"骨架 six 场景通用；缺的是五类表达（分栏/工作台/图表/命名断言/HITL 与合规），v1.1 全做**可选增量**，v1.0 报销实例零修改仍合法。

### 10.1 复核矩阵（v1.0 是否够用）

| 场景诉求 | v1.0 | v1.1 补齐 |
|---|---|---|
| 报销：分区/子表/附件/动作门控/行策略 | ✅ 全覆盖 | — |
| 理赔：员工端+核赔工作台双端；待办三卡片；R-DED 年度累计/封顶共享显示；R-FRAUD 风险标记 P0 转人工；≥1 万双人复核；拒赔≥3 理由；发票vs清单差额>1；申诉 APPEALING；假名化 PII 脱敏；决策轨迹时间线；资金两段式支付确认 | 部分（双端/累计/双人/理由数/脱敏/时间线缺） | `list.stats` 待办卡片；`STAT/COMPUTED(payableAmount)+lineage` 只读引用；`requireDualApproveWhen`；`completeness{minReasons:3}`；`consistency{tolerance:1}`；`mask:hash`；`TIMELINE`；支付 `confirm` |
| 采购审查：左 40% 风险面板+右 60% 原文（锚点联动）；三一致三栏对照；采纳/驳回/改判 HITL + 六类理由；批量采纳二次确认；处置流 3 轮上限；LLM 降级横幅；规则启用/灰度（建模 Tab 事，不进渲染协议）；四角色菜单 | 缺分栏/对照/HITL/轮次/降级 | `layout:split-left-40`；`section.kind:compare`；`actionBar.decisionKind:hitl + reasonTypes`；`confirm`；`maxRounds:3`；`list.degradedBanner`；`roles` |
| 投研：六要素卡片 + 白话结论；假设滑块实时重算；PE Band/敏感性矩阵/同业散点；一键入口（便宜/稳/增长）+高级筛选折叠；策略模板匹配显示；跟踪提醒付费档；档案版本链；免责+AI 标识+模拟标注；数字溯源悬停；DCF 手动假设强警告 | 缺滑块/图表/档位/版本链/合规条 | `SLIDER(min/max/step)`；`CHART(line/band/scatter/matrix/funnel)`；`quickFilters`；`tier:[free/pro]`；`TIMELINE`；`footer{disclaimer,aiBadge,mockDataMark}`；`lineage/ai`；`warningText` |
| ERP：头行结构；借贷平衡（容差 0.01）；期间控制+跨期警告；编号只读；过账不可逆二次确认；外币汇率 | 缺平衡断言 | `balance{tolerance}`；跨期 `notice`；过账 `confirm`；`COMPUTED(debitDiff)` 只显示 |
| CRM：漏斗看板；阶段→概率联动；预期收入计算显示；查重 WARNING；赢单完整性；公海领取；折扣审批 | 缺看板/查重 | `viewKind:kanban`；`unique{scope:team}` WARNING；`completeness`；`CHART(funnel)` |

### 10.2 v1.1 增量清单（全部可选，向后兼容）

1. **区块**：`section.kind: form/dashboard/timeline/compare/report/tasks` + `layout: single/split-left-40/split-left-60`（审查分栏、工作台卡片、版本链、对照表、对账）。
2. **控件**：`SLIDER`（假设面板；min/max/step）·`CHART(line/band/scatter/matrix/funnel/bar/donut` + `dataRef)`（图表只引聚合口径，UI 不自算；donut=环形占比）·`STAT`（卡片）·`QUOTE(anchorRef/quoteText)`（原文引用卡 / 条款引用，quoteText 为引用块正文）·`TIMELINE`（版本链/轨迹，steps 数据驱动）·`COMPUTED(computedFn 白名单 daysLeft/pricePosition/debitDiff/payableAmount/expectedRevenue)`（纯展示；资金与理算仍后端确定性计算）·`ALERT`（内联警告）。
3. **引用增强**：`props.lineage`（数字溯源）·`props.ai{generated,confidence}`（AI 标识）·`props.mask: hash/last4`（PII 脱敏）·`props.tier`（订阅档）·`props.roles`（四角色）·`props.warningText`（红字/黄标）。
4. **linkage then**：`disabled`（按钮可用性）·`notice`（黄标提示）·`tier`（档位显隐）。
5. **submit function**：`balance/unique/consistency/completeness + params{tolerance,minReasons,scope}`（命名断言库，非自由表达式——公式引擎继续封口）。
6. **actionBar**：`decisionKind: open/hitl` + `reasonTypes` + `confirm{title,content}` + `maxRounds` + `requireDualApproveWhen`。
7. **list**：`viewKind: table/calendar/kanban` + `stats[]` + `quickFilters[]` + `degradedBanner`。
8. **顶层**：`footer{disclaimer,aiBadge,mockDataMark}` 常驻合规条。

### 10.3 守住的边界（v1.1 明确不做）

- 图表/计算字段**只显示不决策**：估值数字、理算金额、借贷结果一律后端确定性引擎为准（理赔"AI 不参与金额决策路径"、投研"数字注入+一致性校验"、ERP 过账事务——三条线同一纪律）。
- 规则引擎版本/灰度/评测集是**建模 Tab**的事，不进渲染协议（procuraAI 规则管理页另行设计）。
- Temporal 长流程、资金状态机、回测快照是**后端域**，协议只表达"进度/版本/提醒"，不编排流程。
- 五样板中 `valueRef/dataRef/lineage/policyCode/actionCode` 全为引用占位，联调时以后端聚合端点与 `meta_*` 真源为准；CRM `expectedRevenue` 已用具名函数（`expected_amount` 复用 `payableAmount` 的旧 hack 已修）。

---

## 11. 业界诊断与优化（2026-10-13：对标 RJSF/Amis/XRender + Salesforce/PowerApps/NocoBase）

> 方法：逐条用"业界做法 → 本方案问题 → 修不修（为什么）"过筛。已修 6 项（P0，均为兼容增量）；暂缓 4 项（P1/P2，附理由）。

### 11.1 已修（本次直接落协议 + 样板）

| # | 业界最佳实践 | 本方案问题（已实证） | 修复 | 为什么必须修 |
|---|---|---|---|---|
| 1 | 条件组合是标配：Salesforce 验证规则 AND/OR/NOT、Amis `visibleOn` 表达式、JSON Logic | `when` 只能单条件；快赔 R-FAST-01"四条件全满足"写不出来，拆成四条规则语义从 AND 漂成 OR | `condition` 加 `all/any/not`（深度≤3，空 `all`=真/`any`=假）；样板加 `LINK-FAST-01` 四条件示范 | 不修则理赔快赔、双人复核（金额 AND 风险）等真实语义无法表达——通用性硬缺口 |
| 2 | 展示数据与表单字段分轨：Retool ` {{query.data}}` 引用与表单值命名空间隔离；NocoBase 聚合字段独立声明 | 11 处 STAT/CHART/TIMELINE 占用 `fieldCode`，与"字段只在 `meta_field` 定义"的单真源纪律自相矛盾（脚本实证） | 展示型控件必须 `"virtual": true`，经 `dataRef/lineage` 解析，禁与真实码碰撞；发布校验强制 | 不修则"第二数据模型"以展示之名复活——平台红线 |
| 3 | 上下文变量标准化：PowerApps `User()`、Salesforce `$User`、Amis `$user` | `:me`/`:myteam`/`*` 三种方言并存，无定义 | 统一 `$me/$myteam/$all`，写进 `tabs.value` 描述 | 不修则每接一个后端就要猜一次 magic string |
| 4 | 前向兼容解析：RJSF 忽略未知关键字、`$schema` 版本协商 | `schemaVersion const 1` 无 minor，渲染器无法区分 v1.0/v1.1 能力 | 加可选 `minor` + 规则"未知 minor 只告警不拒绝、未知字段忽略" | 不修则下一次增量又要升大版本、全量迁移 |
| 5 | 纯函数注册表具名：XRender/Amis 的 `formula` 函数白名单各有签名 | CRM `expected_amount` 复用 `payableAmount`（注释自证 hack） | 加 `computedFn.expectedRevenue`，语义金额×概率 | 不修则计算语义靠注释人肉保证 |
| 6 | 规则双跑对账：ServiceNow 同一条 policy 前后端同 ID；Drools 规则编码 | linkage/submit 只有 `name`（展示文案），前后端"文案对齐但以后端为准"是弱约束 | 加可选 `code`（`LINK-*/SUB-*`），日志审计对齐 | 不修则线上客诉无法定位是哪条规则拦的 |

### 11.2 暂缓（明确不修，附理由与触发条件）

| # | 业界做法 | 为什么本次不修 | 触发条件 |
|---|---|---|---|
| 7 | 全量 i18n 词条（Salesforce Custom Labels） | 内联双语会让样板膨胀 1 倍 | ✅ **已落收敛形态（§11.4）**：key 引用式，复用 `shared-utils/i18n` 基建 |
| 8 | 自定义组件沙箱（Amis 自定义 renderer、NocoBase 插件） | 沙箱评审与 CSP 成本高；当前 18 种 controlKind 已覆盖六场景（脚本验证无缺口） | 出现白名单表达不了的第三个真实控件时再开"受审自定义"口 |
| 9 | 模板跟随更新（TPA `pullRulesFromBase`） | 多租户下模板变更惊动面不可控；§5 快照断开继承是刻意选择 | ✅ **已落收敛形态（§11.4）**：租户确认式 pull，永不自动推送 |
| 10 | 离线/弱网（PowerApps offline、 pronounced 在理赔外勤） | 外勤 PDA 场景本期无诉求；离线冲突解决是独立大课题 | 外勤离线立项时单独立项，不塞进渲染协议 |

### 11.3 本次改动兼容性声明

- Schema 仍 `schemaVersion: 1`；新增全可选：v1.0 报销实例**零修改**仍合法（校验脚本已覆盖 7 文件解析 + 白名单覆盖）。
- 唯一的语义收紧是 `virtual:true` 的发布校验（新校验，老实例不受影响——老实例无展示型控件）。

### 11.4 收敛形态详设：key 引用式 i18n + 租户确认式跟随

**为什么是收敛形态（业界依据）：**

- i18n：Salesforce 用 Custom Labels（引用式）而非模板内联双语——内联会让每个样板膨胀约 1 倍且翻译更新要改全量实例；Amis/XRender 同理只做 `localeKey` 映射。Bone 更有现成抓手：`shared-utils/i18n`（i18next 单例、`zh-CN/en-US` 静态包、`fallbackLng zh-CN`、Shell 广播切换、应用不得自决语言）+ PRD 已定案"错误文案走 errorCode i18n"。引用式是顺水推舟，内联是另起炉灶。
- 跟随：Salesforce 受管包升级、NocoBase 模板版本化一律"运营方发版 → 租户确认升级"，从不自动覆盖租户自定义；TPA `pullRulesFromBase` 也是主体主动拉。自动推送在多租户下等于允许模板运营方写全租户生产数据——与 L4 禁止 AI 动生产同源的风险观。

**i18n 协议（本次落字）：**

- 顶层 `i18n.namespace`（如 `bone.ui.t_claim_case`）；文案字段配 `*Key` 即走 `t(namespace:key, {defaultValue: 内联中文})`：`section.titleKey`、`tabs[].titleKey`、`stats[].titleKey`、`control.labelKey`、`then[].noticeKey`、`submit.messageKey`、`confirm.titleKey/contentKey`、`footer.disclaimerKey`。
- 解析链：有 key → 查包；命中则用；miss 则用内联中文（**禁裸奔 key**——正好绕开 i18next miss 返裸 key 的已知坑）；语言切换跟 Shell 广播走，`UiSchema` 内不存语言状态。
- 语言包归属：`shared-utils/i18n/locales/{zh-CN,en-US}.json` 按 namespace 分段维护，随模板资产包版本走（翻译缺失是 WARNING，不阻断发布）。

**跟随协议（本次落字）：**

- 实例侧 `base{templateCode, baseVersion, followMode: pinned|notify}`：`pinned` 默认不提示；`notify` 有新版时在建模 Tab 出可点横幅（仍须租户点"检查更新 → diff 预览 → 一键合入"三步）。
- 合入策略（三路归并）：`base.old → base.new → instance.exclusive`；仅未被租户改过的 section/rule 自动合入；冲突项保留实例值 + WARNING 留痕（理赔拒赔理由、投研假设默认值这类租户已改的绝不静默覆盖）。
- 安全补丁通道：模板发版可标 `security`，横幅置红，但**仍须租户确认**（平台无权替租户改生产表单——与 L4 同源）。
- 前置条件（诚实标注）：模板资产须先完整（含 `uiSchema` 快照 + 语言包分段）——历史上有 `instantiate` 丢 `enumValues/validationRules` 的前科（PRD v0.32 C2），在资产完整性补齐前，跟随入口保持 disabled + tooltip 明示原因，不静默开放半吊子功能。

---

## 12. 租户×场景差异（2026-10-13：variants 变体协议）

> 问题：同一实体在不同租户/场景下是多副面孔——理赔员工端 vs 核赔工作台、投研免费 vs Pro、采购法务 vs 采购员、Web vs H5。v1.1 以前的模型是"一实体一 schema"，只能靠整份 fork 解决，fork 即漂移。
> 业界解：Salesforce 记录页面按"应用+记录类型+Profile"多激活（assignment）、Power Apps 解决方案分层、Shopify 子主题选择性覆盖——共同点是**命名变体 + 匹配规则 + 优先级裁决**，而不是运行时深度归并。本方案照抄该语义。

### 12.1 分层模型（四层，每层职责唯一）

```
平台模板 baseUiSchema（含 variants 全集）
  → 租户实例 exclusive 快照（instantiate 拷贝 + base 指针，§11.4）
    → 场景变体 variants[]（同一 schema 行内，match 命中切换）
      → 个人偏好 UiOverlay（显隐/列序，UiOverlayApplicationService，既有）
```

- 跨租户差异 → 靠"实例行"（`tenant_id` 隔离，天然互不可见）。
- 同租户跨场景差异 → 靠 `variants[]`（不 fork、不新表）。
- 个人差异 → 靠既有 overlay。品牌主题（logo/主色）归 Shell `themeMode`，不进本协议（已有归属，不重复造）。

### 12.2 变体协议（已落 Schema）

`variants[]` 项 = `{key, label(+labelKey), priority(越小越优先，缺省 100), match{roles[], tiers[], channels[], devices[]}, views{list/form/detail}, linkageRules?, submitRules?, actionBar?}`。
- 维度缺省 = 通配；多维度同时满足才命中；首个胜出（同 priority 按数组序）；无命中 → base 行为（全量兼容）。
- 命中后**按块整块替换** base 对应块（views 内再按 list/form/detail 逐视图替换），不做深度归并——可预测优先，编辑器可逐块 diff。
- `roles` 来自 JWT 应用角色（`JwtRoleConverter` 口径）；`tiers` 来自订阅档上下文（缺失时变体不命中，回落 base，禁猜）；`channels: web/h5/miniapp`（投研载体决策即此维度）；`devices: desktop/mobile`（44px 触控/底部导航即此维度）。
- 发布校验加两条：`UI-REF-08` 变体重叠匹配 WARNING（两变体 match 有交集且 priority 相同）；`UI-REF-09` 变体引用的 fieldCode/actionCode 同样走 UI-REF-01~03。

### 12.3 样板示范

- 理赔 `reviewer` 变体（`match.roles=[reviewer,admin]`）：理算与风险前置 dashboard → 决策轨迹 timeline → 明细只读；动作条收敛到 approve/reject/requestMoreEvidence（HITL + 双人条件保留）。员工端 base 不变。
- 投研 `pro` 变体（`match.tiers=[pro,team]`）：假设面板展开 DCF 实验区（STAT 只读 + 强警告，数值走 `lineage: dcf-engine-manual`，禁前端自算）+ 跟踪提醒全开。免费 base 保留样例口径。

### 12.4 技术方案挂接（《前后端技术方案》同步点）

- 渲染：`useBoneUiSchema` 先 `resolveVariant(ctx)` 再渲染；ctx 组装（roles/tier/channel/device）集中一处，缺 tier 时打 WARNING 回落 base。
- 编辑器：`UiSchemaTab` 加变体切换器（base/各 variant 页签）+ 重叠匹配提示（UI-REF-08）；变体块支持"从 base 复制"一键起草（拷贝语义，与模板快照同源）。
- 缓存/审计：审计日志记 `variantKey`（否则客诉无法复现"他看到的那副面孔"）；运行时缓存键不变（裁决在前端，缓存的是 schema 全集）。
- 明确不做：运行时深度归并 delta（读时合并的缓存失效与排错成本 > fork 成本，fork 由三路归并兜底）；变体级独立发布（变体随实体一次发布，禁"只发某变体"造成线上多版本）。

---

## 13. v1.2 增量

### 13.1 `enabled`：规则级启停（linkageRule / submitRule）

| 项 | 内容 |
|---|---|
| 形态 | `"enabled": false`，缺省 = true |
| 语义 | false 时前端渲染与后端强制都整体跳过该条 |
| 约束 | 关闭必须显式落 `false`；`undefined` 等价启用（否则 UI 显示关、引擎仍在跑） |
| 配套 | `disabledReason`：停用原因，建议填写以保留审计 |

### 13.2 `pageHeader`：表单页头关键信息条

| 项 | 内容 |
|---|---|
| 形态 | `views.form.pageHeader` / `views.detail.pageHeader`：`string[]`（字段 code 列表） |
| 语义 | 独立于 `sections` 渲染在视图顶部（如案件号/申请人/状态），与分区互斥 |
| 约束 | 引用的字段必须同时存在于某个 `section.controls`，否则悬空引用（校验 ERROR）；form/detail 各自独立配置 |

### 13.3 `dictionaries`：码表覆写结构（落实 §3.1）

```json
"dictionaries": {
  "invoice_type": {
    "labelOverride": { "NORMAL": "增值税普通发票" },
    "hidden": ["NORMAL"]
  }
}
```

- 只允许在既有 value 集合内裁剪（`hidden`）与改显示名（`labelOverride`）。
- 不允许新增/删除 value、不允许新建码表（真源在 `meta_field.enumValues`）。
- 校验：覆写不存在的 value → ERROR。

### 13.4 明确不纳入

| 提案 | 决定 | 理由 |
|---|---|---|
| `props.optionsRef` | 不纳入 | 实测恒等于控件自身 `fieldCode`，冗余；改按 `fieldCode` 隐式绑定（§3.1），仅留作历史 JSON 兼容读取路径 |
| 按主体/租户的专属码表覆盖 | 不做 | 跨租户靠 `tenant_id` 行隔离，同租户跨场景靠 `variants[]`（§12） |
| 演示扩展 `x_cols` | 不入协议 | 与 `layout: split-left-40/60` 重叠，仅 DEMO 调布局 |

### 13.5 DEMO ⇄ Schema 机器校验

```bash
node doc/design/lowcode/ui/ui-protocol/validate-demo-samples.js
```

- 将 DEMO 三套样板导出为 JSON，用 `bone-ui-schema.schema.json` 校验（根/对象均 `additionalProperties:false`）。
- 退出码：`0` = 无意外漂移；`1` = 出现协议未定义且不在豁免清单内的字段。
- 豁免项与 §13.4 对应（`x_*` 扩展、`props.optionsRef`）；改 §13.4 须同步改脚本 `isExempt`。
- v1.9 起，DEMO 已内嵌本 schema.json（双击即开的真源机校），发布预检=真源机校 + 语义自检 + 变体组合冒烟三道门（与后端 publishEntity 同口径）。

### 13.6 v1.2 待评审登记（本轮诊断新增，评审后定稿才能落 Schema）

| 提案 | 形态 | 动机（真实场景） | 业界对标 |
|---|---|---|---|
| `actionBar[].processCode` | 引用平台流程服务真源（如同 policyCode 引用 row_policies），路由/会签在流程服务内定义 | 报销分级审批（≤1k 主管 / 1k–5k 部门 / >5k 财务+总监会签）是企业采购硬门槛；自建 BPMN 违反「不自建流程引擎」封顶，引用外部流程服务与「引用不复制」同构 | Concur 分级审批；Dynamics 流程引用 |
| `views.list.defaultSort/sortable/pageSize` | 纯展示声明（服务端可裁剪） | 「按申报时间倒序」类诉求在协议层无处声明，运行时写死或各团队自私 | Salesforce List View 默认排序 |
| `ATTACHMENT.uploadApi` 正式化 | 只允许引用平台注册的上传通道 code（STS 临时凭证直传），禁自带 endpoint | 「真上传另立项」无约束力会各造私有上传协议；现在锁死口径，省未来 N 个实现 | Concur/ServiceNow 附件标准接口 |
| `CHILD_TABLE` 行级联动作用域 | `when.fieldCode` 指向子表列时默认行内求值；`scope:"table"` 切聚合（任一/合计） | 行内标红 vs 合计禁提提交是两种不同语义，一字歧义会造成三种实现分歧 | Power Apps 行级/表级规则 |
| 受控表达式 DSL（只读计算字段） | 字段引用 + 四则 + round 白名单；前端求值 + 后端 RuntimeWriteValidator 同构复算，禁函数调用/属性访问 | 单价×数量、税额=金额×税率是真实表单刚需；不破「禁公式引擎」封顶的受控子集 | Salesforce 公式字段安全子集 |
| form/detail 版本提示条 | 渲染器遇未知更高 minor：只告警不拒绝（schema.json 现有语义），同时在预览顶部显示提示条（listView 已有 degradedBanner 无关，勿复用） | 把「前向兼容告警」从隐性约定变成用户可见行为 | JSON Schema 前向兼容惯例 |

补充登记：**发布门禁升格**与 **412 冲突响应结构化**归技术方案承载（§2.5/§2.6），协议不改。

## 14. 安全不变量（成文纪律，非待评审）

本协议路线是「schema 即数据」：能改 schema 的人=能改全部表单行为。以下不变量必须由服务端 validate + 产品渲染器共同强制；不满足任意一条，即视为实现回退。

| # | 不变量 | 强制点 |
|---|---|---|
| S1 | 条件/比较的 `value` 一律字面量或 field 引用，禁止表达式求值、函数调用、属性访问 | 引擎实现（现快赔规则引擎同级）+ validate |
| S2 | `then[].effect` 仅限属性操作白名单（显隐/必填/只读/赋值/禁用），禁止任意 JS 槽位 | schema.json `additionalProperties:false` + 后端白名单断言 |
| S3 | 文本槽位（labelOverride/placeholder/分区 title/footer 文案）禁 HTML 标签，渲染器一律纯文本，禁 `dangerouslySetInnerHTML` | 渲染器实现 + DEMO 自检 WARN |
| S4 | `REFERENCE.entityCode` / 列表数据全部走后端已注册实体白名单，禁任意 URL / 任意接口槽位 | validate 引用完整性（UI-REF 口径） |
| S5 | 未知更高 `minor`：只告警不拒绝、前向忽略未知字段，但渲染器必须在预览顶部提示用户（fail-open with notice，安全底线始终在后端 RuntimeWriteValidator/行策略） | 渲染器 + DEMO 已实现 |
| S6 | 金额类求和/比较：前端仅做体验层展示（浮点+容差），结算/拦截口径一律后端 BigDecimal | 引擎分工纪律 |
| S7 | 码表/字段/动作/行策略真源永不在 UI 层新建（§3.1），UI 只引用 code | 协议既有纪律，不变量重申 |

> 本节能文从「DEMO 碰巧安全」升级为「约定安全」：未来每新增控件类型/规则效果，必须对照本表过一遍。
