# Bone 通用语言（起步表）

> **定位**：产品、领域专家与研发的**同一套词汇**。命令、事件、REST、表字段应对齐本表。  
> **权威**：战略分类、Context Map、战术构件与命名风格统一见 [Bone-DDD-最终实践方案](architecture/Bone-DDD-最终实践方案.md)；核心域决策见 [ADR-0023](architecture/adr/0023-core-domain-smart-metadata.md)。
> **维护**：新增限界上下文或聚合时，先改本表再写代码。模块级细词放在各模块 README。

> **术语分三层，引用时说清是哪一层**（避免把工程选择当成 DDD 原则、把待实现当成已阻断）：
>
> 1. **通用 DDD**——Evans / Vernon 等通行语义：聚合、值对象、领域事件、限界上下文、ACL、CQRS 频谱；
> 2. **Bone 工程约束**——本平台选型带来的约定（`bone-metadata-sdk` 持久化栈、EAV 仅承载扩展字段、元数据只描述模型、`CORE-09`～`CORE-12`），不适用于其他项目，也不是 DDD 的要求；
> 3. **机器可证状态**——门禁实测结论（Active / Manual / Planned）。真源是 [Bone-DDD-最终实践方案 §G-1.7](architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints)，其他任何位置的表述都只是副本。

## 1. 子域与上下文

| 中文 | English | 类型 | 说明 | 禁用同义词 |
|------|---------|------|------|------------|
| 元数据 / 建模 | Metadata / Smart Metadata | **Bone 当前核心域** | 实体、字段、关系目录与运行时数据面 | 勿称「低代码引擎」替代本词；勿与主数据混用 |
| 主数据 | Master Data | 支撑域 | 业务主数据记录与质量 | 勿称核心域 |
| 集成 | Integration | 支撑域 | 连接器、流程编排、执行日志 | 勿称核心域 |
| 扩展 | Extension | 支撑域 | 扩展点与插件生命周期 | 勿称核心域 |
| 身份 | IAM | 通用域 | 用户、角色、权限、认证 | |
| 系统 | System | 通用域 | 配置、日志、告警通道 | |
| 支付（样板） | Payment | 支撑域（blueprint） | 演示聚合，非产品主路径 | 勿当成平台支付中台已交付 |

`ic_*` 保险表示例数据不是上下文，禁止当业务边界引用。

## 2. 元数据核心词

| 中文 | English | 含义 | 勿混淆 |
|------|---------|------|--------|
| 建模实体 | MetaEntity | catalog 中的业务对象定义 | ≠ 运行时某一行业务数据 |
| 建模字段 | MetaField | catalog 字段定义 | ≠ 扩展字段分配（fields:*） |
| 扩展字段 | Extension field | 实体动态扩展属性；SDK 三种存储模式：预留列（默认）/JSON/EAV | ≠ catalog 字段 |
| 运行时 | Metadata runtime | 按模型读写业务数据 | ≠ 代码生成 |

## 3. 战术构件（跨上下文）

| 中文 | English | 一句话 |
|------|---------|--------|
| 聚合 / 聚合根 | Aggregate / Aggregate Root | 强一致不变量与并发边界；一个事务默认修改一个聚合实例 |
| 值对象 | Value Object | 无标识、不可变、按值比较 |
| 领域事件 | Domain Event | 已发生的领域事实（过去式） |
| 集成事件 | Integration Event | 跨上下文契约 |
| 集成事件信封 | Integration Event Envelope | 承载 eventId、tenantId、版本、时间与追踪等传输元数据 |
| 仓储 | Repository | 按 ID 加载/保存聚合，不做报表 |
| 查询端口 | QueryPort | application 读侧的列表、搜索、统计契约，不走写仓储 |
| 防腐层 | ACL / Gateway | 外部模型不进入本上下文 |
| 应用用例边界 | Application Use-case Boundary | CommandHandler、QueryHandler 或语义化 ApplicationService；一个用例只选一种。概念上仍是**用例（Use Case）**，实现名一律用 `ApplicationService`，禁新增 `*UseCase` 类 |

## 4. 领域纯净度与持久化关系

| 维度 | 选项 | 何时用 |
|------|------|--------|
| 领域纯净度 | **D0 Pure** | 只依赖 JDK、Bone 最小领域抽象和 domain 端口 |
| 领域纯净度 | **D1 Annotated** | 映射简单且白名单注解不扭曲领域结构/行为 |
| 持久化关系 | **Shared** | 领域对象同时作为 metadata-sdk 持久化对象 |
| 持久化关系 | **Separated** | infrastructure 使用独立 `*PO` 与 Converter |

跨聚合 ID 或状态机本身不自动触发 `Separated`；嵌套落表集合、语义分歧、多存储、遗留映射或 SDK 能力不足才是分离信号。
