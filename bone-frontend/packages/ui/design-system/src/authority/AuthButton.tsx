import { Button, Tooltip } from 'antd';
import type { ButtonProps } from 'antd';

import { usePermission } from './usePermission';

/**
 * 受权限码控制的按钮 —— 把「写死的操作按钮」接进 IAM 角色授权体系的落点。
 *
 * ## 默认行为为什么是「隐藏」而不是「置灰」
 *
 * 置灰（disabled）会保留按钮的可发现性，让没有权限的用户看见一整排点不动的按钮，
 * 既制造噪音又诱导权限申请；隐藏则是 RBAC 界面的通行做法。需要「让入口可见但不可点」
 * 的场景（如"为什么我不能开票"这类需要解释的功能）显式传 `mode="disable"`。
 *
 * ⚠️ 只是 UX 层：真正的门禁永远在后端 `@PreAuthorize`。前端放行被绕过后，请求照样
 * 被后端拒绝；反过来前端隐藏了也不代表接口安全——两者不可互相替代。
 */
export interface AuthButtonProps extends ButtonProps {
  /** AND 语义：列出的码必须全部命中。 */
  code?: string | string[];
  /** OR 语义：任一命中即可（与 code 互斥，优先取 anyOf）。 */
  anyOf?: string[];
  /** `hide`（默认，无权限不渲染）/ `disable`（无权限置灰并提示）。 */
  mode?: 'hide' | 'disable';
  /** `disable` 模式下的提示文案。 */
  deniedHint?: string;
}

export function AuthButton({
  code,
  anyOf,
  mode = 'hide',
  deniedHint,
  disabled,
  ...rest
}: AuthButtonProps): JSX.Element | null {
  const { has, hasAny } = usePermission();
  const allowed = anyOf && anyOf.length > 0 ? hasAny(anyOf) : has(code);

  if (allowed) {
    // eslint-disable-next-line react/jsx-props-no-spreading
    return <Button {...rest} disabled={disabled} />;
  }
  if (mode === 'hide') {
    return null;
  }
  return (
    <Tooltip title={deniedHint ?? '当前角色未被授予该操作权限'}>
      {/* eslint-disable-next-line react/jsx-props-no-spreading */}
      <Button {...rest} disabled />
    </Tooltip>
  );
}
