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
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { CloudUploadOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import { formatDate, normalizeTotal } from '@bone/shared-utils';
import { channelProductApi } from '../services/api';
import { errMsg } from '../utils/error';
import type { ChannelProductSummary } from '../types';
import { CHANNEL_META, LISTING_STATUS_META } from './channelConstants';

/**
 * 渠道商品上架管理。
 *
 * <b>为何上架结果是「失败」也要留在列表里</b>：渠道拒绝（类目不符、资质过期）是运营要处理的
 * 日常事项，静默丢弃会让「点了没反应」。因此失败态连同失败原因一起展示，可直接重试。
 */
export const ChannelProductManagement: React.FC = () => {
  const [rows, setRows] = useState<ChannelProductSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [filterChannel, setFilterChannel] = useState<string>('');
  const [filterStatus, setFilterStatus] = useState<string>('');

  const [listOpen, setListOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchRows = useCallback(async () => {
    setLoading(true);
    try {
      const res = await channelProductApi.page({
        page,
        size: pageSize,
        channelCode: filterChannel || undefined,
        listingStatus: filterStatus || undefined,
      });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取渠道商品列表失败'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, filterChannel, filterStatus]);

  useEffect(() => {
    void fetchRows();
  }, [fetchRows]);

  const submitList = async (values: {
    channelCode: string;
    productId: string;
    productName: string;
    listingPrice: number;
  }) => {
    setSubmitting(true);
    try {
      const res = await channelProductApi.list({
        channelCode: values.channelCode,
        // 雪花 ID 全程走字符串：Number() 会超出 2^53 被静默截断，命中错误商品
        productId: values.productId,
        productName: values.productName,
        listingPrice: values.listingPrice,
      });
      const data = res.data;
      if (data.listingStatus === 'ONLINE') {
        message.success(`上架成功，渠道商品ID ${data.channelProductId}`);
      } else {
        message.warning(`渠道未受理：${data.failReason ?? '未知原因'}`);
      }
      setListOpen(false);
      form.resetFields();
      setPage(1);
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '上架失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const runDelist = async (row: ChannelProductSummary) => {
    try {
      await channelProductApi.delist({
        channelCode: row.channelCode,
        productId: row.productId,
      });
      message.success('已提交下架');
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '下架失败'));
    }
  };

  const runSync = async (row: ChannelProductSummary) => {
    try {
      const res = await channelProductApi.syncInventory(row.productId, row.listingStock);
      message.success(`库存已广播到 ${res.data.length} 个渠道`);
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '库存同步失败'));
    }
  };

  const columns = [
    {
      title: '渠道',
      dataIndex: 'channelCode',
      key: 'channelCode',
      width: 100,
      render: (code: string) => (
        <Tag color={CHANNEL_META[code]?.color ?? 'default'}>
          {CHANNEL_META[code]?.label ?? code}
        </Tag>
      ),
    },
    { title: '商品ID', dataIndex: 'productId', key: 'productId', width: 150 },
    { title: '商品名称', dataIndex: 'productName', key: 'productName', ellipsis: true },
    {
      title: '渠道商品ID',
      dataIndex: 'channelProductId',
      key: 'channelProductId',
      width: 150,
      render: (v: string | null) => v || '—',
    },
    {
      title: '挂牌价',
      dataIndex: 'listingPrice',
      key: 'listingPrice',
      width: 110,
      render: (v: number | null) => (v == null ? '—' : `¥${Number(v).toFixed(2)}`),
    },
    {
      title: '同步库存',
      dataIndex: 'listingStock',
      key: 'listingStock',
      width: 100,
    },
    {
      title: '状态',
      dataIndex: 'listingStatus',
      key: 'listingStatus',
      width: 110,
      render: (s: string) => (
        <Tag color={LISTING_STATUS_META[s]?.color ?? 'default'}>
          {LISTING_STATUS_META[s]?.label ?? s}
        </Tag>
      ),
    },
    {
      title: '失败原因',
      dataIndex: 'failReason',
      key: 'failReason',
      ellipsis: true,
      render: (v: string | null) =>
        v ? (
          <Tooltip title={v}>
            <span style={{ color: '#cf1322' }}>{v}</span>
          </Tooltip>
        ) : (
          '—'
        ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (v: string | null) => (v ? formatDate(v) : '—'),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_: unknown, r: ChannelProductSummary) => (
        <Space size="small">
          <Popconfirm
            title="确认从该渠道下架？"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void runDelist(r)}
          >
            <Button type="link" size="small" disabled={r.listingStatus !== 'ONLINE'}>
              下架
            </Button>
          </Popconfirm>
          <Button
            type="link"
            size="small"
            icon={<SyncOutlined />}
            onClick={() => void runSync(r)}
          >
            同步库存
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="渠道商品上架"
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
            options={Object.keys(LISTING_STATUS_META).map((s) => ({
              value: s,
              label: LISTING_STATUS_META[s].label,
            }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void fetchRows()}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<CloudUploadOutlined />}
            onClick={() => setListOpen(true)}
          >
            商品上架
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
        title="商品上架到渠道"
        open={listOpen}
        onCancel={() => setListOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => void submitList(v)}>
          <Form.Item
            name="channelCode"
            label="渠道"
            rules={[{ required: true, message: '请选择渠道' }]}
          >
            <Select
              options={Object.keys(CHANNEL_META).map((c) => ({
                value: c,
                label: CHANNEL_META[c].label,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="productId"
            label="内部商品ID"
            rules={[{ required: true, message: '请输入商品ID' }]}
            extra="雪花 ID 为字符串，请勿用数字输入控件。"
          >
            <Input placeholder="如 900001" />
          </Form.Item>
          <Form.Item
            name="productName"
            label="商品名称"
            rules={[{ required: true, message: '请输入商品名称' }]}
          >
            <Input placeholder="渠道侧展示标题" />
          </Form.Item>
          <Form.Item
            name="listingPrice"
            label="挂牌价"
            rules={[{ required: true, message: '请输入挂牌价' }]}
          >
            <InputNumber min={0.01} step={1} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setListOpen(false)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                提交上架
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
