import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { DollarOutlined, SearchOutlined, RollbackOutlined } from '@ant-design/icons';
import { Auth, AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { formatDate } from '@bone/shared-utils';
import { paymentApi } from '../services/api';
import { errMsg } from '../utils/error';
import type { PaymentDetail, PaymentStatus } from '../types';

const STATUS_META: Record<PaymentStatus, { label: string; color: string }> = {
  PENDING: { label: '待支付', color: 'default' },
  PAYING: { label: '支付中', color: 'blue' },
  SUCCESS: { label: '支付成功', color: 'green' },
  FAILED: { label: '支付失败', color: 'red' },
  CLOSED: { label: '已关闭', color: 'orange' },
};

/**
 * 退款表单独立成组件而非内联在父级。
 *
 * 为什么：`Form.useForm()` 必须在组件顶层调用（Hooks 规则），而退款表单只在
 * `detail` 非空时渲染。若父级先 createRef 再条件渲染 <Form>，detail 为空期间该 form
 * 实例**没有挂载任何 Form 元素**，React 会持续告警
 * 「Instance created by `useForm` is not connected to any Form element」。
 * 独立组件把「form 实例的创建」与「Form 元素的挂载」绑定在同一次挂载里。
 */
const RefundSection: React.FC<{
  paymentId: string;
  status: PaymentStatus;
  onDone: () => void | Promise<void>;
}> = ({ paymentId, status, onDone }) => {
  const [form] = Form.useForm<{ refundAmount: number }>();
  const [refunding, setRefunding] = useState(false);

  const submit = async (values: { refundAmount: number }) => {
    setRefunding(true);
    try {
      await paymentApi.refund(paymentId, {
        refundAmount: Number(values.refundAmount),
      });
      message.success('退款成功');
      form.resetFields();
      await onDone();
    } catch (error) {
      message.error(errMsg(error, '退款失败'));
    } finally {
      setRefunding(false);
    }
  };

  return (
    <div style={{ marginTop: 16 }}>
      <Form form={form} layout="inline" onFinish={submit}>
        <Form.Item
          name="refundAmount"
          label="退款金额"
          rules={[
            { required: true, message: '请输入退款金额' },
            { type: 'number', min: 0.01, message: '退款金额必须大于 0' },
          ]}
        >
          <InputNumber step={0.01} min={0.01} style={{ width: 160 }} />
        </Form.Item>
        <Form.Item>
          {/* 被 Popconfirm 包住的按钮只能用 <Auth> 包外层：AuthButton 无权限时返回
              null，会成为 Popconfirm 的 children，antd 对其 cloneElement 会抛异常。 */}
          <Auth code={BonePermissionCodes.ORDER_PAYMENT_WRITE}>
            <Popconfirm
              title="确认退款？"
              description="仅已支付成功的支付单可退款，服务端会校验。"
              okText="确认"
              cancelText="取消"
              onConfirm={() => form.submit()}
            >
              <Button
                danger
                icon={<RollbackOutlined />}
                loading={refunding}
                disabled={status !== 'SUCCESS'}
              >
                退款
              </Button>
            </Popconfirm>
          </Auth>
        </Form.Item>
      </Form>
      {status !== 'SUCCESS' && (
        <div style={{ marginTop: 8, color: '#8c8c8c', fontSize: 12 }}>
          当前状态不可退款（仅 SUCCESS 可退）。
        </div>
      )}
    </div>
  );
};

export const PaymentManagement: React.FC = () => {
  const [initiateForm] = Form.useForm<{ orderId: string }>();
  const [queryForm] = Form.useForm<{ paymentId: string }>();

  const [initiating, setInitiating] = useState(false);
  const [querying, setQuerying] = useState(false);

  const [lastPayUrl, setLastPayUrl] = useState<string | null>(null);
  const [detail, setDetail] = useState<PaymentDetail | null>(null);

  // 从订单详情「查看支付单」跳转而来（?paymentId=xxx）：挂载即按 query 自动查询，
  // 让用户在订单详情刷新后仍能一步定位到对应支付单，无需手动复制粘贴。
  const [searchParams] = useSearchParams();
  useEffect(() => {
    const pid = searchParams.get('paymentId');
    if (pid) {
      queryForm.setFieldsValue({ paymentId: pid });
      void query({ paymentId: pid });
    }
    // 仅在挂载时根据初次 query 触发一次，避免与用户手动查询互相干扰
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const initiate = async (values: { orderId: string }) => {
    setInitiating(true);
    try {
      const res = await paymentApi.initiate({ orderId: values.orderId });
      const { paymentId, payUrl } = res.data;
      setLastPayUrl(payUrl ?? null);
      message.success(`已发起支付，支付单 ${paymentId}`);
      queryForm.setFieldsValue({ paymentId: String(paymentId) });
      await query({ paymentId: String(paymentId) });
    } catch (error) {
      message.error(errMsg(error, '发起支付失败'));
    } finally {
      setInitiating(false);
    }
  };

  const query = async (values: { paymentId: string }) => {
    setQuerying(true);
    try {
      const res = await paymentApi.detail(values.paymentId);
      setDetail(res.data);
    } catch (error) {
      message.error(errMsg(error, '查询支付单失败'));
      setDetail(null);
    } finally {
      setQuerying(false);
    }
  };

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card title="发起支付">
          <Form form={initiateForm} layout="inline" onFinish={initiate}>
            <Form.Item
              name="orderId"
              rules={[{ required: true, message: '请输入订单号' }]}
            >
              <Input placeholder="订单号（如 761591360082935808）" style={{ width: 260 }} />
            </Form.Item>
            <Form.Item>
              <AuthButton
                code={BonePermissionCodes.ORDER_PAYMENT_WRITE}
                type="primary"
                htmlType="submit"
                icon={<DollarOutlined />}
                loading={initiating}
              >
                发起支付
              </AuthButton>
            </Form.Item>
          </Form>
          {lastPayUrl && (
            <div style={{ marginTop: 12 }}>
              <Typography.Text type="secondary">支付链接：</Typography.Text>
              <Typography.Link href={lastPayUrl} target="_blank" rel="noreferrer">
                {lastPayUrl}
              </Typography.Link>
            </div>
          )}
          {/* 为什么明说「不提供模拟回调」：回调端点恒签名校验，密钥只存在于服务端；
              前端模拟等于把密钥下发浏览器。见 services/api.ts 同名注释。 */}
          <div style={{ marginTop: 8, color: '#8c8c8c', fontSize: 12 }}>
            说明：支付结果由渠道 server-to-server 回调推进（服务端 HMAC 验签），
            此处不提供模拟回调；联调由回归脚本以服务端签名覆盖。
          </div>
        </Card>

        <Card title="查询支付单">
          <Form form={queryForm} layout="inline" onFinish={query}>
            <Form.Item
              name="paymentId"
              rules={[{ required: true, message: '请输入支付单号' }]}
            >
              <Input placeholder="支付单号" style={{ width: 260 }} />
            </Form.Item>
            <Form.Item>
              <Button
                htmlType="submit"
                icon={<SearchOutlined />}
                loading={querying}
              >
                查询
              </Button>
            </Form.Item>
          </Form>
        </Card>

        {detail && (
          <Card
            title={`支付单 ${detail.paymentId}`}
            extra={
              <Tag color={STATUS_META[detail.status]?.color ?? 'default'}>
                {STATUS_META[detail.status]?.label ?? detail.status}
              </Tag>
            }
          >
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="支付单号">{detail.paymentId}</Descriptions.Item>
              <Descriptions.Item label="订单号">{detail.orderId}</Descriptions.Item>
              <Descriptions.Item label="客户ID">{detail.customerId}</Descriptions.Item>
              <Descriptions.Item label="金额">
                ¥{Number(detail.amount).toFixed(2)}
              </Descriptions.Item>
              <Descriptions.Item label="支付渠道">{detail.channel || '—'}</Descriptions.Item>
              <Descriptions.Item label="渠道流水号">
                {detail.channelTradeNo || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="支付时间">
                {detail.paidAt ? formatDate(detail.paidAt) : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="退款时间">
                {detail.refundedAt ? formatDate(detail.refundedAt) : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="退款金额">
                {detail.refundAmount != null
                  ? `¥${Number(detail.refundAmount).toFixed(2)}`
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {formatDate(detail.createdAt)}
              </Descriptions.Item>
            </Descriptions>

            {detail.payUrl && (
              <div style={{ marginTop: 12 }}>
                <Typography.Text type="secondary">支付链接：</Typography.Text>{' '}
                <Typography.Link href={detail.payUrl} target="_blank" rel="noreferrer">
                  {detail.payUrl}
                </Typography.Link>
              </div>
            )}

            <RefundSection
              paymentId={detail.paymentId}
              status={detail.status}
              onDone={() => query({ paymentId: detail.paymentId })}
            />
          </Card>
        )}
      </Space>
    </div>
  );
};
