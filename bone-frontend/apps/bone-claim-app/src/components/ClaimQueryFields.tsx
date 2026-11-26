import type { ReactElement } from 'react';
import { Button, Col, DatePicker, Form, Input, InputNumber, Row, Select, Space } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import type { ClaimQueryState } from '../hooks/useClaimQuery';
import type { ClaimQueryParams, ClaimStatusName } from '../services/claimApi';
import { CLAIM_STATUS_META, statusOptions } from '../services/claimStatus';

/**
 * 理赔案件通用查询条件。
 *
 * <p>单一真源：字段值只由 URL 查询串承载（{@link useClaimQuery}），本表单只做「读 URL → 呈现」
 * 与「提交 → 写 URL」的双向转换，不持有第二份筛选状态。这样刷新/前进后退/分享链接都能还原，
 * 保存视图也只是存一段 queryString，不会出现「视图里没存高级条件」的假功能。
 */
export interface ClaimQueryFormValues {
  keyword?: string;
  claimId?: string;
  claimNo?: string;
  reportNo?: string;
  policyNo?: string;
  statuses?: ClaimStatusName[];
  taskStatus?: string;
  stage?: string;
  sourceChannel?: string;
  sourceSystem?: string;
  assigneeId?: string;
  hangUpStatus?: string;
  createdRange?: [Dayjs | null, Dayjs | null] | null;
  amountMin?: number;
  amountMax?: number;
}

const ALL_STATUSES = Object.keys(CLAIM_STATUS_META) as ClaimStatusName[];

const TASK_STATUS_OPTIONS = [
  { value: 'WAITING', label: '待领取' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'DONE', label: '已处理' },
];

export function statusesFromFilter(raw?: string): ClaimStatusName[] | undefined {
  const values = (raw ?? '')
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean) as ClaimStatusName[];
  return values.length ? values : undefined;
}

export function queryStateToFormValues(query: ClaimQueryState): ClaimQueryFormValues {
  const f = query.filters;
  const from = f.createdFrom ? dayjs(f.createdFrom) : null;
  const to = f.createdTo ? dayjs(f.createdTo) : null;
  const min = f.amountMin ? Number(f.amountMin) : undefined;
  const max = f.amountMax ? Number(f.amountMax) : undefined;
  return {
    keyword: query.keyword,
    claimId: f.claimId,
    claimNo: f.claimNo,
    reportNo: f.reportNo,
    policyNo: f.policyNo,
    statuses: statusesFromFilter(f.status),
    taskStatus: f.taskStatus,
    stage: f.stage,
    sourceChannel: f.sourceChannel,
    sourceSystem: f.sourceSystem,
    assigneeId: f.assigneeId,
    hangUpStatus: f.hangUpStatus,
    createdRange: from && from.isValid() || to && to.isValid() ? [from, to] : null,
    amountMin: min,
    amountMax: max,
  };
}

/** 表单值 → URL 查询串；空值一律删键，避免 `f_status=` 这种幽灵筛选。 */
export function formValuesToQueryString(values: ClaimQueryFormValues): string {
  const p = new URLSearchParams();
  const put = (key: string, value?: string | number | null) => {
    if (value !== undefined && value !== null && String(value).trim()) p.set(key, String(value).trim());
  };
  put('q', values.keyword);
  put('f_claimId', values.claimId);
  put('f_claimNo', values.claimNo);
  put('f_reportNo', values.reportNo);
  put('f_policyNo', values.policyNo);
  put('f_status', values.statuses?.join(','));
  put('f_taskStatus', values.taskStatus);
  put('f_stage', values.stage);
  put('f_sourceChannel', values.sourceChannel);
  put('f_sourceSystem', values.sourceSystem);
  put('f_assigneeId', values.assigneeId);
  put('f_hangUpStatus', values.hangUpStatus);
  put('f_createdFrom', values.createdRange?.[0]?.format('YYYY-MM-DD'));
  put('f_createdTo', values.createdRange?.[1]?.format('YYYY-MM-DD'));
  put('f_amountMin', values.amountMin);
  put('f_amountMax', values.amountMax);
  return p.toString();
}

/** URL 查询状态 → 后端查询参数（服务端真过滤、真分页）。 */
export function queryParamsFromState(query: ClaimQueryState): ClaimQueryParams {
  const f = query.filters;
  const num = (raw?: string) => (raw && Number.isFinite(Number(raw)) ? Number(raw) : undefined);
  return {
    keyword: query.keyword || undefined,
    claimId: f.claimId || undefined,
    claimNo: f.claimNo || undefined,
    reportNo: f.reportNo || undefined,
    policyNo: f.policyNo || undefined,
    statuses: statusesFromFilter(f.status),
    taskStatus: f.taskStatus || undefined,
    stage: f.stage || undefined,
    sourceChannel: f.sourceChannel || undefined,
    sourceSystem: f.sourceSystem || undefined,
    assigneeId: f.assigneeId || undefined,
    hangUpStatus: num(f.hangUpStatus),
    createdFrom: f.createdFrom || undefined,
    createdTo: f.createdTo || undefined,
    amountMin: num(f.amountMin),
    amountMax: num(f.amountMax),
    page: query.page,
    size: query.size,
  };
}

export interface ClaimQueryFieldsProps {
  query: ClaimQueryState;
  onSubmit: (values: ClaimQueryFormValues) => void;
  onReset: () => void;
  /** 可选状态范围；不传则展示全部案件状态。 */
  statusChoices?: ClaimStatusName[];
  /** 核赔工作台显示任务维（WAITING/ASSIGNED/PROCESSING/DONE）。 */
  showTaskStatus?: boolean;
}

export function ClaimQueryFields({
  query,
  onSubmit,
  onReset,
  statusChoices = ALL_STATUSES,
  showTaskStatus = false,
}: ClaimQueryFieldsProps): ReactElement {
  const [form] = Form.useForm<ClaimQueryFormValues>();

  return (
    <Form<ClaimQueryFormValues>
      form={form}
      layout="vertical"
      size="small"
      initialValues={queryStateToFormValues(query)}
      onFinish={(values) => onSubmit(values)}
    >
      <Row gutter={12}>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="keyword" label="关键字（案件ID / 赔案号 / 保单号 / 发票号）">
            <Input allowClear placeholder="输入 266400023001 可精确命中案件ID" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="statuses" label="案件状态（多选）">
            <Select
              mode="multiple"
              allowClear
              maxTagCount="responsive"
              placeholder="全部状态"
              options={statusOptions(statusChoices)}
            />
          </Form.Item>
        </Col>
        {showTaskStatus && (
          <Col xs={24} md={12} xl={8}>
            <Form.Item name="taskStatus" label="核赔任务维">
              <Select allowClear placeholder="默认待办（待领取+处理中）" options={TASK_STATUS_OPTIONS} />
            </Form.Item>
          </Col>
        )}
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="reportNo" label="报案号">
            <Input allowClear placeholder="RP..." />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="policyNo" label="保单号（模糊）">
            <Input allowClear placeholder="保单号片段" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="stage" label="案件阶段">
            <Input allowClear placeholder="如 PRE_EXAM / ADJUST" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="createdRange" label="受理时间">
            <DatePicker.RangePicker style={{ width: '100%' }} allowClear />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="amountMin" label="申请金额下限（元）">
            <InputNumber min={0} precision={2} placeholder="最小" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="amountMax" label="申请金额上限（元）">
            <InputNumber min={0} precision={2} placeholder="最大" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="assigneeId" label="处理人 / 初审人">
            <Input allowClear placeholder="用户 ID" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="sourceChannel" label="来源渠道">
            <Input allowClear placeholder="渠道编码" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="sourceSystem" label="来源系统">
            <Input allowClear placeholder="如 pk_tpa" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Form.Item name="hangUpStatus" label="挂起状态">
            <Select
              allowClear
              placeholder="全部"
              options={[
                { value: '0', label: '正常' },
                { value: '1', label: '挂起' },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>
      <Space>
        <Button type="primary" htmlType="submit">
          查询
        </Button>
        <Button
          onClick={() => {
            form.resetFields();
            onReset();
          }}
        >
          重置
        </Button>
      </Space>
    </Form>
  );
}
