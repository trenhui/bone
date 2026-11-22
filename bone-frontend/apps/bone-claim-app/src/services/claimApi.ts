/**
 * synapClaim 理赔 API —— barrel export（向后兼容入口）。
 *
 * 复用平台 createApiClient：统一 Bearer 注入、把 axios 响应解包为 ApiResponse 信封
 * （返回体仍是 { code, message, data }，业务数据须经 .data 取用）、401 过期广播。
 *
 * <p><b>解包与错误语义（与 shared-services/apiClient.ts 对齐）</b>：拦截器只解 axios.response →
 * ApiResponse（业务数据仍须 `.data`）；业务失败以信封 `success:false` 为准并被 reject，
 * 2xx 成功体的 `code` 被归一为 200（创建类 201 不再误判失败）。页面级仍建议对写动作做
 * `code !== 200` 防御，避免非标准信封绕过共享通道。
 * 幂等：写请求带 Idempotency-Key 请求头；同一次动作重试必须复用同一键，UUID 仅作无业务键兜底。
 *
 * <p>所有领域 service 已拆分为独立文件，本文件作为统一出口：
 * 1. import { claimApi } from '../services/claimApi' — 原页面零改动继续工作
 * 2. import { claimApi } from '../services/claim' — 也可直接引用案件主链路
 * 3. 新增页面建议直接从子模块按需 import：import { adjustApi } from '../services/adjustment'
 */

// ===== 类型 =====
export * from './types';

// ===== 子模块独立 API 对象（按需 import 入口） =====
export { newIdempotencyKey, stableClaimActionKey, stableAppendCaseKey } from './claim';
export { adjustApi } from './adjustment';
export { evidenceApi, attachmentApi } from './evidence';
export { settlementApi, stableSettlementKey } from './settlement';
export { quotaApi } from './quota';
export { appealApi } from './appeal';
export { inspectionApi, inspectionHandoffApi } from './inspection';
export { reviewTaskApi } from './reviewTask';

// ===== 向后兼容：组装统一 claimApi 命名空间 =====
//
// 原页面通过 claimApi.adjustApi.latest() / claimApi.evidenceApi.list() / claimApi.attachmentApi.downloadUrl() / claimApi.decide() 等
// 访问方式全部保留；方法签名与拆分前完全一致。

import { claimApi as _claimApi } from './claim';
import { adjustApi } from './adjustment';
import { evidenceApi, attachmentApi } from './evidence';
import { settlementApi } from './settlement';
import { quotaApi } from './quota';
import { appealApi } from './appeal';
import { inspectionApi } from './inspection';
import { reviewTaskApi } from './reviewTask';

export const claimApi = {
  ..._claimApi,
  adjustApi,
  evidenceApi,
  attachmentApi,
  settlementApi,
  quotaApi,
  appealApi,
  inspectionApi,
  reviewTaskApi,
};
