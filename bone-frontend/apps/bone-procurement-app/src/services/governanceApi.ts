import { createApiClient } from '@bone/shared-services';
import type { ApiEnvelope } from '../types/purchase';
import type { GovernancePostureDTO } from '../types/governancePostureView';

/** 与后端 AiGovernanceController 的 @RequestMapping 严格一致。 */
export const AI_GOVERNANCE_BASE = '/api/v1/procurement/ai-governance';

const apiClient = createApiClient('');

export const governanceApi = {
  /** 治理态势聚合只读查询：无参数，不接受客户端阈值注入。 */
  getPosture: (): Promise<ApiEnvelope<GovernancePostureDTO>> =>
    apiClient.get(`${AI_GOVERNANCE_BASE}/posture`),
};
