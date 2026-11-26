## 1. 后端领域模型与持久化

- [x] 1.1 新建签收聚合 `ClaimSignBatch`、值对象 `SignSource/SignType/SignChannel/SignStatus`、子集合 `SignBatchItem/SignTrackLog`（domain 零框架依赖），编译通过且领域单测覆盖四态迁移校验 <!-- comet-task:f18a25ba-0cef-4ef6-acf3-21709798cde3 -->
- [x] 1.2 新建仓储接口 `ClaimSignBatchRepository`（含 CAS 条件更新方法，方法名禁 insert/update/delete/persist），提供全部 `sql/.../<method>.sql` 模板（首行为语句关键字、无注释），启动无 TemplateNotFoundException <!-- comet-task:a32d3510-a69b-49a2-a038-b0566ad3bc8f -->
- [x] 1.3 编写迁移脚本 `scripts/migration/<序号>_claim_secure_signing.sql`（三张表+唯一索引+枚举约束）与 `bone-init.sql` 基线同步，本地迁移验证通过且不触碰生产库 <!-- comet-task:478862cb-de8b-4d2f-add5-f3d80887790c -->

## 2. 后端应用用例

- [x] 2.1 实现 `AutoSignApplicationService.signOnAcceptance`（内部报案末端调用，source=ONLINE/signType=AUTO，写轨迹，事务 REQUIRED），单测覆盖成功、幂等重复、要素缺失三类场景 <!-- comet-task:62dc8068-d227-41eb-832f-bc6dd0156a78 -->
- [x] 2.2 实现外部推送自动签收用例（业务唯一标识去重，signChannel 记录推送方），单测覆盖同 businessKey 重复推送幂等返回首次结果 <!-- comet-task:bb89f111-9be3-4566-8790-4e73b1a3427f -->
- [x] 2.3 实现线下批次用例：创建批次、导入名单（生成明细、阶段 SIGNING）、登记影像、确认签收（CAS + 影像完备 + 意见校验 + 阶段推进 PRE_EXAM + 轨迹），单测覆盖四态流转与终态/跳步/意见过短拒绝 <!-- comet-task:c9876735-0e29-43f7-9625-ae05d5bd6657 -->
- [x] 2.4 实现批次查询与轨迹查询用例（服务端真分页、按来源/状态/渠道筛选），单测覆盖筛选与分页边界 <!-- comet-task:7d5184c2-96c8-4c73-95bd-81ca873d6d08 -->

## 3. 后端适配器与契约

- [x] 3.1 新增 `ClaimSignController` 端点（回执、批次 CRUD、名单导入、影像登记、确认、轨迹）与 `assembler/` 中显式 `*Resp` DTO 装配，机检无领域对象直出、装配收敛 <!-- comet-task:d761cafa-ae88-40d9-9959-6a63c6241bef -->
- [x] 3.2 新增签收错误码并同步 `ClaimErrorCodes` + `DEFAULT_HTTP_STATUS` + 前端中英文语言包 + 错误码台账 §6 四处，冲突类错误映射 409 <!-- comet-task:67049349-5371-430b-8207-be0876c0d052 -->
- [x] 3.3 写端点接受 `Idempotency-Key`（≤64 字符校验，稳定键同键返回首次结果），契约测试覆盖超长拒绝与重试幂等 <!-- comet-task:1a7ef5fb-e658-4846-ba71-5a3645355e8d -->

## 4. 前端页面与 API

- [x] 4.1 新建 `src/services/claimSign.ts` 与类型（复用 `createApiClient('/api/v1/claims')`，写动作带稳定幂等键 ≤64），单测覆盖键归一化与响应解包 <!-- comet-task:f878b77c-28b9-4308-8811-30f5b58e7be4 -->
- [x] 4.2 新建 `src/pages/CaseSignPage.tsx`（具名导出）：线上/线下 Tab、批次列表（复用 useClaimQuery/ClaimQueryFields/ClaimQueryBar/Table/normalizeTotal）、新建批次、名单导入、影像入口、二次确认、轨迹抽屉，组件测试覆盖四态渲染与确认防抖 <!-- comet-task:49d5561f-38c0-4121-99e1-466a87b213b0 -->
- [x] 4.3 `routes.tsx` 追加 visible 路由 `/sign`（名称「案件签收」）+ `App.tsx` 权限守卫 + `constants/permissions.ts` 权限码，同步 IAM 菜单迁移 SQL（`/sclaim/claim/sign`，名称/顺序逐字一致），单导航 E2E 探针通过 <!-- comet-task:09f2c572-8f30-4dd7-b912-8df1b12261dc -->

## 5. 集成验证与收口

- [x] 5.1 本地跑通双触发（内部报案自动签收 + 外部推送幂等签收）与线下四态全链路，记录真实请求/响应与轨迹 <!-- comet-task:da303d2e-1e3f-431f-b99d-d0801ab2d328 -->
- [x] 5.2 跑前端 typecheck、单测、构建与 `./scripts/check.sh` 全量门禁（沙箱测试命令按项目纪律处理），结果填入 Verify 报告 <!-- comet-task:b29baa2b-06c5-422c-96b9-04b16b41b8bc -->
- [x] 5.3 跑 `claim-single-nav-probe.mjs` 验证菜单↔路由双向全等与顺序，确认无幽灵菜单/不可达功能/名称漂移 <!-- comet-task:f78053b1-a0b0-45d5-928a-47089e697436 -->
