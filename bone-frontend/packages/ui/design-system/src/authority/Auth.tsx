import type { ReactNode } from 'react';

import { usePermission } from './usePermission';

/**
 * 按权限码做条件渲染的包装组件。
 *
 * 与 {@link AuthButton} 的分工：`<Auth>` 管任意片段（表格列、整个区块、路由），
 * `<AuthButton>` 只管按钮且自带「无权限时不渲染」的默认行为。
 */
export interface AuthProps {
  /** AND 语义：列出的码必须全部命中。 */
  code?: string | string[];
  /** OR 语义：任一命中即可（与 code 互斥，优先取 anyOf）。 */
  anyOf?: string[];
  /** 无权限时渲染的内容，默认 `null`（什么都不渲染）。 */
  fallback?: ReactNode;
  children: ReactNode;
}

export function Auth({ code, anyOf, fallback = null, children }: AuthProps): JSX.Element {
  const { has, hasAny } = usePermission();
  const allowed = anyOf && anyOf.length > 0 ? hasAny(anyOf) : has(code);
  return <>{allowed ? children : fallback}</>;
}
