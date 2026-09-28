import type { ReactNode } from 'react';
import { Result } from 'antd';
import { hasPermission } from '../auth/permission';

interface AuthorizedProps {
  /** 必须同时拥有的权限码（详设 §5.0；前端守卫，后端 @PreAuthorize 兜底）。 */
  required: string | string[];
  /** 无权时展示的内容；默认渲染 403 提示页。 */
  fallback?: ReactNode;
  children: ReactNode;
}

/**
 * 路由/区块级权限守卫（S-4）。仅 UX 增强；真授权在后端 {@code @PreAuthorize} 兜底。
 *
 * <p>必须渲染在<b>微应用内部</b>（不外包给 Shell 的 {@code <Authorized>}）——否则租户管理员无权限时，
 * Shell 会因容器不渲染而整页空白（详设 §5.0 注：租户管理员 403 → 容器不渲染）。此处只在微应用内
 * 渲染一个 403 兜底页，不影响 qiankun 容器挂载。
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
