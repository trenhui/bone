import { useEffect, useState } from 'react';
import {
  PERMISSIONS_CHANGE_EVENT,
  readPermissionCodes,
  readRoleCodes,
} from '@bone/shared-utils';
import { resolveWorkbenchProfile, type WorkbenchProfile } from './profiles';

function snapshot(): WorkbenchProfile {
  return resolveWorkbenchProfile(readRoleCodes(), readPermissionCodes());
}

/**
 * 当前登录用户的工作台画像。
 *
 * 读取顺序：Shell 下发的 `window.__BONE_GLOBAL_CONTEXT__.permissions.roles` → 权限码快照
 * →（shared-utils 内部三级降级）localStorage scopes → JWT payload。全部走平台单一真源，
 * 不在微应用里另写一份 JWT 解析。
 *
 * 为什么用 effect 显式重算而不是 useMemo：
 * 1. qiankun 场景下 `__BONE_GLOBAL_CONTEXT__` 可能晚于微应用首帧注入，挂载时必须再读一次；
 * 2. 订阅 `bone:permissions:change` 让「切换租户 / 重新授权后不刷新页面」也能重算画像，
 *    否则会出现「权限已变、首页还是上一角色」的静默错配。
 */
export function useWorkbenchProfile(): WorkbenchProfile {
  const [profile, setProfile] = useState<WorkbenchProfile>(snapshot);

  useEffect(() => {
    const refresh = () => setProfile(snapshot());
    refresh();
    if (typeof window === 'undefined') return undefined;
    window.addEventListener(PERMISSIONS_CHANGE_EVENT, refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener(PERMISSIONS_CHANGE_EVENT, refresh);
      window.removeEventListener('storage', refresh);
    };
  }, []);

  return profile;
}
