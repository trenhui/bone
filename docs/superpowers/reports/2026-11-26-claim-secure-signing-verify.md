# Verify: claim-secure-signing（案件签收）

> 验证对象：线上自动签收（内部报案 + 外部推送）与线下人工批次签收的全链路实现。
> 主证据：后端 1711 测试全绿、前端 284 测试全绿、`check.sh` 59/59 全绿、
> API 级双触发/四态联调实跑、UI 级单导航探针 143/143。
> 所有验证均在本地 worktree `comet/claim-secure-signing` 完成，未改生产库。

## 结论

**变更已完整落地并通过验证。** 设计中的四态状态机、双触发幂等、CAS 冲突控制、
显式响应契约、正式菜单与权限守卫均已被真实执行证据覆盖。联调过程中发现并修复了
7 个单测与静态检查覆盖不到的真实缺陷（租户列类型、ID 精度、SDK 启动约束、
审计字段、轨迹终态、shell 分组映射、菜单点击落点），修复后均复跑验证转绿。

## 验证矩阵

| 项 | 状态 | 证据 |
|----|------|------|
| V1 领域四态与守卫 | ✅ | `ClaimSignBatchTest` 5/5：合法四步流转、跳步拒绝、终态拒绝、影像未齐拒绝、意见短于 10 字拒绝 |
| V2 应用服务用例 | ✅ | `ClaimSignApplicationServiceTest` 6/6：内部报案自动签收、同 `businessKey` 幂等重放、缺 businessKey 拒绝、名单为空拒绝、CAS 冲突 409、成功后内存态与轨迹同为 `FINISHED` |
| V3 后端全量构建/测试 | ✅ | `mvn -o -pl bone-apps/synap-claim/synap-claim-app -am clean package`：**1711 tests / 0 failures / 8 skipped，BUILD SUCCESS**；含 28 项 ArchUnit 架构规则 |
| V4 前端类型/单测/构建 | ✅ | `npm run typecheck -w bone-claim-app` 退出 0；37 个测试文件 **284 tests 全通过**（补 shell 分组测试前为 36 文件 277 tests；签收 service 4、页面 7、shell 分组 7）；`npm run build -w bone-claim-app` 成功，`CaseSignPage` 独立 chunk 6.21 kB |
| V5 全量提交门禁 | ✅ | `./scripts/check.sh` **59/59 全绿、退出 0**；覆盖迁移清单/DDL 基线/错误码 i18n/种子一致性/SDK SQL 模板/领域对象直出/Java import/模块边界等 |
| V6 迁移与账本 | ✅ | 隔离清单只应用 0142/0143/0144；`schema_migration_journal` 三行 `source=apply, success=1`；活库三张签收表存在，`tenant_id` 已由 VARCHAR(64) 修正为 BIGINT；5 个租户均有 `/sclaim/claim/sign` 菜单，order_no=20 |
| V7 API 全链路联调 | ✅ | 独立 8097 栈真实请求：自动签收同 `Idempotency-Key` 两次 → 同一 `batchNo`、`created=true/false`；线下创建→导入名单→影像登记→确认 → `FINISHED`；再次确认 → HTTP 409 `SC_CLAIM_SIGN_STATUS_CONFLICT`；详情/列表 `batchId` 为字符串（19 位雪花 ID 不丢精度）；轨迹四步齐全且 CONFIRM 步为 `FINISHED` |
| V8 UI 单导航探针 | ✅ | 对 worktree 代码（shell 3010 / claim 3011）跑 `claim-single-nav-probe.mjs`：**143/143 通过**；「案件签收」菜单可见、规范 pathname 落地、顶栏/页面标题一致、无 5xx、无 console error；无幽灵菜单/不可达功能/名称漂移 |
| V9 安全与契约 | ✅ | `/sign` 复用 `claim:claims:read` 并保留 `Auth` 守卫（直输 URL fail-closed）；轨迹用显式 `SignTrackResp` 装配，不直出领域对象；四个新错误码在 `ClaimErrorCodes`、`DEFAULT_HTTP_STATUS`、中英文语言包、错误码台账四处齐备；写动作稳定幂等键 ≤64 字符 |

## 联调中发现并修复的真实缺陷

| # | 缺陷 | 修复 | 复验 |
|---|------|------|------|
| 1 | 0142 三表 `tenant_id` 误写 VARCHAR(64)，`requireBatch` 比较租户时抛 `ClassCastException`（`String cannot be cast to Long`） | 新建 0144 幂等修正列类型（不改已记账的 0142），同步 `bone-init.sql` | 活库 `information_schema` 三列均 BIGINT；联调不再抛异常 |
| 2 | `SignBatchResp.batchId` 用 `Long`，JS 侧 19 位雪花 ID 精度丢失 | 改为 `String` 并补显式映射 | 详情/列表 `batchId` 与库内一致 |
| 3 | SDK 要求每个 SQL 通道方法有 `@TenantScope` 与专属模板，`saveTrack` 漏标注导致启动失败 | 补 `@TenantScope(MANUAL)`、逐参数签名与 `saveTrack.sql` | 服务启动 health 200；轨迹写入成功 |
| 4 | SDK 级联插入显式写 `created_at=null`，覆盖 DB 默认值，导入名单 400 | `SignBatchItem.of` 创建时显式填审计字段 | 导入名单 200，明细落库 |
| 5 | `SignTrackLog` 被 Controller 直出，暴露 `tenantId` 与内部字段 | 新增 `SignTrackResp` + MapStruct 装配 | 架构门禁通过；响应字段收敛 |
| 6 | shell `CLAIM_MENU_GROUP_BY_HASH` 漏登记 `/sign`，菜单落「未分组」并形成第 5 个组；旧服务下点击落回工作台 | 补 `/sign → 治理` 映射与单测；探针改用 worktree 独立端口 | 探针 143/143，四组精确 |
| 7 | `completeSign` 只写签收字段、不推进内存态，轨迹与领域事件记录前置态 `WAIT_FOR_CONFIRMATION` | CAS 成功后调用 `transitionTo(FINISHED, "CONFIRM", operator)`，补成功用例断言 | 轨迹 CONFIRM 步为 `FINISHED`，后端 1711 测试全绿 |

## 判读要点

- **不是「测试全绿就等于正确」**：缺陷 1/3/4/7 都是服务真实启动或真实请求才暴露，单测与静态门禁无法通过；
  联调脚本保留了每步真实请求/响应，可复跑比对。
- **V8 第一次跑 138/144 是环境假红**（共享 3000/3013 服务器来自主树，看不到 worktree 新路由）；
  用 worktree 独立端口重跑后 143/143，未把环境问题误判为产品缺陷。
- **0144 而非改 0142**：0142 已记账，sha256 变更会造成 drift；按项目纪律用新迁移修正。
- **权限复用而非新增**：菜单与守卫都用既有 `claim:claims:read`，避免新增权限码带来的授权迁移与漂移。

## 遗留与后续（不在本次范围）

- 本地库已应用 0142/0143/0144；生产/CI 环境迁移由部署流程执行，本报告不代表生产已变更。
- 8097 独立栈与 3010/3011 开发服务器仅用于本次验证；联调造数留在本地 `bone` 库，不自动清理。
- 早期门禁失败中的 `claims-e2e-result.json` 依赖是 worktree 缺少未入 Git 的本地证据文件；
  复制主树证据后两项 AI 就绪度单测通过，非源码问题。
