# Bone AI 原生工程基础设施 — 门禁/校验套件联调验证报告

> 日期：2026-11-11
> 关联文档：`doc/architecture/Bone-AI原生工程基础设施改造方案.md`（v3 + 11.6 深化）
> 目的：基于业界最佳实践，对方案中**已落地工程资产**做端到端联调，验证前后块协同通过。

## 1. 联调范围界定

方案 v3 + 11.6 描述了 9 项改造任务（T1–T9）与门禁族（LIV-R1–R8/R9…）。
其中**已落地、可联调**的是门禁/校验工具族（`scripts/check-*.py`）；
**未落地、暂不可联调**的是 11.6 描述的 T2 图谱路由 / T4 影响面 / T6 度量 / T7 路径规则——它们仍属 L4/L3 建议稿，无对应可执行代码。

故本联调以「已落地资产构成的四组衔接闭环」为对象：

- 文档 ↔ 代码符号单一真源
- DDD 文档 ↔ 门禁状态一致
- 前端契约 ↔ 后端 envelope / 路由 / schema 同步
- 门禁族自身活性（防 blind gate）

## 2. 联调套件与结果

| # | 脚本 | 闭环组 | RC | 关键结论 |
|---|------|--------|----|---------|
| 1 | `check-gate-liveness.py` | 门禁族活性 | 0 | 分支 dev 在触发器内；blocking 门禁 41 条（基线 15）；空壳 0；62/64 已接线（3 个 CI-only + 2 个孤儿豁免，均已在 allowlist 登记） |
| 2 | `check-doc-code-symbols.py` | 文档↔代码 | 0 | 3692 个 Java 类无未登记符号漂移 |
| 3 | `check-ddd-doc-drift.py` | DDD↔门禁 | 0 | 1 个 DDD 文档防漂移通过（v4 编号 / 平台 API / 相对链接 / 内部锚点；白名单 125 符号） |
| 4 | `check-ddd-gate-state.py` | DDD↔门禁 | 0 | 门禁状态通过（编号唯一 / 锚点可解析 / 覆盖率阈值一致 / 规则名真实 / 状态真源版本一致；Known-missing 登记 2 条） |
| 5 | `check-frontend-response-contract.py` | 前端↔后端契约 | 0 | 扫描 284 个前端源文件；R1/R2 无违规 |
| 6 | `check-frontend-route-param.py` | 前端↔后端路由 | 0 | [G31] 路由 `:id` 绑定链完整（数据 Tab 不会静默串实体） |
| 7 | `check-ui-schema-sync.py` | 前端↔后端 schema | 0 | 契约一 doc ↔ 后端 classpath 一致（sha256 ab1bc3e7…）；契约二 前端 lint 白名单 ↔ 协议 props 一致（28 项） |

**结论：7/7 全绿，联调通过。**

## 3. 业界实践对照

- **门禁活性（blind gate 防呆）**：对应 11.6.5 引用的 mutation testing / gate liveness 原则——`check-gate-liveness` 断言的是「机制存活」而非「某次具体配置」：分支漂移、blocking 计数下降、空壳步骤、孤儿门禁均判红。它将「全绿 ≠ 有判别力」落成了可验证机制。
- **前端契约前置校验（shift-left）**：R1/R2 响应解包契约、`:id` 绑定链、schema 双契约在**提交期**即拦截前后端不一致，符合「测试左移 / 契约测试」最佳实践，避免联调期才暴露。
- **文档 ↔ 代码单一真源**：`check-doc-code-symbols` + `check-ddd-doc-drift` 把「文档引用的符号必须真实存在」固化为门禁，呼应第九章单一真源口径。

## 4. 边界与遗留

- 11.6 的 T2 / T4 / T6 / T7 仍属建议稿，未落地代码，**未纳入本次联调**；其联调须待授权落地后补做。
- 门禁「会拦」的判别力（负向探针 + 变异验证）属于 11.6.5 后续 L3 落地项，本次仅验证「全绿通过」，未构造负向用例。
- 业务系统级前后端实际跑通（Bone 后端 8081 + 前端 shell :3000）属另一范围，不在本方案模块联调内。

## 5. 收口状态（2026-11-11 11:03）

- [x] 提交本轮 11.6 方案深化 + 本报告：`744418897`（pre-commit 全绿，「✅ 本地自检通过！」）
- [x] PUSH `dev` → 双远程（Gitee + GitHub）成功
- [x] 合并 `master`：fast-forward 至 `744418897`，双远程 PUSH 成功；四引用（dev/origin/dev/master/origin/master）一致
- [ ] 落地 T2/T4/T6/T7 后再做一轮联调（待 L3/L4 授权）

## 6. 合并 master 后复跑结论（2026-11-11 11:03）

在 `744418897` 已合并 `master`、四引用一致后，**重新实跑 §2 联调套件**，确认无回归：

| # | 脚本 | RC |
|---|------|----|
| 1 | `check-gate-liveness.py` | 0 |
| 2 | `check-doc-code-symbols.py` | 0 |
| 3 | `check-ddd-doc-drift.py` | 0 |
| 4 | `check-ddd-gate-state.py` | 0 |
| 5 | `check-frontend-response-contract.py` | 0 |
| 6 | `check-frontend-route-param.py` | 0 |
| 7 | `check-ui-schema-sync.py` | 0 |

**结论：7/7 全绿复跑通过，合并 master 未引入回归。** 各脚本关键结论与 §2 首次联调一致（门禁 62/64 接线、3692 Java 类无漂移、DDD 防漂移通过、前端 284 文件 R1/R2 无违规、[G31] 路由绑定链完整、schema 双契约一致）。

## 7. 关联 change 归档

`openspec/changes/ai-native-platform-plan-review`（8/8 任务完成，纯文档/评审类、skip_specs）已按 OpenSpec/Comet 流程归档至 `openspec/changes/archive/2026-11-11-ai-native-platform-plan-review`。
