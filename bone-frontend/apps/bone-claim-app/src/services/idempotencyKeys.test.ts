// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { stableClaimActionKey } from './claim';
import { stableSettlementKey } from './settlement';

/**
 * 稳定幂等键的判别力测试。
 *
 * 「有幂等键」≠「重试同键」：若每次点击都现取 UUID，网络超时重试仍会重复执行。
 * 这里锁定同一业务快照两次调用必须同键，快照推进后必须换键。
 */
describe('稳定业务幂等键', () => {
  it('报案/决策键：同一案件同一状态快照重复生成同键', () => {
    const a = stableClaimActionKey('1', 'review-decide', 'ADJUSTING');
    const b = stableClaimActionKey('1', 'review-decide', 'ADJUSTING');
    expect(a).toBe(b);
    expect(a).toBe('claim:1|review-decide|ADJUSTING');
    expect(stableClaimActionKey('1', 'review-decide', 'ADJUSTED')).not.toBe(a);
  });

  it('资金键：同一结算单快照的支付动作同键，阶段推进后换键', () => {
    const snapshot = { settlementNo: 'SO-1', fundingStage: 'HARD_RESERVED', attempt: 1 };
    const a = stableSettlementKey('1', 'pay', snapshot);
    const b = stableSettlementKey('1', 'pay', snapshot);
    expect(a).toBe(b);
    expect(a).toBe('settlement:1|pay|SO-1|HARD_RESERVED|1');
    expect(stableSettlementKey('1', 'pay', { ...snapshot, attempt: 2 })).not.toBe(a);
  });
});
