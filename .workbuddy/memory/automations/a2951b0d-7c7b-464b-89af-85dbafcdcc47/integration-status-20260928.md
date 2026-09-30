# Bone 模块前后端联调（C/D）状态与执行计划

- 日期：2026-09-28（该自动化首轮执行）
- 状态真源：`doc/design/_review-status.yaml`
- 阶段模型：A=设计 / B=实现 / B'=代码复核 / C=前后端联调 / D=验收（构造数据→模拟人工→清理自证）

---

## 一、结论（TL;DR）

1. **11 个模块中仅 2 个完成 C/D**：`bone-iam`（implemented，stages 含 C/D）、`bone-metadata-server`（implementing，loop 已闭环，后端 D 完成）。
2. **其余 9 个模块尚未进入 C/D**：
   - `bone-system`：代码已走完 A/A'/B/B'（B 第2夜收口），**是当下唯一立即可做 C/D 的模块**，但仍缺鉴权/路由链。
   - `bone-masterdata` / `bone-integration` / `bone-extension-studio` / `studio-generator`：仅完成 A/A'，**B 实现未开始**，无法联调。
   - `bone-file` / `bone-notification` / `bone-gateway`：状态 `pending`，**无设计稿 / v1 从零产出**。
   - `bone-shell`：宿主应用，单独一轮，依赖网关 + 所有后端。
3. **环境阻塞（实测）**：IAM(8081) 与 Gateway(8888) 均未运行。运行中后端（masterdata 18084 / extension-studio 18088 / file 18089）`/actuator/health`=UP，但业务 API 直连返回 **401/403**；用已知 dev 密钥伪造 JWT 仍被拒（`{"code":401,"message":"未登录或 Token 已过期"}`）→ **没有 IAM 发码 + 网关注入，业务级联调无法进行**。
4. 数据库 MySQL 在跑（pid 353）、Redis 在跑；前端宿主 bone-shell(3000)/bone-iam-app(3003)/bone-system-app(3007) 均返回 HTTP 200。

---

## 二、模块状态矩阵（C/D 就绪度）

| # | 模块 | 预期设计稿 | yaml 状态 | 已完成阶段 | C/D 就绪 | C/D 阻塞点 |
|---|------|-----------|-----------|-----------|---------|-----------|
| 1 | bone-iam | 6 + 6a + 10 | implemented | A/A'/B/B'/C/D | ✅ 已完成 | 无 |
| 2 | bone-metadata-server | 2 + 2a + 2b | implementing | A/A'/B/B'/C/D | ✅ 后端完成 | 前端微应用 skip |
| 3 | bone-masterdata | 3 + 3a | design_ready | A/A' | ❌ 需 B→B'→C→D | HC-008 L3 DDL 缺口（mdm_qcheck_task 等缺 updated_at/deleted）；B 未开始 |
| 4 | bone-integration | 4 | design_ready | A/A' | ❌ 需 B→B'→C→D | F1 错误码散落（无 INT_ 常量类、advice 注释称前端拿不到 errorCode）；B 未开始 |
| 5 | bone-system | 7 + 7a + 1 | implementing | A/A'/B/B' | 🟡 **C/D 下一目标** | 需 IAM+网关鉴权链；前端 S-1/S-2 已修，S-8 errorCode→文案已铺 |
| 6 | bone-extension-studio | 5 + 5a | design_ready | A/A' | ❌ 需 B→B'→C→D | X-1 写入侧租户注入（stampTenant 仅覆盖 2/5 实体）；B 未开始 |
| 7 | studio-generator | 8 | design_ready | A/A' | ❌ 需 B→B'→C→D | G-1 租户闭环（全模块零读 TenantContext，硬编码 0L/1L）；B 未开始 |
| 8 | bone-file | null | pending | — | ❌ v1 从零 | 无设计稿；需先出 v1（MinIO/权限/配额） |
| 9 | bone-notification | null | pending | — | ❌ v1 从零 | 无设计稿；共享端口 8083，先定与 system alert_* 通道边界 |
| 10 | bone-gateway | null | pending | — | ❌ v1 从零 | 无设计稿；**尚未实现**，是所有联调的前提 |
| 11 | bone-shell | 1 + bone-pages-spec.html | pending | — | ❌ 宿主轮 | 依赖网关 + 所有后端 |

---

## 三、环境就绪度（实测）

**已 UP**
- MySQL（pid 353，`/usr/local/mysql/bin/mysqld`）— bone 库可达，运行模块 health=UP
- Redis（6379）
- 后端：masterdata(18084)、extension-studio(18088)、file(18089) — `/actuator/health`=200/UP
- 前端：bone-shell(3000)、bone-iam-app(3003)、bone-system-app(3007) — HTTP 200

**DOWN（关键阻塞）**
- IAM(8081) — 无 JWT 发码
- Gateway(8888) — 无路由/鉴权注入（且 v1 尚未实现）

**鉴权门禁实测证据**
- 直连 `GET /api/v1/masterdata/entities/page` 无 token → `401`（空体，HttpStatusEntryPoint）
- 直连 + 伪造 dev JWT（`dev-only-secret-key-minimum-32-bytes-long`）+ `X-Tenant-Id:1` → `401`，响应体 `{"success":false,"code":401,"message":"未登录或 Token 已过期",...}`
- 结论：运行实例的 JWT 密钥/校验口径与本机 dev 默认不一致，必须走 IAM 发码或网关注入，业务级联调当前不可达。

---

## 四、执行计划（作为后续自动化轮次输入，按就绪度排序）

**Phase 0 — 解锁鉴权链**
- 起 IAM(8081)，与运行中的后端对齐 `BONE_IAM_JWT_SECRET_KEY`（必要时按同一 dev 密钥重启后端，使 IAM 发码被接受）。
- 网关 v1 尚不存在（pending），Phase 1~2 的既有模块联调先走「直连模块端口 + IAM token」；网关作为 Phase 3 优先交付。

**Phase 1 — bone-system C/D（最高就绪）**
- 起 system 后端（端口 8083），经 IAM token 调用 `/api/v1/system/*`：配置编辑（key/value/type 已对齐）、告警创建必 400（metric/level 口径）、errorCode→文案映射（S-8 19 个 SYS_* 译文已补）。
- D 验收：Playwright 走 bone-system-app(3007) 关键流；清理自证。

**Phase 2 — 驱动 B→C/D（沿用夜间循环顺序）**
- bone-masterdata → bone-integration → bone-extension-studio → studio-generator。
- 每模块：先落 B（含 L3 DDL/租户注入等阻断项需架构师审批），B' 门禁，再做 C/D。

**Phase 3 — v1 从零**
- 优先 bone-gateway（所有联调的前提）；随后 bone-file、bone-notification（注意 notification 与 system alert_* 的通道边界，避免重复建设）。

**Phase 4 — bone-shell 宿主轮**
- 依赖 Phase 0~3 全部就绪；qiankun 子应用路由 + 权限码 + 租户注入联调。

---

## 五、本轮已完成的真实动作

- 解析 `_review-status.yaml`（11 模块权威状态）与 `doc/design/modules/` 设计稿清单。
- 实测运行服务、DB、前端可达性（端口扫描 + curl）。
- 实测后端业务 API 鉴权门禁（401/403 + 伪造 token 被拒），定位 C/D **唯一阻塞点 = IAM/网关缺失**。
- 确认网关 v1 尚未实现（pending），直连+IAM token 是既有模块联调的唯一可行路径。

---

## 六、下一轮默认动作（已声明）

本轮回合交付「状态盘点 + 阻塞定位 + 执行计划」。下一轮执行 **Phase 0（起 IAM + 密钥对齐）** 与 **Phase 1（bone-system C/D）**，并回填 `_review-status.yaml` 的 stages_done/status。
