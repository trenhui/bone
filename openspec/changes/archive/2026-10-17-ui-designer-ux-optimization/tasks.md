# Tasks: ui-designer-ux-optimization

> 状态：**全部完成**。实现分三轮（前序会话第一轮；本轮会话第二、三轮并修复缺陷），
> 提交 `59aadac2`（第二轮）、`4d52c3fb`（第三轮修复 + 探针合并），均在 dev 历史、双远程同步。

## 第一轮：基础体验（前序会话）

- [x] T1.1 Tab 换名：「界面」→「显示设置」、「界面（协议）」→「界面设计器」，对齐 PRD §0.7 双界面语义
- [x] T1.2 Tab 图标区分：setting / layout / table 三图标互不相同
- [x] T1.3 全屏禅模式：fixed 吃满视口 + 上下文头，Esc 退出
- [x] T1.4 快捷键落地：Ctrl+Z / Ctrl+Shift+Z / Ctrl+S 真绑定（原为假承诺）
- [x] T1.5 快捷键负向：输入框内 Ctrl+Z 不触发全局撤销
- [x] T1.6 发布协议硬门禁：坏 schema → validate ERROR → 阻断横幅 + 按钮禁用 + 实体保持草稿
- [x] T1.7 发布门禁正向：修复后按钮启用、发布放行（status=1）
- [x] T1.8 变体创作 UI：四维命中条件编辑器（角色/档位/渠道/设备）+ 结构面板复用
- [x] T1.9 3 步上手引导：首访自动弹出、可关闭、帮助按钮可重显
- [x] T1.10 探针契约修复：`lowcode-closed-loop.cjs` 过时契约更新 + 闭环验证

## 第二轮：编辑安全与可达性（`59aadac2`）

- [x] T2.1 未保存离开拦截：`dirty` 挂 `beforeunload`；注释记录 qiankun 路由级拦截不可靠（后续项）
- [x] T2.2 可视化编辑历史栈：Popover 列回退项 + `jumpToPast` 跳回任意步，跳过中间态压 `future` 保留 redo
- [x] T2.3 历史栈按钮 ARIA（`aria-label="编辑历史"`）+ 禁用态（past 为空时 disabled）
- [x] T2.4 命令面板键盘导航：↑/↓ + Enter + 自动滚入视口
- [x] T2.5 图标按钮 ARIA 补齐：撤销/重做/重新加载/FAB（WCAG 2.1）
- [x] T2.6 `SchemaPreview` 包 `React.memo`；移除死辅助函数 `relTime`
- [x] T2.7 门禁修复：`VersionController` 按 `tobe-named` 登记 `doc-code-symbols-baseline.json`（不改为文档语义）

## 第三轮：缺陷修复 + 回归覆盖（`4d52c3fb`）

- [x] T3.1 缺陷定位：合并历史栈断言入主探针首跑即红（future +2 / past=4），读源码定位为更新器嵌套 setState
- [x] T3.2 React 18.3.1 reconciler 逐行证实双倍应用机制（render-phase update 重放路径）
- [x] T3.3 修复：commit/undo/redo/jumpToPast 更新器纯粹化 + `schemaRef` 渲染期同步
- [x] T3.4 取证探针 7 组受控序列 8/8 绿（修复前 future=2/past=4 → 修复后全部符合语义）
- [x] T3.5 E2 段 8 断言合入主探针，计数强化为精确值（past 恰 2、jump 后 past 恰 1 且 future 恰 1）
- [x] T3.6 变异验证：退回修复前代码，2 条强化断言精确变红、其余 39 条不受影响
- [x] T3.7 tsc --noEmit + eslint 通过；check.sh 37 门禁通过
- [x] T3.8 共享树纪律提交（`--only` 只带 2 文件）；双远程推送并经 `ls-remote` 实测确认

## 收口

- [x] T4.1 归档记忆：项目日志 + MEMORY.md 长期要点（更新器纯粹契约、远端跟踪引用过期）
- [x] T4.2 本追述变更产物五件套（proposal/design/tasks/verify/specs）
