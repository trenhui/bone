# 双轨收敛（S1）前后端联调验证报告

- 日期：2026-10-18
- 批次：列表配置双轨收敛——overlay 编辑器从建模台移至运行时数据页；「交付预览」Tab 只读化
- 结论：**全部通过**（静态检查 + 单元测试 + 4 项前后端联调探针）

## 一、实现落地核验（S1 设计要点）

| 设计要点 | 落点 | 状态 |
|---|---|---|
| overlay 唯一状态源 | `hooks/useOverlayBundle.ts`（两个 surface 共用） | ✅ |
| 列设置弹层（显隐/密度/恢复默认） | `components/ColumnSettingsPopover.tsx` | ✅ |
| 视图栏（切换/管理/列设置/遮蔽自救） | `components/PersonalViewBar.tsx` | ✅ |
| 运行时工具栏接入（独立 + 嵌入两处） | `pages/RuntimeDataManagement.tsx` | ✅ |
| 交付预览只读化 + 双口径预览 | `components/InterfaceTab.tsx` 重写 | ✅ |
| Tab 改名「交付预览」+ EyeOutlined | `pages/EntityDetail.tsx` | ✅ |
| 设计器工具栏「运行时列表」反向入口 | `components/uiSchema/UiSchemaDesigner.tsx` | ✅ |
| 删除重复「调个人偏好」按钮 | `components/PublishPreviewPanel.tsx` | ✅ |
| 自伤遮蔽实时判据 + dirty 判据 | `utils/columnProjection.ts` 纯函数 ×2 | ✅ |

## 二、静态检查与单元测试

| 检查项 | 结果 |
|---|---|
| tsc --noEmit（8 个工作区） | ✅ 0 错误 |
| eslint（本批 13 个文件） | ✅ 0 error / 0 warning |
| vitest（从 bone-frontend 根） | ✅ **271/271**（18 文件；columnProjection 60 个，含 +10 新增与负向探针） |

预存问题（已提交文件，非本批引入，建议单独立项）：`ModelingWorkspace.tsx:302` 未使用的
eslint-disable 指令（1 error）；AppAntdProvider / main.tsx / ModuleManagement / vite-env
共 6 warnings；packages/shared-services 28 warnings（git 干净）。

## 三、前后端联调探针（shell:3000 → 网关:8888 → metadata-server:9001，真实登录态）

| 探针 | 结果 | 覆盖 |
|---|---|---|
| ui-personal-views-probe.mjs | ✅ **34/34** | 身份徽标常驻；视图切换/新建/隔离；自伤遮蔽判据 UI 化 + 一键自救；**契约变更驱动**（建模台改契约可见性→发布→runtime 无操作提示实时消失/重现，含「overlay 未被清」判别力断言）；服务端 v3 bundle 落盘；两层闭环；收尾两端同清；零 console 错误 |
| ui-designer-ux-probe.cjs | ✅ **41/41** | 变体创作、FAB/快照/命令面板、发布硬门禁（负向阻断 + 正向放行）、历史栈、禅模式 |
| lowcode-closed-loop.cjs | ✅ **48/48** | 五 Tab 结构（结构/交付预览/界面设计器/数据/发布）；`?tab=ui` 深链；双口径切换；ENUM/状态机/非法流转 409；数据隔离 |
| _probe_p1_contract.js | ✅ **HARD + SOFT 全过** | strict 列集合（恰好契约 6 列同序）；非契约字段不出现；列宽生效；预览≡运行时；旧 tab 名已不存在 |
| ui-app-to-data-probe.mjs | ✅ **24/24** | 应用创建→模块→模型→界面设计→协议表单录数据→列表/详情/刷新/API 交叉核验全链（真实登录 + 真实 HTTP） |

## 四、关键行为证据（本轮实测）

- 自伤遮蔽：视图中隐藏契约列 → 工具栏出现「N 列被个人偏好隐藏 · 恢复显示」→ 一键恢复后表头复原、提示消失。
- **契约变更驱动（S1 计划验收判据，自产实体实测）**：建模台把契约列改回隐藏并发布 → runtime 无任何用户操作，提示实时消失，且服务端 overlay 仍盖着该列（证明是判据重算而非偏好被清）；建模台恢复该列契约可见并发布 → 提示重现；一键自救后列回表头。此场景同时是纯前端实时判据相对「契约指纹」的决定性优势：指纹对「契约把列改回隐藏」（列集合未变）和「契约改无关列」都会误报。
- 视图隔离：切回默认视图，被个人隐藏的列恢复显示（偏好不串台）。
- 服务端一致：UI 操作后 GET ui-overlay 为 v3 bundle；收尾后两端（UI + 服务端）同清，不污染 E2E 基线。
- 交付预览双口径：「全员所见」= 纯契约投影；「我的所见」= 契约 ⊕ 当前个人覆盖。

## 五、本轮联调中发现的真 bug 与修复（EntityManagement 模块作用域失效）

**现象**：全链探针 ④「业务对象清单」失败——页面标称「共 221 个（当前模块）」，但该模块实际只有 18 个实体，探针新建实体不可见。

**根因**（`pages/EntityManagement.tsx:220`）：scoped 路由（`/apps/:appId/modules/:moduleId/entities`）
把原始 `moduleId` 直接初始化进 `selectedModule`，而模块选中态靠 `'mod-'` 前缀识别
（`selectedIsModule = selectedModule.startsWith('mod-')`）。缺前缀 ⇒ 判不出模块作用域 ⇒
`load()` 不发 `moduleId` ⇒ 后端返回**全局** 221 条，页面却标着「（当前模块）」。
即：用户从模块点进来的对象列表，看到的是全平台对象。

**修复**（1 行 + 注释）：scoped 初始化加 `mod-` 前缀。修复后实测：列表正确收敛为该模块 18 条。

**探针加固**（同文件 step ④）：列表按创建时间倒序且分页，实体不必然在首屏——
新增「搜索框按名称收敛」步骤（后端 `moduleId + keyword` 组合过滤已实测可用），
消除首屏位置假设导致的分页假红；「已发布」断言从此作用于本实体行（原实现任何行的标签都能蒙混）。

**复验（修复后全量重跑）**：tsc 0 / eslint（改动文件）0 / vitest 271/271 /
五个探针全绿——个人视图 34/34、设计器 UX 41/41、闭环 48/48、P1 HARD+SOFT、全链 24/24。

## 六、代码复核轮（2026-10-18）：4 项优化 + 抓到一个真 bug（CHILD_TABLE 查询 500）

### 6.1 执行的 4 项优化（业界依据 + 可验证）

| # | 优化 | 文件 | 依据 |
|---|---|---|---|
| 1 | `isOverlayDirty` 只对**能影响投影**的偏离判脏（已删字段/契约外字段的陈旧条目不再把「恢复默认显示」永久置亮） | `columnProjection.ts` + `useOverlayBundle.ts` | 启用条件必须语义真实（Salesforce/Airtable 的 reset 只在视图真偏离时出现） |
| 2 | 全局「恢复默认显示」在 >1 个命名视图时先弹确认（说清爆炸半径：N 个视图 + 契约不受影响）；单视图保持一键（与业界 per-view 即时重置一致） | `ColumnSettingsPopover.tsx` | 破坏性操作确认惯例（GitHub/Airtable/Salesforce） |
| 3 | 重命名空名禁用确定（原为静默无操作） | `PersonalViewBar.tsx` | 表单反馈通则：禁止静默失败 |
| 4 | 交付预览「我的所见」在多视图时标注当前视图名 | `InterfaceTab.tsx` | 预览必须自说明口径（Airtable 视图名常驻 UI） |

单测：274/274（新增 3 个负向探针：已删字段 inert、契约外 inert、契约撤下后脏态自洽回归）。

### 6.2 联调中抓到的真 bug：CHILD_TABLE 字段进运行时查询 ⇒ 500

**现象**：个人视图探针末条「零 console 错误」抓到两个 500。日志定位：
`SELECT ..., items, ... FROM t_expense_claim_e2e_*` → `Unknown column 'items'`。

**根因**：`items` 是 CHILD_TABLE（明细子表）字段——DDL 侧（`JdbcPhysicalStructureGatewayAdapter`）
明确「CHILD_TABLE 无物理列」（建表/加列跳列），但运行时查询层把它当数据列：
前端 `fields=` 参数由投影列全量产出（含明细列 code），后端 `RuntimeQuerySupport` 的白名单
也未排除该类型 ⇒ SQL 引用不存在的列。**任何带明细字段的实体，记录列表即 500。**

**修复（两层，同款判据）**：
- 前端 `RuntimeDataManagement.tsx`：`fields=` 排除 CHILD_TABLE code（明细交互是行内入口，
  按行 `listChildren` 拉子行，不占数据列）；
- 后端 `RuntimeQuerySupport.java`：查询层按 DDL 同判据处理——SELECT 静默跳过（无行数据可取）、
  FILTER/ORDER 显然拒绝并给精确 400（静默忽略过滤/排序会返回误导性结果，比报错危险）。

**验证**：模块单测 127/127（+3 新负向探针，逐条可被对应实现行杀死）；API 实测——
`fields=title,items,amount,status` 从 500 变 200（行数据不含 items）；
`q=items:eq:x` 返回 400「q 过滤字段为 CHILD_TABLE（无物理列，不可过滤）」。

### 6.3 本轮最终验证（全绿）

| 类别 | 结果 |
|---|---|
| tsc（8 工作区） | ✅ 0 |
| eslint（本批 8 个前端文件） | ✅ 0/0 |
| vitest | ✅ **274/274** |
| engine-runtime 模块测试 | ✅ **127/127** |
| 个人显示运行时探针 | ✅ **35/35**（含新增确认框断言；500 已消除） |
| 低代码闭环探针 | ✅ **48/48** |
| P1 契约探针 | ✅ **HARD + SOFT 全过** |
| 应用→数据全链探针 | ✅ **24/24**（零 4xx/5xx） |
| 设计器 UX 探针 | ✅ **41/41**（首轮 flaky：快照下拉点击竞争，重跑即过——已知 antd 下拉坑类，非回归） |

遗留（建议后续单独立项）：设计器/交付预览仍允许把 CHILD_TABLE 配成列表契约列——
修复后它不再引发 500（渲染为空列、排序报 400），但是否应从设计器校验层直接禁配，
需要产品口径定夺（业界无「子表列」先例，明细是行内动作）。
