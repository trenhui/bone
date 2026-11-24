# procurement/owner-freeze-package Specification

## Purpose
为 procuraAI 的 owner、法务与架构评审提供可逐项签核的冻结包草案，统一记录待决项、真源锚点、验收判据和未闭合风险，并在 owner 签署前保持评测与契约状态诚实。

## Requirements

### Requirement: 冻结包草案覆盖四类评审材料

冻结包草案 SHALL 同时提供契约冻结评审清单、评测扩容计划、法务书面结论输入清单和 D-01…D-18 开口项索引；每类材料 MUST 指向可定位的仓库真源或明确标记“待补真源”。

#### Scenario: owner 打开草案即可逐类评审
- **WHEN** owner 打开冻结包草案
- **THEN** 四类评审材料均存在且各自列出真源路径或“待补真源”标记

#### Scenario: 缺失真源不得冒充已完成
- **WHEN** 某项材料找不到可定位真源
- **THEN** 该项被标记为“待补真源”，且不得被表述为已通过评审或已冻结

### Requirement: 每项待签材料使用统一字段

每一项待签材料 MUST 至少包含状态“Draft / 待 owner 批准”、owner（未指派写“未指派”）、可机器验证或可人工复核的验收判据、未闭合风险、真源锚点、待裁决动作六个字段；字段缺失时该项 MUST 被视为未完成。

#### Scenario: 单项材料具备签核所需的全部字段
- **WHEN** owner 复核任一待签项
- **THEN** 该项同时显示状态、owner、验收判据、未闭合风险、真源锚点和待裁决动作

#### Scenario: 未指派 owner 不编造姓名
- **WHEN** 真源未给出具体 owner
- **THEN** 该项 owner 字段写“未指派”，不得使用部门名、角色名或人名代替签署人

### Requirement: 状态诚实采用 fail-closed

草案 MUST NOT 修改 `eval/ai-decision-evalset.json` 的 `status`、`needsOwnerReview`、`scalePlan.status`、`caseCount`，MUST NOT 修改契约工件冻结状态，MUST NOT 写入 owner 签名、法务书面结论或模型效果达标声明；无法确认的状态 MUST 保持 Draft。

#### Scenario: 评测集仍为种子草稿
- **WHEN** 冻结包草案被生成或归档
- **THEN** `evalset.status` 仍为 `seededDraft`，`scalePlan.status` 仍为 `notStarted`，`caseCount` 仍为 27

#### Scenario: 未授权结论不得出现
- **WHEN** 草案需要填写 owner、法务或模型效果结论
- **THEN** 字段保持“待 owner 批准 / 待法务结论 / 待评测达标”，不得出现伪造签名或达标承诺

### Requirement: 契约冻结评审矩阵可追溯

契约冻结评审清单 SHALL 以 D3-v2、LLD 六卷、`contracts/openapi.yaml`、`contracts/asyncapi.yaml` 为对照面，列出差异项、影响能力码、真源裁决链位置和待 owner 裁决动作；草案 MUST NOT 把差异项表述为已冻结。

#### Scenario: reviewer 追踪一处契约差异
- **WHEN** reviewer 查看任一差异项
- **THEN** 能定位到两侧工件路径或章节、裁决链口径和明确待裁决动作

#### Scenario: 差异项不冒充冻结结论
- **WHEN** 某项契约差异尚未获架构评审批准
- **THEN** 该项状态仍为“Draft / 待 owner 批准”，冻结状态不得改为 Frozen

### Requirement: 评测扩容计划可执行但不冒充达标

评测扩容计划 SHALL 描述 27→200 样本扩容、双人标注、第三方仲裁、Cohen's Kappa ≥ 0.8、训练/验证/冻结集三分离和通过线字段；高后果召回率、override 率等数值 MUST 保持“待 owner 冻结”，不得伪造达标数值。

#### Scenario: owner 审查扩容退出条件
- **WHEN** owner 查看 P1-expand 阶段
- **THEN** 计划列出 200 样本、Kappa ≥ 0.8、三分离和字段级通过线待冻结项

#### Scenario: 数值未冻结不得写成通过
- **WHEN** 高后果召回率或 override 率缺少 owner 冻结值
- **THEN** 对应字段写“待 owner 冻结”，且草案不得宣称模型效果达标

### Requirement: 法务输入清单覆盖不可稀释红线

法务书面结论输入清单 SHALL 覆盖六条不可稀释红线（AI 不碰钱、授标归人、结案归人、三态 fail-closed、可解释与留痕、公平护栏）、主体适用性、场景射程，以及 `D3-v2 §3.1` C-2 按客户属性叠加算法备案 / 安全评估材料包的适用条件；清单 MUST NOT 代替法务结论。

#### Scenario: 法务 reviewer 找到六条红线
- **WHEN** 法务 reviewer 打开清单
- **THEN** 六条红线逐条可见，且主体适用性与场景射程分别列为待结论项

#### Scenario: C-2 条件不被一笔带过
- **WHEN** 清单涉及央国企客户或依法必须招标项目
- **THEN** 明确提示须由法务裁决是否叠加算法备案与安全评估材料包

### Requirement: D-01 至 D-18 开口项逐项登记

草案 SHALL 覆盖 `DECISION-REGISTER.md` 的 D-01 至 D-18 全部开口项，逐项给出当前状态、owner、验收判据、未闭合风险和真源锚点；已 `Landed` 的 D-06 / D-11 可注明工程证据，但 MUST NOT 回写历史台账或伪造 owner 签署。

#### Scenario: 全量开口项无遗漏
- **WHEN** 校验草案的开口项索引
- **THEN** D-01 至 D-18 每个编号均出现且仅出现一次

#### Scenario: 已落地项保留历史诚实
- **WHEN** 草案引用 D-06 或 D-11 的已落地证据
- **THEN** 仅引用既有证据路径，不把用户授权写成 owner 签名，也不回写 CHANGELOG 历史快照

### Requirement: 验收判据可机器复核

草案 SHALL 支持在不修改 CI 三入口判据集合的前提下进行本地确定性复核，复核范围至少包含四类材料齐全、D-01 至 D-18 编号齐全、状态诚实字段未被改写、CI 三入口判据常量未被本 change 修改。

#### Scenario: 本地复核通过
- **WHEN** 对草案执行本地确定性检查
- **THEN** 四类材料、18 个开口项与状态诚实断言全部通过，且三入口判据常量保持原值

#### Scenario: 注入缺项必须转红
- **WHEN** 从草案删除 D-15 或把 `caseCount` 改为 200
- **THEN** 本地确定性检查转红，指出具体缺失项或被篡改字段
