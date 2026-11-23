# Tasks

## 1. 登录态身份端口与准入终决

- [x] 1.1 在 sourcing 域新增只读授标身份端口并在 infrastructure 实现认证上下文解析；用匿名/已认证两组单测验证返回 null 与具名操作人。 <!-- comet-task:864f4bcf-8752-4406-bf4b-1a03bf026b39 -->
- [x] 1.2 将 `POST /onboardings/{no}/approve` 改为从 `ReviewIdentityPort` 获取租户与操作人，请求体同名字段只做一致性校验；用跨租户、冒名、匿名三类 Web 层测试验证拒绝且不落库。 <!-- comet-task:d9c5d39e-c6a5-49ac-aece-12aaa635cdb1 -->

## 2. RFx 人工授标 HTTP 入口

- [x] 2.1 增加按 `rfxNo` 只读定位寻源项目的仓储查询，并让 `awardByHuman` 支持可信租户/操作人参数；用存在、不存在、空白操作人三类服务单测验证状态与不变量。 <!-- comet-task:fd36e285-917e-4c5a-9a6a-fd80dab94784 -->
- [x] 2.2 新增 `POST /api/v1/procurement/rfx/{rfxNo}/award`，复用 `procurement:rfx:approve`，确认人只取登录态；用具名成功、无权限/匿名、请求体冒名、AI 直接授标四类 Web 层测试验证 200/403/400 与持久化状态。 <!-- comet-task:049c3659-4076-452f-b24b-131a3b5057b5 -->
- [x] 2.3 同步 `openapi.yaml` 的授标端点说明为“确认人来自登录态”，删除权威 `confirmed_by` 语义；用现有契约/对齐门禁验证零漂移。 <!-- comet-task:c767ca7e-f854-438c-8434-5aadc10424b6 -->

## 3. 集成验收

- [x] 3.1 运行 qualification 与 sourcing 模块全量测试，验证无失败、无跳过回归，并确认所有写操作均有持久化读回或拒绝后状态不变的断言。 <!-- comet-task:49853118-45ea-45c2-b8ac-c5b53f0a1e10 -->
- [x] 3.2 运行采购前端 TypeScript、vitest、ESLint JSON 门禁，验证本 change 未破坏前端构建与行为测试。 <!-- comet-task:b9bf88be-83fc-45cc-885f-69ccbbde99c1 -->
- [x] 3.3 运行 procuraAI 对齐、evalset、AsyncAPI、persistence 门禁，验证 OpenAPI 文案同步未触发 A8/A10 契约漂移。 <!-- comet-task:b5b76d5b-7e13-4112-895a-41f010ea1a74 -->

## Workflow follow-up

- 完成 Comet Verify 的唯一最终集成审查后再归档。
- 仅在用户明确授权时执行提交与推送。
