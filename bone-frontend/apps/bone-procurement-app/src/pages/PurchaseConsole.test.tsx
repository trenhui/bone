import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import PurchaseConsole from './PurchaseConsole';
import { purchaseApi } from '../services/purchaseApi';

vi.mock('../services/purchaseApi', () => ({
  purchaseApi: {
    listPurchaseOrders: vi.fn(),
    sendPurchaseOrder: vi.fn(),
    submitPurchaseOrder: vi.fn(),
    createPurchaseOrder: vi.fn(),
    getPurchaseOrder: vi.fn(),
    createRequisition: vi.fn(),
    convertRequisition: vi.fn(),
  },
}));

function renderPage() {
  return render(
    <AntdApp>
      <PurchaseConsole />
    </AntdApp>,
  );
}

describe('PurchaseConsole', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (purchaseApi.listPurchaseOrders as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { records: [], total: 0, page: 1, size: 20 },
    });
  });

  it('404 降级为能力未启用空态', async () => {
    (purchaseApi.listPurchaseOrders as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { status: 404 },
    });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('能力未启用')).toBeTruthy();
    });
  });

  it('403 只读且不显示建单按钮可用性', async () => {
    (purchaseApi.listPurchaseOrders as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { status: 403 },
    });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/只读/)).toBeTruthy();
      expect(screen.getByRole('button', { name: '新建采购订单' })).toHaveProperty('disabled', true);
    });
  });

  it('迁移失败不本地推进状态：send 失败时显示错误且不调用 reload 成功路径', async () => {
    (purchaseApi.listPurchaseOrders as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        data: {
          records: [{ poNo: 'PO-1', status: 'APPROVED', poAmount: '10.0000', currency: 'CNY', vendorPartyId: 'V1' }],
          total: 1,
          page: 1,
          size: 20,
        },
      })
      .mockResolvedValue({
        data: { records: [], total: 0, page: 1, size: 20 },
      });
    (purchaseApi.sendPurchaseOrder as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { data: { errorCode: '31405' } },
    });
    renderPage();
    // antd 对两个汉字的按钮文案会自动插入空格（DOM 为「下 达」），用可访问名 + 容忍空格的匹配
    const sendButton = await screen.findByRole('button', { name: /下\s*达/ });
    fireEvent.click(sendButton);
    await waitFor(() => {
      expect(screen.getAllByText(/缺少价格快照/).length).toBeGreaterThan(0);
    });
    expect(purchaseApi.sendPurchaseOrder).toHaveBeenCalledTimes(1);
    // 失败后不重新拉取后端列表（无 reload 成功路径），且本地不推进状态：行仍为 APPROVED
    expect(purchaseApi.listPurchaseOrders).toHaveBeenCalledTimes(1);
    expect(screen.getByText('APPROVED')).toBeTruthy();
  });

  it('建单失败保留弹窗与字段值', async () => {
    (purchaseApi.createPurchaseOrder as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { data: { errorCode: '31003' } },
    });
    renderPage();
    await waitFor(() => expect(screen.getByRole('button', { name: '新建采购订单' })).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: '新建采购订单' }));
    // 用 dialog 角色判断弹窗打开，避免与按钮同名文案的重复匹配
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeTruthy();
    const vendorInput = screen.getByLabelText('供应商 partyId') as HTMLInputElement;
    fireEvent.change(vendorInput, { target: { value: 'V-KEEP' } });
    fireEvent.change(screen.getByLabelText('组织 ID'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('法人实体 ID'), { target: { value: '99' } });
    // 触发建单并等待后端错误
    fireEvent.click(screen.getByRole('button', { name: '提 交' }));
    await waitFor(() => {
      expect(screen.getAllByText(/供应商交易资格未开通/).length).toBeGreaterThan(0);
    });
    // 失败保留弹窗与字段值：dialog 仍在、输入值未被清空
    expect(screen.getByRole('dialog')).toBeTruthy();
    expect((screen.getByLabelText('供应商 partyId') as HTMLInputElement).value).toBe('V-KEEP');
  });
});
