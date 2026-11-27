# procurement/business-module-skeleton Specification

## Purpose
procuraAI 寻源/准入/风险绩效骨架的可观察行为契约：寻源生命周期 fail-closed 迁移与人工授标、证照过期与 OCR 人工复核、准入人工终决、预警来源幂等与 UNKNOWN 转人工、绩效只读不自动授信，以及 6 张核心 DDL 与规范清单一致。

## Requirements

### Requirement: 寻源生命周期 fail-closed 迁移
系统 SHALL 用显式迁移表守护寻源项目状态（DRAFT / PUBLISHED / BIDDING_CLOSED / AWARDED / CLOSED / CANCELLED），未列出的迁移 MUST 被拒绝，终态 MUST NOT 回写。项目 PUBLISHED 之后关键字段（品类 / 预算 / 币种 / 策略 / 窗口）MUST 冻结。

#### Scenario: 非法迁移与终态回写被拒
- **WHEN** 对 AWARDED/CLOSED/CANCELLED 态项目执行 publish 或回退到 DRAFT
- **THEN** 迁移被拒并抛 IllegalStateException，项目状态不变

#### Scenario: 发布后关键字段冻结
- **WHEN** 对 PUBLISHED 态项目改写品类或预算
- **THEN** 改写被拒（INV-01），项目保持原值

### Requirement: RFx 截止时间单调与到期拒标
系统 SHALL 保证 RFx 截止时间只可向后顺延，不可提前；到期后 MUST NOT 接收投标。发布时间 MUST 早于截止时间。

#### Scenario: 截止时间提前被拒
- **WHEN** 把截止时间改为早于当前值
- **THEN** 操作被拒（INV-03）

#### Scenario: 到期后不收标
- **WHEN** 当前时间不早于 due_at 时调用 acceptsBidAt
- **THEN** 返回 false

### Requirement: 授标必须具名人工终决
系统 SHALL 只允许具名人工操作人把项目置为 AWARDED；AI 评分与匿名/空白操作人 MUST NOT 直接授标。

#### Scenario: AI 或匿名不得授标
- **WHEN** 以空白操作人调用 awardByHuman
- **THEN** 抛 IllegalArgumentException，项目不进入 AWARDED

#### Scenario: 具名人工授标
- **WHEN** 以非空登录态操作人调用 awardByHuman
- **THEN** 项目转 AWARDED 且记录该操作人

### Requirement: 证照有效期与 OCR 人工复核
系统 SHALL 拒绝把 EXPIRED 证照作为准入通过依据；OCR 结果 MUST 经具名人工复核才生效，OCR 自报结果 MUST NOT 自证。

#### Scenario: 过期证照不得复核通过
- **WHEN** 对已过期证照执行 reviewByHuman
- **THEN** 抛 IllegalStateException（INV-10），核验状态不得为 VERIFIED

#### Scenario: OCR 须具名人工复核
- **WHEN** 以空白复核人提交 OCR 结论
- **THEN** 抛 IllegalArgumentException（INV-11），状态保持 PENDING

### Requirement: 准入人工终决与并发唯一
系统 SHALL 只允许具名人工操作人置 APPROVED/REJECTED；AI 评分只作建议字段，MUST NOT 直接终决。同一供应商同时仅允许一个 IN_REVIEW 准入单，由 DDL 生成列条件唯一约束兜底。

#### Scenario: AI 高分不得自动准入
- **WHEN** 仅凭 AI 评分尝试置 APPROVED 而无具名操作人
- **THEN** 操作被拒，准入单不进入 APPROVED

#### Scenario: 终态不可回写
- **WHEN** 对 APPROVED/REJECTED 准入单再次提交复核
- **THEN** 迁移被拒（INV-12）

#### Scenario: 并发 IN_REVIEW 唯一
- **WHEN** 对已有 IN_REVIEW 准入单的供应商再起一单
- **THEN** 应用服务拒绝（INV-13），DDL uk_proc_onboarding_in_review 提供数据库级兜底

### Requirement: 风险预警幂等与 UNKNOWN 转人工
系统 SHALL 以 (supplier_id, source, source_ref) 为幂等键去重预警；严重度解析失败或空值 MUST 归 UNKNOWN 并 fail-closed 转人工，MUST NOT 压成 LOW 自动放行。预警处置 MUST 由具名人工操作。

#### Scenario: 重复投递幂等
- **WHEN** 以相同 (供应商, 来源, 来源引用) 重复登记预警
- **THEN** 复用既有预警 ID，不新增记录

#### Scenario: UNKNOWN 转人工
- **WHEN** 严重度为未知码或空值
- **THEN** severity 归 UNKNOWN 且 requiresHumanReview() 为 true

#### Scenario: 匿名不得处置预警
- **WHEN** 以空白操作人确认 OPEN 预警
- **THEN** 抛 IllegalArgumentException，状态保持 OPEN

### Requirement: 绩效只读不自动授信
系统 SHALL 把绩效评分作为只读投影；MUST NOT 因分数高低自动产出供应商授信或淘汰决定（权重规则与外部征信属 owner 红线）。

#### Scenario: 高分不自动决定供应商去留
- **WHEN** 查询 canDecideSupplierAutomatically()
- **THEN** 恒返回 false

### Requirement: 核心 DDL 与规范清单一致
系统 SHALL 在 bone-init.sql 定义 6 张业务表，全部含必备列（tenant_id / created_at / updated_at / deleted）与审计四列，并在数据库开发规范 §2.17 登记（采购表数 9 → 15）；init-only 不建增量建表迁移。

#### Scenario: DDL 与规范同步
- **WHEN** 运行 check-ddl-doc-sync.py
- **THEN** SQL 表清单与 §2 清单零差异

#### Scenario: 新增表必备列齐全
- **WHEN** 运行 check-ddl-required-columns.py --check
- **THEN** 新增表 0 违规

#### Scenario: 实体 DDL 对账
- **WHEN** 运行三模块 DdlCoverageTest
- **THEN** 每个 @Table 实体都有对应建表与齐全审计列

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

### Requirement: 投标生命周期与到期拒收

系统 SHALL 用显式状态（DRAFT / SUBMITTED / WITHDRAWN / REJECTED）守护投标；到期后 MUST NOT 接收新投标或从撤回态恢复提交。同一租户、RFx 与供应商组合 MUST 唯一；同一来源引用重复投递 MUST 幂等，不得生成第二条投标事实。

#### Scenario: 到期后提交被拒
- **WHEN** 当前时间不早于 RFx 截止时间时对有效 DRAFT 投标执行提交
- **THEN** 提交被拒，投标保持原状态，不产生投标事实

#### Scenario: 同一供应商重复投标被拒
- **WHEN** 同一租户同一 RFx 下已存在该供应商的 SUBMITTED 投标时再次提交
- **THEN** 第二次提交被拒，既有投标状态与内容不变

#### Scenario: 来源引用重复投递幂等
- **WHEN** 以相同租户、RFx、供应商与来源引用重复登记投标
- **THEN** 系统复用既有投标标识，不新增记录，也不重复触发提交副作用

#### Scenario: 撤回态不得恢复提交
- **WHEN** 对 WITHDRAWN 投标再次执行提交
- **THEN** 操作被拒，投标保持 WITHDRAWN

### Requirement: 报价比价只读与 AI 建议边界

系统 SHALL 以报价明细、币种与有效期形成只读比价视图；币种不一致或有效期已过的报价 MUST 标记为不可比而不被静默纳入排序。AI 评分与推荐排序 MUST 只作建议字段，不得产生授标、入围或自动淘汰结论。

#### Scenario: 过期报价不可比
- **WHEN** 报价有效期已过时被纳入比价视图
- **THEN** 该报价被标记为不可比，排序不将其作为有效候选

#### Scenario: 币种不一致不可比
- **WHEN** 报价币种与 RFx 预算币种不一致时被纳入比价视图
- **THEN** 该报价被标记为不可比，不参与金额排序

#### Scenario: AI 推荐不自动授标
- **WHEN** 报价附带 AI 推荐排序或高分建议
- **THEN** 视图仅展示建议字段，项目状态不得进入 AWARDED，供应商不得被自动淘汰

### Requirement: 授标记录具名人工终决

系统 SHALL 只允许登录态具名人工确认授标或拒绝；匿名、空白操作人、AI 建议或请求体自报身份 MUST 被拒绝。授标后项目与相关投标终态 MUST NOT 回写，记录 MUST 包含确认人、时间与理由。

#### Scenario: 匿名或空白操作人不得授标
- **WHEN** 未认证主体或空白确认人请求人工授标
- **THEN** 请求被拒，项目状态保持不变，不产生授标记录

#### Scenario: AI 建议不得直接授标
- **WHEN** 系统仅依据报价 AI 推荐且无具名人工确认
- **THEN** 项目不得进入 AWARDED，并返回明确拒绝原因

#### Scenario: 具名人工授标成功并留痕
- **WHEN** 具有授标权限的登录态操作人以非空理由确认授标
- **THEN** 项目进入 AWARDED，授标记录包含登录态确认人、时间与理由

#### Scenario: 授标终态不可回写
- **WHEN** 对已 AWARDED 项目再次执行授标或状态回退请求
- **THEN** 请求被拒，项目状态与既有授标记录不变

### Requirement: CAPA 立项、整改与超期转人工

系统 SHALL 为供应商问题建立 CAPA 记录，包含问题描述、责任人、期限、整改动作与关闭证据；责任人或期限缺失 MUST 拒绝立项。超期未关闭 MUST 转人工跟进并保留原期限，不得自动放宽期限。CAPA MUST NOT 自动改变供应商准入状态、绩效评分或授信。

#### Scenario: 缺失责任人或期限不得立项
- **WHEN** 创建 CAPA 时责任人或期限为空白
- **THEN** 立项被拒，不产生 CAPA 事实

#### Scenario: 超期未关闭转人工
- **WHEN** CAPA 超过期限且仍处于 OPEN
- **THEN** 标记需人工跟进并保留原期限，不自动关闭或延长

#### Scenario: 关闭必须具备名操作人与证据
- **WHEN** 以空白操作人或无关闭证据请求关闭 CAPA
- **THEN** 关闭被拒，CAPA 状态保持 OPEN

#### Scenario: CAPA 不自动改变供应商状态
- **WHEN** CAPA 处于 OPEN、超期或关闭任一状态
- **THEN** 系统不得自动改变供应商准入状态、绩效评分或授信

### Requirement: 聚合查询按登录态租户隔离

系统 SHALL 仅向登录态租户返回投标、报价、授标与 CAPA 聚合的列表或详情；缺失租户上下文或跨租户查询 MUST 被拒绝或返回空集，不得回显他方业务事实。

#### Scenario: 跨租户详情查询被拒
- **WHEN** 租户 B 请求租户 A 的投标、报价、授标或 CAPA 详情
- **THEN** 请求被拒或返回空，不回显租户 A 的业务字段

#### Scenario: 缺失租户上下文查询失败关闭
- **WHEN** 未解析出登录态租户时请求聚合列表
- **THEN** 查询被拒或返回空集，不暴露默认租户数据
