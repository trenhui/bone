/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient } from '@bone/shared-services';
import type { ApiResponse, PageResult } from '../types';

/**
 * 应用与模块管理 API（契约按 doc/design/modules/10. 应用与模块管理详细设计方案.md）
 *
 * 后端 API 当前状态：⏳ Target（未实现）
 * - 调用时会返回 501 或 404
 * - 前端捕获后展示"API 尚未实现"提示 + "使用演示数据"按钮
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
  /** 获取当前用户有权限的应用列表 */
  listMine: (params?: { pageNum?: number; pageSize?: number }) =>
    api.get<never, ApiResponse<PageResult<BoneApplication>>>('/apps/mine', { params }),

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
