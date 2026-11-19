import { createApiClient } from '@bone/shared-services';
import type { ApiEnvelope } from '../types/purchase';
import type { AiDisclosureDTO } from '../types/contractReview';

/** 与后端 ContractReviewController 的 @RequestMapping 严格一致。 */
export const CONTRACT_REVIEWS_BASE = '/api/v1/procurement/contract-reviews';

const apiClient = createApiClient('');

export const contractReviewApi = {
  getDisclosure: (reviewId: string): Promise<ApiEnvelope<AiDisclosureDTO>> =>
    apiClient.get(`${CONTRACT_REVIEWS_BASE}/${encodeURIComponent(reviewId)}/disclosure`),
};
