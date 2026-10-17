# Proposal: ui-designer-ux-optimization

> **追述型归档记录**。本 change 对应的实现与验证已在前序会话完成并提交（`59aadac2`、`4d52c3fb`，均在 dev 历史、双远程同步），
> 本文件按项目 Openspec 规约补立变更产物，使三轮界面设计器优化有可追溯的变更档案。

## Why

`bone-metadata-app` 的界面设计器（`UiSchemaDesigner`）在联调中暴露出三类问题，阻碍「功能完成 + 体验达标」：

1. **导航语义混乱（P0）**：实体详情页五个 Tab 中「界面」与「界面（协议）」命名含混、图标重复（均为 table），
   用户无法区分「显示设置（个人偏好）」与「界面设计器（协议编辑）」两个本质不同的入口；与 PRD §0.7 双界面语义
   （0=手写界面 / 1=自动生成界面）不对齐。
2. **关键操作无保障（P0/P1）**：发布链路缺少协议硬门禁——坏 schema（引用不存在字段）可被发布，线上才暴露渲染错误；
    Ctrl+Z 快捷键是「假承诺」（tooltip 有、实现无）；无全屏专注模式；变体创作无 UI 入口；新用户零引导。
3. **编辑安全与可恢复性缺失（P1）**：未保存改动可静默丢失（无离开拦截）；编辑历史不可视、不可跳回；
   命令面板纯鼠标操作，键盘不可达（WCAG 2.1 缺陷）；预览组件无关重渲染拖累大 schema 编辑性能。

## What Changes

**第一轮（基础体验，前序会话提交）**
- Tab 换名 + 图标区分：「界面」→「显示设置」（setting 图标）、「界面（协议）」→「界面设计器」（layout 图标）、「数据」保留 table，三图标互不相同。
- 全屏禅模式（fixed 吃满视口 + 上下文头，Esc 退出）+ 快捷键落地：Ctrl+Z / Ctrl+Shift+Z / Ctrl+S，**输入框内 Ctrl+Z 不触发全局撤销**（负向用例固化）。
- 发布协议硬门禁：后端 validate 报 ERROR 级问题 → 阻断式横幅顶出 + 发布按钮禁用（`preview.runnable=false`）→ 实体保持草稿；修复后放行（status=1）。负向 + 正向双路径固化。
- 变体创作 UI：四维命中条件编辑器（角色/档位/渠道/设备）+ 结构面板复用（合成 schema）。
- 3 步上手引导：首访自动弹出、可关闭、帮助按钮可重显。

**第二轮（编辑安全与可达性，`59aadac2`）**
- 未保存离开拦截：`dirty` 为真挂 `beforeunload`（对标 Google Docs/Figma/VS Code 基线）；qiankun 微应用内 react-router `useBlocker` 不可靠，路由级拦截需 shell 配合，列为后续项。
- 可视化编辑历史栈：Popover 列出全部可回退步骤，点击跳回任意历史步（`jumpToPast`），**被跳过的中间态全部压入 `future` 保留逐条 redo**（对标 VS Code Timeline / Figma 版本历史）。
- 命令面板键盘导航（↑/↓ + Enter + 自动滚入视口）+ 图标按钮补 `aria-label`（WCAG 2.1）。
- `SchemaPreview` 包 `React.memo`，避免无关重渲染。
- 门禁修复：`VersionController` 属 BONE-AI 方案附录的 To-Be 示意名，按 `reason=tobe-named` 登记 `doc/architecture/doc-code-symbols-baseline.json`（不改变文档语义）。

**第三轮（缺陷修复 + 回归覆盖，`4d52c3fb`）**
- **修复真 bug**：历史栈曾把 `setPast/setFuture` 嵌套在 `setSchema` 更新器函数内读 `cur`，违反 React「useState 更新器必须纯粹」契约，嵌套 setState 转入 render-phase update 重放路径被**双倍应用**——undo/jump 的 future 每次 +2、连续编辑 past 出现 phantom 条目（实测 past=4，语义期望 2）。修复：更新器全部纯粹化，当前 schema 改由渲染期同步的 `schemaRef` 提供（与文件内 `actionsRef` 同模式），压栈语义不变。
- 回归盲区补齐：主探针 33 断言未覆盖第二轮新增历史栈逻辑，合并 E2 段 8 断言并把计数强化为精确值（past 恰 2、jump 后 past 恰 1 且 future 恰 1），全量 41 断言。

## Capabilities

### New Capabilities

- `ui-designer-ux`：界面设计器体验基线能力。要求设计器具备无歧义的双入口导航（命名 + 图标）、发布协议硬门禁、专注模式与快捷键、新手上手引导、未保存离开拦截、可视化可跳回编辑历史栈、键盘可达的命令面板，以及覆盖上述行为的浏览器级回归探针。

### Modified Capabilities

（无。本 change 不修改既有 spec 的需求；发布硬门禁复用后端 validate 既有契约，不新增后端接口。）

## Impact

- **前端代码**：`bone-frontend/apps/bone-metadata-app/src/components/uiSchema/UiSchemaDesigner.tsx`（主落点）、
  `StructurePanel.tsx`、`EntityDetail.tsx`（Tab 命名/图标）；`SchemaPreview` memo 化。
- **测试资产**：`scripts/e2e/ui-designer-ux-probe.cjs`（41 断言，A–G 段；经 shell:3000 真实登录 → 网关 8888 自产自销实体）。
- **门禁基线**：`doc/architecture/doc-code-symbols-baseline.json`（登记 `VersionController` 示意名豁免）。
- **文档**：`doc/design/lowcode/ui/ui-protocol/` 对比文档 §10/§11（探针对照）。
- **无后端契约变更**：纯前端 + 测试资产 + 门禁登记；不新增/修改任何 API。
- **关联但不在本 change 范围**：is_entry 入口动作标记（后端状态机死锁修复 + 0041/0048 迁移）为独立后端议题；
  并行会话后续的「发布可见性三件套」「显示设置个人偏好层定位」建立在本 change 基线之上，由其自身提交记录覆盖。
  截至归档日（2026-10-17），并行会话另有**未提交**在途重构（`DesignerTour` 聚光灯抽取、「显示设置」在途改名
  「列表显示」），其落地后本规约的「双入口导航命名」与「新手上手引导」两条 Requirement 需随之修订（见 verify.md）。
