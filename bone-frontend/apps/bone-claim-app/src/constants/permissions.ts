/**
 * 前端权限码常量（单一真源）。
 *
 * <p>后端 {@code claim:policy:write} / {@code claim:quota:write} 等权限点在配置面多处复用，
 * 收口为常量后，后端改码时前端编译期即可发现遗漏（魔法字符串无法在编译期捕获）。
 *
 * <p>作业面（核赔/初审/决策/资金/申诉/后督/材料）的权限码与后端
 * {@code @PreAuthorize} 一一对应；前端用 {@link AuthButton} 做「无权限即隐藏」的 UX 降级，
 * 真正的门禁仍由后端强制（前端可见性不可替代接口鉴权）。
 */
export const PERMISSIONS = {
  // ---- 配置面 ----
  POLICY_READ: 'claim:policy:read',
  POLICY_WRITE: 'claim:policy:write',
  /** 映射处置独立权限点：未命中的建立映射/忽略是「翻译外部码」的高权操作，与一般配置写分开授权。 */
  MAPPING_DISPOSE: 'claim:mapping:dispose',
  QUOTA_READ: 'claim:quota:read',
  QUOTA_WRITE: 'claim:quota:write',
  // ---- 作业面（与后端方法级 @PreAuthorize 对齐）----
  CLAIMS_READ: 'claim:claims:read',
  CLAIMS_WRITE: 'claim:claims:write',
  REVIEW_WRITE: 'claim:review:write',
  DECISION_WRITE: 'claim:decision:write',
  SETTLEMENT_WRITE: 'claim:settlement:write',
  APPEAL_WRITE: 'claim:appeal:write',
  INSPECTION_WRITE: 'claim:inspection:write',
} as const;

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
