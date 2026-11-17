# BONE 前后端全模块联调报告（2026-11-17）

> 环境：网关 `:8888` / IAM `:8081` / masterdata `:8084` / blueprint `:8082` / metadata `:9001` /
> synap-claim `:8087`（本 run 恢复启动）/ 前端壳 `:3000` + 9 个子应用 / MySQL `:3306`（bone 库，tenant 0）/ Redis `:6379`。
> 口径：所有 E2E 走真实 API（与前端同网关 `/api/v1`）；落库对账用 `e2e_lib/mysql` 直查作第二证据源。
> 造数：本 run 经真实 API 幂等构建交易主数据夹具（见 §二），不直连改库、不碰生产路径代码。

## 一、结论

**所有分组全绿**（本 run 目标「直到所有功能正常」达成）：

| 分组 | 结果 | 说明 |
| --- | --- | --- |
| 离线层 `lint` / `selftest` | PASS | 语法门禁 + 共享层契约自证 + 造数真实性 R1–R5 |
| 环境层 `preflight --all` | PASS | 端口 + 前端 + MySQL 直查 + admin 登录探活 |
| 业务层 `py-core` | **175 / 175（0 失败）** | two-scenario 90 + app-to-data 54 + ui-schema-samples 31 |
| 业务层 `node-api` | **222 / 222（0 失败）** | claim-full 67 + workbench 41 + config 86 + fee-category 28 |
| 业务层 `py-sweep` | **0 真实 5xx / 38 PASS / 0 FAIL / 15 SKIP** | 4xx 均为负向探针或需夹具，SKIP 按设计 fail-closed |
| 业务层 `py-chain` | **全绿** | chains 141/141 + multichannel 51/51 + channel-buyer 21/21 + integration-flow 2xx 闭环 |
| UI 探针 `node`（6 个） | 全 PASS、0 新增 console 错误 | probe-commerce 由 34/35 → **37/37**（支付闭环打通） |

## 二、本 run 恢复 / 修复 / 造数明细

### 环境恢复（运行态，非代码）
1. **synap-claim 宕机 → 503**：网关 `GW_UPSTREAM_UNAVAILABLE`（熔断）。`.run-jars/claim.jar`（当日 09:18 构建，新于全部源码）未运行。用既有启动脚本的 env（JWT/DB 密钥对齐、nacos 关闭、上游 URI=127.0.0.1）在 `:8087` 重新启动，网关熔断恢复。`node-api` 由 1/86 → 全绿。
   - 另：`claim-fee-category-e2e.mjs` 默认直连旁路 `:18091`（未启动），通过环境变量 `BONE_E2E_CLAIM_BASE` 指向网关 `:8888` 后 28/28。

### 代码修复（已提交，属本仓库）
2. **`scripts/e2e/chains.py` 网关路由判据**：`ClaimInternalOpsController`（`/api/v1/internal`）在 `doc/ai-product/synapClaim/03-contract/工程详细设计-落地版.md §10.2` 明确定义为「内部接口」（编排层/运维工具的试算/重算/回归/决策触发/对账查询），非端侧业务面。原判据的 `internal_rpc` 白名单只有 `/api/rpc/orders`，误把该内部面判成「用户面路由未登记网关」。补入白名单并写明依据 + 移除判据（若将来需经网关暴露则改走登记）。修改后 G 项由 FAIL → PASS。

### 测试数据构建（新脚本，幂等，走真实 API）
3. **`scripts/e2e/seed-commerce-fixtures.py`**（新增）：dev 库从未 seed 交易主数据，`MD_PRODUCT` / `CUSTOMER` / `CUSTOMER_LEVEL` 三个 entityCode 全缺。脚本按业务编码幂等构建并推进到 PUBLISHED（SoD：admin 提交 + e2eapp 审批）：
   - `MD_PRODUCT` / code=1001 / 无线鼠标 ¥99 → 打通 probe-commerce「发起支付」与 chains C1 下单；
   - `CUSTOMER` / customer_code=1001 / level_code=VIP；
   - `CUSTOMER_LEVEL` / level_code=VIP / discount_rate=0.88 → chains C1「金额含客户等级折扣」由 FAIL（199 无折扣）→ PASS（87.12）。
   - **命名坑**：`CUSTOMER_LEVEL` 实体名一旦含「客户」，会被 chains.py 的 `md_entity_id("客户","CUSTOMER")` 模糊匹配命中，撞 `discount_rate` 必填校验使 C2 假红。故实体名用中性的「等级折扣字典」，仅靠 `entityCode=CUSTOMER_LEVEL` 供 blueprint 精确点查（显示名与消费无关）。
4. 另经 API 补齐两处 seed 缺口：发布残留草稿模板 `e2e817117`（C6「模板均已发布」）、创建系统日志样本（C8「系统日志非空」）；触发 `data-sources/{id}/tables:sync` 同步 238 表（未直接消解快照判据）。
5. **catalog 快照夹具**：`LoadCatalogTablesApplicationService` 只读「tenant 0 + PUBLISHED + delivery_mode≠RUNTIME」的 meta_entity。本机 tenant 0 已发布实体 164 个全部 `delivery_mode=1`（RUNTIME），快照恒空。经 metadata API 创建并发布一个 `delivery_mode=0`（GENERATIVE）实体 `e2e_catalog_probe`，C6「元数据实体快照非空」由 FAIL → PASS。

## 三、提交范围（仓库治理）

共享 dev 树当前处于「bare 模式误置 + 202 项并行在途改动」状态（`.git/config core.bare=true` 自 11-16 被并行会话设置，`git status` 需显式 `GIT_DIR`/`GIT_WORK_TREE` + `core.bare=false` 才能读取）。本 run **只提交自有交付物**，用 `commit --only -- <路径>` 圈定，绝不 `git add -A`：

- `scripts/e2e/chains.py`（网关路由判据修正）
- `scripts/e2e/seed-commerce-fixtures.py`（交易主数据幂等造数脚本）
- `scripts/e2e/integration-report-2026-11-17.md`（本报告）

## 四、复跑命令

```bash
export BONE_E2E_BASE=http://127.0.0.1:8888/api/v1 \
       BONE_E2E_LOGIN_BASE=http://127.0.0.1:8888/api/v1 \
       BONE_E2E_CLAIM_BASE=http://127.0.0.1:8888/api/v1 \
       BONE_UI_BASE=http://localhost:3000 BONE_IAM_BASE=http://127.0.0.1:8081 \
       NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
bash scripts/e2e/run.sh lint                 # PASS
bash scripts/e2e/run.sh py-core              # 175/175
bash scripts/e2e/run.sh node-api             # 需单独跑 claim-fee-category 时带 BONE_E2E_CLAIM_BASE
bash scripts/e2e/run.sh py-sweep             # 0 真实 5xx
bash scripts/e2e/run.sh py-chain             # 全绿（依赖 §二造数夹具已构建）
bash scripts/e2e/run.sh node                 # UI 探针全 PASS
python3 scripts/e2e/seed-commerce-fixtures.py # 幂等重建交易主数据夹具
```
