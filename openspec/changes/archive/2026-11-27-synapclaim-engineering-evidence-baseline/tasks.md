# 工程证据底座 · 任务清单

## 1. 证据视图与决策轨迹

- [x] 1.1 新增 `EngineeringEvidenceService`，输出含稳定判据 ID / 状态 / 原因码的只读证据视图，覆盖 fail-closed 落点、不可达维度、provider 口径、能力声明一致性；以单测验证结构化输出可机检且无业务状态写入。 <!-- comet-task:bb09dcd6-717a-4545-a84b-2fa50a00f083 -->
- [x] 1.2 在决策执行轨迹的 `MANUAL_REQUIRED` 记录中补充可查询 fail-closed 原因码（视觉桩 / 规则桩 / provider 未配 / knowledge 不可用）；以既有关卡测试验证节点拓扑、决策结果、跳过集合均不变。 <!-- comet-task:53a88480-38d6-4ef0-a469-9c6217ba12d7 -->
- [x] 1.3 暴露只读查询入口（Controller 返回 `ApiResponse`）；以 Web 层测试验证只读语义与原因码字段，不新增写接口。 <!-- comet-task:081b118d-4551-4592-a831-8828c7ec0731 -->

## 2. 不可达维度显式拒绝

- [x] 2.1 在 `LimitedConditions` 判定入口检测区域/医保/药品/诊疗四类无配置列维度，返回稳定原因码的显式拒绝；复用已有错误码或按三处齐备规则新增（ClaimErrorCodes + DEFAULT_HTTP_STATUS + 两语言包 + 台账）；以单测验证拒绝、原因码与「拒绝不改变金额语义」。 <!-- comet-task:7f273004-4118-4b61-be1e-d6fd3f18dfa5 -->
- [x] 2.2 以变异测试钉死：医院/病种两已支持维度按既有确定行为比对、不受影响；去掉拒绝判据即翻红。 <!-- comet-task:844afc07-ceb3-45b5-a4e7-92ff8267e8b3 -->

## 3. provider fail-fast

- [x] 3.1 在自动放行前置校验读取 AI 网关 base-url，缺失时以可诊断错误 fail-fast、决策保持 `MANUAL_REQUIRED`；复用 `AiRuntimeHealthLocator` 口径，禁止桩冒充模型；以单测验证无 provider 时不进入放款路径。 <!-- comet-task:c3519c2f-e44c-4993-984c-a5cbd4a2b511 -->
- [x] 3.2 以 E2E 负向钉子验证无 provider 时主链仍走 MANUAL_REQUIRED 而非中断；运行态产物如实标注桩口径与真实参与率。 <!-- comet-task:790db1cd-a4df-44a0-a495-acb434b4a210 -->

## 4. 门禁与验收

- [x] 4.1 在 `scripts/check.sh` 接线新增判据（带 `--check`），并在 `.comet/check-policy.json` v2 登记（argv 精确匹配 + outputs）；`ai-native-readiness` 12 项判据不放宽、证据视图冲突取保守值，以自检负向探针验证。 <!-- comet-task:d4ec56fd-ed9f-46f6-a838-ae7e246a3844 -->
- [x] 4.2 全部新增判据通过变异验证：移除接线/改回默认姿态时判据翻转、仅改状态字面串不翻绿。 <!-- comet-task:aedcd5e4-59ea-440e-8759-4e1db1bef6aa -->
- [x] 4.3 改 Java 后执行 `mvn spotless:apply`；跑 `./scripts/check.sh` 与 synapClaim 专项 E2E，确认 exit 0 且末行「✅ 本地自检通过！」，无资金行为变更、无假绿。 <!-- comet-task:eed3af34-4eb1-443f-99fb-3634f707d185 -->
