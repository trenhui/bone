/** 就绪度 DTO（与后端 AiReadinessView 对齐）。 */
export interface AiReadinessDTO {
  verdict: string;
  verdictText: string;
  providerConfigured: boolean;
  governanceEnabled: boolean;
  killSwitchEngaged: boolean;
  evalFrozen: boolean;
  inferenceCapabilityRegistered: boolean;
  deploymentGatesPassed: boolean;
  authorizationNotice: string;
  reasons: string[];
}

const KNOWN: Record<string, { text: string; color: string }> = {
  AVAILABLE: { text: '可判定可用', color: 'green' },
  DEGRADED: { text: '受限可用', color: 'orange' },
  UNKNOWN: { text: '无法确认', color: 'default' },
};

/** 反假绿：只有 AVAILABLE 才显示可用；未知判词一律「无法确认」，不乐观映射。 */
export function readinessText(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return KNOWN[code]?.text ?? '无法确认';
}

export function verdictColor(code: string | null | undefined): string {
  if (!code) return 'default';
  return KNOWN[code]?.color ?? 'default';
}

/** 展示含原始判词码：未知判词必须原样显示便于排障，缺失显示无法确认。 */
export function verdictTextOrRaw(code: string | null | undefined): string {
  if (!code) return '无法确认';
  return `${readinessText(code)}（原始码：${code}）`;
}

/** 请求失败 fail-closed 文案。 */
export function readinessErrorMessage(): string {
  return '无法确认';
}
