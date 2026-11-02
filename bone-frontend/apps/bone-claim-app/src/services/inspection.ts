/**
 * synapClaim · 后督抽检（A9 InspectionRecord 聚合）。
 *
 * 管理后台命名空间 /api/v1/review。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type {
  ConcludeInspectionReq,
  InspectionAction,
  InspectionDiffType,
  InspectionDimension,
  InspectionResult,
  InspectionView,
} from './types';

const reviewClient = createApiClient('/api/v1/review');

export const inspectionApi = {
  /** #13 发起抽检（MANUAL）：案件须已终态；同案重复发起幂等返回原单。 */
  open(
    claimId: string,
    dimension: InspectionDimension = 'AMOUNT',
  ): Promise<ApiResponse<InspectionView>> {
    return reviewClient.post<{ claimId: string; dimension: InspectionDimension }, ApiResponse<InspectionView>>(
      '/inspections',
      { claimId, dimension },
    );
  },
  /** 领取抽检任务（PENDING → IN_PROGRESS）。 */
  claim(inspectionNo: string): Promise<ApiResponse<InspectionView>> {
    return reviewClient.post<never, ApiResponse<InspectionView>>(
      `/inspections/${encodeURIComponent(inspectionNo)}/claim`,
    );
  },
  /** #14 抽检结论（终态）。 */
  conclude(
    inspectionNo: string,
    req: ConcludeInspectionReq,
  ): Promise<ApiResponse<InspectionView>> {
    return reviewClient.post<ConcludeInspectionReq, ApiResponse<InspectionView>>(
      `/inspections/${encodeURIComponent(inspectionNo)}/conclusion`,
      req,
    );
  },
  /** 按单号查询。 */
  detail(inspectionNo: string): Promise<ApiResponse<InspectionView>> {
    return reviewClient.get<never, ApiResponse<InspectionView>>(
      `/inspections/${encodeURIComponent(inspectionNo)}`,
    );
  },
  /** 按案件查最新抽检单（无则 data 为 null）。 */
  latest(claimId: string): Promise<ApiResponse<InspectionView | null>> {
    return reviewClient.get<never, ApiResponse<InspectionView | null>>('/inspections', {
      params: { claimId },
    });
  },
};

export type { InspectionDimension, InspectionResult, InspectionDiffType, InspectionAction };
