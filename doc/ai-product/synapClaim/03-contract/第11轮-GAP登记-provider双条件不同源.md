---
doc_id: review.round11.gap-provider
role: guide
currency: current
---

# GAP 登记 · `RUNTIME_MODEL_COVERAGE` 双条件「不同源」可伪造风险

> 登记轮次：第 11 轮循环复核（2026-11-07）
> 收敛轮次：第 14 轮（2026-11-07）
> 状态：**判据侧已收敛（方案 A）；采集侧待外部前置**——判据已要求 `probeEvidence` 自洽，但
> `/ai-provider-probe` 尚未返回 token / requestId，真实 provider 接入前该判据恒 BLOCKED（预期 fail-closed，非恒假）。
> 现状：`RUNTIME_MODEL_COVERAGE` = BLOCKED / `PROVIDER_NOT_CONFIGURED`，`l3Claimable=false`，L0。

---

## 一、问题：两个"条件"来自不同源，且都有一个可被书面伪造的入口

`RUNTIME_MODEL_COVERAGE` 要求三要素同时成立：

| # | 要素 | 读取来源 | 可否被"写"伪造 |
|---|---|---|---|
| 1 | provider 已配 | 环境变量 `provider_env`（如 `BONE_AI_GATEWAY_BASE_URL`） | ✅ 可：设个变量即"已配"（不含密钥是否有效） |
| 2 | 探活判词 `REACHABLE` | E2E 产物里的一个**裸字符串** | ✅ 可：手写一行 `probeVerdict=REACHABLE` 即"通了" |
| 3 | 运行期参与率 > 0 | E2E 产物 `participationRate` | ✅ 可：同样手写一个非零小数 |

也就是说：**三要素全部来自"可手写的产物 + 可设置的环境变量"，没有任何一项来自不可伪造的运行期事实**
（如真实 HTTP 200 + 真实模型返回 + 真实 token 计数）。这与本项目「判据不得以配置/文档字符串冒充能力就绪」
的红线（`第9轮-合理性判定矩阵` §业界实践依据、`ADR-生产数据库增量迁移策略` 同源纪律）**直接冲突**。

---

## 二、当前为什么不判红

`self_test` 已有反向探针覆盖了部分路径（`配置态全齐但探活失败 ⇒ 档位须为 L0`、
`配置态全齐但无 E2E 产物 ⇒ 档位须为 L0`），但这些探针验证的是「**缺**条件时是否正确降级」，
**没有**验证「三要素同时被书面伪造时是否仍会被识别」。前者是 fail-closed 探针，后者是
**伪造鲁棒性探针**，二者不等价。

---

## 三、精确修复方案（三个候选，推荐 A）

### 方案 A（推荐）：让第 2、3 要素必须"同源可追溯"到一次真实调用记录

- E2E 产物不再接受裸 `probeVerdict` 字符串，改为要求 `probeEvidence` 对象：
  `{ "httpStatus": 200, "model": "<实际返回的 model 名>", "promptTokens": n, "completionTokens": m, "requestId": "<供应商侧 id>" }`。
- 判据改为：`httpStatus == 200 && model 非空 && (promptTokens + completionTokens) > 0 && requestId 非空`。
- 加校验：`requestId` 必须形如供应商真实格式（正则），且 `completionTokens > 0`（空返回 = 未真正跑模型）。
- **成本**：只改 `_ai_native_readiness.py` 与 `/api/v1/claim-ops/ai-provider/probe` 的产物字段（均在并行会话在途文件内）。
- **收益**：裸字符串伪造路径直接消失——要伪造必须同时伪造一个自洽的 token 计费记录，成本远高于收益。

### 方案 B：把探活结果落到不可手改的位置（服务端内存/落库），判据只读服务端

- 探活由 `AiProviderProbeApplicationService` 执行后写入一个**服务端权威快照**（进程内存或一张 `t_ai_probe_snapshot`）。
- 判据只读该快照，不读文件产物。
- **成本**：需新表（L3 DDL 审批）或至少进程内单例 + 端点暴露；跨进程/多实例语义复杂。
- **缺点**：比 A 重，且多实例下"哪个实例的快照算数"要再定义。

### 方案 C：最保守——把第 2 要素降级为"证据指纹"，只在自测里用

- 即：`probeVerdict` 不参与 L3 判定，只作为**运维可读的诊断信息**；L3 判定要求方案 A 的 `probeEvidence`。
- **成本**：最小；**缺点**：等价于方案 A 的严格子集，不如 A 直接。

**推荐 A**：改动面最小、把"可伪造字符串"换成"自洽的计费证据"、与业界（ML Test Score 的
"模型必须有真实推理证据"、NIST AI RMF 的 MEASURE 函数）一致。

---

## 四、第 14 轮收敛范围与越界边界

- 原阻塞理由已消失：`tools/_ai_native_readiness.py` 已随提交 `1ae2809c6` 入库，不再是在途文件。
- **已落地（判据侧）**：新增 `probe_evidence_valid()` 校验 `httpStatus` / `model` /
  `provenance` / `promptTokens` / `completionTokens` / `requestId`；`RUNTIME_MODEL_COVERAGE`
  扩为四条件；`--self-test` 补 4 条伪造鲁棒性探针与 1 条档位负向探针；表征测试补 5 条
  证据束用例与 4 条源码变异验证；E2E 从同一次探活响应透传 `probeEvidence`，字段缺失写
  `null`，并加「未连通不得编造」与终态对账断言。
- **未落地（采集侧，本轮不越界）**：`ProviderProbeReport` 目前只有
  `verdict` / `configured` / `reachable` / `model` / `provenance` / `latencyMs` / `reason` /
  `action` / `note`，**不返回 token 与 requestId**。补齐需改 `AiProviderProbePort.ProviderProbe`
  与适配器契约（L2 业务变更，须双人 Review），且只能在真实 provider 接入后验证字段语义。
  本轮不改 Java 契约、不伪造这些字段。

---

## 五、验收口径（判据侧已逐项满足）

1. `self_test` 新增"三要素全伪造"探针：手写 `probeVerdict=REACHABLE` + 非零 `participationRate` +
   设置 `provider_env`，但**无 `probeEvidence`** ⇒ 必须仍 BLOCKED。✅
2. 新增"自洽伪造"探针：`probeEvidence` 里 `completionTokens=0` ⇒ 必须 BLOCKED（空返回不算跑过模型）。✅
3. 新增合规正例：真实 `probeEvidence`（httpStatus 200 + tokens > 0 + requestId 合法）⇒ 可达 SATISFIED。✅
4. 独立表征测试 ≥ 3 条，且做移除 `probeEvidence` 校验的变异验证（应转红）。✅ 5 条表征 +
   4 条源码变异（摘除 provider / 探活 / 证据束条件，以及证据束缺失分支退化成恒真）。
5. 不改动 `PROVIDER_CONFIGURED` / `PERCEPTION_MODEL_DRIVEN` 的现有归因（避免连带漂移）。✅

> 第 14 轮实跑：表征测试全绿、`--self-test` PASS；复核器仍 `SATISFIED 5 / BLOCKED 7 /
> RUNTIME_ONLY 0`、L0、`l3Claimable=false`。变异测试曾判出「证据束条件遮蔽探活条件被摘除」
> 的判别力缺口，已补单独钉住探活条件的探针修复。条数以脚本为准。

---

## 六、现状结论（不得越界）

- 当前 `RUNTIME_MODEL_COVERAGE` = **BLOCKED / `PROVIDER_NOT_CONFIGURED`**，`l3Claimable=false`，L0；判据侧收紧未改变结论。
- 裸判词假绿通道已堵：三要素全伪造但无 `probeEvidence` 仍 BLOCKED（`PROBE_EVIDENCE_REQUIRED`），成熟度档位同步回落 L0。
- **仍不得宣称该判据「不可伪造」**：证据束本身是产物字段，方案 A 只把伪造成本抬到「须伪造一份自洽的计费证据」，不是密码学不可伪造；采集侧字段落地前也无法用真实调用反查。

---

## 七、采集侧外部前置（本轮未越界）

| # | 前置 | 归属 | 为什么不可编码替代 |
|---|---|---|---|
| 1 | 真实 provider + 密钥托管 + 采购 | 平台 / 交付 | 无真实上游就没有 token / requestId 可回传 |
| 2 | `/ai-provider-probe` 契约扩展：`promptTokens` / `completionTokens` / `requestId` | 后端（L2，须双人 Review） | 改 `ProviderProbeReport` 与 Port / Adapter 属业务契约，须真实调用验证字段语义 |
| 3 | 实跑 E2E 产出完整 `probeEvidence` 并把判据翻到 SATISFIED | 交付 | 只有真实调用能证明模型上场，静态脚本不能代替 |

前置 2、3 完成前，E2E 产物里 `probeEvidence` 的 token / requestId 恒为 `null`，判据按
`PROBE_EVIDENCE_*` 阻断属**预期 fail-closed**，不是恒假——合规正例探针（构造自洽证据束）
已证明判据可达 SATISFIED。
