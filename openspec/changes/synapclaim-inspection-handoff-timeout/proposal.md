## Why

后督抽检目前只有「登记」没有闭环：打回重办、已支付案件冲正前置、请假/转交/代理关系、以及超时任务回池都缺系统表达；抽检比例也只在文档里写 ≥3%，实际是全局 `@Value` 5% 且不上屏。已归档 v2.3 设计把它列为四批中的批 4。

## What Changes

- 新增 `t_claim_inspection_action`（抽检处置留痕）、`t_claim_user_agent`（代理关系）、`t_claim_task_transfer`（转交留痕）
- 实现抽检打回闭环：打回生成重办任务并留痕；**已支付案件必须先冲正**，否则拒绝打回
- 实现请假 / 转交 / 代理：代理带生效区间，转交 append-only 留痕，任务归属随代理生效
- 实现超时回池扫描：超时未领任务释放归属、回池并落监控
- 抽检比例迁到 `t_feature_toggle` 并上屏；新增 DDL 文档一致性门禁（设计文档引用的列必须存在于 DDL）
- 新增 `INSPECTION_ACTION` 回退开关，关闭后处置动作只登记不生效

## Capabilities

### New Capabilities
- `claim/inspection-handoff-timeout`: 抽检打回闭环与冲正前置、请假/转交/代理关系、超时任务回池、抽检比例配置上屏与处置开关回退

### Modified Capabilities
<!-- 无 -->

## Impact

- 代码：`bone-apps/synap-claim` 抽检/代理/超时扫描、`bone-frontend` 抽检页与代理设置
- 数据：新增 3 张表，增量迁移与清单登记、`bone-init.sql` 同批补齐
- 依赖：批 1（任务表）、批 3（复审状态）；不改变资金三值口径
- 不改网关白名单、不改已封存的 v2.3 设计文档
