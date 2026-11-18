# BONE 前后端全模块联调报告（2026-11-18）

> 环境：网关 `:8888` / IAM `:8081` / masterdata `:8084` / blueprint `:8082` / metadata `:9001` /
> synap-claim `:8087` / 前端壳 `:3000` + 7 个子应用在线 / MySQL `:3306`（bone 库 238 表）/ Redis `:6379`。
> 口径：所有 E2E 走真实 API（与前端同网关 `/api/v1`）；造数走真实 IAM/Metadata API（幂等自愈），
> 不直连改库、不改生产 seed、不动并行会话的在途代码。

## 一、结论

**所有分组全绿**（本 run 目标「直到所有功能正常」达成）：

| 分组 | 结果 | 说明 |
| --- | --- | --- |
| 离线层 `lint` / `selftest` / `preflight --all` | PASS | 语法门禁 + 共享层契约自证 + 造数真实性 R1–R5 + 端口/登录探活 |
| 业务层 `py-core` | **176 / 176（0 失败）** | two-scenario 91 + app-to-data 54 + ui-schema-samples 31 |
| 业务层 `node-api` | **222 / 222（0 失败）** | claim-full 181 + workbench 41 |
| 业务层 `py-sweep` | **0 真实 5xx / 38 PASS / 0 FAIL / 15 SKIP** | AI 配置治理 5/5 |
| 业务层 `py-chain` | **213 / 213（0 失败）** | chains 141 + multichannel 51 + channel-buyer 21 + integration-flow |
| UI 探针 `node`（6 个） | 全绿、无新增 console/pageerror | probe-commerce 37/37、probe-metadata-loop 8/8、其余 4 个 newFailed/failedReq 空 |
| 造数 `seed-commerce-fixtures.py` | PASS | MD_PRODUCT/CUSTOMER/CUSTOMER_LEVEL 幂等校验 + VIP 折扣下单闭环（87.12） |

## 二、本 run 定位并修复的回归

本 run 起点 HEAD=`15983dae8`，相对上次全绿（`fa05494ec`）合入了**采购履约协同切片**和 **claim 权限体系改造**：
`SUPER_ADMIN(role 1)` 被收窄为纯 claim 权限集、旧粗码 `metadata:runtime:write` 从所有角色移除、带 handler 的动作
升级为 escape（需动作级细码）。三处 E2E 夹具与新契约失配，逐个定位并修复：

### 1. `two-scenario-full-chain-e2e.py`：采购审批 403（9 项连锁失败）
- **现象**：approve/reject `COMMON_FORBIDDEN`，后续收货/付款/幂等/审计 8 项连锁 409。
- **根因**（两层门禁，经对照实验逐层确认）：
  - 入口 `@PreAuthorize` 要 `metadata:runtime:write|runtime:*:write|platform.admin` 之一 —— 审批账号 `e2e_po_appr2`
    原挂 role 1(claim)+role 3(IAM)，被改造后一个都没有；
  - escape 动作 `RuntimeAccessGuard#canInvoke` 只认动作级细码 `runtime:{entity}:{action}` —— 采购实体 code 带
    动态后缀 `purchase_order_chain_<suffix>`，细码无法在 seed 预置。11-17 全绿时这些动作 `is_escape=0`（通配写即可），
    后端升级后 escape=True 首次生效。
- **修复**：脚本新增 `_grant_purchase_escape_permissions()`——用真实 IAM API 幂等补种本次实体的
  approve/reject 细码 + 复用 `runtime:*:write` 入口码，自建固定 code 角色 `E2E_PO_APPROVER` 并挂给审批账号，
  重新登录刷新 scopes；teardown 回收本次动态权限码。**自包含验证**：先把审批账号重置回干净 role 1/3
  （移除实验残留），脚本仍能 91/91 全绿。

### 2. `chains.py`：主数据 submit/approve 403（8 项连锁失败）+ 网关路由漏登记（1 项）
- **现象**：C1/C2 主数据 submit/approve 全 403「没有该操作权限」；G 项报 `/api/v1/procurement*` 未登记网关。
- **根因**：e2esub/e2eapp 只挂 role 1，缺 `masterdata:records:write/approve`（id 43/44）；采购履约新模块的
  `FulfillmentController`(`/api/v1/procurement`) 合入时漏在网关登记路由（网关按前缀白名单转发，缺路由整体 404
  且不产生后端日志）。
- **修复**：
  - `chains.py` 新增 `ensure_md_approve_permissions()`——幂等自建角色 `E2E_MD_APPROVER` 授三项主数据权限并挂给
    两个测试账号（并集追加，不改 SUPER_ADMIN seed），在登录 sub/app 前执行以便 scopes 生效；
  - 网关 `application.yml` 补 `bone-procurement` 路由 `Path=/api/v1/procurement/** → ${BONE_PROCUREMENT_URI:…:8090}`。
    （说明：procurement 服务本机未启动，本次为路由登记修复；chains G 项为静态 Controller↔网关判据，服务上线后即可直达。）

### 3. UI 探针：前端术语/交互改版导致探针过时（非产品缺陷）
- `probe-commerce.mjs`：列表页「新建实体」→「**新建业务对象**」；侧边栏交易菜单是二级菜单，未展开时子项不在
  DOM。修复：断言前先展开「交易管理」SubMenu 再读菜单（与 gotoMenu 同口径）。37/37 全绿。
- `probe-metadata-loop.mjs`：多项前端改版——①「新建实体」→「新建业务对象」；② antd 两字按钮注入空格
  （确定→确 定、发布→发 布）；③ 字段向导显示名/名称(英文)/编码三项均必填，按 placeholder 精确定位；
  ④ 发布确认 Modal 残留需先关「关闭」按钮；⑤ 数据 Tab 切换；⑥ 行编辑图标为 `<span aria-label="edit">`、
  hover 才显示，且需等表格重渲染后取可见行。全部以正则兼容新旧文案，8/8 全绿。
- 判定依据：所有失败均为按钮/术语定位问题，功能本身正常（dump 页面可见完整新界面；对应 API 闭环 py-core 全绿；
  操作后消息为「创建成功/更新成功/已发布」、failedReq 为空）。

## 三、造数（真实 API，幂等自包含）

- 交易主数据夹具复跑（seed-commerce-fixtures.py）：MD_PRODUCT/1001、CUSTOMER/1001、CUSTOMER_LEVEL/VIP(0.88)
  三实体均复用成功，下单验证 VIP 折扣 87.12 PASS。
- 本次修复过程中，两个夹具脚本自建了自愈角色/权限（`E2E_PO_APPROVER` / `E2E_MD_APPROVER`），均为固定 code、
  幂等复用、不污染 SUPER_ADMIN seed，可在任意环境自愈。

## 四、提交范围（共享 dev 树纪律）

当前 dev 树有其他会话的在途改动（`ClaimWorkbenchController.java`、`synap-claim-independent-re-review-e2e.py`
及一个新测试文件），本 run **不碰、不提交**。仅提交自有交付物，用 scoped hooksPath + `commit --only -- <paths>`：
- `scripts/e2e/two-scenario-full-chain-e2e.py`
- `scripts/e2e/chains.py`
- `bone-platform/bone-gateway/src/main/resources/application.yml`
- `scripts/e2e/ui/probe-commerce.mjs`
- `scripts/e2e/ui/probe-metadata-loop.mjs`
- `scripts/e2e/integration-report-2026-11-18.md`（本报告）

## 五、复跑命令

```bash
source /tmp/bone-e2e-env.sh   # BONE_E2E_BASE/LOGIN_BASE/CLAIM_BASE=:8888/api/v1, UI=:3000, NODE_PATH=...
bash scripts/e2e/run.sh lint
bash scripts/e2e/run.sh py-core      # 176/176
bash scripts/e2e/run.sh node-api     # 222/222
bash scripts/e2e/run.sh py-sweep     # 0 真实 5xx
bash scripts/e2e/run.sh py-chain     # 213/213
bash scripts/e2e/run.sh node         # UI 探针（建议串行，避免多浏览器争抢）
python3 scripts/e2e/seed-commerce-fixtures.py
```
