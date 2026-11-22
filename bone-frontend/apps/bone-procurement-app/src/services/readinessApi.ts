import { createApiClient } from '@bone/shared-services';
import type { ApiEnvelope } from '../types/purchase';
import type { AiReadinessDTO } from '../types/readinessView';

/** 与后端 AiReadinessController 的 @RequestMapping 严格一致。 */
export const AI_READINESS_BASE = '/api/v1/procurement';

const apiClient = createApiClient('');

export const readinessApi = {
  getReadiness: (): Promise<ApiEnvelope<AiReadinessDTO>> =>
    apiClient.get(`${AI_READINESS_BASE}/ai-readiness`),
};
