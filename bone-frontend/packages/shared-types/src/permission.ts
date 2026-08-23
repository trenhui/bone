/**
 * 权限领域类型（对齐 iam-app 真实模型）
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
