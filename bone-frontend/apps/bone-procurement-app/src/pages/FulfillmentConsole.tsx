import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { fulfillmentApi } from '../services/fulfillmentApi';
import {
  canCreateAsn,
  canSupplement,
  canWrite,
  degradeMessage,
  fromApiError,
  invoiceStateProjection,
  safeStateProjection,
  type AccessLevel,
} from './fulfillmentView';
import { useMessage } from '../App';
import type {
  FulfillmentViewDTO,
  InvoiceCommandPayload,
  IrExceptionDTO,
} from '../types/fulfillment';

const { Title, Text } = Typography;

/**
 * 履约协同页：待确认 PO / ASN 登记 / 发票提交 / 例外看板四视图。
 * 所有状态推进由后端裁决，前端只按后端返回的快照决定按钮可见性，不本地推进、不做业务判断。
 */
export default function FulfillmentConsole(): JSX.Element {
  const message = useMessage();
  const [access, setAccess] = useState<AccessLevel>('readwrite');
  const [errorText, setErrorText] = useState<string>('');
  const [poNo, setPoNo] = useState<string>('');
  const [view, setView] = useState<FulfillmentViewDTO | null>(null);
  const [exceptions, setExceptions] = useState<IrExceptionDTO[]>([]);
  const [exceptionPage, setExceptionPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [supplementTarget, setSupplementTarget] = useState<string | null>(null);
  const [ackForm] = Form.useForm();
  const [asnForm] = Form.useForm();
  const [invoiceForm] = Form.useForm();
  const [supplementForm] = Form.useForm();
  const tenantId = resolveTenant();

  const handleError = useCallback((err: unknown): string => {
    const code = fromApiError(err);
    if (code === '403') setAccess('readonly');
    if (code === '404') setAccess('unavailable');
    const text = degradeMessage(code);
    setErrorText(text);
    message.error(text);
    return text;
  }, [message]);

  const loadView = useCallback(async () => {
    if (!poNo.trim()) return;
    try {
      const resp = await fulfillmentApi.fulfillmentView(poNo.trim(), tenantId);
      setView(resp.data ?? null);
      setErrorText('');
    } catch (err) {
      handleError(err);
    }
  }, [poNo, tenantId, handleError]);

  const loadExceptions = useCallback(
    async (page: number, append: boolean) => {
      try {
        const resp = await fulfillmentApi.listExceptions(tenantId, page, 20);
        const records = resp.data?.records ?? [];
        const total = resp.data?.total ?? 0;
        setExceptions((prev) => (append ? [...prev, ...records] : records));
        setHasMore(page * 20 < total);
        setExceptionPage(page);
        setErrorText('');
      } catch (err) {
        handleError(err);
      }
    },
    [tenantId, handleError],
  );

  useEffect(() => {
    void loadExceptions(1, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tenantId]);

  const doAck = useCallback(async () => {
    if (!canWrite(access) || !poNo.trim()) return;
    try {
      const values = await ackForm.validateFields();
      await fulfillmentApi.ack(poNo.trim(), {
        tenantId,
        poVersion: Number(values.poVersion),
        ackType: values.ackType,
        lines: [],
      });
      message.success('采购订单已确认');
      ackForm.resetFields();
      await loadView();
    } catch (err) {
      handleError(err);
    }
  }, [access, poNo, tenantId, ackForm, loadView, handleError, message]);

  const doCreateAsn = useCallback(async () => {
    if (!canWrite(access)) return;
    try {
      const values = await asnForm.validateFields();
      const sscc: string[] = values.sscc
        ? String(values.sscc).split(',').map((s: string) => s.trim()).filter(Boolean)
        : [];
      await fulfillmentApi.createAsn({
        tenantId,
        asnNo: values.asnNo,
        poNo: values.poNo,
        shipDate: values.shipDate,
        carrier: values.carrier,
        trackingNo: values.trackingNo,
        sscc,
        lines: [],
      });
      message.success('发货通知已登记');
      asnForm.resetFields();
      await loadExceptions(1, false);
    } catch (err) {
      handleError(err);
    }
  }, [access, tenantId, asnForm, loadExceptions, handleError, message]);

  const doSubmitInvoice = useCallback(async () => {
    if (!canWrite(access)) return;
    try {
      const values = await invoiceForm.validateFields();
      const payload: InvoiceCommandPayload = {
        tenantId,
        invoiceNo: values.invoiceNo,
        poNo: values.poNo,
        amount: values.amount,
        tax: values.tax,
        currency: values.currency,
        category: values.category,
        lines: [],
      };
      await fulfillmentApi.submitInvoice(payload);
      message.success('发票已提交，等待财务匹配');
      invoiceForm.resetFields();
      await loadExceptions(1, false);
    } catch (err) {
      // 失败不 reset：保留表单输入，仅展示可读错误（31504 场景）
      handleError(err);
    }
  }, [access, tenantId, invoiceForm, loadExceptions, handleError, message]);

  const openSupplement = useCallback((no: string) => setSupplementTarget(no), []);

  const doSupplement = useCallback(async () => {
    if (!canWrite(access) || !supplementTarget) return;
    try {
      const values = await supplementForm.validateFields();
      await fulfillmentApi.supplement(supplementTarget, {
        tenantId,
        supplementRef: values.supplementRef,
      });
      message.success('补料已提交');
      supplementForm.resetFields();
      setSupplementTarget(null);
      await loadExceptions(1, false);
    } catch (err) {
      handleError(err);
    }
  }, [access, supplementTarget, supplementForm, tenantId, loadExceptions, handleError, message]);

  if (access === 'unavailable') {
    return (
      <Card>
        <Title level={4}>履约协同</Title>
        <Empty description="能力未启用" />
      </Card>
    );
  }

  const acks = view?.acks ?? [];
  const asns = view?.asns ?? [];
  const invoices = view?.invoices ?? [];
  const acknowledged = view?.acknowledged === true || acks.length > 0;

  return (
    <Card>
      <Title level={4}>履约协同</Title>
      {errorText && <Alert type="error" message={errorText} showIcon />}
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="采购订单号"
          value={poNo}
          onChange={(e) => setPoNo(e.target.value)}
          style={{ width: 220 }}
        />
        <Button onClick={() => void loadView()}>查询履约</Button>
        <Text type="secondary">权限：{canWrite(access) ? '可写' : '只读'}</Text>
      </Space>

      <Card type="inner" title="待确认 PO" size="small">
        <Text type="secondary">
          确认状态：{view ? (acknowledged ? '已确认' : '待确认') : '尚未查询'}
        </Text>
        {canWrite(access) && !acknowledged && poNo.trim() && (
          <Form form={ackForm} layout="inline" style={{ marginTop: 12 }}>
            <Form.Item name="poVersion" initialValue={1}>
              <InputNumber placeholder="PO 版本" min={1} />
            </Form.Item>
            <Form.Item name="ackType" initialValue="FULL">
              <Select
                options={[
                  { value: 'FULL', label: '全部确认' },
                  { value: 'PARTIAL', label: '部分确认' },
                  { value: 'REJECT', label: '拒收' },
                ]}
              />
            </Form.Item>
            <Button type="primary" onClick={() => void doAck()}>
              确认订单
            </Button>
          </Form>
        )}
        <Table
          rowKey={(r, i) => r.poNo ?? String(i)}
          dataSource={acks}
          pagination={false}
          size="small"
          style={{ marginTop: 12 }}
          columns={[
            { title: '单号', dataIndex: 'poNo' },
            { title: '版本', dataIndex: 'poVersion' },
            { title: '确认类型', dataIndex: 'ackType' },
          ]}
        />
      </Card>

      <Card type="inner" title="ASN 登记" size="small" style={{ marginTop: 16 }}>
        {!canCreateAsn(view?.acknowledged) && view ? (
          <Empty description="采购订单未确认，不能登记发货通知" />
        ) : (
          <Form form={asnForm} layout="vertical">
            <Space>
              <Form.Item label="ASN 单号" name="asnNo" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item label="采购订单号" name="poNo" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item label="承运商" name="carrier">
                <Input />
              </Form.Item>
            </Space>
            {canWrite(access) && (
              <Button type="primary" onClick={() => void doCreateAsn()}>
                登记发货
              </Button>
            )}
          </Form>
        )}
        <Table
          rowKey={(r, i) => r.asnNo ?? String(i)}
          dataSource={asns}
          pagination={false}
          size="small"
          style={{ marginTop: 12 }}
          columns={[
            { title: 'ASN', dataIndex: 'asnNo' },
            { title: 'PO', dataIndex: 'poNo' },
            { title: '状态', dataIndex: 'status', render: (s) => <Tag>{safeStateProjection(s)}</Tag> },
          ]}
        />
      </Card>

      <Card type="inner" title="发票提交" size="small" style={{ marginTop: 16 }}>
        <Form form={invoiceForm} layout="vertical">
          <Space wrap>
            <Form.Item label="发票号" name="invoiceNo" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item label="采购订单号" name="poNo">
              <Input />
            </Form.Item>
            <Form.Item label="金额" name="amount" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item label="币种" name="currency" initialValue="CNY">
              <Input />
            </Form.Item>
            <Form.Item label="品类" name="category" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </Space>
          {canWrite(access) && (
            <Button type="primary" onClick={() => void doSubmitInvoice()}>
              提交发票
            </Button>
          )}
        </Form>
        <Table
          rowKey={(r, i) => r.invoiceNo ?? String(i)}
          dataSource={invoices}
          pagination={false}
          size="small"
          style={{ marginTop: 12 }}
          columns={[
            { title: '发票号', dataIndex: 'invoiceNo' },
            { title: '金额', dataIndex: 'amount' },
            {
              title: '状态',
              dataIndex: 'status',
              render: (s) => <Tag>{invoiceStateProjection(s)}</Tag>,
            },
          ]}
        />
      </Card>

      <Card type="inner" title="例外看板" size="small" style={{ marginTop: 16 }}>
        <Table
          rowKey={(r, i) => r.exceptionNo ?? String(i)}
          dataSource={exceptions}
          pagination={false}
          size="small"
          columns={[
            { title: '例外号', dataIndex: 'exceptionNo' },
            { title: '发票号', dataIndex: 'invoiceNo' },
            { title: '例外码', dataIndex: 'exceptionCode' },
            { title: '匹配态', dataIndex: 'matchStatus', render: (s) => safeStateProjection(s) },
            {
              title: '状态',
              dataIndex: 'status',
              render: (s) => <Tag>{safeStateProjection(s)}</Tag>,
            },
            {
              title: '操作',
              key: 'actions',
              render: (_, r) =>
                canWrite(access) && canSupplement(r.status) ? (
                  <Button size="small" onClick={() => openSupplement(r.exceptionNo ?? '')}>
                    补料
                  </Button>
                ) : null,
            },
          ]}
        />
        {hasMore && (
          <Button style={{ marginTop: 8 }} onClick={() => void loadExceptions(exceptionPage + 1, true)}>
            加载更多
          </Button>
        )}
      </Card>

      <Modal
        title="例外补料"
        open={supplementTarget !== null}
        onCancel={() => setSupplementTarget(null)}
        onOk={() => void doSupplement()}
        okText="提交"
        cancelText="取消"
      >
        <Form form={supplementForm} layout="vertical">
          <Form.Item label="补料引用" name="supplementRef" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

function resolveTenant(): number {
  const globalCtx = (
    typeof window !== 'undefined' ? window : undefined
  ) as unknown as { __BONE_GLOBAL_CONTEXT__?: { tenantId?: string | number } } | undefined;
  const n = Number(globalCtx?.__BONE_GLOBAL_CONTEXT__?.tenantId ?? 0);
  return Number.isFinite(n) ? n : 0;
}
