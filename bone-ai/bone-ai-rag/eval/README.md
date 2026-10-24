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
| `eval_rag.py` | 评测主程序（golden vs actual 打分 + recall 门槛门禁） |
| `golden_set.sample.json` | 金标准样例（查询 → 预期 chunkRef 集合），按真实业务语料补齐 |

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
