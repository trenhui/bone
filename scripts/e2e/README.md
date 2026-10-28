# scripts/e2e —— BONE 端到端测试能力（现场手册）

> Agent 认知入口见 [doc/agents/04-测试与代码质量.md §6.3](../../doc/agents/04-测试与代码质量.md)（由 AGENTS.md 路由表「测试 / 质量门禁 → 04」自动加载）。
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
│   └── client.cjs      Node 侧同源常量与登录（.mjs/.cjs 探针共用）
├── *-full-chain-e2e.py 全链路用例（Python，断言制）
├── *-sweep.py / chains.py / multichannel*.py / channel_*  联调扫描与渠道链路
├── cleanup-residue.py  残留定点回收执行体（teardown.register 的落点）
├── *.mjs / *.cjs       UI 与接口探针（Playwright/直连 HTTP）
└── ui/                 浏览器探针（写新探针前必读 ui/README.md 第 0 条）
```

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

## 4. 脚本清单（按用途）

| 类别 | 脚本 | 说明 |
|---|---|---|
| 全链路 | `two-scenario-full-chain-e2e.py` | 报销/理赔/采购三样板：建模深化 + 状态机 + 回收站 + 审计（基线 87 断言） |
| 全链路 | `app-to-data-full-chain-e2e.py` | 应用→模块→模型→界面→发布→数据全生命周期 + MySQL 落库核查（基线 54） |
| 全链路 | `ui-schema-samples-e2e.py` | 协议样例对账：提交/联动强制（基线 31；直连 metadata :9001） |
| 联调 | `chains.py`、`multichannel.py`、`integration-flow-exec-chain.py` | 跨模块链路与多渠道回归 |
| 扫描 | `api-sweep.py`、`write-sweep.py`、`real-id-sweep.py`、`write_coverage.py` | 端点探活/写覆盖扫描 |
| 渠道 | `channel_simulator.py`、`channel-buyer-broadcast.py` | 模拟渠道侧回调与广播 |
| 回收 | `cleanup-residue.py` | `--suffix` 定点回收（默认 dry-run，`--apply` 才执行） |
| 探针 | `*.mjs` / `*.cjs` / `ui/*` | UI 与接口探针，统一走 `e2e_lib/client.cjs` |

基线断言数会随用例增长而变，以脚本实际输出的 `汇总` 行为准；**重构不改基线**是提交纪律。

## 5. 环境变量（`e2e_lib/config.py` 是唯一真源）

| 变量 | 默认 | 含义 |
|---|---|---|
| `BONE_E2E_BASE` | `http://127.0.0.1:8888/api/v1` | API 基址（默认走网关=真联调） |
| `BONE_E2E_LOGIN_BASE` | =BASE | 登录端点（直连 metadata :9001 时须指到 IAM :8081，9001 不承载 `/iam/**`） |
| `BONE_E2E_USER` / `BONE_E2E_PWD` | `admin` / `123456` | 主会话凭据（本地种子账号） |
| `BONE_E2E_TENANT` | `0` | 租户头 `X-Tenant-Id` |
| `BONE_E2E_KEEP_RESIDUE` | 关 | `1` = 跑完保留残留现场 |
| `BONE_E2E_MYSQL_HOST/PORT/USER/PWD/DB` | 本机 bone 库 | 第二证据源直查连接（口令走环境，不写字面量） |
| `BONE_E2E_APPROVER_USER/PWD/DEPT_ID` | 采购审批人 | 三样板 SoD 审批账号 |

> 直连后端排查示例：`BONE_E2E_BASE=http://127.0.0.1:9001/api/v1 BONE_E2E_LOGIN_BASE=http://127.0.0.1:8081/api/v1 python3 scripts/e2e/two-scenario-full-chain-e2e.py`
> 必须在 `import e2e_lib` **之前**设置（config 在导入期读环境变量）。

## 6. 常用命令

```bash
# 全链路（默认走网关，退出即自动清残留）
python3 scripts/e2e/two-scenario-full-chain-e2e.py
python3 scripts/e2e/app-to-data-full-chain-e2e.py
python3 scripts/e2e/ui-schema-samples-e2e.py

# 保留产物给 UI 探针消费（消费完手工补清）
BONE_E2E_KEEP_RESIDUE=1 python3 scripts/e2e/app-to-data-full-chain-e2e.py
node scripts/e2e/ui-app-to-data-probe.mjs
python3 scripts/e2e/cleanup-residue.py --suffix <SFX> --apply

# 残留排查：先 dry-run 看清单，再 --apply
python3 scripts/e2e/cleanup-residue.py --suffix 793158068

# 语法自检（提交前）
python3 -m py_compile scripts/e2e/*.py scripts/e2e/e2e_lib/*.py
node --check scripts/e2e/<探针>.mjs
```

## 7. 与其他测试的关系

- 单测/ArchUnit/静态分析见 [doc/agents/04-测试与代码质量.md](../../doc/agents/04-测试与代码质量.md)（§6.1/§6.2/§7）。
- UAT 演示数据门禁是 `scripts/seed/uat/95_validate.py`（8 类断言，直查库），与本目录互补：
  e2e 验**行为**，95_validate 验**数据不变量**。
- `./scripts/check.sh`（pre-commit 38 项）是提交门禁，与 e2e 独立，两者都要过。

## 8. 诊断待办（已知问题，本轮评审决定暂不修）

| 问题 | 影响 | 建议修法 |
|---|---|---|
| `api-sweep.py` 探测体写共享文件 `/tmp/_sweep_body.txt` | 两份 sweep 并发跑会互相覆盖 body（误报 500 明细） | 改 `tempfile.mkstemp` 每进程独立临时文件 |
| `ui-schema-samples-e2e.py` 无 teardown（原设计） | 每跑一次留一份后缀残留，需手工 `cleanup-residue.py --suffix` 补清 | 待定：若样例对账不再需要保留现场，补 `teardown.register` |
| `fixtures` 词表是 `scripts/seed/uat/gen.py` 的精简镜像 | 两处词表各自演化可能漂移 | 长期抽到共享词表模块（需 UAT 侧文件所有者配合） |
