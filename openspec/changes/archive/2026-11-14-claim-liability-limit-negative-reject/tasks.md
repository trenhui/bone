# Tasks: claim-liability-limit-negative-reject

> 状态：**全部完成**（实现 + 存量归一化 + 回归防护 + 文档）。

## 一、后端守卫（配置期）

- [x] T1.1 先写会红的用例（4 条）：新建负上限 → 拒 / 编辑负上限 → 拒 / `0` → 接受 / 不填 → 接受
- [x] T1.2 确认红灯成立（19 跑 2 红，恰好是新写的两条负数用例）
- [x] T1.3 实现：`assertRuleUsable` 增加责任额度上限的非负守卫（`signum() < 0`，只拦负数）
- [x] T1.4 守卫消息补 `consequence`（说清"负数 = 取消限额；不封顶请填 0 或留空"）
- [x] T1.5 更新 `assertRuleUsable` 注释：写明三条依据（风险方向相反 / 同域三字段已一致 / S25 原文是 0）
- [x] T1.6 更新 `LiabilityRule#liabilityUncapped()` javadoc：区分「契约语义 0」与「读取期兜底 <=0」
- [x] T1.7 更新 `QuotaStack#liabilityLayer` 注释：同上
- [x] T1.8 确认转绿（19/19）+ 全模块回归 RC=0

## 二、变异验证（证明断言有牙齿）

- [x] T2.1 变异一：去掉守卫 ⇒ 恰好的 2 条负数用例转红（19 跑 2 红）
- [x] T2.2 变异二：守卫写成 `signum() <= 0`（过度收紧）⇒ 恰好的 2 条正向钉子转红
- [x] T2.3 恢复原版并复跑全绿

## 三、存量归一化

- [x] T3.1 新增 `0096_normalize_negative_liability_limit.sql`（审计留痕 + 归一化 + bump version + 终态断言）
- [x] T3.2 登记进 `migration-manifest.json`（order 102 / kind mixed / executableCount 102）
- [x] T3.3 迁移门禁通过（109 文件 / 102 可执行 / order 1..102 连续）+ G39 init 覆盖通过
- [x] T3.4 本地库执行：8 条负数 → 0，RC=0（终态断言通过）

## 四、回归防护

- [x] T4.1 API 级 E2E：负数断言由「契约固化（200）」改为「必须 4xx」
- [x] T4.2 API 级 E2E：新增正向钉子「0 → 200 且落库为 0（防过度收紧）」
- [x] T4.3 API 级 E2E 全绿
- [x] T4.4 UI 级 E2E 回归全绿

## 五、文档

- [x] T5.1 `proposal.md`（Why / What / 影响面 / 明确排除项）
- [x] T5.2 `design.md`（双层防御论证 / 防过度收紧 / 存量归一化 / 参考对照）
- [x] T5.3 `specs/claim-liability-config/spec.md`（规约条款）
- [x] T5.4 `verify.md`（验证证据）

## 明确不做

- [ ] T6.1 不改 `liabilityUncapped()` / `liabilityLayer` 的 `<= 0` 判据（读取期兜底，见 design.md）
- [ ] T6.2 不引入独立 `uncapped` 布尔列（列变更属 L3 结构变更，记为后续可选演进）
- [ ] T6.3 不动 `payPercent`（`assertPercent` 已覆盖 0~100）
