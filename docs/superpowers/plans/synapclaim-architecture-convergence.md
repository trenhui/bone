---
change: synapclaim-architecture-convergence
design-doc: openspec/changes/synapclaim-architecture-convergence/design.md
base-ref: 3d43aa238ad243a0e022ed36ff345a5e6e841ece
archived-with: 2026-11-14-synapclaim-architecture-convergence
---

# 实施计划：synapClaim 架构收敛剩余项

<!-- comet-task-authority: openspec/changes/synapclaim-architecture-convergence/tasks.md -->

## 执行约束

- 配置：`build_mode=direct` / `tdd_mode=direct` / `review_mode=off` / `isolation=current`（dev）/ `direct_override=true`。用户已一次性授权连续推进到归档与 PUSH。
- 单一真源：节点拓扑以 `DecisionGraph.canonical()` 为准，行为需求以三份 delta spec 为准，技术取舍以 design.md 为准，任务完成状态只以 tasks.md 勾选为准。
- 红线：L4 项 AI 不执行；M2 不写 `scripts/migration/`；不并入 `synap-claim-oracle`；不动其它 3 个未归档 change；不改 05 契约枚举；不物理移动 500+ 包。
- 每次改动后跑相关单测；提交前跑完整验证组（7.1–7.4）。任何金额差异、门禁失败或文档机检失败即停下修复，不掩盖。
- 失败处理：范围明确的实现缺陷直接在当前 change 修复并复验；Runtime/环境故障按恢复协议处理，不手改状态、不伪造收据。
- 回退：金额与决策链路改动按开关切回旧实现，不热修；文档与门禁改动按提交整体 revert。

## 1. 基线复核与单一真源

- 1.1 复核 1290 例单测 / 12 道门禁 / 文档机检 19 步 / 错误码 52↔52 四条基线，输出留档 <!-- comet-task-ref:6359dced-e410-4eec-8e9d-01673381eb98 -->
- 1.2 建立 20 子域 → 7 上下文机器可读映射，门禁读文件不硬编码 <!-- comet-task-ref:d9ad7c52-0c42-4cfc-9837-fbfac223be2c -->
- 1.3 包归属门禁固定 `quota` 归 Funds，改坏映射须转红 <!-- comet-task-ref:943f1548-affd-48b5-8b9d-5558729e487c -->

依赖：无。验收：JSON 可解析、门禁单测含负向探针。

## 2. 决策图与流程层

- 2.1 canonical 单一真源断言（顺序 / 类型 / 白名单） <!-- comet-task-ref:942cd9b4-a6b9-4065-9cb6-b02a847b90a0 -->
- 2.2 Stage → FlowFireService → Trigger → Condition 骨架，求值异常 fail-closed <!-- comet-task-ref:548e125a-892b-43cc-9538-fac7f3c20bdf -->
- 2.3 两个回放端点改显式 `DecisionTraceResp` + Assembler，禁止领域对象直出 <!-- comet-task-ref:c4465b6f-6c64-4213-9b00-bd054fab074b -->
- 2.4 落地版 §7.3 与总体技术方案 §0 改为「现状 + 待评审扩展阶段」分层登记 <!-- comet-task-ref:1b12be98-fcea-4882-b1eb-eb1e7a72e1d3 -->

依赖：1.1。验收：决策图单测、Assembler 映射单测与反射负向钉子、`_check_numbers.py` / `check_all.py` 全绿。高风险点：轨迹响应是 BREAKING 变更，字段只收窄租户与审计列，E2E 回放断言须保持通过。

## 3. M5b 脚本沙箱

- 3.1 受限求值器端口与声明式白名单操作符 <!-- comet-task-ref:40c29810-5462-42d4-b852-a309f5f57a82 -->
- 3.2 超时 / 步数 / 标识符白名单，阻断文件、网络、反射、类加载 <!-- comet-task-ref:304e5a3e-10f3-496b-9606-b89c046a9175 -->
- 3.3 沙箱不可用 fail-closed 且不写业务状态 <!-- comet-task-ref:c9c9e44c-d781-40f8-8e37-2ec0366fabad -->
- 3.4 诊断报告与总体技术方案登记实际状态，区分现状 / 目标态 <!-- comet-task-ref:7a51ad8f-41bd-4f26-967a-bdb7566479a2 -->

依赖：2.2。验收：四类越界负向探针全绿、异常注入断言无状态变更。高风险点：不启用 JVM 全局 `SecurityManager`，不引 Aviator。

## 4. 理算引擎三策略与三模块边界

- 4.1 抽策略接口与三策略实现、枚举分派，未知类型明确拒绝 <!-- comet-task-ref:79c5155d-cd3a-4a12-9a40-b4ae7e9c215a -->
- 4.2 双轨对拍 / 缺口哨兵 / 变异鉴别三轨锁金额等价 <!-- comet-task-ref:528a034b-592d-457e-89c6-bb9d1ce97eee -->
- 4.3 旧 `AdjustmentEngine` 标 `@Deprecated` 并提供回退开关 <!-- comet-task-ref:7278f9e3-0e9a-4157-a978-b7585c0800df -->
- 4.4 SDK 落纯 Java 契约，引擎落纯函数内核，两模块独立编译通过 <!-- comet-task-ref:336715a1-e7dc-44d3-abd8-0c6cec96bb28 -->
- 4.5 依赖边界机检（SDK 零框架依赖、引擎无 Spring / 元数据 SDK） <!-- comet-task-ref:a2bc813b-c089-48ee-9d3d-50cb1d02579a -->

依赖：1.1。验收：`Adjustment*Test` 全绿且人为改金额时对拍失败；开关两路均可执行。高风险点：4.1–4.4 改金额链路，必须保留旧实现与开关，禁止一边搬模块一边改算法。

## 5. 响应契约与文档治理

- 5.1 全量扫描 Controller 响应泛型，补齐剩余 `*Resp` / Assembler <!-- comet-task-ref:0483454a-eeb8-40d8-8138-38b6b7673033 -->
- 5.2 `AI技术方案.md` 瘦身，版本历史与实施日志外置并登记 manifest / AI-INDEX <!-- comet-task-ref:0bc24bf3-e8d3-440f-8169-543306656130 -->
- 5.3 回写诊断报告、整合历史 P38、文档地图与全部数字口径 <!-- comet-task-ref:0b509222-6ae2-4b55-9600-968a84653315 -->
- 5.4 新增错误码时同步常量 / HTTP 映射 / zh-CN / en-US <!-- comet-task-ref:67b7dd6d-974c-47c4-ba68-4b7e6dde7273 -->

依赖：2.3。验收：响应契约机检零违规；`_check_xref.py` / `_check_contract_drift.py` / `_check_ddl.py` / `_check_numbers.py` / `check_all.py` 全绿。

## 6. L4 人工窗口交付物

- 6.1 M2 责任要素建议表设计与 R1–R8 待拍板清单，`scripts/migration/` 零新增 DDL <!-- comet-task-ref:f82a2d44-5216-415f-b74c-416cfcc08858 -->
- 6.2 D4 物理重构、05 契约枚举扩展、扩展链路真实接入登记为独立后续 change <!-- comet-task-ref:dd07fd68-a218-488a-a407-39e8365819c6 -->

依赖：无。验收：design.md 附录 A 有可逐项核对条目，措辞机检无「已落地」误述。这两项**不产生生产代码**，Q3 要求的强制全量部分（DDL / 物理移包 / 伪造节点）按选项自带约束交人工窗口。

## 7. 全量验证

- 7.1 `mvn -o -q -pl bone-apps/synap-claim/synap-claim-app spotless:apply test` 零失败，测试数不低于 1290 <!-- comet-task-ref:f3ee49eb-a585-45ca-9483-e2f3c618975c -->
- 7.2 `./scripts/check.sh` 12 道门禁全 PASS <!-- comet-task-ref:f0853ff4-641a-45db-a9f5-472c54bfaaca -->
- 7.3 文档机检四件套 + `check_all.py` + 错误码 / i18n 机检全绿，无新增 baseline 例外 <!-- comet-task-ref:3e175e24-4448-43ca-8338-081c9dcb115f -->
- 7.4 `mvn -o -q -pl bone-apps/synap-claim/synap-claim-app -am clean test` 核对提交树，排除增量编译假绿 <!-- comet-task-ref:2afa2e76-9f60-4d46-a6a2-e647253af87c -->

依赖：1–6 全部完成。验收：命令实际执行且输出留档；任一失败回到对应任务修复后复验。

## 8. 交付

- 8.1 确认 `dev` 分支，按用户 Q2 授权 `git add -A` 全量提交；`git branch --contains <sha>` 验证落点 <!-- comet-task-ref:93e2fadc-818b-4c6d-9e36-b2062abc9965 -->
- 8.2 `git push origin dev`，核对与 `origin/dev` 同步 <!-- comet-task-ref:78b0302c-26a2-4fea-9fac-95d6e6e4c65f -->

依赖：7.1–7.4。验收：工作区干净、远端包含该提交。高风险点：并发提交者已暂存文件按授权一并纳入，提交信息分类列出，失败按 SHA 回退，不 `--amend` 掩盖。
