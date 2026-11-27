# Proposal：procuraAI 业务聚合补齐（投标 / 报价 / 授标 / CAPA）

## Why

procuraAI 已有寻源项目、RFx、准入、风险绩效的骨架，但 Gartner 对 Source-to-Pay 套件的定义强调寻源—合同—采购—收货—付款的连续数据流，e-sourcing 实务把 RFI/RFP 征集、竞争投标、报价比较与具名授标视为核心环节；供应商质量场景还需要 CAPA（纠正预防措施）闭环。当前代码层缺少 Bid / Quotation / Award / CAPA 四类聚合，LLD 的关键不变量无法在领域层执行，导致寻源到授标、供应商问题到整改的产品链路只能靠口头约定。

## What Changes

- 新增 **Bid（投标）** 聚合：草稿 / 提交 / 撤回状态、截止时间后拒收、租户锚定与按来源幂等；AI 评分只能作为建议字段，不得替代投标事实。
- 新增 **Quotation（报价）** 聚合：报价明细、币种与有效期校验、比价只读视图；金额计算与推荐排序为规则/AI 建议，不自动产生授标结论。
- 新增 **Award（授标）** 聚合：仅具名人工可确认授标或拒绝，匿名、AI 建议、空白操作人一律拒绝；终态不可回写，保留确认人、时间与理由。
- 新增 **CAPA（纠正预防措施）** 聚合：问题立项、责任人、期限、整改动作与关闭；超期未关闭转人工跟进，不自动淘汰供应商或改变授信。
- 提供与现有模块一致的只读列表 / 详情端点，沿用登录态租户与操作人身份，不新增 DDL、不改 CI 三入口判据集合、不执行生产库操作。
- 为四类聚合补齐正向与负向测试，覆盖终态回写、跨租户、到期投标、匿名/AI 授标、CAPA 超期等 fail-closed 场景。

## Capabilities

### New Capabilities

- （无。新增需求归属于既有 `procurement/business-module-skeleton`，不建立近似重复的能力边界。）

### Modified Capabilities

- `procurement/business-module-skeleton`：在既有寻源生命周期、人工授标与准入/风险不变量之上，增加投标、报价、授标记录与 CAPA 四类聚合的行为要求与验收场景。

## Impact

- **后端**：在 `bone-procurement-sourcing` / `bone-procurement-qualification` 相邻边界新增领域聚合、应用服务与只读 Web 端点；不改持久化表结构，沿用现有 SDK/内存仓储模式与统一 `ApiResponse` 契约。
- **测试**：新增领域不变量测试、应用层 fail-closed 测试与端点身份绑定测试；采购模块测试与主门禁必须保持通过。
- **文档**：新增本 change 的 delta spec 与设计/任务产物；不改主 spec 的历史快照。
- **非目标**：不做 owner 契约冻结、评测转正、真实模型接入或效果达标声明；不改法务/合规结论；不接生产 MQ；不新增 DDL 或执行生产库操作；不扩大 CI 判据集合。
