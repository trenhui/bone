# 低代码最小闭环 · 专题包（Mode B）

> **本目录**收拢 Mode B「建模即页面 · 发布即生效」的 **产品 PRD + 数据面设计 + 前端工程 + 界面协议 + 场景资产 + 评审**。
>
> **单一真源约定**：
> - API 契约真源 → [`doc/architecture/openapi/metadata-runtime-v1.yaml`](../../architecture/openapi/metadata-runtime-v1.yaml)
> - 元数据模块详设 → [`../modules/2. 元数据管理模块详细设计方案.md`](../modules/2.%20元数据管理模块详细设计方案.md)
> - 平台主 PRD → [`doc/prd/BONE产品需求文档正式版.md`](../../prd/BONE产品需求文档正式版.md)
>
> ⚠️ 本目录文档互相引用用的是**相对路径**，移动文件后请用 `git grep` 复核，勿裸改文件名。

## 文档地图（按角色）

| 路径 | 角色 | 最后更新基线 |
|------|------|--------------|
| [`BONE-低代码最小闭环PRD.md`](./BONE-低代码最小闭环PRD.md) | **产品** Why / What / 验收（MoSCoW）｜§2.5.10 = 采购订单第三验收场景 | v0.51 |
| [`BONE-低代码Runtime数据面设计.md`](./BONE-低代码Runtime数据面设计.md) | **数据面** CRUD + Describe/Invoke + 逃逸｜§4.8.4 三单匹配闸门 · §5.0 采购端到端 | v0.50 |
| `ui/元数据工作区前端融合方案.md` | **前端工程 · 已落地记录**：四 Tab 工作区 As-Is→To-Be、W0–W2 切片、门禁 | 2026-10-09 |
| `ui/ui-protocol/` | **界面协议族 v1.0 详细设计**：JSON 协议 + 前后端技术方案 + Schema | 2026-10-13 |
| `ui/bone-metadata-ui-prototype.html` | 评审可点原型（UID-9 四 Tab + 场景包 + 双界面 + 逃逸弹层） | 2026-10-09 |
| `cases/费用报销系统-元数据建模方案.md` | **业务示范 + 缺口探针**：报销 L0/L1 | 2026-10-11 (v0.50) |
| `ui/业务建模三入口页UI优化方案.md` | **诊断证据**：D1–D4 / B1–B5 / O1–O5，每条绑定代码行号 + 业界对标（"是什么问题"） | 效果图见 `ui/optimized-*.png` |
| `ui/业务建模三入口页最佳方案综述.md` | **决策综述**：取各家之长的取舍与关键交互决策（"为什么这样改"） | 结论载体 `cases/mock-best-scheme.html`（外部引用，勿移动） |
| `cases/ui-schema-samples/` | **界面协议样板**：7 个场景 Schema 样例（**三验收场景**：`expense-claim` / `claim-outpatient-fasttrack` / `purchase-order`）+ 协议驱动渲染 Demo | 2026-10-14 |
| `cases/ui-schema-samples/purchase-order-ui-schema.example.json` | **采购订单协议样例**：10 列行项目 + 预算闸门 + 三单匹配 `SUB-MATCH-01/02`（与 PRD §2.5.10 同构） | 2026-10-14 |
| `../../scripts/e2e/ui-designer-three-scenario-probe.mjs` | **设计器 UI 探针**（19 断言）：三场景切换 + 企业子表合计/增删 + 试运行负向点名规则 | 2026-10-14 |
| `reviews/生产就绪诊断-2026-10-11.md` | **生产就绪诊断**：P0/P1/P2 + 修复追踪（**已闭环**） | 2026-10-11 |

## 推荐阅读顺序

1. [PRD](./BONE-低代码最小闭环PRD.md) §0.6 / §0.7 / §2.5.8–2.5.9（先读，定调）
2. [Runtime 数据面设计](./BONE-低代码Runtime数据面设计.md)
3. [ui 融合方案](./ui/元数据工作区前端融合方案.md) + [原型](./ui/bone-metadata-ui-prototype.html)
4. **下一阶段（界面协议驱动渲染）**：`ui/ui-protocol/README.md` → 协议设计 → 前后端技术方案 → `cases/ui-schema-samples/expense-claim-ui-schema.example.json`
5. [cases 费用报销](./cases/费用报销系统-元数据建模方案.md)（业务串联）

## 目录结构

```
doc/design/lowcode/
├── README.md                              ← 本文件（全目录真索引）
├── BONE-低代码最小闭环PRD.md              ← 产品
├── BONE-低代码Runtime数据面设计.md        ← 数据面
├── ui/
│   ├── README.md                          ← UI 资产索引（含稿件版本链）
│   ├── 元数据工作区前端融合方案.md         ← 已落地的四 Tab 工作区融合记录
│   ├── 业务建模三入口页UI优化方案.md        ← 诊断证据（D1–D4/B1–B5/O1–O5）
│   ├── 业务建模三入口页最佳方案综述.md      ← 决策综述（结论载体在 cases/mock-best-scheme.html）
│   ├── ui-protocol/                       ← 界面协议族 v1.0（下一阶段详细设计）
│   │   ├── README.md
│   │   ├── BONE-业务对象界面JSON协议设计.md
│   │   ├── BONE-业务对象界面前后端技术方案.md
│   │   └── bone-ui-schema.schema.json
│   ├── bone-metadata-ui-prototype.html    ← 评审原型
│   ├── walkthrough-2026-10-09.md          ← 走查证据
│   ├── preview-*.png                      ← 走查截图（有效）
│   └── archive/                           ← 历史归档（见 archive/README.md，只读）
├── cases/
│   ├── README.md                          ← 场景资产索引（报销业务 + 协议样板）
│   ├── 费用报销系统-元数据建模方案.md       ← 业务示范 + 缺口探针
│   ├── mock-best-scheme.html              ← 最终交互稿基线（外部 doc/plans 引用，勿移动）
│   ├── mock-expense-flow.html             ← 报销串联稿（现行）
│   ├── archive/                           ← 过程稿归档（见 archive/README.md，只读）
│   └── ui-schema-samples/                 ← 界面协议样板（6 Schema + Demo）
└── reviews/
    ├── README.md                          ← 评审索引
    └── 生产就绪诊断-2026-10-11.md          ← 生产就绪诊断（已闭环）
```

## 归档约定

- 历史/废弃稿件统一 `git mv` 进同目录 `archive/`（`cases/archive/`、`ui/archive/`），附 `archive/README.md` 一行原因；不再使用散落的 `.archived-` 前缀。
- 移动或重命名任意文档后，用 `git grep -n "被移动的文件名" doc/design` 复核相对链接。
