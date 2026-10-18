/**
 * 实体行策略（row-level policy）解析——「行策略 rule name 集合」的唯一真源。
 *
 * entity.rowPolicies 是实体上的一条 JSON 列：
 *   {"owner_field": "created_by", "rules": [{"name": "owner_or_approver", ...}]}
 * 取出的 rule name 集合是界面设计器列表视图 dataScope.policyCode 下拉的真源
 * （协议 §3.2：只引用 row_policies 声明的 rule name，WHERE 由后端强制，前端不拼）。
 *
 * 命名说明：policy 指行级数据策略（RLS，决定一行数据对谁可见），与保险业务域的
 * 「保单」无关——故对外统一称 rowPolicyCodes，避免逐字歧义。
 *
 * 该函数被两处共用，避免解析逻辑双实现漂移：
 * - EntityDetail（宿主预置 rowPolicyCodes，省一次请求）；
 * - UiSchemaDesigner（宿主未预置时自拉实体详情兜底，防新宿主漏传静默降级）。
 */
export function parseRowPolicyCodes(entity: unknown): string[] {
  const raw = (entity as { rowPolicies?: string | null } | null | undefined)?.rowPolicies;
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as { rules?: { name?: string }[] };
    return (parsed.rules ?? []).map((r) => r.name).filter((n): n is string => !!n);
  } catch {
    // JSON 损坏按「未声明行策略」处理：下拉降级为提示，不阻断设计器其余能力
    return [];
  }
}
