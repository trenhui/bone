## 1. 配置写 API

- [ ] 1.1 新增 L1/L2/L3 配置受控写端点（`@PreAuthorize` + 租户隔离 + 审计留痕），验证无权限与跨租户请求均被拒 <!-- comet-task:5e145902-7ff6-4ea3-9de7-8c410c0c10f4 -->
- [ ] 1.2 实现只收紧棘轮比较（逐字段、拒绝放宽、允许相等与更严格），验证放宽用例返回错误码且库中值不变、收紧用例读回新值 <!-- comet-task:55bc64de-7930-4876-b4ca-54331cae7971 -->
- [ ] 1.3 新增写 API 开关与关闭回落，验证开关关闭时写请求被拒且治理页回到只读 <!-- comet-task:0448d17d-12ab-4232-98f2-c6fe495a720e -->

## 2. 治理页与初审页

- [ ] 2.1 治理页改为可写并显式标注「仅收紧」，验证 `tsc` / `eslint` 通过且 ID 字段为 `string` <!-- comet-task:bae83e17-83a8-4bfc-90c1-1b330f1fcab3 -->
- [ ] 2.2 初审页上报 AI 建议曝光与裁决（带 `traceId`、AI 参与四态、`ReasonType`），验证曝光计入分母、`blind_label` 与非 `AI_ASSISTED` 裁决被拒 <!-- comet-task:6e5c9061-373e-4f57-aea6-efdced5d0b8d -->

## 3. 回流幂等与合并

- [ ] 3.1 以租户内 `event_id` 实现幂等（同键同载荷返回首次结果、同键异载荷 409），验证重放探针与并发探针 <!-- comet-task:2e0d7615-962a-460e-8cd4-14ddc438f226 -->
- [ ] 3.2 与并行会话在途的 `0124` / `AiFeedback*` 成果合并且不重复建表，验证清单 M1/M2/M3 全绿、启动期无 `TemplateNotFoundException` <!-- comet-task:ed1fb46c-d157-4a41-b8cb-d85c76f6eb29 -->

## 4. 验收与门禁

- [ ] 4.1 跑放开阈值、关闭写 API、盲标拒绝三条负向判据并留存运行期证据，验证报告写入 `docs/superpowers/reports/` <!-- comet-task:3d63c6d3-99f2-46b8-8a66-3faecbfb0b68 -->
- [ ] 4.2 跑仓库原生全量门禁（含错误码双语、造数资产同步、E2E 影响分析），验证红点归零或明确归因为零交集基线项 <!-- comet-task:aacede13-7249-49a9-b491-77ec95147e9c -->
- [ ] 4.3 变异验证「放宽阈值被拒」判据确实会转红，验证门禁有判别力 <!-- comet-task:6264245f-7583-4dcb-8a9a-805078db200a -->
