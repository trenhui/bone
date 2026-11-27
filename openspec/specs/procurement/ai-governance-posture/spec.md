# procurement/ai-governance-posture Specification

## Purpose
把 procuraAI 已就绪的只读治理信号（能力裁决、漂移三态、监督有效性三态、全域总闸、就绪度）聚合为单一可复核的态势视图，使运维与审查者能在一处看到「哪些 AI 能力可用、哪些需要关注、哪些不可判定」，而不改变人工终决与 fail-closed 红线。

## Requirements

### Requirement: 治理态势聚合端点必须只读且 fail-closed

系统 MUST 提供一个需要 `procurement:ai-governance:read` 权限的只读聚合端点，返回能力清单及每个能力的裁决、漂移判词、监督判词，以及全域总闸与汇总态势判词。治理能力开关关闭时，该端点 MUST NOT 注册。端点 MUST NOT 接受任何客户端传入的阈值、判词或状态参数；阈值 MUST 只来自服务端 owner 配置。

#### Scenario: 无权限读取被拒绝

- GIVEN 治理能力开关已启用
- WHEN 一个仅具读权限之外权限的主体请求聚合端点
- THEN 请求被拒绝，且不返回任何聚合数据

#### Scenario: 治理开关关闭时端点不存在

- GIVEN 治理能力开关关闭
- WHEN 任何主体请求聚合端点
- THEN 端点未注册（404 或等效），不返回任何聚合数据

#### Scenario: 客户端阈值注入被忽略

- GIVEN 服务端未配置漂移/监督阈值
- WHEN 请求携带试图自设通过线的查询参数
- THEN 相关分项判词仍为 UNDETERMINED，不因客户端参数变为通过

### Requirement: 汇总态势判词必须由分项确定性推导

系统 MUST 由分项判词确定性推导汇总态势判词 `UNDETERMINED` / `ALL_CLEAR` / `ATTENTION_REQUIRED`：任一必填分项不可判定或缺失时 MUST 为 `UNDETERMINED`；否则任一能力的漂移或监督判词 actionable 时 MUST 为 `ATTENTION_REQUIRED`；全部通过时为 `ALL_CLEAR`。汇总不得掩盖不可判定分项，也不得声明模型效果达标。

#### Scenario: 分项不可判定则整体不可判定

- GIVEN 能力清单中至少一个能力的监督判词为 UNDETERMINED
- WHEN 计算汇总态势判词
- THEN 结果为 UNDETERMINED，即便其余能力全部通过

#### Scenario: 存在需关注能力则整体需关注

- GIVEN 所有分项均可判定，且至少一个能力的漂移判词 actionable
- WHEN 计算汇总态势判词
- THEN 结果为 ATTENTION_REQUIRED

#### Scenario: 全部通过则整体清楚

- GIVEN 所有分项均可判定、所有能力判词均不 actionable、全域总闸未停用
- WHEN 计算汇总态势判词
- THEN 结果为 ALL_CLEAR

### Requirement: 前端必须诚实渲染聚合治理态势

前端治理态页面 MUST 展示汇总判词、能力明细（状态/裁决/漂移/监督）与全域总闸。未知判词码 MUST 显示原始码而非压成「正常」或「未参与」；任一分项取数失败 MUST 显示「无法确认」而非空白或默认通过。合同审查 AI 参与披露 MUST 以入口链接呈现，不得虚构全局披露状态。页面 MUST NOT 触发任何治理写操作，MUST NOT 接收或回显 PII 与模型输出原文。

#### Scenario: 未知判词显示原始码

- GIVEN 后端返回前端未识别的判词码 `SOME_NEW_VERDICT`
- WHEN 页面渲染该判词
- THEN 展示原始码文本 `SOME_NEW_VERDICT`，不展示「正常」

#### Scenario: 取数失败显示无法确认

- GIVEN 聚合端点请求失败或返回空
- WHEN 页面渲染
- THEN 显示「无法确认」提示，不显示默认通过态

#### Scenario: 披露以入口呈现不虚构状态

- GIVEN 合同审查披露为 per-review 端点
- WHEN 页面渲染披露区域
- THEN 仅渲染跳转至已有披露页的入口，不生成全局「已披露/未披露」结论
