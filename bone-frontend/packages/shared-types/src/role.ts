/**
 * 角色领域类型（对齐 iam-app 真实模型）
 *
 * ID 一律 `string`：后端雪花 ID 以字符串下发（详设 §2.10），数值化会丢精度。
 */
import type { Permission } from './permission';

export interface Role {
  id: string;
  tenantId: string;
  name: string;
  code: string;
  type: 0 | 1; // 0=系统角色, 1=自定义角色
  description: string;
  parentRoleId?: string;
  createdBy?: string;
  updatedBy?: string;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  version?: number;
  permissions?: Permission[];
}

export interface CreateRoleRequest {
  name: string;
  description: string;
  code?: string;
  tenantId?: string;
  parentRoleId?: string;
}

export interface UpdateRoleRequest {
  name: string;
  description: string;
  code?: string;
  tenantId?: string;
  parentRoleId?: string;
}
