/**
 * 统一 API 响应与分页类型（公共层）
 * 对齐后端 com.bone.core.api.ApiResponse / PageResult
 */

/**
 * 统一 API 响应包装类型
 */
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
}

/**
 * 统一分页结果类型（权威形态②）
 * 对齐后端 com.bone.core.api.PageResult（list / total / pageNum / pageSize）
 */
export interface PageResult<T = unknown> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/**
 * @deprecated IAM 兼容别名。iam 旧读取点使用 records / data，请逐步迁移到 PageResult（list）。
 * 保留 records / data 字段仅用于过渡期读取兼容。
 */
export interface PageResultIamCompat<T = unknown> {
  total: number;
  pageNum?: number;
  pageSize?: number;
  /** @deprecated 旧前端字段，兼容读取 */
  records?: T[];
  /** @deprecated 旧前端字段，兼容读取 */
  data?: T;
  /** 权威字段，存在时优先使用 */
  list?: T[];
}

/**
 * 通用分页查询参数
 */
export interface PageQuery {
  pageNum: number;
  pageSize: number;
}
