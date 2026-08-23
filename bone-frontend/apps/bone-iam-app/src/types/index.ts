/**
 * IAM 应用类型统一收敛到 @bone/shared-types
 * 本地领域类型已上提至 packages/shared-types/src/iam.ts
 */
export * from '@bone/shared-types';
export type { ApiResponse, PageResultIamCompat as PageResult } from '@bone/shared-types';
