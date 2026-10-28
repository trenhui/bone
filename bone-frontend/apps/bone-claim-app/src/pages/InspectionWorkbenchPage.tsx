import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Segmented,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { FileSearchOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import {
  inspectionApi,
  type InspectionAction,
  type InspectionDiffType,
  type InspectionDimension,
  type InspectionResult,
  type InspectionView,
} from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';

/**
 * 后督抽检工作台（A9 / §7.6）。
 *
 * 业务口径：
 * - 抽检前置：案件须已终态（结案 50 或拒赔生效 45）——后督是**事后**监督，不是审核环节；
 * - 结论即终态、不可改写：需更正则另开一次抽检（审计证据不得覆盖）；
 * - 结论与处置必须自洽：PASS 不带归因，DIFF 必带受控差异类型 + 处置动作 + 差异明细；
 * - 处置只登记意图，「改案件状态回流」属 V2——所以目标状态是选填，且明确标注「本期不自动执行」。
 */

const DIMENSION_LABEL: Record<InspectionDimension, string> = {
  AMOUNT: '金额准确性',
  DOCUMENT: '单证完整性',
  MEDICAL: '医疗合理性',
  PROCESS: '流程时效',
};

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待抽检' },
  IN_PROGRESS: { color: 'processing', label: '抽检中' },
  CONCLUDED: { color: 'green', label: '已结论' },
};

const DIFF_TYPE_LABEL: Record<InspectionDiffType, string> = {
  ADJUST_MISMATCH: '理算差异',
  RULE_MISS: '规则漏命中',
  AI_MISJUDGE: 'AI 误判',
  QUOTA_ABNORMAL: '额度异常',
  NONE: '无差异',
};

const ACTION_LABEL: Record<InspectionAction, string> = {
  RECALCULATE: '触发重算',
  REVIEW: '转人工复核',
  REFUND: '退款/追回',
  CLOSE: '维持结案',
  NONE: '无处置',
};

/** 差异明细的可选字段（受控键，便于按字段聚合「哪类差异最多」）。 */
const DIFF_FIELD_OPTIONS = [
  { value: 'approvedAmount', label: '核定金额' },
  { value: 'itemCategory', label: '费用类别' },
  { value: 'socialFlag', label: '社保内外拆分' },
  { value: 'clinicalNecessity', label: '医疗必要性' },
  { value: 'evidenceRefs', label: '证据引用' },
  { value: 'processTimeline', label: '时效节点' },
];

/** 目标状态只允许已建模状态名（一期质检段 30/31/32 仅登记，不执行回流）。 */
const TARGET_STATUS_OPTIONS = [
  { value: 'ADJUSTING', label: 'ADJUSTING（核赔中，41）' },
  { value: 'CLOSED_REJECTED', label: 'CLOSED_REJECTED（拒赔生效，45）' },
  { value: 'FINISHED', label: 'FINISHED（已结案，50）' },
  { value: '30', label: '30（质检段·待检，V2 才执行）' },
];

interface DiffRow {
  field: string;
  value: string;
}

interface ConcludeFormValues {
  result: InspectionResult;
  diffType?: InspectionDiffType;
  action?: InspectionAction;
  targetStatus?: string;
  diffRows?: DiffRow[];
}

export function InspectionWorkbenchPage(): ReactElement {
  const [searchParams, setSearchParams] = useSearchParams();
  const claimId = searchParams.get('claimId') ?? '';
  const [input, setInput] = useState(claimId);
  const [dimension, setDimension] = useState<InspectionDimension>('AMOUNT');
  const [inspection, setInspection] = useState<InspectionView | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);
  const [form] = Form.useForm<ConcludeFormValues>();
  const result = Form.useWatch('result', form) as InspectionResult | undefined;

  const load = useCallback(async () => {
    if (!claimId) {
      setInspection(null);
      return;
    }
    setLoading(true);
    try {
      const resp = await inspectionApi.latest(claimId);
      setInspection(resp?.data ?? null);
    } catch {
      message.error('抽检单查询失败');
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (fn: () => Promise<unknown>, okMsg: string) => {
    setActing(true);
    try {
      await fn();
      message.success(okMsg);
      await load();
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      if (errorCode === 'SC_CLAIM_STATUS_CONFLICT') {
        message.error('案件尚未终态：后督抽检只针对已结案/已拒赔生效案件');
      } else if (errorCode === 'SC_CLAIM_INSPECTION_CONFLICT') {
        message.error('该抽检单已结论，结论不可改写');
      } else if (errorCode === 'SC_CLAIM_NOT_FOUND') {
        message.error('案件不存在或不可见');
      } else {
        message.error('操作失败');
      }
    } finally {
      setActing(false);
    }
  };

  const onSearch = () => {
    const id = input.trim();
    setSearchParams(id ? { claimId: id } : {});
  };

  const onOpen = () => run(() => inspectionApi.open(claimId, dimension), '抽检单已创建（或已存在）');
  const onClaim = () =>
    inspection ? run(() => inspectionApi.claim(inspection.inspectionNo), '已领取') : undefined;

  const onConclude = async () => {
    if (!inspection) return;
    const values = await form.validateFields();
    const diffDetail =
      values.result === 'DIFF'
        ? Object.fromEntries(
            (values.diffRows ?? [])
              .filter((row) => row?.field && row?.value)
              .map((row) => [row.field, row.value] as const),
          )
        : undefined;
    await run(
      () =>
        inspectionApi.conclude(inspection.inspectionNo, {
          result: values.result,
          diffType: values.result === 'DIFF' ? values.diffType : undefined,
          action: values.result === 'DIFF' ? values.action : undefined,
          targetStatus: values.result === 'DIFF' ? values.targetStatus : undefined,
          diffDetail,
        }),
      '抽检结论已登记（终态，不可改写）',
    );
  };

  const diffDetailText = useMemo(() => {
    if (!inspection?.diffDetail) return '';
    try {
      return JSON.stringify(JSON.parse(inspection.diffDetail), null, 2);
    } catch {
      return inspection.diffDetail;
    }
  }, [inspection?.diffDetail]);

  const concluded = inspection?.status === 'CONCLUDED';
  const statusTag = inspection ? STATUS_TAG[inspection.status] : null;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="后督抽检" />
      <Card
        title={
          <Space>
            <SafetyCertificateOutlined />
            抽检任务
          </Space>
        }
        loading={loading}
        extra={
          <Space>
            <Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onPressEnter={onSearch}
              placeholder="案件ID"
              style={{ width: 140 }}
            />
            <Button icon={<ReloadOutlined />} onClick={onSearch}>
              查询
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message="结案案件每日按分层比例（默认 5%）自动抽样；人工可对任一已终态案件发起抽检。抽检结论一经登记不可改写，需更正则另开一次抽检。"
          />

          {!claimId ? (
            <Empty description="输入案件ID后查询抽检单，或直接发起抽检" />
          ) : !inspection ? (
            <Card type="inner" title="发起抽检（#13）" extra={<Tag color="default">该案暂无抽检单</Tag>}>
              <Space wrap>
                <Select<InspectionDimension>
                  value={dimension}
                  onChange={setDimension}
                  style={{ width: 200 }}
                  options={(Object.keys(DIMENSION_LABEL) as InspectionDimension[]).map((key) => ({
                    value: key,
                    label: DIMENSION_LABEL[key],
                  }))}
                />
                <Button
                  type="primary"
                  icon={<FileSearchOutlined />}
                  loading={acting}
                  onClick={onOpen}
                >
                  发起人工抽检
                </Button>
                <Typography.Text type="secondary">
                  仅已结案（50）/拒赔生效（45）案件可抽检
                </Typography.Text>
              </Space>
            </Card>
          ) : (
            <>
              <Descriptions bordered column={2} size="small">
                <Descriptions.Item label="抽检单号">{inspection.inspectionNo}</Descriptions.Item>
                <Descriptions.Item label="案件号">{inspection.claimNo || '-'}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={statusTag?.color}>{statusTag?.label}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="发起方式">
                  {inspection.inspectType === 'AUTO' ? '自动抽样' : '人工发起'}
                </Descriptions.Item>
                <Descriptions.Item label="抽检维度">
                  {DIMENSION_LABEL[inspection.dimension] ?? inspection.dimension}
                </Descriptions.Item>
                <Descriptions.Item label="分层标识">
                  {inspection.sampleStratum || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="质检人">
                  {inspection.inspectorId || '未领取'}
                </Descriptions.Item>
                <Descriptions.Item label="结论">
                  {concluded ? (
                    <Tag color={inspection.result === 'PASS' ? 'green' : 'red'}>
                      {inspection.result === 'PASS' ? '复检一致' : '存在差异'}
                    </Tag>
                  ) : (
                    '-'
                  )}
                </Descriptions.Item>
              </Descriptions>

              {concluded ? (
                <Card type="inner" title="抽检结论（终态）">
                  <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label="差异类型">
                      {DIFF_TYPE_LABEL[inspection.diffType] ?? inspection.diffType}
                    </Descriptions.Item>
                    <Descriptions.Item label="处置动作">
                      {ACTION_LABEL[inspection.actionTaken] ?? inspection.actionTaken}
                    </Descriptions.Item>
                    <Descriptions.Item label="目标状态">
                      {inspection.targetStatus || '未登记'}
                    </Descriptions.Item>
                    <Descriptions.Item label="回流执行">
                      <Typography.Text type="secondary">V2（本期只登记意图）</Typography.Text>
                    </Descriptions.Item>
                  </Descriptions>
                  {diffDetailText && (
                    <pre
                      style={{
                        marginTop: 12,
                        padding: 12,
                        background: 'rgba(0,0,0,0.03)',
                        borderRadius: 6,
                        fontSize: 12,
                        maxHeight: 220,
                        overflow: 'auto',
                      }}
                    >
                      {diffDetailText}
                    </pre>
                  )}
                </Card>
              ) : (
                <Card
                  type="inner"
                  title="登记抽检结论（#14）"
                  extra={
                    inspection.status === 'PENDING' ? (
                      <Button loading={acting} onClick={onClaim}>
                        领取任务
                      </Button>
                    ) : null
                  }
                >
                  <Form<ConcludeFormValues>
                    form={form}
                    layout="vertical"
                    initialValues={{ result: 'PASS' }}
                  >
                    <Form.Item name="result" label="结论" rules={[{ required: true }]}>
                      <Segmented
                        options={[
                          { value: 'PASS', label: '复检一致（PASS）' },
                          { value: 'DIFF', label: '存在差异（DIFF）' },
                        ]}
                      />
                    </Form.Item>

                    {result === 'DIFF' && (
                      <>
                        <Space size="large" wrap align="start">
                          <Form.Item
                            name="diffType"
                            label="差异类型"
                            rules={[{ required: true, message: 'DIFF 必须给出受控差异类型' }]}
                          >
                            <Select
                              style={{ width: 220 }}
                              placeholder="选择差异类型"
                              options={(
                                Object.keys(DIFF_TYPE_LABEL) as InspectionDiffType[]
                              )
                                .filter((key) => key !== 'NONE')
                                .map((key) => ({ value: key, label: DIFF_TYPE_LABEL[key] }))}
                            />
                          </Form.Item>
                          <Form.Item
                            name="action"
                            label="处置动作"
                            rules={[{ required: true, message: 'DIFF 必须给出处置动作' }]}
                          >
                            <Select
                              style={{ width: 220 }}
                              placeholder="选择处置动作"
                              options={(
                                Object.keys(ACTION_LABEL) as InspectionAction[]
                              )
                                .filter((key) => key !== 'NONE')
                                .map((key) => ({ value: key, label: ACTION_LABEL[key] }))}
                            />
                          </Form.Item>
                          <Form.Item
                            name="targetStatus"
                            label="目标状态（选填，本期不自动执行）"
                          >
                            <Select
                              style={{ width: 280 }}
                              allowClear
                              placeholder="选择处置目标状态"
                              options={TARGET_STATUS_OPTIONS}
                            />
                          </Form.Item>
                        </Space>

                        <Form.List name="diffRows">
                          {(fields, { add, remove }) => (
                            <Form.Item
                              label="差异明细（必填：字段 + 本方值/复核值）"
                              required
                              style={{ marginBottom: 0 }}
                            >
                              <Space direction="vertical" style={{ width: '100%' }}>
                                {fields.map((field) => (
                                  <Space key={field.key} align="baseline">
                                    <Form.Item
                                      name={[field.name, 'field']}
                                      rules={[{ required: true, message: '选择差异字段' }]}
                                      style={{ marginBottom: 8 }}
                                    >
                                      <Select
                                        style={{ width: 200 }}
                                        placeholder="差异字段"
                                        options={DIFF_FIELD_OPTIONS}
                                      />
                                    </Form.Item>
                                    <Form.Item
                                      name={[field.name, 'value']}
                                      rules={[{ required: true, message: '填写差值/复核值' }]}
                                      style={{ marginBottom: 8 }}
                                    >
                                      <Input
                                        style={{ width: 320 }}
                                        placeholder="如 120.00 → 98.00（条款 3.2）"
                                      />
                                    </Form.Item>
                                    <Button type="link" danger onClick={() => remove(field.name)}>
                                      删除
                                    </Button>
                                  </Space>
                                ))}
                                <Button
                                  type="dashed"
                                  onClick={() => add({ field: '', value: '' })}
                                  block
                                >
                                  + 增加差异行
                                </Button>
                              </Space>
                            </Form.Item>
                          )}
                        </Form.List>
                      </>
                    )}

                    <Space style={{ marginTop: 16 }}>
                      <Button type="primary" loading={acting} onClick={onConclude}>
                        提交结论（终态）
                      </Button>
                      <Typography.Text type="secondary">
                        提交后不可修改；差异案件的重算/退款编排属 V2，本期只登记处置意图。
                      </Typography.Text>
                    </Space>
                  </Form>
                </Card>
              )}
            </>
          )}
        </Space>
      </Card>
    </div>
  );
}
