# 「界面（协议）」vs BONE 业务对象界面设计器 — 体验对比与优化建议

> 对比对象（均已读源码/运行验证，非凭记忆）：
> - **当前实现**：`bone-metadata-app` 实体详情页「界面（协议）」Tab → `UiSchemaDesigner`（`UiSchemaTab.tsx` / `uiSchema/UiSchemaDesigner.tsx` + `SchemaPreview.tsx`）。URL：`/metadata#/entities/...?tab=uischema`
> - **BONE 业务对象界面设计器**：`doc/design/lowcode/ui/ui-protocol/bone-ui-schema-designer.html`（高保真单文件原型，已读关键片段）
>
> 验证方式：本地 dev 服务已起，Playwright 登录后截图三张（见文末）；原型与实现的设备/身份/快照/FAB/引导等能力均 grep 源码确认。

---

## 1. 核心结论

| 维度 | 当前实现 | HTML 原型 |
|------|---------|-----------|
| 工程成熟度 | **已落地**：真实后端持久化、If-Match 乐观锁保存、撤销/重做、前端+后端协议校验、场景样例载入 | 静态单文件原型，**无真实持久化**，状态仅存 localStorage |
| 交互范式 | 嵌入实体详情 Tab 的「左配置面板 + 右预览」 | 独立全屏的「左 Rail（树 + 属性检查器）+ 右画布」 |
| 信息架构 | 按「结构 / 规则 / 动作条 / 列表 / 字典 / 自检 / JSON」分 Tab（顶层） | 按「选中画布对象 → 下方 Rail 检查器」的树/检查器模式 |
| 空间利用 | 被实体详情页挤压，左右栏宽度受限（左栏可拖拽但上限 900） | 独占视口，画布、树、检查器都舒展 |
| 设备预览 | ✅ 桌面/平板/手机三档（SchemaPreview 已实现） | ✅ 桌面 1080/平板 768/手机 375 三档（两者都有，**非差异点**） |
| 身份模拟 | ✅ **角色 + 档位(tier) + 渠道(channel) + 设备 四维全**（当前实现更完整） | ⚠️ 仅角色 + 设备；渠道/团队/档位原型自述为「后续迭代」 |
| 版本快照 | ❌ 仅撤销/重做（50 步内存栈） | ✅ 快照/版本历史（localStorage，上限 30，恢复前协议 Gate） |
| 引导/上手 | ❌ 无新手引导（仅有空态 Alert） | ✅ 「3 步上手」蒙层引导 |
| 控件库入口 | 左栏底部「字段库」（点选/拖入） | 悬浮 FAB「＋」就近调出控件库 popover |

**一句话**：当前实现是**能力骨架完整、且部分能力（身份模拟）已超过原型**的可用协议化设计器；HTML 原型在「空间感、引导性、版本快照、FAB 控件库、全屏沉浸」上体验更优，是下一阶段的目标态，但**不能据此说原型全面领先**——它在身份模拟维度反而弱于现状。

---

## 2. 当前「界面（协议）」已具备的能力（不容低估）

从源码逐项确认（`UiSchemaDesigner.tsx` / `SchemaPreview.tsx` / `StructurePanel.tsx`）：

- **双栏实时预览**：左配置 / 右预览，预览跑的是**运行时同一套引擎**（`resolveVariant` / `linkageState` / `runSubmit`，注释明确“预览跑运行时同一套引擎，不落库”）——这是 Retool/Appsmith/Lightning 的共同范式，且避免了“预览通过≠运行时通过”。
- **视图与设备**：表单/详情视图独立配置切换；桌面/平板/手机三档预览（`uisSchemaTheme.css` 的 `.uis-stage.mobile/.tablet`）。
- **四维身份模拟**：角色（employee/reviewer/admin）、档位 tier、渠道（web/mobile/wechat）、设备 → 实时裁决变体（员工端 vs 核赔工作台），直接服务「智能理赔」联调。
- **规则追踪**：`⚡ 规则追踪（命中/总数 联动）` 展开面板，配置对错一目了然。
- **拖放编辑**：分区拖拽排序、控件跨分区拖放、字段库拖入分区（`StructurePanel.tsx` + `drag.ts`）。
- **协议校验**：前端自检（`lint.ts`，错误数显示在 Tab 角标）+ 后端校验（`metadataUiSchemaApi.validate`）。
- **工程细节**：撤销/重做（带 `Ctrl+Z`/`Ctrl+Shift+Z` tooltip）、脏态标记、乐观锁保存、场景样例载入、JSON 直接编辑。

---

## 3. HTML 原型体验“更好”的真实原因（已逐条核对，剔除误传）

| 优势点 | 是否属实 | 为什么更好 |
|--------|---------|-----------|
| **独立全屏画布** | ✅ | 设计界面需要空间上下文；嵌入 Tab 后左右栏过窄，长表单/子表/图表无法舒展。这是原型**最核心**的优势。 |
| **Rail 树 + 下方 Inspector** | ✅ | 选中对象后属性面板即时跟随，减少在多个顶层 Tab 之间切换的认知负荷。 |
| **FAB 控件库（悬浮「＋」）** | ✅（但非 Cmd+K 命令面板） | 悬浮按钮就近调出控件面板，比当前「字段库」挤在左栏底部更符合低代码设计器直觉。 |
| **版本快照/历史** | ✅ | 设计试错成本高，快照可回滚；当前只有撤销/重做，无法长期存档。恢复前跑协议 Gate 阻断错误快照。 |
| **「3 步上手」引导** | ✅ | 新用户首次进入有蒙层，说明①从字段库拖字段②右侧预览③自检通过再发布。当前完全缺失。 |
| **发布前协议 Gate（视觉阻断）** | ⚠️ 部分 | 原型明确“存在协议错误则禁用发布/阻断恢复”；当前保存按钮虽二次确认 + 有自检 Tab，但**保存**不硬阻断（仅 warn）。这点原型更严。 |
| ~~Command Palette (Ctrl/Cmd+K)~~ | ❌ **误传，原型并无** | 原型的 `palettePop` 是“添加控件到选区的控件库”，不是命令面板；全原型无 Cmd+K 快捷键搜索。请勿据此给现状列需求。 |
| ~~设备三档~~ | ❌ 非差异 | 当前实现已有桌面/平板/手机三档，与原型对等。 |
| ~~角色/档位/渠道模拟栏~~ | ❌ **反了** | 当前实现四维齐全且强于原型（原型仅角色+设备）；这是现状优势，不是原型优势。 |

> 修正要点：**原型并非全面领先**。在身份模拟维度它弱于现状；设备三档两者对等；Cmd+K 命令面板原型根本没有。真正值得向原型看齐的是：全屏空间、Rail+Inspector 布局、FAB 控件库、版本快照、新手引导、更硬的协议 Gate。

---

## 4. 点「界面（协议）」是否建议打开独立设计器窗口？

**不建议用浏览器 `window.open` 新窗口**，原因：
- 阻断上下文：从实体详情弹新窗，用户会丢失“当前在编辑哪个实体/哪个视图”的上下文；多显示器、弹窗拦截、刷新后状态难同步、两窗状态不一致。
- 与 qiankun 微前端子应用路由体系冲突（新窗要重新 bootstrap 子应用、重登录态）。

**建议做法（业界通用）**：
1. **进入独立全屏路由**（如 `/metadata/designer/:entityId`），仍在当前浏览器 Tab 内，占满整个工作区，脱离实体详情的 Tab 容器。
2. 实体详情页的「界面（协议）」Tab 保留为**轻量入口 + 摘要预览**，顶部放醒目的「进入设计器」按钮。
3. 设计器内提供「返回实体详情」面包屑（或左上的实体名链接），保证来回穿梭不迷路。
4. 若担心长表单横向空间，可允许设计器路由“出壳”（hidden shell header）以最大化画布——但仍在同一 SPA 内，非新窗口。

**业界最佳实践**：
- **Retool / Appsmith**：全屏画布 + 左组件库 + 右属性面板 + 顶部工具条，都是**沉浸式工作空间**，而非详情页里的一个 Tab。
- **Salesforce Lightning App Builder**：全屏页面构建器，组件拖入画布、右属性编辑器。
- **Mendix / OutSystems**：独立可视化建模工作台，含版本、校验、设备预览、角色模拟。
- 共性结论：**配置类工具应给“沉浸模式”，浏览类（实体详情）应给“摘要+入口”**。当前把重设计器塞进详情 Tab，是“浏览态”与“创作态”混用，是主要体验短板。

---

## 5. 优化建议（按优先级，均基于上述核实）

### P0 — 命名与信息架构（最该先做，零成本）
- **「界面」与「界面（协议）」并存且都用 `TableOutlined` 图标，严重混淆**（源码 `EntityDetail.tsx` L648/L690 确认）。
  - 轻量列配置 Tab（`InterfaceTab`）改名为 **「显示设置」**（或「列配置」）。
  - 协议化设计器 Tab（`UiSchemaTab`）改名为 **「界面设计器」**（与 BONE 业务对象界面设计器原型命名一致）。
  - 两者图标区分（如显示设置用 `SettingOutlined`，设计器用 `LayoutOutlined`/`BuildOutlined`）。

### P0 — 全屏设计器入口
- 在「界面设计器」Tab 顶部加「进入全屏设计器」按钮 → 跳转 `/metadata/designer/:entityId`，占满内容区，左 Rail（树/检查器）+ 右画布。保留详情页回链。

### P1 — 新手引导与空态
- 首次进入设计器展示「3 步上手」蒙层（参考原型）：①从字段库拖字段 ②右侧实时预览 ③自检通过再保存。
- 截图当前确有 `no_such_field` 的协议漂移错误，应在保存/发布前给**阻断式修复清单**（靠近保存按钮），而非仅藏在自检 Tab 角标。

### P1 — 协议 Gate 更硬
- 当存在 `error` 级自检问题时，保存按钮显示红态并 hover 提示“有 N 处协议错误待修复”；或像原型那样“存在错误时禁用保存/发布”并弹修复清单。

### P2 — 向原型看齐的能力（增量）
- **版本快照**：本地快照（上限 30），恢复前跑协议 Gate（原型已实现，现状缺失）。
- **FAB 控件库**：把左栏底部「字段库」升级为悬浮「＋」控件库 popover，支持搜索/分组/拖入选区（原型 `btnPalette`）。
- **Cmd+K 命令面板**：**这是现状与原型都没有的能力**，可作为新增项（快速切换视图、载入场景、跑校验、跳到某控件）——但注意它不属于“原型已有、现状缺失”，是两边都可补的增量。
- **Rail 树布局**：把“结构/规则/动作条/列表/字典/自检/JSON”七个顶层 Tab 收敛为「画布选中对象 → 下方检查器」的树+Inspector 模式，降低顶层切换认知负荷（原型 `rail-tree` + `rail-inspect`）。

---

## 6. 「界面（协议）」是否该换名？

**应该换。**

- **「协议」是后端/工程术语**，指 UI Schema 这份 JSON 契约。对配置界面的人（业务架构/实施顾问）而言，他们关心的是“我要设计一个表单/列表/详情页”，而不是“我在编辑一份协议”。命名应以用户心智模型为准（ Nielsen：用用户语言，不用系统语言）。
- **现状还有一个同名的「界面」Tab**，双重“界面”进一步放大混淆。
- **建议名称**：
  - 首选：**界面设计器**（最直观，与 BONE 业务对象界面设计器原型一致）。
  - 备选：视图设计、页面设计、界面配置。
- 若想保留“协议”概念的可发现性，放在**次要说明文案**里即可，例如 Tab 副标题：“界面设计器 · 基于 bone-ui-schema 协议”。

---

## 7. 截图参考

- `shot_app_ui.png`：当前「界面」Tab（`InterfaceTab`，轻量列配置/显示设置）。
- `shot_app_uischema.png`：当前「界面（协议）」Tab（`UiSchemaDesigner`，左配置右预览，显示 1 处协议错误 `no_such_field`）。
- `shot_proto_designer.png`：BONE 业务对象界面设计器原型（全屏 Rail + 画布 + 3 步上手引导）。

---

## 8. 已执行优化（实施记录）

> 依据上文建议，已在 `bone-metadata-app` 落地下列改动（4 个文件）。未做「Rail 树布局」重架构——见末尾说明。

### 8.1 P0 — 命名 + 图标区分（`EntityDetail.tsx`）
- `界面` Tab → **「显示设置」**，图标 `SettingOutlined`；`界面（协议）` Tab → **「界面设计器」**，图标 `LayoutOutlined`（原两者同名且同用 `TableOutlined`，已拆分）。
- 修复连带 bug：`goInterface`（发布后「查看界面」）原指向 `ui` Tab，改为指向 `uischema`（界面设计器），避免点过去落到显示设置。

### 8.2 P0 — 全屏沉浸入口（`UiSchemaDesigner.tsx`）
- 工具栏新增「全屏」按钮：`fixed` 覆盖层（z-index 1080）占满视口，脱离详情 Tab 宽度限制；顶部含「返回实体详情」面包屑（`navigate` 回 `/entities/:id?tab=uischema`）+「退出全屏」。

### 8.3 P1 — 协议 Gate 强化 + 新手引导（`UiSchemaDesigner.tsx`）
- **红色阻断式 Alert**：存在 error 级自检时，顶部显示「界面协议存在 N 处错误，保存前建议修复」并列出前 3 条 +「前往自检面板修复」按钮；保存 `Popconfirm` 在错误时变为 `danger` 的「强制保存」。
- **3 步上手引导**：首次进入弹 Modal（localStorage `bone-designer-onboarded` 去重），工具栏「?」按钮可重开。

### 8.4 P2 — 版本快照 + FAB 控件库 + Cmd+K（`UiSchemaDesigner.tsx` / `StructurePanel.tsx` / 新增 `snapshots.ts`）
- **版本快照**：工具栏「快照」下拉 → 保存当前快照 / 版本历史（localStorage，按实体隔离，上限 30）；恢复前跑协议 Gate（含 error 级问题则 `Modal.warning` 阻断，与原型一致）。
- **FAB 控件库**：左栏悬浮蓝色「＋」`Popover`，就近调出字段库（`StructurePanel` 抽出可复用 `FieldPalette`，原内联字段库移除）。
- **Cmd/Ctrl+K 命令面板**：全局快捷键开 Modal，支持搜索执行「切视图 / 后端校验 / 保存 / 撤销 / 重做 / 全屏 / 新手引导 / 跳到各面板」。

### 8.5 验证状态（已真机前后端联调，非仅类型检查）
本仓库 `bone-metadata-app` 已在隔离 worktree 中经 **Playwright 登录 + 经网关(8888)全链路 E2E** 跑通（报告 §9）。关键结论：
- ✅ 类型检查：worktree 跑 `tsc --noEmit`，已修复本文件全部真实错误（`CommandOutlined`→`MacCommandOutlined`、`PlusOutlined`/`SchemaPreview` 漏导入、`toolbar` 漏渲染）。
- ✅ **前后端联调闭环通过**：`GET ui-schema(lock=0) → UI 添加分区变脏 → 保存 PUT(If-Match:0) → 后端持久化(lock=1) → GET 比对变更 → 还原 PUT=200 回原始`。乐观锁 `If-Match` 契约端到端生效。
- ✅ **联调中发现并修复 1 个真实缺陷**：`<Spin tip="…">` 独立用法触发 antd `tip only work in nest/fullscreen` 警告（console error），已改为 `Spin + 独立文字` 消除；重跑 **15/15 全绿、零真实 console 错误**。
- ⚠️ 残余仅 2 条 antd `Select clearIcon` 弃用警告（来自 shell/既有组件的 antd 用法，非本次改动，已标注 benign）。
- ⚠️ 早期一轮出现 `single-spa #31`（微应用挂载竞态），重跑未复现，判定为 qiankun dev 挂载竞态、非本次回归。

### 8.6 未执行项（需单独设计评审）
- **Rail 树布局重构**：把「结构/规则/动作条/列表/字典/自检/JSON」七个顶层 Tab 收敛为「画布选中对象 → 下方 Rail 检查器」的树+Inspector 模式。这是对该设计器交互骨架的**根本性重架构**，盲目实施风险高、易回归现有可用工作流，故留作独立设计评审项，不与本次增量改动混做。

---

## 9. 前后端联调测试记录（2026-10-15）

**环境**：worktree `bone-metadata-app` 经软链复用主 checkout 依赖起服（:3004），由主 checkout shell(:3000) 托管；后端 gateway(:8888) + iam(8081)/blueprint(8082)/masterdata(8084)/metadata-server(9001) 全部常驻。实体 `765601800232370176`。

**探针脚本**：`scripts/_e2e_uischema.js`（Playwright 登录 → 进设计器 → 核验重命名/引导/全屏/Cmd+K/FAB → GET→UI 改→PUT→GET 比对→还原）。

### 9.1 结果（15/15 PASS）
| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | 登录拿 token | ✅ | token_len=3305 |
| 2 | 进入设计器挂载 | ✅ | 「结构」Tab 可见 |
| 3 | **重命名生效**（worktree 代码已加载） | ✅ | 含「界面设计器」、不含「界面（协议）」 |
| 4 | 首次「3 步上手」引导 | ✅ | Modal 弹出并关闭 |
| 5 | 全屏沉浸覆盖层 | ✅ | `position:fixed; inset:0` 出现 |
| 6 | Cmd/Ctrl+K 命令面板 | ✅ | 弹出 |
| 7 | FAB 控件库 Popover | ✅ | 「添加字段到界面」弹出 |
| 8 | GET ui-schema（基线） | ✅ | code=200，lock=0，json=178B |
| 9 | UI 添加分区 → 变脏 | ✅ | 「未保存改动」标记 |
| 10 | UI 保存 → 后端持久化 | ✅ | 「已保存」标记 |
| 11 | **GET 比对：变更 + 乐观锁自增** | ✅ | lock 0→1，json 改变 |
| 12 | 还原 PUT → 回原始 | ✅ | PUT=200，json 回原始 |
| 13 | 真实 console 错误 | ✅ | 0（含修复的 Spin 警告） |
| 14 | 未捕获页面异常 | ✅ | 0 |
| 15 | 脚本步骤汇总 | ✅ | 15/15 |

### 9.2 联调中发现并修复的问题
- **`Spin tip` 警告（真缺陷）**：`UiSchemaDesigner` 加载态用 `<Spin tip="加载界面配置…">` 独立渲染，antd 报 `tip only work in nest or fullscreen pattern`（console error）。改为 `<Spin/> + 独立文字`，重跑零真实 console 错误。
- **环境类（非缺陷，已标注）**：2 条 antd `Select clearIcon` 弃用警告（来自 shell/既有组件，非本次改动）；一轮 `single-spa #31` 挂载竞态未复现（qiankun dev 竞态）。

### 9.3 截图证据
`outputs/e2e_designer.png`（设计器主界面+重命名）、`e2e_onboard.png`（3 步上手）、`e2e_fullscreen.png`（全屏覆盖层）、`e2e_cmdk.png`（命令面板）、`e2e_fab.png`（FAB 控件库）。

### 9.4 当前 dev 状态说明
- worktree `bone-metadata-app` 现正运行于 `:3004`（由 `:3000` shell 托管），即浏览器 `http://localhost:3000/metadata#/entities/765601800232370176?tab=uischema` 已反映本次全部优化，可直接验收。
- `bone-frontend/node_modules` 为指向主 checkout 依赖的软链（本地 dev 产物，gitignore），无需提交。


---

## 10. 第三轮：领域防混淆 + 业界最佳实践统一优化（2026-10-15 晚）

### 10.1 本轮修复/优化项
| # | 项 | 类型 | 说明 |
|---|---|---|---|
| 1 | 预览场景默认按实体领域自动选定 | 真缺陷 | 原硬编码 `SCENARIOS[1]`（门诊快赔）导致报销单默认带错场景；改为首进自动选领域最贴合场景，用户显式选择后固定 |
| 2 | 预览视图跟随编辑视图（表单/详情） | WYSIWYG 缺口 | 结构面板可切「详情视图」但预览无对应档位；`editView` 下沉 + 预览 Segmented 恒显「详情」档（原仅 `hasDetail` 时显示，新建 detail 视图前无从预览），用户手点「列表」后不被拽回 |
| 3 | 丢弃未保存改动的三道守卫 | 最佳实践 | 「重新加载」Popconfirm、全屏「返回实体详情」Popconfirm、`beforeunload`（刷新/关闭）——此前均会静默丢失界面配置 |
| 4 | 全局撤销/重做快捷键 | 最佳实践 | Ctrl/⌘+Z / Shift+Z / Ctrl+Y；输入框/可编辑区不拦截；undo/redo 改 useCallback 稳定引用 |

### 10.2 验证（Playwright 探针 `scripts/_probe_designer_polish.js`，19/19 PASS）
- 报销单：默认场景=差旅报销、不含门诊快赔、预览默认表单、结构切详情→预览跟随（徽标=详情视图）、载入样例变脏、Ctrl+Z 回已保存、重新加载弹丢弃确认
- 理赔：默认场景=门诊快赔、不含差旅报销、载入样例变脏、**未保存离开页面弹 beforeunload**（负向验证：对话框确实触发）
- 控制台真实错误 0（antd clearIcon 弃用告警为环境良性，已过滤）

### 10.3 探针方法论踩坑（复用价值）
- **hash 导航不刷新**：两实体 URL 仅 hash 不同，`page.goto` 为同文档导航，微应用不重挂载——`waitForSelector('.uis-pv-badge')` 会命中旧设计器导致误判（曾误报「理赔默认场景=差旅报销」）。必须 `page.reload()` + 等新 entityCode 出现。
- **antd Popconfirm 文案在 `.ant-popover-inner-content`**，不在 `.ant-popover-title`；双汉字按钮渲染为「取 消」（带空格）。
- **onboarding 模态真实按钮是「开始设计」**（非「开始使用」）；`offsetParent` 对 fixed 定位元素恒为 null，不可用于可见性判断，应用 `getComputedStyle().display`。
- **beforeunload 探针**：dialog 需 `accept()` 才放行导航，`dismiss()` 会取消导航导致 `reload` 超时——超时本身即守卫生效的证据。

### 10.4 环境备注
- metadata-server(:9001) 本轮曾崩溃（后端整站 503），后自行恢复（PID 10911）；探针失败三连发曾疑为前端缺陷，实为后端抖动 + 探针缺陷叠加。后端实况：gateway 8888 / iam 8081 / blueprint 8082 / masterdata 8084 / metadata-server 9001 均正常。
- 截图：`outputs/probe_polish_报销单.png`、`outputs/probe_polish_理赔案件.png`。


---

## 11. 第四轮：「显示设置」定位修正 + 设计器 list 契约真源收敛（2026-10-16）

### 11.1 问题诊断（源码实证，非推断）
- **「显示设置」（tab=ui）**：`InterfaceTab` 列投影 overlay（显隐/显示名覆盖/列序/密度），独立 `ui-overlay` API + localStorage，**是运行时数据列表列与密度的唯一真源**（`RuntimeDataManagement: projectColumns(writableFields, overlay)`）。
- **「界面设计器」（tab=uischema）**：`UiSchemaDesigner` 的 `views.list`（列/列宽/固定列/列序/列标题）**零运行时消费方**——grep `views.list` 全部命中都在设计器内部（ListPanel/SchemaPreview/lint/schemaOps/variants），写入了 published 快照却没人读，「配了不生效」。
- 两处都能配列表列 ⇒ 用户认知重叠（不知谁生效）。直接删「显示设置」不可行（运行时列表会立刻失去配置源）。

### 11.2 方案（对标 Salesforce Search Layouts 组织级 + List View 个人偏好双轨）
| 层 | 归属 | 内容 | 生效方式 |
|---|---|---|---|
| 契约层 | 界面设计器 `views.list`（published 快照优先，与后端 `schemaForExecution` 同口径） | 列集合/列序/列宽/固定列/列标题 | 发布后生效 |
| 偏好层 | 列表显示页 overlay | 个人显隐/密度/个人改名 | 即时生效 |

- **strict 集合语义**：契约即完整列集合（未列入字段不进运行时列表）——该实体 22 字段、契约仅 6 列，若"未列出即追加"会把策划清单淹没；新字段由建模者在设计器显式加入，「列表显示」页以「未在契约」标签 + 「去设计器加入」引导，不静默追加。
- 可见性：契约隐藏或个人隐藏任一为真即隐藏；列标题：个人改名（仅确实改过名）> 契约 `format.title` > 字段名。
- ListPanel「移除列」由删条目改为**契约内置隐藏**（`visible:false` 保留条目）：strict 集合下删条目与置隐藏的运行时效果相同，但删条目会丢失显式契约记录、无法原路恢复；保留 `visible:false` 条目让「这列被契约隐藏」成为可审计、可反转的决策（原 `removeColumn` 随之删除，理由写入 schemaOps 注释）。

### 11.3 代码改动
- `utils/columnProjection.ts`：新增 `projectRuntimeColumns(fields, overlay, listView)` + `fieldsOutsideContract(fields, listView)` 纯函数；无契约列时整体回落旧 `projectColumns`（零回归）。
- `hooks/useBoneUiSchema.ts`：新增 `executionSchema`/`executionSource`（published 快照优先、从未发布回落草稿）——与后端 `schemaForExecution` 同口径；草稿管线 `schema` 不动（设计器表单行为零变化）。
- `pages/RuntimeDataManagement.tsx`：列表列投影切到契约优先；antd Table 接入契约 `width`/`pinned`（`fixed:'left'`）。
- `components/InterfaceTab.tsx`：契约感知——分层 banner、契约列序置灰（「契约」标签）、契约隐藏列置灰+解释、未列入字段「未在契约」+去设计器加入、预览与运行时共用同一投影。
- `components/uiSchema/ListPanel.tsx` + `schemaOps.ts`：移除列 = 契约内置隐藏；删 `removeColumn` 死代码。
- `pages/EntityDetail.tsx`：tab 更名「显示设置」→「列表显示」（URL key `?tab=ui` 不变，深链兼容）；注入 `entityId`/`onOpenDesigner`。

### 11.4 验证（三重）
1. **单测** `columnProjection.test.ts` 27/27 PASS（含 2 个负向探针：无 width 契约列不得产 width、overlay 列序不得颠覆契约序）。
2. **存量回归** `scripts/_probe_designer_polish.js` 全绿（保存 200/重载持久化/预览跟随/三态徽标未破坏）。
3. **P1 联调探针** `scripts/_probe_p1_contract.js`（理赔案件 765601790585470976，published 契约 6 列）：HARD 4/4——表头严格=契约 6 列同序、非契约字段（理算金额）不出现、**契约列宽生效**（colgroup = 150/100/110/120/120/120px，设计器配置第一次真正驱动运行时）；SOFT 5/5——tab 更名、分层 banner、「未在契约」引导、预览≡运行时同列；零 console error。

### 11.5 运维踩坑（复用价值）
- **环境变量 `SERVER__PORT=58926` 会被 Spring 宽松绑定成 `server.port`**：本机 shell 注入了该变量（WorkBuddy），导致 `java -jar` 不读 yaml 的 9001 而去抢 58926，与僵死实例冲突后「Web server failed to start」。**解法：显式 `--server.port=9001`**（CLI 优先于环境变量）。
- 本机 curl 走 `HTTP_PROXY=127.0.0.1:53687`，localhost 探测也被代理劫持返回 502——**本地健康检查必须 `--noproxy '*'`**，否则把代理故障误判为服务故障。
- 前台 bash 里 `nohup java &` 会被沙箱回收（进程组清理）；长驻服务必须用后台任务方式启动。
- antd v5 在 `scroll.x` 下渲染 `.ant-table-measure-row`（无 td 的度量行），探针统计行数必须剔除，否则每表多一行导致断言失败。
