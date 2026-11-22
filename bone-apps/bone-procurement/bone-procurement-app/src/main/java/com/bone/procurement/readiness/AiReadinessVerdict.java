package com.bone.procurement.readiness;

/** AI 就绪度受控判词。只有 AVAILABLE 表示可判定可用，其余均为 fail-closed。 */
public enum AiReadinessVerdict {
  AVAILABLE,
  DEGRADED,
  UNKNOWN
}
