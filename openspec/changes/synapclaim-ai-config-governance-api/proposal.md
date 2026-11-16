## Why

AI 自动初审的 L1/L2/L3 配置目前只存在于文档与静态配置，治理页只读、无法受控调整；AI 建议的曝光与人工裁决没有服务端回流，采纳率缺少分母，阈值是否被真实执行也无法审计。已归档 v2.3 设计把它列为四批中的批 2。

## What Changes

- 新增 L1/L2/L3 配置的受控写 API（功能权限 + 租户隔离 + 审计留痕），阈值**只收紧不放开**（棘轮），拒绝任何放宽写入
- 治理页从只读改为可写，并显式标注「仅收紧」；开关关闭即回落只读
- 新增 AI 建议曝光与采纳回流：曝光是采纳率分母，裁决带 `traceId`、AI 参与四态、`ReasonType` 四值；只有 `AI_ASSISTED` 且非 `blind_label` 场景可裁决
- 事件重放按租户内 `event_id` 幂等：返回首次结果，同键不同载荷 409
- 复用 `t_feature_toggle`，本批不新增业务表

## Capabilities

### New Capabilities
- `claim/ai-config-governance`: AI 三层配置的受控写入、只收紧棘轮、治理页可写与关闭回落，以及 AI 建议曝光/裁决服务端回流与幂等

### Modified Capabilities
<!-- 无 -->

## Impact

- 代码：`bone-apps/synap-claim` 配置写 API 与回流端点、`bone-frontend` 治理页与初审页
- 数据：复用 `t_feature_toggle`；并行会话在途的 `0124_synap_claim_ai_feedback_event.sql` / `AiFeedback*` 属本批回流能力，本 change 等其入库后基于成果续做，**不重复实现**
- 依赖：批 1（任务归属用于归因）；批 3/4 不依赖本批
- 不改网关白名单、不新开 `/api/ai/v1/**`、不引入 `AUTO_REJECT`、不改已封存的 v2.3 设计文档
