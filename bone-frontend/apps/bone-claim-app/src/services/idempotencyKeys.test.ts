// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { stableAppendCaseKey, stableClaimActionKey } from './claim';
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

  /**
   * 追加赔案键：这条键的意义比前两条更重——它是「同一事故追加第二笔赔付」的唯一重复防线。
   * 若退化成 UUID，用户点两次「追加赔案」就会真的多赔一笔（后端 uk_tenant_idem 拿不到同键就无从去重）。
   */
  it('追加赔案键：同报案同票据同金额同键；换报案/换金额必须换键', () => {
    const req = {
      invoiceCode: '04400001',
      invoiceNo: '12345678',
      items: [{ itemName: '诊查费', amount: 10 }],
    };
    const a = stableAppendCaseKey('RP20261122ABCDEFGH1234', req);
    const b = stableAppendCaseKey('RP20261122ABCDEFGH1234', req);

    expect(a).toBe(b);
    expect(a).toBe('claim:append:RP20261122ABCDEFGH1234|04400001|12345678|10.00');
    // 补录了明细等于另一笔业务，必须换键，否则第二笔会被幂等表当成重放吞掉
    expect(
      stableAppendCaseKey('RP20261122ABCDEFGH1234', {
        ...req,
        items: [{ itemName: '诊查费', amount: 20 }],
      }),
    ).not.toBe(a);
    // 不同报案绝不同键，否则跨事故的赔付会互相去重
    expect(stableAppendCaseKey('RP20261122ZZZZZZZZ9999', req)).not.toBe(a);
  });
});
