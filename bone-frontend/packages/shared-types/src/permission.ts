/**
 * 权限领域类型（对齐 iam-app 真实模型）
 *
 * ID 一律 `string`：后端雪花 ID 以字符串下发（详设 §2.10），数值化会丢精度。
 */

export interface Permission {
  id: string;
  name: string;
  code: string;
  description?: string;
  resourceType: string;
  resourcePath: string;
  action: string;
  parentId?: string;
  sortOrder?: number;
  createdBy?: string;
  updatedBy?: string;
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
  parentId?: string;
  type?: string;
  sortOrder?: number;
}

export interface UpdatePermissionRequest {
  name: string;
  description?: string;
  resourceType?: string;
  resourcePath?: string;
  action?: string;
  parentId?: string;
  type?: string;
  sortOrder?: number;
}
