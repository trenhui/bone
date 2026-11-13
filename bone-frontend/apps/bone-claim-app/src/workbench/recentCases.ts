/**
 * 最近访问案件（会话级，sessionStorage）。
 *
 * 为什么是 sessionStorage 而不是隐式全局缓存 / localStorage：
 * - 只恢复「本次会话的作业上下文」，不在换账号后残留上一人的案件号（数据最小化）；
 * - 与 URL 里的 claimId 互补：URL 负责可分享/可回退，最近访问只负责少一次输入；
 * - 隐私：只存 claimId + tab + 时间戳，不存姓名、保单号、金额。
 */
const STORAGE_KEY = 'bone:claim:recent:v1';
const MAX_ITEMS = 8;

export interface RecentCase {
  claimId: string;
  tab: string;
  at: string;
}

function storage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

export function readRecentCases(): RecentCase[] {
  const s = storage();
  if (!s) return [];
  try {
    const parsed: unknown = JSON.parse(s.getItem(STORAGE_KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((x): x is RecentCase => Boolean(x) && typeof (x as RecentCase).claimId === 'string')
      .slice(0, MAX_ITEMS);
  } catch {
    return [];
  }
}

export function pushRecentCase(claimId: string, tab: string): RecentCase[] {
  const s = storage();
  if (!s || !claimId) return readRecentCases();
  const next: RecentCase[] = [
    { claimId, tab, at: new Date().toISOString() },
    ...readRecentCases().filter((x) => x.claimId !== claimId),
  ].slice(0, MAX_ITEMS);
  try {
    s.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // 隐私模式 / 配额满：最近访问是便利功能，失败不该影响主流程。
  }
  return next;
}

export function clearRecentCases(): void {
  try {
    storage()?.removeItem(STORAGE_KEY);
  } catch {
    // 同上：便利功能降级即可。
  }
}
