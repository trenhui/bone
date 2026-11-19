# 任务：procuraAI 安全治理（批次3）

## 1. 领域层
- [x] 1.1 `GlobalKillSwitch` 三态状态机（STANDBY 缺省全停 / ENGAGED / DISENGAGED，读库即时生效）
- [x] 1.2 `ContentLabel` + `DeliveryDecision`（EXPLICIT/IMPLICIT 缺一阻断，LABEL-01）
- [x] 1.3 `BiasSignal` + `FairnessDecision`（FAIR-01/02 expected 对齐）
- [x] 1.4 `FairnessRule` 确定性偏见检测（所有制/地域/规模 + UNKNOWN fail-closed）

## 2. 端口与应用层
- [x] 2.1 `GlobalKillSwitchStore` 端口 + 受控内存实现
- [x] 2.2 `SafetyApplicationService`（kill switch / 出域校验 / 公平裁决 / 演练快照）
- [x] 2.3 `KillSwitchView` 只读投影；操作人取登录态 fail-closed

## 3. 端点
- [x] 3.1 `SafetyController` 6 端点（读写权限分离 + 能力开关）
- [x] 3.2 请求 DTO（kill switch / content label / fairness，枚举解析 UNKNOWN fail-closed）

## 4. 测试（82 例全绿）
- [x] 4.1 kill switch 5 例 + 双标识 5 例 + 公平规则 6 例
- [x] 4.2 SafetyApplicationService 9 例 + SafetyController 契约 4 例
- [x] 4.3 FAIR-01/02 与 LABEL-01 testSymbol 源码可达；clean test BUILD SUCCESS

## 5. 契约与门禁
- [x] 5.1 openapi 6 端点 + A10.9 豁免（不改 FULL_GATE_KEYS）
- [x] 5.2 全族门禁双绿（align 10/10 / self-test 146/146 / asyncapi / evalset / frontmatter / ci 69/69）

## 6. 交付
- [ ] 6.1 归档 change + 主 spec 同步
- [ ] 6.2 commit + push（fetch 检查并行冲突）
