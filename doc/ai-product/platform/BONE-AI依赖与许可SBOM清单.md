# BONE AI 依赖与许可 SBOM 清单（法务送审版）

> **版本**：v1.0　**日期**：2026-10-15　**归口**：平台架构组
> **用途**：POC 签前法务 SBOM 评估的送审底稿；范围内依赖的许可证据、禁用清单与待确认点供法务一次性裁定。法务确认后**冻结选型**，与《BONE-AI能力中台整体方案》§4.5/§4.6 一并作为新增依赖准入依据。
> **口径**：✅=官方来源已实证；⚠️=需法务确认；⛔=禁用。第三方组件以本清单 + 机器导出 SBOM（CycloneDX）为准，两者不一致时以机器导出为新证据回填本表。

---

## 1. 一句话结论

AI 中台范围内**默认选型全部为宽松许可**（Apache-2.0 / MIT / PostgreSQL License）；规则 DSL 为 QLExpress 4.1.3 唯一引擎，存量表达式执行为 SpEL（字段/公式计算域，非规则引擎，冻结不动）；Aviator 新旧 GAV 均移出选型（零消费者死实现删除）。**禁用清单**（AGPL/自定义红线/带 CVE 的 EOL 框架）已从选型中排除并写入门禁。法务需裁定的仅 5 项（§5），均不影响第一批开工节奏。

---

## 2. 依赖总表（Java 视角：平台直接依赖）

| 组件 | 版本 | 许可 | 状态 | 使用范围 | 备注 |
|---|---|---|---|---|---|
| `com.alibaba:qlexpress4` | 4.1.3 | Apache-2.0 | ✅（2026-08 活跃维护） | bone-ai-rules 规则 DSL 唯一引擎 | 表达式追踪服务"判定路径可回放"；沙箱保持"禁止脚本与应用代码交互"（禁用 open security strategy）；存量表达式执行为 SpEL（计算域，非规则引擎），见方案 §4.3 |
| `io.github.aviatorscript:aviator` | 5.9.0 | Apache-2.0 | ⛔ 移出选型 | 无（从未启用：extension / metadata 域实际均为 SpEL） | 零消费者死实现，随 `AviatorExpressionEvaluator` 一并删除；旧 GAV `com.googlecode.aviator:aviator` 5.4.1 为 LGPL-2.1+，曾完成 GAV 升级，现整体移除，不进冻结面 |
| Apache PDFBox / Tika / POI | 现行（POI 5.3.0） | Apache-2.0 | ✅ | bone-ai-document 解析基线 | 全部已在库 |
| `pgvector`（PostgreSQL 扩展） | 随发行版 | PostgreSQL License（宽松） | ✅ | bone-ai-rag 向量检索（最终形态；实例形态推迟实测后决策） | 禁用同类 pg_search（见 §4） |
| bge-reranker 系 ONNX 模型 | 随选型 | ⚠️ 模型许可随选型确认 | ⚠️ | bone-ai-rag rerank（本地推理，数据不出域） | 选定具体模型后回填许可与来源哈希 |
| bge-m3 系 ONNX 模型（embedding） | 随选型 | ⚠️ 模型许可随选型确认 | ⚠️ | bone-ai-rag embedding（1024 维起步） | 与 bge-reranker 同族不同任务（向量化 / 重排），非重复依赖；选定后回填许可与来源哈希 |
| RAGAS（可选 sidecar） | 节奏跟进 | Apache-2.0 | ✅ | 离线评测辅助（RAG 指标） | 非主链路，可裁剪；对抗评测走 C8 对抗样本库 + CI 负向探针，不另引 promptfoo |
| MCP Java SDK（预留） | 2.0.x 复核 | ⚠️ 随版本复核 | ⚠️（未引入） | MCP Server/Client 能力（独立 POC） | CVE-2026-59279（会话无界增长）修复于 2.0.1，锁定 ≥2.0.1 |

## 3. sidecar 与模型权重清单

| 对象 | 版本/来源 | 许可 | 状态 | 备注 |
|---|---|---|---|---|
| Docling（docling-serve） | LF AI & Data 官方 | MIT | ✅ | OCR/版面 sidecar 实现一 | sidecar 契约为 HTTP REST JSON 唯一基线，不引任何一方 SDK 客户端（docling-java 等），保 Docling/PaddleOCR 适配器级互换 |
| PaddleOCR（PP-StructureV3） | 百度官方 | Apache-2.0 | ✅ | OCR/版面 sidecar 实现二，与 Docling 同契约为可互换实现 |
| MinerU | ≥3.1.0 | ⚠️ 基于 Apache-2.0 的自定义许可（>100M MAU 或 >$20M/月营收需商业授权 + 在线服务署名） | ⚠️（仅精度候选，**默认不引入**） | 私有化内部部署基本不触发门槛；若启用须法务书面确认版本与模型权重许可；对外 SaaS 不建议 |
| vLLM（部署侧推理服务） | 随锁型 | ⚠️ 随版本记录 | ⚠️ | 私有化部署组件，随模型锁型一并入 SBOM 扫描 |
| Qwen3.5 系（9B/27B/35B-A3B/122B） | 魔搭/HF 官方权重 | **27B 档两代（Qwen3.5-27B/Qwen3.8-27B）均为 Apache-2.0**（官方 LICENSE + GGUF 元数据多源实证） | ✅（27B 档） | 模型 SBOM 要求：权重来源 URL + 权重文件哈希 + 版本 commit 记录留档 |
| Qwen Max/Flash-Next 等云端档位 | 阿里云 | ⚠️ Qwen License / Qwen Community License 1.0（>100M MAU 或 >$20M/月营收需展示模型名；MaaS/AI Work Assistant 需单独授权；纯内部使用豁免） | ⚠️ | 自运营 SaaS 开放模型能力时触发 MaaS 条款，需法务；私有化客户内部使用基本豁免 |

## 4. 禁用清单（负表，门禁机器拦截）

| 组件 | 许可/安全问题 | 禁用原因 | 替代 |
|---|---|---|---|
| pg_search（ParadeDB Community） | AGPL-3.0 | 私有化分发必触 copyleft | pgvector + BM25/ES |
| PyMuPDF | AGPL/商业双授权 | 分发传染 | PDFBox/Docling/PaddleOCR |
| Marker | 权重 OpenRAIL-M（$5M 收入阈值） | 权重许可阈值条款 | 同上 |
| Dify 内核 | Apache-2.0 修改版，多租户运营需书面授权（tenant=workspace），LOGO 不可移除 | 多租户红线且私有化交付不豁免 | 不引入（编排自建） |
| Spring AI 官方 1.1.x（≤1.1.8） | 未修复 CVE（含 CVE-2026-59318 工具调用越权，多个 High，明细见 spring.io 公告）+ 已随 Boot 3.5.x 于 2026-06-30 OSS EOL | 安全 + 生命周期双重不达标 | 自建 OpenAI 兼容客户端 |
| `com.googlecode.aviator:aviator`（旧 GAV） | LGPL-2.1+ | 传染性许可 | 整体移出选型（新 GAV 亦删：实现零消费者从未启用） |

## 5. 待法务确认点（5 项，POC 签前回复）

| # | 事项 | 问题 | 影响 |
|---|---|---|---|
| 1 | bge-reranker 许可 | 选定 ONNX rerank 模型后确认权重许可与商用范围 | 选定前不进分发清单，仅内部评测 |
| 2 | Qwen 非 Apache 档位 | 自运营 SaaS 暴露模型能力是否触发 MaaS 条款 | 仅影响 SaaS 形态；私有化不受影响 |
| 3 | MinerU（若启用） | 自定义许可条款 + 模型权重许可逐条确认 | 不启用则无此项 |
| 4 | Boot 3.5.16 商业支持 | 若选商业支持（Tanzu/HeroDevs NES），覆盖范围须含 Bone 实际使用的全部 Spring 组合件 | 平台级拍板（2026-11-30 前） |
| 5 | 客户交付的 SBOM 披露义务 | 私有化交付合同中的 SBOM/许可披露条款口径 | 随合同模板 |

---

## 6. 导出与门禁流程

1. **机器导出**：`mvn -B org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom` 生成 CycloneDX SBOM；模型权重单独登记（来源 URL、SHA-256、commit）。
2. **扫描门禁**：新增/升级依赖一律过 CVE 扫描（OWASP Dependency-Check 或 Trivy）+ 本清单负表比对；命中负表或出现 high CVE 即阻断合并。
3. **法务签字**：§5 五项逐条回复后，本清单升级 v1.1（定稿），选型冻结；此后变更依赖须重走本流程。
4. **一致性纪律**：本清单组件版本与 `bone-parent` dependencyManagement 保持一致，漂移即视为未冻结变更。

---

| 角色 | 签字/日期 |
|---|---|
| 平台架构组（提交） | 2026-10-15 |
| 法务（确认） | 待签 |
