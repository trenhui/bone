// @vitest-environment node
import { describe, expect, it } from 'vitest';
import {
  formValuesToQueryString,
  queryParamsFromState,
  queryStateToFormValues,
} from './ClaimQueryFields';
import type { ClaimQueryState } from '../hooks/useClaimQuery';

const state: ClaimQueryState = {
  keyword: '266400023001',
  page: 3,
  size: 50,
  filters: {
    claimId: '266400023001',
    status: 'PENDING_PRE_EXAM,EVIDENCE_SUBMITTING',
    hangUpStatus: '1',
    createdFrom: '2026-04-01',
    createdTo: '2026-04-30',
    amountMin: '0',
    amountMax: '1000',
  },
};

describe('案件通用查询条件转换', () => {
  it('URL 状态与表单值互转：多选状态按逗号分隔，金额 0 不被吞掉', () => {
    const values = queryStateToFormValues(state);
    expect(values.statuses).toEqual(['PENDING_PRE_EXAM', 'EVIDENCE_SUBMITTING']);
    expect(values.amountMin).toBe(0);
    expect(values.amountMax).toBe(1000);
    expect(formValuesToQueryString(values)).toContain('f_status=PENDING_PRE_EXAM%2CEVIDENCE_SUBMITTING');
    expect(formValuesToQueryString(values)).toContain('f_amountMin=0');
  });

  it('空值不写成幽灵筛选键', () => {
    const qs = formValuesToQueryString({ keyword: '', statuses: [], amountMin: undefined });
    expect(qs).toBe('');
  });

  it('查询参数把结构化字符串转换为后端数值条件', () => {
    const params = queryParamsFromState(state);
    expect(params).toMatchObject({
      keyword: '266400023001',
      claimId: '266400023001',
      statuses: ['PENDING_PRE_EXAM', 'EVIDENCE_SUBMITTING'],
      hangUpStatus: 1,
      createdFrom: '2026-04-01',
      createdTo: '2026-04-30',
      amountMin: 0,
      amountMax: 1000,
      page: 3,
      size: 50,
    });
  });
});
