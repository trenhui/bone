## Why

复审目前与初审、44 双人复核共用同一套状态与权限，无法表达「独立复审」这一阶段，职责分离（同人不可自审）与复审队列都缺位；质检结论也无法归因到具体复审任务。已归档 v2.3 设计把它列为四批中的批 3。

## What Changes

- 新增独立复审状态 `43`（待独立复审）与 `46`（独立复审通过/处置终态），与 `44` 双人复核并列，不合并
- 新增权限码 `claim:re-review:write` 与复审队列（按任务归属过滤，复用批 1 的 `t_claim_task`）
- 实现职责分离：同一案件初审人与独立复审人不得为同一人，命中即拒
- 新增 `RE_REVIEW` 回退开关，关闭后 43/46 零流量
- 新增错误码并按仓库铁律三处齐：`ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS` + 前端两语言包，台账 §6 同批登记

## Capabilities

### New Capabilities
- `claim/independent-re-review`: 独立复审状态 43/46、专属权限码、复审队列、职责分离约束与开关回退

### Modified Capabilities
<!-- 无：44 双人复核行为不变 -->

## Impact

- 代码：`bone-apps/synap-claim` 状态字典 / 权限码 / 复审队列端点、`bone-frontend` 复审页与两语言包
- 数据：`t_claim_review_record` 与状态字典登记；本批不新增大表
- 依赖：批 1（任务归属）；批 4 依赖本批
- 不改网关白名单、不改既有审核状态机、不改已封存的 v2.3 设计文档
