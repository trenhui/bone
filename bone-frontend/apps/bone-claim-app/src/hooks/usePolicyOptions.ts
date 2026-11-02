import { useCallback, useEffect, useState } from 'react';
import { App as AntApp } from 'antd';
import { claimConfigApi, type PolicyView } from '../services/claimConfigApi';
import { resolveErrorMessage } from '@bone/shared-utils';
import { LIBRARY_MAX_PAGES, LIBRARY_PAGE_SIZE, fetchAllPages } from '../utils/libraryPaging';

/**
 * 保单下拉选项共享数据。
 *
 * <p>配置面 Plan / Quota / Liability 三个页面都需要「保单列表」作为级联选择器的第一级，
 * 原先三页各自写了一份 {@code loadPolicies({ page: 1, size: 200 })}（逐字重复，且失败处理也各写一份）。
 * 抽到本 hook 后只需一行调用，保单列表的加载与失败提示只有一份真源。</p>
 *
 * <p><b>为什么分页取尽而不是固定 {@code size: 200}</b>：固定单页在保单数超过 200 时**静默截断**，
 * 级联选择器第一级缺项，界面不报错、只表现为「这张保单明明存在却选不到」——与责任配置基础目录
 * 此前 {@code size: 500} 的缺陷同一形态（见 {@link fetchAllPages}）。触达页数硬上限仍未取尽时
 * 显式告警，绝不把部分数据当完整数据用。</p>
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
      const { rows, truncated } = await fetchAllPages((page, size) =>
        claimConfigApi.listPolicies({ page, size }),
      );
      setPolicies(rows);
      if (truncated) {
        message.warning(
          `保单数超过单次加载上限（${LIBRARY_MAX_PAGES * LIBRARY_PAGE_SIZE} 条），下拉未取全；请到「保单配置」页检索`,
        );
      }
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
