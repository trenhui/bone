/**
 * 受控码展示字典 · 统一出口与契约声明。
 *
 * ⚠️ 单一真源纪律：本目录/服务下的所有「受控码 → 中文标签」映射，其<b>唯一真源是后端</b>
 * （synap-claim 的域枚举、错误码、规则码，以及 delivery.05《接口契约与数据字典》）。
 * 前端这些文件只是<b>镜像</b>，用于把后端下发的 code 渲染成可读中文。
 *
 * 漂移防护（业界最佳实践）：
 * 1. 绝不在前端<b>发明</b>后端没有的 code；未知码一律回退原始 code（各字典已做降级可见）。
 * 2. 后端枚举/错误码/规则码变更时，必须同步改这里对应的映射；改完跑一次页面核对。
 * 3. 真正的根治是<b>代码生成</b>：后端经 OpenAPI / {@code GET /api/v1/claim-config/dicts} 暴露
 *    code→label，前端构建期 codegen 生成本模块，彻底消灭手维护双源。本文件即为此迁移的落点——
 *    新增字典请在此登记，并标注其对应的后端枚举/码表（见下方逐项映射）。
 *
 * 逐项映射（前端字典 → 后端真源）：
 * - PERMISSIONS                → IAM 权限码（claim:claims:read 等），由 bone-iam 下发
 * - CLAIM_STATUS_META         → ClaimStatus 域枚举（domain/model/claim/ClaimStatus）
 * - REJECT_REASON_LABELS      → 拒赔受控原因码（domain/security 下受控码）
 * - ZERO_REASON_LABELS        → 零赔付受控原因码（同上）
 * - FRAUD_GATE_LABELS         → 反欺诈硬闸 R-FRAUD 规则码
 * - CONSUMPTION_REASON_META   → 自动放款消费结果字典（DecisionConsumption 相关枚举）
 * - INVOLVEMENT_META          → AI 参与标识 AiInvolvement 枚举
 */
export * from './permissions';
export * from './dicts.generated';
export * from '../services/claimStatus';
export * from '../services/rejectReason';
export * from '../services/decisionExplain';
export * from '../services/decisionConsumption';
export * from '../services/aiDisclosure';
