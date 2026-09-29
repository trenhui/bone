# API 网关（bone-gateway）设计方案复核报告（含 v1 设计稿产出）

> **执行时间**：2026-09-29（夜间自动化）｜**轮换序号 #10**｜**对应设计稿**：无（锚定 `design_doc: null`，本晚产出 v1）
> **锚定版本**：`doc/design/_global-contracts.yaml` `generated_at=2026-09-26T23:40`
> **代码快照**：分支 `dev`，HEAD `332c1b925`
> **对标基线**：锚定 `hc_semantics` + `doc/architecture/Bone-API-规范.md`（§10 链路）+ `doc/architecture/多租户数据隔离方案设计.md`（§5 R1/R4、通则 T1）+ `Bone-错误码登记.md` §3.1
> **本晚产物**：`doc/design/modules/bone-gateway-设计方案.md`（v1，从零）

---

## 〇、运行前提与模块选择说明（本晚特殊，先交代）

| # | 前提 | 实测 | 处置 |
|---|------|------|------|
| 1 | 工作树洁净 | **不满足**：39 项在途改动（`studio-generator` 15 改 + 20 新文件、`bone-iam/Tenant.java`、`bone-system/SysDictItemRepository.java`、`bone-init.sql`、`bone-generator-app/**`） | 不碰任何在途文件、不做 `git add -A`、不把他人 WIP 卷入提交 |
| 2 | 轮换目标 `studio-generator`（#7）可执行 | **不满足**：该模块正被并发会话改造（21 个新文件，最近写入 03:08），同模块动代码无法用显式路径隔离 | **顺延**，选一个无在途改动的模块 |
| 3 | 首选 `bone-file`（#8）可执行 | **不满足**：06:07–06:11 另一会话正在做 bone-file #8（同 HEAD `332c1b925`），已产出设计稿 + 报告并正在改 `_review-status.yaml` / `_nightly-log.md` | **让路**：不重复劳动，且**不碰任何共享状态文件**（并行写同一文件会静默丢写） |
| 4 | 最终选择：`bone-gateway`（#10） | ✅ `pending`、无设计稿、无在途改动、并发会话短期不会触及 | 执行 A + A' |
| 5 | 锚定新鲜 | `generated_at=2026-09-26T23:40` | 满足 |
| 6 | 无显式叫停 | `doc/design/approvals/bone-gateway.yaml` 不存在 | 满足（= `auto`） |

> **给使用者的提示**：同一自动化在 06:05–06:11 期间出现了**两个并发实例**（另一实例做了 bone-file #8）。本实例改做 bone-gateway 以规避冲突，但**根因（重复触发）未消除**，建议核查调度配置，否则后续每夜仍可能重复劳动。

---

## 一、阻断级问题（必须先改设计/实现才能达标）

| # | 问题 | 违反项 | 修复建议 | 证据（文件:行） |
|---|------|--------|----------|------------------|
| G-1 | **熔断参数三源冲突，`application.yml` 调参实际无效**：Java `configureDefault` 给的是 wait **10s** / window **10** / halfOpen **3** / TimeLimiter **5s**；yml `resilience4j.instances.gatewayRoutes` 写的是 wait **30s** / window **50** / halfOpen **5** / TimeLimiter **30s**；而 breakerId 是 **routeId**（`bone-iam` 等），与实例名 `gatewayRoutes` 字符串不匹配 | 配置治理「单一真源」；可运维性（运维按文档调参得到另一个值） | 二选一并**删除另一处**：(a) 以 Java `ResilienceConfig` 为唯一真源，删除 yml `resilience4j.*` 整段；(b) 删 Java，yml 改为 routeId 粒度实例。**必须补一条「参数生效性」回归测试**（连续打挂上游，断言熔断在第 N 次打开、开放态时长=M 秒） | `ResilienceConfig.java:20-44`；`application.yml:106-122`；`CircuitBreakerGatewayFilter.java:48-50`；**自认注释** `application.yml:103-105` |
| G-3 | **错误响应信封与平台 `ApiResponse` 不一致**：网关手写拼串产出 `{"code":401,"message":"...","traceId":"..."}`，而平台信封为 `{success, code, message, data, timestamp}`。网关是**前端唯一入口**，前端无法用统一解析器处理 401/429/503 | HC-003 精神（统一响应）；`Bone-错误码登记.md` §3.1「新接口强制字符串业务码」（网关无 `errorCode`） | 改为序列化 `com.bone.core.model.ApiResponse`（禁止手写 JSON）；`success=false`、`timestamp` 补齐；`traceId` 保留在**响应头** `X-Trace-Id`（`TraceIdRelayGatewayFilter` 已写入）而非 body；补 `GW_*` 错误码 | `GatewayErrorWriter.java:22-29`；`ApiResponse.java:22-26`（`success/code/message/data/timestamp`）；`grep -rn "ErrorCode" src/` → 0 命中 |

> **G-1 的确定性说明（不过度断言）**：yml `application.yml:103-105` 的注释**已自认**该限制。推断基于两点——`configureDefault` 为所有 `create(id)` 提供默认配置、breakerId(`routeId`) ≠ `gatewayRoutes`。**建议以回归测试实测确认实际生效值**，再执行删除动作。

---

## 二、建议级优化

| # | 当前设计/实现 | 业界对标 | 优化方案 | 证据 |
|---|--------------|----------|----------|------|
| G-2 | **限流默认关闭 + IP 维度可伪造**：`enabled: false`；`resolveIp` 优先取 `X-Forwarded-For`，未认证端点改 XFF 即可换桶 | 网关限流默认应可观测/可开启；XFF 只在可信代理后置时采信 | 生产显式开启；XFF 仅在配置了可信代理跳数时采信（或按 `RemoteAddress` 优先） | `application.yml:77`；`RateLimitGatewayFilter.java:100-115` |
| G-4 | **JWT 白名单双真源漂移**：Java 默认 `/api/v1/iam/**auth**/login`，yml 实际 `/api/v1/iam/login`，**IAM 真实端点是 `/api/v1/iam/login`**（yml 对、Java 默认错） | 配置单一真源 | 修正 Java 默认值与 yml 一致；或移除 Java 默认、强制外部配置 | `GatewayJwtProperties.java:57-59`；`application.yml:72`；`AuthController.java:37,46` |
| G-5 | **测试覆盖不足**：10 条路由仅 4 条有 IT（`iam`/`extension`/`generator`/`integration`）；**鉴权/限流/熔断三大横切能力零测试** | 横切能力是网关核心价值，应有回归 | 补剩余 6 条路由 IT；补鉴权（401/白名单/防伪造头）、限流（429+Retry-After）、熔断（G-1 参数生效性） | `src/test/resources/application-test.yml`（仅 4 条）；`grep -rln "ratelimit\|CircuitBreaker\|Jwt\|whitelist" src/test/` → **0 命中** |
| G-6 | **健康检查为 STUB**：静态 `UP` + `status=STUB`，未做上游心跳/熔断状态/Redis 桶 | 网关应聚合上游可用性 | 保留 STUB 明示（与 bone-system S-9 同口径：**占位须自述限制，不得伪装真实**），按 javadoc 待办实现聚合 | `GatewayModuleReactiveHealthIndicator.java:27-36` |
| G-7 | **`ErrorBody` 死代码**：`dto/ErrorBody.java` 全模块零引用（错误写回走 `GatewayErrorWriter` 手写串） | 无死代码 | 改信封（G-3）时删除，或让 `GatewayErrorWriter` 复用它 | `grep -rn "ErrorBody" src/ \| grep -v dto/ErrorBody.java` → 0 命中 |
| G-8 | **JSON 转义不完整**：`escape()` 仅处理 `\` 与 `"`，未处理 `\n`/`\r`/`\t`/控制字符 | 合法 JSON | 随 G-3 改用序列化器一并消除 | `GatewayErrorWriter.java:35-40` |
| G-9 | **链路 ID 信任客户端 + JWT 无吊销**：`X-Trace-Id` 直接沿用客户端值（日志污染面）；无登出黑名单 | 输入校验；会话吊销 | traceId 加长度/字符集白名单；JWT 吊销登记为已知限制（或引入短期 token + Redis 黑名单） | `TraceIdRelayGatewayFilter.java:31-41`；全模块无黑名单实现 |
| G-10 | **CORS 端口不全且口径不一致**：网关仅放通 `3000`、`3008`；同仓 `bone-file` 放通 `3000-3008`；实测前端 8 个应用为 3000/3003/3004/3005/3006/3008/**3009**，两者**都漏 3009** | 配置一致性 | 统一为配置项；`allowedMethods: "*"` 收窄到实际方法 | `application.yml:14-16`；`bone-file/application.yml:74-84`；`apps/*/vite.config.ts` |
| G-11 | **租户头下游覆盖待核实**：`WebTenantConfiguration` 实测仅 3 个模块持有（integration / masterdata / studio-generator），而路由有 10 条上游 | 多租户方案 §5 R1（端到端绑定） | 逐上游核实；缺失则网关注入的 `X-Tenant-Id` 被**静默丢弃**（不报错，最危险） | `find . -name WebTenantConfiguration.java` → 3 处；`application.yml:20-64`（10 条路由） |

---

## 三、参考级对标（亮点）

- **租户头防伪造是本轮最强设计**：`JwtAuthGlobalFilter` 在注入前**先 `remove`** 客户端自带的 `X-Tenant-Id`/`X-User-Id`/`X-Roles`，从根上消除「伪造租户头直通下游」的越权向量——这恰是多租户隔离中最易被忽略的一环。（`JwtAuthGlobalFilter.java:68-74`）
- **上下游契约自洽且 fail-closed**：下游 `WebTenantConfiguration` 读取同名头，缺失时**不回落平台租户 0**，保持空由 SDK `MissingTenantContextException` 失败关闭，其 javadoc 明确交叉引用 `bone-gateway JwtAuthGlobalFilter`。（`bone-integration/.../WebTenantConfiguration.java:15-22`）
- **限流原子性处理正确**：用 Lua 脚本把 `INCR` 与首条 `EXPIRE` 合并，消除「进程崩溃后计数不过期导致永久 429」的竞态窗口——比常见的「先 INCR 再 EXPIRE 两步调用」严谨。（`RateLimitGatewayFilter.java:47-58`）
- **熔断按 routeId 隔离，且留了演进说明**：javadoc 记录了从「单一全局 breaker 误伤全栈联调」到「按路由隔离故障域」的动因，是高质量的技术决策留痕。（`CircuitBreakerGatewayFilter.java:19-26,48-50`）
- **密钥 fail-fast 优于同仓多数模块**：启动期校验空值、长度 <32、prod 使用默认密钥 → **拒绝启动**；RS256 PEM 非法亦拒启。（`GatewayJwtProperties.java:31-52`）
- **过滤器 order 显式编排且顺序正确**：链路 ID（`HIGHEST_PRECEDENCE`）早于鉴权，保证 401/429/503 响应也带 `traceId`；限流在鉴权后（才能取 `X-User-Id`）；熔断最后包裹转发。
- **测试用真实 Mock HTTP Server 全链路**：RouteIT 起 `com.sun.net.httpserver.HttpServer` + `WebTestClient`，非纯 mock，可信度高。
- **运维端点保护口径自洽**：`metrics`/`prometheus` 已暴露但**不在 JWT 白名单**，yml 注释亦写明「禁止网关匿名暴露」。（`application.yml:73`）
- **Reactive 栈使用正确**：限流用 `ReactiveStringRedisTemplate`、健康检查实现 `ReactiveHealthIndicator`（非阻塞），无阻塞调用混入。

> **判定为合规、不计缺口**：网关**零表**（无状态是正确定位，非 HC-008 缺口）；分层无 `domain`/`application` 且 `ArchitectureTest` 统一 `allowEmptyShould(true)`（网关为基础设施组件，与既有约定一致）。

---

## 四、v1 设计稿

已产出 `doc/design/modules/bone-gateway-设计方案.md`，要点：

- **§1.1 边界裁决**：网关**不产生业务错误码**，只产传输层错误（401/429/503/404）；但因是唯一入口，**错误信封必须与 `ApiResponse` 一致**。
- **§2.2 / §2.3**：固化「先 remove 再注入」的防伪造模式与 `X-Tenant-Id` 上下游契约。
- **§2.5 / §5.2**：把熔断三源冲突写成明确定级的设计裁决（**以 Java 为唯一真源**）。
- **§3.0**：确认零表为 by-design，未来若需配额持久化/审计落库再议（L3）。
- **§4.1 / §4.2**：定义目标错误信封与 4 个 `GW_*` 码。
- **§6 / §7**：性能安全清单与测试补齐计划。

---

## 五、实现计划（逐项标 L 级）

| 项 | 级别 | 说明 |
|----|------|------|
| G-1 熔断配置收敛（删一处 + 补参数生效性回归测试） | **L2** | 影响运行时行为，需 Review；回归测试是验收关键 |
| G-3 错误信封对齐 `ApiResponse` + 4 个 `GW_*` 码 + 台账登记 | **L2** | **响应契约变更**，需在 PR 声明；前端需确认能解析（`success` 从 `undefined` 变 `false`） |
| G-4 白名单默认值订正 | L1 | 单行，零风险 |
| G-7 删 `ErrorBody` 死代码 | L1 | 随 G-3 一并做 |
| G-8 转义修正 | L1 | 随 G-3 改用序列化器后自动消除 |
| G-10 CORS 外置为配置项 | L1 | 配置变更 |
| G-5 补测试（路由 6 条 + 横切 3 类） | L1 | 测试代码 |
| G-2 限流开启 / XFF 采信策略 | **L3** | 属生产运行策略，需架构师定 |
| G-6 健康检查聚合实现 | L2 | 涉及探测上游，需定超时/阈值 |
| G-11 租户头下游覆盖核实与补齐 | L2 | 跨模块，需逐上游确认 |
| G-9 JWT 吊销 | L3 | 引入 Redis 黑名单属架构变更 |

### 待审批清单（L3 / L4，本任务**不执行**）
- G-2：生产限流阈值与 XFF 可信代理跳数。
- G-9：JWT 吊销机制（黑名单 vs 短期 token + 刷新）。
- 未来若网关需落库（配额/审计），重新评估无状态定位并新增表（L3 DDL）。

---

## 六、AI 自审结论（A' 三问）

| 问 | 结论 |
|----|------|
| **Q1 有无假阳性（误报）？** | **有 2 例已剔除**：① **「网关零表」不可计为 HC-008 缺口**——网关是无状态转发组件，零表是正确定位，已明确写入 §3.0 为 by-design；② **「JSON 转义不全」不可定级为现存 bug**——三处 message 均为固定中文常量、不含换行，**当前不发作**，故降为建议级 G-8 并注明触发条件（一旦透传异常 message 即触发），避免过度断言。 |
| **Q2 有无漏检（假阴性）？** | **有 3 例已补**：① **G-1 熔断配置三源冲突**——锚定未涉及，且 yml 注释虽自认但未定级、未修复，本轮升为阻断级（运维调参失效是最易踩的坑）；② **G-10 CORS 端口**——交叉核对前端 8 个应用实际端口后发现网关放通 3000/3008 **漏 6 个**、且与 `bone-file` 的 3000-3008 口径不一致、两者**都漏 3009**；③ **G-11 租户头下游覆盖**——`WebTenantConfiguration` 仅 3/10 上游持有，若其余上游缺失则网关注入被**静默丢弃**（不报错，最危险）。 |
| **Q3 证据是否可复现？** | 是。全部结论带 `文件:行`；G-5/G-7 用「0 命中」反向扫描佐证；G-4 交叉核对了 IAM 真实端点（而非只看配置）；G-1 标注了推断依据并给出**实测确认方法**（参数生效性回归测试），未伪装成已验证事实。 |

**自审结论：PASS（可放行至 B 阶段；G-1 与 G-3 应优先于其余 B 项）。**

---

## 附：本晚状态迁移与遗留

- **本晚不修改** `doc/design/_review-status.yaml` 与 `doc/design/_nightly-log.md`：06:07–06:11 另一并发会话正在写入这两个文件（最近一次 06:11:28），并行编辑同一文件会**静默丢写**。状态迁移需在并发会话结束后单独执行（或由使用者确认后补做）。
- **建议补做的状态迁移**（待执行）：`bone-gateway` 条目 `status: pending → design_ready`、`design_doc` 指向本晚 v1 稿、`report_path` 指向本报告、`stages_done: [A, A']`、`last_reviewed_at: 2026-09-29`；`next_run` 指向下一个 pending 模块。
- **遗留**：`bone-file` #8 由并发会话完成；`studio-generator` #7 因并发占用顺延；`bone-notification` #9 仍为 pending。
- **根因提示**：同一自动化出现并发实例，建议核查调度配置。
