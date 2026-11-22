import type { MouseEvent, ReactElement } from 'react';
import { Button, Tooltip } from 'antd';
import { isSafePolicyNo } from '../workbench/policyDeepLinks';
import { buildPolicyDetailHref } from '../workbench/sclaimRoutes';

/**
 * 保单号 → 保单详情的唯一「可点击单元格」入口。
 *
 * <p>与 ClaimIdLink 同构但目标不同
 * --------------------------------
 * 案件 ID 下钻案件 360；保单号下钻保单管理页的详情抽屉（深链 `/policy?policyNo=`）。
 * 两条链路共用同一套浏览器语义：真实 `<a target="_blank" rel="noopener noreferrer">`
 * 在新标签页打开，保留复制地址 / 中键点击 / 状态栏预览；`onClick` 只 `stopPropagation`
 * 防止触发表格整行的选中，不 `preventDefault`（否则浏览器不会开新标签页）。
 *
 * <p>安全：保单号经 {@link isSafePolicyNo} 白名单校验后才生成链接，非法值 fail-closed
 * 降级为纯文本，绝不把用户输入拼进 URL。URL 契约完全委托 {@link buildPolicyDetailLink}。
 *
 * <p>注意：保司保单号（`insurerPolicyNo`）可能对应多张平台保单（后端会返回 422 AMBIGUOUS），
 * 因此本组件只用于**平台保单号** `policyNo`，不要接到保司号列上。
 */
export interface PolicyNoLinkProps {
  /** 平台保单号（t_claim_policy.policy_no）。数字类型会被转成字符串。 */
  policyNo: string | number | null | undefined;
  /** 单元格显示文本；缺省显示 policyNo。 */
  text?: string;
  /** E2E 稳定锚点；未传时按 `policy-no-link-<policyNo>` 生成。 */
  testId?: string;
  /** 非法/空值时的占位符，默认 '—'。 */
  placeholder?: string;
}

export function PolicyNoLink({
  policyNo,
  text,
  testId,
  placeholder = '—',
}: PolicyNoLinkProps): ReactElement {
  const id = policyNo == null ? '' : String(policyNo);
  const label = text?.trim() || id || placeholder;
  const href = buildPolicyDetailHref(id);

  if (!href || !isSafePolicyNo(id)) {
    return <span data-testid={testId ?? 'policy-no-link-invalid'}>{label}</span>;
  }

  const resolvedTestId = testId ?? `policy-no-link-${id}`;
  const handleClick = (e: MouseEvent<HTMLElement>): void => {
    e.stopPropagation();
  };

  return (
    <Tooltip
      title={
        <span>
          保单号 {label}
          <br />
          在新标签页打开保单详情
        </span>
      }
      mouseEnterDelay={0.4}
    >
      <Button
        type="link"
        size="small"
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        data-testid={resolvedTestId}
        aria-label={`在新标签页打开保单 ${label} 详情`}
        onClick={handleClick}
        // 与 ClaimIdLink 同构：长号在窄列里省略，完整值由 Tooltip 承载，不撑破单元格。
        style={{ paddingInline: 0, height: 'auto', maxWidth: '100%', textAlign: 'left' }}
        className="sclaim-id-link"
      >
        {label}
      </Button>
    </Tooltip>
  );
}
