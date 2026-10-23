# bone-workflow-engine

轻量**审批流**引擎：流程定义 / 实例 / 任务 / 指派 / 条件分支。

> 定位：**专用于「人审批」的表单流程**，不是通用 BPMN 引擎。刻意不做 BPMN 解析、DMN、过程仿真、复杂事件网关——
> 那些是通用编排引擎的卖点，低代码审批流用不上，做了就是负担。

## 为什么是独立模块（而不是写在 bone-metadata-server 里）

审批流是**平台级能力**：报销、采购、合同、用印……都会用。把它塞进某个应用模块，第二个场景来时就只能复制粘贴。
独立成模块后：

- 引擎**对 `bone-metadata-engine` 零依赖**（由 `bone-workflow-runtime` 的 ArchUnit 规则守护），只经 ports 契约与平台对话；
- 任何宿主只要实现那 7 个端口就能用上引擎（不止 BONE 平台）；
- 依赖方向严格向内：`domain`（纯逻辑）→ `ports`（契约）→ `runtime`（编排）→ `starter`（Boot 3 装配）。

## 模块形状

对齐 `bone-metadata-engine` 的四段式惯例：

| 模块 | 包 | 职责 | 对外依赖 |
|---|---|---|---|
| `bone-workflow-domain` | `com.bone.workflow.domain` | 流程定义 / 实例 / 任务模型、条件求值、分支选择、发布期校验 | 仅 JDK + Jackson |
| `bone-workflow-ports` | `com.bone.workflow.ports.spi` | 7 个端口：定义仓储、实例仓储、指派解析、业务数据、状态推进、审计、id 生成 | 仅 domain |
| `bone-workflow-runtime` | `com.bone.workflow.runtime` | 引擎实现：推进 / 任务完成判定 / 指派冻结 | domain + ports + Spring |
| `bone-workflow-starter` | `com.bone.workflow.starter` | Boot 3 自动配置（`.imports`）+ **BONE 集成适配器**（`adapter` / `spi` 包：SDK 仓储、状态投影、记录读取、指派解析、id 生成、审计——原独立桥接模块 `bone-workflow-platform` 已撤销并入，见设计稿 §2.2） | runtime + ports + `bone-metadata-sdk` + `bone-metadata-server` |

`domain` 层有独立 ArchUnit 规则守护「不得依赖 Spring / 平台 / 外层」——否则「引擎可独立复用」这条前提会被悄悄破坏。

## 核心概念

- **流程定义（`WorkflowDefinition`）**：一次发布产出的不可变快照。节点类型 `START / APPROVE / CONDITION / CC / END`，
  审批完成判定 `SINGLE（单签） / ALL（会签） / ANY（或签）`。
- **流程实例（`WorkflowInstance`）**：一张单据的一次审批过程。`phase` 是**流程进度的权威**，业务单据的 `status` 是
  **对外投影**（由宿主经状态端口落地）——二者不一致时以 `phase` 为准重投影。
- **审批任务（`WorkflowTask`）**：某节点下「一位办理人 × 一项待办」。办理人在节点进入时**一次性解析并冻结**，
  杜绝「昨天主管批、今天总监批」的不可审计状态。

## 四条硬纪律

1. **不直写业务单据状态**——只经 `WorkflowStatusPort` 表达意图，由宿主走平台状态机落地，避免与 `phase` 双真源。
2. **审批人解析不出 ⇒ 实例挂起 SUSPENDED，绝不放行**（组织架构断链是常态，当「跳过节点」等于让单据无人审批直接
   通过）。挂起**不是终态**：发起人仍可撤回（否则被锁死、单据变死单）。
3. **任务决策走 CAS**（`compareAndSetTaskStatus`）：读-判-写在两个审批人同时点「同意」时会双写、重复推进流程。
4. **节点推进走存储层原子判定**（`compareAndAdvanceNode`）：「会签是否已齐」与「推进」必须在**同一条原子操作**里
   完成。应用层读两次快照的实现会出现经典死锁——两个会签人各自读到「对方还没通过」，双方都判定「未齐」，
   **流程永远卡在待办**。宿主实现该端口时务必落成带 `COUNT` 子查询的**条件 UPDATE**，而非「先查后改」。

   > 之所以不会死锁：`adv` 严格晚于自己的 `taskCAS`。若双方互等，则 `A.adv < B.taskCAS` 且 `B.adv < A.taskCAS`，
   > 与 `A.taskCAS < A.adv`、`B.taskCAS < B.adv` 矛盾——**不可能成立**。

另有一条贯穿全模块的 **fail-closed 纪律**：条件求值失败不得当 false 静默放行；分支不命中 / 多条命中 / 条件非法
一律抛错。UI 规则里「条件不命中 = 规则不适用」可以安全地当 false，但**分支不命中 = 没人被选中**，两者语义不可混用。

另两条并发/幂等纪律：

- **重复发起幂等**：同一单据再次 `start` 复用既有实例（否则网络重试 / 连点两次提交会产生两个并行实例、双份审批）。
- **终态不可回写**：流程通过后，晚到的「驳回」必须被拒绝——否则或签节点上「先同意后驳回」会把已 APPROVED 的流程
  改写成 REJECTED。已决策任务的**同结论**重复提交则幂等返回（正常重试不该报错）。

## 错误分类（稳定契约）

引擎抛 `WorkflowEngineException`，携带 `WorkflowErrorCode` 枚举（`NOT_ASSIGNEE` / `CONFLICT` / `INSTANCE_TERMINATED` /
`ALREADY_STARTED` / `NO_ASSIGNEE` / `BRANCH_AMBIGUOUS` …）。**宿主按 `code()` 映射平台错误码，不要解析异常 message**——
否则同一条语义会在两处各写一套字符串，迟早漂移。

## 条件语义黄金样例

`ConditionSemanticsSamples` 把条件求值语义钉成一组**公共契约样例**（正反两侧都有，含「未知算子」「嵌套超深」
等负向样例）。宿主注入「与平台 UI 协议规则同源」的求值器时，在自己的测试里跑同一份样例即可证明与引擎自带实现
**不漂移**——这类漂移不会有任何测试自动报出来。

## 条件求值：结构化 JSON，不用表达式

条件形如 `{"all":[{...}]}` / `{"any":[{...}]}` / `{"not":{...}}` / `{"fieldCode":"amount","operator":"gt","value":5000}`，
算子集见 `StructuredConditionEvaluator.SUPPORTED_OPERATORS`。

**不用 SpEL / JS 表达式**：流程条件是业务人员可编辑的元数据，表达式求值器一旦能调方法 / 构造对象，即等于开放 RCE。
平台通用的 `ExpressionEngine` 走的正是 SpEL + `StandardEvaluationContext`，因此**不可**用于流程条件。

语义与平台 UI 协议规则（`UiSchemaRuleEngine`）逐条对齐（同算子、同金额容差 0.005、同子表路径穿透、同 `$me` 解析），
以满足「前端先拦、后端强制同一结论」的同源纪律；`WorkflowConditionEvaluator` 是可替换端口，宿主可注入与 UI 规则
同源的实现（建议在装配层补 parity 测试锁定两者不漂移）。

## 端口清单（宿主需实现）

| 端口 | 职责 | 建议实现 |
|---|---|---|
| `WorkflowDefinitionRepository` | 取流程定义快照 | 平台实体 `wf_process_def`（`definition_json` 存整图快照；节点/边/指派规则**不拆物理表**，见设计稿 §3 取舍） |
| `WorkflowInstanceRepository` | 实例与任务持久化 | 平台实体 `wf_instance` / `wf_task`（租户隔离经 `@TenantScope` + 显式谓词、审计、乐观锁） |
| `ApproverResolver` | 指派策略 → 账户 id | 组织目录 / IAM |
| `WorkflowRecordPort` | 读业务单据数据（条件求值用） | 平台运行时读接口 |
| `WorkflowStatusPort` | 推进业务单据状态 | 平台动作端点（`RuntimeActionService.invokeAction`） |
| `WorkflowAuditPort` | 审批轨迹留痕 | 平台动作执行留痕链路（与业务操作日志同一条时间线） |
| `WorkflowIdGenerator` | id 生成 | 平台雪花 id |

`WorkflowInstanceRepository` 上有三个**并发关键**方法，实现时必须落到存储层的原子语义：

- `compareAndSetTaskStatus` —— 单条任务状态 CAS；
- `compareAndAdvanceNode` —— **节点级原子推进**（判据 + 推进同一条条件 UPDATE，见硬纪律 4）；
- `compareAndSetPhase` —— 实例阶段 CAS（终止流程用，避免与并发推进互相覆盖）。

另有 `listTasksByAssignee`（我的待办，跨实例）与 `cancelPendingTasksOfNode`（节点完成后作废余下待办，
否则待办列表出现幽灵项）。

## 接入方式

```xml
<dependency>
  <groupId>com.bone</groupId>
  <artifactId>bone-workflow-starter</artifactId>
  <version>${project.version}</version>
</dependency>
```

`starter` 会在宿主提供**全部 7 个端口**时装配引擎；缺端口不是引擎的错（可能只想用纯领域规则），此时**不**装配引擎
而不是把宿主启动打挂。条件求值器与发布期校验器可被宿主 bean 覆盖。

## 当前状态（v1）

已实现：流程定义模型、条件求值（+ 语义黄金样例）、fail-closed 分支选择、发布期完整性校验、引擎推进
（单签/会签/或签/条件分支/驳回/撤回）、指派冻结、任务 CAS、**节点级原子推进**、重复发起幂等、终态不可回写、
无人可办挂起、错误分类、结构化日志、时钟注入（`Clock`，可测且为 SLA 铺路）。

未实现（后续阶段）：SLA 超时提醒与自动转交、委派、转办/加签、抄送节点（v1 发布期拒绝：静默穿透即「配了不生效」，v2 落地为抄送任务 + 通知）、退回指定节点、子流程、流程版本回溯、
可视化流程设计器、`wf_*` 实体与 BONE 集成适配器的落地（适配器在 starter 内，需 L3 DDL 审批）。
规划中的 v1 补齐（设计稿 §2.3/§4.4）：`NotificationPort` 通知最小集、实例轨迹查询 `listInstanceHistory`、
管理员干预最小集（改派 / 终止 / 催办）。

> 挂起实例的恢复入口（`resume`）已实现：组织架构修好后重新解析审批人并续跑，仍解析不出则保持挂起并明确报错，
> 避免「恢复成功」的假象。

## 边界

- 不做通用 BPMN / DMN / 过程仿真。
- 不自建独立库与独立部署单元——状态与业务数据同源，避免双真源。
- 审批动作的**授权**由平台动作级细码承担（`RuntimeAccessGuard` 对逃逸动作的既有纪律），引擎不自行判权。
