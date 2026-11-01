import { useCallback, useEffect, useState } from 'react';
import { App as AntApp } from 'antd';
import { claimConfigApi, type PolicyView } from '../services/claimConfigApi';
import { resolveErrorMessage } from '@bone/shared-utils';

/**
 * 保单下拉选项共享数据。
 *
 * <p>配置面 Plan / Quota / Liability 三个页面都需要「保单列表」作为级联选择器的第一级，
 * 原先三页各自写了一份 {@code loadPolicies({ page: 1, size: 200 })}（逐字重复，且失败处理也各写一份）。
 * 抽到本 hook 后只需一行调用，保单列表的加载与失败提示只有一份真源。</p>
 */
export interface PolicyOptions {
  policies: PolicyView[];
  loading: boolean;
  reload: () => void;
}

export function usePolicyOptions(): PolicyOptions {
  const { message } = AntApp.useApp();
  const [policies, setPolicies] = useState<PolicyView[]>([]);
  const [loading, setLoading] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const res = await claimConfigApi.listPolicies({ page: 1, size: 200 });
      setPolicies(res.data?.records ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '保单列表加载失败'));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { policies, loading, reload };
}
