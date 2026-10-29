# Verify: ui-designer-ux-optimization

> 验证三轮界面设计器优化的落地情况。主证据为 2026-10-16 对**提交态**（`4d52c3fb`）的 41 断言全绿跑；
> 2026-10-17 的复跑尝试受并行会话在途重构阻挡，已逐条归因（见 V4），不影响本 change 的验证结论。

## 结论

**三轮优化全部落地并通过验证。** 第一/二轮行为由 41 断言探针固化；第三轮缺陷修复经「取证探针 + 变异验证」
双重实证（缺陷写法下断言精确变红）；提交态源码复核确认核心机制在后续并行提交中保持完好。

## 已验证

| 项 | 状态 | 证据 |
|----|------|------|
| V1 主探针 41 断言全绿 | ✅ | 2026-10-16 对提交态跑 ux 主探针：41/41、exit=0、零 console error（命令已随 2026-10-28 入口合并更新为 `node scripts/e2e/ui/ui-designer-probe.mjs --scenario ux`）。覆盖 B 换名/图标、C 引导、D 禅模式、E 快捷键（含输入框负向）、E2 历史栈（精确计数）、F 变体、F2 FAB/快照/命令面板、G 发布硬门禁（负向+正向） |
| V2 历史栈缺陷修复实证 | ✅ | 取证探针 7 组受控序列 8/8 绿：修复前 undo/jump future=2、E 序列 past=4；修复后 commit +1 / undo −1 / jump −1、future 每次恰 +1 |
| V3 变异验证（断言有牙齿） | ✅ | 退回修复前代码重跑主探针：2 条强化计数断言精确变红（pastCount=4；jump 后 past=3/future=2），其余 39 条不受影响；恢复后复绿 |
| V4 静态检查 | ✅ | `tsc --noEmit` 通过；eslint（`--max-warnings 0`）干净；`check.sh` 37 门禁 exit=0（提交前预跑 + pre-commit 钩子复跑） |
| V5 提交与同步 | ✅ | `59aadac2`（第二轮）+ `4d52c3fb`（第三轮）均只含本方文件（`--only` 纪律）；`git ls-remote` 实测 Gitee/GitHub dev 均含 `4d52c3fb` |

## V4 附注：2026-10-17 复跑归因（不影响结论）

对**当前工作树**复跑主探针，在 C 段（引导）FATAL：live dev server 已搭载并行会话**未提交**的在途重构——
`DesignerTour.tsx` 聚 spotlight 组件抽取（UiSchemaDesigner 中旧「开始使用」按钮被移除）、
「显示设置」在途改名为「列表显示」。逐项归因：

- 失败项（Tab 名「显示设置」、引导自动弹出/「开始使用」）均指向该在途重构，**非本 change 范围回归**；
- 提交态 HEAD 源码复核：引导模态（`开始使用`）仍在 HEAD；历史栈纯粹化模式完整（`schemaRef` 声明+同步 +
  commit/undo/redo/jumpToPast 四操作经 ref 取当前值，HEAD 第 119–202 行）；
- 结论：本规约声明的行为在**提交态**成立（V1 全绿 + HEAD 源码复核）；在途重构落地后需由其自身变更记录
  更新本规约（Tab 命名与引导实现的所有权已转移至该重构）。

## 遗留 / 后续（非阻塞）

- qiankun 应用内路由级离开拦截（`useBlocker` 不可靠）需 shell 配合，未做；
- 并行会话在途重构（DesignerTour 聚光灯 / OnboardingChecklist / 「列表显示」命名）落地后，
  `ui-designer-ux` 规约的「双入口导航命名」与「新手上手引导」两条 Requirement 需同步修订。
