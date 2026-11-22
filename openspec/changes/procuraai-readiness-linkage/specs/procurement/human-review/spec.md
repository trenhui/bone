# Spec Delta: 采购人工终决可操作契约

## Purpose

保证采购高风险 AI 环节（证照 OCR 复核、合同审查推翻）在"AI 仅辅助、人工最终决策"的前提下可被具体操作、可持久化读回、可审计，并防止重复或跨租户篡改。

## ADDED Requirements

### Requirement: 证照 OCR 人工复核必须可用且具名

系统 MUST 提供证照 OCR 人工复核能力，由具名复核人提交结论；MUST NOT 以 `UnsupportedOperationException` 或等价未实现异常响应正常业务请求。复核结论 MUST 持久化并可被读回，读回值 MUST 与提交值一致。

复核记录 MUST 携带租户上下文、复核人身份、复核时间、被复核对象标识与结论；缺失租户上下文时 MUST fail-closed 拒绝，MUST NOT 使用默认租户或空值落库。

#### Scenario: 具名复核后可读回

- **WHEN** 具名复核人提交一条 OCR 复核结论
- **THEN** 系统 MUST 持久化该记录，且后续读回 MUST 返回相同结论、复核人与租户

#### Scenario: 缺租户时拒绝

- **WHEN** 复核请求缺少租户上下文
- **THEN** 系统 MUST 拒绝该请求，MUST NOT 以默认租户或空租户写入

### Requirement: 人工复核必须幂等且拒绝跨租户篡改

同一复核对象在同一租户下重复提交 MUST 幂等：MUST NOT 产生第二条记录、MUST NOT 报错为内部异常；幂等键与对象标识 MUST 唯一约束。跨租户对同一对象提交复核 MUST 被拒绝，MUST NOT 覆盖原记录。

#### Scenario: 重复提交幂等

- **WHEN** 相同租户、相同对象以相同幂等键重复提交复核
- **THEN** 系统 MUST 返回首次结果，且持久化记录数 MUST 保持为 1

#### Scenario: 跨租户覆写拒绝

- **WHEN** 另一租户尝试复核同一对象
- **THEN** 系统 MUST 拒绝，原租户记录 MUST 保持不变

### Requirement: 前端必须提供可操作的人工终决工作台

前端 MUST 提供人工终决工作台页面，展示待决项及其 AI 建议/披露信息，允许具名用户执行采纳或驳回，并把结果写回后端后刷新读回。MUST NOT 只允许只读查看；MUST NOT 将 AI 建议态渲染为已自动生效的最终结论。

未知状态 MUST 显示原始码，取数失败 MUST 显示"无法确认"，MUST NOT 乐观映射为成功或达标。

#### Scenario: 人工终决可写回并读回

- **WHEN** 用户在终决工作台对某待决项执行采纳或驳回
- **THEN** 前端 MUST 提交到后端，刷新后 MUST 显示后端读回的终决结果，而非本地乐观值

#### Scenario: AI 建议不得被渲染为自动生效

- **WHEN** 待决项只有 AI 建议态且尚未人工终决
- **THEN** 工作台 MUST 显示其为待决/建议，MUST NOT 显示为已生效结论

### Requirement: 前后端联调必须以落库为第二证据

前后端联调的验收 MUST 包含真实启动后端与前端、真实浏览器操作以及数据库落库直查或持久化读回。仅 2xx 响应 MUST NOT 单独判定成功。

#### Scenario: 端到端操作有落库证据

- **WHEN** 通过浏览器完成一次人工终决并提交
- **THEN** 数据库或持久化读回 MUST 出现对应记录，且字段与提交一致
