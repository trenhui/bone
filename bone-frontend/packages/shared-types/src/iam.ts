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
  /**
   * 直属主管账户 ID（审批流 LINE_MANAGER 的数据源）；无上级时为 null。
   *
   * 注意：这是「人向谁汇报」，与 `deptId`（人属于哪个部门）是两条不同的线 —
   * 同部门内平级同事互相不审批，跨部门虚线汇报也真实存在，二者不可互相推导。
   */
  managerId?: string | null;
  /** 直属主管姓名（后端回填，仅展示用） */
  managerName?: string | null;
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
  /** 直属主管账户 ID（可选，创始人/CEO 无上级） */
  managerId?: string | null;
  roleIds?: string[];
}

export interface UpdateAccountRequest {
  email: string;
  phone?: string;
  realName?: string;
  status: 0 | 1 | 2;
  /** 归属部门 ID；null=不变更（沿用原值），必填项不可清空（0 撤销已禁用） */
  deptId?: string | null;
  /**
   * 直属主管：`null`/`undefined` = 不变更，`'0'` = 清空（无主管），其他 = 设为该账户。
   *
   * 清空用 '0' 哨兵而不是 null：本接口是全量覆盖语义，若 null 表示清空，
   * 任何「只改手机号」的提交都会把主管静默清掉。
   */
  managerId?: string | null;
  roleIds?: string[];
}

/**
 * 重置密码入参。
 *
 * 字段名必须与后端 `ResetPasswordReq#newPassword` 一致（`@NotBlank` + `@Size(min = 8)`）。
 * 历史 bug：这里曾写作 `password`，与后端字段名不符 ⇒ `newPassword` 收到 null ⇒
 * 后端抛 `IAM_WEAK_PASSWORD`（"密码长度至少 8 位"），UI 上「重置密码」无论输入什么都失败。
 * 与相邻的 {@link ChangePasswordRequest} 保持同一命名口径。
 */
export interface ResetPasswordRequest {
  newPassword: string;
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
