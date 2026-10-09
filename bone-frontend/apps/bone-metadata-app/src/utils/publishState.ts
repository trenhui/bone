/**
 * 发布脏态（MVP 近似）本地状态。
 *
 * 背景：PRD §0.5 / 融合方案 §3.2 要求实体工作区顶栏显示「未发布 · N 项」脏态条，
 * 让「已发布实体加字段后需重新发布」在详情页一眼可见。
 *
 * 精确做法（v1.1）：后端发布快照比对。MVP 用**前端时间戳近似**——发布成功时记下
 * 本地时刻，与实体 `updatedAt`（结构变更会回写）比较：若 `updatedAt > lastPublish`
 * 则说明发布后又有结构变更，判定为「有更新待重新发布」。
 * 时钟偏差在 MVP 容忍范围内（详见融合方案 §3.2 脏态算法说明）。
 */

export function lastPublishKey(entityId: string): string {
  return `bone.meta.lastPublish.${entityId}`;
}

/** 读取最近一次发布时刻（毫秒时间戳），无记录返回 0 */
export function getLastPublish(entityId: string): number {
  try {
    const raw = localStorage.getItem(lastPublishKey(entityId));
    return raw ? Number(raw) || 0 : 0;
  } catch {
    return 0;
  }
}

/** 记录一次发布成功（写入本地时刻） */
export function setLastPublish(entityId: string): void {
  try {
    localStorage.setItem(lastPublishKey(entityId), String(Date.now()));
  } catch {
    /* 忽略存储异常（隐私模式等） */
  }
}

/**
 * 实体是否处于「已发布但结构已更新、待重新发布」的脏态。
 * @param entityUpdatedAt 实体 updatedAt（ISO 字符串或时间戳）
 */
export function isDirtySincePublish(entityUpdatedAt?: string | number, lastPublish = 0): boolean {
  if (!entityUpdatedAt) return false;
  const updated =
    typeof entityUpdatedAt === 'number' ? entityUpdatedAt : Date.parse(String(entityUpdatedAt));
  if (Number.isNaN(updated) || updated <= 0) return false;
  return updated > lastPublish;
}
