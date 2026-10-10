# 原型闭环走查记录 · 通用性验证（2026-10-09）

> 原型：`bone-metadata-ui-prototype.html`（场景包驱动版，**v0.32 · 代码实测修正**）。走查方式：三场景深链逐 Tab 截图。
> 结论：**换场景不换路径** —— 闭环六步（新建 → 结构 → 界面 → 发布 → 数据 → 再加字段·再发布）在三场景下步骤完全一致，仅字段表、ESC 提示与边界卡随场景变化。

## 〇、v0.32 修订说明（本轮）

原型与 PRD 同步到 **v0.32**，核心是把「逐行代码实测」的三处推翻结论**前置到评审入口**，避免评审误判：

| # | 实测结论（`file:line`） | 原型/PRD 落点 |
|---|---|---|
| C1 | `MetaList/MetaForm` **全仓零命中**（`@bone/ui` 仅 tokens/theme/authority；`@bone/shared-components` 零消费死包） | 原型新增「落地路线 v0.32」Tab；PRD §0.6.1C1 + §11.1 改为「**从零新建**」 |
| C2 | `FIELD_TYPES`（`shared-types/metadata.ts:175`）**无 ENUM**；`MetaTemplateApplicationService.java:106` 丢 `enumValues`；`meta_model_template_field` 无 `enum_values` 列 | 原型结构页新增 **ENUM 建模门**（按场景列出依赖的枚举字段 + T3 前置提示）；PRD §0.6.2 立 T1/T2/T3 |
| C3 | `/…/data` 只读 `?entity=code`（`RuntimeDataManagement.tsx:88`），列表跳 path id（`EntityManagement.tsx:461`）⇒ **静默看错实体** | PRD §0.6.1C3 提为 **P0 首位**（非「体验摩擦」），并给负向断言判据 |

原型侧同步修复：`goTab()` 移除空 `if` 死语句（Tab/URL 同步整改项 W0b-B2 的原型对照）、补货场景 `enumPill` 重复 `CANCELLED` 分支、W0 退出标准改为可脚本化判据。

## 一、走查矩阵（3 场景 × 闭环步骤）

| 闭环步骤 | 设备巡检（够用） | 供应链补货（边界） | 访客登记（换皮） |
|---|---|---|---|
| ① 新建 RUNTIME 实体 | `t_device_inspect` 纯配置型 · 无聚合 | `t_replenishment` 配置型 + 状态机 | `t_visitor_pass` 另一垂直 |
| ② 结构：字段建模 | 6 字段 | 9 字段（5 态 ENUM + writeOnce 快照） | 7 字段（enum 商务/面试/施工） |
| ③ 界面：快照渲染 + 预览验收 | list/form 同源 Meta* | 同左 | 同左 |
| ④ 发布：影响分析 + 热加载 | L1 · P95≤5s | L1 · 状态机链展开 · 跨实体逃逸标注 | 同巡检（步骤与巡检相同 UC-04） |
| ⑤ 数据：运行时 CRUD | 首条记录 + 412 演示 | RPL-单号列表 + 状态徽标 + 状态动作推导(v1.1) | 同构 MetaList |
| ⑥ 再加字段 → 再发布（UC-02 · 1→n） | 热加载演示字段 | expected_arrival_date / suggested_qty / safety_snapshot | 热加载演示字段 |

场景通用性由原型内声明 + 实测双重佐证：顶部阶段条右侧明示「**场景包只换数据 · 步骤不变**（PRD §2.0）」；访客场景结构页内嵌「**换皮验证：证明 Meta\* 不绑死供应链文案**」横条。

## 二、关键截图证据

| 证据点 | 截图 |
|---|---|
| 补货 · 结构（9 字段 · writeOnce） | `preview-scene-replenishment.png` |
| 补货 · 发布（影响分析字段级 + Schema diff） | `preview-walk-repl-pub.png` |
| 补货 · 数据（MetaList + 状态徽标 + catalog 快照） | `preview-walk-repl-data.png` |
| 访客 · 结构（换皮验证横条 + 7 字段） | `preview-walk-visitor-fields.png` |
| 访客 · 界面（配置态/使用态 + 同源渲染） | `preview-walk-visitor-ui.png` |
| 巡检 · 界面（默认场景基线） | `preview-walk-device-ui.png` |

> 截图均在 `doc/design/ui/`，由无头 Chromium 深链逐 Tab 生成（`#scene=<x>&t-<tab>`）。

## 三、通用性机制归因（为什么其他场景都能支持）

1. **场景包 = 纯数据结构**（实体名/字段表/listCols/formSample/enumPill/ESC 提示），不携带任何步骤逻辑——步骤由通用 Phase UI（0→1 / 1→n）驱动；
2. **Meta\* 渲染链与业务字段解耦**：界面 Tab 与数据 Tab 全部从字段元数据即时生成（字段→控件映射 TYPE_CTRL），新增垂直领域（如 HR 请假、IT 工单）只需新增一个场景包对象；
3. **边界卡随场景自适应**：Mode B 覆盖范围与逃逸建议（如补货→建议算法/Inventory.receive；访客→门禁硬件联动）按场景声明，防止"低代码越界"；
4. 真实链路已在 `tools/supplychain-replenishment-demo/` 对运行时服务完成种子级验证（零手工 DDL、发布自动建表、If-Match 幂等）。

## 四、遗留提醒

- 「验收路径 A」类文案横幅在当前版本仍存在（v0.24 用户要求清除，此版被改回并指向 UC 编号）——与"DEMO 不出现评审话术"约束冲突，待外部会话收敛后统一以中性措辞（如「闭环走查」）替换；
- 原型文件存在多会话并发写入，深链增强（`#scene=x&t-<tab>` 复合 hash）本轮已合入，若被覆盖需重放；
- **v0.32 新增遗留**：结构页「ENUM 建模门」依赖 T1（`meta_model_template_field` 增列，L3 DDL）。**未落地前，评审能看到原型演示 ENUM 徽标，但生产建模 UI 建不出枚举字段** —— 演示与生产的能力差必须在评审口径里说清，否则会出现「原型能做=生产能做」的误判。

## 五、v0.32 走查新增路径

| 路径 | 入口 | 看什么 | 截图 |
|---|---|---|---|
| 落地路线 | 侧栏「落地路线 · v0.32」 | 三处推翻（C1/C2/C3）→ ENUM 三步 → W0–W2 切片与 DoD → 菜单收口 → 两处待拍板偏离 | `preview-v32-plan.png` |
| ENUM 建模门 | 切任一场景 →「结构」Tab 顶部黄条 | 本场景依赖哪几个 ENUM 字段、为何是 T3 前置条件（随场景联动：补货 `status` / 访客 `visit_type`） | `preview-v32-enum-gate.png` |
| 跨层一致 | 落地路线 → 「看补货 5 态字段表」 | 原型字段表 ⇄ PRD §2.5 字段表 ⇄ 生产模板种子（T4）三者须逐字对齐 | — |

> 无头 Chromium 实测：控制台**零错误**，7 个面板（结构/界面/发布/数据/实现差距/落地路线/状态异常）与侧栏导航目标一一对应，JS 括号平衡。
