/**
 * 案件签收台（对齐普康 TPA 团险签收）。
 *
 * 三层视图：全部/线下/线上来源切换 → 批次组合查询 → 批次详情（赔案明细/轨迹/建案结果）。
 * 保留 Bone 自有的四态状态机、影像登记、二次确认与签收后自动建案；
 * 字段、查询条件与操作入口以参考站真实页面为基准，但不照搬源站脏数据。
 */
import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
  Typography,
} from 'antd';
import type { Dayjs } from 'dayjs';
import { InfoCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import { AuthButton } from '@bone/ui';
import { PERMISSIONS } from '../constants/permissions';
import { formatDate, resolveErrorMessage } from '@bone/shared-utils';
import {
  claimSignApi,
  type SignBatch,
  type SignBatchItem,
  type SignBatchListParams,
  type SignCaseItem,
  type SignTrack,
} from '../services/claimSign';

const MSG_KEY = 'case-sign-action';
const MIN_REMARK_LENGTH = 10;
const STATUS_LABEL: Record<string, string> = {
  WAIT_FOR_PARTICIPANT: '待上传人员',
  WAIT_FOR_IMAGE: '待上传影像',
  WAIT_FOR_CONFIRMATION: '待签收确认',
  FINISHED: '已签收确认',
  DELETED: '已删除',
};
const STATUS_COLOR: Record<string, string> = {
  WAIT_FOR_PARTICIPANT: 'orange',
  WAIT_FOR_IMAGE: 'blue',
  WAIT_FOR_CONFIRMATION: 'gold',
  FINISHED: 'green',
  DELETED: 'default',
};
const SIGN_TYPE_LABEL: Record<string, string> = { AUTO: '线上', MANUAL: '线下' };
const CHANNEL_LABEL: Record<string, string> = {
  EXPRESS: '快递签收',
  COUNTER: '上门签收',
  API_PUSH: 'API推送',
  EXTERNAL_PUSH: '外部推送',
};
const DETAIL_TABS = [
  { key: 'items', label: '赔案明细' },
  { key: 'tracks', label: '签收轨迹' },
  { key: 'cases', label: '自动建案' },
];

type SourceTab = 'ALL' | 'ONLINE' | 'OFFLINE';
type DetailTab = 'items' | 'tracks' | 'cases';
type FilterForm = {
  batchNo?: string;
  signType?: 'AUTO' | 'MANUAL';
  signChannel?: string;
  signStatus?: string;
  insuranceCompany?: string;
  insuranceSubsidiary?: string;
  insuringAgency?: string;
  signOperator?: string;
  signTime?: [Dayjs, Dayjs];
  createTime?: [Dayjs, Dayjs];
};

interface DetailState {
  batch: SignBatch | null;
  items: SignBatchItem[];
  tracks: SignTrack[];
  caseItems: SignCaseItem[];
}

const EMPTY_DETAIL: DetailState = { batch: null, items: [], tracks: [], caseItems: [] };

/** 404/403/5xx 翻译成用户下一步动作，避免「请求的资源不存在」式死胡同。 */
export function describeSignLoadError(error: unknown): string {
  const status = (error as { response?: { status?: number } } | undefined)?.response?.status;
  if (status === 404) {
    return '签收服务未在当前环境部署（接口返回 404）。请切换到已部署签收服务的环境，或联系管理员升级后端后重试。';
  }
  if (status === 403) {
    return '当前账号缺少签收权限（claim:claims:read），请联系管理员授权后刷新。';
  }
  if (typeof status === 'number' && status >= 500) {
    return `签收服务暂时不可用（HTTP ${status}），请稍后重试；若持续失败请联系管理员查看后端日志。`;
  }
  return resolveErrorMessage(error, '签收批次加载失败，请检查网络或稍后重试');
}

function rangeParams(values: FilterForm): SignBatchListParams {
  const params: SignBatchListParams = {
    batchNo: values.batchNo?.trim() || undefined,
    signType: values.signType,
    signChannel: values.signChannel?.trim() || undefined,
    signStatus: values.signStatus,
    insuranceCompany: values.insuranceCompany?.trim() || undefined,
    insuranceSubsidiary: values.insuranceSubsidiary?.trim() || undefined,
    insuringAgency: values.insuringAgency?.trim() || undefined,
    signOperator: values.signOperator?.trim() || undefined,
  };
  if (values.signTime?.length === 2) {
    params.signTimeStart = values.signTime[0].toISOString();
    params.signTimeEnd = values.signTime[1].toISOString();
  }
  if (values.createTime?.length === 2) {
    params.createTimeStart = values.createTime[0].toISOString();
    params.createTimeEnd = values.createTime[1].toISOString();
  }
  return params;
}

/** 具名导出（routes.tsx 用 .then 映射，禁 default 导出）。 */
export function CaseSignPage(): ReactElement {
  const { message } = App.useApp();
  const [source, setSource] = useState<SourceTab>('ALL');
  const [filterParams, setFilterParams] = useState<SignBatchListParams>({});
  const [rows, setRows] = useState<SignBatch[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [participantsOpen, setParticipantsOpen] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState<string | null>(null);
  const [detailTab, setDetailTab] = useState<DetailTab>('items');
  const [detail, setDetail] = useState<DetailState>(EMPTY_DETAIL);
  const [confirmRemark, setConfirmRemark] = useState('影像齐全，确认完成签收');
  const [filterForm] = Form.useForm<FilterForm>();
  const [createForm] = Form.useForm();
  const [participantsForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const resp = await claimSignApi.list({ ...filterParams, source, page, size });
      setRows(resp?.data?.records ?? []);
      setTotal(Number(resp?.data?.total ?? 0) || 0);
    } catch (err) {
      const hint = describeSignLoadError(err);
      setLoadError(hint);
      message.error({ content: hint, key: MSG_KEY });
    } finally {
      setLoading(false);
    }
  }, [filterParams, source, page, size, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const search = async () => {
    try {
      const values = await filterForm.validateFields();
      setFilterParams(rangeParams(values));
      setPage(1);
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '查询条件不合法'), key: MSG_KEY });
    }
  };

  const reset = () => {
    filterForm.resetFields();
    setFilterParams({});
    setPage(1);
  };

  const runAction = async (batchId: string, action: () => Promise<unknown>, okMsg: string) => {
    setActingId(batchId);
    try {
      await action();
      message.success({ content: okMsg, key: MSG_KEY });
      await load();
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '操作失败'), key: MSG_KEY });
    } finally {
      setActingId(null);
    }
  };

  /** 三个子请求并行且互不拖累：轨迹失败不影响明细，明细失败不影响建案结果。 */
  const showDetail = async (batchId: string, tab: DetailTab = 'items') => {
    setDetailOpen(batchId);
    setDetailTab(tab);
    setDetail(EMPTY_DETAIL);
    const [batchResult, itemsResult, tracksResult] = await Promise.allSettled([
      claimSignApi.detail(batchId),
      claimSignApi.items(batchId),
      claimSignApi.tracks(batchId),
    ]);
    if (batchResult.status === 'fulfilled') {
      const batch = batchResult.value?.data;
      setDetail((prev) => ({
        ...prev,
        batch: batch ?? null,
        caseItems: batch?.caseItems ?? [],
      }));
    } else {
      message.error({ content: resolveErrorMessage(batchResult.reason, '批次摘要加载失败'), key: MSG_KEY });
    }
    if (itemsResult.status === 'fulfilled') {
      setDetail((prev) => ({ ...prev, items: itemsResult.value?.data ?? [] }));
    } else {
      message.error({ content: resolveErrorMessage(itemsResult.reason, '赔案明细加载失败'), key: MSG_KEY });
    }
    if (tracksResult.status === 'fulfilled') {
      setDetail((prev) => ({ ...prev, tracks: tracksResult.value?.data ?? [] }));
    } else {
      message.error({ content: resolveErrorMessage(tracksResult.reason, '轨迹加载失败'), key: MSG_KEY });
    }
  };

  /** 二次确认内拦截非法意见，避免用户以为已提交但后端拒绝。 */
  const confirmSign = async (batchId: string) => {
    const remark = confirmRemark.trim();
    if (remark.length < MIN_REMARK_LENGTH) {
      message.error({ content: `签收意见不少于 ${MIN_REMARK_LENGTH} 个字符`, key: MSG_KEY });
      return false;
    }
    await runAction(batchId, () => claimSignApi.confirm(batchId, remark), '签收完成');
    return true;
  };

  const imageText = (row: SignBatch): string => {
    if (!row.claimCount) return '否';
    return (row.imageReadyCount ?? 0) >= row.claimCount ? '是' : '否';
  };

  const columns = [
    { title: '序号', key: 'idx', width: 60,
      render: (_: unknown, __: SignBatch, index: number) => (page - 1) * size + index + 1 },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 170 },
    { title: '签收方式', dataIndex: 'signType', key: 'signType', width: 90,
      render: (v: string) => SIGN_TYPE_LABEL[v] ?? v },
    { title: '签收渠道', dataIndex: 'signChannel', key: 'signChannel', width: 110,
      render: (v: string) => (v && v !== 'UNKNOWN' ? CHANNEL_LABEL[v] ?? v : '-') },
    { title: '赔案数', dataIndex: 'claimCount', key: 'claimCount', width: 80 },
    { title: '紧急程度', dataIndex: 'emergency', key: 'emergency', width: 100,
      render: (v: string) => v || '-' },
    { title: '保险公司', dataIndex: 'insuranceCompany', key: 'insuranceCompany', width: 180,
      render: (v: string) => <Tooltip title={v}>{v || '-'}</Tooltip> },
    { title: '保险分公司', dataIndex: 'insuranceSubsidiary', key: 'insuranceSubsidiary', width: 180,
      render: (v: string) => <Tooltip title={v}>{v || '-'}</Tooltip> },
    { title: '投保公司', dataIndex: 'insuringAgency', key: 'insuringAgency', width: 160,
      render: (v: string) => <Tooltip title={v}>{v || '-'}</Tooltip> },
    { title: '有上传影像', key: 'imageUploaded', width: 100, render: (_: unknown, row: SignBatch) => imageText(row) },
    {
      title: '签收状态', dataIndex: 'signStatus', key: 'signStatus', width: 130,
      render: (v: string) => <Tag color={STATUS_COLOR[v] ?? 'default'}>{STATUS_LABEL[v] ?? v}</Tag>,
    },
    { title: '签收人员', dataIndex: 'signOperator', key: 'signOperator', width: 110 },
    { title: '签收时间', dataIndex: 'signTime', key: 'signTime', width: 175,
      render: (v: string) => (v ? formatDate(v) : '-') },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 175,
      render: (v: string) => (v ? formatDate(v) : '-') },
    { title: '备注', dataIndex: 'signRemark', key: 'signRemark', width: 160,
      render: (v: string) => <Tooltip title={v}>{v || '-'}</Tooltip> },
    {
      title: [
        '自动建案',
        <Tooltip key="tip" title="新建 / 跳过 / 待人工，点击查看轨迹明细">
          <InfoCircleOutlined style={{ marginLeft: 4, color: '#999' }} />
        </Tooltip>,
      ],
      key: 'caseBuild', width: 130,
      render: (_: unknown, row: SignBatch) => (
        <span data-testid="sign-case-summary">
          {row.createdCaseCount ?? 0} / {row.skippedCaseCount ?? 0} / {row.manualCaseCount ?? 0}
        </span>
      ),
    },
    {
      title: '操作', key: 'action', width: 250, fixed: 'right' as const,
      render: (_: unknown, row: SignBatch) => (
        <Space size={4} wrap>
          <Button type="link" size="small" data-testid="sign-detail"
            onClick={() => void showDetail(row.batchId)}>批次详情</Button>
          {row.signStatus === 'WAIT_FOR_PARTICIPANT' && (
            <AuthButton code={PERMISSIONS.CLAIMS_WRITE} type="link" size="small"
              data-testid="sign-import" loading={actingId === row.batchId}
              onClick={() => setParticipantsOpen(row.batchId)}>
              导入名单
            </AuthButton>
          )}
          {row.signStatus === 'WAIT_FOR_IMAGE' && (
            <AuthButton code={PERMISSIONS.CLAIMS_WRITE} type="link" size="small"
              data-testid="sign-image" loading={actingId === row.batchId}
              onClick={() => void runAction(row.batchId,
                () => claimSignApi.registerImage(row.batchId), '影像已登记')}>
              登记影像
            </AuthButton>
          )}
          {row.signStatus === 'WAIT_FOR_CONFIRMATION' && (
            <AuthButton code={PERMISSIONS.CLAIMS_WRITE} type="link" size="small"
              data-testid="sign-confirm" loading={actingId === row.batchId}
              onClick={() => Modal.confirm({
                title: '确认完成签收',
                content: '请确认本批次下所有赔案均已完成影像上传。确认后案件进入初审。',
                okText: '确认签收', cancelText: '取消',
                onOk: () => confirmSign(row.batchId),
              })}>
              确认签收
            </AuthButton>
          )}
          <Button type="link" size="small" data-testid="sign-tracks"
            onClick={() => void showDetail(row.batchId, 'tracks')}>轨迹</Button>
        </Space>
      ),
    },
  ];

  const itemColumns = [
    { title: '赔案号', dataIndex: 'claimNo', key: 'claimNo', width: 150 },
    { title: '保单号', dataIndex: 'policyNo', key: 'policyNo', width: 180 },
    { title: '出险人', dataIndex: 'insuredName', key: 'insuredName', width: 110 },
    { title: '证件类型', dataIndex: 'identityType', key: 'identityType', width: 100,
      render: (v: string) => v || '-' },
    { title: '证件号', dataIndex: 'insuredIdNo', key: 'insuredIdNo', width: 180 },
    { title: '清点张数', dataIndex: 'countingResult', key: 'countingResult', width: 100,
      render: (v: number) => v ?? '-' },
    { title: '影像是否上传', dataIndex: 'imageReady', key: 'imageReady', width: 110,
      render: (v: boolean) => (v ? '是' : '否') },
    { title: '影像数量', dataIndex: 'imageCount', key: 'imageCount', width: 100,
      render: (v: number) => v ?? '-' },
    { title: '影像上传次数', dataIndex: 'imageUploadCount', key: 'imageUploadCount', width: 120,
      render: (v: number) => v ?? '-' },
    { title: '影像上传时间', dataIndex: 'imageUploadTime', key: 'imageUploadTime', width: 175,
      render: (v: string) => (v ? formatDate(v) : '-') },
    { title: '影像操作人员', dataIndex: 'imageUploader', key: 'imageUploader', width: 120,
      render: (v: string) => v || '-' },
    { title: '建案状态', dataIndex: 'caseStatus', key: 'caseStatus', width: 120,
      render: (v: string) => <Tag>{v || '-'}</Tag> },
  ];

  const batch = detail.batch;
  const summary = [
    ['批次号', batch?.batchNo], ['赔案数', batch?.claimCount],
    ['保险公司', batch?.insuranceCompany], ['保险分公司', batch?.insuranceSubsidiary],
    ['投保公司', batch?.insuringAgency], ['签收渠道', batch?.signChannel],
    ['创建时间', batch?.createdAt ? formatDate(batch.createdAt) : undefined],
  ];

  const emptyText = (
    <Empty description={source === 'ONLINE' ? '暂无线上自动签收记录' : '暂无签收批次'}>
      {source !== 'ONLINE' && (
        <AuthButton code={PERMISSIONS.CLAIMS_WRITE} type="primary"
          data-testid="sign-empty-create" onClick={() => setCreateOpen(true)}>
          新建第一批
        </AuthButton>
      )}
    </Empty>
  );

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="案件签收" description="线上自动签收与线下人工签收统一入口" />
      <Card>
        <Tabs activeKey={source} onChange={(k) => { setSource(k as SourceTab); setPage(1); }}
          items={[
            { key: 'ALL', label: '全部' },
            { key: 'OFFLINE', label: '线下签收' },
            { key: 'ONLINE', label: '线上签收' },
          ]} />
        {source === 'ONLINE' && (
          <Alert type="info" showIcon style={{ marginBottom: 16 }}
            message="线上签收由系统自动完成"
            description="报案建案末端与外部推送回执会自动创建签收批次，无需人工操作；此处用于查询自动签收结果与轨迹。" />
        )}
        {loadError && (
          <Alert type="error" showIcon data-testid="sign-load-error" style={{ marginBottom: 16 }}
            message="签收批次加载失败" description={loadError}
            action={<Button size="small" data-testid="sign-retry" onClick={() => void load()}>重试</Button>} />
        )}
        <Form<FilterForm> form={filterForm} layout="vertical" size="small"
          style={{ marginBottom: 8 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 12 }}>
            <Form.Item name="batchNo" label="批次号"><Input allowClear placeholder="请输入批次号" /></Form.Item>
            <Form.Item name="signStatus" label="签收状态">
              <Select allowClear placeholder="请选择签收状态"
                options={Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="signType" label="签收方式">
              <Select allowClear placeholder="请选择签收方式"
                options={[{ value: 'MANUAL', label: '线下' }, { value: 'AUTO', label: '线上' }]} />
            </Form.Item>
            <Form.Item name="signChannel" label="签收渠道">
              <Select allowClear placeholder="请选择签收渠道" showSearch
                options={[{ value: 'EXPRESS', label: '快递签收' }, { value: 'COUNTER', label: '上门签收' }]} />
            </Form.Item>
            <Form.Item name="insuranceCompany" label="保险公司"><Input allowClear placeholder="请输入保险公司" /></Form.Item>
            <Form.Item name="insuranceSubsidiary" label="保险分公司"><Input allowClear placeholder="请输入保险分公司" /></Form.Item>
            <Form.Item name="insuringAgency" label="投保公司"><Input allowClear placeholder="请输入投保公司" /></Form.Item>
            <Form.Item name="signOperator" label="签收人员"><Input allowClear placeholder="请输入签收人员" /></Form.Item>
            <Form.Item name="signTime" label="签收时间">
              <DatePicker.RangePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="createTime" label="创建时间">
              <DatePicker.RangePicker showTime style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Space>
            <Button type="primary" data-testid="sign-search" onClick={() => void search()}>搜索</Button>
            <Button data-testid="sign-reset" onClick={reset}>重置</Button>
            {source !== 'ONLINE' && (
              <AuthButton code={PERMISSIONS.CLAIMS_WRITE} type="primary" icon={<SafetyCertificateOutlined />}
                data-testid="sign-create" onClick={() => setCreateOpen(true)}>新批次签收</AuthButton>
            )}
            <Input placeholder="确认签收意见（不少于10字）" value={confirmRemark}
              onChange={(e) => setConfirmRemark(e.target.value)} style={{ width: 280 }} />
            <Button onClick={() => void load()}>刷新</Button>
          </Space>
        </Form>
        <Table<SignBatch> rowKey="batchNo" columns={columns} dataSource={rows} loading={loading}
          locale={{ emptyText }} scroll={{ x: 2200 }}
          pagination={{ current: page, pageSize: size, total, showSizeChanger: true,
            pageSizeOptions: [20, 50, 100], showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => { if (s !== size) { setSize(s); setPage(1); } else setPage(p); } }} />
      </Card>

      <Modal open={createOpen} title="新批次签收" okText="创建" cancelText="取消"
        onCancel={() => setCreateOpen(false)}
        onOk={async () => {
          try {
            const values = await createForm.validateFields();
            await claimSignApi.createOfflineBatch({
              signChannel: values.signChannel ?? 'EXPRESS',
              expressCompany: values.expressCompany,
              expressNo: values.expressNo,
              sender: values.sender,
              insuranceCompany: values.insuranceCompany,
              insuranceSubsidiary: values.insuranceSubsidiary,
              insuringAgency: values.insuringAgency,
              emergency: values.emergency,
            });
            message.success({ content: '批次已创建', key: MSG_KEY });
            setCreateOpen(false);
            createForm.resetFields();
            await load();
          } catch (err) {
            message.error({ content: resolveErrorMessage(err, '创建失败'), key: MSG_KEY });
          }
        }}>
        <Form form={createForm} layout="vertical" size="small">
          <Form.Item name="signChannel" label="签收渠道" initialValue="EXPRESS"
            rules={[{ required: true, message: '请选择签收渠道' }]}>
            <Select options={[
              { value: 'EXPRESS', label: 'EXPRESS · 快递签收' },
              { value: 'COUNTER', label: 'COUNTER · 上门签收' },
            ]} />
          </Form.Item>
          <Form.Item name="insuranceCompany" label="保险公司"><Input /></Form.Item>
          <Form.Item name="insuranceSubsidiary" label="保险分公司"><Input /></Form.Item>
          <Form.Item name="insuringAgency" label="投保公司"><Input /></Form.Item>
          <Form.Item name="emergency" label="紧急程度">
            <Select allowClear options={[{ value: '正常', label: '正常' }, { value: '紧急', label: '紧急' }]} />
          </Form.Item>
          <Form.Item name="expressCompany" label="快递公司"><Input /></Form.Item>
          <Form.Item name="expressNo" label="快递单号"><Input /></Form.Item>
          <Form.Item name="sender" label="发件人"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal open={participantsOpen !== null} title="导入人员名单（赔案ID）" okText="确认导入"
        cancelText="取消" onCancel={() => setParticipantsOpen(null)}
        onOk={async () => {
          const batchId = participantsOpen;
          if (!batchId) return;
          const ids = (participantsForm.getFieldValue('claimIds') as string | undefined)
            ?.split(/[\s,，]+/).filter(Boolean) ?? [];
          if (!ids.length) {
            message.error({ content: '请填写至少一个赔案ID', key: MSG_KEY });
            return;
          }
          await runAction(batchId, () => claimSignApi.importParticipants(batchId, ids), '名单已导入');
          setParticipantsOpen(null);
        }}>
        <Form form={participantsForm} layout="vertical" size="small">
          <Form.Item name="claimIds" label="赔案ID列表（逗号/空格分隔，19位雪花ID保持字符串）">
            <Input.TextArea rows={4} placeholder="例如：1900000000000000001,1900000000000000002" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer open={detailOpen !== null} title="签收批次详情" width={960}
        onClose={() => setDetailOpen(null)} destroyOnHidden>
        <Space size={24} wrap style={{ marginBottom: 16 }}>
          {summary.map(([label, value]) => (
            <span key={label} style={{ fontSize: 12 }}>
              <Typography.Text type="secondary">{label}：</Typography.Text>
              {value || '-'}
            </span>
          ))}
        </Space>
        <Tabs activeKey={detailTab} onChange={(k) => setDetailTab(k as DetailTab)} items={DETAIL_TABS.map((t) => ({
          key: t.key,
          label: t.label,
          children: t.key === 'items' ? (
            <Table<SignBatchItem> rowKey="itemId" columns={itemColumns} dataSource={detail.items}
              size="small" scroll={{ x: 1500 }} pagination={false} />
          ) : t.key === 'tracks' ? (
            <Timeline items={detail.tracks.map((track) => ({
              color: track.signStatus === 'FINISHED' ? 'green' : 'blue',
              children: (
                <div>
                  <Typography.Text strong>{track.message ?? track.actionType}</Typography.Text>
                  <div>{track.operator ?? '系统'} · {track.occurredAt}</div>
                  {track.remark && <div>{track.remark}</div>}
                </div>
              ),
            }))} />
          ) : (
            <div>
              {detail.caseItems.length === 0 ? (
                <Empty description="暂无自动建案记录" />
              ) : detail.caseItems.map((item) => (
                <div key={item.itemId} style={{ marginBottom: 8 }}>
                  <Tag color={item.caseStatus === 'NEW' ? 'blue' : item.caseStatus === 'SKIPPED' ? 'default' : 'orange'}>
                    {item.caseStatus}
                  </Tag>
                  <span>{item.claimNo}</span>
                  {item.caseId && <span> · 案件 {item.caseId}</span>}
                  <div style={{ color: '#666', fontSize: 12 }}>{item.result}</div>
                </div>
              ))}
            </div>
          ),
        }))} />
      </Drawer>
    </div>
  );
}
