import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { App as AntdApp } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 案件签收页面护栏。
 *
 * 守五类回归：
 *   1. 四态状态机在 UI 上漏动作 / 多动作（终态还留写按钮 = 允许二次签收）；
 *   2. 二次确认被绕过（点一下就直接签收，没有确认弹窗）；
 *   3. 来源 Tab 切换不改查询条件（线上/线下串数据）；
 *   4. 404 只有一闪而过的 toast、用户无法自助排查（必须持久化 Alert + 重试）；
 *   5. 空态只有「暂无数据」，没有下一步动作入口。
 */
const h = vi.hoisted(() => ({
  list: vi.fn(),
  tracks: vi.fn(),
  detail: vi.fn(),
  items: vi.fn(),
  confirm: vi.fn(),
  registerImage: vi.fn(),
  importParticipants: vi.fn(),
  createOfflineBatch: vi.fn(),
  confirmModal: vi.fn(),
}));

vi.mock('../services/claimSign', () => ({
  claimSignApi: {
    list: h.list,
    tracks: h.tracks,
    confirm: h.confirm,
    registerImage: h.registerImage,
    importParticipants: h.importParticipants,
    createOfflineBatch: h.createOfflineBatch,
    acknowledge: vi.fn(),
    detail: h.detail,
    items: h.items,
  },
}));

vi.mock('@bone/ui', () => ({
  // 让受权按钮在测试里全部可见：断言的是「四态暴露哪些动作」，不是权限隐藏。
  AuthButton: ({
    children,
    onClick,
    loading,
    'data-testid': testId,
  }: {
    children?: ReactNode;
    onClick?: () => void;
    loading?: boolean;
    'data-testid'?: string;
  }) => (
    <button type="button" data-testid={testId} disabled={loading} onClick={onClick}>
      {children}
    </button>
  ),
}));

// 捕获 Modal.confirm 配置后手动驱动 onOk：既验证「必须先二次确认」，
// 又能精确断言 onOk 只触发一次真实写调用，不受 antd portal/动画时序影响。
vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>();
  return {
    ...actual,
    // Modal 本身是函数组件，必须保留其可调用性；只覆盖 confirm 静态方法，
    // 若用 Object.assign({}, ...) 会把组件变成普通对象，<Modal> 直接渲染失败。
    Modal: Object.assign(actual.Modal, { confirm: h.confirmModal }),
  };
});

import { CaseSignPage, describeSignLoadError } from './CaseSignPage';

const ok = <T,>(data: T) => ({ code: 200, message: 'ok', success: true, data });
const BATCH_ID = '776102367333449728';
const BATCH_NO = 'SIGN-20261125-0001';

/** App.useApp() 依赖 antd App context；真实入口由 AppAntdProvider + AntdApp 提供。 */
function renderPage(): ReturnType<typeof render> {
  return render(
    <AntdApp>
      <CaseSignPage />
    </AntdApp>,
  );
}

function batch(signStatus: string) {
  return {
    batchId: BATCH_ID,
    batchNo: BATCH_NO,
    source: 'OFFLINE',
    signType: 'MANUAL',
    signChannel: 'EXPRESS',
    signStatus,
    claimCount: 3,
    imageReadyCount: 0,
  };
}

function givenStatus(signStatus: string) {
  h.list.mockResolvedValue(ok({ records: [batch(signStatus)], total: '1', page: 1, size: 20 }));
}

beforeEach(() => {
  vi.clearAllMocks();
  h.confirmModal.mockImplementation(() => ({ destroy: vi.fn(), update: vi.fn() }));
  h.confirm.mockResolvedValue(ok(null));
  h.registerImage.mockResolvedValue(ok(null));
  h.tracks.mockResolvedValue(ok([]));
  h.detail.mockResolvedValue(ok(batch('FINISHED')));
  h.items.mockResolvedValue(ok([]));
});

describe('CaseSignPage · 四态操作入口', () => {
  it.each([
    ['WAIT_FOR_PARTICIPANT', 'sign-import', '导入名单'],
    ['WAIT_FOR_IMAGE', 'sign-image', '登记影像'],
    ['WAIT_FOR_CONFIRMATION', 'sign-confirm', '确认签收'],
  ])('%s 状态只暴露 %s 动作', async (status, testId, label) => {
    givenStatus(status);
    renderPage();

    expect(await screen.findByText(BATCH_NO)).toBeTruthy();
    expect(screen.getByTestId(testId).textContent).toBe(label);
    for (const other of ['sign-import', 'sign-image', 'sign-confirm']) {
      if (other !== testId) expect(screen.queryByTestId(other)).toBeNull();
    }
  });

  it('FINISHED 终态不暴露任何写操作入口', async () => {
    givenStatus('FINISHED');
    renderPage();

    expect(await screen.findByText('已签收确认')).toBeTruthy();
    expect(screen.queryByTestId('sign-import')).toBeNull();
    expect(screen.queryByTestId('sign-image')).toBeNull();
    expect(screen.queryByTestId('sign-confirm')).toBeNull();
  });
});

describe('CaseSignPage · 交互护栏', () => {
  it('确认签收先弹二次确认，onOk 只触发一次写调用', async () => {
    givenStatus('WAIT_FOR_CONFIRMATION');
    renderPage();

    fireEvent.click(await screen.findByTestId('sign-confirm'));

    expect(h.confirmModal).toHaveBeenCalledTimes(1);
    expect(h.confirm).not.toHaveBeenCalled();
    const config = h.confirmModal.mock.calls[0][0];
    expect(config.title).toBe('确认完成签收');
    expect(config.okText).toBe('确认签收');

    await config.onOk();
    expect(h.confirm).toHaveBeenCalledTimes(1);
    expect(h.confirm).toHaveBeenCalledWith(BATCH_ID, '影像齐全，确认完成签收');
  });

  it('签收意见不足 10 字时拦截写调用并保持弹窗打开', async () => {
    givenStatus('WAIT_FOR_CONFIRMATION');
    renderPage();

    const input = await screen.findByPlaceholderText('确认签收意见（不少于10字）');
    fireEvent.change(input, { target: { value: '太短' } });
    fireEvent.click(await screen.findByTestId('sign-confirm'));

    const config = h.confirmModal.mock.calls[0][0];
    expect(await config.onOk()).toBe(false);
    expect(h.confirm).not.toHaveBeenCalled();
  });

  it('切换线上 Tab 立即以 ONLINE 来源重新查询，不与线下串数据', async () => {
    givenStatus('FINISHED');
    renderPage();

    await screen.findByText(BATCH_NO);
    fireEvent.click(screen.getByText('线上签收'));

    await waitFor(() =>
      expect(h.list).toHaveBeenCalledWith({ source: 'ONLINE', page: 1, size: 20 }),
    );
    expect(await screen.findByText('线上签收由系统自动完成')).toBeTruthy();
  });

  it('轨迹抽屉按批次加载并展示真实轨迹内容', async () => {
    givenStatus('FINISHED');
    h.tracks.mockResolvedValue(
      ok([
        {
          id: '1',
          batchId: BATCH_ID,
          actionType: 'CREATE',
          message: '批次创建',
          operator: 'admin',
          occurredAt: '2026-11-25 10:00:00',
        },
      ]),
    );
    renderPage();

    fireEvent.click(await screen.findByTestId('sign-tracks'));

    expect(h.tracks).toHaveBeenCalledWith(BATCH_ID);
    expect(h.detail).toHaveBeenCalledWith(BATCH_ID);
    expect(h.items).toHaveBeenCalledWith(BATCH_ID);
    expect(await screen.findByText('批次创建')).toBeTruthy();
  });
});

describe('CaseSignPage · 可观测性与空态', () => {
  it('404 展示持久化错误态与重试，而不是一闪而过的 toast', async () => {
    h.list.mockRejectedValue({ response: { status: 404 } });
    renderPage();

    const alert = await screen.findByTestId('sign-load-error');
    expect(alert.textContent).toContain('签收服务未在当前环境部署');
    expect(screen.getByTestId('sign-retry')).toBeTruthy();
  });

  it('空态给出新建批次入口，不是干巴巴的暂无数据', async () => {
    h.list.mockResolvedValue(ok({ records: [], total: '0', page: 1, size: 20 }));
    renderPage();

    expect(await screen.findByText('暂无签收批次')).toBeTruthy();
    expect(screen.getByTestId('sign-empty-create')).toBeTruthy();
  });

  it('错误翻译覆盖 404 / 403 / 5xx 三类可操作提示', () => {
    expect(describeSignLoadError({ response: { status: 404 } })).toContain('未在当前环境部署');
    expect(describeSignLoadError({ response: { status: 403 } })).toContain('缺少签收权限');
    expect(describeSignLoadError({ response: { status: 500 } })).toContain('HTTP 500');
  });
});

describe('CaseSignPage · TPA 签收台对齐', () => {
  it('搜索条件走服务端查询参数，不做前端假筛选', async () => {
    givenStatus('FINISHED');
    renderPage();
    await screen.findByText(BATCH_NO);

    fireEvent.change(screen.getByPlaceholderText('请输入批次号'), {
      target: { value: '264203068' },
    });
    fireEvent.click(screen.getByTestId('sign-search'));

    await waitFor(() =>
      expect(h.list).toHaveBeenCalledWith(
        expect.objectContaining({ batchNo: '264203068', source: 'ALL', page: 1 }),
      ),
    );
  });

  it('批次详情一次加载摘要、赔案明细与轨迹', async () => {
    givenStatus('FINISHED');
    h.detail.mockResolvedValue(
      ok({ ...batch('FINISHED'), insuranceCompany: '永诚财险', caseItems: [] }),
    );
    h.items.mockResolvedValue(
      ok([
        {
          itemId: '1',
          claimNo: '264203068004',
          policyNo: 'P-2026-001',
          insuredName: '唐娜',
          identityType: '身份证',
          insuredIdNo: '150426194811037349',
          imageReady: false,
          caseStatus: 'NEW',
        },
      ]),
    );
    renderPage();

    fireEvent.click(await screen.findByTestId('sign-detail'));

    expect(await screen.findByText('264203068004')).toBeTruthy();
    expect(screen.getByText('永诚财险')).toBeTruthy();
    expect(h.detail).toHaveBeenCalledWith(BATCH_ID);
    expect(h.items).toHaveBeenCalledWith(BATCH_ID);
  });
});
