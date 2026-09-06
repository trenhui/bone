# ADR-0013：extension-studio 仓储读侧拆分为 gateway 端口

| 项 | 内容 |
|----|------|
| **状态** | 已接受 |
| **日期** | 2026-05-22 |
| **决策者** | 架构组 |

---

## 背景

`bone-extension-studio` 的 `domain/repository/*` 含 `findAll`、`search`、`update`、`count` 等方法，违反 DDD E-9.2 写侧仓储白名单，长期依赖 ArchUnit freeze 基线容忍。

## 决策

1. **写侧** `domain/repository/*Repository` 仅保留：`save`、`remove`/`delete`（映射 `remove`）、`findById`、单键 `findByXxx` / `existsByXxx`（无 `And`/`Or`）。
2. **读侧** 列表/搜索/统计迁入 `domain/gateway/*ReadPort`，由 `infrastructure/persistence/*` 同一实现类双接口实现。
3. `*QueryHandler` 只依赖 `*ReadPort`；`*CommandHandler` 写路径用 `save` 替代 `update`，批量删除通过读端口查 ID 后 `remove`。
4. 禁止新增 `findBy*And*` 仓储方法（已改为 `findByPluginVersion`）。

## 理由

- 对齐 P0-4 + §18.2，可逐步收缩 `repository_methods_whitelist` freeze。
- Studio 为内存/元数据混合持久化，读端口与写仓储同实现类，迁移成本低。

## 后果

### 正面

- ArchUnit 可对写侧仓储直接生效（不必永久 freeze 读方法违规）。

### 负面 / 风险

- 接口数量增加；实现类需 `implements XxxRepository, XxxReadPort`。

## 合规与迁移

- 读侧端口模式已写入《Bone-DDD》**§18.5**（v4.2）；本 ADR 为首发模块记录。
- 本 ADR 合并时完成 extension-studio 全量拆分；后续新增读方法只加在 `*ReadPort`。

## 相关文档

- [扩展管理模块详细设计方案 v2.5 §5.3](../design/modules/5.%20扩展管理模块详细设计方案.md#53-核心组件) — `*ReadPort` 在核心组件表中的职责说明
- [Bone-DDD 最终实践方案 §18.5](../Bone-DDD-最终实践方案.md) — 读侧端口模式
