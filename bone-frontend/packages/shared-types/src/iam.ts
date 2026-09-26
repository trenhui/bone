/**
 * IAM 领域类型（对齐 iam-app 真实模型）
 *
 * **ID 与标量契约（详设 §2.10）**：后端 `Long`（雪花 ID）在 JSON 里序列化为**字符串**，
 * 因此所有 ID 字段一律声明为 `string`。前端禁止对 ID 做 `Number()` / `parseInt` / `==` 比较 ——
 * 雪花 ID 超过 `Number.MAX_SAFE_INTEGER`，数值化会静默丢精度（改错对象、删错行且无报错）。
 */
import type { Role } from './role';

export interface Account {
  id: string;
  tenantId: string;
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
  createdBy?: string;
  updatedBy?: string;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  version?: number;
  /** 归属部门 ID（主部门；跨聚合仅存 ID，不级联部门聚合） */
  deptId?: string | null;
  /** 归属部门名称（列表/详情接口回填，仅展示用） */
  deptName?: string | null;
  roles?: Role[];
  /** 详情 API 返回的绑定角色 ID */
  roleIds?: string[];
}

export interface CreateAccountRequest {
  username: string;
  password: string;
  email: string;
  phone?: string;
  realName?: string;
  tenantId?: string;
  /** 归属部门 ID（必填，账号必须归属一个主部门） */
  deptId?: string | null;
  roleIds?: string[];
}

export interface UpdateAccountRequest {
  email: string;
  phone?: string;
  realName?: string;
  status: 0 | 1 | 2;
  /** 归属部门 ID；null=不变更（沿用原值），必填项不可清空（0 撤销已禁用） */
  deptId?: string | null;
  roleIds?: string[];
}

export interface ResetPasswordRequest {
  password: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export interface AuditLog {
  id: string;
  tenantId: string;
  userId: string;
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
  id: string;
  name: string;
  code: string;
  level: number;
  status: number; // 0=禁用, 1=启用
  adminEmail: string;
  maxAccounts?: number;
  maxRoles?: number;
  createdBy?: string;
  updatedBy?: string;
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
