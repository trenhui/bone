import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import { CloudDownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { formatDate, normalizeTotal } from '@bone/shared-utils';
import { channelApi, channelOrderApi } from '../services/api';
import { errMsg } from '../utils/error';
import type { ChannelSummary, PullChannelOrderReq } from '../types';
import { CHANNEL_META, CHANNEL_SKU_PREFIX } from './channelConstants';

/**
 * 渠道管理 + 渠道订单接入。
 *
 * <b>为何把「拉单」放在渠道页</b>：拉单的输入是「渠道 + 渠道原始订单号 + 渠道 SKU」，
 * 这三样都以渠道为前提；放在订单页会让用户先选渠道再跳走，操作链路断裂。
 */
export const ChannelManagement: React.FC = () => {
  const [rows, setRows] = useState<ChannelSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  const [pullOpen, setPullOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchChannels = useCallback(async () => {
    setLoading(true);
    try {
      const res = await channelApi.page({ page, size: pageSize });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取渠道列表失败'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    void fetchChannels();
  }, [fetchChannels]);

  const toggleEnabled = async (row: ChannelSummary, enabled: boolean) => {
    try {
      await (enabled ? channelApi.enable(row.channelCode) : channelApi.disable(row.channelCode));
      message.success(enabled ? '渠道已启用' : '渠道已停用');
      void fetchChannels();
    } catch (error) {
      message.error(errMsg(error, enabled ? '启用失败' : '停用失败'));
    }
  };

  const toggleSync = async (row: ChannelSummary, checked: boolean) => {
    try {
      await channelApi.setOrderSync(row.channelCode, checked);
      message.success(checked ? '已开启订单自动同步' : '已关闭订单自动同步');
      void fetchChannels();
    } catch (error) {
      message.error(errMsg(error, '设置失败'));
    }
  };

  const submitPull = async (values: {
    channelCode: string;
    channelOrderNo: string;
    buyerNick?: string;
    productId: string;
    quantity: number;
    unitPrice: number;
  }) => {
    setSubmitting(true);
    try {
      const payload: PullChannelOrderReq = {
        channelCode: values.channelCode,
        channelOrderNo: values.channelOrderNo,
        buyerNick: values.buyerNick,
        receiverName: '渠道买家',
        receiverPhone: '13800000000',
        receiverAddress: '渠道回传地址',
        lines: [
          {
            // 渠道 SKU 编码 = 渠道前缀 + 内部商品ID，由渠道扩展实现解析还原
            outerSkuId: `${CHANNEL_SKU_PREFIX[values.channelCode] ?? ''}${values.productId}`,
            title: '渠道商品',
            quantity: values.quantity,
            unitPrice: values.unitPrice,
          },
        ],
      };
      const res = await channelOrderApi.pull(payload);
      message.success(`渠道订单落单成功，订单号 ${res.data}`);
      setPullOpen(false);
      form.resetFields();
    } catch (error) {
      message.error(errMsg(error, '渠道订单落单失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: '渠道',
      dataIndex: 'channelCode',
      key: 'channelCode',
      width: 120,
      render: (code: string) => (
        <Tag color={CHANNEL_META[code]?.color ?? 'default'}>
          {CHANNEL_META[code]?.label ?? code}
        </Tag>
      ),
    },
    { title: '渠道码', dataIndex: 'channelCode', key: 'code', width: 110 },
    {
      title: '接入地址',
      dataIndex: 'apiEndpoint',
      key: 'apiEndpoint',
      ellipsis: true,
      render: (v: string | null) => v || '—',
    },
    {
      title: '命中扩展实现',
      dataIndex: 'extImplCode',
      key: 'extImplCode',
      width: 220,
      // 该字段由扩展点路由结果回填：为空说明该渠道尚未被路由过，
      // 非空则可用于确认「这次请求究竟命中了哪个实现」——排障第一手证据。
      render: (v: string | null) => v || <span style={{ opacity: 0.45 }}>未路由</span>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (v: boolean) => (
        <Tag color={v ? 'success' : 'default'}>{v ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '最近同步',
      dataIndex: 'lastSyncAt',
      key: 'lastSyncAt',
      width: 180,
      render: (v: string | null) => (v ? formatDate(v) : '—'),
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_: unknown, r: ChannelSummary) => (
        <Space size="small">
          <Popconfirm
            title={r.enabled ? '确认停用该渠道？' : '确认启用该渠道？'}
            description={
              r.enabled ? '停用后该渠道不再参与拉单、上架与发货。' : undefined
            }
            okText="确认"
            cancelText="取消"
            onConfirm={() => void toggleEnabled(r, !r.enabled)}
          >
            <Button type="link" size="small">
              {r.enabled ? '停用' : '启用'}
            </Button>
          </Popconfirm>
          <Button type="link" size="small" onClick={() => setPullOpen(true)} disabled={!r.enabled}>
            拉单
          </Button>
          <Switch
            size="small"
            checkedChildren="同步开"
            unCheckedChildren="同步关"
            checked={r.orderSyncEnabled}
            disabled={!r.enabled}
            onChange={(checked) => void toggleSync(r, checked)}
          />
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="销售渠道"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchChannels()}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<CloudDownloadOutlined />}
            onClick={() => setPullOpen(true)}
          >
            渠道拉单
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
        title="渠道订单拉取（模拟渠道推送）"
        open={pullOpen}
        onCancel={() => setPullOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => void submitPull(v)}>
          <Form.Item
            name="channelCode"
            label="渠道"
            rules={[{ required: true, message: '请选择渠道' }]}
          >
            <Select
              options={rows
                .filter((r) => r.enabled)
                .map((r) => ({
                  value: r.channelCode,
                  label: CHANNEL_META[r.channelCode]?.label ?? r.channelCode,
                }))}
            />
          </Form.Item>
          <Form.Item
            name="channelOrderNo"
            label="渠道原始订单号"
            rules={[{ required: true, message: '请输入渠道订单号' }]}
            extra="同一渠道订单号重复提交会返回同一内部订单（幂等）。"
          >
            <Input placeholder="如 TB20261005001" />
          </Form.Item>
          <Form.Item name="buyerNick" label="渠道买家昵称">
            <Input placeholder="用于归并到同一内部客户" />
          </Form.Item>
          <Form.Item
            name="productId"
            label="内部商品ID"
            rules={[{ required: true, message: '请输入商品ID' }]}
            extra="会按「渠道前缀 + 商品ID」组装渠道 SKU 编码。"
          >
            <Input placeholder="如 900001" />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="数量"
            initialValue={1}
            rules={[{ required: true, message: '请输入数量' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="unitPrice"
            label="渠道成交单价"
            initialValue={99}
            rules={[{ required: true, message: '请输入单价' }]}
          >
            <InputNumber min={0.01} step={1} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setPullOpen(false)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                拉取并落单
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
