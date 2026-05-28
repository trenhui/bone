import type { ReactNode } from 'react';
import { Result } from 'antd';
import { hasPermission } from './jwt';

interface AuthorizedProps {
  /** 必须同时拥有的权限码（详设 §5.0；前端守卫，后端 @PreAuthorize 兜底）。 */
  required: string | string[];
  /** 无权时展示的内容；默认渲染 403 提示页。 */
  fallback?: ReactNode;
  children: ReactNode;
}

/**
 * 前端路由/区块权限守卫。**仅做 UX 增强**：真正的授权校验在后端
 * {@code @PreAuthorize}（控制台为 {@code sys:console:read}）。
 *
 * <p>用法：<code>&lt;Authorized required="sys:console:read"&gt;...&lt;/Authorized&gt;</code>。
 */
export default function Authorized({ required, fallback, children }: AuthorizedProps): JSX.Element {
  if (hasPermission(required)) {
    return <>{children}</>;
  }
  if (fallback !== undefined) {
    return <>{fallback}</>;
  }
  return (
    <Result
      status="403"
      title="403"
      subTitle={`您没有访问当前页面的权限（需要 ${Array.isArray(required) ? required.join(', ') : required}）。请联系管理员授予权限。`}
    />
  );
}
