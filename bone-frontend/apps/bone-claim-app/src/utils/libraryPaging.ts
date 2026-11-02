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

/** 单页条数（在服务端分页接口单页上限内取较大值，减少往返）。 */
export const LIBRARY_PAGE_SIZE = 200;

/** 页数硬上限（20 × 200 = 4000 条），防配置异常时无限翻页。 */
export const LIBRARY_MAX_PAGES = 20;

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
    // 本页未满 ⇒ 已到末页；有 total 时以其为准（服务端可能限制单页上限）
    if (records.length < LIBRARY_PAGE_SIZE) return { rows, truncated: false };
    const total = Number(resp.data?.total);
    if (Number.isFinite(total) && rows.length >= total) return { rows, truncated: false };
  }
  return { rows, truncated: true };
}
