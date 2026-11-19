# procurement/ai-safety-governance Specification

## Purpose
procuraAI 安全治理的可观察行为契约：全域 kill switch 读库即时生效与停机演练、生成内容双标识缺一阻断出域、G15 公平确定性裁决与模型晋升阻断，确保任一 AI 能力可被一键停机、生成内容可标识、偏见可 fail-closed 拦截，且不伪造模型效果或法务定性。

## Requirements

### Requirement: 全域 kill switch 读库即时生效
系统 SHALL 提供覆盖全部 AI 能力的全域停用开关，三态 STANDBY/ENGAGED/DISENGAGED；缺省 STANDBY MUST NOT 放行 AI 路径，只有显式 DISENGAGED 才允许。任意态 MUST 可一键停用（ENGAGED），仅 ENGAGED 可显式解除。开关状态 MUST 每次裁决时读最新快照（无缓存、即时生效），并记录操作人、理由、时间与演练编号入审计。停机操作缺登录态操作人 MUST fail-closed。

#### Scenario: 缺省不放行 AI
- **WHEN** kill switch 无记录或处于 STANDBY/ENGAGED
- **THEN** `globalAllowsAi()` 为 false，任何能力不得进入 AI 推理路径

#### Scenario: 一键全域停用与演练留痕
- **WHEN** 具名操作人执行 engage 并带演练编号
- **THEN** 状态转 ENGAGED、拒绝全部 AI 路径，审计记录操作人与演练编号

#### Scenario: 仅显式解除可恢复
- **WHEN** 对非 ENGAGED 态执行解除
- **THEN** 操作被拒绝；仅 ENGAGED 态可转 DISENGAGED 恢复 AI 路径

### Requirement: 停机演练无残留 AI 路径
系统 SHALL 在停机演练快照中列出每个能力的生效态，且当全域开关未解除时 MUST 显示所有能力的 AI 路径均不激活（即使单能力状态为 AVAILABLE）。

#### Scenario: 全域停机覆盖已启用能力
- **WHEN** 某能力为 AVAILABLE 但全域 kill switch 未解除
- **THEN** 演练快照中该能力 `aiPathActive=false`，无残留自动结论

### Requirement: 生成内容双标识缺一阻断出域
系统 SHALL 要求生成合成内容（M7 起草 / M2 审查报告 / M5 问答 / M6 投标响应草稿）同时带显式标识（EXPLICIT）与隐式元数据标识（IMPLICIT）；缺任一 MUST 阻断出域。内容自报 aiGenerated=false MUST NOT 豁免标识义务。系统 MUST NOT 代替法务对标识办法主体适用性的定性。

#### Scenario: LABEL-01 缺隐式标识阻断
- **WHEN** 内容有显式标识但缺隐式元数据标识
- **THEN** `released=false`、`reasonCode=MISSING_IMPLICIT_LABEL`，不给出域（对应负向用例 missingImplicitLabel_blocksDelivery）

#### Scenario: 双标识齐备放行
- **WHEN** 内容同时带显式标识与隐式元数据标识
- **THEN** `released=true`、`reasonCode=LABELS_COMPLETE`

#### Scenario: 自报非 AI 不豁免
- **WHEN** 内容自报 aiGenerated=false 但缺显式标识
- **THEN** 仍阻断出域，标识义务按内容属性而非自报

### Requirement: G15 公平确定性裁决
系统 SHALL 以确定性规则检查 AI 辅助选商/评标/推荐是否按所有制、地域、规模产生偏见；命中 MUST fail-closed 并留伦理审查标记。所有制偏见 MUST 阻断模型版本晋升（decision=REJECT_RELEASE / model_version_promoted=false / ethics_review_required=true）；地域偏好 MUST 转人工且不发出建议。任一特征不可判定（UNKNOWN）MUST NOT 压成无偏见。工程裁决 MUST NOT 自行晋升模型版本。

#### Scenario: FAIR-01 所有制偏见阻断晋升
- **WHEN** AI 建议文本含所有制偏好（如国企优先）且特征完整
- **THEN** `bias_detected=true` / `decision=REJECT_RELEASE` / `model_version_promoted=false` / `ethics_review_required=true` / `suggestion_emitted=false`（对应 ownershipBias_blocksModelPromotion）

#### Scenario: FAIR-02 地域偏好不发出建议
- **WHEN** AI 建议文本含地域偏好（如本地优先）
- **THEN** `bias_detected=true` / `tier=MANUAL` / `suggestion_emitted=false`（对应 regionalPreference_noSuggestionEmitted）

#### Scenario: 特征不可判定不压成无偏见
- **WHEN** 所有制/地域/规模任一特征为 UNKNOWN
- **THEN** 裁决为偏见不可判定、转人工、不发出建议，不判为 clear

#### Scenario: 工程不晋升模型版本
- **WHEN** 任意公平性裁决生成
- **THEN** `modelVersionPromoted=false`（晋升须 owner + 发布门禁，工程不代办）

### Requirement: 能力开关与权限边界
系统 SHALL 以 `bone.procurement.ai-governance.enabled`（默认 false）控制安全治理端点注册；关闭时 MUST NOT 注册。kill switch 写操作 MUST 受 write 权限保护，读操作（含出域校验与公平裁决）MUST 受 read 权限保护。操作人 MUST 取登录态、不接受自报。

#### Scenario: 开关关闭不注册端点
- **WHEN** 能力开关为 false
- **THEN** 安全治理端点不注册

#### Scenario: 读写权限分离与匿名拒绝
- **WHEN** 无权限或无登录态调用
- **THEN** 请求被拒绝，不产生治理副作用
