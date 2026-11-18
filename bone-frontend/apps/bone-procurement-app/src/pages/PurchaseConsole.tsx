import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, Form, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import { purchaseApi } from '../services/purchaseApi';
import {
  allowedPoEvents,
  allowedPrEvents,
  degradeMessage,
  formatMoney,
  fromApiError,
  safeStateProjection,
} from './purchaseView';
import { useMessage } from '../App';
import type { PurchaseOrderDTO, RequisitionDTO } from '../types/purchase';

const { Title, Text } = Typography;

type AccessLevel = 'readwrite' | 'readonly' | 'unavailable';

/** 采购交易控制台：请购单与采购订单列表、建单、状态推进、详情。所有守卫由后端裁决。 */
export default function PurchaseConsole(): JSX.Element {
  const message = useMessage();
  const [orders, setOrders] = useState<PurchaseOrderDTO[]>([]);
  const [requisitions] = useState<RequisitionDTO[]>([]);
  const [access, setAccess] = useState<AccessLevel>('readwrite');
  const [errorText, setErrorText] = useState<string>('');
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const tenantId = resolveTenant();

  const load = useCallback(async () => {
    try {
      const resp = await purchaseApi.listPurchaseOrders({ page: 1, size: 20 }, tenantId);
      setOrders(resp.data?.records ?? []);
      setAccess('readwrite');
      setErrorText('');
    } catch (err) {
      const code = fromApiError(err);
      if (code === '404') {
        setAccess('unavailable');
        setOrders([]);
      } else if (code === '403') {
        setAccess('readonly');
        setErrorText(degradeMessage('403'));
      } else {
        setErrorText(degradeMessage(code));
      }
    }
  }, [tenantId]);

  useEffect(() => {
    void load();
  }, [load]);

  const advance = useCallback(
    async (po: PurchaseOrderDTO, event: string) => {
      if (access !== 'readwrite' || !po.poNo) return;
      // 迁移失败不本地推进状态：成功后再重新加载后端真值
      try {
        if (event === 'SEND') {
          await purchaseApi.sendPurchaseOrder(po.poNo, tenantId);
        } else {
          await purchaseApi.submitPurchaseOrder(po.poNo, tenantId);
        }
        await load();
      } catch (err) {
        const code = fromApiError(err);
        setErrorText(degradeMessage(code));
        message.error(degradeMessage(code));
      }
    },
    [access, load, message, tenantId],
  );

  const createOrder = useCallback(async () => {
    try {
      const values = await form.validateFields();
      await purchaseApi.createPurchaseOrder({
        tenantId,
        orgId: Number(values.orgId),
        vendorPartyId: values.vendorPartyId,
        legalEntityId: Number(values.legalEntityId),
        declaredAmount: values.declaredAmount,
        taxCodeVersion: values.taxCodeVersion,
        lines: (values.lines ?? []).map((l: Record<string, string>) => ({
          itemId: l.itemId,
          itemName: l.itemName,
          quantity: l.quantity,
          unit: l.unit,
          netPrice: l.netPrice,
          taxRate: l.taxRate ?? '0',
        })),
      });
      setModalOpen(false);
      form.resetFields();
      await load();
    } catch (err) {
      // 失败保留弹窗与字段值：不关闭 modal、不 reset
      const code = fromApiError(err);
      setErrorText(degradeMessage(code));
      message.error(degradeMessage(code));
    }
  }, [form, load, message, tenantId]);

  if (access === 'unavailable') {
    return (
      <Card>
        <Title level={4}>采购交易</Title>
        <Empty description="能力未启用" />
      </Card>
    );
  }

  return (
    <Card>
      <Title level={4}>采购交易</Title>
      {errorText && <Alert type="error" message={errorText} showIcon />}
      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          disabled={access !== 'readwrite'}
          onClick={() => setModalOpen(true)}
        >
          新建采购订单
        </Button>
        <Text type="secondary">权限：{access === 'readonly' ? '只读' : '可写'}</Text>
      </Space>

      <Title level={5}>请购单</Title>
      <Table<RequisitionDTO>
        rowKey="prNo"
        dataSource={requisitions}
        pagination={false}
        size="small"
        columns={[
          { title: '单号', dataIndex: 'prNo' },
          { title: '状态', dataIndex: 'status', render: (s) => <Tag>{safeStateProjection(s)}</Tag> },
          { title: '金额', dataIndex: 'totalAmount', render: (a, r) => formatMoney(a, r.currency) },
          {
            title: '操作',
            key: 'actions',
            render: (_, r) => (
              <Space>
                {allowedPrEvents(r.status).map((e) => (
                  <Button key={e.event} size="small" disabled={access !== 'readwrite'}>
                    {e.label}
                  </Button>
                ))}
              </Space>
            ),
          },
        ]}
      />

      <Title level={5} style={{ marginTop: 24 }}>
        采购订单
      </Title>
      <Table<PurchaseOrderDTO>
        rowKey="poNo"
        dataSource={orders}
        pagination={false}
        size="small"
        columns={[
          { title: '单号', dataIndex: 'poNo' },
          { title: '供应商', dataIndex: 'vendorPartyId' },
          { title: '状态', dataIndex: 'status', render: (s) => <Tag>{safeStateProjection(s)}</Tag> },
          { title: '金额', dataIndex: 'poAmount', render: (a, r) => formatMoney(a, r.currency) },
          {
            title: '操作',
            key: 'actions',
            render: (_, r) => (
              <Space>
                {allowedPoEvents(r.status).map((e) => (
                  <Button
                    key={e.event}
                    size="small"
                    disabled={access !== 'readwrite'}
                    onClick={() => void advance(r, e.event)}
                  >
                    {e.label}
                  </Button>
                ))}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="新建采购订单"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void createOrder()}
        okText="提交"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item label="组织 ID" name="orgId" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="供应商 partyId" name="vendorPartyId" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="法人实体 ID" name="legalEntityId" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="声明总额（可选）" name="declaredAmount">
            <Input />
          </Form.Item>
          <Form.Item label="税码版本" name="taxCodeVersion">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

function resolveTenant(): number {
  const globalCtx = (
    typeof window !== 'undefined'
      ? window
      : undefined
  ) as unknown as { __BONE_GLOBAL_CONTEXT__?: { tenantId?: string | number } } | undefined;
  const n = Number(globalCtx?.__BONE_GLOBAL_CONTEXT__?.tenantId ?? 0);
  return Number.isFinite(n) ? n : 0;
}
