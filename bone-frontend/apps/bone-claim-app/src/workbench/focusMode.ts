/**
 * 无壳独立详情页（新标签页）的 URL 唯一真源。
 *
 * <p>为什么用 /standalone
 * ------------------------
 * 微前端领域把「脱离主应用壳、只挂载内容区」的页面称为 standalone page（single-spa /
 * qiankun 生态通用术语）。相比上一版 /claim-detail：
 *  - 语义中立：同一前缀承载案件 360、保单详情及未来任何独立页，不再被 claim 业务域绑死；
 *  - 不含 detail 这类视图态词：path 只标识「独立渲染模式」，资源由 hash 段表达；
 *  - 便于运维：网关 / 静态托管 / 监控可按 /standalone 前缀统一施策。
 *
 * <p>路由结构：path + hash 混合
 * ------------------------------
 * path 段由 shell（BrowserRouter）识别渲染模式；hash 段由微应用（HashRouter）识别业务页面。
 * 这是 qiankun 微前端的历史约束，换 path 前缀不影响 `#/case?claimId=...` /
 * `#/policy?policyNo=...` 的页面契约——只换壳，不换页面。
 *
 * <p>旧别名
 * ----------
 * /claim-detail 是上一版契约，保留为只读别名：历史链接（已收藏 / IM 已发出）仍可直达，
 * 但新链接一律生成 /standalone。
 */

/** 独立页路径前缀——新链接的唯一生成口径。 */
export const STANDALONE_PATH = '/standalone';

/**
 * 旧版焦点路径，仅用于识别历史链接，不要再用于生成新链接。
 */
export const LEGACY_FOCUS_PATHS: readonly string[] = ['/claim-detail'];

/** 所有被识别为独立页模式的路径前缀（新 + 旧别名）。 */
export const FOCUS_PATHS: readonly string[] = [STANDALONE_PATH, ...LEGACY_FOCUS_PATHS];

/**
 * 判断 pathname 是否处于无壳独立页模式。
 * 新旧路径都认；按完整路径段匹配（`/standaloneX` 不算），避免前缀误判。
 */
export function isFocusPath(pathname: string | null | undefined): boolean {
  if (!pathname) return false;
  return FOCUS_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/**
 * 把微应用内 hash 链接拼成新标签页可直达的完整 URL。
 * 非应用内绝对路径（含协议相对 `//`）一律返回 null（fail-closed，调用方降级为纯文本）。
 */
export function buildFocusHref(appLink: string | null | undefined): string | null {
  if (!appLink || !appLink.startsWith('/') || appLink.startsWith('//')) return null;
  return `${window.location.origin}${STANDALONE_PATH}#${appLink}`;
}
