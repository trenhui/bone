import { useEffect, useState } from 'react';
import { aiGovernanceApi } from '../services/aiGovernance';

/**
 * 双人复核金额门的**运行时真源**（后端 `TriageLimits.dualReviewAmount`）。
 *
 * <p>前端不再镜像一份 `10000`：后端治理页可在运行时收紧/放宽该阈值，硬编码常量会让列表徽标
 * 与真实判定脱节（调低阈值后应复核的大额案件不再被标记）。
 *
 * <p><b>失败口径</b>：接口不可用或字段非法时 `threshold = null`，调用方必须隐藏依赖阈值的徽标/计数
 * 并提示刷新，而不是回落到某个"看起来差不多"的常量——那会重新制造第二真源，且把"未知"伪装成"已知"。
 */
export interface DualReviewThresholdState {
  threshold: number | null;
  loading: boolean;
  error: boolean;
}

export function useDualReviewThreshold(): DualReviewThresholdState {
  const [state, setState] = useState<DualReviewThresholdState>({
    threshold: null,
    loading: true,
    error: false,
  });

  useEffect(() => {
    let cancelled = false;
    aiGovernanceApi
      .config()
      .then((snapshot) => {
        if (cancelled) return;
        const value = snapshot?.limits?.dualReviewAmount;
        const threshold =
          typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null;
        setState({ threshold, loading: false, error: threshold == null });
      })
      .catch(() => {
        if (!cancelled) setState({ threshold: null, loading: false, error: true });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}
