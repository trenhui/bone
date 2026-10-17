# Design: ui-designer-ux-optimization

> 记录三轮优化的关键设计决策与被交所依据（业界基线）。实现细节以提交 `59aadac2` / `4d52c3fb` 与
> `UiSchemaDesigner.tsx` 源码为真源。

## D1. 双入口导航：命名 + 图标双通道区分

「显示设置」（个人偏好层）与「界面设计器」（协议编辑）是两个本质不同的入口，仅靠命名不够——Tab 图标同步区分：
setting / layout / table 三图标互不相同。图标语义对照 antd 图标集常规用法，与 PRD §0.7 双界面语义对齐
（手写界面 vs 自动生成界面）。

## D2. 发布协议硬门禁：负向阻断 + 正向放行双路径

坏 schema（如引用不存在字段 `no_such_field_xyz`）不得进入发布态。实现链路：
后端 validate 返回 ERROR 级问题 → 前端顶出**阻断式横幅**（不藏在 Tab 里）→ 发布按钮禁用（`preview.runnable=false`）
→ 实体保持草稿（status=0）。修复后按钮启用、发布放行（status=1）。探针对负向/正向各固化一条断言。

## D3. 快捷键：全局监听 + 输入框负向排除

Ctrl+Z / Ctrl+Shift+Z / Ctrl+S 挂全局 keydown。**输入框聚焦时 Ctrl+Z 不得触发全局撤销**
（负向用例：输入框内 Ctrl+Z 后 dirty 仍为 1）——否则与文本编辑原生撤销冲突。

## D4. 未保存离开拦截：beforeunload 及其边界

`dirty` 为真时挂 `beforeunload`，覆盖刷新/关闭标签页（对标 Google Docs / Figma / VS Code 基线）。
**已知边界**：qiankun 微应用内 react-router 的 `useBlocker` 不可靠（子应用路由事件被 shell 劫持），
应用内路由级拦截需 shell 配合，列为后续项，不在本 change 范围。

## D5. 可视化编辑历史栈：jumpToPast 语义 + 更新器纯粹化（本 change 最重要的技术决策）

**交互语义**：Popover 列出全部可回退步骤（「回退 1 步 / 回退 2 步 / …」+ 「共 N 步可回退」），
点击跳回任意历史步。`jumpToPast(fromMostRecentIndex)`：被跳过的若干中间态**全部压入 `future`**，
保证后续仍可逐条 redo——与 undo 语义严格一致（对标 VS Code Timeline / Figma 版本历史）。
跳回后仍保留脏状态（相对已发布态），「可重做 N 步」与 past/future 实际长度恒等。

**纯粹化不变量（缺陷修复沉淀）**：历史栈的四个操作（commit/undo/redo/jumpToPast）曾把 `setPast/setFuture`
嵌套在 `setSchema` 更新器函数内读 `cur`。该写法违反 React「useState 更新器必须纯粹」契约：
嵌套 setState 会转入 render-phase update 重放路径，同一次 dispatch 内的嵌套更新被重复消费，
表现为 **future 每次 +2、past 出现 phantom 条目**（React 18.3.1 reconciler 实测）。
修复模式：更新器只做纯计算，跨状态读取一律经**渲染期同步的 ref**（`schemaRef`，与文件内既有的 `actionsRef` 同模式），
压栈值仍为「动作前 schema」，语义不变。**该不变量由探针精确计数断言守护**（见 V2）。

## D6. 命令面板键盘可达性

命令面板（Cmd/Ctrl + K）补 ↑/↓ 游标 + Enter 执行 + 激活项自动滚入视口；全部图标按钮补 `aria-label`
（撤销/重做/重新加载/FAB/历史），满足 WCAG 2.1 名称-角色-值三要素。

## D7. 预览性能

`SchemaPreview` 包 `React.memo`：设计器左栏任何状态变化（含打开 Popover、切换面板）不再触发预览树重渲染。

## D8. 回归探针架构与验证纪律

`scripts/e2e/ui-designer-ux-probe.cjs`：Playwright + Chrome，经 shell:3000 真实登录取 JWT → 网关 8888 →
实例化 `tpl_device_inspection` 模板实体自产自销，A–G 段共 **41 断言**（B 换名/图标、C 引导、D 禅模式、
E 快捷键、**E2 历史栈**、F 变体、F2 FAB/快照/命令面板、G 发布硬门禁负向+正向），末段断言零 console error。

验证纪律（本项目铁律，本 change 全程执行）：
- **负向实证**：发布门禁负向（坏 schema 阻断）、输入框快捷键负向、beforeunload 之外的状态漂移检测。
- **变异验证**：退回修复前代码重跑探针，强化计数断言必须精确变红（实测 2 条红、其余 39 条不受影响），
  证明断言咬住缺陷而非连带误伤。
- **跑通 ≠ 测到**：E2 段断言钉死 past/future **精确计数**（恰 2 / 恰 1），宽松存在性断言视为无判别力。
