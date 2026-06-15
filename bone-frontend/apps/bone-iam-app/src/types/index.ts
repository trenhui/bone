// ==================== 账号相关类型（对齐 IAM 详细设计方案 v2.0） ====================

/**
 * 账号信息
 */
export interface Account {
  id: number;
  tenantId: number;
  username: string;
  email: string;
  phone?: string;
  realName?: string;
  avatarUrl?: string;
  status: 0 | 1 | 2; // 0=禁用, 1=启用, 2=锁定
  isAdmin: boolean;
  lastLoginAt?: string;
  lastLoginIp?: string;
  loginFailCount?: number;
  lockedAt?: string;
  passwordUpdatedAt?: string;
  createdBy?: number;
  updatedBy?: number;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  version?: number;
  roles?: Role[];
  /** 详情 API 返回的绑定角色 ID */
  roleIds?: number[];
}

/**
 * 创建账号请求
 */
export interface CreateAccountRequest {
  username: string;
  password: string;
  email: string;
  phone?: string;
  realName?: string;
  tenantId?: number;
  roleIds?: number[];
}

/**
 * 更新账号请求
 */
export interface UpdateAccountRequest {
  email: string;
  phone?: string;
  realName?: string;
  status: 0 | 1 | 2;
  roleIds?: number[];
}

/**
 * 重置密码请求
 */
export interface ResetPasswordRequest {
  password: string;
}

/**
 * 修改密码请求
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// ==================== 角色相关类型 ====================

/**
 * 角色信息
 */
export interface Role {
  id: number;
  tenantId: number;
  name: string;
  code: string;
  type: 0 | 1; // 0=系统角色, 1=自定义角色
  description: string;
  parentRoleId?: number;
  createdBy?: number;
  updatedBy?: number;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  version?: number;
  permissions?: Permission[];
}

/**
 * 创建角色请求
 */
export interface CreateRoleRequest {
  name: string;
  description: string;
  code?: string;
  tenantId?: number;
  parentRoleId?: number;
}

/**
 * 更新角色请求
 */
export interface UpdateRoleRequest {
  name: string;
  description: string;
  code?: string;
  tenantId?: number;
  parentRoleId?: number;
}

// ==================== 权限相关类型 ====================

/**
 * 权限信息
 */
export interface Permission {
  id: number;
  name: string;
  code: string;
  description?: string;
  resourceType: string;
  resourcePath: string;
  action: string;
  parentId?: number;
  sortOrder?: number;
  createdBy?: number;
  updatedBy?: number;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  children?: Permission[];
}

/**
 * 创建权限请求
 */
export interface CreatePermissionRequest {
  name: string;
  code: string;
  description?: string;
  resourceType: string;
  resourcePath?: string;
  action: string;
  parentId?: number;
  type?: string;
  sortOrder?: number;
}

/**
 * 更新权限请求
 */
export interface UpdatePermissionRequest {
  name: string;
  description?: string;
  resourceType?: string;
  resourcePath?: string;
  action?: string;
  parentId?: number;
  type?: string;
  sortOrder?: number;
}

// ==================== 审计日志相关类型 ====================

/**
 * 审计日志
 */
export interface AuditLog {
  id: number;
  tenantId: number;
  userId: number;
  operation: string;
  resourceType: string;
  resourceId?: string;
  ip?: string;
  userAgent?: string;
  parameters?: string;
  result: 'SUCCESS' | 'FAILED';
  duration?: number;
  createdAt: string;
}

/**
 * 审计设置
 */
export interface AuditSettings {
  retentionDays: number;
  autoArchiveEnabled: boolean;
  archiveAfterDays: number;
  storageType: 'DATABASE' | 'MINIO' | 'S3';
  wormEnabled: boolean;
}

// ==================== 登录相关类型 ====================

/**
 * 登录请求
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * 刷新令牌请求
 */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/**
 * 登录响应
 */
export interface LoginResponse {
  token: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  account: Account;
}

// ==================== SSO 相关类型 ====================

/**
 * SSO 配置
 */
export interface SsoConfig {
  enabled: boolean;
  providers: string[];
}

// ==================== 租户管理相关类型 ====================

/**
 * 租户信息
 */
export interface Tenant {
  id: number;
  name: string;
  code: string;
  level: number;
  status: number; // 0=禁用, 1=启用
  adminEmail: string;
  maxAccounts?: number;
  maxRoles?: number;
  createdBy?: number;
  updatedBy?: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * 创建租户请求
 */
export interface CreateTenantRequest {
  name: string;
  code: string;
  level?: number;
  adminEmail?: string;
}

/**
 * 更新租户请求
 */
export interface UpdateTenantRequest {
  name: string;
  level?: number;
  adminEmail?: string;
}

/**
 * 更新租户配额请求
 */
export interface UpdateTenantQuotaRequest {
  maxAccounts?: number;
  maxRoles?: number;
}

// ==================== 通用响应类型 ====================

/**
 * 分页结果（对齐 bone-core PageResult）
 */
export interface PageResult<T> {
  records: T[];
  total: number;
  page?: number;
  size?: number;
  /** @deprecated 旧前端字段，兼容读取 */
  data?: T[];
}

/**
 * API 响应
 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}
