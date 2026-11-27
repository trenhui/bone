# Change: 签收功能对齐普康 TPA 团险签收

## Why

深度抓取普康 TPA `jobManage/groupInsuranceSignList` 后发现 Bone 签收页虽已具备四态批次与自动建案，
但缺少参考站的核心作业能力：服务端组合查询、批次/赔案双维度视图、保险公司三方字段、紧急程度、
批量名单与影像明细。用户在签收台无法按批次号/保司/签收人/时间窗定位批次，也无法在批次内查看
赔案级影像要素，导致"能签收但不好用"。

## What Changes

- 新增 `claim/sign-tpa-parity` 能力：签收批次服务端组合查询、批次明细视图、TPA 对齐字段。
- 修改 `claim/secure-signing`：批次实体与响应 DTO 扩展保司/紧急程度等字段；保持四态状态机与
  自动建案链路不变。

## Capabilities

### New Capabilities
- `claim/sign-tpa-parity`：以参考站真实页面为基准的查询、展示与批次详情能力。

### Modified Capabilities
- `claim/secure-signing`：批次响应新增字段，查询入参扩展；不改变状态迁移与幂等语义。

## Impact

- 数据库：新增 0147 迁移（批次 4 列 + 明细 6 列），同步 `bone-init.sql` 与 manifest。
- 后端：DTO、实体、仓储查询条件、新明细查询端点。
- 前端：签收页查询表单、列集合、三段 Tab、批次详情抽屉。
