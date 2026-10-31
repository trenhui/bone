---
doc_id: index
role: navigation
currency: current
---

# synapClaim 文档入口（1 屏版）

> 全族 55 份文档已登记在 [`docs.manifest.json`](docs.manifest.json)，你通常只需要读 1–2 份。**先回答：你这次来做什么？**
>
> **AI Agent 请先读 [`AI-INDEX.md`](AI-INDEX.md)** —— 那是机器优先入口，含「按来意路由」表与每份文档的
> `role`（权威角色）/ `currency`（时效）标记；默认只信任 `currency=current`。
>
> 机读索引（含每份文档的 `summary` / `authority` / `role` / `currency`）在 `docs.manifest.json`，
> 层与真源归属在 [`00-governance/00-文档地图.md`](00-governance/00-文档地图.md)。

| 你要做什么 | 读这一份 | 再补这一份 |
|---|---|---|
| 了解产品是什么、卖给谁、能做什么 | `02-requirements/产品需求文档.md` | `01-strategy/Synap（思纳普）创业方案.md`（商业与团队） |
| 改代码 / 加接口 / 动数据模型 | `03-contract/工程详细设计-落地版.md`（工程契约 SSOT） | `04-design/理算引擎设计.md`（若涉金额）、`07-delivery/05-接口契约与数据字典（MVP）.md`（对外契约） |
| 做 AI / 决策图 / 模型接入 | `03-contract/AI技术方案.md` | `04-design/BONE-AI能力依赖图谱.md` |
| 审理算算法 / 查金额为什么这么算 | `04-design/理算引擎设计.md` | `03-contract/智能理算引擎业务规则V1.md`（业务口径）、`06-verification/理算引擎验证套件.md`（怎么验证） |
| 跑测试 / 判断"是不是真的修好了" | `06-verification/理算引擎验证套件.md` | `synap-claim-oracle-POINTER.md` → `doc/ai-product/synap-claim-oracle/synap-claim-settlement/README.md` |
| 对客户交付 / 试点落地 | `07-delivery/README.md`（交付包索引） | `05-plan/部署集成方案-FDE.md` |
| 准备上线 / 过合规 | `08-go-live/00-总纲与Go-Live门禁.md` | `08-go-live/01-生产准入合规包.md` |
| 排期 / 定里程碑 | `05-plan/MVP可落地执行方案.md` | — |
| 想知道为什么这么设计、对标了谁 | `09-records/reviews/业界对标整改台账.md` | `09-records/reviews/TPA-SAAS基线差距分析.md`（上游源系统现状） |
| 想知道某处代码改动的前因后果 | `09-records/impl-log/`（14–20，按实施顺序） | `09-records/reviews/理算引擎诊断记录.md` |

## 目录结构（**目录号 = 层号**，机检锁死）

```
synapClaim/
├── README.md                 ← 你在这里（人读入口）
├── AI-INDEX.md               ← AI 机器入口（生成物：语料模型 + 路由 + 现行/草案/冻结三段）
├── docs.manifest.json        ← 机读索引：路径 / 层 / 短名 / 权威域 / role / currency（唯一真源）
├── 00-governance/            L0  导航与治理：文档地图 / 治理规约 / 整合历史
├── 01-strategy/              L1  战略：创业方案（不含工程口径）
├── 02-requirements/          L2  需求：PRD（需求真源）
├── 03-contract/              L3  契约：工程落地版 / AI 技术方案 / 理算业务规则 V1
├── 04-design/                L4  设计：理算引擎 / 风控与支付 / BONE-AI 依赖
├── 05-plan/                  L5  实施：MVP 执行方案 / 部署集成 FDE
├── 06-verification/          L6  验证：理算引擎验证套件
├── 07-delivery/              L7  交付：交付包 00–10 + demo/
├── 08-go-live/               L8  上线：门禁总纲 00 + 规范分册 01–13
├── 09-records/               L9  记录：reviews/（评审与台账）+ impl-log/（实施记录 14–20）
├── 10-archive/               L10 归档：正文作废，只留洞察
├── synap-claim-oracle-POINTER.md  REF 指针：可执行参考实现已移出文档树（非产品代码）
└── tools/                    6 道机检 + 1 个索引生成器（见 00-governance/01-文档治理规约.md）
```

> 可执行参考实现（oracle）是**独立工程**，已移出文档树：`doc/ai-product/synap-claim-oracle/synap-claim-settlement/`。
> 它不是产品代码，不得被当作设计引用；本目录仅保留指针文档。

## 三条阅读纪律

1. **先看层，再看文**。`03-contract` 里的才算契约，`09-records` 里的是证据——同一句话在两个层里含义不同。
2. **过程记录不等于当前设计**。`09-records/` 里的诊断与实施记录是**某时点的取证**，部分结论已被后续记录撤销（如 `impl-log/15` 撤销了 `14` 的"额度服务 0 调用方"）。要"现在是什么样"，读 L3/L4 或直接读 `bone-apps/synap-claim` 代码。
3. **新增内容优先回写章节，而不是新建文档**。规则见 [`00-governance/01-文档治理规约.md`](00-governance/01-文档治理规约.md)——它的由来是本目录曾在 7 天内从 13 份涨到 45 份，且两两相似度全部 <0.2（同一主题换角度重写）。
