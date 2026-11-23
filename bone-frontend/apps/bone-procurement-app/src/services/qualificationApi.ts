import { createApiClient } from '@bone/shared-services';
import type { ApiEnvelope } from '../types/purchase';

export const QUALIFICATIONS_BASE = '/api/v1/procurement/qualifications';

const apiClient = createApiClient('');

/** 证照终决读回 DTO（只取展示所需字段）。 */
export interface QualificationDTO {
  id: string;
  tenantId: string;
  supplierId: string;
  certType: string;
  verifyStatus: string;
  reviewedBy: string | null;
  ocrResultJson: string | null;
}

export const qualificationApi = {
  findOne: (id: string, tenantId: string): Promise<ApiEnvelope<QualificationDTO>> =>
    apiClient.get(`${QUALIFICATIONS_BASE}/${encodeURIComponent(id)}`, { params: { tenantId } }),
  /** 租户/复核人由后端从登录态权威解析；请求体仅传 accurate，自报字段不参与落库。 */
  reviewOcr: (id: string, payload: { accurate: boolean }): Promise<ApiEnvelope<null>> =>
    apiClient.post(`${QUALIFICATIONS_BASE}/${encodeURIComponent(id)}/ocr-review`, payload),
};
