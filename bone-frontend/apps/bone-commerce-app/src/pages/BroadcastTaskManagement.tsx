import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Select, Space, Statistic, Table, Tag, Tooltip, message } from 'antd';
import { ReloadOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { normalizeTotal, formatDate } from '@bone/shared-utils';
import { channelBroadcastApi } from '../services/api';
import { errMsg } from '../utils/error';
import { BROADCAST_STATUS_META, CHANNEL_META } from './channelConstants';
import type { BroadcastTaskSummary } from '../types';

/**
 * 库存广播任务（Outbox 异步投递）。
 *
 * <b>为什么需要「失败清单 + 人工重试」</b>：库存变更现在只与「待广播」同事务落库，真正调渠道由中继异步做。
 * 中继会自动退避重试，但渠道侧持续失败（access token 过期、资质失效、类目下线、商品已删除）重试再多次也不会成功，
 * 必须有人看见并处理 —— 否则渠道会继续按旧库存售卖直到超卖。
 *
 * <b>为什么默认筛「失败」</b>：PENDING/PROCESSING 是正常的、机器自己能处理完的；只有 FAILED 需要人。
 */
export const BroadcastTaskManagement: React.FC = () => {
  const [rows, setRows] = useState<BroadcastTaskSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<string | undefined>('FAILED');
  const [retryingId, setRetryingId] = useState<string | null>(null);

  const fetchRows = useCallback(async () => {
    setLoading(true);
    try {
      const res = await channelBroadcastApi.page({ status, page, size: pageSize });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取广播任务失败'));
    } finally {
      setLoading(false);
    }
  }, [status, page, pageSize]);

  useEffect(() => {
    void fetchRows();
  }, [fetchRows]);

  const retry = async (row: BroadcastTaskSummary) => {
    setRetryingId(row.id);
    try {
      await channelBroadcastApi.retry(row.id);
      message.success('已重新入队并推进一轮中继，请稍后刷新查看结果');
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '重试失败'));
    } finally {
      setRetryingId(null);
    }
  };

  const relayNow = async () => {
    try {
      const res = await channelBroadcastApi.relayNow();
      message.success(`已推进一轮中继，成功投递 ${res.data?.[0] ?? 0} 条`);
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, '手动推送失败'));
    }
  };

  const columns = [
    {
      title: '任务ID',
      dataIndex: 'id',
      key: 'id',
      width: 190,
      render: (v: string) => (
        <Tooltip title={v}>
          <span style={{ fontFamily: 'monospace' }}>{v}</span>
        </Tooltip>
      ),
    },
    {
      title: '渠道',
      dataIndex: 'channelCode',
      key: 'channelCode',
      width: 100,
      render: (v: string) => (
        <Tag color={CHANNEL_META[v]?.color ?? 'default'}>{CHANNEL_META[v]?.label ?? v}</Tag>
      ),
    },
    {
      title: '商品',
      key: 'product',
      width: 220,
      ellipsis: true,
      render: (_: unknown, r: BroadcastTaskSummary) => (
        <Tooltip title={`商品ID ${r.productId ?? '—'}`}>
          <span>{r.productName || r.productId || '—'}</span>
        </Tooltip>
      ),
    },
    {
      title: '目标库存',
      dataIndex: 'targetStock',
      key: 'targetStock',
      width: 100,
      render: (v: number | null) => (
        <span style={{ fontWeight: 600 }}>{v ?? '—'}</span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (v: string) => (
        <Tag color={BROADCAST_STATUS_META[v]?.color ?? 'default'}>
          {BROADCAST_STATUS_META[v]?.label ?? v}
        </Tag>
      ),
    },
    {
      title: '重试',
      dataIndex: 'retryCount',
      key: 'retryCount',
      width: 80,
    },
    {
      title: '失败原因',
      dataIndex: 'lastError',
      key: 'lastError',
      ellipsis: true,
      render: (v: string | null, r: BroadcastTaskSummary) =>
        v ? (
          <Tooltip title={v}>
            <span style={{ color: '#cf1322' }}>{v}</span>
          </Tooltip>
        ) : r.mergedIntoId ? (
          <Tooltip title={`已合并到任务 ${r.mergedIntoId}（同商品同渠道只投最新库存）`}>
            <Tag color="default">已被新任务合并</Tag>
          </Tooltip>
        ) : (
          '—'
        ),
    },
    {
      title: '入队时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (v: string | null) => (v ? formatDate(v) : '—'),
    },
    {
      title: '操作',
      key: 'ops',
      width: 100,
      render: (_: unknown, r: BroadcastTaskSummary) =>
        r.status === 'SENT' ? (
          <span style={{ color: '#999' }}>—</span>
        ) : (
          <AuthButton
            code={BonePermissionCodes.COMMERCE_BROADCAST_WRITE}
            type="link"
            size="small"
            loading={retryingId === r.id}
            onClick={() => void retry(r)}
          >
            重试
          </AuthButton>
        ),
    },
  ];

  return (
    <Card
      title="库存广播任务"
      extra={
        <Space>
          <Select
            style={{ width: 160 }}
            value={status ?? 'all'}
            onChange={(v) => {
              setStatus(v === 'all' ? undefined : v);
              setPage(1);
            }}
            options={[
              { value: 'FAILED', label: '仅失败（需处理）' },
              { value: 'PENDING', label: '待投递' },
              { value: 'PROCESSING', label: '投递中' },
              { value: 'SENT', label: '已投递' },
              { value: 'all', label: '全部' },
            ]}
          />
          {/* 手动推进中继会真实调用渠道接口并消耗配额，与重试同码。 */}
          <AuthButton
            code={BonePermissionCodes.COMMERCE_BROADCAST_WRITE}
            icon={<ThunderboltOutlined />}
            onClick={() => void relayNow()}
          >
            立即推送一轮
          </AuthButton>
          <Button icon={<ReloadOutlined />} onClick={() => void fetchRows()}>
            刷新
          </Button>
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }} size="large" wrap>
        <Statistic title="任务总数" value={total} />
        <Statistic
          title="本页失败"
          value={rows.filter((r) => r.status === 'FAILED').length}
          valueStyle={{ color: '#cf1322' }}
        />
        <Statistic title="本页已投递" value={rows.filter((r) => r.status === 'SENT').length} />
      </Space>

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

      <Space style={{ marginTop: 12 }} align="start">
        <SendOutlined style={{ color: '#999', marginTop: 4 }} />
        <span style={{ color: '#666', fontSize: 12, lineHeight: 1.7 }}>
          库存变更（含入库/扣减/预留/释放）与「待广播」同事务落库；投递由中继异步完成，失败自动指数退避重试，
          重试耗尽转「失败（死信）」需在此人工重试。同商品同渠道的连续变更只投最新库存值。
        </span>
      </Space>
    </Card>
  );
};
