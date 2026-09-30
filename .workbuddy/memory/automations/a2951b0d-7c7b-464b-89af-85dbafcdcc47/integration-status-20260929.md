# Bone 前后端联调 — 集成状态报告 (2026-09-29)

> 自动化轮次产出。上一轮 (2026-09-28) 锁定了「IAM/Gateway 鉴权链 DOWN」为关键阻塞并规划了 Phase 0/1。本轮实测推翻了「必须先把 IAM 跑起来」的假设，并修复了主数据的真实运行时缺陷。

## 一、本轮关键结论

### 1. 鉴权链其实可绕过（无需 IAM / Gateway 在线）
资源服务器用 HS256 校验 JWT，密钥取自 `bone.iam.jwt.secret-key`（`${BONE_IAM_JWT_SECRET_KEY}`，无默认值；若未注入则回落到 `dev-only-secret-key-minimum-32-bytes-long`）。
实测：**用 dev 默认密钥签发的 token（claims: `sub`/`userId`/`tenantId`/`scopes`，HS256）被所有在跑的资源服务器接受**：
- masterdata：无 token→401，错密钥→401，dev 密钥→200/400（已过鉴权，到达 Controller）
- extension-studio：公开端点 200；鉴权端点带 token 200、无 token 200（公开）
- 控制台结论：**联调任意资源服务器的 C/D 都不依赖 IAM/Gateway 先上线**。IAM/Gateway 只在「端到端走登录流程」时才必需。

### 2. 主数据 (bone-masterdata) 的真实阻塞 = SDK 类加载缓存失效（已修复）
- 现象：masterdata 所有请求返回 **500**，堆栈 `NoClassDefFoundError: com/bone/metadata/sdk/query/criteria/Condition`，发生在 `Criteria.java:587` `new Condition(...)`。
- 根因：进程于 09-27 23:10 启动，加载的是当时的 `.m2` 旧 `bone-metadata-sdk` jar；SDK 在 09-28 22:45 被重新 `install` 到 `.m2`（已含 `query/criteria/Condition`）。JVM ClassLoader 在首次 `new Condition` 失败（当时磁盘 jar 缺该类）后**永久缓存了失败状态**，即便磁盘 jar 已补回该类，后续请求仍持续 500。
- 修复：用原进程完全相同的启动命令行（`java -Dserver.port=18084 -Dspring.cloud.nacos.discovery.enabled=false -cp <原 classpath> MasterdataApplication`）**重启进程**，全新 ClassLoader 从当前 `.m2` 正确 jar 加载 `Condition`。
- 验证（重启后）：`[C] GET /categories → 200`、`[D] POST /categories → 200`（创建+自清理删除均 200）、`post-delete read → 200`。✅

### 3. 同期清掉其余 SDK 消费者的同类隐患
extension-studio 在 18088/18089 上各跑一个实例（注意：**18089 不是 bone-file**，两个都是 `ExtensionStudioApplication`）。它们同样在 09-27 加载了旧 SDK，存在同样的缓存失效风险。已用相同方式重启两个实例（端口、命令行完全还原），现均正常 `Started ExtensionStudioApplication`，监听 18088/18089。

## 二、真实运行时拓扑（实测，2026-09-29 07:20）

| 端口 | 模块 | 主类 | 状态 |
|---|---|---|---|
| 18084 | bone-masterdata | `MasterdataApplication` | ✅ UP（本轮修复并验证 C/D） |
| 18088 | bone-extension-studio (实例1) | `ExtensionStudioApplication` | ✅ UP（本轮重启） |
| 18089 | bone-extension-studio (实例2) | `ExtensionStudioApplication` | ✅ UP（本轮重启；**非 bone-file**） |
| 3000 / 3003 / 3007 | 前端 (node) | — | ✅ UP |
| 8081 | bone-iam | — | ❌ DOWN |
| 8083 | bone-system | — | ❌ DOWN |
| 8888 | bone-gateway | — | ❌ DOWN |
| 9001 | bone-metadata-server | — | ❌ DOWN |
| 8085 | bone-integration | — | ❌ DOWN |
| 8107 | bone-file | — | ❌ DOWN |

## 三、11 个模块的联调状态与下一步

| # | 模块 | 本轮状态 | 缺口 / 阻塞 | 下一步 |
|---|---|---|---|---|
| 1 | bone-iam | 设计 A/A' 已完成（09-26），**未运行** | 8081 down；在脏 WIP 分支上 | 启动 iam 后跑登录→签发 token 的真实链路；对齐 `BONE_IAM_JWT_SECRET_KEY` |
| 2 | bone-metadata-server | **未运行** | 9001 down | 启动后跑 2/2a/2b 的 C/D |
| 3 | bone-masterdata | ✅ **C/D 已验证** | 无（本轮修复） | 维持；纳入回归 |
| 4 | bone-integration | **未运行** | 8085 down | 启动后跑 4 的 C/D |
| 5 | bone-system | **未运行** | 8083 down；脏 WIP | 先合/稳定分支再启动；跑 7/7a/1 的 C/D |
| 6 | bone-extension-studio | ✅ 冒烟通过 | 18088/18089 双实例（建议收敛为单实例） | 跑 5/5a 的 C/D |
| 7 | studio-generator | 未作为端口服务运行 | 脏 WIP | 单独验证生成器产物 |
| 8 | bone-file | **未运行 / 需从零产出 v1** | 8107 down，无 design_doc | 先出 v1 设计+实现，再联调 |
| 9 | bone-notification | **未运行 / 需从零产出 v1** | 无 design_doc | 先出 v1 设计+实现，再联调 |
| 10 | bone-gateway | **未运行 / 需从零产出 v1** | 8888 down，无 design_doc | 先出 v1 设计+实现，再联调 |
| 11 | bone-frontend/bone-shell | 前端 UP（3000/3003/3007） | bone-shell 宿主单独一轮 | 单独轮次联调控制台+`bone-pages-spec.html` |

## 四、风险与注意

- **未运行模块占比高（7/11 后端不在跑，其中 3 个需从零建）**：单轮自动化无法把 11 个模块全部联调跑通，本伦聚焦「可立即修复/验证」的部分（主数据 + 鉴权链 + extension 健康度），并给出缺口清单。
- **脏 WIP 分支** `codex/nightly-studio-generator-20260929`：iam/system/studio-generator 在改动中，**不要**由本自动化 `git add -A` 或提交，避免污染他人在途改动。
- **重启方法已验证**：对 `mvn spring-boot:run` 启动的服务，若 `.m2` 依赖被 `install` 更新，必须重启进程（JVM 持有启动时刻 jar 句柄 + 类加载失败缓存）。仓库自带 `scripts/dev/restart-mvp-services.sh` 即为此设计，但其端口假设（8084/8088/8107）与实测端口（18084/18088/18089）不一致，**不能直接套用**，须用实测命令行重启。
- **18089 实为 extension-studio 第二实例**：之前把 18089 当作 bone-file 探测是误判；bone-file 根本没启动。

## 五、建议的后续轮次计划（按价值排序）

1. **启动并联调 bone-metadata-server / bone-integration / bone-system**（已有实现，仅未启动）→ 跑各自 C/D。
2. **收敛 extension-studio 双实例为单实例**（或明确 18089 的用途）。
3. **从零产出 bone-file / bone-notification / bone-gateway 的 v1**（design_doc=null）→ 实现后联调。
4. **启动 bone-iam + bone-gateway**，跑真实登录→签发→网关路由的端到端链路，替代 dev 密钥伪造 token。
5. **bone-shell 宿主前端**单独轮次联调控制台与 `bone-pages-spec.html`。
