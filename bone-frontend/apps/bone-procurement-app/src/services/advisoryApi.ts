import { createApiClient } from '@bone/shared-services';
import type { ApiEnvelope } from '../types/purchase';
import type { AdvisoryAdviceDTO } from '../types/advisoryView';

/** 与后端 AdvisoryController 的 @RequestMapping 严格一致。 */
export const AI_ADVISORY_BASE = '/api/v1/procurement/ai-advisory';

const apiClient = createApiClient('');

export const advisoryApi = {
  listCapabilities: (): Promise<ApiEnvelope<string[]>> =>
    apiClient.get(`${AI_ADVISORY_BASE}/capabilities`),
  getAdvice: (code: string): Promise<ApiEnvelope<AdvisoryAdviceDTO>> =>
    apiClient.get(`${AI_ADVISORY_BASE}/capabilities/${encodeURIComponent(code)}/advice`),
};
