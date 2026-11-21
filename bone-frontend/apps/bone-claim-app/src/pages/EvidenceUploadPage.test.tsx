import { render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// 只替换网络服务与 URL 参数 hook，保留页面真实的 Tag / 表格渲染
vi.mock('../services/claimApi', () => ({
  evidenceApi: {
    list: vi.fn(),
    register: vi.fn(),
    lock: vi.fn(),
    updateClearType: vi.fn(),
  },
  attachmentApi: {
    upload: vi.fn(),
    downloadUrl: vi.fn(() => ''),
  },
}));
vi.mock('../hooks/useClaimIdParam', () => ({
  useClaimIdParam: () => ({
    claimId: 'c-1',
    input: 'c-1',
    setInput: vi.fn(),
    submit: vi.fn(),
    clear: vi.fn(),
  }),
}));

import { EvidenceUploadPage } from './EvidenceUploadPage';
import { evidenceApi } from '../services/claimApi';

const ok = <T,>(data: T) => ({ code: 200, message: 'ok', data });

/**
 * 清晰度三态的判别力护栏：三态必须是三种可区分的 Tag。
 *
 * 只断言「UNCLEAR 红 / CLEAR 绿」不足以覆盖回归边界——若有人把三态压成一个默认色，
 * 或把 UNCLEAR 误映射为绿，这里都会红。查询限定在 table 内，避开操作区 Select 的
 * 已选值文案（Select 同样显示「清晰」）。
 */
describe('EvidenceUploadPage · 影像清晰度三态', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (evidenceApi.list as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok([
        { id: 'e1', claimId: 'c-1', docType: 'INVOICE', clearType: 'UNCLEAR' },
        { id: 'e2', claimId: 'c-1', docType: 'LIST', clearType: 'CLEAR' },
        { id: 'e3', claimId: 'c-1', docType: 'MEDICAL_RECORD', clearType: 'INCOMPLETE' },
      ]),
    );
  });

  it('UNCLEAR 渲染为红 Tag「不清晰」，CLEAR 渲染为绿 Tag「清晰」', async () => {
    render(<EvidenceUploadPage embeddedClaimId="c-1" />);

    const table = screen.getByRole('table');
    const unclear = await within(table).findByText('不清晰');
    expect(unclear.className).toContain('ant-tag-red');

    const clear = within(table).getByText('清晰');
    expect(clear.className).toContain('ant-tag-green');

    // 第三态同样可区分，不能被吞成默认色
    const incomplete = within(table).getByText('不完整');
    expect(incomplete.className).toContain('ant-tag-orange');
  });
});
