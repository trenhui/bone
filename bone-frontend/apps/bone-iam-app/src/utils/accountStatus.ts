/** 将后端 AccountStatus 枚举或数字统一为 0/1/2 */
export function accountStatusToCode(status: unknown): number {
  if (typeof status === 'number' && !Number.isNaN(status)) {
    return status;
  }
  if (typeof status === 'string') {
    const map: Record<string, number> = {
      DISABLED: 0,
      ENABLED: 1,
      LOCKED: 2,
    };
    return map[status] ?? 0;
  }
  return 0;
}
