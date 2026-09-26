/* eslint-disable @typescript-eslint/explicit-module-boundary-types --
   Return types are conveyed via Axios' generic `<never, ApiResponse<T>>` annotation
   that flows through the response interceptor; adding explicit return annotations
   would duplicate the generic and reduce readability without adding type safety. */
import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type {
  ApiResponse, PageResult,
  Account, Role, Permission, AuditLog, Tenant,
  LoginRequest, LoginResponse,
  CreateAccountRequest, UpdateAccountRequest, ResetPasswordRequest,
  CreateRoleRequest, UpdateRoleRequest,
  CreatePermissionRequest, UpdatePermissionRequest,
  AuditSettings, RefreshTokenRequest,
  CreateTenantRequest, UpdateTenantRequest, UpdateTenantQuotaRequest,
} from '../types';

// 重新导出，保持向后兼容（其他文件可能引用了 setQiankunToken）
export { setQiankunToken };

const api = createApiClient('/api/v1/iam');

// ==================== 认证相关 ====================

/**
 * 用户登录
 */
export const login = (data: LoginRequest) =>
  api.post<never, ApiResponse<LoginResponse>>('/login', data);

/**
 * 用户登出
 */
export const logout = () =>
  api.post<never, ApiResponse<void>>('/logout');

/**
 * 刷新令牌
 */
export const refreshToken = (data: RefreshTokenRequest) =>
  api.post<never, ApiResponse<LoginResponse>>('/refresh', data);

/**
 * 获取SSO配置
 */
export const getSsoConfig = () =>
  api.get<never, ApiResponse<{ enabled: boolean; providers: string[] }>>('/sso/config');

// ==================== 我的（个人信息 / 改密） ====================

/** 当前登录账号的安全脱敏视图（后端 `MeResp`）。 */
export interface MyProfile {
  id: string;
  username: string;
  email: string;
  phone?: string;
  realName?: string;
  avatarUrl?: string;
  status: number;
  isAdmin: boolean;
  tenantId: string;
  scopes: string[];
  lastLoginAt?: string;
  passwordUpdatedAt?: string;
}

export const getMyProfile = () => api.get<never, ApiResponse<MyProfile>>('/me');

export const updateMyProfile = (data: {
  realName?: string;
  phone?: string;
  avatarUrl?: string;
}) => api.put<never, ApiResponse<void>>('/me', data);

export const changeMyPassword = (data: { oldPassword: string; newPassword: string }) =>
  api.post<never, ApiResponse<void>>('/me/change-password', data);

// ==================== 账号管理 ====================

/**
 * 分页查询账号列表
 * @param deptId 按归属部门过滤（含子部门，后端递归子树）；不传查全部
 */
export const getAccounts = (
  page = 1,
  pageSize = 10,
  keyword?: string,
  status?: number,
  deptId?: string | null,
) =>
  api.get<never, ApiResponse<PageResult<Account>>>('/accounts', {
    params: { page, size: pageSize, keyword, status, ...(deptId != null ? { deptId } : {}) },
  });

/**
 * 获取账号详情
 */
export const getAccount = (id: string) =>
  api.get<never, ApiResponse<Account>>(`/accounts/${id}`);

/**
 * 创建账号
 */
export const createAccount = (data: CreateAccountRequest) =>
  api.post<never, ApiResponse<string>>('/accounts', data);

/**
 * 更新账号
 */
export const updateAccount = (id: string, data: UpdateAccountRequest) =>
  api.put<never, ApiResponse<void>>(`/accounts/${id}`, data);

/**
 * 删除账号
 */
export const deleteAccount = (id: string) =>
  api.delete<never, ApiResponse<void>>(`/accounts/${id}`);

/**
 * 启用账号
 */
export const enableAccount = (id: string) =>
  api.post<never, ApiResponse<void>>(`/accounts/${id}/enable`);

/**
 * 禁用账号
 */
export const disableAccount = (id: string) =>
  api.post<never, ApiResponse<void>>(`/accounts/${id}/disable`);

/**
 * 重置密码
 */
export const resetAccountPassword = (id: string, data: ResetPasswordRequest) =>
  api.post<never, ApiResponse<void>>(`/accounts/${id}/reset-password`, data);

/**
 * 批量导入用户
 */
export const importAccounts = (data: CreateAccountRequest[]) =>
  api.post<never, ApiResponse<string>>('/accounts/import', data);

/**
 * 导出用户
 */
export const exportAccounts = (params?: { keyword?: string; status?: number }) =>
  api.get<never, ApiResponse<Account[]>>('/accounts/export', { params });

// ==================== 角色管理 ====================

/**
 * 分页查询角色列表
 */
export const getRoles = (page = 1, pageSize = 10, keyword?: string) =>
  api.get<never, ApiResponse<PageResult<Role>>>('/roles', { params: { page, size: pageSize, keyword } });

/**
 * 获取角色详情
 */
export const getRole = (id: string) =>
  api.get<never, ApiResponse<Role>>(`/roles/${id}`);

/**
 * 创建角色
 */
export const createRole = (data: CreateRoleRequest) =>
  api.post<never, ApiResponse<string>>('/roles', { ...data, tenantId: data.tenantId ?? 0 });

/**
 * 更新角色
 */
export const updateRole = (id: string, data: UpdateRoleRequest) =>
  api.put<never, ApiResponse<void>>(`/roles/${id}`, { ...data, tenantId: data.tenantId ?? 0 });

/**
 * 删除角色
 */
export const deleteRole = (id: string) =>
  api.delete<never, ApiResponse<void>>(`/roles/${id}`);

/**
 * 为角色分配权限
 */
export const assignPermissions = (roleId: string, permissionIds: string[]) =>
  api.post<never, ApiResponse<void>>(`/roles/${roleId}/permissions`, { permissionIds });

/**
 * 获取角色的权限列表
 */
export const getRolePermissions = (roleId: string) =>
  api.get<never, ApiResponse<Permission[]>>(`/roles/${roleId}/permissions`);

// ==================== 权限管理 ====================

/**
 * 分页查询权限列表
 */
export const getPermissions = (page = 1, pageSize = 10, keyword?: string) =>
  api.get<never, ApiResponse<PageResult<Permission>>>('/permissions', { params: { page, size: pageSize, keyword } });

/**
 * 获取权限树
 */
export const getPermissionTree = () =>
  api.get<never, ApiResponse<Permission[]>>('/permissions/tree');

/**
 * 获取权限详情
 */
export const getPermission = (id: string) =>
  api.get<never, ApiResponse<Permission>>(`/permissions/${id}`);

/**
 * 创建权限
 */
export const createPermission = (data: CreatePermissionRequest) =>
  api.post<never, ApiResponse<string>>('/permissions', data);

/**
 * 更新权限
 */
export const updatePermission = (id: string, data: UpdatePermissionRequest) =>
  api.put<never, ApiResponse<void>>(`/permissions/${id}`, data);

/**
 * 删除权限
 */
export const deletePermission = (id: string) =>
  api.delete<never, ApiResponse<void>>(`/permissions/${id}`);

// ==================== 审计日志 ====================

/**
 * 分页查询审计日志
 */
export const getAuditLogs = (params: {
  page?: number;
  pageSize?: number;
  userId?: string;
  operation?: string;
  resourceType?: string;
  result?: string;
  startTime?: string;
  endTime?: string;
}) => {
  const { page, pageSize, startTime, endTime, ...rest } = params;
  return api.get<never, ApiResponse<PageResult<AuditLog>>>('/audit/logs', {
    params: {
      ...rest,
      page,
      size: pageSize,
      startedAt: startTime,
      endedAt: endTime,
    },
  });
};

/**
 * 导出审计日志（**字节流契约**，详设 §5.8）
 *
 * 后端 `@GetMapping("/logs/export")` 返回 `ResponseEntity<byte[]>` + `Content-Disposition: attachment`，
 * **不包 `ApiResponse` 信封**。因此这里必须以 `responseType: 'blob'` 请求，并自行从响应头取文件名；
 * 用 `api.get<never, ApiResponse<AuditLog[]>>` 解包会把二进制流当 JSON 解析，导出直接失败。
 *
 * 参数名与后端 `AuditLogListQuery` 对齐（`startedAt / endedAt / operation`），
 * 与列表查询共用同一套键名，避免「列表能查出来、导出查不出来」的口径分裂。
 *
 * @returns 原始 `Blob` 响应（含 `Content-Disposition` / `X-Export-Truncated` 头）
 */
export const exportAuditLogs = (params?: {
  userId?: string;
  operation?: string;
  resourceType?: string;
  result?: string;
  startedAt?: string;
  endedAt?: string;
}) =>
  api.get<never, Blob>('/audit/logs/export', {
    params,
    responseType: 'blob',
  });

/**
 * 获取审计设置
 */
export const getAuditSettings = () =>
  api.get<never, ApiResponse<AuditSettings>>('/audit/settings');

/**
 * 更新审计设置
 */
export const updateAuditSettings = (data: Partial<AuditSettings>) =>
  api.put<never, ApiResponse<void>>('/audit/settings', data);

// ==================== 租户管理 ====================

/**
 * 分页查询租户列表
 */
export const getTenants = (page = 1, pageSize = 10, keyword?: string) =>
  api.get<never, ApiResponse<PageResult<Tenant>>>('/tenants', {
    params: { page, size: pageSize, keyword },
  });

/**
 * 获取租户详情
 */
export const getTenant = (id: string) =>
  api.get<never, ApiResponse<Tenant>>(`/tenants/${id}`);

/**
 * 创建租户
 */
export const createTenant = (data: CreateTenantRequest) =>
  api.post<never, ApiResponse<string>>('/tenants', data);

/**
 * 更新租户
 */
export const updateTenant = (id: string, data: UpdateTenantRequest) =>
  api.put<never, ApiResponse<void>>(`/tenants/${id}`, data);

/**
 * 删除租户
 */
export const deleteTenant = (id: string) =>
  api.delete<never, ApiResponse<void>>(`/tenants/${id}`);

/**
 * 启用租户
 */
export const enableTenant = (id: string) =>
  api.post<never, ApiResponse<void>>(`/tenants/${id}/enable`);

/**
 * 禁用租户
 */
export const disableTenant = (id: string) =>
  api.post<never, ApiResponse<void>>(`/tenants/${id}/disable`);

/**
 * 更新租户配额
 */
export const updateTenantQuota = (id: string, data: UpdateTenantQuotaRequest) =>
  api.put<never, ApiResponse<void>>(`/tenants/${id}/quota`, data);

export default api;

// ==================== 组织机构管理 ====================

export interface DeptNode {
  /** 雪花 ID 由后端序列化为字符串，前端以字符串承载避免精度丢失 */
  id: string;
  name: string;
  parentId: string | null;
  orderNo?: number;
  status?: number;
  children?: DeptNode[];
}

export interface DeptReq {
  name: string;
  parentId?: string | null;
  orderNo?: number;
  status?: number;
}

/** 获取组织机构树 */
export const getDeptTree = (keyword?: string) =>
  api.get<never, ApiResponse<DeptNode[]>>('/depts/tree', { params: { keyword } });

/** 创建组织机构节点 */
export const createDept = (data: DeptReq) =>
  api.post<never, ApiResponse<string>>('/depts', data);

/** 更新组织机构节点 */
export const updateDept = (id: string, data: DeptReq) =>
  api.put<never, ApiResponse<void>>(`/depts/${id}`, data);

/** 删除组织机构节点 */
export const deleteDept = (id: string) =>
  api.delete<never, ApiResponse<void>>(`/depts/${id}`);

// ==================== 菜单管理 ====================

export interface MenuNodeItem {
  /** 雪花 ID 由后端序列化为字符串，前端以字符串承载避免精度丢失 */
  id: string;
  name: string;
  parentId: string | null;
  path?: string;
  icon?: string;
  orderNo?: number;
  permission?: string;
  type?: number;
  children?: MenuNodeItem[];
}

export interface MenuReq {
  name: string;
  parentId?: string | null;
  path?: string;
  icon?: string;
  orderNo?: number;
  permission?: string;
  type?: number;
}

/** 获取菜单树 */
export const getMenuTree = (keyword?: string) =>
  api.get<never, ApiResponse<MenuNodeItem[]>>('/menus/tree', { params: { keyword } });

/** 创建菜单节点 */
export const createMenu = (data: MenuReq) =>
  api.post<never, ApiResponse<string>>('/menus', data);

/** 更新菜单节点 */
export const updateMenu = (id: string, data: MenuReq) =>
  api.put<never, ApiResponse<void>>(`/menus/${id}`, data);

/** 删除菜单节点 */
export const deleteMenu = (id: string) =>
  api.delete<never, ApiResponse<void>>(`/menus/${id}`);
