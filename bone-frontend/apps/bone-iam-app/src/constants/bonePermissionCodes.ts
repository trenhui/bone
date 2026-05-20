/**
 * 与 @bone/shared-types 及 IAM 详设 §3.7 对齐；微应用本地副本避免未声明 workspace 依赖。
 * 变更时请同步 packages/shared-types/src/bonePermissionCodes.ts
 */
export {
  BonePermissionCodes,
  BonePermissionDomain,
  BONE_PERMISSION_CODE_CATALOG,
  ALL_BONE_PERMISSION_CODE_VALUES,
} from '../../../../packages/shared-types/src/bonePermissionCodes';

export type { BonePermissionCode } from '../../../../packages/shared-types/src/bonePermissionCodes';
