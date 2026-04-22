// 用户相关类型
export interface User {
  id: string;
  username: string;
  email: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  createdAt: string;
  updatedAt: string;
  roles: Role[];
}

export interface CreateUserRequest {
  username: string;
  email: string;
  name: string;
  password: string;
  roleIds: string[];
}

export interface UpdateUserRequest {
  email: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  roleIds: string[];
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// 角色相关类型
export interface Role {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  permissions: Permission[];
}

export interface CreateRoleRequest {
  name: string;
  description: string;
  permissionIds: string[];
}

export interface UpdateRoleRequest {
  name: string;
  description: string;
  permissionIds: string[];
}

// 权限相关类型
export interface Permission {
  id: string;
  name: string;
  code: string;
  description: string;
  resourceType: string;
  action: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePermissionRequest {
  name: string;
  code: string;
  description: string;
  resourceType: string;
  action: string;
}

export interface UpdatePermissionRequest {
  name: string;
  description: string;
  resourceType: string;
  action: string;
}

// 审计日志相关类型
export interface AuditLog {
  id: string;
  userId: string;
  username: string;
  action: string;
  resourceType: string;
  resourceId: string;
  details: string;
  ipAddress: string;
  userAgent: string;
  createdAt: string;
}

// 登录相关类型
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

// 通用响应类型
export interface PageResult<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}