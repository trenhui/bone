# Specification: claim/sign-tpa-parity

## ADDED Requirements

### Requirement: 签收批次服务端组合查询
系统 SHALL 支持按批次号（模糊）、签收方式、签收渠道、签收状态、保险公司、保险分公司、
投保公司、签收人员、签收时间窗、创建时间窗组合查询签收批次，分页在数据库完成。

#### Scenario: 按保司与签收人筛选
- GIVEN 签收批次列表存在多条不同保司/签收人记录
- WHEN 查询参数包含 insuranceCompany 与 signOperator
- THEN 仅返回同时满足两个条件的批次，total 为过滤后总数

#### Scenario: 三段来源筛选
- GIVEN source 参数可为 ALL / ONLINE / OFFLINE
- WHEN source=ALL
- THEN 返回线上与线下全部批次；source 为空时行为与 ALL 一致

### Requirement: 批次字段对齐参考站
签收批次 SHALL 记录并展示保险公司、保险分公司、投保公司、紧急程度；列表 SHALL 展示创建时间与备注。
影像状态 SHALL 由 imageReadyCount/claimCount 派生为"是/否"，不冗余存储布尔列。

### Requirement: 批次明细视图
系统 SHALL 提供批次内赔案明细查询，返回赔案号、保单号、出险人、证件类型、证件号、影像要素与建案结果。

#### Scenario: 查看批次详情
- GIVEN 批次存在 N 条明细
- WHEN 打开批次详情
- THEN 同时展示批次摘要、赔案明细、轨迹与自动建案结果，任一子请求失败不影响其他子请求

## MODIFIED Requirements

### Requirement: 签收批次列表响应
`SignBatchResp` SHALL 新增 insuranceCompany、insuranceSubsidiary、insuringAgency、emergency、createdAt
字段；既有 ID 仍为字符串，避免雪花 ID 精度丢失。
