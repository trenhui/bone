---
doc_id: ai-index
role: navigation
currency: current
---

# synapClaim · AI 入口索引（机器优先）

> **本文件由 `tools/_gen_ai_index.py` 从 `docs.manifest.json` 生成，禁止手工编辑。**
> 改内容请改 manifest 后重跑：`python3 tools/_gen_ai_index.py`；校验用 `--check`。
>
> 权威真源以 [`docs.manifest.json`](docs.manifest.json) 为准（含每份文档的 `role` / `currency` /
> `authority` / `superseded_by` 机器字段）。
>
> **黄金法则**：默认只信任 `currency=current`；`role=draft` 是待合入草案；
> `role=evidence`（`currency=frozen`）是历史取证，**不得当作当前设计引用**。


## 一、语料模型（检索前先理解）

| `role` | 含义 | AI 如何处理 |
|---|---|---|
| `ssot` | 当前权威真源 | 可引用为事实 |
| `navigation` | 导航/治理元文档 | 用于路由，不含业务口径 |
| `draft` | 待合入草案（如 go-live 01–13） | 仅作计划/提案，不当现行契约 |
| `evidence` | 冻结的过程证据（L9/L10） | 仅回答「为什么/历史」，不当现状 |
| `tooling` | 工具/参考实现（oracle） | 非产品代码，不当设计引用 |

| `currency` | 含义 |
|---|---|
| `current` | 现行——默认可引用 |
| `draft` | 草案——仅在明确询问规划时引用 |
| `frozen` | 冻结——只作历史取证，禁止当作现状 |

## 二、按来意路由（ask → read）

| 你想做什么 | 读这些（id） |
|---|---|
| 产品定位 / 卖给谁 / 能做什么 | `prd` / `strategy` |
| 改代码 / 加接口 / 动数据模型 / 契约口径 | `contract.landing` / `design.adjustment` / `delivery.05` |
| AI 编排 / 决策图 / 模型接入 | `contract.ai` / `design.bone-ai-deps` |
| 理算算法 / 金额怎么算 / 业务口径 | `design.adjustment` / `contract.rules-v1` / `verify.adjustment` |
| 跑测试 / 验证是否真的修好 | `verify.adjustment` / `impl.readme` |
| 客户交付 / 试点落地 / POC | `delivery.index` / `plan.fde` |
| 上线 / 合规 / 门禁 | `golive.00` / `golive.01` |
| 排期 / 里程碑 / 迭代 | `plan.mvp` |
| 为什么这么设计 / 对标 / 差距 | `records.review.业界对标` / `records.review.TPA-` |
| 某次代码改动的前因后果 / 历史取证 | `records.impl.14` / `records.impl.15` / `records.impl.16` / `records.impl.17` / `records.impl.18` / `records.impl.19` / `records.impl.20` / `records.review.理算引擎` |

## 三、ACTIVE 现行真源（currency=current，可引用为事实，共 29 份）

| id | 路径 | role | 一句话 |
|---|---|---|---|
| `map` | `00-governance/00-文档地图.md` | navigation | 层 ↔ 目录映射表 + 每份文档的 SSOT 归属与权威边界（冲突时谁说了算） |
| `governance` | `00-governance/01-文档治理规约.md` | navigation | 命名、引用、机检、新增登记的强制规则（防再次膨胀） |
| `history` | `00-governance/02-整合历史.md` | navigation | P1–P10 各轮整合的动因、动作与口径修正记录（历史，非当前口径） |
| `strategy` | `01-strategy/Synap（思纳普）创业方案.md` | ssot | 公司定位、1 底座 + 2 触角战略、竞争与团队；只讲商业，不含工程口径 |
| `prd` | `02-requirements/产品需求文档.md` | ssot | 需求真源：功能范围、用户故事（BDD）、KPI、约束 |
| `contract.ai` | `03-contract/AI技术方案.md` | ssot | bone-ai 落地：六模块映射、DAG 执行器、五段式编排预算、资金、评测、ADR 摘记 |
| `contract.landing` | `03-contract/工程详细设计-落地版.md` | ssot | 工程契约 SSOT：35 表 DDL(§9.5)、OpenAPI(§10)、非功能(§11)、ADR(§14)、时序 |
| `contract.rules-v1` | `03-contract/智能理算引擎业务规则V1.md` | ssot | 理算业务口径真源(V1)：责任 11 要素、发票 6 公式、九步主流程、免赔 6 维、控额 7 层；含 Q-01–Q-15 待澄清 |
| `design.bone-ai-deps` | `04-design/BONE-AI能力依赖图谱.md` | ssot | 中台依赖真源：C1–C11 复用映射、平台缺口 E1–E5、6 节点串联 |
| `design.adjustment` | `04-design/理算引擎设计.md` | ssot | 理算子域唯一设计真源：架构决策、引擎编排顺序、三类策略算法、控额、快照契约、端口 |
| `design.risk-payment` | `04-design/风控与支付通道设计.md` | ssot | 风控与直付真源：视觉反诈 E1、图计算 E2、MRM E4、C10 直付三态 |
| `plan.mvp` | `05-plan/MVP可落地执行方案.md` | ssot | 实施计划真源：范围 In/Out、迭代 I0–I4、任务拆解、里程碑与 Go/No-Go |
| `plan.fde` | `05-plan/部署集成方案-FDE.md` | ssot | 交付与售前：与存量理赔系统集成矩阵、FDE 工程产物、三阶段落地 |
| `verify.adjustment` | `06-verification/理算引擎验证套件.md` | ssot | 验证资产唯一真源：S1–S28 场景、P1–P28(30 断言) 探针、19 类变异（防假绿） |
| `delivery.00` | `07-delivery/00-落地交付包清单.md` | ssot | 交付包总览与生成物清单（由 gen_delivery_list.js 产出，勿手改） |
| `delivery.01` | `07-delivery/01-试点合作框架协议模板（MOU-NDA-DPA）.md` | ssot | 试点合作 MOU/NDA/DPA 三件套条款骨架与签署顺序 |
| `delivery.02` | `07-delivery/02-数据脱敏与个人信息处理方案.md` | ssot | 数据分级、逐字段脱敏算法、验证方法与 PIPL 合规检查 |
| `delivery.03` | `07-delivery/03-POC演示脚本.md` | ssot | 25 分钟 POC 演示剧本与异议应答 |
| `delivery.04` | `07-delivery/04-业务规则书（Rulebook）-门诊小额快赔MVP全集.md` | ssot | 规则 ID 定义真源（R-AREA/R-DIR/R-DED/R-RAT/R-CAP…） |
| `delivery.05` | `07-delivery/05-接口契约与数据字典（MVP）.md` | ssot | 对外接口契约真源：MVP 端点子集、对外枚举、错误码、数据字典 |
| `delivery.06` | `07-delivery/06-医学目录库建设方案.md` | ssot | 医学目录库四类组成、数据来源合规、入库字段与更新机制 |
| `delivery.07` | `07-delivery/07-测试方案与性能基准.md` | ssot | 测试分层、评测集标注规范、性能基准与发布门禁 |
| `delivery.08` | `07-delivery/08-部署运维手册.md` | ssot | 部署、日常运维、应急手册 |
| `delivery.09` | `07-delivery/09-培训与用户手册.md` | ssot | 培训计划、核赔工作台操作手册、AI 人责归属说明 |
| `delivery.10` | `07-delivery/10-商务合同模板与规模化准备.md` | ssot | 商务模式、竞品矩阵、白皮书大纲、等保清单、规模化检查表 |
| `delivery.index` | `07-delivery/README.md` | navigation | 交付包索引：01–10 编号与内容对照（新增交付包必须在此登记） |
| `golive.00` | `08-go-live/00-总纲与Go-Live门禁.md` | ssot | 总纲：缺口→分册映射表、G1–G16 上线门禁、与既有治理的衔接 |
| `ai-index` | `AI-INDEX.md` | navigation | 机器优先入口：语料模型+按来意路由+ACTIVE/DRAFT/FROZEN 三段清单，供 AI 确定性检索 |
| `index` | `README.md` | navigation | 3 分钟入口：按「你这次来做什么」路由到唯一该读的文档；不再按目录树猜 |

## 四、DRAFT 草案（role=draft，待合入 L3，不当现行契约，共 13 份）

- `golive.01` → `08-go-live/01-生产准入合规包.md`：PIA/DPIA、等保备案、AI 算法备案、SBOM、渗透测试门禁
- `golive.02` → `08-go-live/02-外部依赖SLA与降级责任矩阵.md`：外部依赖清单、SLA 与降级责任矩阵、支付实时对账契约
- `golive.03` → `08-go-live/03-容量性能韧性基线.md`：连接池、OCR 背压、网关 WAF、水平扩展模型、压测计划
- `golive.04` → `08-go-live/04-租户隔离CI自动化护栏.md`：ArchUnit 结构锁 + 反例测试，租户隔离纳入 CI
- `golive.05` → `08-go-live/05-生产成熟度增强.md`：阈值校准方法论、审计 hash 链、错误预算/SLO burn-rate
- `golive.06` → `08-go-live/06-数据主体权利与PIPL合规（DSAR）.md`：PIPL 第四章权利清单、DSAR 流程、删除权×WORM 调和
- `golive.07` → `08-go-live/07-事件响应与Runbook（IRP）.md`：IRP 框架与常见故障 Runbook
- `golive.08` → `08-go-live/08-规则发布鉴权与防投毒.md`：规则即攻击面：四眼发布、签名、灰度与回滚
- `golive.09` → `08-go-live/09-保险条款费率监管报批.md`：条款费率报批与算法备案的分界、须建设内容
- `golive.10` → `08-go-live/10-备份恢复实测与演练证据.md`：备份可证伪：恢复实测与演练证据要求
- `golive.11` → `08-go-live/11-训练数据合法性论证.md`：医疗数据训练合法性闭环
- `golive.12` → `08-go-live/12-资金累计DB级并发正确性.md`：累计扣减的 DB 级并发正确性（单测契约不足）
- `golive.13` → `08-go-live/13-理算引擎三重路径收敛与护栏移植（F1）.md`：唯一权威资金路径决策、整改步骤、双路径歧义根除

## 五、FROZEN 冻结证据（role=evidence/tooling，仅用于「为什么/历史」，禁止当作现状，共 13 份）

- `records.impl.14` → `09-records/impl-log/14-主链路闭环体检与P0修复记录.md`：四本账未对齐的体检与 3 项 P0 修复（含变异证据）  ⚠️ 已被 `09-records/impl-log/15-跨案累计读侧与责任账接线（P0-4）.md` 撤销/修正：impl-log/15 已撤销本册"额度服务 0 调用方"判断
- `records.impl.15` → `09-records/impl-log/15-跨案累计读侧与责任账接线（P0-4）.md`：跨案累计读侧与 A7 责任账接线
- `records.impl.16` → `09-records/impl-log/16-理算引擎V1重建（分层管线·七层控额·免赔六维）.md`：按 V1 规则重建分层管线与控额  ⚠️ 已被 `09-records/impl-log/17-控额层接线修正与多次闭环.md` 撤销/修正：impl-log/17 修正本册"七层控额四层仅声明未读取"（死配置）
- `records.impl.17` → `09-records/impl-log/17-控额层接线修正与多次闭环.md`：四层死配置改为「生效或拒绝」，8 项变异被抓
- `records.impl.18` → `09-records/impl-log/18-次分组维度落地与降级判据.md`：「次」分组维度落地与 fail-closed 降级判据
- `records.impl.19` → `09-records/impl-log/19-额度释放原子性与占用归属（P0-5）.md`：额度释放的占用归属校验与跨账原子性（含 CORE-06 例外登记与 4 项变异证据）
- `records.impl.20` → `09-records/impl-log/20-额度投影自愈与释放阻塞（P0-6）.md`：额度投影自愈：共享池被越权释放抽干时，本单自有 hold 足额仍须能正常取消（含口径自洽性验算与 7 项变异证据）
- `records.review.TPA-` → `09-records/reviews/TPA-SAAS基线差距分析.md`：上游飞书 TPA-SAAS 源系统盘点与差距（护城河与欠账）
- `records.review.业界对标` → `09-records/reviews/业界对标整改台账.md`：业界对标量化基线、已落地整改项、V1.0 执行清单
- `records.review.代码整改` → `09-records/reviews/代码整改清单.md`：待架构评审的代码整改项（含已闭环项的备查记录）
- `records.review.理算引擎` → `09-records/reviews/理算引擎诊断记录.md`：两轮取证：修复前后的实证与新发现的缺陷
- `archive.system-design` → `10-archive/系统设计方案.md`：上游设计演进记录；2026-10-21 起正文作废，仅留行业洞察与选型理由  ⚠️ 已被 `03-contract/工程详细设计-落地版.md` 撤销/修正：正文已作废，独特价值已并入《工程落地版》§3.6/§3.7/§4.3
- `impl.readme` → `synap-claim-oracle-POINTER.md`：可执行参考实现（双轨 oracle）指针：真实位置 doc/ai-product/synap-claim-oracle/synap-claim-settlement/；非产品代码

## 六、manifest 字段图例

- `role`：文档角色（ssot/navigation/draft/evidence/tooling）
- `currency`：时效（current/draft/frozen）
- `authority`：本文件独占真源的主题
- `superseded_by`：被哪份文档撤销/修正（仅证据类有）
- `aiRouting`：本索引第二节的路由表真源（在 manifest 中，不在本文件）

