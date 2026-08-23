/**
 * 集成应用类型统一收敛到 @bone/shared-types
 * 本地领域类型已上提至 packages/shared-types/src/integration.ts
 */
export * from '@bone/shared-types';
export type { ApiResponse, PageResult } from '@bone/shared-types';

// 分页查询参数（集成模块扩展：含 keyword / status 过滤）
export interface PageQuery {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  status?: string;
}
