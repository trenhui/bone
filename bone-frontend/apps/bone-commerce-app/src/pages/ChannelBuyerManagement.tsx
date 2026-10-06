import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { ReloadOutlined, TeamOutlined, WarningOutlined } from '@ant-design/icons';
import { normalizeTotal, formatDate } from '@bone/shared-utils';
import { channelBuyerApi } from '../services/api';
import { errMsg } from '../utils/error';
import { BINDING_SOURCE_META, CHANNEL_META } from './channelConstants';
import type { ChannelBuyerSummary } from '../types';

type BindMode = 'bind' | 'rebind';

/**
 * 渠道买家映射（渠道买家账号 ↔ 内部客户）。
 *
 * <b>为什么这个页面是必需的</b>：渠道订单的内部客户维度来自本映射。早前用买家昵称哈希当客户ID，
 * 买家一改名订单就散到不同客户下，客户维度的统计/会员权益/售后全部失真。现在映射是显式数据，
 * 拉单时自动登记「影子」（尚未识别到真实客户），由运营在此页补绑。
 *
 * <b>为什么默认筛「待绑定」</b>：已绑定的行没什么要处理的；待绑定才是需要人行动的那批，
 * 而且其中高频买家（拉单笔数多）不绑定就一直在超卖风险里。
 */
export const ChannelBuyerManagement: React.FC = () => {
  const [rows, setRows] = useState<ChannelBuyerSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [channelCode, setChannelCode] = useState<string | undefined>();
  const [boundFilter, setBoundFilter] = useState<'all' | 'bound' | 'shadow'>('all');

  const [modal, setModal] = useState<{ mode: BindMode; row: ChannelBuyerSummary } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchRows = useCallback(async () => {
    setLoading(true);
    try {
      const res = await channelBuyerApi.page({
        page,
        size: pageSize,
        channelCode,
        bound: boundFilter === 'all' ? undefined : boundFilter === 'bound',
      });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取渠道买家映射失败'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, channelCode, boundFilter]);

  useEffect(() => {
    void fetchRows();
  }, [fetchRows]);

  const openBind = (row: ChannelBuyerSummary, mode: BindMode) => {
    setModal({ mode, row });
    form.setFieldsValue({
      customerId: mode === 'bind' ? undefined : row.customerId,
      customerName: mode === 'bind' ? undefined : row.customerName ?? undefined,
      reason: undefined,
    });
  };

  const submit = async (values: {
    customerId: string;
    customerName?: string;
    reason?: string;
  }) => {
    if (!modal) {
      return;
    }
    setSubmitting(true);
    try {
      const base = {
        channelCode: modal.row.channelCode,
        channelBuyerId: modal.row.channelBuyerId,
        customerId: values.customerId,
        customerName: values.customerName,
      };
      if (modal.mode === 'bind') {
        await channelBuyerApi.bind(base);
        message.success('已绑定，该渠道买家的后续订单将落到该内部客户');
      } else {
        await channelBuyerApi.rebind({ ...base, reason: values.reason ?? '' });
        message.success('已改绑，变更已记入备注');
      }
      setModal(null);
      form.resetFields();
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, modal.mode === 'bind' ? '绑定失败' : '改绑失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const doUnbind = (row: ChannelBuyerSummary) => {
    Modal.confirm({
      title: '确认解绑',
      content: `解绑后「${row.channelBuyerNick ?? row.channelBuyerId}」的后续订单将重新落到「未知客户」（customerId=0），`
        + '不再挂在当前客户下。用于「绑错了」或「客户已合并/注销」。',
      okText: '确认解绑',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await channelBuyerApi.unbind({
            channelCode: row.channelCode,
            channelBuyerId: row.channelBuyerId,
          });
          message.success('已解绑');
          void fetchRows();
        } catch (error) {
          message.error(errMsg(error, '解绑失败'));
        }
      },
    });
  };

  const columns = [
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
      title: '渠道买家账号',
      dataIndex: 'channelBuyerId',
      key: 'channelBuyerId',
      width: 200,
      ellipsis: true,
      render: (v: string) => (
        <Tooltip title={v}>
          <span style={{ fontFamily: 'monospace' }}>{v}</span>
        </Tooltip>
      ),
    },
    {
      title: '买家昵称',
      dataIndex: 'channelBuyerNick',
      key: 'channelBuyerNick',
      width: 140,
      ellipsis: true,
      render: (v: string | null) => v || '—',
    },
    {
      title: '内部客户',
      key: 'customer',
      width: 190,
      render: (_: unknown, r: ChannelBuyerSummary) =>
        r.bound ? (
          <span>
            <span style={{ fontFamily: 'monospace' }}>{r.customerId}</span>
            {r.customerName ? ` · ${r.customerName}` : ''}
          </span>
        ) : (
          <Tag color="warning">待绑定（订单落 customerId=0）</Tag>
        ),
    },
    {
      title: '来源',
      dataIndex: 'bindingSource',
      key: 'bindingSource',
      width: 140,
      render: (v: string) => (
        <Tag color={BINDING_SOURCE_META[v]?.color ?? 'default'}>
          {BINDING_SOURCE_META[v]?.label ?? v}
        </Tag>
      ),
    },
    {
      title: '拉单笔数',
      dataIndex: 'orderCount',
      key: 'orderCount',
      width: 100,
      sorter: (a: ChannelBuyerSummary, b: ChannelBuyerSummary) =>
        (a.orderCount ?? 0) - (b.orderCount ?? 0),
    },
    {
      title: '最近拉单',
      dataIndex: 'lastOrderAt',
      key: 'lastOrderAt',
      width: 170,
      render: (v: string | null) => (v ? formatDate(v) : '—'),
    },
    {
      title: '操作',
      key: 'ops',
      width: 190,
      render: (_: unknown, r: ChannelBuyerSummary) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openBind(r, r.bound ? 'rebind' : 'bind')}>
            {r.bound ? '改绑' : '绑定'}
          </Button>
          {r.bound && (
            <Button type="link" size="small" danger onClick={() => doUnbind(r)}>
              解绑
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="渠道买家映射"
      extra={
        <Space>
          <Select
            placeholder="渠道"
            allowClear
            style={{ width: 130 }}
            value={channelCode}
            onChange={(v) => {
              setChannelCode(v);
              setPage(1);
            }}
            options={Object.entries(CHANNEL_META).map(([value, meta]) => ({
              value,
              label: meta.label,
            }))}
          />
          <Select
            style={{ width: 160 }}
            value={boundFilter}
            onChange={(v) => {
              setBoundFilter(v);
              setPage(1);
            }}
            options={[
              { value: 'all', label: '全部' },
              { value: 'shadow', label: '仅待绑定（影子）' },
              { value: 'bound', label: '仅已绑定' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void fetchRows()}>
            刷新
          </Button>
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }} size="large" wrap>
        <Statistic title="映射总数" value={total} />
        <Statistic
          title="本页待绑定"
          value={rows.filter((r) => !r.bound).length}
          prefix={<WarningOutlined />}
        />
        <Statistic title="本页已绑定" value={rows.filter((r) => r.bound).length} prefix={<TeamOutlined />} />
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

      <Modal
        title={
          modal
            ? `${modal.mode === 'bind' ? '绑定' : '改绑'}内部客户 · ${modal.row.channelBuyerNick ?? modal.row.channelBuyerId}`
            : ''
        }
        open={modal !== null}
        onCancel={() => setModal(null)}
        footer={null}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => void submit(v)}>
          <Form.Item
            name="customerId"
            label="内部客户ID"
            rules={[{ required: true, message: '请输入内部客户ID' }]}
            extra="雪花 ID 为字符串，请勿用数字输入控件（会丢精度）。"
          >
            <Input placeholder="如 2001" />
          </Form.Item>
          <Form.Item name="customerName" label="客户名称（快照，便于运营识别）">
            <Input placeholder="选填" />
          </Form.Item>
          {modal?.mode === 'rebind' && (
            <Form.Item
              name="reason"
              label="改绑原因"
              rules={[{ required: true, message: '改绑必须填写原因（影响历史订单归属，需留痕）' }]}
            >
              <Input.TextArea rows={3} placeholder="如：售后申诉核实后确认绑错" />
            </Form.Item>
          )}
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setModal(null)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                提交
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
