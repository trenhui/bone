import { useState, type ReactElement } from 'react';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { claimApi, type ClaimItemReq, type ItemCategory, type SubmitClaimReq } from '../services/claimApi';

/**
 * 员工端 · 报案页（synapClaim 一期：企业补充医疗 · 门诊小额快赔）。
 *
 * 提交即受理（后端 DRAFT→7）；缺件走工作台「要求补件」回流，不阻塞受理。
 * 材料上传（ATTACHMENT 真上传）为后续迭代，当前以发票代码/号码登记（R-FRAUD-07 唯一约束）。
 */
/**
 * 报案表单值类型：出险日期为 dayjs（DatePicker），提交时转 ISO 字符串；
 * 明细走 Form.List， items 为行数组。
 */
type SubmitFormValues = Omit<SubmitClaimReq, 'outInsureTime'> & {
  outInsureTime: dayjs.Dayjs;
  items: ClaimItemReq[];
};

export function ClaimSubmitPage(): ReactElement {
  const [form] = Form.useForm<SubmitFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ claimNo: string; status: string } | null>(null);

  const onFinish = async (values: SubmitFormValues) => {
    setSubmitting(true);
    try {
      const req: SubmitClaimReq = {
        ...values,
        outInsureTime: values.outInsureTime.toISOString(),
        items: values.items ?? [],
      };
      const resp = await claimApi.submit(req);
      const data = resp?.data;
      setResult({ claimNo: data?.claimNo ?? '', status: data?.status ?? '' });
      message.success(`报案已受理：${data?.claimNo ?? ''}`);
      form.resetFields();
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      const text =
        errorCode === 'SC_CLAIM_POLICY_NOT_ACTIVE'
          ? '保单不存在或未生效，请核对保单号/计划'
          : errorCode === 'SC_CLAIM_OUT_OF_POLICY_PERIOD'
            ? '出险时间不在保单有效期内'
            : errorCode === 'SC_CLAIM_INSURED_MISMATCH'
              ? '当前登录账号与该保单被保人不匹配'
              : '提交失败，请稍后重试或联系核赔员';
      message.error(text);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 24 }}>
      <Typography.Title level={3}>理赔申请</Typography.Title>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="门诊小额快赔：≤500 元且材料完整、无风险标记、责任明确可自动通过；其余转人工核赔。"
      />
      {result && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 16 }}
          message={`报案已受理（${result.claimNo}）`}
          description={
            <>
              当前状态：<Tag color="blue">{result.status}</Tag> 决策结果将在 30 秒内返回，可在「我的报案」查看。
            </>
          }
        />
      )}
      <Card title="报案信息">
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ claimType: 'OUTPATIENT', items: [] as ClaimItemReq[] }}
        >
          <Space size="large" wrap>
            <Form.Item name="policyNo" label="保单号" rules={[{ required: true, message: '请输入保单号' }]}>
              <Input placeholder="EG20260001" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="planId" label="计划ID" rules={[{ required: true, message: '请输入计划ID' }]}>
              <InputNumber placeholder="7" stringMode style={{ width: 160 }} />
            </Form.Item>
            <Form.Item
              name="insuredIdHash"
              label="被保人标识"
              tooltip="服务端按登录主体计算，演示环境填写假名化ID"
              rules={[{ required: true, message: '请输入被保人标识' }]}
            >
              <Input placeholder="hash-abc" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item
              name="outInsureTime"
              label="出险日期"
              rules={[{ required: true, message: '请选择出险日期' }]}
            >
              <DatePicker style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="invoiceCode" label="发票代码">
              <Input placeholder="04400001" style={{ width: 200 }} />
            </Form.Item>
            <Form.Item name="invoiceNo" label="发票号码">
              <Input placeholder="12345678" style={{ width: 200 }} />
            </Form.Item>
          </Space>

          <Form.List name="items" initialValue={[{}]}>
            {(fields, { add, remove }) => (
              <>
                <Typography.Text strong>费用明细（至少一项）</Typography.Text>
                <Table<ClaimItemReq>
                  rowKey={(_, i) => String(i)}
                  dataSource={fields.map((f) => form.getFieldValue(['items', f.name]) as ClaimItemReq)}
                  pagination={false}
                  size="small"
                  style={{ marginBottom: 12 }}
                  columns={[
                    {
                      title: '项目名称',
                      render: (_, __, i) => (
                        <Form.Item
                          name={[i, 'itemName']}
                          rules={[{ required: true, message: '必填' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <Input placeholder="诊查费" />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '类别',
                      width: 140,
                      render: (_, __, i) => (
                        <Form.Item name={[i, 'category']} initialValue="SERVICE" style={{ marginBottom: 0 }}>
                          <Select<ItemCategory>
                            options={[
                              { value: 'DRUG', label: '药品' },
                              { value: 'SERVICE', label: '诊疗服务' },
                              { value: 'EXAM', label: '检查' },
                              { value: 'MATERIAL', label: '材料' },
                            ]}
                          />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '金额（元）',
                      width: 160,
                      render: (_, __, i) => (
                        <Form.Item
                          name={[i, 'amount']}
                          rules={[{ required: true, message: '必填' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '操作',
                      width: 70,
                      render: (_, __, i) =>
                        fields.length > 1 ? (
                          <Button type="link" danger onClick={() => remove(i)}>
                            删除
                          </Button>
                        ) : null,
                    },
                  ]}
                />
                <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ category: 'SERVICE' })}>
                  添加明细
                </Button>
              </>
            )}
          </Form.List>

          <Form.Item style={{ marginTop: 24 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                提交理赔
              </Button>
              <Button onClick={() => form.resetFields()}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
      <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>
        出险日期请按就诊日期填写（医保结算单日期）；发票代码/号码为重复报销校验依据（R-FRAUD-07），请勿填错。
        演示环境当前使用{dayjs().format('YYYY-MM-DD')}的客户端日期。
      </Typography.Paragraph>
    </div>
  );
}
