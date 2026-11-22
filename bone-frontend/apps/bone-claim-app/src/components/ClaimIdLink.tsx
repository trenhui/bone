import type { MouseEvent, ReactElement } from 'react';
import { Button, Tooltip } from 'antd';
import { buildCase360Link, isSafeClaimId, type CaseTab } from '../workbench/caseDeepLinks';
import { buildFocusHref } from '../workbench/focusMode';

/**
 * 案件 ID / 报案号 → 案件 360 详情页的唯一「可点击单元格」入口。
 *
 * <p>为什么要这个组件
 * ----------------
 * 案件 360 页面（`/case?claimId=...&tab=...`）早已完整实现，但核赔/初审/盲标三个工作台的
 * 「报案号 / 案件 ID」列此前是纯文本——用户看到一个 19 位雪花号，只能手抄到查询框。
 * Nielsen #6「识别优于回忆」：列表里给出的对象标识应可直接点击下钻，而不是逼用户记忆/转录。
 *
 * <p>单一真源
 * ----------
 * URL 契约完全委托给 {@link buildCase360Link}，本组件不拼任何字符串；安全由
 * {@link isSafeClaimId}（[A-Za-z0-9_-]{1,64}）把关。非法 ID **fail-closed** 降级为纯文本，
 * 绝不生成可能被注入的链接——与 caseDeepLinks.ts 的安全约定一致。
 *
 * <p>导航行为：始终新标签页打开
 * ------------------------------
 * 用户在工作台逐案核对时，原列表的滚动位置、筛选条件、已勾选任务都必须原样保留，
 * 因此普通左键点击也**强制新标签页**打开案件 360。实现上渲染真实
 * `<a href target="_blank" rel="noopener noreferrer">`，而不是在 onClick 里 `window.open`：
 * 前者保留「复制链接地址 / 右键新标签页打开 / 状态栏预览 / 中键点击」等原生语义，
 * 且 `noopener` 防止新页面通过 window.opener 反向操作原窗口，`noreferrer` 不泄漏来源。
 *
 * <p>无壳焦点模式
 * --------------
 * href 由 {@link buildFocusHref} 统一拼成 `origin + /claim-detail#/case?...`：shell 识别
 * `/claim-detail` 后只挂载微应用内容区，不渲染侧栏/顶栏/Content 边距，得到与旧理赔
 * `claimDetail` 一致的独立详情页。微应用内部仍是 HashRouter，hash 路由契约不变。
 */
export interface ClaimIdLinkProps {
  /** 案件 ID（真值来源）。数字类型会被转成字符串；空值降级为占位符。 */
  claimId: string | number | null | undefined;
  /** 单元格显示文本。缺省显示 claimId；传入时用显示文本（如报案号）但下钻仍用 claimId。 */
  text?: string;
  /** 目标案件 360 Tab，默认 overview。 */
  tab?: CaseTab;
  /** E2E 稳定锚点；未传时按 `claim-id-link-<claimId>` 生成。 */
  testId?: string;
  /** 非法/空 ID 时的占位符，默认 '—'。 */
  placeholder?: string;
}

export function ClaimIdLink({
  claimId,
  text,
  tab = 'overview',
  testId,
  placeholder = '—',
}: ClaimIdLinkProps): ReactElement {
  const id = claimId == null ? '' : String(claimId);
  const label = text?.trim() || id || placeholder;
  const href = buildFocusHref(buildCase360Link(id, tab));

  // fail-closed：非法 ID（含注入字符）或空值不生成链接，只显示纯文本。
  if (!href || !isSafeClaimId(id)) {
    return <span data-testid={testId ?? 'claim-id-link-invalid'}>{label}</span>;
  }

  const resolvedTestId = testId ?? `claim-id-link-${id}`;

  const handleClick = (e: MouseEvent<HTMLElement>): void => {
    // 盲标等工作台整行有 onClick（选中任务）：点 ID 不应同时触发行选中。
    // 不调用 preventDefault —— 让浏览器按 <a target="_blank"> 原生语义打开新标签页。
    e.stopPropagation();
  };

  return (
    <Tooltip title={`在新标签页打开案件 ${id} 详情`} mouseEnterDelay={0.4}>
      <Button
        type="link"
        size="small"
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        data-testid={resolvedTestId}
        aria-label={`在新标签页打开案件 ${label} 详情`}
        onClick={handleClick}
        style={{ paddingInline: 0, height: 'auto' }}
      >
        {label}
      </Button>
    </Tooltip>
  );
}
