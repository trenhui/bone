import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * 案件 ID 的 URL 参数读写（`?claimId=`）。
 *
 * <p><b>为什么不再给默认值 `'1'`</b>：`EvidenceUploadPage` / `AppealPage` / `SuspendPage` 原来写
 * `searchParams.get('claimId') ?? '1'`，进页面即对一个未必存在的案件发请求：未命中时后端 404
 * `SC_CLAIM_NOT_FOUND`，被 catch 兜底成「加载失败」——把「还没选案件」这种正常空态报成了系统故障，
 * 而且每次进页面都白跑一次请求。
 *
 * <p>同一批代码里 `AdjustmentAttributionPage` / `FundWorkbenchPage` 的注释已明确禁止占位 `'1'`，
 * 两种口径并存本身就是缺陷来源。本 hook 统一为：**URL 没有就是空串**，页面据此渲染空态、不发请求。
 */
export interface ClaimIdParam {
  /** 当前生效的案件 ID；空串表示未选择（调用方应据此渲染空态而不是发请求）。 */
  claimId: string;
  /** 输入框受控值（未提交前的临时输入）。 */
  input: string;
  setInput: (value: string) => void;
  /** 提交输入 → 写回 URL；空输入不写入。 */
  submit: () => void;
  /** 清空选择（回到空态）。 */
  clear: () => void;
}

export function useClaimIdParam(): ClaimIdParam {
  const [searchParams, setSearchParams] = useSearchParams();
  const claimId = (searchParams.get('claimId') ?? '').trim();
  const [input, setInput] = useState(claimId);

  /**
   * 与 URL 单向同步：浏览器前进/后退或外部跳转改了 claimId 时，输入框跟着变。
   * 缺了这一步，用「上一条」退回后输入框仍显示旧值，用户会以为没生效。
   */
  useEffect(() => {
    setInput(claimId);
  }, [claimId]);

  const submit = useCallback(() => {
    const id = input.trim();
    if (!id) return;
    setSearchParams({ claimId: id });
  }, [input, setSearchParams]);

  const clear = useCallback(() => {
    setInput('');
    setSearchParams({});
  }, [setSearchParams]);

  return { claimId, input, setInput, submit, clear };
}
