# scripts/e2e —— BONE 端到端测试能力（现场手册）

> Agent 认知入口见 [doc/agents/04-测试与代码质量.md §6.3](../../doc/agents/04-测试与代码质量.md)（由 AGENTS.md 路由表「测试 / 质量门禁 → 04」自动加载）。
> **方案口径（架构层沉淀）**见 [doc/architecture/Bone-测试策略.md §7](../../doc/architecture/Bone-测试策略.md)（三层沉淀 / 库分层 / 八步门禁 / 数据铁律 / 基线表；只做指针，不复制本文件正文）。
> 本文件是**操作真源**：怎么跑、怎么写、模块 API 在哪查。

## 1. 能力分层

```
scripts/e2e/
├── e2e_lib/            ← 可复用公共层（唯一真源：e2e_lib/__init__.py 模块 docstring）
│   ├── config.py       环境配置（全走环境变量，见 §5）
│   ├── http.py         HTTP 与会话（login/call/call_as/ecode/…）
│   ├── modeling.py     建模侧（create_entity/ensure_field/put_schema/validate/publish）
│   ├── runtime.py      业务侧（rec_create/child_replace/act/act_as/rec_status）
│   ├── fixtures.py     生产化测试数据（单号/金额守恒拆分/词表/串链身份，见 §3）
│   ├── report.py       断言与报告（section/check/note/finish）
│   ├── teardown.py     运行级残留自动回收（register/clean）
│   ├── mysql.py        第二证据源直查（query，防「接口 200 但没落库」）
│   ├── sign.py         渠道回调 hmac 签名
│   ├── chain_runner.py 链路通用执行器（run_steps：步骤声明 → 发请求/取变量/落断言/传下一步；见 §8）
│   ├── client.cjs      Node 侧同源常量与登录（Node 探针共用；含 registerCleanup/runCleanup 对称 Python teardown）
│   ├── fixtures.cjs    Node 侧生产化测试数据（口径对齐 fixtures.py，见 §3）
│   └── ui.cjs          共享 UI 交互层（uiLogin/shot/waitAppReady；禁止在探针内重抄登录选择器）
├── *-full-chain-e2e.py / claim-full-chain-e2e.mjs 全链路用例（断言制；理赔为 Node 侧，造数走 fixtures）
├── *-sweep.py / chains.py / multichannel*.py / channel_*  联调扫描与渠道链路
├── cleanup-residue.py  残留定点回收执行体（teardown.register 的落点）
├── lowcode-closed-loop.cjs  低代码最小闭环端到端（非 UI 探针族，见 §4.2）
├── ui/                 浏览器探针**单族**：27 个探针全为 `.mjs`（写新探针前必读 ui/README.md 第 0 条）
├── run.sh              统一入口（list/lint/selftest/preflight/py-core|sweep|chain/py/node-api|node/all）
├── lint.sh             零依赖语法门禁（py_compile + node --check）
├── selftest.py/.cjs    共享层离线自证（无需起服务；见 §4.7）
└── preflight.py        环境快检（端口 + 可选登录探活；见 §4.7）
```

**三层使用骨架**（按「要不要起服务」分层，是选命令的唯一依据）：

| 层 | 命令 | 要服务吗 | 耗时 | 用途 |
|---|---|---|---|---|
| 离线层 | `./run.sh lint` / `./run.sh selftest` | 不要 | 秒级 | 改完共享层先自证，别拿业务失败当基础设施坏 |
| 环境层 | `./run.sh preflight [--ui\|--deep\|--all]` | 只探端口 | 2 秒 | 栈没起时**立即**给出「起哪个服务」，而不是 11 个脚本各等 40s |
| 业务层 | `py-core` / `py-sweep` / `py-chain` / `node-api` / `node` / `py` | 要完整栈 | 分钟级 | 真联调；默认先过 preflight（`BONE_E2E_SKIP_PREFLIGHT=1` 可跳） |

**分层铁律**：脚本只写业务断言，基础设施一律 `import e2e_lib`，禁止再在脚本内
复制 login / call / check / teardown 样板（发现即重构回公共层）。

## 2. 新增/改造一份 E2E 的规范步骤

1. **取配置**：只从 `e2e_lib.config` 取地址与凭据（环境变量可覆盖），不硬编码端口/口令
   （硬编码 `password="..."` 会被 gitleaks 门禁拦下）。
2. **登录**：`from e2e_lib import http; http.login()` —— 失败即退出，不许静默跑成假红；
   多身份（SoD 审批）用 `login_as` + `call_as`。
3. **数据生产化**：业务值一律走 `fixtures`（可读单号/非圆整金额/守恒拆分/词表人名公司），
   每个值**只生成一次存变量**、创建与断言共用——详见 §3。
4. **落断言**：`report.section(...)` 分节、`report.check(名称, 条件, 失败详情)` 逐项落牙；
   **每条断言必须能回答「删掉对应实现它会不会变红」**，只断状态码 200 的不算数。
5. **负向探针**：缺参 400、冲突 409、坏 ETag 412、发布闸门 409 等负向用例必须成对存在。
6. **残留治理**：运行级产物（动态表/实体/账号）用 `config.run_suffix()` 打后缀 +
   `teardown.register(SUFFIX, accounts=(...))` 注册 atexit 回收；
   排查时 `BONE_E2E_KEEP_RESIDUE=1` 保留现场，跑完手工
   `python3 scripts/e2e/cleanup-residue.py --suffix <SFX> --apply` 补清。
7. **收口**：`report.finish("汇总")` 输出统计并按失败数定退出码。
8. **自检后提交**：`python3 -m py_compile scripts/e2e/<脚本>` / `node --check <探针>`，
   真跑一遍全绿，再过 `./scripts/check.sh`。

### 元组口径（公共层契约，写错会静默错位）

| 函数 | 返回 |
|---|---|
| `http.call` / `modeling.put_schema` / `publish` 等 | `(status, body)` 二元组 |
| `http.call_full`（需响应头，如 ETag） | `(status, body, headers)` 三元组 |
| `http.login` | token 字符串；失败 `SystemExit`（薄适配须捕获并返回 None 保退出码） |

## 3. 测试数据规范（生产仿真 · `e2e_lib/fixtures`）

测试数据也要过「像生产」这道关：能被人读懂、能被演示截图、能靠业务键串成一条链。
统一由 **`e2e_lib/fixtures.py`** 供给（词表口径对齐 `scripts/seed/uat/gen.py` 的
拟真/确定/守恒红线，脚本里**禁止**再手写圆整数、test123、裸随机串）。

| 要求 | 反例（旧数据） | 正例（fixtures） |
|---|---|---|
| 单号可读 | `claim_no` 无格式 | `ds.doc_no("BX")` → `BX-20261028-7069`（前缀-日期-序号，可 grep） |
| 金额像真 | `140`、`100+40` 圆整数 | `ds.amount()` → `2863.50`（非圆整、带业务量级、按品类区间取价） |
| 文案像真 | 「10 月差旅报销」 | `ds.city + ds.days_ago()` 组合 → 「福建省厦门市客户拜访差旅报销」 |
| 明细守恒 | 手写 100+40=140 易漂移 | `ds.split(total, n)` Σ 精确 == total（与 95_validate G2 同口径） |
| 负向可控 | 手凑「不等」值 | `ds.split(total, n, break_=True)` Σ == total+1.01（422 断言可预期） |
| 能串链 | 各处随机、互不相干 | `ds.customer / ds.supplier / ds.channel_*` 同数据集恒定，主表/子表/动作引用同一身份 |
| 数据老化 | 日期写死或乱填 | `ds.days_ago(k)` 由运行日倒推，读起来是「最近发生过的业务」 |
| 词表受控 | test、abc、UUID 当显示名 | 人名/公司/城市/部门/SKU/设备全部出自受控中文词典（`fx.SURNAMES` 等） |

**铁律：每个值只生成一次存变量**——API 创建、断言比对、MySQL 落库核查共用同一变量；
两处各抽一次 rng 必然对不上（假红）。422 负向用 `break_` 拆分破坏、再用守恒拆分恢复，
保持「先破坏后恢复」结构。`ds.city` / `ds.dept` 是**属性**（构造期定死），其余生成器是方法。

```python
from e2e_lib import fixtures as fx
ds = fx.dataset(SUFFIX)              # 同后缀 ⇒ 同数据（复跑可比对）
total = ds.amount()                  # 2863.50
parts = ds.split(total, 2)           # Σ == total
```

## 4. 脚本清单（全量能力目录）

> 编目原则：**目录里每个脚本必须能在这里查到**（查不到 = 不存在 = 会被重复造轮子）。
> 新增 / 合并 / 归档脚本时同步维护本表。

### 4.1 全链路回归套件（常驻 · 有断言基线）

| 脚本 | 意图 | 基线 |
|---|---|---|
| `two-scenario-full-chain-e2e.py` | 报销/理赔/采购三样板：建模深化 + 状态机 + 回收站 + 审计（经网关） | 90 |
| `app-to-data-full-chain-e2e.py` | 设备巡检工单：应用→模块→模型→界面→发布→数据全生命周期 + MySQL 落库核查 | 54 |
| `ui-schema-samples-e2e.py` | 界面协议样例对账：submitRules/linkage 服务端强制（直连 metadata :9001） | 31 |
| `claim-full-chain-e2e.mjs` | 智能理赔 A1-A9 全链：7 分支（小额直付/大额双人复核/拒赔申诉维持·推翻/补件/挂起/决策图/后督抽检）+ SoD + 负向 + MySQL 落库对账 + 额度夹具跨轮整备（可用额度回种子水位，保证复跑可绿）；造数全走 `fixtures`（同 STAMP 同数据） | 59 |

### 4.2 跨模块联调链（走网关）

| 脚本 | 意图 |
|---|---|
| `chains.py` | 跨模块 8 条链路 C1..C8 + 门禁 G（网关路由 vs 代码路径一致性） |
| `multichannel.py` | 多渠道交易域：渠道注册→库存→上架→拉单（幂等）→发货→签收 + 负向 |
| `integration-flow-exec-chain.py` | 集成模块「建流程→激活→执行→看监控」闭环 |
| `channel-buyer-broadcast.py` | 渠道买家映射 + 库存广播 Outbox |
| `channel_simulator.py` | 支付渠道模拟器（被回调方，本地联调/回归用） |
| `lowcode-closed-loop.cjs` | 低代码最小闭环端到端（PRD v0.34 W0-1~W1a + UC-01~06） |

### 4.3 端点扫描（只读/空体 · 出报告不阻断）

| 脚本 | 意图 / 分工 |
|---|---|
| `api-sweep.py` | 全模块 GET 探活，路径变量用 `1` 占位（404 多为占位假象） |
| `real-id-sweep.py` | 带真实 ID 的 GET 扫描（与 api-sweep 分工：真 404 vs 占位 404） |
| `write-sweep.py` | 写端点空体 `{}` 扫描：揪出 5xx（服务端把客户端错误报成故障） |
| `write_coverage.py` | 写端点覆盖率补测：真正落库的最小写闭环（补最后一公里） |
| `_extract_endpoints.py` | 辅助：从 Controller 源码提取端点清单（两份 sweep 的输入） |

### 4.4 UI / 浏览器探针（Playwright · 需先起前端环境）

| 脚本 | 意图 |
|---|---|
| `ui/probe-buttons.mjs` | 采集各页面真实菜单项与按钮名（探针数据源） |
| `ui/probe-routes.mjs` | 逐菜单点击导航 + 断言业务元素（DATA/EMPTY/CARDS/FORM/THIN） |
| `ui/probe-write.mjs` | 写入口深度交互：开弹窗/填表/提交/取消 |
| `ui/probe-empty.mjs` | EMPTY 页面归因：接口没数据 vs UI 没渲染 |
| `ui/probe-commerce.mjs` | 多渠道交易域 UI 巡检（含写操作闭环） |
| `ui/probe-metadata-loop.mjs` | 元数据低代码最小闭环（metadata-app 真实 UI 走通） |
| `ui/smoke-render.mjs` | 渲染冒烟：shell 经 qiankun 挂载 metadata-app |
| `ui/smoke-runtime-autoselect.mjs` | W0-1/D12 回归：/metadata#/runtime 无参入口自动选中 |
| `ui/ui-all-menu-regression.mjs` | 全菜单回归（凭据缺省值约定见 client.cjs） |
| `ui/ui-app-to-data-probe.mjs` | 全链路 UI 探针：shell 登录 → 应用→模块→模型→界面→数据完整旅程 |
| `ui/ui-full-module-crud-probe.mjs` | 全模块 CRUD 探针（`BONE_E2E_ONLY` 局部复跑） |
| `ui/ui-nine-module-deep-probe.mjs` | 九模块深探（按 label 显式填值；`ui/ui-full-module-crud-probe.mjs` 通用填表失败时的兜底，覆盖其搞不定的模块） |
| `ui/ui-create-data-probe.mjs` | 建数据表单实测（tree-select/排序号/状态等真实控件） |
| `ui/ui-personal-views-probe.mjs` | 「个人显示」运行时探针（v3 bundle 双轨收敛后） |
| `ui/ui-runtime-evidences-regression.mjs` | 运行时证据回归（failures[] 口径汇总） |
| `ui/ui-interface-tab-boundary-probe.mjs` | 实体详情界面 Tab 边界探针 |
| `ui/ui-delivery-preview-relocation-probe.mjs` | 交付预览迁位探针 |
| `ui/ui-shadowed-column-alert-probe.mjs` | 影子列告警探针 |
| `ui/ui-view-filter-persistence-probe.mjs` | 视图筛选持久化探针 |
| `ui/claim-single-nav-probe.mjs` | 智能理赔单导航回归：同域双层菜单/子项漂移（shell 与微应用菜单对账） |
| `ui/claim-functional-probe.mjs` | 理赔 9 页功能级实操：表单真实提交→附件上传→锁定→核赔工作台/进度页出现该单 |
| `ui/claim-toast-hygiene-probe.mjs` | 核赔工作台错误提示卫生：失败文案带后端真实原因、无案件不发占位查询 |
| `ui/claim-diag.mjs` | 理赔页 4xx/5xx 定向诊断（打印 URL+响应体，按菜单路径参数化） |
| `ui/micro-app-matrix-probe.mjs` | 微应用矩阵加载巡检：逐入口实证「微应用加载失败」类故障 |
| `ui/ui-approval-sendback-probe.mjs` | 审批「驳回退回指定节点」真实点击回归（原地返工、不产生 409 死单） |
| `ui/ui-approval-suspend-reason-probe.mjs` | 挂起原因可诊断：describe suspendReason 渲染进详情告警 |

> 2026-10-28 单族化收敛：根目录 12 个 `ui-*.mjs` 全部移入 `ui/`、存量 8 个 `ui/*.cjs` 统一转 `.mjs`，
> 现 **UI 探针只有 `ui/` 一族、全为 `.mjs`**（见 §8 收敛记录）。

### 4.5 界面设计器探针（参数化单入口）

| 脚本 | 意图 |
|---|---|
| `ui/ui-designer-probe.mjs` | 6 场景合一：`--scenario three\|n4n5\|n7\|perf\|production\|ux\|all`。three=三样板切换/子表合计/协议自检，n4n5=控件校验规则+样例数据预览，n7=角色身份变体，perf=大数据量性能，production=生产 DOM 活体，ux=UX 优化批次回归 |
| `n7-mutation-verify.sh` | n7 场景的变异验证辅助（断言牙齿核对） |

### 4.6 归档与残留治理

| 路径 | 说明 |
|---|---|
| `cleanup-residue.py` | `--suffix` 定点回收执行体（默认 dry-run，`--apply` 执行） |
| `run.sh` / `lint.sh` | 统一入口与零依赖语法门禁（用法见 §4.7 与 §8 目录纪律） |

### 4.7 诊断脚手架：先自证、再快检、最后跑业务

三个脚手架解决的是同一类问题——**「红了到底是代码坏了、还是环境没起、还是基础设施本身坏了」**。
以前这三种情况在日志里长得一模一样（都是 `Connection refused` 或一堆 ✗），只能靠人推断。

| 命令 | 判什么 | 怎么判断有牙 |
|---|---|---|
| `./run.sh selftest` | `fixtures` 守恒、`chain_runner` 判据、`ui.cjs`/`client.cjs` 契约、JUnit 双端可解析、纪律棘轮 | 无需服务；**变异验证过**：改坏 `split` 尾条 / 去掉转义 / 去掉路径模板替换，对应断言精确转红 |
| `./run.sh preflight [--ui\|--deep\|--all]` | 端点 TCP 可达（网关/登录/前端/MySQL），`--deep` 加真实登录探活 | 指向死端口必转红并给出「起哪个服务」 |
| `./run.sh lint` | 只查语法（`py_compile` + `node --check`），**不查行为** | —— |

**JUnit 机器可读结果（CI 用，零侵入）**：设 `BONE_E2E_JUNIT_DIR=/dir/` 后，
Python 侧（`report.summary()` + atexit）与 Node 侧（`makeChecker()` + `process.on('exit')`）
都会把断言写成 `<dir>/<套件名>.xml` —— **20 个 UI 探针与 11 个 Python 脚本一行都不用改**。
失败详情作为 `<failure message>`，特殊字符已转义；不设该变量时零副作用。

> 为什么是「目录 + 每套件一个文件」而不是单个固定路径：一次 CI 会跑几十个脚本，共用文件会
> **互相覆盖**（本仓实测踩到：Node 自检的 2 条断言把 Python 侧 34 条覆盖成 2 条，CI 只看到最后
> 一个脚本）。按套件分文件既无竞争，也让每个脚本的结果可独立定位；`selftest.py` 有专门断言锁死这个行为。

**纪律棘轮（`selftest.py` 的 `INLINE_LOGIN_BASELINE`）**：把「禁止在探针内重抄登录选择器」
从文档口号变成机器门禁——仍内联浏览器登录的探针数**只可收缩**。当前基线 **3**
（20 个探针已迁到 `uiLogin`，剩 3 个因依赖 `fixtures.cjs` 数据集构造待逐个迁移；
2026-10-28 首批迁 13 个后棘轮由 5 下调至 3，再迁一个就再下调一格）。
另有一条硬门禁：探针内不得自定义 `uiLogin`/`login` 函数（必须 import `ui.cjs`）。
与 `doc-code-symbols-baseline.json` 的「只可收缩」是同一条纪律。

基线断言数会随用例增长而变，以脚本实际输出的 `汇总` 行为准；**重构不改基线**是提交纪律。

> **目录纪律（2026-10-28 整合）**
> - 一次性诊断探针（`diag-*` / `debug-form.mjs`，硬编码特定实体编码、针对某次发布的「快速实证」）：首轮整合归档至 `archive/`，**第二轮（2026-10-28 收口）确认无复用价值后整体删除**——它们不是回归资产、不进任何套件，结论以 Git 历史为准。新增可复用 UI 用例一律放进 `ui/`（2026-10-28 单族化后不再落根目录）。
> - `run.sh` / `lint.sh`：统一入口与零依赖语法门禁。各 agent / CI 无需记住每个脚本名——`./run.sh list`（看有什么）、`lint`、`selftest`、`preflight`、`py-core|py-sweep|py-chain|py`、`node-api|node`、`all`（默认 = lint+selftest+preflight+py）、`<脚本名>`（指定脚本）。
> - 环境变量唯一清单见 [`.env.example`](.env.example)；`config.py` 与 `client.cjs` 的默认值须保持同步。

## 5. 环境变量（`e2e_lib/config.py` 是唯一真源）

| 变量 | 默认 | 含义 |
|---|---|---|
| `BONE_E2E_BASE` | `http://127.0.0.1:8888/api/v1` | API 基址（默认走网关=真联调） |
| `BONE_E2E_LOGIN_BASE` | =BASE | 登录端点（直连 metadata :9001 时须指到 IAM :8081，9001 不承载 `/iam/**`） |
| `BONE_UI_BASE` / `BONE_IAM_BASE` | `:3000` / `:8081` | 前端壳层 / IAM 直连（UI 探针与 `preflight --ui` 共用） |
| `BONE_E2E_USER` / `BONE_E2E_PWD` | `admin` / `123456` | 主会话凭据（本地种子账号） |
| `BONE_E2E_TENANT` | `0` | 租户头 `X-Tenant-Id` |
| `BONE_E2E_KEEP_RESIDUE` | 关 | `1` = 跑完保留残留现场 |
| `BONE_E2E_JUNIT_DIR` / `BONE_E2E_JUNIT_SUITE` | 关 / 脚本名 | JUnit 输出**目录**与套件名（CI 消费，见 §4.7） |
| `BONE_E2E_SKIP_PREFLIGHT` | 关 | `1` = `run.sh` 业务层分组跳过快检 |
| `BONE_E2E_MYSQL_HOST/PORT/USER/PWD/DB` | 本机 bone 库 | 第二证据源直查连接（口令走环境，不写字面量） |
| `BONE_E2E_APPROVER_USER/PWD/DEPT_ID` | 采购审批人 | 三样板 SoD 审批账号 |

> 直连后端排查示例：`BONE_E2E_BASE=http://127.0.0.1:9001/api/v1 BONE_E2E_LOGIN_BASE=http://127.0.0.1:8081/api/v1 python3 scripts/e2e/two-scenario-full-chain-e2e.py`
> 必须在 `import e2e_lib` **之前**设置（config 在导入期读环境变量）。

## 6. 常用命令

```bash
# 先看有什么（agent 第一条命令）
./scripts/e2e/run.sh list

# 改完共享层先自证（不需要起任何服务，秒级）
./scripts/e2e/run.sh selftest

# 栈没起时先快检（2 秒给出「起哪个服务」，而不是 11 个脚本各等 40s）
./scripts/e2e/run.sh preflight --all

# 全链路（默认走网关，退出即自动清残留）
./scripts/e2e/run.sh py-core

# 智能理赔全链（Node API 侧，仅需网关；造数走 fixtures，退出即自动落 JUnit/汇总）
./scripts/e2e/run.sh node-api

# 保留产物给 UI 探针消费（消费完手工补清）
BONE_E2E_KEEP_RESIDUE=1 python3 scripts/e2e/app-to-data-full-chain-e2e.py
node scripts/e2e/ui/ui-app-to-data-probe.mjs
python3 scripts/e2e/cleanup-residue.py --suffix <SFX> --apply

# 残留排查：先 dry-run 看清单，再 --apply
python3 scripts/e2e/cleanup-residue.py --suffix 793158068

# CI：语法门禁 + 离线自证 + 全量业务，结果按套件落 JUnit 目录
BONE_E2E_JUNIT_DIR=/tmp/bone-junit ./scripts/e2e/run.sh all

# 语法自检（提交前，等价 ./run.sh lint）
python3 -m py_compile scripts/e2e/*.py scripts/e2e/e2e_lib/*.py
node --check scripts/e2e/<探针>.mjs
```

## 7. 与其他测试的关系

- 单测/ArchUnit/静态分析见 [doc/agents/04-测试与代码质量.md](../../doc/agents/04-测试与代码质量.md)（§6.1/§6.2/§7）。
- UAT 演示数据门禁是 `scripts/seed/uat/95_validate.py`（8 类断言，直查库），与本目录互补：
  e2e 验**行为**，95_validate 验**数据不变量**。
- `./scripts/check.sh`（pre-commit 38 项）是提交门禁，与 e2e 独立，两者都要过。

## 8. 诊断与整合记录

> 2026-10-28 整合分两轮：第一轮把散落脚本沉淀为「可复用库 + 临时调试区」两层并补通用能力；第二轮（收口）删除冗余/一次性脚本、把残存内联样板收口进公共层。

**已修复 / 已整合**

| 问题 | 处置 |
|---|---|
| 根目录散落 12 个一次性调试脚本（`diag-*` + `debug-form.mjs`，硬编码实体编码 `insp_1792042722480` 等），污染可复用库、README §4 未登记 | 首轮归档至 `archive/`（3 个 runtime 枚举探针合并为 `diag-runtime-enum.mjs`）；**第二轮收口确认无复用价值后整体删除 `archive/`**——非回归资产，结论以 Git 历史为准 |
| `chains.py` 硬编码 `GW` / `ADMIN`（`"admin","123456"`），违反 §2.1「取配置只从 e2e_lib.config」 | 改读 `config.BASE.rsplit("/api/v1",1)[0]` 与 `(config.USER, config.PWD)`；文档运行路径去绝对化 |
| Node 探针无对称 teardown：UI 探针在浏览器内创建的实体/记录/账号退出后无自动回收 | `client.cjs` 新增 `registerCleanup(fn)` / `runCleanup()`（对称 Python `teardown.py`，探针 finally 中 await） |
| 无统一入口，各 agent 须记住每个脚本名；无 CI 可消费的语法门禁 | 新增 `run.sh`（lint/py/node/<脚本>/all）+ `lint.sh`（py_compile + node --check，零依赖） |
| 配置项分散在 `config.py` 与 `client.cjs`，两处默认值易漂移 | 新增 `.env.example` 作为变量单一清单，并在两处注释交叉引用 |
| 6 个 `ui-designer-*` 探针各起炉灶（同为 Playwright + client.cjs，骨架重复），且 README 未编目过半脚本（34/59 查不到） | 合并为参数化单入口 `ui-designer-probe.mjs --scenario <three\|n4n5\|n7\|perf\|production\|ux\|all>`（断言与选择器逐字保留，three/n7 已实跑全绿）；§4 升级为**全量能力目录**，目录内每个脚本必须可查 |
| **第二轮（2026-10-28 收口）删除冗余** | |
| `ui-two-scenario-probe.mjs` 内联硬编码 `'admin'/'123456'` 凭据（违反 §2.1、gitleaks 风险）+ 与 `ui-app-to-data-probe.mjs` / `lowcode-closed-loop.cjs` 重叠 | 删除；功能由上述两脚本覆盖，且消除硬编码口令 |
| `probe-metadata-loop-api.cjs` 与套件内 canonical `ui/probe-metadata-loop.mjs` 同目的最小闭环 | 删除（canonical 以 `ui/probe-metadata-loop.mjs` 为准，已接入 `run.sh node`） |
| `probe-hot-column.cjs` 仅 `console.log` JSON 无断言门禁、含 62s TTL 等待、与 `ui-create-data-probe.mjs` 重叠 | 删除（慢且无 gate，价值由 create-data 探针覆盖） |
| `ui-nine-module-deep-probe.mjs` 与 `ui-full-module-crud-probe.mjs` 同目 | **保留**：其头注释明确是通用填表失败时的兜底，覆盖 9 个通用探针搞不定的模块，属独特价值 |
| **第二轮（2026-10-28 收口）整合重构** | |
| `write_coverage.py` 内联 `GW` / `ADMIN=("admin","123456")` 且自包 `http()`（与 `chains.py` 同类违反 §2.1） | 改读 `config.BASE.rsplit("/api/v1",1)[0]` 与 `(config.USER, config.PWD)`，复用 `e2e_lib.http`（零硬编码、gitleaks 友好） |
| `ui/probe-commerce.mjs` 只取 `USER` 却内联口令猜测 `['123456','admin123','Admin@123']` | 改用 `client.cjs` 的 `PWD`（与 §2.1 一致，去猜测逻辑） |

**2026-10-28 第三轮收口：三项 L2 已落地**

| 问题 | 处置 |
|---|---|
| Node 侧无 `fixtures` 等价物（探针手写 `inspector:'实证'`、圆整金额、裸单号） | **已抽 `e2e_lib/fixtures.cjs`**：可种子化 PRNG（FNV-1a + mulberry32），方法名与 `fixtures.py` 同名同义（`doc_no`/`amount`/`split`/`person`/产物身份 `customer`·`supplier`），跨进程同 seed 同值已实测；`amount` 非圆整、`split` 分位守恒、`break_=true` 恒差 1.01 |
| UI 探针两族并行（root `ui-*.mjs` 12 + `ui/*.cjs` 8，模块系统不一致） | **已单族化 + 登录去重**：全部并入 `ui/` 统一 `.mjs`；登录收敛为 `e2e_lib/ui.cjs` 的 `uiLogin(page)`（另含 `shot`/`waitAppReady`），已 5 个探针改调（`ui-app-to-data` / `ui-nine-module-deep` / `probe-commerce` / `ui-personal-views` / `ui-designer` 4 份登录副本合 1）；`run.sh node` 分组同步枚举 `ui/probe-*.mjs`。**菜单点击/弹窗判定片段有意不抽**：各探针超时口径（递增倍数不同）与弹窗判据（`:visible` vs DOM 存在）本就不同，强抽会改断言语义，故只收敛登录/截图/就绪三件套 |
| `chains.py` / `multichannel.py` 单体大文件，链路定义与断言内联 | **已抽 `e2e_lib/chain_runner.py`**：链路仅声明「步骤字典 + 判据数据」，`run_steps` 统一执行「发请求→取变量→落断言→传下一步」；`chains.py` C1–C9 与 `multichannel.py` 均改为数据驱动（已用 fake http 验证路径模板/`extract`/`status`·`eq`·`page` 断言行为） |

**2026-10-28 第四轮：可诊断性（从「能跑」到「能定位」）**

> 前三轮解决「越跑越乱、重复多、配置散」；这一轮解决**故障归因**：红了到底是代码坏了、
> 环境没起、还是测试基础设施本身坏了。三者此前在日志里长得一模一样。

| 问题 | 处置 |
|---|---|
| 栈没起时 11 个 Python + 20 个探针**各等 40s 超时**，日志满屏 `Connection refused`，只能靠人推断 | 新增 `preflight.py`：端点 TCP 快检（2s）+ `--deep` 真实登录探活，失败直接给「起哪个服务」的可执行提示，退出码非零；`run.sh` 业务层分组默认先过它（`BONE_E2E_SKIP_PREFLIGHT=1` 可跳） |
| `lint.sh` 只查语法，**语法通过 ≠ 行为正确**（`split` 守恒、路径模板替换、JUnit 转义坏了都照样「绿」） | 新增 `selftest.py` + `selftest.cjs`：fake http 跑 `chain_runner`、双实例验 `fixtures` 确定性、子进程验 JUnit 可解析、验 `client.cjs` 导出面与「导入期不登录」 |
| 结果只有人读控制台，CI 无法消费；`client.cjs` 探针各自 `process.exit`，无处插入报告逻辑 | `report.py` 的 `summary()`+atexit 与 `client.cjs` 的 `makeChecker()`+`process.on('exit')` 在 `BONE_E2E_JUNIT_DIR` 下自动写标准 JUnit XML（**每套件一个文件**，避免多脚本互相覆盖）——**31 个脚本一行未改**即获得机器可读结果 |
| 「禁止在探针内重抄登录选择器」只是文档口号，无人守 | 升级为机器门禁：`selftest.py` 的 `INLINE_LOGIN_BASELINE` 棘轮（只可收缩）+ 硬断言「探针内不得自定义 `uiLogin`/`login`」 |
| `config.py` 缺 `UI_BASE`/`IAM_BASE`（只在 `client.cjs` 有），而 `.env.example` 标注为「仅 Node 使用」——真源声明与实际不符 | `config.py` 补齐 `UI_BASE`/`IAM_BASE`，`preflight --ui` 直接复用；`.env.example` 改为「UI 探针与 preflight 共用」 |
| `run.sh` 的 `py` 只跑 3 个脚本，**另外 8 个 Python 脚本（chains/sweep/渠道）没有任何分组入口** | 分组补全为 `py-core`(3) / `py-sweep`(4) / `py-chain`(4) 覆盖全部 11 个，并新增 `list` 自描述；分组失败不中断其余分组、聚合退出码 |

**2026-10-28 第五轮：方案沉淀 + 外部漂移适配**

| 问题 | 处置 |
|---|---|
| 方案只活在本 README，架构层文档无从引用（`Bone-测试策略.md` 只覆盖单测/契约/E2E 分层，没有 scripts/e2e 的操作口径） | 架构层新增 [Bone-测试策略.md §7「端到端可复用测试方案」](../../doc/architecture/Bone-测试策略.md)：三层沉淀入口（04 §6 认知 → README 操作 → e2e_lib docstring 真源）、库四层职责、八步门禁、数据铁律、基线表（90/54/31）——**只做指针不复制正文**；README 顶部补回链 |
| `perf` 场景度量中断仍返回 0（「有产出即通过」的假绿口子，违反 §9.7 退出码即契约） | `ui-designer-probe.mjs` 加 `failed` 中断标记，任一档失败或 `table.length < SIZES.length`（档位产出不全）一律退出码 1 |
| **教训：共享无后缀夹具实体会被并行种子演化**——`expense_reimbursement_item` 因协议样例 `props.childEntity` 固定引用此名无法加运行后缀，2026-10-28 并发会话迁移种子给它补必填「消费说明」`note`，`ui-schema-samples-e2e.py` 与 `two-scenario-full-chain-e2e.py` 明细负载没带 → 400 拦下全部明细写入、下游 submit 断言级联假红（31→22、90 套件同病） | 两套件明细行构造补 `note`（每个值只生成一次存变量、正负两版共用），重跑 31/31、90/90 恢复；**复用铁律**：写共享（无运行后缀）实体的负载前，先直查 `meta_field` 确认必填集，种子给共享实体加必填字段必须显式适配所有套件负载 |

**仍开放（待后续）**

| 问题 | 影响 | 建议修法 |
|---|---|---|
| `fixtures` 词表三处镜像（`gen.py` 精简镜像 / `fixtures.cjs` 内嵌镜像）——**已收敛单源，本行仅存档结论** | 三处各自演化可能漂移的隐患已消除：词表唯一存放处 = 共享 JSON | **已落地**：`gen.py` / `fixtures.py` / `fixtures.cjs` 同读 **`scripts/seed/uat/wordlist.json`**（分别按 `HERE` / `__file__` / 模块相对 `require` 定位，均不依赖运行 cwd）；历史口径差异以 `*_e2e` 键在 JSON 内按原文显式保留，内容零变化断言 36 键全绿 |
| `ui/` 内仍有 **3** 个探针内联浏览器登录（`probe-metadata-loop` / `ui-runtime-evidences-regression` / `lowcode-closed-loop.cjs`，均为 fixtures 依赖探针、待逐个实跑迁移） | 与 ui/README §0 纪律仍不一致 | 逐个触碰时迁移（**不批量盲改**：无法实跑全栈时批量替换选择器 = 假绿风险）；新增/重构一律 `import { uiLogin } from '../e2e_lib/ui.cjs'` 并传 `page`。**已由 selftest 棘轮托管**：迁移一个就把 `INLINE_LOGIN_BASELINE` 下调一格（本轮已从 16 收到 3） |

## 9. 整合与复用：设计原则与 Why

> 目标：**各 agent 拿到 `scripts/e2e/` 就能直接复用，不必重新推导样板、不必猜哪个脚本是权威**。

1. **测试套件是「库」不是「脚本堆」**（xUnit / Playwright 最佳实践）。基础设施（登录、HTTP、配置、teardown、断言收集）只应有一份，所有用例 import 它。
   本目录的 `e2e_lib/`（Python）与 `e2e_lib/client.cjs`（Node）就是这份唯一真源；脚本只写**业务断言**。
2. **关注点分离 = Page Object / Driver 模式**。把「怎么连、怎么登、怎么回收」从「测什么」里剥出来，用例才易读、易维护、易让 agent 生成。
3. **一次性调试脚本禁止留在可复用库**。它们硬编码某次发布的实体编码、针对瞬时状态「快速实证」，既不能被复跑比对，又让 agent 误以为它们是权威入口 → 故直接删除（曾短暂归档至 `archive/`，收口时整体移除）。
4. **配置即环境（12-Factor）**：地址/口令/租户全走环境变量，脚本零硬编码。切环境改 `.env` 而非改脚本；硬编码口令还会被 gitleaks 拦下。
5. **可选择性执行（pytest markers / jest tags 思路）**。用 `run.sh` 的 `py`/`node`/`<脚本>` 分流，agent 可按模块只跑相关链路，不必全量；CI 用 `lint.sh` 先卡语法。
6. **跨语言对称**：Python 有 `teardown` + `fixtures`，Node 也该有等价物，否则 UI 探针会留下无法自动回收的脏数据，且数据不「像生产」。
7. **退出码即契约**：每个脚本 `report.finish()` / `makeChecker().report()` 都按失败数定退出码，`run.sh` 聚合，供 CI 与编排判定——没有「跑通即验收」的假绿。

> 更深一层的整合（Node fixtures、UI 探针单族化、链路数据驱动）已于 2026-10-28 第三轮全部落地（见 §8）；方案整体口径已沉淀至 `doc/architecture/Bone-测试策略.md §7`。
