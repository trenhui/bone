# ADR-0025：DDD v5.0.2 规则标识与实现状态对齐

| 属性 | 内容 |
|------|------|
| 状态 | 已接受 |
| 日期 | 2026-09-11 |
| 决策者 | Bone 架构组 |
| 关联 | [Bone-DDD v5.0.2](../Bone-DDD-最终实践方案.md)、[ADR-0024](./0024-ddd-v5-rule-semantics-and-document-split.md) |

## 背景

v5.0.1 复核发现：

1. 新核心规则继续使用 R1–R8，与 v4.x 已有 R8/R9 语义冲突。
2. “domain 零框架依赖”与 metadata-sdk 注解模型同时存在，缺少受控例外定义。
3. Hard gate 的规则类别与模块实际启用状态混写。
4. D0/D1/D2 同时描述领域纯净度和持久化对象，分类维度重叠。
5. QueryPort 目标位置、ApplicationService 入口与现有 ArchUnit 存在迁移差距。

## 决策

### 1. 稳定规则标识

- v5 核心规则使用 `CORE-01`～`CORE-08`。
- v4.x R8 测试卫生规则改为 `TEST-HYGIENE-01`。
- v4.x R9 只作为历史名称；当前语义归入 `CORE-06`，扫描规则为 Advisory。

### 2. 领域模型采用双维度分类

- 领域纯净度：`D0 Pure` / `D1 Annotated`。
- 持久化关系：`Shared` / `Separated`。
- `*PO` 是 infrastructure 对象，不再称为 D2 领域模型。

D1 只允许 metadata-sdk 映射注解、Lombok、`org.springframework.lang` 等项目白名单编译期注解，不允许领域代码依赖具体数据库、Web、MQ 或事务实现。

### 3. 门禁状态显式化

每条共享规则分别记录：

- `Eligible`：适合成为机器门禁；
- `Active`：模块已启用且无基线违规；
- `Frozen`：已有违规进入基线，只阻断新增；
- `Planned`：尚未接入。

“Hard gate”只表示规则类别，不再等同于全仓已经启用。

### 4. 查询边界

- 新 QueryPort 统一位于 `application/query/port`。
- QueryBuilder、Criteria、SQL 位于 infrastructure/query。
- 存量简单详情查询可以暂时按 ID 使用写 Repository；复杂投影必须迁移 QueryPort。
- metadata-sdk Repository 暴露的读侧能力记录为结构性迁移项，不因继承可见就允许写侧任意使用。

### 5. 可靠事件

不可丢事件在耐久交接失败时必须使业务事务失败；发布器不得吞异常或在交接成功前清除聚合事件。允许丢失的通知必须显式标记 best-effort。

## 后果

### 正向

- 规则编号不会再随版本复用。
- 领域纯净度与 PO 分离可以独立决策。
- 文档能准确区分目标态、freeze 与已清零状态。
- QueryPort 和可靠事件具有可验证迁移路径。

### 成本

- 需要更新 ArchUnit 文案、模块规则接入和 freeze 基线。
- 存量 QueryHandler、TenantContext 调用与 SDK Repository 需要渐进迁移。
- ApplicationService 入站规则仍需正反 fixture 后才能调整为 Active。

## 验收

1. 主文档中的 glossary 引用、门禁和迁移台账使用一致术语。
2. Criteria 被识别为 `@ReadSideOnly`。
3. IAM、MasterData、Integration 接入读侧 DSL 与 TenantContext freeze 规则。
4. Integration 异常继承平台正确根。
5. Integration Outbox 失败向上传播且聚合事件不被提前清除。
