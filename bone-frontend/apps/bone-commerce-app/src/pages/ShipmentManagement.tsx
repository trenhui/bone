import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Timeline,
  message,
} from 'antd';
import { CarOutlined, ReloadOutlined } from '@ant-design/icons';
import { formatDate, normalizeTotal } from '@bone/shared-utils';
import { shipmentApi } from '../services/api';
import { errMsg } from '../utils/error';
import type { ShipmentSummary, ShipmentTrace } from '../types';
import { CHANNEL_META, SHIPMENT_STATUS_META } from './channelConstants';

/**
 * 发货物流管理。
 *
 * <b>「已发货但渠道未同步」的展示</b>：渠道回传失败时后端保留 SHIPPED 状态
 * （货已经发出，回退状态会制造未发货假象），仅在 channelAck=false 上体现。
 * 因此这里把 channelAck 单独成一列——它是补偿重试的入口信号，不能和状态混在一起。
 */
export const ShipmentManagement: React.FC = () => {
  const [rows, setRows] = useState<ShipmentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [filterChannel, setFilterChannel] = useState<string>('');
  const [filterStatus, setFilterStatus] = useState<string>('');

  const [createOpen, setCreateOpen] = useState(false);
  const [shipTarget, setShipTarget] = useState<ShipmentSummary | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [createForm] = Form.useForm();
  const [shipForm] = Form.useForm();

  const [traceOpen, setTraceOpen] = useState(false);
  const [traces, setTraces] = useState<ShipmentTrace[]>([]);
  const [traceLoading, setTraceLoading] = useState(false);

  const fetchRows = useCallback(async () => {
    setLoading(true);
    try {
      const res = await shipmentApi.page({
        page,
        size: pageSize,
        channelCode: filterChannel || undefined,
        status: filterStatus || undefined,
      });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取发货单列表失败'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, filterChannel, filterStatus]);

  useEffect(() => {
    void fetchRows();
  }, [fetchRows]);

  const submitCreate = async (values: {
    orderId: string;
    channelCode?: string;
    receiverName?: string;
    receiverPhone?: string;
    receiverAddress?: string;
  }) => {
    setSubmitting(true);
    try {
      const res = await shipmentApi.create({
        orderId: values.orderId,
        channelCode: values.channelCode,
        receiverName: values.receiverName,
        receiverPhone: values.receiverPhone,
        receiverAddress: values.receiverAddress,
      });
      message.success(`发货单已创建：${res.data.shipmentNo}`);
      setCreateOpen(false);
      createForm.resetFields();
      setPage(1);
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '创建发货单失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const submitShip = async (values: { logisticsCompany?: string; trackingNo: string }) => {
    if (!shipTarget) {
      return;
    }
    setSubmitting(true);
    try {
      await shipmentApi.ship(shipTarget.id, {
        logisticsCompany: values.logisticsCompany,
        trackingNo: values.trackingNo,
      });
      message.success('已发货并回传渠道');
      setShipTarget(null);
      shipForm.resetFields();
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '发货失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const runSign = async (row: ShipmentSummary) => {
    try {
      await shipmentApi.sign(row.id);
      message.success('已签收');
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '签收失败'));
    }
  };

  const openTraces = async (row: ShipmentSummary) => {
    setTraceOpen(true);
    setTraceLoading(true);
    try {
      // 先从渠道拉取最新轨迹落库，再读本地列表：保证客服看到的是最新进度
      await shipmentApi.syncTrace(row.id).catch(() => undefined);
      const res = await shipmentApi.traces(row.id);
      setTraces(res.data ?? []);
    } catch (error) {
      message.error(errMsg(error, '获取物流轨迹失败'));
    } finally {
      setTraceLoading(false);
    }
  };

  const columns = [
    { title: '发货单号', dataIndex: 'shipmentNo', key: 'shipmentNo', width: 200 },
    { title: '订单ID', dataIndex: 'orderId', key: 'orderId', width: 170 },
    {
      title: '渠道',
      dataIndex: 'channelCode',
      key: 'channelCode',
      width: 100,
      render: (code: string | null) =>
        code ? (
          <Tag color={CHANNEL_META[code]?.color ?? 'default'}>
            {CHANNEL_META[code]?.label ?? code}
          </Tag>
        ) : (
          '—'
        ),
    },
    { title: '物流公司', dataIndex: 'logisticsCompany', key: 'logisticsCompany', width: 120 },
    {
      title: '运单号',
      dataIndex: 'trackingNo',
      key: 'trackingNo',
      width: 160,
      render: (v: string | null) => v || '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: string) => (
        <Tag color={SHIPMENT_STATUS_META[s]?.color ?? 'default'}>
          {SHIPMENT_STATUS_META[s]?.label ?? s}
        </Tag>
      ),
    },
    {
      title: '渠道回传',
      dataIndex: 'channelAck',
      key: 'channelAck',
      width: 110,
      render: (v: boolean, r: ShipmentSummary) =>
        r.channelCode ? (
          <Tag color={v ? 'success' : 'warning'}>{v ? '已回传' : '未回传'}</Tag>
        ) : (
          '—'
        ),
    },
    {
      title: '发货时间',
      dataIndex: 'shippedAt',
      key: 'shippedAt',
      width: 180,
      render: (v: string | null) => (v ? formatDate(v) : '—'),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_: unknown, r: ShipmentSummary) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            disabled={r.status !== 'CREATED' && r.status !== 'FAILED'}
            onClick={() => {
              setShipTarget(r);
              shipForm.resetFields();
            }}
          >
            发货
          </Button>
          <Popconfirm
            title="确认签收？"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void runSign(r)}
          >
            <Button
              type="link"
              size="small"
              disabled={r.status !== 'SHIPPED' && r.status !== 'IN_TRANSIT'}
            >
              签收
            </Button>
          </Popconfirm>
          <Button type="link" size="small" onClick={() => void openTraces(r)}>
            轨迹
          </Button>
        </Space>
      ),
    },
  ];

  const statusSteps = traces.length > 0;

  return (
    <Card
      title="发货物流"
      extra={
        <Space>
          <Select
            allowClear
            placeholder="渠道筛选"
            style={{ width: 130 }}
            value={filterChannel || undefined}
            onChange={(v) => {
              setFilterChannel(v ?? '');
              setPage(1);
            }}
            options={Object.keys(CHANNEL_META).map((c) => ({
              value: c,
              label: CHANNEL_META[c].label,
            }))}
          />
          <Select
            allowClear
            placeholder="状态筛选"
            style={{ width: 130 }}
            value={filterStatus || undefined}
            onChange={(v) => {
              setFilterStatus(v ?? '');
              setPage(1);
            }}
            options={Object.keys(SHIPMENT_STATUS_META).map((s) => ({
              value: s,
              label: SHIPMENT_STATUS_META[s].label,
            }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void fetchRows()}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<CarOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            新建发货单
          </Button>
        </Space>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, s) => {
            setPage(p);
            setPageSize(s);
          },
        }}
      />

      <Modal
        title="新建发货单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={(v) => void submitCreate(v)}>
          <Form.Item
            name="orderId"
            label="订单ID"
            rules={[{ required: true, message: '请输入订单ID' }]}
          >
            <Input placeholder="雪花 ID 字符串" />
          </Form.Item>
          <Form.Item name="channelCode" label="渠道">
            <Select
              allowClear
              options={Object.keys(CHANNEL_META).map((c) => ({
                value: c,
                label: CHANNEL_META[c].label,
              }))}
            />
          </Form.Item>
          <Form.Item name="receiverName" label="收货人">
            <Input />
          </Form.Item>
          <Form.Item name="receiverPhone" label="收货电话">
            <Input />
          </Form.Item>
          <Form.Item name="receiverAddress" label="收货地址">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setCreateOpen(false)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                创建
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="发货（生成运单号并回传渠道）"
        open={shipTarget !== null}
        onCancel={() => setShipTarget(null)}
        footer={null}
        destroyOnClose
      >
        <Form form={shipForm} layout="vertical" onFinish={(v) => void submitShip(v)}>
          <Form.Item name="logisticsCompany" label="物流公司" initialValue="顺丰速运">
            <Select
              options={[
                { value: '顺丰速运', label: '顺丰速运' },
                { value: '中通快递', label: '中通快递' },
                { value: '圆通速递', label: '圆通速递' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="trackingNo"
            label="运单号"
            rules={[{ required: true, message: '运单号不能为空' }]}
            extra="无运单号的「已发货」是虚假发货，服务端会拒绝。"
          >
            <Input placeholder="如 SF1234567890" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setShipTarget(null)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                确认发货
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="物流轨迹"
        width={480}
        open={traceOpen}
        onClose={() => setTraceOpen(false)}
      >
        {traceLoading ? (
          <div>加载中…</div>
        ) : statusSteps ? (
          <Timeline
            items={traces.map((t) => ({
              children: (
                <div>
                  <div style={{ fontWeight: 600 }}>{t.traceDesc}</div>
                  <div style={{ opacity: 0.6, fontSize: 12 }}>
                    {formatDate(t.traceTime)} · {t.traceStatus ?? '—'}
                  </div>
                </div>
              ),
            }))}
          />
        ) : (
          <Steps
            direction="vertical"
            size="small"
            current={0}
            items={[{ title: '暂无轨迹', description: '发货并同步后可见' }]}
          />
        )}
      </Drawer>
    </Card>
  );
};
