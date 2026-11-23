---
comet_change: procuraai-human-decision-identity-binding
role: verification-report
canonical_spec: openspec
---

# Verification Report — procuraAI 人工终决身份绑定

日期：2026-11-23
工作区：`dev` @ `8c04f5100d2b98666947aa20030c816507e71eb4`（当前基线）
结论：**PASS**（可进入归档；提交/推送与 owner 决策不在本报告范围）

## 1. 验证范围与规格真源

- 规格真源：`openspec/changes/procuraai-human-decision-identity-binding/specs/procurement/business-module-skeleton/spec.md`（2 Requirement / 6 场景）。
- 设计：`design.md`（含 5 项决策、测试策略、范围外缺口记录）；计划：`docs/superpowers/plans/2026-11-23-procuraai-human-decision-identity-binding.md`。
- 交付文件：源码/测试/契约共 11 项修改 + 6 项新增（不含 OpenSpec 产物与 walkthrough）。

## 2. 构建与测试结果（全绿）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 采购后端全量 | `mvn -o -f bone-apps/bone-procurement/pom.xml test` | **BUILD SUCCESS**；10 模块全 SUCCESS；模块测试合计 438 例 / 0 失败 / 0 错误 / 0 跳过 |
| qualification 模块 | 同上 `-pl bone-procurement-qualification` 等价全量 | 41 例全绿（身份绑定 10 例） |
| sourcing 模块 | 同上 | 40 例全绿（授标身份 5 / 契约 3 / 服务 4 / 仓储 4 / 端口 3 / 架构 13） |
| 前端 tsc | `npx tsc --noEmit -p apps/bone-procurement-app/tsconfig.json` | 通过 |
| 前端 vitest | `npm test --workspace=bone-procurement-app` | 15 文件 / 107 例通过 |
| 前端 ESLint | `npm run lint -- -f json -o ...`（解析 errorCount/warningCount） | errors=0 warnings=0 files=46 |
| 对齐门禁 | `python3 tools/check_procura_alignment.py` | PASS 10/10 |
| 持久化门禁 | `python3 tools/check_procura_persistence.py` | A10.29 PASS（6 表 / 双唯一键 / 9 端口持久化） |
| evalset | `python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py` | RESULT: PASS（结构/能力码/负向/锚点；**不守模型效果**） |
| AsyncAPI | `python3 .../generate_asyncapi.py --check` | PASS 29 条事件零漂移 |
| frontmatter | `python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py` | PASS 33 份 |
| 迁移清单 | `python3 scripts/check-migration-manifest.py` | OK 150 文件 / 143 脚本 / order 连续 |
| Build Guard | `comet guard <change> build --apply -- mvn ... test` | ALL CHECKS PASSED — phase=verify |

## 3. 规格场景逐条核对（6/6）

| 场景 | 实现与测试证据 |
| --- | --- |
| 请求体冒名准入终决被拒 | `ReviewIdentityBindingTest.准入终决请求操作人与登录态不一致时拒绝` → `QualificationController.java:99-103` |
| 匿名或缺少租户时准入终决被拒 | `ReviewIdentityBindingTest.准入终决无登录态时拒绝且不落库` / `登录态身份字段不全时拒绝且不落库` / `登录态复核人空白时拒绝且不落库` |
| 请求体冒名授标被拒 | `AwardIdentityBindingTest.请求体冒名授标被拒且不落库` → `SourcingController.java:70-74` |
| 具名审批人授标成功 | `AwardIdentityBindingTest.具名登录态按权威值授标` + `SourcingAwardByRfxNoTest.存在且具名操作人授标成功并读回状态`（AWARDED + 登录态操作人） |
| 无权限或匿名授标被拒 | 匿名：`AwardIdentityBindingTest.匿名授标被拒且不落库`；无权限：`SourcingControllerContractTest.awardEndpointRequiresRfxApproveAuthority` 锁定 `@PreAuthorize("hasAuthority('procurement:rfx:approve')")`，无权限主体由框架 403 不入方法 |
| AI 结论不得直接授标 | `SourcingAwardByRfxNoTest.AI结论不得直接授标必须为具名人工`：AI/系统主体无具名操作人 → 400，状态保持 BIDDING_CLOSED，无 save；领域 `SourcingProject.awardByHuman` 空白拒绝，全量 main 无 AI 绕过写入 AWARDED |

## 4. 独立审查结论（standard）

- Build 阶段审查：无 CRITICAL；2 项 IMPORTANT 已修复 —— ① 两个 Controller 增加身份 record 字段非空非空白纵深校验并补负向测试；② 截标生命周期缺口记录为 owner/L3 范围外，不在 `awardByRfxNo` 内隐式改状态。
- Verify 阶段最终集成审查：前次修复复核通过；新发现 2 项 IMPORTANT 已修复 —— ① 补齐无权限 403 契约断言与 AI 直接授标负向测试；② 两个 ExceptionHandler 加 `@Order(HIGHEST_PRECEDENCE)` 锁定 400/409 不被全局 handler 抢占。SUGGESTION（跨租户仓储弱断言、reviewer 空白分支、平台管理员切租户取值）前两项已修复；第三项为既有对称行为、非本次回归。
- 最终结论：无 CRITICAL / IMPORTANT 遗留；代码与规格一致，可进入归档。

## 5. TDD 与反假绿证据

- onboarding approve：先写 3 例冒名/匿名/跨租户负向测试 → RED（3 failures）→ 实现 → GREEN。
- `findProjectByRfxNo`：先写租户隔离测试 → RED（Optional empty）→ Adapter 实现 → GREEN；跨租户用例令 tenant2 的 RFx 指向 tenant1 真实项目 id，确保 `findById` 缺 tenant 过滤时必然失败。
- 所有负向用例均带 `verifyNoInteractions` / `never().save()`，锁定"拒绝且不落库"，不是仅断言异常类型。

## 6. 已知边界与未执行项（诚实声明）

- **截标状态推进缺口（范围外）**：生产代码无调用方把 `SourcingProject` 从 DRAFT/PUBLISHED 推进到 BIDDING_CLOSED；正向授标只在已截标项目上可达。该生命周期属 Bid/Award 聚合与契约冻结的 owner/L3 边界，本 change 明确 Non-Goal，未以隐式改状态方式绕过。
- 真实 MySQL E2E、生产 MQ/ACL 不在本 change 范围；evalset 仍 `seededDraft`，不宣称模型效果达标。
- 未做提交/推送（需用户明确授权）；两个未跟踪 `synapclaim-*` change 未触碰。
