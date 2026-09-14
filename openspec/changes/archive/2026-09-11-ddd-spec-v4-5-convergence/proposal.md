# 收敛 Bone DDD 规范 v4.4 → v4.5：从形式合规转向模型质量

## Why

`doc/architecture/Bone-DDD-最终实践方案.md`（v4.4，1192 行）在**依赖方向、CQRS 分离、反贫血、事件与幂等**上方向正确，但它把**可门禁的战术形态**（包路径、类后缀、方法名白名单、Facade 数量阈值）写成了 DDD 本身。后果是团队会稳定交付「ArchUnit 全绿、但聚合没有行为」的贫血模块化单体：

- **门禁优化替代了模型质量**：20 个 ArchUnit 规则方法测的是文件名与 import，测不到「聚合是否封装了不变量」「Handler 是否在写领域规则」；
- **领域纯净度的修辞与实现脱节**：D1 允许领域类型打 `@Table`/`@Id`（即表映射），D2 基类经 Lombok `@Data` 在编译期生成 setter，反贫血却只靠 CR 口头禁止；
- **过度约束逼出规避**：仓储禁 `findBy*And*`，而 `tenantId + code` 本是多租户下的自然键，团队只能新建假 `*ReadPort` 或把租户藏进 `ThreadLocal`；
- **规范自身有内部矛盾**：§14.3.1 禁止 `application/service`，§23.2 却给出 `OrderShippingService` 命名示例；第一部分（原则区）混入 `record` 强制、禁 UseCase、支付样板等实现偏好。

四轮外部评审的结论一致：**问题不是 DDD 不够，而是 DDD 过度工程化**。现在需要一次架构收敛。

## What Changes

- **铁律替换**：现有 P0 七条 → **v4.5 铁律 8 条**（7 条架构/DDD 原则 + 1 条 Bone 门禁），每条可机器判定或明确标注为 CR 检查点。
- **适应度函数换轨**：新增 **P2「每个聚合根必须有一个纯单测」**（覆盖主状态机 + ≥1 条拒绝路径，无容器、无 Spring），取代「ArchUnit 全绿」作为模型质量的主判据。
- **三处公开改口**（BREAKING，规范语义变更）：
  1. D1 更名为「**可持久化充血模型**」（Active Record 风格聚合），不再是「领域纯净度分级」；
  2. 承认 Bone 是「**厚共享内核的模块化单体**」，优化目标是平台内一致开发体验，而非上下文可独立替换持久化；
  3. `bone-gateway` 从「应用/控制面 BFF」改回「基础设施服务，豁免 §14」。
- **文档三层重构**：统一 `P-`（原则）/ `E-`（工程）/ `G-`（门禁）编号消除双 §10 歧义；支付样板外置；删除 COLA 术语对照；freeze 台账增加「拆除条件 + 到期日」。
- **应用层减负**：删除 Facade 触发条件 F3（「>7 个 Handler」魔法数）；`*Orchestrator` 由 ADR 例外降为模块 README 三级例外；允许 `{语义}ApplicationService` 作为复杂流程编排的合法命名。
- **仓储判定改法**：从「扫方法名 `And/Or`」改为「**返回类型只能是聚合根 / `Optional<聚合>` / `boolean` / `void`**」，允许 `tenantId + code` 等复合自然键。
- **门禁重配（design 阶段出方案，本次不改代码）**：命名/事务注解类规则降为 Checkstyle warn；跨上下文规则改用 FQN 正则，去掉 `allowEmptyShould(true)` 空命中。
- **补齐失败语义**：Outbox 由「可选」改为按**可靠性要求**触发的决策表；外部回调三件套（验签端口 + 聚合内幂等 + 存储级并发兜底）从支付样板提升为全局硬规则。

**非目标**：不改业务代码、不改 SDK 运行时行为、不动 `bone-blueprint` 已通过的 136 个测试、不引入新框架。M3（ID 契约、反贫血机制、Outbox 平台化）本阶段只产出 ADR 草稿。

## Capabilities

### New Capabilities

- `ddd-architecture-governance`：架构门禁与领域模型质量治理——定义 v4.5 铁律 8 条、适应度函数（聚合纯单测）、以及每条铁律的判定方式（ArchUnit / Checkstyle / CR）。

### Modified Capabilities

（无。既有的 `extension-frontend-routing`、`frontend-shared-layer`、`metadata-server-e2e` 三个能力的需求不受本次规范重构影响。）

## Impact

- **规范文档**：`doc/architecture/Bone-DDD-最终实践方案.md`（主文档）、`doc/architecture/ddd/` 分册、`doc/architecture/adr/0011`、`0012`、`0013` 的关联条文。
- **门禁代码**：`bone-framework/bone-architecture-test/src/main/java/com/bone/architecture/BoneDddArchRules.java`（现 20 个规则方法，390 行）、各应用模块 `src/test/java/.../architecture/ArchitectureTest.java`、`archunit_store/` 冻结基线。
- **AI/Agent 守则**：`AGENTS.md` 第 11 节对本文的引用、`CLAUDE.md`、§22.2 AI 生成守则。
- **下游文档**：`doc/architecture/Bone-API-规范.md`（异常与错误码映射）、`doc/design/modules/*.md`（按影响面同步）。
