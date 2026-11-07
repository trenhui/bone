# Bone 智能理赔 OCR 技术选型与可信文档解析架构（已采纳）

> **文档性质**：架构设计规格（已采纳）。本文件记录智能理赔 OCR 能力的选型结论、可信解析架构、质量校验机制、PoC 实施方案与 DDD 模块划分。
> **采纳来源**：基于《Bone 智能理赔 OCR 技术选型复核与最佳方案》评审意见收敛，取代此前「以 PaddleOCR 为唯一主引擎」的初步结论。
> **关联决策**：[ADR-0046](./adr/0046-claim-ocr-selection-and-trusted-parsing.md)（本文件的决策登记）。
> **最后更新**：2026-11-06

---

## 一、总体判断与采纳结论

智能理赔不应被简化为「选一个 OCR 模型」，而应作为**独立的、可审计的理赔文档智能解析与可信数据提取系统**来设计。原方案（以 PaddleOCR 为唯一主引擎 + Jev 为最终判定器）方向基本正确，但选型结论不够严谨、架构可进一步优化。

本次采纳三项核心修正：

1. **不预设单一主引擎**。区分「传统 OCR / 端到端文档 VLM / 专用票据识别模型」，按票种分别选型。
2. **Jev（或任意模型置信度）不是 OCR 结果正确性的最终判定器**。OCR 置信度、字段规则校验、交叉识别、业务一致性校验是不同层次，必须组合使用。
3. **不采用单一 OCR 引擎处理所有理赔材料**。身份证、银行卡、医疗发票、费用清单、病历、出院记录、保险单的版式、识别难点与准确性要求差异很大。

**一句话结论**：主识别采用「PaddleOCR 3.7.x（基础底座）+ GLM-OCR（VLM 主力候选）」双引擎对照，MiniCPM-OCR 1.0 + Jev 作为第三方评测与质量评估候选；整体架构遵循「专用识别 + 通用解析 + 字段级可信度 + 规则校验 + 人工审核」。

---

## 二、逐项复核结论（采纳的关键修正）

### 2.1 MiniCPM-OCR 1.0：能力被低估，但不独占主识别职责

官方资料（截至 2026-11）：
- 1.3B 参数；支持中文、英文、表格、公式、图表、印章与结构化信息抽取。
- Real5-OmniDocBench 93.68；Wild-OmniDocBench 92.39。
- RTX 5090 测试：单并发约 **1.217 页/秒**，并发 8 约 **2.133 页/秒**。

**采纳修正**：不能把 1.2 页/秒直接外推为生产吞吐上限，更不能推导为「每页固定 5～9 秒」（原推导基于 M5 Pro CPU/llama.cpp 局部环境，与官方 GPU 基准不可比）。保留其作为 PoC 对比对象，重点测试拍照、倾斜、弯曲、复杂版面和表格，但**暂不定为唯一主引擎**。

### 2.2 MiniCPM-OCR Jev：最值得保留的创新点

Jev 定位是「判断已有 OCR 结果是否可信」，而非重新识别：
- 2B 参数；对文本/公式/表格区域的 OCR 候选结果做质量评分。
- JevOCRBench AUROC 约 0.936；官方测试覆盖 MiniCPM-OCR、GLM-OCR、PaddleOCR-VL-1.6 等解析器，可插在任一主引擎后。

**采纳修正（关键）**：**AUROC 93.6% ≠ OCR 结果有 93.6% 正确率**。AUROC 衡量的是「区分正确与错误结果的能力」，不代表评分可直接作为正确概率，也不代表高分结果可无条件自动通过。官方明确要求用真实业务样本做阈值标定，并验证不同分数区间的实际正确率。

处置：保留 Jev 作为候选质量评估模型；**不将其作为唯一质量闸门**；不采用统一阈值处理所有票种；对金额、日期、身份证号、税号等关键字段必须建立**独立校验机制**。

### 2.3 PaddleOCR：首选基础设施，但版本与组件描述需升级

方向正确，但应区分组件、升级版本（截至 2026-11 已到 3.7.x，PaddleOCR-VL-1.6 于 2026-05 发布）：

| 组件 | 实际职责 | 理赔中的用途 |
|------|----------|--------------|
| PP-OCRv6 | 文本检测、文字识别（Medium 34.5M） | 身份证、发票、票据文字 |
| PP-DocLayout | 版面分析 | 文档区域、阅读顺序 |
| PP-StructureV3 | 文档结构解析 | 表格、复杂版式、结构 |
| PaddleOCR-VL-1.6 | VLM 文档理解与解析（OmniDocBench v1.6 约 96.33） | 复杂票据、病历、表格 |
| PP-ChatOCRv4 | 文档信息抽取（KIE，较上代 +15%） | 从解析结果提取业务字段 |

**采纳修正**：
- 96.33（OmniDocBench v1.6）是**通用文档解析榜单分**，**不等于**医疗票据字段准确率。通用榜单与理赔字段级准确率不是同一指标。
- 原初稿中「93.2% 忠实还原、理赔专用票据优势」等具体结论，缺乏统一、可复现的独立评测依据支撑，**不写进选型结论**；其低幻觉特性需在真实理赔样本上自证，而非直接采信。
- PaddleOCR 是最适合做基础识别与文档处理底座的候选之一，但不应认为它在所有理赔票种上都一定优于其他模型。

### 2.4 GLM-OCR：提高到第一梯队

- 0.9B 参数；CogViT 视觉编码器 + 轻量语言解码器；「版面分析 → 并行识别」两阶段。
- 支持文本、表格、公式、印章及信息抽取；支持 vLLM / SGLang / Ollama 自托管。
- OmniDocBench V1.5 94.62（SOTA 区间）；官方同环境（RTX 5090）吞吐：单并发 1.179 页/秒、P95 2.0s，与 MiniCPM-OCR 1.0 基本持平，优于 PaddleOCR-VL-1.6（0.818 页/秒、P95 2.8s）。

**采纳修正**：GLM-OCR 应与 PaddleOCR 同列第一梯队，而非仅作备选。其轻量、票据/卡证结构化 JSON 开箱即用，且官方明确面向「银行、保险行业系统」对接。

**许可注意（商业使用前必核）**：
- GLM-OCR 仓库代码：Apache-2.0；
- GLM-OCR 模型权重：MIT；
- 集成的 PP-DocLayoutV3：遵守其自身许可。
- 须分别核对模型权重、代码、依赖与再分发条款。

### 2.5 其他模型取舍

| 模型 | 评价 | 推荐用途 |
|------|------|----------|
| Qwen3-VL | 强视觉语义理解 | 高风险字段二次复核、语义理解 |
| LightOnOCR-3 | 2026-10 发布，0.8B/1B/4B，Apache-2.0，支持版面定位/文本/图表抽取 | 复杂布局、低质量图像兜底（**不应仅归类为烂图工具**） |
| RapidOCR | 轻量高效（基于 PaddleOCR 权重） | 端侧、CPU、高并发文字识别 |
| Surya OCR | 复杂文档分析 | 辅助对比 |
| Tesseract | 传统 OCR | 简单场景，不建议进主流程 |

---

## 三、分层选型

### 第一梯队：必须进入 PoC

| 候选 | 定位 | 验证重点 |
|------|------|----------|
| **PaddleOCR 3.7.x**（PP-OCRv6 + PP-StructureV3 + PaddleOCR-VL-1.6） | 首选基础底座 | 传统 OCR、版面、结构化解析，作为高性能、可控识别基础设施 |
| **GLM-OCR**（0.9B） | 首选 VLM 候选 | 医疗发票、复杂费用清单、手写、印章、结构化 JSON 抽取 |
| **MiniCPM-OCR 1.0 + Jev**（1.3B + 2B） | 质量评估候选 | 复杂文档识别对照 + OCR 结果质量打分 |

> 注：「首选基础底座」不代表 PaddleOCR 在医疗票据上的实际准确率已胜出，最终必须由真实理赔样本决定。

### 第二梯队：专项验证

- **LightOnOCR-3**：复杂布局、低质量图像。
- **Qwen3-VL**：高价值字段的视觉语义复核。
- **RapidOCR**：轻量快速识别（端侧/CPU 高并发）。

---

## 四、推荐架构（7 层 + 设计原则）

```
① 文档接入层      拍照 / 扫描件 / PDF / 电子票据 / 多页材料
② 预处理与分类     去噪、纠偏、旋转、裁剪、质量检测、票种分类、重复件检测
③ 多引擎 OCR 识别层  专用 OCR(身份证/银行卡/发票) | 通用 OCR(PaddleOCR/GLM-OCR)
                     | 复杂文档解析(PaddleOCR-VL/MiniCPM) | 专项(手写/印章/低质)
④ 结构化数据抽取    统一字段 Schema、字段值、原文、坐标、页码、识别引擎
⑤ OCR 可信度与质量校验 规则校验 | 模型质检(Jev/交叉比较) | 业务一致性 | 原图证据核验
⑥ 可信度决策路由    自动通过(高可信) | 二次识别(疑点) | 人工审核(高风险)
⑦ 理赔业务数据层    结构化理赔数据 → 责任判定 → 费用审核 → 理赔决策
```

### 四个关键设计原则

1. **专用票种优先，通用模型补充**。示例：

| 文档类型 | 优先识别方案 | 重点字段 |
|----------|--------------|----------|
| 身份证 | 身份证专用识别 | 姓名、证件号、有效期 |
| 银行卡 | 银行卡专用识别 | 户名、卡号、银行 |
| 医疗发票 | 票据专用识别 + 通用 OCR | 发票号、金额、日期、医院 |
| 医疗费用清单 | 表格解析 + OCR | 项目、数量、单价、金额 |
| 病历 | 通用文档解析 + 医疗领域模型 | 诊断、症状、治疗 |
| 出院记录 | 通用文档解析 + 医疗领域模型 | 入院、出院、诊断、手术 |
| 保单 | 文档解析 + 领域信息抽取 | 险种、责任、保额、期限 |

专用识别不必然等于采购商业 API；固定票种也可训练专用模型或开发模板解析器。

2. **OCR 识别与理赔语义理解必须分离**。OCR 只负责忠实还原原始内容；后续理赔 AI 负责「这是什么费用 / 是否属保险责任 / 是否免赔 / 是否重复报销 / 是否符合保单约定」。**不让 LLM 在 OCR 阶段直接推理、修正或补全原始数字**（VLM 的数字幻觉是理赔致命风险）。

3. **必须保留字段级证据链**。每个字段至少保存：

```json
{
  "field": "total_amount",
  "value": "12580.00",
  "raw_text": "合计金额：壹万贰仟伍佰捌拾元整",
  "page": 1,
  "bbox": [120, 350, 420, 390],
  "engine": "GLM-OCR",
  "confidence": 0.98,
  "validation_status": "passed",
  "review_status": "pending"
}
```

`confidence` 必须明确来源（OCR 自身置信度 / Jev 分数 / 经校准的字段正确概率），**三者不可混为一谈**。

4. **可信度应是字段级的，而非文档级的**。一张医疗发票可能医院名/日期/发票号正确，但金额与个付金额错误——不能因整张识别好就自动放行金额字段。

---

## 五、五层可信度机制

这是理赔 OCR 区别于普通文档 OCR 的核心。

| 层次 | 校验方式 | 典型案例 |
|------|----------|----------|
| L1 | OCR 原始置信度 | 字符识别是否清晰 |
| L2 | 规则校验 | 日期格式、金额大小写、身份证校验位 |
| L3 | 多模型交叉校验 | PaddleOCR 与 GLM-OCR 是否一致 |
| L4 | 业务逻辑校验 | 费用总额与明细汇总是否一致 |
| L5 | 原图及人工复核 | 关键金额、模糊数字、异常字段 |

**关键提醒**：多模型结果一致 ≠ 结果一定正确（不同模型可能犯相同错误），不能仅凭两模型一致就自动通过。

### 审核策略

| 字段类别 | 建议处理 |
|----------|----------|
| 普通文本 | 达到经标定的阈值后自动通过 |
| 日期、证件号码 | 格式 + 业务规则双重校验 |
| 发票金额、报销金额 | 金额关系校验，异常时二次识别 |
| 诊断、手术名称 | 医疗术语与上下文交叉校验 |
| 保单责任、免责条款 | 原文定位 + 语义抽取 + 人工确认高风险事项 |

- **不设置全局 0.95 阈值**：应经真实标注数据，针对票种、字段、风险等级分别标定。
- **金额大小写一致性**并非所有发票都适用，按票据类型判断是否启用。
- 优先控制**关键字段的错误放行率**，而非单纯追求自动审核率。

---

## 六、PoC 实施方案

### 6.1 参评模型

| 模型 | 评测定位 |
|------|----------|
| PaddleOCR 3.7.x（拆为 PP-OCRv6 与 PaddleOCR-VL-1.6 两类实验） | 基础识别 + 文档解析 |
| GLM-OCR | 轻量 VLM 文档解析 |
| MiniCPM-OCR 1.0 | 文档解析对照 |
| MiniCPM-OCR Jev | OCR 质量评估 |
| Qwen3-VL | 高风险字段二次复核 |

### 6.2 测试数据

首轮 500～1000 份**脱敏真实理赔材料**，覆盖：医疗发票、医疗费用清单、门诊/住院病历、出院记录、身份证、银行卡、保单及保险责任材料；并覆盖正常图、低清、倾斜、反光、遮挡、手写、印章、多页 PDF。同一原始样本让所有模型识别，以统一人工标注 Ground Truth 对比。

### 6.3 评测指标（关键字段错误放行率为第一优先级）

| 指标 | 重要程度 | 说明 |
|------|----------|------|
| 字符准确率 CER | 高 | 文字识别能力 |
| 字段准确率 | 极高 | 每个业务字段是否正确 |
| 金额准确率 | 极高 | 金额、大小写、明细合计 |
| 日期准确率 | 高 | 日期及时间识别 |
| 表格结构准确率 | 高 | 行列、项目与金额对应 |
| **关键字段错误放行率** | **极高** | 错误结果被自动接受的比例 |
| 自动通过率 | 高 | 无需人工介入的比例 |
| P95 处理时延 | 高 | 用户实际体验 |
| 单份理赔材料成本 | 高 | 推理 + 计算 + 人工复核成本 |

> 例：自动放行 10000 个金额字段中有 2 个错误 → 错误放行率 0.02%。这比单纯报 99% OCR 准确率更有业务价值。

### 6.4 建议验收目标（首轮工程目标，非现有模型已达指标）

| 指标 | 建议目标 |
|------|----------|
| 关键金额字段准确率 | ≥99.9% |
| 证件号码准确率 | ≥99.9% |
| 普通字段准确率 | ≥98% |
| 自动通过字段错误率 | ≤0.01% |
| 关键字段证据定位覆盖率 | 100% |
| 文档识别成功率 | ≥99% |
| 自动通过率 | 在错误率约束下尽可能提高 |

**统计纪律**：99.9% 字段准确率须明确统计口径、样本量与置信区间；小样本（如 1000 样本零错误）**不能直接宣称**错误率 < 0.01%。

---

## 七、DDD 模块划分（目标架构，分阶段落地）

OCR 能力应作为**独立领域边界**，不直接把 PaddleOCR / GLM-OCR SDK 引入理赔业务模块。

| 模块 | 职责 |
|------|------|
| Document Management | 文档上传、存储、版本、生命周期 |
| Document Classification | 文档分类、票种识别 |
| OCR Engine | 多引擎适配、识别调度 |
| Document Parsing | 版面分析、表格解析、字段提取 |
| OCR Quality | 可信度评分、规则校验、交叉验证 |
| Review Management | 人工复核、异常处理 |
| Claim Intelligence | 理赔责任判断、费用审核、理赔决策 |

核心抽象：
- `OcrEngine`：统一 OCR 引擎抽象；
- `OcrProvider`：PaddleOCR / GLM-OCR / MiniCPM 等适配器；
- `DocumentParser`：不同票种解析策略；
- `OcrQualityEvaluator`：可信度评估；
- `FieldValidationRule`：字段级校验规则；
- `ReviewPolicy`：自动通过 / 重试 / 人工审核策略。

OCR 是基础设施，理赔业务规则属于理赔领域——**不让 OCR 模块承担保险责任判断**。后续更换模型、增加新票种或接入商业 OCR 服务，均不影响理赔核心领域模型。

---

## 八、三阶段落地路线

**阶段一：建立标准 OCR 基础能力（1～2 周）**
PaddleOCR 3.7.x 作基础底座、GLM-OCR 作 VLM 对比、MiniCPM-OCR 1.0 作第三方评测；统一文档分类、字段 Schema、识别接口与结果格式；建立真实票据 Ground Truth 数据集。
*交付：OCR 模型评测报告、标准化 OCR 服务。*

**阶段二：建设可信 OCR（2～3 周）**
引入 MiniCPM-OCR Jev；建立字段级规则引擎；建立双模型交叉验证；实现自动通过 / 二次识别 / 人工复核；保存原图、字段坐标与模型版本等证据。
*交付：可信 OCR 质量控制服务。*

**阶段三：理赔领域适配（3～5 周）**
对高频票种建专用解析模板；用真实理赔数据微调或专项优化；引入医疗术语词典、ICD 编码与业务规则；优化吞吐/并发/GPU 成本；建立人工复核反馈与持续迭代机制。
*交付：面向理赔场景的文档智能解析能力。*

---

## 九、最终结论

| 角色 | 选型 |
|------|------|
| 第一选择（基础底座） | **PaddleOCR 3.7.x**（PP-OCRv6 + PP-StructureV3 + PaddleOCR-VL-1.6） |
| 重点对比（VLM 主力候选） | **GLM-OCR**（医疗票据、结构化字段抽取重点验证） |
| 质量增强 | **MiniCPM-OCR Jev**（独立质量评估，配规则引擎） |
| 高风险复核 | **Qwen3-VL**（复杂字段视觉复核，不参与原始 OCR 自动修改） |
| 核心架构 | 专用识别 + 通用解析 + 字段级可信度 + 规则校验 + 人工审核 |

合规前提：医疗/保险数据能否调用云端 API，须以实际部署环境、数据处理协议、授权与适用数据保护要求为准。自托管可降低数据外流风险，但不等于自动满足全部合规要求。

最值得长期沉淀的竞争力：① 对不同理赔材料的专业识别能力；② 对金额/诊断/费用等关键字段的可信提取能力；③ 对错误识别的自动发现与风险拦截能力；④ OCR 结果与理赔业务规则的深度结合；⑤ 可审计、可追溯、可持续优化的闭环。

---

## 参考技术资料

- PaddleOCR 官方仓库：https://github.com/PaddlePaddle/PaddleOCR
- GLM-OCR 官方仓库：https://github.com/zai-org/GLM-OCR
- MiniCPM-OCR / Jev 官方仓库：https://github.com/OpenBMB/MiniCPM-OCR
- LightOnOCR-3 官方仓库：https://github.com/lightonai/LightOnOCR

---

## 九、实施进度（骨架 + 端到端链路已实现 · 2026-11-07）

ADR-0046 决策已在 `bone-ai/bone-ai-document` 模块落地为可运行骨架，并通过 124 项单测（含 ArchUnit DDD 门禁、Spring 上下文装配守护与真实 HTTP 端到端链路 PoC）。

### 已实现的代码资产

| 层次 | 文件 | 说明 |
|---|---|---|
| domain/model | `CrossCheck`、`RiskThreshold` | L3 交叉结论枚举；按风险分级的阈值记录 |
| domain/model | `FieldReviewContext`（扩展） | 新增 `crossCheck` 信号（聚合 OCR 置信度 + 质量分 + 风险 + 交叉） |
| domain/service | `NumericAmountRule` / `DateLegalityRule` / `IdCardCheckDigitRule` / `AmountCaseConsistencyRule`（+`ChineseAmount`） | **L2 确定性规则层**：金额数值格式、日期合法性、身份证校验位、金额大小写一致性 |
| domain/service | `L3CrossValidator` | 主/次引擎字段级交叉校验（MATCH/MISMATCH/NOT_RUN） |
| domain/service | `TrustedOcrEngine`（扩展） | 七层编排 + 可选次引擎（同一字节流复用）+ 交叉信号注入 |
| domain/service | `DefaultReviewPolicy`（重写） | 由 `Map<RiskLevel,RiskThreshold>` 驱动；MISMATCH 强制人工复核；CRITICAL 缺质量分转二次识别 |
| config | `DocumentProperties.TrustedOcr` | 新增 `paddleUrl`/`glmUrl`/`minicpmUrl`/`maxBytes`/`timeoutMs` + `riskThresholds` 标定覆盖 |
| config | `TrustedOcrAutoConfiguration`（重写） | 按 URL 装配真实 `HttpOcrClient` 适配器（复用既有传输契约）、注入 4 个 L2 规则、GLM-OCR 作交叉次引擎、按标定阈值构建策略。**默认关闭** |
| domain/service | `HeuristicOcrQualityEvaluator` | 默认质量评估器（模型无关启发式：相对置信度离散度 + 证据覆盖 + 校验状态 + 空值惩罚），使 L3 质量感知路由生效（CRITICAL 不再因缺质量分永远转二次识别）；真实 Jev 标定模型以 `@ConditionalOnMissingBean` 覆盖 |
| domain/model | `RecognizedField`（扩展） | 支持可选 `page` / `BoundingBox`，多页逐字段定位 |
| domain/model | `OcrFieldEvidence.from`（扩展） | 透传页码与坐标（缺页码回退 1）；保留三参兼容构造 |
| infrastructure | `HttpOcrClient` | 解析响应 `page` / `bbox` 节点 |
| config | `TrustedOcrAutoConfiguration` | 默认装配 `HeuristicOcrQualityEvaluator` 替换 `NoOp`（**默认关闭**） |
| domain/model | `ClaimDocumentTemplate` / `RequiredField` | 按票种版式契约：必填关键字段（别名精确匹配 + 字段风险）与引擎优先顺序；字段名归一化消除 schema 差异 |
| domain/service | `ClaimDocumentTemplateRegistry` | 版式契约唯一事实源；结构化票种声明必填字段，自由文本票种（病历/出院/处方）刻意不声明以控制误报；内置值仅为骨架默认，须 PoC 校准 |
| domain/model | `OcrFieldEvidence.missing` | 漏识别字段的占位证据：空值 + 零置信 + FAILED + 强制人工复核，`[MISSING_REQUIRED_FIELD]` 审计标记 |
| domain/service | `TrustedOcrEngine`（Phase-4 扩展） | ② 票种分类（仅 GENERAL 时）与按票种引擎偏好稳定排序；⑦ 必填字段缺失拦截（在质量评估之后追加，避免污染质量分基准） |
| domain/service | `ClaimFieldRisk`（扩展） | 模板声明的字段风险优先，未声明回退全局启发式 |
| config | `TrustedOcrAutoConfiguration`（Phase-4 扩展） | 装配 `DocumentClassifier`（NoOp 安全默认）与模板注册表 Bean；旧扁平阈值显式登记不采信 + 配置非默认值 fail-fast；分级阈值合法性与序关系校验 |
| domain/model | `CalibrationSample` / `LevelCalibration` / `CalibrationReport` | 离线阈值标定的样本、单等级结论与报告载体；含 Wilson 95% 上界、最小样本量、统计证明标记与配置片段 |
| domain/service | `ThresholdCalibrator` | 网格搜索候选阈值，判定**直接复用运行时 `DefaultReviewPolicy.decide`**；零错误小样本不得宣称达标；输出证明目标所需的最小样本量 |
| domain/service | `OcrAcceptanceMetrics` | 从字段级证据链重算验收指标（自动通过率 / 人工介入率 / 关键字段 100% 证据定位覆盖率 / CRITICAL 自动通过数 / 漏字段数），聚合计数不一致时可反查 |
| infrastructure | `JevHttpQualityEvaluator` | 真实 Jev 质量分 HTTP 适配器；服务故障 / 非法 JSON / 越界分值一律质量分不可用（fail-closed），绝不用启发式分数伪装模型判断 |
| infrastructure | `HttpDocumentClassifier` | 真实票种分类 HTTP 适配器；未知票种 / 服务故障 / 文件超限一律回落 GENERAL，绝不猜测票种 |
| config | `DocumentProperties.TrustedOcr`（Phase-5 扩展） | 新增 `jevUrl` / `classifierUrl`；非空即装配上述真实适配器覆盖安全默认 |
| integration（测试） | `TrustedOcrHttpPipelinePoCTest` | 本地 HttpServer + 真实 Spring 上下文 + 真实 JDK HttpClient 跑通七层管线；含 Jev / 分类服务不可达时的 fail-closed 对照 |

### 验证结果

`mvn -o -pl bone-ai/bone-ai-document test`（绕沙箱）：**124 用例，0 失败 0 错误**。`ArchitectureTest`（11 项）确认新增代码未违反 DDD 门禁；`TrustedOcrAutoConfigurationContextTest` 以真实 Spring 上下文验证默认关闭、开启后全部端口装配、自定义分类器可覆盖、配置 URL 后真实适配器覆盖安全默认、旧扁平阈值拒绝启动；`TrustedOcrHttpPipelinePoCTest` 以本地 HttpServer + 真实 JDK HttpClient 跑通七层管线，并给出服务不可达时的 fail-closed 对照（Jev 挂 → CRITICAL 转二次识别、分类器挂 → 回落 GENERAL 且不制造假红）。

**统计纪律对照**：`ThresholdCalibratorTest` 证明 100 个「零错误」自动通过样本的 Wilson 95% 上界仍远高于 0.01% 目标，`statisticallyProven=false` 且给出证明目标所需的最小样本量（>3 万）；放宽目标到 1% 且样本 500 时才通过。避免「小样本零错误就宣称达标」这一最常见验收失真。

**修复前后对照实验**：同一份只识别出 `total_amount` / `hospital_name` 的发票，关闭模板时漏掉的 `invoice_no` / `invoice_date` 完全不可见，`autoPassRate`=0.50；启用模板后补 2 条 FAILED + 人工复核证据，`autoPassRate`=0.25。另有防误报对照：中文别名（`合计金额` / `发票号码` / `开票日期`）与 `start_date` 不被误判。

### 关键铁律落地点（对照 §四）

- **确定性规则优先**：L2 规则 FAILED → 直接人工复核，先于模型判断。
- **多模型一致 ≠ 正确**：交叉 MATCH 不自动通过 CRITICAL；MISMATCH 直接升级人工复核。
- **可信度字段级 + 风险分级标定**：`RiskThreshold` 按 CRITICAL/HIGH/MEDIUM/LOW 分别持有，禁全局 0.95；默认骨架值须由真实样本标定覆盖。
- **置信度来源显式区分**：`ConfidenceSource` 标注 OCR/质量模型/标定，禁止跨来源比较。
- **漏识别 ≠ 可放行**：票种声明的必填字段未返回时补 FAILED 证据并强制人工复核，分母计入缺失字段，自动通过率不再因「字段消失」而虚高。
- **按票种分别选型**：引擎优先顺序由票种模板单点定义（发票/证件/清单 → PaddleOCR 优先，病历/出院/保单 → GLM-OCR 优先），而非一律取装配顺序第一个。
- **误报控制**：别名经归一化后**精确**比对（不做子串匹配，`start_date` 不会被当作 `invoice_date`）；自由文本票种刻意不声明必填字段，避免 schema 差异批量制造假红、诱使使用者关掉闸门。
- **同一语义唯一口径**：阈值只认 `risk-thresholds.<LEVEL>.*`；旧扁平阈值显式登记不采信且配置非默认值即 fail-fast，杜绝「配了以为生效、实际静默忽略」。分级阈值还校验值域与「人工门槛 ≤ 自动通过门槛」的序关系。

### 待续（仅剩需真实数据 / 外部模型服务的前置项）

> 已于 Phase-5 落地：Jev 与票种分类的**真实 HTTP 适配器**（含 fail-closed）、离线阈值标定工具与统计纪律、验收指标可计算化、**编排契约级**端到端 HTTP PoC。下方待续项均无法靠代码自包含完成，须有脱敏标注数据与真实模型服务。

1. 部署并标定真实 Jev 模型服务：按票种 / 字段 / 风险用标注样本标定质量分，AUROC 仅作筛选、不得当作字段正确率；填 `jev-url` 即由 `JevHttpQualityEvaluator` 接入（也可 `@ConditionalOnMissingBean` 覆盖）。
2. 部署真实票种分类服务（CNN/VLM）：填 `classifier-url` 即由 `HttpDocumentClassifier` 接入，且仅在调用方传 `GENERAL` 时参与判定。
3. 用 500–1000 份脱敏标注样本跑 `ThresholdCalibrator` 校准 `riskThresholds`；只有 Wilson 95% 上界不超过目标的等级才可上生产。
4. 用同一批样本校准模板别名、必填字段清单与引擎偏好顺序（内置值仅为骨架默认；通用榜单分 ≠ 理赔字段级准确率，不得预置结论）。
5. 部署 PaddleOCR / GLM-OCR 服务并填 URL 开启 `trusted-ocr.enabled=true`，做**真实模型**端到端准确率验收；当前 PoC 用固定桩响应，只证明链路可跑通与故障不放行，不构成任何准确率结论。
