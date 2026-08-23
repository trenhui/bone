# ADR-0017：业务元数据实体（`meta_*`）→ 主数据实体（`mdm_*`）同步范式

| 项 | 内容 |
|----|------|
| **状态** | 已接受（阶段一：复制 + 来源追溯） |
| **日期** | 2026-05-20 |
| **决策者** | 平台架构 |
| **关联** | [主数据详设 §0](../../design/modules/3.%20主数据管理模块详细设计方案.md#0-数据血缘与-catalog-协同业界最佳实践) · [元数据能力 §8](../../design/modules/元数据能力-实现映射与竞品对照.md#8-与-bone-masterdata-的边界) |

---

## 背景

- PRD 要求「业务实体 → 主数据实体」能力；As-Is 已实现 `POST /api/v1/masterdata/entities/convert?businessEntityId=`（`ConvertFromBusinessEntityCommandHandler`）。
- `meta_*`（应用生成 / catalog）与 `mdm_*`（SSOT 治理）**不得**无规范双写，否则血缘不可追溯、质量规则与集成出口分裂。
- 业界常见三种范式：一次性复制、领域事件同步、只读联邦视图。

## 决策

**阶段一（As-Is / 已接受）**：采用 **显式复制 + 来源追溯**。

1. 转换入口仅 **`bone-masterdata`** 的 `convert` API（或后续批处理 Job），**禁止**集成流程直接写 `mdm_*` 绕过主数据服务。
2. `mdm_entity`（或等价表）保留 **`source_entity_id`**（或 `business_entity_id`）指向 `meta_entity.id`。
3. catalog 侧实体须 **`status=1`（已发布）** 方可转换；草稿/归档拒绝并返回 `MD_ENTITY_NOT_PUBLISHED`（**[Target]** 错误码登记）。
4. 对外系统分发/回写统一走 **`bone-platform/bone-integration`**，主数据模块不重复实现连接器运行时。

**阶段二（[Target]）**：在复制基础上增加 **`meta_entity` 发布事件** → 主数据订阅更新（字段增删策略见下）。

**阶段三（[Vision]）**：OpenLineage / Marquez 风格血缘图 UI；不在本 ADR 范围。

## 字段增删策略（阶段二草案）

| catalog 变更 | 主数据侧行为 |
|--------------|--------------|
| 新增字段 | 追加 `mdm_field`，默认非必填 |
| 修改展示名/类型（非破坏） | 更新元数据，不删历史记录 |
| 删除字段 | **软禁用**字段 + 质量规则下线，不物理删列（与 `bone-init.sql` 软删一致） |
| 实体归档 | 主数据实体置「只读」状态，禁止新记录 |

## 理由

- **复制 + 来源 ID** 实现成本最低，与当前 `convert` API 一致，满足 MVP 与审计「谁生成了谁」。
- 事件同步避免轮询 catalog，利于多租户与最终一致；放在阶段二不阻塞 As-Is。
- 联邦视图（只读 join）不利于主数据质量规则与集成出口，暂不采用为默认。

## 后果

### 正面

- SSOT 边界清晰：治理在 `mdm_*`，建模在 `meta_*`。
- 集成、质量、血缘文档可引用本 ADR 单一决策点。

### 负面 / 风险

- 复制后 catalog 与 mdm 可能短暂不一致，需事件或定时对账（**[Target]**）。
- `convert` 幂等：同一 `businessEntityId` 重复调用须返回已有 `mdm_entity_id`（**[Target]** 实现）。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| 仅联邦视图（mdm 读 meta） | 质量规则、导出、集成难以统一在 mdm 域 |
| 双写（UI 同时写两表） | 无单一写入点，血缘与冲突解决复杂 |
| 仅手工 Excel 导入 | 无法与元数据驱动战略对齐 |

## 合规检查

- [ ] `convert` 实现幂等与已发布校验（P0 看板 MD-04 **open**）
- [ ] OpenAPI 登记 `convert` 参数（`businessEntityId`）
- [ ] 详设 §0 与本 ADR 互链保持同步
