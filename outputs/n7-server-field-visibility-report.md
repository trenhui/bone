# N7 服务端字段级角色可见性 —— 读写双侧闭环交付报告

> 日期：2026-10-20 ｜ 任务：列级裁剪（服务端强制）｜ 状态：**已完成并提交**

## 1. 背景与边界

N7 前端闭环（`04be68c5`）后遗留的诚实边界：**前端按角色隐藏只是体验层，服务端仍返回明文**——懂技术的用户直接调 API 即可拿到 `salary` 等受限字段。本任务把 UI 协议 `control.props.roles` 升级为**服务端强制**，读写双侧 fail-closed。

## 2. 契约设计（三态角色码）

| roleCodes | 语义 | 行为 |
|---|---|---|
| `null` | 内部/受信调用 | 不裁剪、不守卫（零影响升级，历史调用方无感） |
| 空集合 | 已认证但无角色 | fail-closed：全部受限字段裁掉 |
| 非空 | 正常已认证用户 | 按角色交集判定 |

**跨表面取交集**（最严格者胜出）：同一字段在列表/详情/表单多处配置 `roles` 时取交集；交集为空 ⇒ `denyAll`，对所有人不可见——**响亮失败而非静默泄露**。

**系统列永不裁**（id/created_by 等），**矛盾配置显式 denyAll**（配置错误不应悄悄放开）。

## 3. 落地组件

| 层 | 组件 | 职责 |
|---|---|---|
| bone-security | `JwtTokenService` 五参重载 | 登录签发放 `roleCodes` claim；**空集合不写 claim**（token 形状与历史一致） |
| bone-security | `JwtPrincipal` 第 5 组件 | claim 缺失 ≡ 空集合 |
| bone-iam | `AuthApplicationService.resolveRoleCodes` | 经 `accountRole → RoleHierarchyResolver 继承闭包 → roleRepository` 解析角色码；登录/刷新两处签发 |
| engine-runtime | `RuntimeFieldVisibility` | `(fieldCode, roles, denyAll)` 记录；紧凑构造归一化（null→空集、去空白、denyAll 与 roles 互斥） |
| engine-runtime | `RuntimeFieldVisibilityPolicy` | 读裁剪（连 `code__label` 伴生键一并裁）+ 写守卫（抛 `META_RUNTIME_FIELD_NOT_VISIBLE`）+ 空允许清单 ALLOW_ALL 快路径 |
| metadata-server | `RuntimeFieldVisibilityProjector` | 发布态 ui-schema JSON 树遍历提取 `control.props.roles`；多处配置取交集；只吞 `JsonProcessingException`（行走缺陷不吞，防「配了不生效」假绿） |
| metadata-server | `CurrentRoleProvider` + `SecurityContextCurrentRoleAdapter` | 三态端口；从 `ROLE_` 前缀 authority 反提取（与权限码命名空间分离） |
| metadata-server | `JwtAuthenticationFilter` | authorities 并入 `ROLE_<code>` |
| metadata-server | 应用层 + Redis 目录缓存 | page/getById/create/update/restore/pageDeleted/updateState 全路径透传；缓存载荷扩展 `VisibilityPayload` |

**错误码**：`META_RUNTIME_FIELD_NOT_VISIBLE` → HTTP 403（与实体级授权拒绝同档），三处登记（`MetadataErrorCodes` / 台账 §6 / zh-CN+en-US 语言包）。

## 4. 验证证据

### 4.1 全量测试（5 个受影响模块）

| 模块 | 结果 | 说明 |
|---|---|---|
| bone-security | 36/36 ✅ | 含 JWT roleCodes 解析新用例 |
| bone-iam | 286/286 ✅ | 含继承闭包解析新用例 |
| bone-notification | 42/42 ✅ | JwtPrincipal 五参适配 |
| engine-runtime | 166/166 ✅ | 新增 20（策略 13 + H2 端到端 7） |
| metadata-server | 298/298 ✅ | 新增 21（投影器 6 + 适配器 5 + filter 2 + 接线 1 + 既有签名更新 7） |

### 4.2 变异验证（5 连过，均双断言「植入成功 + 测试转红」）

| # | 变异 | 转红数 |
|---|---|---|
| M1 | 写守卫静默放行（不抛 403） | 3 红 |
| M2 | getById 读路径不裁剪 | 4 红 |
| M3 | `__label` 伴生键不裁 | 1 红 |
| M4 | 应用层角色码接线断开 | 6 红 |
| M5 | 投影器不提取 roles | 5 红 |

> 过程教训：M5 首次用 `Boolean.getBoolean` 条件写反造成**空操作变异**（0 红假信号），识别后摘除 collect 调用重做；变异体曾被 spotless 前置门禁拦下（根本没跑测试），已改合规写法。

### 4.3 编译假绿排查（bone-notification 14 ERROR）

现象：`NotificationApplicationServiceTest` 11 例运行期报 `Unresolved compilation problems: Flexible Constructor Bodies only available with source level 25`。取证：主类在 HEAD **未改动**、target/ 类为 10-16 的 ECJ 错误标记遗留（major 65）、javac 增量判定 "Nothing to compile" 跳过重编。**强制全量重编后 42/42 全绿**——债在陈旧构建产物，不在源码、不在本次改动。

## 5. 诚实边界（本期不落地）

1. **`props.tier`（档位可见性）无服务端来源**：JWT 无档位 claim，本期只做角色维度。
2. **管理员不做字段可见性豁免**：与行级 ACL 豁免互不影响，避免「管理员可见一切」的暗门。
3. **矛盾配置 denyAll 是有意 fail-closed**：配置错误时对所有人不可见，迫使配置修复而非静默放开。

## 6. 提交说明

- 本功能 33 个文件 + 基线债修复 1 个文件（`TenantDeletionGatewayAdapterTest`：`8da26285` 引入 `hasTenantIdColumn` 守卫时未同步 mock stub ⇒ 全表跳过 + `everyRegisteredTableGetsDeleted` 假绿，补 stub 对齐既有 `TenantDeletionGatewayDriftTest` 模式）。
- 提交经干净 worktree（HEAD + 本改动）过全门禁后 cherry-pick 回主树：主树 `check-i18n-sync` 被 **bone-ai 未提交的 3 个错误码**（`AI_DOC_OCR_PROVIDER_FAIL` / `AI_RAG_EMBEDDING_PROVIDER_FAIL` / `AI_RAG_RERANK_PROVIDER_FAIL`）拦下，已逐条证明非本提交引入（HEAD 无此三码）；本提交新码 `META_RUNTIME_FIELD_NOT_VISIBLE` 零违规。
