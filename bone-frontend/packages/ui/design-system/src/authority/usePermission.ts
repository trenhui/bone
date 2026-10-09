import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  PERMISSIONS_CHANGE_EVENT,
  hasAnyPermission,
  hasPermission,
  readGlobalPermissions,
  readPermissionCodes,
  readRoleCodes,
  type BonePermissionSnapshot,
} from '@bone/shared-utils';

/**
 * 细粒度权限是「用户 → 角色 → 操作权限 → 微服务 API」链路的最后一公里：
 * 前三层（账号/角色/角色-权限绑定）由 IAM 维护并随 JWT `scopes` 下发，落到 UI 就是
 * 「一个码 → 一个按钮」的直接判定。本 hook 是 UI 侧唯一入口。
 */
export interface BoneAuthority {
  /** 当前生效权限码。 */
  codes: string[];
  /** 当前生效角色 code。 */
  roles: string[];
  /** 当前生效租户。 */
  tenantId?: string;
  /** AND 语义判定。 */
  has: (required?: string | string[]) => boolean;
  /** OR 语义判定。 */
  hasAny: (list: string[]) => boolean;
}

/**
 * 订阅 Shell 下发的权限快照。
 *
 * `__BONE_GLOBAL_CONTEXT__` 是一个**可变 window 对象**，不是 React state —— 只
 * 在读的那一刻取值会导致「切租户后按钮不刷新」。因此这里额外订阅
 * `bone:permissions:change`：Shell 每次刷新 permissions 都会广播，宿主页重渲染。
 */
export function usePermission(): BoneAuthority {
  const [snapshot, setSnapshot] = useState<BonePermissionSnapshot | null>(() =>
    readGlobalPermissions(),
  );

  useEffect(() => {
    const handleChange = () => setSnapshot(readGlobalPermissions());
    window.addEventListener(PERMISSIONS_CHANGE_EVENT, handleChange);
    return () => window.removeEventListener(PERMISSIONS_CHANGE_EVENT, handleChange);
  }, []);

  const codes = useMemo(() => readPermissionCodes(snapshot), [snapshot]);
  const roles = useMemo(() => (snapshot?.roles ?? readRoleCodes()), [snapshot]);

  const has = useCallback((required?: string | string[]) => hasPermission(required, codes), [codes]);
  const hasAny = useCallback((list: string[]) => hasAnyPermission(list, codes), [codes]);

  return { codes, roles, tenantId: snapshot?.tenantId, has, hasAny };
}
