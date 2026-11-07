# GAP 登记 · `RUNTIME_MODEL_COVERAGE` 双条件「不同源」可伪造风险

> 登记轮次：第 11 轮循环复核（2026-11-07）
> 状态：**已登记，未修复**（根在并行会话在途文件 `tools/_ai_native_readiness.py`，本轮不擅改）
> 严重度：中（不改变当前结论——现状仍是 `PROVIDER_NOT_CONFIGURED` BLOCKED，但埋了未来假绿通道）

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

## 四、为什么本轮不修

- 目标文件 `doc/ai-product/synapClaim/tools/_ai_native_readiness.py` 正被并行会话
  `synapclaim-ai-governance-hardening` 修改（该会话已扩至 743 行，self_test 探针 26→33）。
- 强行并改会造成共享树冲突，且该会话的 `provider 双条件` 正是其 Impact 声明范围内的事项。
- 按共享树纪律：**登记 + 给精确方案，不擅改对方在途文件**。

---

## 五、验收口径（修复后必须同时满足）

1. `self_test` 新增"三要素全伪造"探针：手写 `probeVerdict=REACHABLE` + 非零 `participationRate` +
   设置 `provider_env`，但**无 `probeEvidence`** ⇒ 必须仍 BLOCKED。
2. 新增"自洽伪造"探针：`probeEvidence` 里 `completionTokens=0` ⇒ 必须 BLOCKED（空返回不算跑过模型）。
3. 新增合规正例：真实 `probeEvidence`（httpStatus 200 + tokens > 0 + requestId 合法）⇒ 可达 SATISFIED。
4. 独立表征测试 ≥ 3 条，且做移除 `probeEvidence` 校验的变异验证（应转红）。
5. 不改动 `PROVIDER_CONFIGURED` / `PERCEPTION_MODEL_DRIVEN` 的现有归因（避免连带漂移）。

---

## 六、现状结论（不得越界）

- 当前 `RUNTIME_MODEL_COVERAGE` = **BLOCKED / `PROVIDER_NOT_CONFIGURED`**，`l3Claimable=false`，L0。
- 本 gap **不影响**当前结论；它只说明"未来若有人想伪造，存在一条低成本通道"。
- **严禁**在修复前宣称该判据"已 hardened"或"不可伪造"。
