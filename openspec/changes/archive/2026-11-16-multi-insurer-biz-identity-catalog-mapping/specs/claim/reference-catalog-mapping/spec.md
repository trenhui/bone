## Purpose

基于三层主数据映射体系（标准库唯一真源 → 保司码表 → 映射，外加分字段绑定与标准码生命周期），规范「保司码→标准码」解析链的验收口径：fail-closed、未命中落库可治理、映射版本化可回放、导入可运营。标准库（t_ref_* 四库）仍是唯一真源，规则只认标准码。

## ADDED Requirements

### Requirement: 解析 fail-closed
保司码→标准码解析 SHALL 遵循：命中唯一 ACTIVE 映射则返回标准码；多义（同码多条 ACTIVE 且无消歧键）、标准码已拆分（1:N）、未命中一律 fail-closed——返回 MISSING/歧义状态且 standard_code 为 null，MUST NOT 回落空串、原始码或猜测值；解析入口同时负责未命中/歧义的落库记账。

#### Scenario: 唯一命中解析成功
- **WHEN** 以 (YCBX, HOSPITAL, XHYY) 解析且仅一条 ACTIVE 映射
- **THEN** 返回 RESOLVED 与标准码、标准名称

#### Scenario: 未命中 fail-closed 且不入库原始码
- **WHEN** 收单侧解析无映射的保司码
- **THEN** 返回 MISSING（standard_code=null），明细不写原始码兜底，该码进入未命中队列

#### Scenario: 多义转人工
- **WHEN** 同一保司码存在两条 ACTIVE 映射且调用方未提供消歧键
- **THEN** 返回歧义状态转人工裁决，不自动选择任何一条

### Requirement: 批量解析
解析入口 SHALL 提供批量解析能力：一次请求传入同一主体+域下的多个保司码，返回逐码结果（命中/MISSING/歧义各自独立）；批量结果 MUST 与逐码解析语义完全一致，任一码的失败不影响其他码的结果；单批上限与超限拒绝策略 SHALL 与导入一致。

#### Scenario: 批量解析混合结果
- **WHEN** 收单侧一次传入 20 个保司码，其中 17 个命中、2 个未命中、1 个多义
- **THEN** 返回 20 条逐码结果，未命中与多义的码 fail-closed 并各自入队，命中码正常返回

#### Scenario: 批量与单码语义一致
- **WHEN** 同一批码分别用单码接口与批量接口解析
- **THEN** 逐码结果（状态、标准码）完全一致

### Requirement: 主数据健康度度量
系统 SHALL 提供按主体×域的映射健康度统计：标准目录总数、已建立映射数、映射覆盖率、未命中队列 OPEN 数与近 N 天新增未命中数；统计 SHALL 供映射工作台顶部卡片与主体选择联动展示，数据来源为既有表聚合查询，不建新表。

#### Scenario: 工作台展示覆盖率
- **WHEN** 运营在映射工作台选择主体 YCBX 与域「医院」
- **THEN** 页面展示该主体该域的映射覆盖率与 OPEN 未命中数

#### Scenario: 低覆盖率提示
- **WHEN** 某主体某域映射覆盖率低于阈值（如 80%）
- **THEN** 工作台给出显著提示，引导导入或处置未命中

### Requirement: 未命中队列一码一行
未命中队列 SHALL 按 (tenant, insurer, domain, raw_code) 一码一行记账：重复未命中累加 occur_count、更新 last_seen_at；已解决（RESOLVED）后再次未命中累加 reopen_count 并重新打开（映射失效信号）；状态流转 OPEN/RESOLVED/IGNORED，唯一键 MUST NOT 含状态列。

#### Scenario: 三次未命中不撞键
- **WHEN** 同一保司码经历 OPEN→RESOLVED→OPEN→IGNORED→OPEN
- **THEN** 队列中仍为一行，occur_count=3，状态 OPEN

#### Scenario: 已解决后复发计入 reopen
- **WHEN** 某码 RESOLVED 后再次未命中
- **THEN** 该行 reopen_count+1 并回到 OPEN

### Requirement: 映射版本化发布
映射发布 SHALL 采用「关旧行+插新行」：旧行 active_flag 置空并记录 superseded_by_id，新行 active_flag='Y' 且携带 version_no（发布批次）、match_rule、source、reason_code；同一 (tenant, insurer, domain, item, 消歧键) 下 active_flag='Y' 的行 MUST 唯一；发布前 MUST 校验标准码存在；已结案案件可按 version_no 回放「当时按哪条映射翻译」。

#### Scenario: 重发布保留回放链
- **WHEN** 对已有 ACTIVE 映射的码发布新标准码
- **THEN** 旧行关闭并指向新行，历史案件按原 version_no 回放结果不变

#### Scenario: 发布前标准码不存在被拒
- **WHEN** 发布映射指向标准库不存在的码
- **THEN** 发布被拒绝并提示标准目录项不存在

### Requirement: 映射 Excel 批量导入
系统 SHALL 支持按主体+域批量导入映射（Excel .xlsx，列：保司码值/保司名称/标准码），source=IMPORT，并提供模板下载（列头+示例行）；导入 SHALL 全量逐行校验（标准码存在、保司码格式与重复），任一失败整批拒绝并返回行号+原因清单，不写入任何行；成功行逐条走版本化发布语义；单批行数上限 2000，超限拒绝；重复上传内容完全相同的文件 SHALL 幂等处理（按主体+域+文件内容指纹去重，直接返回首次导入结果），内容有差异的文件视为一次新发布。

#### Scenario: 全量校验通过导入成功
- **WHEN** 用户上传 100 行全部合法的映射文件
- **THEN** 系统一次性发布 100 条映射并返回成功统计

#### Scenario: 存在非法行时整批失败
- **WHEN** 文件中有 2 行标准码不存在
- **THEN** 不写入任何行，返回这 2 行的行号与失败原因

#### Scenario: 超单批上限被拒
- **WHEN** 用户上传 2001 行文件
- **THEN** 系统拒绝导入并提示单批上限

#### Scenario: 重复上传相同文件幂等
- **WHEN** 用户第二次上传与某次成功导入完全相同的文件
- **THEN** 系统识别内容指纹，不产生新映射版本，返回首次导入的结果摘要

#### Scenario: 模板下载
- **WHEN** 用户点击导入弹窗中的「下载模板」
- **THEN** 返回含列头与示例行的 .xlsx 模板

### Requirement: 标准码生命周期与引用保护
标准目录项 MUST NOT 物理删除而使映射指向幽灵码：弃用/更名走 1:1 别名（MERGE/RENAME，映射解析时透明归并到现行码），一拆多走拆分登记（转人工分流）；直接删除仍被 ACTIVE 映射引用的标准项 MUST 被拒绝或提示引用数量后走弃用流程。

#### Scenario: 标准码合并后旧映射解析归并
- **WHEN** 标准码 A 被归并到 B（RENAME/MERGE 别名生效）
- **THEN** 历史映射解析到 A 时返回现行码 B

#### Scenario: 删除被引用标准项被拦截
- **WHEN** 用户删除仍被 ACTIVE 映射引用的标准医院
- **THEN** 系统提示引用映射数量，引导走弃用/归并流程

### Requirement: 管理面查询
系统 SHALL 提供映射、保司码表、未命中队列的分页查询（按主体+域筛选、关键字检索、未命中按 last_seen_at/occur_count 排序）及人工裁决入口（补映射/忽略并记录原因），供映射工作台消费；未命中的每次处置 MUST 留痕（操作人、时间、动作、reason_code），处置动作 SHALL 需要独立权限点（与映射查看权限分离）；导入与发布动作 SHALL 同样记录操作人与批次。

#### Scenario: 按主体查看映射列表
- **WHEN** 用户选择主体 YCBX 与域「医院」查询映射
- **THEN** 返回该主体该域的映射分页结果，含保司码值、标准码、标准名称与版本批次

#### Scenario: 处置留痕
- **WHEN** 运营对一条 OPEN 未命中执行「忽略」
- **THEN** 该行状态转为 IGNORED，并记录操作人、时间与忽略原因，可在详情中查看
