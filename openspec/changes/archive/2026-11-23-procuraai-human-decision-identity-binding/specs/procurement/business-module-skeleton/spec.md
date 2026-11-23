# Spec Delta

## ADDED Requirements

### Requirement: 人工终决身份必须来自登录态
系统在 HTTP 边界执行准入人工终决或人工授标时，SHALL 只接受经过认证的主体提供的租户与操作人身份。请求体自报的租户、操作人或确认人 MUST NOT 成为权威值；与登录态不一致时 MUST 拒绝且不得产生任何状态写入。

#### Scenario: 请求体冒名准入终决被拒
- **WHEN** 已登录用户提交 onboarding approve，请求体中的 `operator` 或 `tenantId` 与登录态不一致
- **THEN** 请求被拒绝，准入单状态与审计记录保持不变

#### Scenario: 匿名或缺少租户时准入终决被拒
- **WHEN** 未认证主体或无法解析租户/操作人的主体提交 onboarding approve
- **THEN** 请求被拒绝，系统不得使用默认租户或请求体身份继续处理

#### Scenario: 请求体冒名授标被拒
- **WHEN** 已登录用户请求 RFx 人工授标，请求体中的确认人与登录态不一致
- **THEN** 请求被拒绝，项目状态保持非 AWARDED，不记录该冒名操作人

### Requirement: 人工授标必须具备可审计的 HTTP 入口
系统 SHALL 提供 RFx 人工授标的 HTTP 入口，并要求调用方具有既有 RFx 审批权限。入口 MUST 使用登录态操作人作为授标确认人；AI 结论、空操作人和非授权主体 MUST NOT 使项目进入 AWARDED。

#### Scenario: 具名审批人授标成功
- **WHEN** 具有 RFx 审批权限的登录态操作人对有效 RFx 请求人工授标
- **THEN** 对应项目进入 AWARDED，并记录登录态操作人为确认人

#### Scenario: 无权限或匿名授标被拒
- **WHEN** 无 RFx 审批权限的主体或匿名主体请求人工授标
- **THEN** 请求被拒绝，项目状态不变，不产生授标审计记录

#### Scenario: AI 结论不得直接授标
- **WHEN** 系统仅依据 AI 建议且没有具名人工确认
- **THEN** 项目不得进入 AWARDED，并返回明确拒绝原因
