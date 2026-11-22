# Tasks

## 1. 装配一致性与启动探针

- [x] 1.1 给 `AdvisoryController` 与 `AdvisoryApplicationService` 加 `@ConditionalOnAiGovernanceEnabled`，与治理 Bean 同条件；用 `ApplicationContextRunner` 双态测试断言：默认关闭时三者 Bean 均不存在、开启时各恰好一个且依赖可满足（`mvn -o -f bone-apps/bone-procurement/pom.xml test` 相关测试通过） <!-- comet-task:2ad3372a-fc4e-4a70-841e-21026e7871ef -->
- [x] 1.2 确认 `bone-procurement-app` 默认配置下 `ProcurementApplication` 可无 Bean 缺失启动；记录启动日志与条件报告（启动命令成功、`ConditionEvaluationReport` 中 advisory/治理条件一致） <!-- comet-task:217e9b37-cd95-49d5-83cd-190574f4ae75 -->

## 2. Mock 身份降级

- [x] 2.1 在 `ContractReviewPortAdapter.review()` 的 `gateway.invoke()` 后、`parseSuggestion` 前识别 mock 身份（响应 `mock:true` / provider 名含 mock），追加 `MOCK_PROVIDER_REJECTED` trace 并走 `degrade()`；单测断言 mock 响应返回 `UNKNOWN`、不产生 `AVAILABLE`、trace 有原因码（`ContractReviewPortAdapterTest` 新增用例通过） <!-- comet-task:70494619-94a0-4efc-a62d-49a13ddd2477 -->
- [x] 2.2 未配置 provider 时在启动日志与 readiness 输出显式提示"Mock/未配置真实 provider"；负向测试断言不静默回退为可用（日志/响应断言通过） <!-- comet-task:49e8fe71-9d26-4f1f-8271-08ee1b361396 -->

## 3. 就绪度只读端点

- [x] 3.1 新增 `AiReadinessApplicationService`（只读聚合 provider 配置态、能力注册态、总闸态、评测态）与 `GET /api/v1/procurement/ai-readiness` Controller，判词 `AVAILABLE/DEGRADED/UNKNOWN`，响应含部署门禁与授标/放款许可分离说明；单测覆盖四类事实组合与未知 fail-closed（新增测试通过，`ApiResponse` 包装一致） <!-- comet-task:0003c198-edf2-4a27-acc1-8ab99a1468f6 -->
- [x] 3.2 为 readiness 端点写 Web 层测试：默认配置不返回 AVAILABLE、评测未转正不声称达标、取数失败返回 UNKNOWN、未知判词原样透传（测试通过） <!-- comet-task:32006124-4189-4b82-b3dd-d18700968d30 -->

## 4. OCR 人工复核

- [x] 4.1 在 `Qualification` 实体实现 `reviewByHuman(reviewer, accurate, now)` 幂等领域方法；`QualificationApplicationService.reviewOcrByHuman` 改为 findById → 领域方法 → save，替换 `UnsupportedOperationException`；新增Controller 端点；持久化读回测试断言结论一致（测试通过，方法不再抛未实现异常） <!-- comet-task:8f888c67-8b45-40dd-bb26-a4e11056631e -->
- [x] 4.2 为复核补负向测试：缺租户/跨租户拒绝、重复提交幂等不新增记录（测试通过） <!-- comet-task:cef1c366-5df4-461c-b134-ad9ce60597e9 -->

## 5. 前端页面与反假绿

- [x] 5.1 在 `bone-procurement-app` 新增 `AiReadiness`、`HumanReviewConsole`、`GovernanceStatus` 三页并挂路由，复用现有 services/useMessage；未知判词显示原始码、取数失败显示"无法确认"、AI 建议不渲染为已生效（组件测试断言通过，`tsc` 通过） <!-- comet-task:911fdaf2-d458-4503-8bf2-8a96801b4fab -->
- [x] 5.2 前端质量门禁：eslint 以 `-f json` 解析 `errorCount=0`、`vitest` 用例数不低于现有基线且 0 失败（命令输出可核验，不看 stylish 退出码） <!-- comet-task:062ccbde-0c72-4c5d-9e4f-159279ee9a4f -->

## 6. 门禁同步与负向探针

- [x] 6.1 若新增机器判据，按 A10.17 同步 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` + 判据常量 + 注入探针；若无需新增判据则在报告中说明"现有 A10.17 三入口集合未变"（自检探针全绿，注入必红/还原必绿） <!-- comet-task:769f4cd2-f59f-40b2-acaf-f190b33c4887 -->
- [x] 6.2 跑 procuraAI 主门禁与自检：`check_procura_alignment.py` / `check_procura_persistence.py` / `check_procura_evalset.py` 及 `--self-test` 全绿（命令实际执行且退出码 0） <!-- comet-task:dd41eea0-6b9d-4892-9958-2a90f95a1a08 -->

## 7. 真实前后端联调与验收

- [x] 7.1 本地真实启动后端（H2/内存或已确认的本地 MySQL 配置）与前端 dev server；用真实浏览器打开 readiness 页、进入人工终决页执行一次采纳/驳回并刷新；截图与日志作为证据（浏览器可访问、页面渲染判词与终决结果） <!-- comet-task:bb72099c-2248-47ee-9050-4446541584ba -->
- [x] 7.2 对写操作以数据库直查或持久化读回作第二证据，并断言 Mock/未配 provider 场景不出现 AVAILABLE（落库证据存在、API 与 DB 一致） <!-- comet-task:ef4c55d2-c516-4e25-a4bf-ad54481a83bb -->
- [ ] 7.3 运行完整验证与独立审查，产出 Verify 报告并归档（报告 PASS，无 CRITICAL/IMPORTANT，归档完成） <!-- comet-task:b1dc3a0f-a022-4e1a-8eba-7fd549a3dccc -->

## Workflow follow-up

- 归档后按用户授权决定是否提交与推送；不触碰两个未跟踪的 synapclaim 目录。
- 遗留 owner 事项（法务结论、评测转正、契约冻结、真模型、Outbox/MQ）在报告中列明，不伪造完成。
