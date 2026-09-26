/**
 * IAM 领域类型（对齐 iam-app 真实模型）
 */
import type { Role } from './role';

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
  /** 归属部门 ID（主部门；跨聚合仅存 ID，不级联部门聚合） */
  deptId?: number | null;
  /** 归属部门名称（列表/详情接口回填，仅展示用） */
  deptName?: string | null;
  roles?: Role[];
  /** 详情 API 返回的绑定角色 ID */
  roleIds?: number[];
}

export interface CreateAccountRequest {
  username: string;
  password: string;
  email: string;
  phone?: string;
  realName?: string;
  tenantId?: number;
  /** 归属部门 ID（必填，账号必须归属一个主部门） */
  deptId?: number | null;
  roleIds?: number[];
}

export interface UpdateAccountRequest {
  email: string;
  phone?: string;
  realName?: string;
  status: 0 | 1 | 2;
  /** 归属部门 ID；null=不变更（沿用原值），必填项不可清空（0 撤销已禁用） */
  deptId?: number | null;
  roleIds?: number[];
}

export interface ResetPasswordRequest {
  password: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

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

export interface AuditSettings {
  retentionDays: number;
  autoArchiveEnabled: boolean;
  archiveAfterDays: number;
  storageType: 'DATABASE' | 'MINIO' | 'S3';
  wormEnabled: boolean;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  account: Account;
}

export interface SsoConfig {
  enabled: boolean;
  providers: string[];
}

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

export interface CreateTenantRequest {
  name: string;
  code: string;
  level?: number;
  adminEmail?: string;
}

export interface UpdateTenantRequest {
  name: string;
  level?: number;
  adminEmail?: string;
}

export interface UpdateTenantQuotaRequest {
  maxAccounts?: number;
  maxRoles?: number;
}
