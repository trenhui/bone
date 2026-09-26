/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient } from '@bone/shared-services';
import type { ApiResponse, PageResult } from '../types';

/**
 * 应用与模块管理 API（元数据建模消费侧）。
 *
 * 领域边界：应用(App)与模块(Module)聚合根归属 IAM 上下文，后端由 IAM 独占提供
 * （`/api/v1/apps`、`/api/v1/apps/{id}/modules`，经网关路由到 bone-iam）。
 * 应用的管理界面（新建/编辑/删除）在 bone-iam-app「应用管理」页（IAM 真源
 * `AppController`，写操作需 `iam:apps:write`）；本文件仅供元数据建模链路
 * （建模工作台 → 模块 → 实体）消费 App/Module 作为归属维度，不存在
 * "未实现/演示数据兜底"。
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

export interface BoneModule {
  id: string;
  appId: string;
  name: string;
  code: string;
  description: string;
  status: number;
  entityCount: number;
  fieldCount: number;
  sortOrder: number;
  createdAt: string;
}

export interface CreateModuleReq {
  name: string;
  code: string;
  description?: string;
}

export interface UpdateModuleReq {
  name?: string;
  description?: string;
  status?: number;
}

export interface AppPermission {
  userId: number;
  username: string;
  role: 'admin' | 'developer' | 'viewer';
  createdAt: string;
}

const api = createApiClient('/api/v1');

export const appApi = {
  /** 获取当前用户有权限的应用列表（IAM /apps/mine 按当前登录用户过滤；admin 回退全部） */
  listMine: (params?: { page?: number; size?: number }) =>
    api.get<never, ApiResponse<PageResult<BoneApplication>>>('/apps/mine', {
      params: { page: params?.page ?? 1, size: params?.size ?? 100 },
    }),

  /** 分页查询应用（管理员用） */
  list: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    api.get<never, ApiResponse<PageResult<BoneApplication>>>('/apps', { params }),

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

  /** 应用权限列表 */
  listPermissions: (appId: string) =>
    api.get<never, ApiResponse<AppPermission[]>>(`/apps/${appId}/permissions`),

  /** 授予用户权限 */
  grantPermission: (appId: string, userId: number, role: string) =>
    api.post<never, ApiResponse<void>>(`/apps/${appId}/permissions`, { userId, role }),

  /** 移除用户权限 */
  revokePermission: (appId: string, userId: number) =>
    api.delete<never, ApiResponse<void>>(`/apps/${appId}/permissions/${userId}`),
};

export const moduleApi = {
  /** 查询模块列表 */
  listByApp: (appId: string, params?: { pageNum?: number; pageSize?: number }) =>
    api.get<never, ApiResponse<PageResult<BoneModule>>>(`/apps/${appId}/modules`, { params }),

  /** 模块详情 */
  detail: (appId: string, moduleId: string) =>
    api.get<never, ApiResponse<BoneModule>>(`/apps/${appId}/modules/${moduleId}`),

  /** 创建模块 */
  create: (appId: string, data: CreateModuleReq) =>
    api.post<never, ApiResponse<string>>(`/apps/${appId}/modules`, data),

  /** 更新模块 */
  update: (appId: string, moduleId: string, data: UpdateModuleReq) =>
    api.put<never, ApiResponse<void>>(`/apps/${appId}/modules/${moduleId}`, data),

  /** 删除模块 */
  delete: (appId: string, moduleId: string) =>
    api.delete<never, ApiResponse<void>>(`/apps/${appId}/modules/${moduleId}`),
};

/** 判断 API 错误是否为「未实现」 */
export function isApiNotImplemented(error: unknown): boolean {
  const err = error as { response?: { status?: number } } | undefined;
  return err?.response?.status === 501 || err?.response?.status === 404;
}
