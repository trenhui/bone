# ui-designer-ux Specification

## Purpose

定义 `bone-metadata-app` 界面设计器（`UiSchemaDesigner`）的体验基线：无歧义的双入口导航、发布协议硬门禁、
专注模式与快捷键、新手上手引导、未保存离开拦截、可视化可跳回编辑历史栈、键盘可达命令面板，
以及覆盖上述行为的浏览器级回归探针。本规约由追述 change `ui-designer-ux-optimization` 的三轮优化沉淀而成。

## Requirements

### Requirement: 双入口导航命名与图标区分

实体详情页 SHALL 以「显示设置」（个人偏好层）与「界面设计器」（协议编辑）两个命名无歧义的 Tab 呈现双入口，
且两 Tab 图标互不相同、与「数据」Tab 三者均不重复，使用户不依赖记忆即可区分入口职责。

#### Scenario: 五 Tab 命名与三图标区分
- **WHEN** 用户进入实体详情页
- **THEN** Tab 集合包含「结构 / 显示设置 / 界面设计器 / 数据 / 发布」，不存在旧同名「界面（协议）」
- **AND** 显示设置用 setting 图标、界面设计器用 layout 图标、数据保留 table 图标，三者互不相同

### Requirement: 全屏禅模式与编辑快捷键

设计器 SHALL 提供全屏禅模式（吃满视口、保留上下文头、Esc 退出），并真实绑定 Ctrl+Z / Ctrl+Shift+Z / Ctrl+S。
输入框聚焦时的 Ctrl+Z SHALL 走文本编辑原生撤销，不得触发全局撤销。

#### Scenario: 禅模式进入与退出
- **WHEN** 用户触发全屏禅模式
- **THEN** 出现 fixed 定位吃满视口的覆盖层并带上下文头；按 Esc 后退出

#### Scenario: 快捷键负向——输入框内不触发全局撤销
- **WHEN** 焦点在输入框内、存在未保存改动时按 Ctrl+Z
- **THEN** 输入框执行文本原生撤销，全局未保存改动状态不变（仍为脏）

### Requirement: 发布协议硬门禁

存在 ERROR 级协议问题的 schema SHALL 阻断发布：顶出阻断式横幅、发布按钮禁用、实体保持草稿；
修复后 SHALL 自动放行。负向与正向路径均须可自动化验证。

#### Scenario: 坏 schema 阻断发布
- **WHEN** schema 引用不存在字段（如 `no_such_field_xyz`）且用户尝试发布
- **THEN** 后端 validate 返回 ERROR 级问题，前端顶出阻断横幅，发布按钮禁用，实体 status 保持 0（草稿）

#### Scenario: 修复后放行
- **WHEN** 用户删除坏引用后重新发布
- **THEN** 发布按钮启用，发布成功，实体 status 变为 1

### Requirement: 新手上手引导

首次访问设计器 SHALL 自动弹出 3 步上手引导（左配置右预览 / 身份模型 / 快捷键说明），
可关闭，且经帮助按钮可再次唤显。

#### Scenario: 首访引导弹出与重显
- **WHEN** 用户首次进入设计器
- **THEN** 自动弹出含三步与快捷键说明的引导；关闭后可通过帮助按钮重新唤显

### Requirement: 未保存离开拦截

存在未保存改动时，刷新或关闭标签页 SHALL 触发 `beforeunload` 确认，防止静默丢失。
应用内路由级拦截（qiankun 子应用 `useBlocker`）不在本规约范围（已知不可靠，需 shell 配合）。

#### Scenario: 脏状态下刷新拦截
- **WHEN** 设计器存在未保存改动且用户刷新或关闭标签页
- **THEN** 浏览器弹出离开确认（beforeunload 生效）

### Requirement: 可视化编辑历史栈

设计器 SHALL 以 Popover 展示全部可回退步骤（含「共 N 步可回退」计数），支持点击跳回任意历史步。
跳回时被跳过的中间态 SHALL 全部压入 future 保留逐条 redo；跳回后仍保留相对已发布态的脏状态。
past/future 计数 SHALL 与实际堆栈恒等，不得出现 phantom 条目或双倍压栈。

#### Scenario: 跳回任意历史步并保留 redo
- **WHEN** 用户连续编辑 2 步后打开历史 Popover 并跳回最近 1 步
- **THEN** 列出「回退 1 步 / 回退 2 步」两项（共 2 步可回退）；跳回后「重做」可用、脏状态保留 1 步

#### Scenario: 时间线计数恒等
- **WHEN** 跳回后二次打开历史 Popover
- **THEN** 仅剩 1 个回退项（共 1 步可回退），且「可重做 1 步」——past/future 计数与实际堆栈一致

#### Scenario: 更新器纯粹化不变量
- **WHEN** 执行 undo / redo / jumpToPast 任一操作
- **THEN** 每次操作对 past/future 的影响恰为语义值（undo：past −1、future +1；jump 依跳过步数等量转移），
  不因 React 状态更新机制产生重复压栈（规约由精确计数探针守护）

### Requirement: 命令面板键盘可达性

命令面板（Cmd/Ctrl + K）SHALL 支持 ↑/↓ 游标导航 + Enter 执行 + 激活项自动滚入视口；
设计器全部图标按钮 SHALL 具备可访问名称（`aria-label`），满足 WCAG 2.1。

#### Scenario: 命令面板键盘执行
- **WHEN** 用户以键盘打开命令面板并以上下键选择条目后按 Enter
- **THEN** 对应命令执行（如跳转面板），激活项在导航时保持可见

### Requirement: 设计器回归探针

设计器体验基线 SHALL 由浏览器级回归探针守护：经 shell:3000 真实登录 → 网关自产自销实体，
覆盖换名/图标、引导、禅模式、快捷键（含负向）、历史栈（含精确计数）、变体、FAB/快照/命令面板、
发布硬门禁（负向+正向）与零 console error。计数类断言须使用精确值，并经验证具备判别力
（变异测试：缺陷写法下相关断言必须变红）。

#### Scenario: 全量探针通过
- **WHEN** 对运行中的全栈执行 `scripts/e2e/ui-designer-ux-probe.cjs`
- **THEN** 全部断言通过（41 项），无新增 console error
