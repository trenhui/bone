/**
 * 无壳焦点详情页（新标签页）的 URL 唯一真源。
 *
 * <p>为什么要独立路径而不是复用 `/claim#/case?...`
 * ------------------------------------------------
 * `/claim` 是 qiankun 微应用路由，新标签页打开时会连带 shell 的侧栏、顶栏与 Content 卡片边距。
 * 用户在列表里逐案/逐单核对时，需要的是只含内容区的独立详情页（对标旧理赔
 * `/claimDetail?claimId=...` 的形态）。故 shell 新增 `/claim-detail` 焦点模式：
 * 识别该前缀后只挂载 `#subapp-viewport`，不渲染任何导航外壳。
 *
 * <p>微应用内部仍用 HashRouter 读 hash，因此 pathname 换成 `/claim-detail` 不影响
 * `#/case?claimId=...` / `#/policy?policyNo=...` 的路由契约——只换壳，不换页面。
 */
export const CLAIM_FOCUS_PATH = '/claim-detail';

/**
 * 把微应用内 hash 链接拼成新标签页可直达的完整 URL。
 * 非应用内绝对路径一律返回 null（fail-closed，调用方降级为纯文本）。
 */
export function buildFocusHref(appLink: string | null | undefined): string | null {
  if (!appLink || !appLink.startsWith('/')) return null;
  return `${window.location.origin}${CLAIM_FOCUS_PATH}#${appLink}`;
}
