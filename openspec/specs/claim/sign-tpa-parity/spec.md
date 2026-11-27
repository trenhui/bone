# claim/sign-tpa-parity Specification

## Purpose
TBD - created by archiving change claim-sign-tpa-parity. Update Purpose after archive.

## Requirements

### Requirement: 签收批次列表响应
`SignBatchResp` SHALL 新增 insuranceCompany、insuranceSubsidiary、insuringAgency、emergency、createdAt
字段；既有 ID 仍为字符串，避免雪花 ID 精度丢失。

#### Scenario: 扩展字段与字符串 ID 契约
- GIVEN 分页查询返回一条签收批次
- WHEN 序列化为 `SignBatchResp`
- THEN 响应包含上述新增字段，且 batchId 为字符串、与雪花 ID 的值一一对应不发生精度丢失
