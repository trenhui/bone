/* eslint-disable @typescript-eslint/explicit-module-boundary-types --
   Return types are conveyed via Axios' generic `<never, ApiResponse<T>>` annotation. */
import { createApiClient } from '@bone/shared-services';
import type { ApiResponse, PageResult } from '../types';

/**
 * 应用管理 API（IAM 限界上下文）。
 *
 * 领域边界：应用(App)聚合根归属 IAM —— 后端 `AppController`（`/api/v1/apps`，
 * 经网关路由到 bone-iam，写操作需 `iam:apps:write`）。元数据建模侧只消费
 * App/Module 作为归属维度（见 bone-metadata-app「建模工作台」）。
 */

export interface BoneApplication {
  id: string;
  name: string;
  code: string;
  description: string;
  icon: string;
  status: number;
  moduleCount: number;
  entityCount: number;
  /** 当前用户在应用中的角色：admin / developer / viewer */
  myRole?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAppReq {
  name: string;
  code: string;
  description?: string;
  icon?: string;
}

export interface UpdateAppReq {
  name?: string;
  description?: string;
  icon?: string;
}

const api = createApiClient('/api/v1');

export const appApi = {
  /** 获取当前用户有权限的应用列表 */
  listMine: (params?: { pageNum?: number; pageSize?: number }) =>
    api.get<never, ApiResponse<PageResult<BoneApplication>>>('/apps/mine', { params }),

  /** 应用详情 */
  detail: (id: string) =>
    api.get<never, ApiResponse<BoneApplication>>(`/apps/${id}`),

  /** 创建应用 */
  create: (data: CreateAppReq) =>
    api.post<never, ApiResponse<string>>('/apps', data),

  /** 更新应用 */
  update: (id: string, data: UpdateAppReq) =>
    api.put<never, ApiResponse<void>>(`/apps/${id}`, data),

  /** 删除应用 */
  delete: (id: string) =>
    api.delete<never, ApiResponse<void>>(`/apps/${id}`),
};
