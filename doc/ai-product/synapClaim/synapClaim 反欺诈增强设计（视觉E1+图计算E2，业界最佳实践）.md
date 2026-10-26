# synapClaim 反欺诈增强设计（视觉 E1 + 图计算 E2，业界最佳实践）

> 本文是《synapClaim 依赖 BONE-AI 能力图谱与增强方案（企业级串联）》§3/§4 的工程化落地详设，
> 对齐《BONE-AI 能力中台整体方案》C12-visual / C12-graph 与《业务规则书（Rulebook）》R-FRAUD-05/08/09/10/11。
> **定位**：V1.0 Phase B 增量，不挤占 MVP I0–I4 容量矩阵；所有命中走"系统建议 + 人工确认"（`SUGGEST_REJECT`），AI 不碰钱、不自动拒赔。
> **铁律**：synapClaim domain 仅经 `VisualForensicsPort` / `GraphFraudPort` 消费能力，禁止直接 import `bone-ai-visual` / `bone-ai-graph` 任何类。

---

## 0. 范围与契约落点

| 增强项 | 平台能力 | 承载规则 | 决策图节点 | 端口 |
|---|---|---|---|---|
| E1 视觉反诈 | C12-visual（平台新增） | R-FRAUD-08 | `EvidenceLoad`(视觉) + `FraudScreen` | `VisualForensicsPort` |
| E2 图计算反欺诈 | C12-graph（平台新增） | R-FRAUD-05(升格)/09/10/11 | `FraudScreen` | `GraphFraudPort` |
| E2 预警闭环 | C9 提醒预警 | 命中即预警 | `FraudScreen` | `NotificationPort` |
| E2 证据留存 | C4 快照审计 | 全命中入 trace | `FraudScreen` | `TracePort` |

> 为何先做 E1/E2：理赔欺诈损失中"重复报案/PS 翻拍/团伙聚集"占比最高，且二者是 Net-new 能力（既有 `R-FRAUD-01~07` 仅覆盖单案规则）。行业实证：平安财险以 13 模型 + 208 规则构建反欺诈体系；太保/泰康以关系图谱做团伙识别；国寿"识图"以影像相似度拦截重复报案；众安以"图像 + 关系图谱"联合反诈。本设计对齐该范式，把最成熟的两条能力从 V2 提前到 V1.0 Phase B。

---

## 1. E1 视觉反诈（Visual Forensics）— R-FRAUD-08

### 1.1 业界实践对标

- **国寿"识图"**：对医疗票据/病历影像提取指纹，跨历史案件检索相似影像，拦截"一票多赔/翻拍 PS"。
- **众安图像反诈**：发票/病历影像做篡改检测（ELA + 噪声一致性）+ 跨案重复检索联合规则。
- **平安财险图像篡改检测**：JPEG 网格不一致、EXIF 元数据异常、生成式修补痕迹识别。
- **经验区间（需以本平台标注集实测校准，非承诺值）**：医疗理赔中翻拍/PS 类可疑影像行业经验占比约 3%–8%，团伙案中跨案复用影像比例显著更高——故"跨案相似度"是最高 ROI 信号。

### 1.2 能力分解

| 子能力 | 方法 | 输出 |
|---|---|---|
| 感知哈希 | pHash（DCT 低频）/ aHash / dHash 三路，对关键区域（发票代码区、金额区、印章区）分别取哈希 | 64-bit ×3 指纹 |
| 特征向量 | 轻量 CNN（私有化部署，如 PaddleOCR 衍生或 ResNet 轻量头）提取全局 + 区域特征，维度 512 | 向量 `feature_vec` |
| 篡改检测 | ELA（错误级别分析）高残差区、噪声一致性（多区域噪声方差差异）、JPEG 8×8 网格边界不连续、EXIF 与内容矛盾（如 PS 软件标记/重压缩痕迹） | 篡改分 `tamper_score` 0–1 |
| 跨案相似检索 | 影像指纹库（按 `tenant_id` 隔离）做 pHash 汉明距离 Top-K；向量库（TiDB Vector / Milvus）做余弦 Top-K；融合排序 | 相似源案件清单 + 相似度 |

### 1.3 端口契约（domain 词汇，infrastructure 内做第三方转换）

```
VisualForensicsPort
  入参 ImageForensicQuery { tenantId, caseId, docType, imageRef, regionHints? }
  出参 ForensicHit {
         phashA/phashD/phashH, featureVecRef,
         tamperScore,                       // 0–1，≥阈值标记疑似篡改
         similarCases: List<SimilarCase{ caseId, similarity, sameInvoice? }>,
         hitRules: Set<R_FRAUD>,            // 如 R-FRAUD-08
         confidence, evidenceRef            // 落 C4 trace 的引用
       }
  失败语义：服务不可达 → 抛 `VisualForensicsUnavailable`，调用方降级（见 §1.7）
```

### 1.4 数据模型与索引（域中立，落 `bone-ai-visual` 侧存储，synapClaim 仅持 `evidenceRef`）

```
forensic_image_hash (
  tenant_id    BIGINT,
  case_id      VARCHAR,
  evidence_id  VARCHAR,
  doc_type     VARCHAR,         -- INVOICE / LIST / MEDICAL_RECORD
  phash_a      VARCHAR(16),     -- 十六进制
  phash_d      VARCHAR(16),
  phash_h      VARCHAR(16),
  feature_ref  VARCHAR,         -- 向量库外部键
  created_at   DATETIME,
  PRIMARY KEY (tenant_id, evidence_id),
  INDEX idx_tenant_doctype (tenant_id, doc_type)
)
```
- 向量索引建在外部向量库，按 `tenant_id` 分片保证隔离与不出域。
- PII 不出域：影像仅在租户私有存储与私有化模型内处理，指纹/向量不跨租户。

### 1.5 SLA / 容量 / 成本

| 项 | 参数 | 理由 |
|---|---|---|
| 单份感知哈希 | < 5ms（CPU） | 三路哈希纯计算，可批处理 |
| 向量抽取 | < 300ms/份（GPU 轻量模型，无 GPU 退 CPU 约 1s） | 仅在 `EvidenceLoad` 后异步触发 |
| 跨案检索 | < 200ms（Top-K=20，向量库近邻） | 限 K 防规模爆炸 |
| 触发时机 | `EvidenceLoad` 锁定证据后**异步**批量送视觉，不阻塞主链路；`FraudScreen` 取已算结果 | 不拖垮 MVP 理赔主链路（§3.3） |
| 容量上限 | 沿用单案 ≤20 份、单份 ≤10MB | 与 `ATTACHMENT` 纪律一致 |

### 1.6 评估指标（防假绿：必须含负向探针）

- **标注集**：每类正例 ≥ 50（重复报案 / PS 翻拍 / 篡改各一），反例 ≥ 200（真实不同案件的不同发票）；与 Rulebook §十 风险标记样例对齐。
- **指标**：`@阈值0.92` 的 Precision / Recall / F1；阈值扫描曲线（0.85–0.97）。
- **负向探针（证明"测到"而非"跑通"）**：
  1. 同一张真实发票重新上传（同案件）→ 相似度 = 1.0 但 `sameInvoice=true`，**不**误标重复报案；
  2. 换一张完全不同真实发票 → 相似度 < 0.5，不命中；
  3. 随机高斯噪声图 → tamper_score 高但 `similarCases` 为空，仅标记疑似篡改转人工，不自动拒赔。
- **门禁**：上线前 F1 ≥ 0.85 且误伤率（把真实不同发票判重复）≤ 1%；否则规则不启用，退回阈值从严。

### 1.7 fail-safe 降级

- `VisualForensicsPort` 不可达 / 超时（> 1.5s）→ 抛 `VisualForensicsUnavailable`，`FraudScreen` 跳过视觉分支，案件降级为单案规则路径（`R-FRAUD-01~07`）转人工（与工程落地版 §7.3 安全兜底一致）。
- 视觉**仅产出建议**：命中 `R-FRAUD-08` → 标记风险 + 附相似源案件，**绝不自动拒赔**，一律 `MANUAL_REQUIRED` + 升级风控岗。
- 阈值保守（0.92）：宁可漏过（转人工复核）不可误伤客户。

---

## 2. E2 图计算反欺诈（Graph Fraud）— R-FRAUD-05(升格)/09/10/11

### 2.1 业界实践对标

- **太保/泰康关系图谱团伙识别**：构建"人-案件-机构-设备"图谱，社区发现识别职业骗保团伙。
- **众安关系图谱 + 设备指纹**：同设备多账号、设备聚集直接阻断。
- **蚂蚁"蚁盾"**：设备指纹 + 关系网络实时风险评分。
- **银行图计算反洗钱**：子图同构 / PageRank 识别资金回路——理赔可借鉴"收款账户聚集"思路。
- **平安 13 模型 + 208 规则**：规则 + 模型双引擎，本设计以 `R-FRAUD-01~11` 覆盖规则、以图模型覆盖模型侧。

### 2.2 图模型（租户隔离为核心约束）

- **节点**：`Person`(被保险人/受益人/代理人)、`ClaimCase`、`Institution`(医院/药店)、`Device`(上传终端指纹)、`Account`(收款账户)、`Address`、`IP`。
- **边**：`PERSON-申报->CLAIM`、`CLAIM-收款->ACCOUNT`、`PERSON-就诊->INSTITUTION`、`CLAIM-使用设备->DEVICE`、`DEVICE-同指纹->DEVICE`、`PERSON-同地址->ADDRESS`、`ACCOUNT-聚集->ACCOUNT`。
- **隔离**：`tenant_id` 作为图分区键（NebulaGraph 的 `tag partition` / TuGraph 的属性过滤），所有查询带 `tenant_id` 谓词，杜绝跨租户串数据。

### 2.3 能力分解

| 子能力 | 方法 | 承载规则 |
|---|---|---|
| 设备指纹 | 上传终端特征（UA/分辨率/Canvas/时区）+ IP + 账号设备绑定历史，生成设备指纹哈希 | R-FRAUD-09 |
| 社区发现 | Louvain / 连通分量，对"人-案件-机构-设备-账户"子图聚类，输出社区 ID 与模块度 | R-FRAUD-09 / R-FRAUD-05(升格) |
| 中心性 | PageRank / 度中心性识别"枢纽人物/机构"（如某医院关联异常多高额案） | R-FRAUD-09 |
| 跨案关联 | 同被保险人/证件/手机号/收款账户/邮寄地址的多案聚合，识别"化整为零""一人多投多赔" | R-FRAUD-11 |
| 知识图谱一致性 | 医学目录底座 + 理赔知识图谱，推理"诊断-用药-诊疗-时间轴"矛盾（与 R-DIR-06 互补） | R-FRAUD-10 |

### 2.4 端口契约

```
GraphFraudPort
  入参 GraphFraudQuery { tenantId, caseId, personIds, accountIds, deviceFingerprint?, institutionIds? }
  出参 GraphFraudHit {
         communityId?, riskScore,            // 0–1 团伙风险分
         relatedCaseIds: List<caseId>,       // 关联案件簇
         hitRules: Set<R_FRAUD>,             // R-FRAUD-05/09/10/11
         deviceCluster?,                     // 同设备多账号证据
         consistencyConflicts?,              // R-FRAUD-10 矛盾点
         evidenceRef                        // 落 C4 trace
       }
  失败语义：图库不可达 → 抛 `GraphFraudUnavailable`，降级单案规则路径
```

### 2.5 图库选型（信创优先）

| 选型 | 适用 | 备注 |
|---|---|---|
| **NebulaGraph** | 大规模、高并发、分布式 | 国产开源，信创友好，推荐生产主选 |
| **TuGraph** | 中小规模、单机/轻量 | 国产，事务图，适合试点 |
| 轻量兜底 | Neo4j（社区版）/ TiDB 图计算 | 仅 Phase B 试点期或无法署 Nebula 时；不推荐长期（栈分裂） |

- 部署：私有化、数据不出域；图库与向量库同可用区，避免公网拉取。
- 不为想象规模预付：Phase B 先以单租户子集 + 近 90 天窗口构图，跑通后再扩全量。

### 2.6 实时 vs 离线

- **实时（案件提交时）**：增量写入新节点/边 → 局部邻域查询（该人/账户/设备的 Top-K 邻居、同设备账号数）→ 输出 `riskScore` 与 `relatedCaseIds`，延迟预算 < 500ms。
- **离线（每日/每夜）**：全量社区发现、团伙聚类、PageRank 重算，结果写回供实时查询参考；离线失败不影响实时降级路径。

### 2.7 评估指标（防假绿）

- **标注团伙集**：运营沉淀的已知骗保团伙（≥ 10 簇）作为正例；随机无关案件簇作为反例。
- **指标**：社区发现的 NMI / ARI（与标注簇比对）、团伙召回率（已知团伙成员命中率）、设备聚集识别准确率。
- **负向探针**：
  1. 随机编造的无关案件（不同人/账户/设备/医院）→ 不应成团，`communityId` 为空或风险分 < 0.3；
  2. 同一人正常多案（如逐年续保的不同疾病）→ 不误判团伙（靠"高额聚集 + 材料相似 + 设备聚集"多维权，而非单纯多案）；
  3. 图库注入 10% 噪声边 → 社区发现模块度下降但核心团伙仍召回（证明鲁棒）。

### 2.8 fail-safe 降级

- `GraphFraudPort` 不可达 / 超时（> 2s）→ 抛 `GraphFraudUnavailable`，`FraudScreen` 跳过图分支，降级单案规则路径（`R-FRAUD-01~07`）转人工。
- 高置信团伙（`riskScore ≥ 0.8` 且社区规模 ≥ 3）→ **P0 强制转人工** + 进入风控专项，绝不自动通过；命中经 `NotificationPort`(C9) 发多级预警（风控岗/反欺诈负责人），`FRAUD` 挂起不设自动恢复（工程落地版 §6.5）。

---

## 3. 串联：接入 FraudScreen 决策节点

```
EvidenceLoad(OCR+C1) → RuleCheck(RULE+C2) → AiAssess(LLM+C5+C6+E5)
   → FraudScreen(FRAUD, V1.0 Phase B):
        ├─ VisualForensicsPort(E1)  → R-FRAUD-08 命中 → 标记 + 附相似源案
        ├─ GraphFraudPort(E2)      → R-FRAUD-05/09/10/11 命中 → riskScore + 关联簇
        ├─ NotificationPort(C9)     → 命中即多级预警 + 挂起 FRAUD
        └─ TracePort(C4)           → ForensicHit/GraphFraudHit 全量入 claim_decision_trace
   → Arbitrate(DECISION+C3) → Adjust(RULE+C2+C11)
```

- **证据闭环**：所有反诈命中必须可回放——`ForensicHit`/`GraphFraudHit` 的 `evidenceRef` 写入 `claim_decision_trace`（C4 快照审计），满足金发8号"可解释 + 可人工复核"。
- **类型护栏**：`FraudScreen` 输出 `AiSuggestion` 型建议，最终 `Confirmed` 由人工在 `Arbitrate` 节点落定（AI 不碰钱、不自动拒赔）。
- **后督**：自动通过/建议拒赔结论 100% 经 AI 质检（工程落地版 §7.6），反诈命中列为高优先级抽检。

---

## 4. 企业场景契合

| 约束 | 落点 |
|---|---|
| 信创 | 图库 Nebula/TuGraph；向量 TiDB Vector/Milvus；视觉模型私有化（PaddleOCR 系/ResNet 轻量头），不依赖境外 SaaS |
| 数据不出域 | 影像/图/向量均租户私有存储 + 私有化推理；指纹按 `tenant_id` 隔离；PII 不入 trace（与网关 C8 语义一致） |
| 多租户 | 图分区键 + 向量分片 + 所有查询带 `tenant_id` 谓词；共享图库按租户逻辑隔离 |
| 合规 | 金发8号（可解释+可人工复核）、195 号文（AI 辅助性定位）、TC260/GB-T 45654（内容安全已复用 C8）；反诈仅建议不决断 |

---

## 5. 风险与回退（复用 master §7）

- 图库/视觉模型引入运维税：先以 MVP 单案规则兜底，Phase B 再接；不为想象规模预付。
- 规模：图/向量均须先做近 90 天窗口 + 限 K 检索，否则成业务硬顶。
- 回退：任一平台能力故障 → `FraudScreen` 按 fail-safe 转人工（安全兜底优先于 R-FAST 快赔）。

---

## 6. 下一步

1. E2 图库选型 PoC（NebulaGraph vs TuGraph 私有化部署压测 + 邻域查询延迟）。
2. E1 视觉指纹基线：采集本平台真实影像建库，标定 0.92 阈值下的 P/R。
3. 与 C10 直付通道、E4 MRM 联调：反诈命中关联信任额度（C11）下调与直付熔断。
