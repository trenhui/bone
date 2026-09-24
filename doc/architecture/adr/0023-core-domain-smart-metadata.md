# ADR-0023：Bone 当前核心域定为 Smart Metadata（元数据 / 建模引擎）

| 项 | 内容 |
|----|------|
| **状态** | 已接受 |
| **日期** | 2026-09-10 |
| **决策者** | 平台架构组（评审结论执行：Bone-DDD v4.9） |
| **关联** | [Bone-DDD v5.1.0](../Bone-DDD-最终实践方案.md)、[Context Map](../Bone-DDD-最终实践方案.md#context-map-业务限界上下文)、[元数据能力对照](../../design/modules/元数据能力-实现映射与竞品对照.md)、[glossary.md](../../glossary.md) |

---

## 背景

P-10.1 曾把 **MasterData / Integration / Extension** 同时标成核心域，并加「待复核」冻结。按 Evans 战略设计，核心域是差异化竞争力所在，资源应集中投入。DDD **不限制一个组织只能有一个核心域**；问题不在数量本身，而在此前没有给出客户价值、竞品差异与战略投入依据，却把多个重要模块直接等同于核心域，并据此铺开 L3 治理成本。

Bone 的产品定位是**元数据驱动的快速开发平台**。不可被采购替代的是动态建模与元数据运行时（`bone-metadata-sdk` / `bone-metadata-server` / `bone-metadata-engine`），而不是主数据治理、连接器编排或扩展点注册。

## 决策

1. **当前核心域**：Bone 在当前产品战略下只认定 **Metadata**（Smart Metadata：建模 catalog + 元数据运行时）为核心域。未来可以通过新 ADR 增加或调整核心域，不受“只能有一个”的数量限制。
2. **支撑域**：MasterData、Integration、Extension、Notification、Generator、Payment（blueprint 样板）。
3. **通用域**：IAM、System。
4. **基础设施**（非限界上下文）：Gateway、File（空壳）、bone-framework 共享内核。
5. L 档与门禁强度：
   - 核心域优先配齐 `TEST-HYGIENE-01`、application `*QueryPort`、表归属清单；跨进程发布时再上 L3（依赖 ADR-0021）。
   - 支撑域默认 L1–L2，**不得**仅因「重要」上 L3。
   - L3 仍可按**集成形态**启用（资金、对外回调、跨进程不可丢失事件），与子域分类独立。
6. `bone-init.sql` 中 `ic_*` 保险表示例数据**不是**限界上下文，不进入上下文映射图。

## 理由

- 核心域判据包括客户购买理由、竞品差异、战略投入与「换掉它，产品是否仍是 Bone」，不是「模块工作量大」或「技术上难替换」。
- 主数据 / 集成 / 扩展均可被外部产品替代；建模引擎不能。
- 子域分类一旦定稿，B.1.1 的「双向冻结」可以解除：适用性快照不再等待本 ADR。
- `bone-metadata-sdk` 的持久化契约属于平台技术能力，不因被广泛依赖而自动成为业务核心域模型。

## 后果

### 正面

- 资源与门禁强度有唯一投向。
- 上下文图补上长期缺失的 Metadata BC。
- 支撑域可走 E-5.4 CRUD 简化档，减少治理税。

### 负面 / 风险

- 已把 MasterData 等当核心域投入的 ArchUnit / 文档表述需要改口，避免「降级」被误解为不重要。
- 元数据实现拆在 sdk / server / engine 三模块，BC 是**能力边界**不是单一 Maven 模块；跨模块仍须 ACL，禁止把 sdk 当领域模型共享。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 维持三个核心域 | 当前缺少三者都是客户差异化投资的证据；L3 成本无对应回报 |
| B. 以 MasterData 为核心域 | 主数据是平台上的一个应用域，不是平台本身的差异化引擎 |
| C. 不做分类，全模块 L3 | 规范已承认成本显著、收益存疑 |

## 合规与迁移

1. 同步 [Bone-DDD-最终实践方案](../Bone-DDD-最终实践方案.md) P-10、一页纸 L 档、B.1.1。
2. 同步 [glossary.md](../../glossary.md) 与 `doc/agents/01-项目概览与模块结构.md` §1 一句定位。
3. **不改代码、不改表、不改 API**。本 ADR 只定战略分类与门禁投向。
4. `bone-metadata-server`（有业务 REST）按 E-5.4「看 Controller」归入应用模块；sdk / engine 仍为 SDK / 引擎库。
