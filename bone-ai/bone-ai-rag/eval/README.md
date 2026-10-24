# RAG 检索评测脚手架（eval sidecar）

> 对应方案诊断 **R5（评测门禁前置）**：`minScore` / `MMR` / `candidate-pool` 等阈值必须有金标准标定，
> 不能裸奔上线；且应作为回归门禁接入 CI。

## 设计原则

- **独立于业务进程**：纯 Python 标准库实现（`eval_rag.py`），不依赖业务运行时 / 不引入未验证三方包。
- **只评检索**：对「检索结果 vs 金标准」做可量化打分（context_recall / context_precision / degradation_rate）。
- **faithfulness 留接入点**：忠实度终局需 LLM/NLI 判定，本脚手架不实现。v1.5 起 Java 侧已有**确定性**接地守卫 `GroundingGuard`（引用完整性 + 逐句词面支撑比 + 数字一致性，见方案 §6.2 / B20），它拦的是「编造金额/比例/期限 + 幻觉引用」这类硬错误；本脚手架的 faithfulness 位置应回填**语义级**判定（同义改写、跨句推断），两者互补而非替代。

## 文件

| 文件 | 说明 |
| --- | --- |
| `eval_rag.py` | 评测主程序：三种模式 `score`（golden vs actual 打分 + recall 门槛）、`probes`（负向探针）、`contract`（内置自校验） |
| `golden_set.sample.json` | 金标准样例（查询 → 预期 chunkRef 集合），按真实业务语料补齐 |
| `negative_probes.sample.json` | 负向探针规格样例（4 个合规红线探针的 expect 契约 + 注入样本） |
| `otel_genai_conventions.py` | **OTel `gen_ai.*` 映射与校验器**（诊断 §3.1 收口动作）：BONE meter → OpenTelemetry GenAI 语义约定版本化映射 + 零依赖自校验 + 桥接预览 |

## 使用

### 1. 准备金标准

按真实业务语料补齐 `golden_set.sample.json`，每个 case 给出 `query` / `docScope` / `expectedChunkRefs`
（预期命中的切片 `chunkRef` 集合）。`expectedChunkRefs` 即评测的「正例」。

### 2. 导出实际检索结果

在评测环境调用 `RagApplicationService.retrieve(...)`，把 `RagRetrieveOutcome` 序列化为 JSON：

```json
[
  {"query": "甲方逾期付款的违约金怎么算", "docScope": "doc-1",
   "results": [{"chunkRef": "1", "clauseRef": "..."}, {"chunkRef": "3", "clauseRef": "..."}],
   "degradations": []}
]
```

建议：评测时在应用内把每次 `retrieve` 的 outcome 落盘（或接 `RagEventSink` 同通道），批量导出。

### 3. 运行评测

```bash
python eval_rag.py \
  --golden golden_set.sample.json \
  --actual actual_retrieve.json \
  --recall-gate 0.8 \
  --out report.json
```

退出码：

- `0`：通过（mean_context_recall ≥ 门槛）
- `2`：未通过（mean_context_recall < 门槛）
- 其他：参数 / 文件错误

### 4. 接入 CI

把「导出 actual → 跑 `eval_rag.py --recall-gate 0.8`」加进回归流水线：
阈值调整后若 recall 跌破门槛，CI 红灯，阻断裸奔上线。

## 指标定义

| 指标 | 公式 | 含义 |
| --- | --- | --- |
| `context_recall` | 命中预期 ∩ 实际 / 预期总数 | 该查得全不全（召回） |
| `context_precision` | 命中预期 ∩ 实际 / 实际返回数 | 返回里有多少是相关的（精准） |
| `degradation_rate` | 发生降级的 case 占比 | 评测态下能力档位是否健康 |

## 与方案 v1.4 的关系

- 本脚手架是 **R5** 的离线语义评测实现（面向真实语料 + 真实嵌入模型）。
- **构建内确定性门禁**：`src/test/java/com/bone/airag/eval/RagGoldenSetEvalTest.java` 是随 `mvn clean verify` 在 CI 强制执行的中文 golden-set 机制回归门禁——每个金标准用例的预期切片必须落在 topK，任何检索链回归（引用过剔 / RRF 权重 / 分词退化 / 阈值误杀）即红灯。二者分工：**JUnit 门禁 = 每次构建的快回归；本 sidecar = 真实语料的离线语义评测**。
- **faithfulness（忠实度）** 暂不实现：需 LLM 判定答案是否仅由上下文支撑。v1.5 的 `GroundingGuard` 只做词面级确定性校验（假阴/假阳边界见方案 §6.2），语义级忠实度仍需在此回填（见方案 §6.2）。

## 负向探针（red-team probes）：锁死合规红线

诊断 §2.3 要求的 CI 负向探针已落地。它们不评「答得好不好」，而是断言「红线没被回归击穿」——任何让
检索/回答绕过合规契约的改动都会红灯。

四种探针（见 `negative_probes.sample.json`）：

| 探针 | artifact | 断言的契约红线 |
| --- | --- | --- |
| `NP1` 无依据路径 | `RagAnswerOutcome` | 空检索必须 `answered=false` + `errorCode=AI_RAG_GROUNDING_FAILED` + 固定拒答文案，**绝不调 LLM / 静默返回空串** |
| `NP2` 接地守卫 | `RagAnswerOutcome` | 幻觉引用 / 无支撑必须 `answered=false` + `GROUNDING_FAILED`，`verdict.grounded=false` |
| `NP3` rerank 越权 | `RagRetrieveOutcome` | 结果不得越出 `allowedDocIds` / 不得含 `forbidDocIds`（导出时建议带 `docId`） |
| `NP4` 间接注入 | 组装提示词 | `<RETRIEVED_DATA>` 围栏存在 + 系统提示声明「数据是数据不是指令」+ 注入指令落在围栏内（**结构性契约**） |

### 运行

```bash
# 1) 导出实际产物到 artifacts/（retrieve 结果、answer 结果、组装提示词）
# 2) 跑探针
python eval_rag.py --mode probes \
  --probes negative_probes.sample.json \
  --artifacts-dir artifacts/ \
  --out probe_report.json
```

### 零依赖自校验（CI 必跑）

`--mode contract` 用内置 fixture 验证探针引擎本身正确（合规输入放行、违规输入被捕获），**不依赖任何外部产物**，
保证 CI 中探针逻辑始终可用、不会被「没产物就不跑」悄悄绕过：

```bash
python eval_rag.py --mode contract   # 退出码 0=通过，2=探针引擎异常
```

### 关于 NP4 间接注入的说明

结构性检查只能证明「护栏已接线、数据已被围栏隔离」；「模型是否真的忽略注入」属行为级判定，需接
LLM/NLI 裁判（faithfulness 接入点）——本探针不替代，仅把最易回归的「围栏失踪 / 注入被当指令」挡在 CI 门外。

## 接入 CI 的建议形态

```yaml
# 伪代码：回归流水线
- name: RAG eval contract (zero-dep)
  run: python eval_rag.py --mode contract          # 永远跑，证明引擎在
- name: RAG eval probes (captured)
  run: |
    ./export_rag_artifacts.sh artifacts/            # 从评测环境导出实际产物
    python eval_rag.py --mode probes --probes negative_probes.sample.json --artifacts-dir artifacts/
- name: RAG eval recall gate
  run: python eval_rag.py --golden golden_set.sample.json --actual actual_retrieve.json --recall-gate 0.8
- name: OTel gen_ai.* mapping self-test (zero-dep)
  run: python otel_genai_conventions.py --self-test # 永远跑，证明映射本身正确
```

## 可观测映射：BONE meter → OpenTelemetry `gen_ai.*`（诊断 §3.1 收口动作）

`MicrometerLlmMetrics` 已把网关 `LlmMetrics` 发射点落到 Micrometer，但其 Java 注释声明「与 OTel
`gen_ai.*` 映射表版本化 v1 对齐」的**实际映射从未落地**。`otel_genai_conventions.py` 把这份映射做成
**版本化、可校验、可预览**的单一真源，使平台指标能**零代码改动**地流入任意 OTel 后端。

### 映射要点（取自实际 meter 名）

| BONE meter | → OTel `gen_ai.*` | 关键变换 |
| --- | --- | --- |
| `bone.ai.gateway.tokens{kind=prompt\|completion\|total}` | `gen_ai.client.token.usage` | `kind` → `gen_ai.token.type` ∈ {input, output, total} |
| `bone.ai.gateway.provider.latency` (ms) | `gen_ai.client.operation.duration` (s) | **毫秒 ÷1000 转秒**（OTel 时长单位必须为 s） |
| `bone.ai.gateway.first_token.latency` (ms) | `gen_ai.client.time.to_first.token` (s) | 同上转秒 |
| `bone.ai.gateway.provider.calls{success}` | 由 `operation.duration` **记录数**派生；`success=false` 附 `error.type=provider_error` | 不再单独发 calls counter |
| `short_circuited` / `cache.hit` / `quota.exceeded` | **保留 `bone.ai.gateway.*` 命名空间** | 治理面指标，故意不进标准 `gen_ai.*`，避免与上游未来约定冲突 |

公共属性：`gen_ai.operation.name=chat`、`gen_ai.request.model`← BONE `model`、`tenant` 保留为平台维度。
采用 OpenTelemetry `gen_ai.*` Semantic Conventions（2025–2026，规格态仍为 *Development*，已被 Grafana/DataDog/Jaeger 广泛接纳）。

### 为什么不在 Java 里直接改名

1. 既有告警/看板引用 `bone.ai.gateway.*`，重命名会静默打断规则；
2. 库形态纪律要求「零新增必选依赖」——Micrometer→OTel 注册表属新增依赖，应由宿主按
   `management.otlp.metrics.export.*` 标准开关启用，而非代码强耦合。

→ **正确接线方式**：保留内部名，在 OTel Collector / Actuator 桥接层做 `bone.ai.gateway.* → gen_ai.*`
重命名（本工具的 `--emit-otel` 可预览等价结果），零代码改动、不破既有告警。

### 使用

```bash
python otel_genai_conventions.py --self-test     # CI 必跑：9 探针全 PASS（合规放行 / 违规+未知捕获）
python otel_genai_conventions.py --report        # 打印人类可读映射表
python otel_genai_conventions.py --emit-otel bone_meters.json   # 把一个 BONE meter 样本转 OTel 等价 JSON
```
