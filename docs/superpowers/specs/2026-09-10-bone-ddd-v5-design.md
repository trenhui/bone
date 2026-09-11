# Bone DDD v5.0 规范重构设计

## 1. 目标

重写 `doc/architecture/Bone-DDD-最终实践方案.md`，纠正 DDD 概念错位与内部矛盾，并将原则、参考架构、机器门禁、迁移台账和版本历史分离。

完成标准：

1. 主文档不再混入长版本历史、代码债台账和大段门禁模板。
2. Context Map 只包含限界上下文；技术模块使用独立依赖图。
3. R9、应用层用例边界、D0/D1、读侧端口只有一个无矛盾口径。
4. Outbox、并发控制定义能力保证，具体技术为默认参考实现。
5. 机器门禁只声明可可靠证明的结构属性，并如实标注实现状态。
6. `AGENTS.md`、ADR、glossary、DDD 索引与主文档术语一致。
7. 仓内相关 Markdown 路径和既有关键锚点保持可用。

## 2. 文档结构

### 2.1 规范入口

`doc/architecture/Bone-DDD-最终实践方案.md` 重写为 500–700 行左右的规范入口，保留：

- DDD 原则与 Bone 决策；
- 适用范围和裁剪档位；
- 最小强制规则；
- 指向各分册的入口；
- 仓内正在引用的 P-/E-/G 标题兼容段。

### 2.2 新增分册

- `doc/architecture/ddd/context-map.md`
  - 业务限界上下文、上下游、团队关系和发布契约；
  - 单独给出技术模块依赖图；
  - 修正 Shared Kernel、Conformist 等关系模式的使用。
- `doc/architecture/ddd/application-and-consistency.md`
  - 应用用例边界、CQRS、事务、跨聚合流程；
  - 可靠事件发布、幂等与并发策略。
- `doc/architecture/ddd/enforcement.md`
  - 机器门禁、人工语义评审和建议项分级；
  - 当前实现状态及待调整规则。
- `doc/architecture/ddd/migration-ledger.md`
  - 存量问题、freeze 策略和迁移清单。
- `doc/architecture/ddd/CHANGELOG.md`
  - 从主文档迁出的版本摘要。

已有 `naming-style.md` 继续作为非 DDD 原则的风格分册。

## 3. 规则语义

### 3.1 战略设计

- Bone 当前选择 Metadata 为唯一已认定核心域，但不宣称 DDD 要求组织只能有一个核心域。
- 核心域分类必须由客户价值、竞品差异和战略投入验证；平台持久化契约按技术能力描述。
- 一个模块只能归属一个限界上下文；一个限界上下文可以包含多个内聚模块。

### 3.2 聚合与事务

- 聚合继续作为强一致不变量边界。
- R9 改为“一个事务默认修改一个聚合实例”。
- 按 Repository 类型扫描只能发现部分风险，降为建议性检查，不再宣称证明事务语义。
- 同构批处理、技术 Outbox 写和业务强一致例外必须明确理由与测试。
- Orchestrator 的每一步事务通过独立 Bean 或 `TransactionTemplate` 建立，禁止依赖 Spring 自调用代理。

### 3.3 应用层

- `CommandHandler`、`QueryHandler`、语义化 `ApplicationService` 都可作为应用用例边界。
- 一个用例只能选择一个边界构件，禁止 Handler 与 ApplicationService 一对一套娃。
- 写事务位于最外层写用例边界。
- Orchestrator 用于跨步骤、可重试或可补偿流程，不作为普通代码复用手段。
- Facade 仅用于稳定 SDK 契约或多个入站适配器共享用例集合。
- 入站端口接口按替换和发布需要选用，不按名称强制。

### 3.4 CQRS 与端口

- 写侧 Repository 位于 domain，服务聚合加载和保存。
- 查询端口位于 application read side，返回 application projection。
- 新读路径统一为 `QueryHandler → QueryPort → infrastructure query adapter`。
- QueryBuilder 仅在 infrastructure 使用，不进入 application 或 domain。
- 跨上下文业务能力端口按领域语义决定放 domain 或 application，不与模块内查询端口混用。

### 3.5 持久化模型

- 删除“跨聚合 ID 引用必然要求 D0+PO”。
- D0+PO 的触发条件为：嵌套持久化集合、模型与表语义分歧、多存储、遗留映射、存储横切或 SDK 无法正确映射。
- 简单 CRUD 支撑域可继续使用 D1；有状态机不自动等于必须拆 PO，最终依据映射复杂度和领域纯净收益判断。

### 3.6 可靠性、并发与错误

- 不可丢业务事实必须采用可证明的原子发布机制；Outbox 是默认实现，不是唯一实现。
- 领域事件保持领域语义；Integration Event Envelope 承载 `eventId`、`tenantId`、`type`、`version`、`occurredAt` 和追踪信息。
- 可并发写聚合必须声明并测试并发策略；乐观锁为默认值，允许条件更新、唯一约束、悲观锁或单写者。
- 模块可以定义三根异常的语义子类；错误码、继承关系和边界映射保持统一。

### 3.7 测试与门禁

- R8 只作为测试卫生检查，不再称为反贫血主判据。
- 反贫血通过领域行为、拒绝路径、不变量测试和人工评审共同证明。
- 删除固定 70/20/10 测试比例。
- 门禁分为：
  - Hard gate：静态工具能够稳定证明；
  - Semantic review：需要业务语义判断；
  - Advisory：启发式风险提示；
  - Planned：尚未实现。

## 4. ADR 与兼容

- 新增 ADR-0024，记录 v5.0 规则语义调整和文档拆分，状态为“已接受”。
- ADR-0023 保留 Bone 当前 Metadata 战略选择，修正“核心域通常只有一个”的行业断言。
- 主文档保留仓内已引用的关键 P-/E-/G 标题或兼容入口。
- Java ArchUnit 实现本轮不修改；与新语义不一致的规则在 `enforcement.md` 标记为待调整，不能继续宣称全仓硬门禁。

## 5. 修改范围

本轮修改：

- DDD 主文档与新增分册；
- ADR-0023、ADR-0024；
- `doc/glossary.md`；
- `doc/architecture/ddd/README.md`；
- `AGENTS.md` 中 DDD 规则摘要与索引；
- 直接引用旧锚点的模块 README/DDD 分册。

本轮不修改：

- Java 业务代码；
- ArchUnit 规则实现；
- 数据库 DDL；
- API 契约；
- AI 自主权等级与其他无关项目规则。

## 6. 验证

1. 搜索并核对所有 `Bone-DDD-最终实践方案.md#...` 引用。
2. 检查新增和修改 Markdown 相对路径存在。
3. 扫描 `TBD`、`TODO`、旧版本矛盾表述和重复规则。
4. 运行仓库现有 docs compliance。
5. 对照设计逐项检查：Context Map、R9、应用用例、QueryPort、D0/D1、可靠发布、并发、异常、R8。

## 7. 风险控制

- 采用一次重写，但按文件分批落盘和校验，避免同时破坏所有链接。
- 保留旧锚点兼容入口，不要求下游文档同一提交全部改写措辞。
- 对尚未实现的门禁只记录迁移目标，不伪造已完成状态。
- 当前工作区已有大量用户改动，只修改本设计列出的文档，不格式化或清理其他文件。
