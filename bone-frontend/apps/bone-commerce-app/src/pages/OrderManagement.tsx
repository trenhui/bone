import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  DollarOutlined,
} from '@ant-design/icons';
import { formatDate, normalizeTotal } from '@bone/shared-utils';
import { orderApi, paymentApi } from '../services/api';
import { errMsg } from '../utils/error';
import type {
  CreateOrderReq,
  OrderDetail,
  OrderStatus,
  OrderSummary,
} from '../types';

/** 订单状态 → 中文 / 颜色。与后端 OrderStatus 枚举一一对应，漏一个就会显示原始码。 */
const STATUS_META: Record<OrderStatus, { label: string; color: string }> = {
  CREATED: { label: '已创建', color: 'default' },
  PAID: { label: '已支付', color: 'blue' },
  SHIPPED: { label: '已发货', color: 'cyan' },
  DELIVERED: { label: '已送达', color: 'green' },
  CANCELLED: { label: '已取消', color: 'red' },
  REFUNDED: { label: '已退款', color: 'orange' },
};

/** 来源渠道（后端字典 source_channel）；留空表示未知渠道，服务端允许下单。 */
const CHANNEL_OPTIONS = [
  { value: 'WEB', label: 'WEB（网页）' },
  { value: 'APP', label: 'APP（移动端）' },
  { value: 'MINI', label: 'MINI（小程序）' },
];

interface OrderFormValues {
  customerId: string;
  channelSource?: string;
  items: {
    productId: string;
    productName: string;
    quantity: number;
    unitPrice: number;
  }[];
}

export const OrderManagement: React.FC = () => {
  const navigate = useNavigate();

  const [rows, setRows] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  const [filterCustomerId, setFilterCustomerId] = useState('');
  const [filterStatus, setFilterStatus] = useState<OrderStatus | ''>('');

  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<OrderFormValues>();

  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const res = await orderApi.page({
        pageNum: page,
        pageSize,
        customerId: filterCustomerId || undefined,
        status: filterStatus || undefined,
      });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取订单列表失败'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, filterCustomerId, filterStatus]);

  useEffect(() => {
    void fetchOrders();
  }, [fetchOrders]);

  const openDetail = async (id: string) => {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const res = await orderApi.detail(id);
      setDetail(res.data);
    } catch (error) {
      message.error(errMsg(error, '获取订单详情失败'));
    } finally {
      setDetailLoading(false);
    }
  };

  const runAction = async (
    id: string,
    action: 'ship' | 'deliver' | 'cancel',
    verb: string,
  ) => {
    try {
      if (action === 'ship') await orderApi.ship(id);
      else if (action === 'deliver') await orderApi.deliver(id);
      else await orderApi.cancel(id);
      message.success(`${verb}成功`);
      void fetchOrders();
      if (detail?.id === id) {
        void openDetail(id);
      }
    } catch (error) {
      message.error(errMsg(error, `${verb}失败`));
    }
  };

  const payFromDetail = async (orderId: string) => {
    try {
      const res = await paymentApi.initiate({ orderId });
      const { paymentId, payUrl } = res.data;
      message.success(
        payUrl
          ? `已发起支付，支付单 ${paymentId}；请在支付页打开支付链接完成付款`
          : `已发起支付，支付单 ${paymentId}`,
      );
      void fetchOrders();
      void openDetail(orderId);
    } catch (error) {
      message.error(errMsg(error, '发起支付失败'));
    }
  };

  const submitCreate = async (values: OrderFormValues) => {
    setSubmitting(true);
    try {
      const payload: CreateOrderReq = {
        customerId: values.customerId,
        channelSource: values.channelSource || undefined,
        items: values.items.map((it) => ({
          productId: it.productId,
          productName: it.productName,
          quantity: it.quantity,
          unitPrice: it.unitPrice,
        })),
      };
      const res = await orderApi.create(payload);
      message.success(`下单成功，订单号 ${res.data.id}`);
      setCreateOpen(false);
      form.resetFields();
      setPage(1);
      void fetchOrders();
    } catch (error) {
      message.error(errMsg(error, '下单失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { title: '订单号', dataIndex: 'id', key: 'id', width: 190 },
    { title: '客户ID', dataIndex: 'customerId', key: 'customerId', width: 120 },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 110,
      render: (v: number) => `¥${Number(v).toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: OrderStatus) => (
        <Tag color={STATUS_META[s]?.color ?? 'default'}>
          {STATUS_META[s]?.label ?? s}
        </Tag>
      ),
    },
    {
      title: '来源渠道',
      key: 'channel',
      width: 130,
      render: (_: unknown, r: OrderSummary) =>
        r.channelSourceName || r.channelSource || '—',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      // 走 shared-utils 的 formatDate 而非原生 Date 的本地化字符串方法：后者不受全局
      // locale 与时区控制，且各 JS 引擎输出格式不一致；check.sh 第 8 步 i18n 门禁
      // 会拦截该原生方法，要求统一到 formatDate。
      render: (v: string) => (v ? formatDate(v) : '—'),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_: unknown, r: OrderSummary) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => void openDetail(r.id)}>
            详情
          </Button>
          <Popconfirm
            title="确认发货？"
            description="订单须处于已支付状态，否则服务端会拒绝。"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void runAction(r.id, 'ship', '发货')}
          >
            <Button type="link" size="small" disabled={r.status !== 'PAID'}>
              发货
            </Button>
          </Popconfirm>
          <Popconfirm
            title="确认送达？"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void runAction(r.id, 'deliver', '送达')}
          >
            <Button type="link" size="small" disabled={r.status !== 'SHIPPED'}>
              送达
            </Button>
          </Popconfirm>
          <Popconfirm
            title="确认取消订单？"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void runAction(r.id, 'cancel', '取消')}
          >
            <Button
              type="link"
              size="small"
              danger
              disabled={r.status === 'CANCELLED' || r.status === 'DELIVERED'}
            >
              取消
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Space wrap>
          <Input
            allowClear
            placeholder="客户ID"
            style={{ width: 180 }}
            value={filterCustomerId}
            onChange={(e) => setFilterCustomerId(e.target.value)}
          />
          <Select
            allowClear
            placeholder="订单状态"
            style={{ width: 160 }}
            value={filterStatus || undefined}
            onChange={(v) => setFilterStatus(v ?? '')}
            options={Object.entries(STATUS_META).map(([value, m]) => ({
              value,
              label: m.label,
            }))}
          />
          <Button
            type="primary"
            icon={<SearchOutlined />}
            onClick={() => {
              setPage(1);
              void fetchOrders();
            }}
          >
            查询
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchOrders()}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            // 不在此处 form.resetFields()：此刻 Modal 还没渲染，form 实例未绑定任何
            // Form 元素，antd 会告警「Instance created by useForm is not connected to
            // any Form element」。弹窗已设 destroyOnHidden，每次打开都是全新 Form，
            // 本就无需手动重置。
            onClick={() => setCreateOpen(true)}
          >
            新建订单
          </Button>
        </Space>
      </Card>

      <Card>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={rows}
          columns={columns}
          scroll={{ x: 1100 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </Card>

      <Modal
        title="新建订单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        okText="提交"
        cancelText="取消"
        width={720}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={submitCreate}>
          <Form.Item
            name="customerId"
            label="客户ID"
            rules={[{ required: true, message: '请输入客户ID' }]}
          >
            <Input placeholder="主数据客户编号，如 1001" />
          </Form.Item>
          <Form.Item name="channelSource" label="来源渠道">
            <Select allowClear placeholder="未知渠道可留空" options={CHANNEL_OPTIONS} />
          </Form.Item>

          <Form.List
            name="items"
            initialValue={[
              { productId: '', productName: '', quantity: 1, unitPrice: 1 },
            ]}
          >
            {(fields, { add, remove }) => (
              <>
                {/* 解构出 key 并直接传给 <Space key={...}>：若整体 `{...field}` 展开到
                    Form.Item，React 会因「key 经由 props 展开传入」而告警
                    （实测 "A props object containing a key prop is being spread into JSX"）。 */}
                {fields.map(({ key, name, ...restField }) => (
                  <Space
                    key={key}
                    align="baseline"
                    style={{ display: 'flex', marginBottom: 8 }}
                  >
                    <Form.Item
                      {...restField}
                      name={[name, 'productId']}
                      rules={[{ required: true, message: '商品ID' }]}
                    >
                      <Input placeholder="商品ID" style={{ width: 130 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'productName']}
                      rules={[{ required: true, message: '商品名称' }]}
                    >
                      <Input placeholder="商品名称" style={{ width: 160 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'quantity']}
                      rules={[{ required: true, message: '数量' }]}
                    >
                      <InputNumber min={1} placeholder="数量" style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'unitPrice']}
                      rules={[{ required: true, message: '单价' }]}
                    >
                      <InputNumber
                        min={0.01}
                        step={0.01}
                        placeholder="单价"
                        style={{ width: 110 }}
                      />
                    </Form.Item>
                    <Button
                      type="link"
                      danger
                      disabled={fields.length === 1}
                      onClick={() => remove(name)}
                    >
                      删除
                    </Button>
                  </Space>
                ))}
                <Button
                  type="dashed"
                  block
                  icon={<PlusOutlined />}
                  onClick={() =>
                    add({ productId: '', productName: '', quantity: 1, unitPrice: 1 })
                  }
                >
                  添加订单项
                </Button>
              </>
            )}
          </Form.List>

          {/* 为什么明示「以主数据为准」：服务端忽略客户端单价、按主数据价格 × 客户等级折扣
              计价（防客户端篡改金额）。不让用户以为自己填的单价就是成交价。 */}
          <div style={{ marginTop: 12, color: '#8c8c8c', fontSize: 12 }}>
            提示：服务端以主数据商品价与客户等级折扣计价，此处单价用于下单校验，
            最终以服务端回算金额为准。
          </div>
        </Form>
      </Modal>

      <Drawer
        title={`订单详情${detail ? ` · ${detail.id}` : ''}`}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={720}
      >
        {detail && (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="订单号">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="客户ID">{detail.customerId}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_META[detail.status]?.color ?? 'default'}>
                  {STATUS_META[detail.status]?.label ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="金额">
                ¥{Number(detail.totalAmount).toFixed(2)}
              </Descriptions.Item>
              <Descriptions.Item label="来源渠道">
                {detail.channelSourceName || detail.channelSource || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="支付单号">
                {detail.paymentId ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {formatDate(detail.createdAt)}
              </Descriptions.Item>
            </Descriptions>

            <Table
              // 优先用行 id；缺失时用「商品ID+商品名」兜底（同商品多行是合法数据，
              // 仅用 productId 会撞出「Encountered two children with the same key」）。
              // 不用 rowKey 的 index 参数：antd 已标记其 deprecated 且不保证行为。
              rowKey={(r) => r.id ?? `${r.productId}-${r.productName}`}
              size="small"
              loading={detailLoading}
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                { title: '商品ID', dataIndex: 'productId' },
                { title: '商品名称', dataIndex: 'productName' },
                { title: '数量', dataIndex: 'quantity', width: 70 },
                {
                  title: '单价',
                  dataIndex: 'unitPrice',
                  width: 100,
                  render: (v: number) => `¥${Number(v).toFixed(2)}`,
                },
                {
                  title: '小计',
                  dataIndex: 'subtotal',
                  width: 110,
                  render: (v: number) =>
                    v == null ? '—' : `¥${Number(v).toFixed(2)}`,
                },
              ]}
            />

            <div style={{ marginTop: 16 }}>
              <Button
                type="primary"
                icon={<DollarOutlined />}
                disabled={detail.status !== 'CREATED'}
                onClick={() => void payFromDetail(detail.id)}
              >
                发起支付
              </Button>
              <Button
                style={{ marginLeft: 12 }}
                disabled={!detail.paymentId}
                onClick={() => navigate(`/payments?paymentId=${detail.paymentId}`)}
              >
                查看支付单
              </Button>
              <span style={{ marginLeft: 12, color: '#8c8c8c', fontSize: 12 }}>
                仅「已创建」订单可发起支付；支付结果由渠道异步回调推进，请到支付管理查看。
              </span>
            </div>
          </>
        )}
      </Drawer>
    </div>
  );
};
