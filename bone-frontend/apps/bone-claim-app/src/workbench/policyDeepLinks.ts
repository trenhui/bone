/**
 * 保单详情 · 深链唯一真源。
 *
 * <p>为什么指向 `/policy?policyNo=` 而不是再造一个详情路由
 * ------------------------------------------------------------------
 * 保单管理页已有完整详情抽屉：基础信息 / 主体映射健康度 / 责任全貌 / 适用名单 / 额度池
 * 五个 Tab，消费 `getPolicy` + `getPolicyDetail` 两个接口。新建独立详情页会把这几百行
 * 展示逻辑复制成第二份——项目反复踩过「同一语义两处配置源」的坑，两份界面必然漂移。
 * 故深链指向保单管理页并带 `policyNo` 参数，由页面挂载时读参数自动打开既有抽屉；
 * 详情实现仍然只有一份，且 URL 可分享、可在新标签页直接打开。
 *
 * <p>与案件 360 深链（{@link buildCase360Link}）保持同一形态：微应用内 hash 路由
 * path + query，调用方统一拼 origin + 应用前缀 + `#` 生成可新标签页打开的完整 URL。
 */

// 规则与案件 ID 同一白名单（字母/数字/_/-，1..64）；复用同一实现，避免两处正则漂移。
import { isSafeClaimId } from './caseDeepLinks';

/** 保单号 URL 安全校验：非法值（含注入字符）一律 fail-closed。 */
export function isSafePolicyNo(value: string | null | undefined): boolean {
  return isSafeClaimId(value);
}

/** 保单号 → 微应用内 hash 深链；非法值返回 null。 */
export function buildPolicyDetailLink(policyNo: string): string | null {
  if (!isSafePolicyNo(policyNo)) return null;
  return `/policy?policyNo=${encodeURIComponent(policyNo.trim())}`;
}

/** 从 hash query 读保单号；非法值返回 null，让调用方 fail-closed。 */
export function parsePolicyNoFromSearch(search: string | null | undefined): string | null {
  const raw = new URLSearchParams(search ?? '').get('policyNo');
  return isSafePolicyNo(raw) ? raw!.trim() : null;
}
