import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 供应商治理页面级回归测试（UI 层）。
 *
 * 背景：Build 阶段独立审查 IMPORTANT-1 指出——页面把失败语义收敛到
 * `try/catch → fromApiError → degradeMessage`，但只有纯函数测试（supplierView.test.ts）
 * 与 API 契约测试（supplierApi.test.ts），**页面本身**是否正确调用这条链、失败时是否
 * 不推进本地状态，零测试保护，可被无声回归。本文件用组件渲染把这三个失败形态钉住：
 *   1) 列表加载 404 → 降级空态 + message.error（能力未启用），而非空白/崩溃；
 *   2) 登记失败 → message.error，弹窗保持打开、表单不清空（可重试）；
 *   3) 迁移失败 → message.error，且本地状态不推进（不谎报成功）。
 *
 * 只 mock 网络层与 message/权限按钮，页面真实渲染，assert 的是用户可见行为。
 */

// 统一 message API：捕获 error/success/warning 调用
const messageSpy = {
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
  loading: vi.fn(),
  open: vi.fn(),
  destroy: vi.fn(),
};

vi.mock('../App', () => ({
  useMessage: () => messageSpy,
}));

vi.mock('../services/supplierApi', () => ({
  supplierApi: {
    register: vi.fn(),
    transitionSupplier: vi.fn(),
    transitionVendor: vi.fn(),
    current: vi.fn(),
    page: vi.fn(),
  },
  resolveTenantId: vi.fn(() => 1),
}));

// AuthButton 保留按钮语义但去掉权限上下文依赖，避免测试被权限体系阻断
vi.mock('@bone/ui', () => ({
  AuthButton: ({ children, onClick, ...rest }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button onClick={onClick} {...stripCode(rest)}>
      {children}
    </button>
  ),
}));

// 剔除自定义权限 prop（code），避免 React 未知属性告警
function stripCode(rest: Record<string, unknown>) {
  const copy = { ...rest };
  delete copy.code;
  return copy;
}

import SupplierManagement from './SupplierManagement';
import { supplierApi } from '../services/supplierApi';

const api = vi.mocked(supplierApi);

/** apiClient 对 HTTP 失败的 reject 形态（axios Error with response.status） */
function httpError(status: number, message = 'Request failed') {
  return { response: { status, data: { message } }, message };
}

/** 业务失败（HTTP 200 + success:false）被拦截器转成的 Error */
function businessError(displayMessage: string) {
  return { displayMessage, message: displayMessage };
}

const emptyPage = { success: true, code: 0, message: 'ok', data: { records: [], total: 0, page: 1, size: 10 } };

const sampleState = {
  partyId: 'P-1001',
  supplierState: 'ACTIVE',
  supplierVersion: 3,
  vendorState: 'PAYMENT_BLOCKED',
  vendorVersion: 5,
  supplierHistory: ['PROSPECT->ACTIVE (onboarding_approved)'],
  vendorHistory: ['ACTIVE_VENDOR->PAYMENT_BLOCKED (payment_block_raised)'],
};

const onePage = {
  success: true,
  code: 0,
  message: 'ok',
  data: { records: [sampleState], total: '1', page: 1, size: 10 },
};

/**
 * antd `size="small"` 按钮会在中文字符间插入空格（如「查看」渲染为「查 看」），
 * 故按包含匹配而非精确 accessible name，避免测试被样式细节误伤。
 */
function buttonByText(text: string): HTMLElement {
  const normalized = (s: string) => s.replace(/\s+/g, '');
  const btn = screen
    .getAllByRole('button')
    .find((b) => normalized(b.textContent ?? '').includes(normalized(text)));
  if (!btn) throw new Error(`未找到按钮：${text}`);
  return btn;
}

describe('SupplierManagement 页面失败路径（UI 回归保护）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.page.mockResolvedValue(emptyPage as never);
    api.current.mockResolvedValue({ success: true, code: 0, message: 'ok', data: null } as never);
  });

  it('列表加载返回 404 → 显示能力未启用降级空态，并调用 message.error', async () => {
    api.page.mockRejectedValueOnce(httpError(404, 'No handler for POST'));

    render(<SupplierManagement />);

    await waitFor(() => {
      expect(messageSpy.error).toHaveBeenCalledWith(
        '供应商能力未启用（后端能力开关未打开或端口未就绪）',
      );
    });
    // 降级空态可见，且不是空白页
    expect(await screen.findByText('供应商能力未启用（后端能力开关未打开或端口未就绪）')).not.toBeNull();
  });

  it('列表加载返回 403 → 显示无权限降级文案', async () => {
    api.page.mockRejectedValueOnce(httpError(403, 'Forbidden'));

    render(<SupplierManagement />);

    await waitFor(() => {
      expect(messageSpy.error).toHaveBeenCalledWith('无权限：需要供应商治理读写权限');
    });
    expect(await screen.findByText('无权限：需要供应商治理读写权限')).not.toBeNull();
  });

  it('详情加载业务失败 → message.error 展示后端原文，页面不崩溃', async () => {
    api.page.mockResolvedValue(onePage as never);
    api.current.mockRejectedValueOnce(businessError('身份冲突，需人工复核'));

    render(<SupplierManagement />);
    // 等列表行渲染出「查看」入口
    await waitFor(() => expect(screen.getByText('P-1001')).not.toBeNull());
    fireEvent.click(buttonByText('查看'));

    await waitFor(() => {
      expect(messageSpy.error).toHaveBeenCalledWith('身份冲突，需人工复核');
    });
    // 无当前选中态时不臆造状态卡片
    expect(screen.getByText('请先查询供应商')).not.toBeNull();
  });

  it('登记失败 → message.error，弹窗保持打开、表单值不清空（可重试）', async () => {
    api.register.mockRejectedValueOnce(businessError('身份冲突，登记未成立'));

    render(<SupplierManagement />);
    await waitFor(() => expect(api.page).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: '登记供应商' }));
    const mergeInput = await screen.findByPlaceholderText('如 merge-2026-001');
    fireEvent.change(mergeInput, { target: { value: 'merge-retry-1' } });
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }));

    await waitFor(() => {
      expect(messageSpy.error).toHaveBeenCalledWith('身份冲突，登记未成立');
    });
    // 失败后弹窗仍在、输入值保留——这是用户能就地重试的前提
    expect((screen.getByPlaceholderText('如 merge-2026-001') as HTMLInputElement).value).toBe('merge-retry-1');
    expect(messageSpy.success).not.toHaveBeenCalled();
  });

  it('迁移失败 → message.error，本地状态不推进（不谎报成功）', async () => {
    api.page.mockResolvedValue(onePage as never);
    api.current.mockResolvedValue({ success: true, code: 0, message: 'ok', data: sampleState } as never);
    api.transitionVendor.mockRejectedValueOnce(businessError('守卫拒绝：付款冻结需财务复核来源'));

    render(<SupplierManagement />);
    await waitFor(() => expect(screen.getByText('P-1001')).not.toBeNull());

    fireEvent.click(buttonByText('查看'));
    // 等详情真正加载：列表 tag 也有 PAYMENT_BLOCKED，但「解除冻结」按钮只在详情态渲染，
    // 以它作为 current 已就绪的信号，避免列表先渲染导致的竞态误点。
    await waitFor(() => expect(buttonByText('解除冻结（需财务复核）')).toBeTruthy());
    fireEvent.click(buttonByText('解除冻结（需财务复核）'));

    await waitFor(() => {
      expect(messageSpy.error).toHaveBeenCalledWith('守卫拒绝：付款冻结需财务复核来源');
    });
    // 失败不本地推进：没有成功提示，也没有乐观更新为 ACTIVE_VENDOR
    expect(messageSpy.success).not.toHaveBeenCalled();
    expect(screen.queryByText('ACTIVE_VENDOR')).toBeNull();
  });
});
