# Synap Equity 技术方案

> **文档编号**：SE-TECH-001　**版本**：v0.2　**日期**：2026-10-14　**作者**：产品委员会
> **对应 PRD**：《Synap Equity 产品方案》v0.17 §6/§7/§12.1，附录 B「T0 前置缺口」
> **配套**：《Synap Equity 估值方法学 v0.2》《Synap Equity 数据源获取与验证方案 v0.2》
> **定位**：把 PRD 的「做什么」翻译为「怎么做」——模块划分、数据模型、API 契约、引擎设计、部署与安全。

---

## 1. 架构总览（六层，对应 PRD §6.1）

```plaintext
┌──────────────────────────────────────────────┐
│ 前端（响应式 Web，PC/移动浏览器）              │
├──────────────────────────────────────────────┤
│ API 层（FastAPI，REST + 结构化输出）           │
├──────────────────────────────────────────────┤
│ 跟踪验证层（档案/对账/评测）      [确定性]      │
├──────────────────────────────────────────────┤
│ 策略纪律层（模板/分档/组合）      [确定性规则]  │
├──────────────────────────────────────────────┤
│ 分析增强层（LLM 数字注入/报告）   [受控 LLM]   │
├──────────────────────────────────────────────┤
│ 估值计算层（PE/PB/DCF）          [确定性纯函数] │
├──────────────────────────────────────────────┤
│ 数据校验层（快照/口径/校验）      [确定性]      │
└──────────────────────────────────────────────┘
```

**关键边界**：只有「分析增强层」可调用 LLM；估值/策略/跟踪层为确定性代码，可脱离 LLM 独立测试与运行（PRD §6.2 计算与解释分离）。

---

## 2. 模块划分与代码结构

| 模块 | PRD 对应 | 代码包 | 说明 |
|---|---|---|---|
| D 数据 | §7.1 | `app/data` | 取数脚本、口径映射、快照写入、校验 |
| V 估值 | §7.1 | `app/valuation` | PE/PB/DCF 纯函数、区间锚定、三情景 |
| F 基本面 | §7.2 | `app/fundamental` | 五维体检指标与评分 |
| R 报告 | §7.3 | `app/report` | 数字注入清单、LLM 报告生成、引用溯源 |
| T 跟踪 | §7.4 | `app/tracking` | 研究档案、对账、提醒 |
| Q 评测 | §7.5 | `app/eval` | 评测集、指标计算、门禁 |
| S 策略 | §7.6 | `app/strategy` | 策略模板、分档纪律、组合建议 |

```text
synap-equity/
├── app/
│   ├── data/        # 取数、校验、快照
│   ├── valuation/   # 估值纯函数（无 LLM 依赖）
│   ├── fundamental/ # 五维体检
│   ├── report/      # LLM 报告（数字注入）
│   ├── tracking/    # 档案、对账、提醒
│   ├── strategy/    # 策略与纪律
│   ├── eval/        # 评测与门禁
│   ├── api/         # FastAPI 路由
│   └── models/      # 领域模型与 DTO
├── web/             # 响应式前端
├── tests/           # 单元/集成/评测
├── scripts/         # 取数脚本
└── config/application.yaml
```

---

## 3. 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | Python 3.12 | 团队可用、数据处理生态成熟（PRD §6.1 技术栈口径） |
| Web 框架 | FastAPI | 异步、自动 OpenAPI、类型校验 |
| 数据处理 | pandas | 财务指标计算便捷 |
| 存储（MVP） | SQLite | 零运维、单机足够；快照数据量小 |
| 存储（P1） | PostgreSQL（写）/ 对象存储（原始公告） | 并发与容量增长 |
| 前端 | 响应式 Web（Vue/React 二选一） | PRD §4.4 载体决策 |
| LLM | 供应商 API（结构化输出 + 工具调用） | 报告生成；**不参与估值计算** |
| 测试 | pytest | 引擎幂等/边界测试 |
| 部署 | 单机 Docker（MVP）；P1 私有化容器编排 | 机构私有化需求 |

---

## 4. 数据模型（DDL 核心表）

> 完整契约以迁移脚本为准；此处为核心表结构（含单位/时区口径，PRD §10 数据字典的升级版）。

### 4.1 快照与基础

```sql
-- 标的基础信息
CREATE TABLE security (
  code            TEXT PRIMARY KEY,      -- 股票代码，如 600519.SH
  name            TEXT NOT NULL,
  industry_sw3    TEXT,                  -- 申万三级
  shares          INTEGER NOT NULL,      -- 最新总股本快照（股）；增发/回购回溯的 as_of 股本重建由 share_history 表支撑（P1），MVP 取快照并标注
  is_financial    INTEGER DEFAULT 0,     -- 金融/重资产标记（决定主法）
  status          TEXT DEFAULT 'ACTIVE', -- ACTIVE/ST/SUSPENDED/DELISTED
  updated_at      TEXT NOT NULL
);

-- as_of 股本变动（增发/回购/送转）：方法学 §2 时点股本硬要求，防股本前视
CREATE TABLE share_history (
  code TEXT NOT NULL, effective_date TEXT NOT NULL, shares INTEGER NOT NULL,
  change_type TEXT, disclosed_at TEXT NOT NULL, source TEXT,
  PRIMARY KEY(code, effective_date)
);
-- ST/退市状态时点历史：回测剔除用，禁止用当前 status 回看历史（防前视）
CREATE TABLE status_history (
  code TEXT NOT NULL, effective_date TEXT NOT NULL, status TEXT NOT NULL, disclosed_at TEXT NOT NULL,
  PRIMARY KEY(code, effective_date)
);

-- 财务快照（不可变，重述追加新版本）
CREATE TABLE snapshot_financial (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  code            TEXT NOT NULL,
  period_end      TEXT NOT NULL,         -- 报告期末 YYYY-MM-DD
  disclosed_at    TEXT NOT NULL,         -- 披露日（= as_of 可见边界）
  revenue         REAL, net_profit      REAL,
  net_profit_ded  REAL,                  -- 扣非归母（net_profit=归母净利润，均取**累计值**；单季=本期累计−上期累计，见方法学 §2 符号约定）
  equity          REAL, total_assets    REAL, total_liabilities REAL,
  interest_debt   REAL, ocf             REAL, capex REAL,
  operating_cost REAL, ebit REAL, da REAL,            -- 营业成本/EBIT/折旧摊销（方法学 §5 DCF 与毛利率必需）
  cash REAL, goodwill REAL, accounts_receivable REAL, -- 货币资金/商誉/应收账款（FR-F1 风险指标）
  non_recurring REAL, ebitda REAL,                    -- 非经常性损益/EBITDA（方法学 §8 指标分母）
  currency        TEXT DEFAULT 'CNY',
  source          TEXT,                  -- 数据来源（A 级）
  version         INTEGER DEFAULT 1,     -- 重述版本
  verified        INTEGER DEFAULT 0,     -- 人工核对通过
  UNIQUE(code, period_end, version)
);

-- 行情快照（日频）
CREATE TABLE snapshot_price (
  code         TEXT NOT NULL,
  trade_date   TEXT NOT NULL,
  close        REAL NOT NULL,
  adjusted     REAL,                     -- 复权价（仅展示用）
  disclosed_at TEXT NOT NULL,
  PRIMARY KEY(code, trade_date)
);
```

### 4.2 档案 / 提醒 / 订阅

> **定点与合规字段（2026-10-14）**：REAL 列仅作存储/展示，**真相字段为 Decimal 字符串定点**（`fair_text`，方法学 §9 禁二进制浮点；DB 往返逐位一致用例挂 §8 门禁）；档案表新增 `ai_generated` / `disclaimer` 两合规字段（对齐《合规评估报告 v0.2》AI 标识与免责双层）。

```sql
-- 研究档案（聚合根，版本链）
CREATE TABLE research_dossier (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  code         TEXT NOT NULL,
  version      INTEGER NOT NULL,
  as_of        TEXT NOT NULL,
  method_family TEXT NOT NULL,           -- PE/PB/DCF 组合（方法族登记）
  fair_low     REAL, fair_mid REAL, fair_high REAL,
  fair_text    TEXT,                      -- Decimal 定点副本（可复算真相字段，REAL 仅展示）
  ai_generated INTEGER DEFAULT 1,         -- AI 生成标识（合规 §6 显式/隐式标识）
  disclaimer   TEXT NOT NULL DEFAULT '研究性内容，不构成投资建议',  -- 发布时免责快照
  quality_score INTEGER,
  assumptions  TEXT NOT NULL,            -- JSON：增速/目标PE/折现率等
  zone         TEXT,                     -- UNDER/FAIR/OVER（三分档）
  status       TEXT DEFAULT 'PUBLISHED',
  published_at TEXT NOT NULL
);

-- 对账记录（预测 vs 实际）
CREATE TABLE reconciliation (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  dossier_id   INTEGER NOT NULL,
  actual_period TEXT NOT NULL,
  assumption_snapshot TEXT NOT NULL,     -- JSON
  actual_value TEXT NOT NULL,            -- JSON
  deviation    REAL,                     -- 偏离度
  created_at   TEXT NOT NULL
);

-- 提醒
CREATE TABLE tracking_alert (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id      TEXT NOT NULL,
  code         TEXT NOT NULL,
  rule_type    TEXT NOT NULL,            -- PRICE_BOUND/FINANCE_SEASON/ASSUMPTION
  threshold    TEXT,
  status       TEXT DEFAULT 'ACTIVE',
  triggered_at TEXT, delivered_at TEXT,
  ack_at       TEXT
);

-- 订阅（与估值域解耦）
CREATE TABLE subscription (
  user_id      TEXT PRIMARY KEY,
  plan         TEXT NOT NULL,            -- FREE/PRO/PRO_PLUS
  quota_used   INTEGER DEFAULT 0,
  expire_at    TEXT
);
```

### 4.3 评测 / 策略

```sql
CREATE TABLE eval_case (          -- 黄金标注集
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  code      TEXT NOT NULL,
  period_end TEXT NOT NULL,
  golden    TEXT NOT NULL,        -- JSON：正确答案
  annotated_by TEXT, annotated_at TEXT
);

CREATE TABLE strategy_template (  -- 策略模板（可配置）
  id        TEXT PRIMARY KEY,     -- VALUE/GROWTH/DIVIDEND
  params    TEXT NOT NULL,        -- JSON：筛选条件/锚点/纪律
  updated_at TEXT NOT NULL
);
```

---

## 5. API 契约（核心接口）

| 方法 | 路径 | 说明 | 确定性 |
|---|---|---|---|
| GET | `/securities/{code}` | 标的基础信息 | ✅ |
| POST | `/valuations/compute` | 按 `as_of` + 参数计算估值，返回区间+假设+溯源 | ✅ 纯函数 |
| GET | `/dossiers/{code}` | 研究档案（版本链） | ✅ |
| POST | `/dossiers` | 发布结论（入库、进跟踪队列） | ✅ |
| POST | `/dossiers/{id}/reconcile` | 手动对账 | ✅ |
| GET | `/cards/{code}` | 研究卡片（六要素，数字注入） | 日志 LLM 受控 |
| POST | `/alerts` | 创建提醒（Pro） | ✅ |
| GET | `/alerts/samples` | 提醒样例（免费层） | ✅ |
| GET | `/strategy/{code}/match` | 策略匹配度（规则解释，**非交易指令**） | ✅ |
| POST | `/eval/run` | 运行评测（门禁） | ✅ |

**`/valuations/compute` 请求/响应示例**：

```json
// 请求
{ "code": "600519.SH", "as_of": "2026-04-30", "params": { "pe_lookback_years": 3 } }

// 响应
{
  "code": "600519.SH", "as_of": "2026-04-30",
  "method_family": ["PE", "PB"],
  "fair_value": { "low": 1450.0, "mid": 1680.0, "high": 1920.0 },
  "zone": "FAIR",
  "assumptions": [{ "key": "target_pe_mid", "value": 28.5, "source": "peer_median∩self_p90" }],
  "trace": [{ "field": "net_profit", "snapshot_id": 1024, "formula_id": "PE-02" }]
}
```

**契约原则**：① 所有计算接口必须带 `as_of`；② 所有数值响应带 `trace`（溯源三元组）；③ 缺假设返回 `422` 拒绝产出（PRD FR-D4）。

---

## 6. 确定性估值引擎设计

| 设计点 | 实现 |
|---|---|
| 纯函数 | `compute(snapshot, params) -> Result`，无副作用、无 IO、无时间依赖 |
| Decimal | 金额/比率用 `Decimal`，避免浮点误差（方法学 §9.2） |
| 幂等测试 | pytest 属性测试：同输入 100 次输出逐位一致 |
| 方法族登记 | 引擎返回 `method_family`，写入档案；变更需显式 diff |
| 拒绝产出 | 数据缺失/交集为空等触发 `ValuationNotApplicable`，不产出近似值 |

**LLM 集成（分析增强层）**：

```text
估值结果(数字清单) → 注入模板 → LLM 生成文字(仅引用数字) → 数字一致性校验
                                                          ↓ 校验失败
                                                     拦截重试 / 降级纯模板
```

**硬规则**：LLM 输出中的每个数字必须命中注入清单，否则拦截（PRD FR-R2）。

---

## 7. 部署与安全

| 项 | MVP | P1（B 端） |
|---|---|---|
| 部署 | 单机 Docker（Web + API + SQLite） | 私有化容器编排，数据不出机构 |
| 认证 | 简单账号/IP 限制（种子内测） | SSO/OAuth2 + 租户隔离 |
| 用户数据 | 自选池/组合加密存储，**不用于训练** | 机构内网存储 |
| 审计 | 结论发布/对账留痕 | 建议稿留痕导出（合规） |
| 密钥 | 环境变量注入，不入库不入仓 | KMS/Vault |
| 合规字段 | 卡片/档案/接口响应常驻 `notice`（非投资建议）+ `ai_generated` 标识；操作与结论日志留存 ≥1 年 | 同左，建议稿留痕导出含免责快照（合同必备条款，合规 §8） |
| 出境与 LLM | 数据不出境（本地/私有化）；报告生成 LLM 仅组织文字、数字走注入清单强制拦截（§6）；接入外部 API 前置出境评估（合规 §5） | 同左 + 模型/提示词版本化登记 |

---

## 8. 可观测性与评测门禁

| 项 | 实现 |
|---|---|
| 日志 | 结构化日志（请求/引擎/LLM 调用），含 `trace_id` |
| 指标 | 引擎调用成功率、LLM 拦截率、提醒漏报率、单用户 token 成本 |
| 门禁 | CI 中运行 `pytest` + `/eval/run`，字段提取 ≥95%、引用 ≥98%、**可复算 100%（同输入重跑逐位一致，含 fair_text 往返）** 才可发布 |
| 回放审计 | 按 `as_of` 重跑，抽查披露时间戳证明无泄漏 |

---

## 9. 演进路线

| 阶段 | 变化 |
|---|---|
| MVP | 单机 SQLite、单模板报告、PE/PB 主法 |
| P1 | PostgreSQL、DCF 自动假设、提醒自动化、B 端 POC 私有化 |
| P2 | 多市场、策略回测、Synap Core PDF 表格解析接入 |
| P3 | 财务预测模型、数据/API 输出 |

---

## 10. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.1 | 2026-10-12 | 首版：六层架构、模块划分、技术栈、核心 DDL、API 契约、引擎设计、部署与门禁 |
| v0.2 | 2026-10-14 | 业界实践复核修订：① §4.2 补定点与合规字段（fair_text Decimal 真相字段 + ai_generated/disclaimer）；② §7 补合规字段与出境/LLM 两行；③ §8 门禁补可复算 100%；④ 配套方法学联动 v0.2 |

> **待确认**：① 前端框架最终选型（Vue 3 或 React）；② SQLite → PostgreSQL 迁移时点（触发条件：并发写入 >50 QPS 或多用户）；③ LLM 供应商与降级策略（单供应商 + 纯模板兜底 vs 双供应商）。
