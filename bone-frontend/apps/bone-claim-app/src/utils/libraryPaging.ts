/**
 * 基础目录分页取全。
 *
 * <p>从 `LiabilityConfigPage` 抽出：选择器需要「全部编码」，此前固定 `size: 500` 取单页，
 * 目录超过 500 条时**静默截断**——配置岗在下拉里选不到第 501 条以后的医院/疾病/药品，
 * 界面不报错，只表现为「这个编码不存在」，比直接报错更难排查。抽成纯函数后可单测，防回归。
 *
 * <p>触达页数上限仍未取尽时返回 `truncated=true`，由调用方**显式告警**，
 * 绝不静默返回部分数据。
 */

/**
 * 单页条数。
 *
 * <p>必须 ≤ 服务端单页上限（`PageParam.MAX_PAGE_SIZE = 100`）：服务端会把 `size` 收窄到 100，
 * 若这里仍要 200，第一页只会回来 100 条，而「本页未满 ⇒ 已到末页」的判定会把 101 条之后的数据
 * **静默丢掉**——正是本文件要消灭的那类缺陷，只是换了个触发点。
 */
export const LIBRARY_PAGE_SIZE = 100;

/** 页数硬上限（50 × 100 = 5000 条），防配置异常时无限翻页。 */
export const LIBRARY_MAX_PAGES = 50;

/** 分页响应的最小结构。`total` 线上类型为 `string | number`（分页组件序列化差异）。 */
export interface PageLike<T> {
  records?: T[];
  total?: string | number;
}

export interface FetchAllResult<T> {
  rows: T[];
  /** 触达页数上限仍未取尽：调用方必须显式告警，不得当作完整数据。 */
  truncated: boolean;
}

/**
 * 逐页拉取直到取尽。
 *
 * <p>终止判定**以 `total` 为准**，`records.length < 请求页大小` 只作为无 `total` 时的兜底：
 * 服务端可能把 `size` 收窄到比请求更小的值，此时「本页未满」并不代表已到末页。
 *
 * @param fetchPage 取一页的方法（page 从 1 开始）
 */
export async function fetchAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<{ data?: PageLike<T> }>,
): Promise<FetchAllResult<T>> {
  const rows: T[] = [];
  for (let page = 1; page <= LIBRARY_MAX_PAGES; page += 1) {
    const resp = await fetchPage(page, LIBRARY_PAGE_SIZE);
    const records = resp.data?.records ?? [];
    rows.push(...records);
    // 空页一定到尽（也防 total 与实际不一致时死循环）
    if (records.length === 0) return { rows, truncated: false };
    const rawTotal = resp.data?.total;
    // 注意不能用 Number(null)：null 会被转成 0，导致首页就误判「已取尽」
    const total = rawTotal === undefined || rawTotal === null ? Number.NaN : Number(rawTotal);
    if (Number.isFinite(total) && rows.length >= total) return { rows, truncated: false };
    // 无 total 时只能按「本页未满」判断末页
    if (!Number.isFinite(total) && records.length < LIBRARY_PAGE_SIZE) {
      return { rows, truncated: false };
    }
  }
  return { rows, truncated: true };
}
