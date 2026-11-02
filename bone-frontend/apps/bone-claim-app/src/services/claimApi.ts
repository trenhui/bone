/**
 * synapClaim 理赔 API —— barrel export（向后兼容入口）。
 *
 * 复用平台 createApiClient：统一 Bearer 注入、把 axios 响应解包为 ApiResponse 信封
 * （返回体仍是 { code, message, data }，业务数据须经 .data 取用）、401 过期广播。
 *
 * <p><b>注意（解包语义，勿再误读）</b>：拦截器只解 axios.response → ApiResponse，**不会**再解一层
 * ApiResponse → data，也不按业务码 reject。因此「HTTP 200 + 业务 code !== 200」会被正常
 * resolve，调用方若不检查 code 就会把错误响应当成功数据渲染。两条统一写法二选一：
 * ① 用 apiCall(...) 取 .data（不做业务码校验，适合确信只成功分支的场景）；
 * ② 显式 if (res.code !== 200) throw ...。同一模块内不要两种混用。
 * 幂等：写请求带 Idempotency-Key 请求头（UUID v4），同键同参返回首次结果。
 *
 * <p>所有领域 service 已拆分为独立文件，本文件作为统一出口：
 * 1. import { claimApi } from '../services/claimApi' — 原页面零改动继续工作
 * 2. import { claimApi } from '../services/claim' — 也可直接引用案件主链路
 * 3. 新增页面建议直接从子模块按需 import：import { adjustApi } from '../services/adjustment'
 */

// ===== 类型 =====
export * from './types';

// ===== 子模块独立 API 对象（按需 import 入口） =====
export { newIdempotencyKey, REVIEW_THRESHOLD } from './claim';
export { adjustApi } from './adjustment';
export { evidenceApi, attachmentApi } from './evidence';
export { settlementApi } from './settlement';
export { quotaApi } from './quota';
export { appealApi } from './appeal';
export { inspectionApi } from './inspection';
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
