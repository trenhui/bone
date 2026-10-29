# BONE 对外 UAT / 客户演示 · 测试数据治理与端到端生成方案

> 状态：修订版 v2（待评审） ｜ 范围：单示范租户（复用基线 DEMO 租户 1001）+ 全量模块 + **中等规模** + 策展 SQL 种子/加载器 + **黄金动线串联**
> 作者：AI 协作 ｜ 修订日期：2026-10-28
> 目标：把"杂乱的本地测试数据"替换为**对标生产、模块串联、拟真可读**的演示数据，用于对外 UAT 环境与客户演示。

---

## 0. 设计原则（贯穿全流程的红线）

1. **拟真可读，拒绝随机乱码**：所有显示名、编码、文本来自"受控词典 + 真实语义"；客户/供应商/物料/员工均有可读中文名与合规编码，无 UUID/随机串当显示名。
2. **复用基线示范租户**：全库演示数据统一挂在 **`tenant_id=1001`（基线 `DEMO` 租户）** 下；平台内置数据 `tenant_id=0` 保留不动；新增 ~200 个拟真账号，**保留**基线 `tenant_admin`(id=2) 为租户总管。
3. **参照完整（RI 零孤儿）**：每个外键都指向已存在的记录；`created_by/updated_by` 必须指向真实存在的账号——这也是数据血缘（lineage）最佳实践。
4. **时间轴自洽（数据老化）**：`created_at` 落在近 180 天窗口且分布合理；工作流 `submitted_at < approved_at < instance 完成`；订单 `paid < ship < deliver`；所有时间戳由"基准日 − 随机偏移"确定性生成。
5. **职责分离 SoD（对标 SOX/ISO 27001）**：主数据 `submitted_by ≠ approved_by`；结算大额双人复核不同人；流程审批人 ≠ 发起人；权限遵循基线已定义的"租户管理员不绑平台域写码"边界。
6. **状态分布真实（状态机生命周期）**：不全是终态，保留 `DRAFT/PENDING_REVIEW/APPROVED/PUBLISHED/RETIRED` 与订单 `PAID/SHIPPED/DELIVERED/CANCELLED/REFUNDED` 的真实比例，演示全生命周期。
7. **金额守恒（复式/三口径不变量）**：`t_order.total_amount = Σ明细小计 + freight − discount`；结算金额 = 核定金额；额度 `initial = claimed + frozen + available`；支付额 = 订单应收。
8. **字典覆盖**：所有 `status/枚举` 列取值必须在 `sys_dict` 或代码枚举中有对应，杜绝"幽灵状态"。
9. **确定性可复现**：ID 由"固定 ID 段 + 种子"生成，同环境同输入产出完全一致，便于评审、回归与跨环境重建。

### 0.1 业界最佳实践对照（本方案采用的 11 条）

| # | 实践 | 落地方式 |
|---|---|---|
| A | **DDL 与数据分离**（DBT seed / Flyway 思路） | 建表只靠 `bone-init.sql` + `scripts/migration/*`；seed 仅含业务数据，零 DDL |
| B | **幂等确定性种子** | `load.sh` 先 `DELETE tenant=1001` 再插入；固定 ID 段 + 固定随机种子 ⇒ 重跑结果一致 |
| C | **黄金数据集 + 规模化合成**（BI/MDM 演示范式） | `02_golden.sql` 手工策展 1 条贯穿全模块的动线；其余用 `gen.py` 按中等规模放大 |
| D | **拓扑序加载 + FK 校验门禁** | 加载顺序 = 依赖拓扑；执行后跑 7 类校验，孤儿/不守恒直接 fail |
| E | **SoD / 职责分离** | 审批人≠发起人、双人复核、平台域写码不授租户管（复用基线边界） |
| F | **数据血缘与老化** | `created_by/updated_by` 全填充；时间戳由基准日倒推，呈现真实"数据年龄" |
| G | **财务不变量** | 订单三口径、结算=核定、额度三值、支付=应收，全部断言 |
| H | **多渠道多币种真实建模** | 6 渠道 + CNY 主币 + 渠道账期；渠道买家↔内部客户映射 |
| I | **PII 最小化 / 个保法合规** | 理赔域 `insured_id_hash` 假名化、无明文 PII；证件号脱敏存储 |
| J | **环境护栏** | `load.sh` 校验 `BONE_ENV=uat`；清理只动 `tenant_id=1001`，不碰 `0` 与 `builtin=1` |
| K | **可回放 / 版本化** | 种子带 `seed_version` 注释；`mysqldump` 导出 UAT 快照可随时重建 |

---

## 1. 演示主体与故事线（一条能讲通的企业叙事）

**示范企业：`鼎晖智造集团股份有限公司`（复用基线租户 `id=1001, code=DEMO`，UAT 下将 tenant 显示名更新为该企业名）**
定位：一家"智能制造 + 全渠道零售"的集团企业，同时把 BONE 当作企业数字化底座，并内置一个垂直行业演示应用（保险 TPA 理赔）以展示"平台可托管业务应用"。

叙事主线（客户演示动线）：
1. **组织与人**（IAM）：集团 → 10 个事业部/中心 → 约 50 个科室/团队 → 约 **200 名**员工，覆盖 CEO、部门负责人、主数据专员（数据管家）、审批人、财务、业务员、审计、低代码配置员、运维、只读访客。
2. **建模与治理**（Metadata + MDM）：在建模模块定义"客户/供应商/物料/商品/项目/员工/资产"等业务对象 → 在主数据模块沉淀**已治理**的客户/供应商/物料/商品等主数据（带审批流、版本、数据管家、参考数据、质量工单）。
3. **业务流程**（Workflow）：主数据新增/变更审批、费用报销审批、采购申请审批、请假审批，实例化**进行中 + 已完成**流程，待办任务指派给真实审批人。
4. **真实业务交易**（Commerce，6 渠道）：淘宝/京东/抖音/拼多多/微信小程序/官网商城 → 商品上架 → 客户下单（`t_order` 三口径）→ 发货/物流轨迹 → 支付/对账；库存与补货。
5. **平台能力**（Integration / Generator / AI / Extension / Console）：ERP/企微/钉钉/快递100 连接器与同步流程；由元数据生成代码；产品手册 RAG 知识库；渠道定价扩展点；高管仪表盘与待办消息。
6. **垂直演示应用**（Claim/Settle）：保险 TPA 门诊理赔全流程（报案→核赔→理算→结算→额度→申诉→AI 辅助裁决），作为"构建在 BONE 上的行业应用"演示。

> 说明：第 6 部分按 v1 结论定为**"平台内置垂直演示应用"**（不混入制造/零售叙事），通过 `bone_application` 注册为独立应用，演示"平台可托管业务应用"。

---

## 2. 模块数据设计总览（规模 = **中等**）

| 域 | 关键表 | 演示内容与拟真要点 | 目标行数（中等） |
|---|---|---|---|
| **IAM** | `iam_tenant, iam_dept, iam_account, iam_role, iam_permission, iam_role_permission, iam_account_role, iam_menu` | 复用租户 1001（改名鼎晖智造）；自引用组织树（集团+10 部门+~50 科室，~62 节点）；**~200 真实中文名员工**；~12 角色（租户总管/组织管理员/主数据管家/主数据审批/流程发起/流程审批/财务/业务员/审计/低代码配置/运维/只读）；权限绑定**复用基线 121 个权限码**，仅新建角色↔权限关系；菜单树 | 租户1/部门~62/账号~200/角色~12 |
| **系统字典** | `sys_dict_type, sys_dict_item, sys_dict_hierarchy, sys_dict_item_text, sys_config, sys_alert_rule, sys_schedule_task` | 真实值域：性别、行政区划（GB/T 2260 省→市→区 级联，覆盖 ~15 省）、客户/供应商等级、物料分类、计量单位、币种(ISO 4217)、渠道类型、订单状态映射、审批结论；平台配置项；3 条告警规则；2 个定时任务 | 字典类型~14/字典项~300 |
| **元数据建模** | `meta_entity, meta_field, meta_entity_relation, meta_model_template, meta_model_template_field, meta_runtime_sequence, meta_ui_overlay, meta_code_template` | 定义 客户/供应商/物料/商品/项目/员工/资产/渠道买家 8 个业务对象及其字段（含类型/约束/UI）；2~3 个模型模板；运行时序列；UI 覆盖配置 | 实体~8/字段~70 |
| **主数据 MDM** | `mdm_domain_template, mdm_entity, mdm_record, mdm_category, mdm_steward, mdm_steward_scope, mdm_reference_set, mdm_reference_value, mdm_quality_issue, mdm_qcheck_*, mdm_feedback` | 8 标准域；客户~500/供应商~200/物料~800/商品~500/项目~40/员工~200/资产~100；`mdm_record.current_data` 合规 JSON；状态按 ~70% PUBLISHED + 其余 DRAFT/PENDING/APPROVED/RETIRED；~12 数据管家与血缘；参考数据 ~10 集 ~200 值；质量工单 ~40 | 实体~8/记录~**2000**/管家~12 |
| **工作流** | `wf_process_def, wf_process_draft, wf_instance, wf_active_instance, wf_task, wf_binding, wf_notification` (迁移 0056/0065/0066) | 4 条流程定义：主数据审批、费用报销、采购申请、请假；流程与 mdm 实体/表单绑定；实例化 ~150 条（含进行中待办任务，指派给真实审批人） | 定义4/实例~150/任务~400 |
| **集成** | `int_connector, int_flow, int_flow_node, int_flow_connection, int_template, int_execution_log` | 连接器：用友ERP、企业微信、钉钉、快递100、抖音开放平台、微信支付；~10 条流程（订单同步ERP、主数据变更通知、物流回写、支付对账）；节点与执行日志 | 连接器~6/流程~10/日志~800 |
| **代码生成** | `gen_data_source, gen_table_metadata, gen_column_metadata, gen_code_template, gen_type_mapping, gen_generation_task, gen_code_generation_history` | 数据源 1；对 `mdm_record/t_order/iam_account/bp_channel_product` 做表元数据反向工程；5 个 Freemarker 模板；生成历史体现"建模→出码" | 模板~5/表元数据~8 |
| **供应链 Commerce** | `bp_channel, bp_channel_product, bp_channel_buyer, bp_inventory, bp_shipment, bp_shipment_trace, bp_payment, bp_replenishment_order` | **6 渠道**全启用（淘宝/京东/抖音/拼多多/微信小程序/官网商城）；商品×渠道上架带价（~2500 行）；库存 ~2500；按订单生成发货/物流轨迹(~6000)/支付(~1800)；补货单 ~60 | 渠道6/渠道商品~2500/库存~2500/发货~1500 |
| **订单** | `t_order, t_order_item` | ~2000 订单，关联真实客户+渠道，`order_no` 业务号，金额三口径守恒，状态混合（~75% 终态 + 进行中 + 取消/退款）；明细 ~6500 | 订单~2000/明细~6500 |
| **AI** | `ai_rag_chunk, ai_trace_snapshot` | 产品手册/保修政策/API 文档分片（真实中文文本，~250 切片）；少量 trace 快照 | 切片~250 |
| **扩展点** | `exts_extension_point, exts_extension_impl, exts_plugin_version` | 渠道定价扩展点 + 抖音特价/会员价实现；主数据校验扩展点；插件版本 | 扩展点~5/实现~8 |
| **控制台/消息** | `cnsl_dashboard_widget, cnsl_notification, ntf_message` | 高管仪表盘（销售趋势/订单状态/库存预警/审批待办）挂 CEO 与部门负责人；站内信与待办 | 看板~10/消息~300 |
| **应用注册** | `bone_module, bone_application, bone_app_permission` | 注册各模块与应用目录 + 权限点（平台应用墙），含 Claim/Settle 垂直应用 | 模块~10/应用~14 |
| **垂直应用 Claim/Settle** | `t_claim_case, t_claim_evidence, t_claim_item, settle_order, settle_funding_event, t_quota_account, t_quota_transaction, t_adj_*, appeal_record, hitl_item, eval_case, rule_asset, claim_decision_*` | 保险 TPA 门诊理赔：~150 案件（假名化 `insured_id_hash`，无明文 PII），状态全生命周期；结算/资金流水/额度三值模型/申诉~25/AI 裁决~35/评测~30/规则资产~40 | 案件~150/结算~150/额度~150 |

---

## 3. 跨模块串联关系（数据血缘图）

```
iam_tenant(1001 「鼎晖智造集团」)
  └─ iam_dept(自引用树 ~62 节点) ── iam_account(dept_id, ~200 人, role=account_role)
        │ created_by/updated_by 贯穿所有业务表（数据血缘）
        ▼
meta_entity/field(8 对象) ──(建模)──► mdm_entity(domain_code)
        │                         └─ mdm_record(tenant,entity,current_data JSON, status, submitted/approved_by)
        │                               └─ wf_binding(mdm_entity) ─► wf_process_def ─► wf_instance/wf_task(assignee=account)
        ▼
bp_channel(6 渠道) ── bp_channel_product(商品=mdm_record 商品域) ── bp_inventory
        │
        ▼
t_order(customer_id=mdm_record客户, channel_code=bp_channel) ── t_order_item(商品)
        ├─ bp_shipment + bp_shipment_trace
        ├─ bp_payment
        └─ int_flow("订单同步ERP") ── int_connector(用友ERP) ── int_execution_log
        ▼
gen_table_metadata(t_order) ◄── meta_entity(建模产物反向出码) ── gen_code_generation_history
ai_rag_chunk(产品手册, doc 归属=account)   exts_extension_impl(抖音定价)   cnsl_dashboard_widget(CEO)
ntf_message(待审批通知→account)            bone_application(应用墙, 含 Claim/Settle)
        ▼
垂直应用: t_claim_case ─ settle_order ─ settle_funding_event ─ t_quota_account/transaction ─ appeal_record ─ hitl_item ─ claim_decision_trace
```

**串联要点**：客户/商品先在主数据"被治理发布"，才出现在订单与渠道上架；审批流与真实账号绑定（发起人≠审批人）；集成流消费订单；建模产物被代码生成反向引用——形成"**数据 → 治理 → 业务 → 集成 → 复用**"的闭环。

### 3.5 黄金动线（一条数据贯穿全模块 · 手工策展，确保串联可演示）

> 最佳实践 C：**先用一条手工策展的子集把"全模块串联"跑通并锁定，再让 `gen.py` 围绕它放大体量**。这条动线用固定 ID，是客户演示时"点开任何一个模块都能看到同一家客户/同一笔订单"的锚点。

| 环节 | 表 | 固定 ID / 业务号 | 串联关系 |
|---|---|---|---|
| 客户主数据 | `mdm_record` | `300001` `CUST-2026-000001`「上海瀚海科技有限公司」(PUBLISHED) | steward=李静(120002)、approved_by=王芳(120003) |
| 商品主数据 | `mdm_record` | `300500` `PROD-2026-000001`「鼎晖智能扫地机器人 X1」(PUBLISHED) | 同管家/审批链 |
| 建模对象 | `meta_entity/field` | `200001`「客户」、字段 统一社会信用代码/联系人/电话/地区/等级/授信 | 由 `gen_table_metadata` 反向出码 |
| 渠道上架 | `bp_channel_product` | `500001` 淘宝渠道上架该商品，价 ¥2,999 | channel=TAOBAO，product=300500 |
| 库存 | `bp_inventory` | `500500` 该商品淘宝渠道库存 120 | 与发货联动 |
| 订单 | `t_order` | `400123` `SO-2026-000123` 总额 ¥2,999（三口径守恒） | customer=300001，channel=TAOBAO |
| 订单明细 | `t_order_item` | `400123-1` 扫地机器人×1、`400123-2` 配件×2 | product=300500 |
| 发货+轨迹 | `bp_shipment` | `500900` `SH-2026-000456` 4 段轨迹（揽收→运输→派送→签收） | order=400123 |
| 支付 | `bp_payment` | `501000` `PA-2026-000789` 已支付 ¥2,999 | order=400123，对账成功 |
| 集成消费 | `int_flow` + `int_execution_log` | `700001`「淘宝订单同步ERP」执行成功 | 消费 order=400123，connector=用友ERP |
| 审批流 | `wf_instance` + `wf_task` | `600012` 主数据审批（发起 李静→审批 王芳） | 绑定 mdm_entity，task 指派 120003 |
| AI 知识 | `ai_rag_chunk` | `900001` 产品手册片段，归属商品 300500 | doc_owner=账户 |
| 扩展点 | `exts_extension_impl` | `910001` 抖音特价实现，影响定价 | point=渠道定价 |
| 控制台 | `cnsl_dashboard_widget` | `920001` CEO 销售趋势看板 | owner=张伟(CEO) |
| 待办消息 | `ntf_message` | `920100` 待审批通知→王芳 | 关联 wf_task |
| 垂直应用 | `t_claim_case` | `940001` 该集团员工门诊理赔 | linked customer 300001，settle/quota 同链 |
| 额度 | `t_quota_account` | `940500` 该案件额度三值 | transaction 同链 |

这条动线在 `02_golden.sql` 中**全手工、固定 ID** 写入，并通过 §8 的"黄金动线断言"门禁强制校验每个环节 ID 真实存在且外键闭合。

---

## 4. 拟真数据规范（示例，证明"非随机"）

**组织树（节选，ID 段 11001+）**
```sql
INSERT INTO iam_dept(id,tenant_id,name,parent_id,order_no,status) VALUES
(11001,1001,'鼎晖智造集团',NULL,1,1),
(11002,1001,'智能制造事业部',11001,1,1),
(11003,1001,'新零售事业部',11001,2,1),
(11004,1001,'供应链中心',11001,3,1),
(11005,1001,'财务共享中心',11001,4,1),
(11050,1001,'集团总部',11001,10,1);
```

**账号（节选，真实姓名 + 合规编码，ID 段 120001+）**
```sql
INSERT INTO iam_account(id,tenant_id,username,real_name,dept_id,status,is_admin) VALUES
(120001,1001,'ceo.zhang','张伟',11001,1,0),     -- CEO
(120002,1001,'mdm.li','李静',11006,1,0),         -- 主数据管家
(120003,1001,'approval.wang','王芳',11005,1,0);  -- 审批人
```

**主数据记录（客户，JSON 合规、可读、PII 合规）**
```sql
INSERT INTO mdm_record(id,tenant_id,mdm_entity_id,record_code,display_name,current_data,status,submitted_by,approved_by) VALUES
(300001,1001,200001,'CUST-2026-000001','上海瀚海科技有限公司',
 '{"unifiedSocialCreditCode":"91310000MA1FL2AB3X","contact":"陈晨","phone":"13800001111","regionCode":"310115","customerLevel":"A","creditLimit":500000.00,"industry":"智能制造"}',
 'PUBLISHED',120002,120003);
```

**订单（三口径守恒）**
```sql
INSERT INTO t_order(id,tenant_id,customer_id,order_no,total_amount,freight_amount,discount_amount,status,channel_code) VALUES
(400123,1001,300001,'SO-2026-000123',2999.00,0.00,0.00,'DELIVERED','TAOBAO');
-- 校验: 2999.00 = Σ明细小计(扫地机器人2999 + 配件0) + 运费(0) - 优惠(0)
```

**字典（受控、级联）**
```sql
INSERT INTO sys_dict_type(id,tenant_id,code,name,category,code_segments) VALUES
(200001,1001,'region','行政区划','CASCADE','2,2,2');
INSERT INTO sys_dict_item(id,tenant_id,type_code,code,label,external_code) VALUES
(200002,1001,'region','310000','上海市','CN-31'),
(200003,1001,'region','310100','市辖区',''),
(200004,1001,'region','310115','浦东新区','');
```

---

## 5. 生成与落地机制

**目录结构**
```
scripts/seed/uat/
├── 00_ids.sql              # 固定 ID 段定义（租户/部门/账号/实体/记录…），所有脚本引用（ID 段见 §5.1）
├── 01_iam.sql              # tenant 改名 + dept/account(~200)/role(~12)/permission绑定/menu
├── 02_golden.sql           # ★ 黄金动线：手工策展 1 条贯穿全模块的数据（固定 ID，见 §3.5）
├── 03_sys_dict.sql         # 系统字典与配置（~300 字典项）
├── 04_metadata.sql         # meta_entity/field/template（8 对象）
├── 05_mdm.sql              # mdm_entity/record(~2000)/steward/reference/quality
├── 06_workflow.sql         # wf 定义/绑定/实例(~150)/任务(~400)
├── 07_integration.sql      # connector/flow(~10)/execution_log
├── 08_generator.sql        # gen_*
├── 09_commerce.sql         # bp_channel(6)/inventory/channel_product
├── 10_orders.sql           # t_order(~2000)/items（Generated）
├── 11_ai_extension_console.sql
├── 12_claim_settle.sql     # 垂直应用（~150 案件）
├── 90_clean.sql            # 按 tenant 选择性清理（保留 builtin=1 / tenant 0）
├── gen.py                  # 大批量业务数据确定性生成器（中文词典+种子→*.generated.sql）
├── load.sh                 # 主控：环境守卫 + 按拓扑序 source + 跑校验门禁
└── README.md               # 灌库与清理指引
```

**机制要点**
- **确定性 ID 段（§5.1）**：`00_ids.sql` 集中定义，避免与基线（账号 1/2、角色 1/2/3、权限 1–121）撞车：
  - dept `11xxx`、account `12xxxx`、role `1xx`、role_perm `1000+`、menu `1000+`
  - dict `20xxxx`、meta_entity `200001+`、mdm_entity `200001+`、mdm_record `300001–399999`
  - t_order `400001+`、bp_* `500001+`、wf_* `600001+`、int_* `700001+`、gen_* `800001+`、ai_* `900001+`、exts_* `910001+`、cnsl/ntf `920001+`、bone_* `930001+`、claim/settle `940001+`
- **大批量用生成器**：订单/明细/物料等用 `gen.py`（受控中文词典 + 固定随机种子，规模参数 `--scale medium`，可选 `large`）生成 `10_orders.generated.sql` 等，产出可入库、可评审、可重复。
- **加载顺序即依赖拓扑**：`load.sh` 按编号 source，先基础层(IAM/字典) → 建模治理 → 流程 → 业务交易 → 平台层 → 垂直应用；**`02_golden.sql` 紧随 IAM 之后先落锚点，保证后续生成数据能正确引用同一批客户/商品 ID**。
- **与基线关系**：UAT 先跑 `bone-init.sql` + `scripts/migration/*` 建最新表结构；seed 仅含业务数据，不含 DDL。
- **环境守卫**：`load.sh` 开头校验 `BONE_ENV=uat`，禁止对 `prod` 执行；清理脚本只按 `tenant_id=1001` 操作，不碰 `tenant_id=0` 与 `builtin=1`。

---

## 6. 清理策略（UAT 外部演示环境）

- **推荐：全量重建**。独立 UAT 实例 `DROP DATABASE bone; CREATE DATABASE bone;` → 跑 `bone-init.sql` + 全部 `scripts/migration` → 再跑 seed。保证 schema 与基线一致、数据从零干净。
- **备选：选择性清理**。若需在已有库清理，`90_clean.sql` 按 `tenant_id=1001` `DELETE` 业务表数据，**保留** `builtin=1` 的平台字典/权限基线；不动 `tenant_id=0`。
- **红线**：严禁在生产库执行任何清理/seed；演示账号口令统一策略（分角色 BCrypt 哈希预置，首次登录强制改密）。

---

## 7. 执行步骤（分阶段，每阶段带校验闸门）

- **P0 环境与基线**：确认 UAT DB 可达（本机当前无 `mysql` 客户端，loader 需在能连 UAT 的环境运行，或经 gateway 服务 API 灌入）；跑 `bone-init` + migration 建表。
- **P1 基础层**：tenant(改名) → sys_dict → iam(dept/account/role/permission绑定/menu) → bone_module/app。
- **P2 黄金锚点**：先跑 `02_golden.sql`，落定 1 客户/1 商品/1 订单/1 审批/1 集成 等贯穿 ID，确保串联闭合（**先于规模化生成**）。
- **P3 建模与治理**：meta_entity/field → mdm_entity → mdm_record(含 JSON，围绕 golden 客户/商品展开到 ~2000) → steward/reference/quality。
- **P4 流程**：wf_process_def/binding → 实例化 ~150 流程与 task（指派真实审批人）。
- **P5 业务交易**：bp_channel(6) → product/inventory(~2500) → customer/supplier(MDM) → t_order/items(~2000) → shipment/payment/replenishment。
- **P6 平台能力**：integration → generator → ai → extension → console → notification。
- **P7 垂直应用**：claim/settle/quota/appeal/hitl/eval/rule(~150 案件)。
- **P8 校验门禁**（见 §8）。
- **P9 交付**：`mysqldump` 导出 UAT 快照；编写演示动线 README；给出客户演示路径清单。

---

## 8. 校验与质量门禁（执行后必跑）

1. **参照完整性**：逐表校验外键孤儿（`tenant_id=1001` 下，如 `t_order.customer_id` 必须 ∈ `mdm_record.id`）。
2. **金额守恒**：`t_order` 三口径等式成立；`settle_order.amount = t_claim_case.approved_amount`；额度 `initial = claimed + frozen + available`；`bp_payment.amount = t_order.receivable`。
3. **SoD**：`mdm_record`（非草稿）`submitted_by ≠ approved_by` 必须为 0；结算大额复核换人；审批人 ≠ 发起人。
4. **状态覆盖**：所有 `status` 取值 ∈ 已知枚举/字典集合（零幽灵状态）。
5. **计数断言**：account≥200、customer≥500、supplier≥200、material≥800、product≥500、order≥2000、claim≥150 等下限。
6. **时间轴**：`created_at` 在合理窗口；工作流/订单状态机时间单调。
7. **★ 黄金动线断言**：§3.5 表格中每个固定 ID（300001/300500/400123/500900/501000/600012/700001/900001/910001/920001/940001…）必须真实存在且外键闭合——这是"串联可演示"的硬门槛。
8. **冒烟 E2E**：以 demo 管理员登录拿 token → 调 `/iam/org/tree`、`/mdm/entity/list`、`/commerce/order/page`、`/workflow/instance/page` 均 200 且非空；走一遍"主数据发布→订单生成→审批待办"动线。

---

## 9. 风险与待确认项

- **环境连通**：本机无 `mysql` 客户端，需确认 UAT 执行环境（直连 or 经服务 API）；现有运行库"乱数据"执行阶段先做只读盘点再定清理范围。
- **工作流 schema**：`wf_*` 表在 `scripts/migration/0056|0065|0066`，seed 须对齐最新 migration 版本（非 `bone-init.sql`）。
- **ID 段隔离**：已基于基线现状（账号 1/2、角色 1/2/3、权限 1–121）规划独立 ID 段，避免撞主键。
- **基线租户复用**：演示数据挂在 `tenant_id=1001`（基线 DEMO 租户），UAT 下将其显示名更新为「鼎晖智造集团股份有限公司」，保留 `tenant_admin`(id=2) 为租户总管；权限**复用**基线 121 码，仅新建角色绑定。
- **PII 合规**：理赔域全量假名化（`insured_id_hash`、证件脱敏），符合个保法/行业演示合规。
- **体量可调**：`gen.py --scale` 参数支持 `medium`（默认）/ `large`，UAT 默认中等，后续可一键放大。

---

## 10. 交付物清单

- `scripts/seed/uat/*`（`load.sh` 编排 + `90_clean.py` 清理器 + `gen.py` 生成器 + `95_validate.py` 门禁 + `build/*.sql` + `README.md`）
- 本方案 `doc/uat-demo-data-plan.md`
- UAT 数据快照 `outputs/uat/bone_uat_seed_20261028.sql`（14.7 MB，44,988 行含平台基线）
- 客户演示动线清单（见 `scripts/seed/uat/README.md` §5 黄金动线）

---

## 10.1 执行记录（2026-10-28 · 已落地）

> 本节为实施后的实测结论，覆盖 §9 的待确认项。

**环境**：本机 MySQL 8.0.31 / `127.0.0.1:3306` / 库 `bone`。无 `mysqldump` 客户端，改用纯 Python 逻辑备份 `_dump.py`。

**决策取值**（对应 §11 评审清单，无人值守时按推荐项执行）：

| 评审项 | 取值 | 说明 |
|---|---|---|
| 企业设定 | 鼎晖智造集团（制造 + 全渠道零售 + 保险 TPA 演示） | 按 §1 |
| 租户 | **复用 `tenant_id=1001`**（code 保留 `DEMO`，显示名改为鼎晖智造集团股份有限公司） | 按 §9 |
| 规模 | **medium** | 25,133 行演示数据；`--scale large` 可一键放大 |
| 黄金动线 | 客户 300001 / 商品 303001 / 订单 500001（`SO-2026-000001`） | 按 §3.5，实际 ID 段见 README §7 |
| 账号口令 | 统一 `123456`（沿用基线 BCrypt 哈希），`tenant_admin` 为租户总管 | 对外演示前建议改密 |
| 清理策略 | **选择性清理**（非全量重建）+ 全库备份 | 备份 19.2 MB |
| 垂直应用 | 保留 Claim/Settle 作为平台内置演示应用 | 按 §1 第 6 条 |

**清理前后实测**：

| 项 | 处理前 | 处理后 |
|---|---|---|
| 表总数 | 465 | **147** |
| 垃圾表（`bak_*` / `*_e2e_*` / `*_chain_*` / `md_shield_*` / `md_uidesign_*` / `meta_iso_*` …） | 318 张 | 0 |
| 租户 | 64（63 个 `e2e*` / `uatNNNN` / `verify*` 残留） | **1** |
| 平台侧噪声账号 / 角色 / 菜单 / 部门 | 134 / 77 / 194 / 75 | 5 / 4 / 141 / 19 |
| 租户 0 事务噪声（`display_name=REC_<雪花>`、`order_no=NULL`） | 4,735 行 | 0 |
| 孤儿租户数据 | 4,932 行 | 0 |
| 孤儿反向工程元数据（`gen_column_metadata` 指向已删表） | 21,116 行 | 0 |
| 运行期日志（审计/令牌/告警/调度/outbox） | ~15,000 行 | 0 |

**校验门禁实测**：`95_validate.py` **93 项断言全绿（0 失败）**，覆盖
G1 参照完整性 30 项 · G2 金额守恒 7 项 · G3 职责分离 4 项 · G4 状态枚举 10 项 ·
G5 体量下限 10 项 · G6 时间轴 8 项 · G7 拟真可读 6 项 · G8 黄金动线串联 18 项。

**与方案的偏差（如实记录）**：

1. **`mdm_record` 无 `approved_by` 列**（实际 schema 只有 `submitted_by`），SoD 断言落在 `mdm_entity`（`submitted_by ≠ approved_by`）与 `wf_task`（`assignee ≠ started_by`）上。
2. **订单状态枚举无 `REFUNDED`**（`OrderStatus` 仅 `CREATED/PAID/SHIPPED/DELIVERED/CANCELLED`），取消/退款统一用 `CANCELLED`，杜绝幽灵状态。
3. **未单独产出 `00_ids.sql` / `02_golden.sql`**：ID 段与黄金动线锚点直接内置在 `gen.py`（`seq()` 固定段 + golden 分支），避免双份维护漂移；SQL 产物在 `build/` 下按拓扑序编号，可直接评审。
4. **订单明细 5,075 条**（方案估 6,500）：实际每单 1–4 行、均值 2.5，金额守恒前提下保持真实分布，未为凑数放大。

### 10.2 残留清理补遗（2026-10-28 自动化巡检）

> 上一轮落地后，live 库仍残留少量自动化测试/迁移噪声，本次巡检已定点清理并复验 `95_validate.py` **93/93 全绿（0 失败）**。
> 明细与结论见 [`outputs/uat/uat-demo-data-cleanup-report-20261028.md`](../../outputs/uat/uat-demo-data-cleanup-report-20261028.md)。

- 平台 E2E 账号 `iam_account` `E2E817117`（tenant 0）＋ 1 条 `account_role` → 删除。
- E2E 垃圾租户 `iam_tenant` `code=e2e817117`（id `770662773293056000`）＋ 子账号 `e2e817117_admin` ＋ 1 角色 ＋ 1 `account_role` → 删除。
- 迁移备份表 `bak_iam_menu_0067/0068/0070/0071`（4 张）→ `DROP`。
- 清理后实测：租户 **1**（1001）、表 **148**、`bak_*`/`*_e2e_*` 垃圾表 **0**、junk 租户 **0**、tenant 1001 账号 **200**、`mdm_record` **2,400**、`t_order` **2,000**。
- 注意：不应对整库跑 `90_clean.py --apply`——其 §4b 白名单会删除 tenant 0 中不在硬编码 6 账户列表内的全部账号，有破坏平台基线风险；残留噪声用**按 id/tenant 定点删除**更安全。验证须用 **live DB** 实跑门禁，不能只看快照 report（本次即发现快照 report 绿但 live 仍有 E2E 账号/租户残留）。

---

## 11. 评审清单（请在执行前确认）

- [ ] 企业设定（制造+零售+保险 TPA 演示）与行业是否合适？
- [ ] **复用基线 DEMO 租户 1001**（改名鼎晖智造）是否可接受？还是新建独立租户？
- [ ] **中等规模**行数（§2）是否合适？是否需要 `large` 档？
- [ ] 黄金动线（§3.5）锚定的客户/商品/订单 ID 与演示叙事是否匹配？
- [ ] 演示账号与口令策略（分角色 BCrypt + 首次强改密）？
- [ ] 清理策略（全量重建 vs 选择性）+ 执行环境（直连 UAT DB / 经服务 API）与执行时段？
- [ ] 理赔/结算作为"平台内置垂直演示应用"是否认可？
