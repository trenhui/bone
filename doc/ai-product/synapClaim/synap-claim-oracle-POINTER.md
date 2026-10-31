---
doc_id: impl.readme
role: tooling
currency: frozen
---

# synap-claim 理算引擎参考实现（双轨 oracle）· 指针文档

> **本文件不是产品代码。** 它是生产引擎 `AdjustmentEngine` 的**独立 oracle 与探针库**，价值全在「独立于被测实现」——一旦并入生产即自证失效。AI 检索时请视为 `role=tooling` 的工具资产，不要当作当前设计或待上线代码。

**真实位置（已移出文档树）：** `doc/ai-product/synap-claim-oracle/synap-claim-settlement/`

- 构建运行：`cd doc/ai-product/synap-claim-oracle/synap-claim-settlement && mvn test`（JDK 21）
- 资产：30 条性质探针（P1–P28）+ 28 个真实 TPA 场景（S1–S28）+ 19 类变异开关（`EngineFlags`）
- 常跑入口：`.github/workflows/synap-claim-oracle.yml`（独立 CI job，不并入主 reactor）
- 设计语义基线见《04-design/理算引擎设计.md》§1–§13；「现在实际怎么算」看 §14（V1 九步分层管线）
