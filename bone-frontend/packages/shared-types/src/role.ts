/**
 * 角色领域类型（对齐 iam-app 真实模型）
 */
import type { Permission } from './permission';

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

export interface CreateRoleRequest {
  name: string;
  description: string;
  code?: string;
  tenantId?: number;
  parentRoleId?: number;
}

export interface UpdateRoleRequest {
  name: string;
  description: string;
  code?: string;
  tenantId?: number;
  parentRoleId?: number;
}
