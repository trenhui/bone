# 任务：procuraAI AI 决策治理（批次1）

## 1. 模块与骨架
- [x] 1.1 父 pom 新增 `bone-procurement-ai-governance` 模块
- [x] 1.2 DDD 四层包骨架 + package-info + pom 依赖（不含 AI 底座）
- [x] 1.3 ArchitectureTest 架构门禁（13 条规则，含分层/端口/权限码/跨上下文守护）

## 2. 领域层
- [x] 2.1 `CapabilityState` 五态 + `allowsInference()` / `isIndeterminate()`
- [x] 2.2 `CapabilityCode` / `OwnerRef` 值对象（空值 fail-closed）
- [x] 2.3 `AiCapability` 聚合：状态机 + 具名责任人 + 不可变追溯链
- [x] 2.4 `GovernanceDecision` 值对象（ALLOWED_ADVISORY_ONLY + 拒绝 reasonCode）
- [x] 2.5 `HumanFinalDecision` 值对象（终决权恒人工 / override 识别 / 具名+耗时强校验）
- [x] 2.6 `CapabilityGovernancePolicy` 纯领域裁决策略
- [x] 2.7 四个出站端口 + 审计追溯条目

## 3. 应用层
- [x] 3.1 能力开关属性 + 组合装配条件（默认 false）
- [x] 3.2 `register / decide / changeState / recordHumanDecision` 四用例
- [x] 3.3 `GovernanceActorPort` 登录态操作人 fail-closed
- [x] 3.4 `CapabilityView` / `HumanDecisionView` 只读投影（不回传 output）

## 4. 基础设施与端点
- [x] 4.1 受控内存 registry / ledger / trace 适配器
- [x] 4.2 `AiGovernanceController` 8 端点（读写权限分离 + 能力开关）
- [x] 4.3 登录态操作人端口（JwtPrincipal / 匿名拒绝）

## 5. 测试（53 例全绿）
- [x] 5.1 领域状态机 10 例 + 裁决策略 7 例 + 人工终决 7 例
- [x] 5.2 应用服务 9 例（含登录态 fail-closed / override 回流 / 留痕）
- [x] 5.3 Controller 契约 4 例 + 开关门禁 3 例 + ArchUnit 13 例
- [x] 5.4 `clean test` BUILD SUCCESS

## 6. 契约与门禁
- [x] 6.1 openapi.yaml 新增 ai-governance tag + 8 端点
- [x] 6.2 门禁 `EXTRA_OPENAPI_ENDPOINTS` 豁免登记（源码先行，不改 FULL_GATE_KEYS）
- [x] 6.3 权限种子 0129（读/写两码 + 角色绑定，号段 940... 幂等）
- [x] 6.4 本族门禁双绿（alignment 10/10 + self-test 146/146 + manifest + asyncapi 29 + evalset + frontmatter 33 + ci 69/69；负向探针注入转红/还原转绿）（alignment/self-test/manifest + asyncapi + evalset + frontmatter）

## 7. 交付
- [x] 7.1 归档 change + 同步主 spec
- [ ] 7.2 commit + push（fetch 检查并行冲突）
