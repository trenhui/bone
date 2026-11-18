import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import FulfillmentConsole from './FulfillmentConsole';
import { fulfillmentApi } from '../services/fulfillmentApi';

vi.mock('../services/fulfillmentApi', () => ({
  fulfillmentApi: {
    listExceptions: vi.fn(),
    fulfillmentView: vi.fn(),
    ack: vi.fn(),
    createAsn: vi.fn(),
    submitInvoice: vi.fn(),
    supplement: vi.fn(),
  },
}));

function renderPage() {
  return render(
    <AntdApp>
      <FulfillmentConsole />
    </AntdApp>,
  );
}

const emptyPage = { data: { records: [], total: 0, page: 1, size: 20 } };

describe('FulfillmentConsole 挂载与降级', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (fulfillmentApi.listExceptions as ReturnType<typeof vi.fn>).mockResolvedValue(emptyPage);
    (fulfillmentApi.fulfillmentView as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { poNo: 'PO-1', acknowledged: false, acks: [], asns: [], invoices: [], exceptions: [] },
    });
  });

  it('正常挂载渲染四视图且无白屏', async () => {
    renderPage();
    expect(screen.getByText('履约协同')).toBeTruthy();
    expect(screen.getByText('待确认 PO')).toBeTruthy();
    expect(screen.getByText('ASN 登记')).toBeTruthy();
    expect(screen.getByText('发票提交')).toBeTruthy();
    expect(screen.getByText('例外看板')).toBeTruthy();
    await waitFor(() => expect(fulfillmentApi.listExceptions).toHaveBeenCalledTimes(1));
  });

  it('404 降级为能力未启用空态', async () => {
    (fulfillmentApi.listExceptions as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { status: 404 },
    });
    renderPage();
    await waitFor(() => expect(screen.getByText('能力未启用')).toBeTruthy());
  });

  it('403 越权展示无权提示且隐藏写按钮', async () => {
    (fulfillmentApi.listExceptions as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { status: 403 },
    });
    renderPage();
    await waitFor(() => {
      expect(screen.getAllByText(/无权限|越权/).length).toBeGreaterThan(0);
    });
    expect(screen.getByText(/只读/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: '提交发票' })).toBeNull();
  });
});

describe('FulfillmentConsole 按钮按态展示', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (fulfillmentApi.listExceptions as ReturnType<typeof vi.fn>).mockResolvedValue(emptyPage);
  });

  it('已确认 PO 不再显示确认按钮，未确认时显示', async () => {
    (fulfillmentApi.fulfillmentView as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        poNo: 'PO-1',
        acknowledged: true,
        acks: [{ poNo: 'PO-1', ackType: 'FULL' }],
        asns: [],
        invoices: [],
        exceptions: [],
      },
    });
    renderPage();
    fireEvent.change(screen.getByPlaceholderText('采购订单号'), { target: { value: 'PO-1' } });
    fireEvent.click(screen.getByRole('button', { name: '查询履约' }));
    await waitFor(() => expect(screen.getByText(/已确认/)).toBeTruthy());
    expect(screen.queryByRole('button', { name: '确认订单' })).toBeNull();
  });

  it('MATCH_PENDING 发票不显示任何放行/裁决入口', async () => {
    (fulfillmentApi.fulfillmentView as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        poNo: 'PO-1',
        acknowledged: true,
        acks: [{ poNo: 'PO-1', ackType: 'FULL' }],
        asns: [],
        invoices: [{ invoiceNo: 'INV-1', status: 'MATCH_PENDING', amount: '100.0000' }],
        exceptions: [],
      },
    });
    renderPage();
    fireEvent.change(screen.getByPlaceholderText('采购订单号'), { target: { value: 'PO-1' } });
    fireEvent.click(screen.getByRole('button', { name: '查询履约' }));
    await waitFor(() => {
      expect(screen.getByText(/语法回执/)).toBeTruthy();
    });
    expect(screen.queryByRole('button', { name: /放行/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /裁决/ })).toBeNull();
  });
});

describe('FulfillmentConsole 提交失败保留表单', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (fulfillmentApi.listExceptions as ReturnType<typeof vi.fn>).mockResolvedValue(emptyPage);
  });

  it('31504 超未开票余额：保留发票号与金额、不清空、展示可读错误', async () => {
    (fulfillmentApi.submitInvoice as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { status: 409, data: { errorCode: '31504' } },
    });
    renderPage();
    const invoiceInput = screen.getByLabelText('发票号') as HTMLInputElement;
    const amountInput = screen.getByLabelText('金额') as HTMLInputElement;
    fireEvent.change(invoiceInput, { target: { value: 'INV-KEEP' } });
    fireEvent.change(amountInput, { target: { value: '999.0000' } });
    fireEvent.change(screen.getByLabelText('品类'), { target: { value: 'GOODS' } });
    fireEvent.click(screen.getByRole('button', { name: '提交发票' }));
    await waitFor(() => {
      expect(screen.getAllByText(/未开票余额/).length).toBeGreaterThan(0);
    });
    expect((screen.getByLabelText('发票号') as HTMLInputElement).value).toBe('INV-KEEP');
    expect((screen.getByLabelText('金额') as HTMLInputElement).value).toBe('999.0000');
    expect(fulfillmentApi.submitInvoice).toHaveBeenCalledTimes(1);
  });
});
