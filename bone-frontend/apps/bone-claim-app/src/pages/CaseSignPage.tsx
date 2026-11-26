/**
 * 案件签收（线上自动签收 + 线下人工批次签收）。
 *
 * 与参考 TPA（普康团险签收）同构：批次为单位、四态状态机、二次确认签收、全程轨迹；
 * 差异在 Bone 的技术栈（React18 + antd5）与显式 source/signType/signChannel 三维字段。
 *
 * 可观测性：加载失败不再只是一闪而过的 toast，而是页面内持久 Alert + 重试；
 * 对 404/403/5xx 给出可操作提示，避免「点了没反应/请求的资源不存在」这类无法自助排查的状态。
 */
import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
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
import { InfoCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import { AuthButton } from '@bone/ui';
import { PERMISSIONS } from '../constants/permissions';
import { formatDate, resolveErrorMessage } from '@bone/shared-utils';
import {
  claimSignApi,
  type SignBatch,
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

/**
 * 把 HTTP 错误翻译成「用户下一步能做什么」。
 *
 * 404 是本功能最常见的部署错位：菜单来自数据库种子，但后端 jar 未含签收端点。
 * 只说「请求的资源不存在」会让用户以为是页面 bug，故显式点出服务未部署。
 */
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

/** 具名导出（routes.tsx 用 .then 映射，禁 default 导出）。 */
export function CaseSignPage(): ReactElement {
  const { message } = App.useApp();
  const [source, setSource] = useState<'ONLINE' | 'OFFLINE'>('OFFLINE');
  const [rows, setRows] = useState<SignBatch[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [participantsOpen, setParticipantsOpen] = useState<string | null>(null);
  const [tracksOpen, setTracksOpen] = useState<string | null>(null);
  const [tracks, setTracks] = useState<SignTrack[]>([]);
  const [caseItems, setCaseItems] = useState<SignCaseItem[]>([]);
  // 确认签收意见由页面输入；Modal.confirm 的 onOk 闭包捕获点击时的 state，无需伪 ref。
  const [confirmRemark, setConfirmRemark] = useState('影像齐全，确认完成签收');
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const params: SignBatchListParams = { source, page, size };
      const resp = await claimSignApi.list(params);
      setRows(resp?.data?.records ?? []);
      setTotal(Number(resp?.data?.total ?? 0) || 0);
    } catch (err) {
      const hint = describeSignLoadError(err);
      setLoadError(hint);
      message.error({ content: hint, key: MSG_KEY });
    } finally {
      setLoading(false);
    }
  }, [source, page, size, message]);

  useEffect(() => {
    void load();
  }, [load]);

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

  const showTracks = async (batchId: string) => {
    setTracksOpen(batchId);
    setTracks([]);
    setCaseItems([]);
    try {
      const [trackResult, detailResult] = await Promise.allSettled([
        claimSignApi.tracks(batchId),
        claimSignApi.detail(batchId),
      ]);
      if (trackResult.status === 'fulfilled') {
        setTracks(trackResult.value?.data ?? []);
      } else {
        message.error({
          content: resolveErrorMessage(trackResult.reason, '轨迹加载失败'),
          key: MSG_KEY,
        });
      }
      if (detailResult.status === 'fulfilled') {
        setCaseItems(detailResult.value?.data?.caseItems ?? []);
      } else {
        message.error({
          content: resolveErrorMessage(detailResult.reason, '自动建案结果加载失败'),
          key: `${MSG_KEY}-case`,
        });
      }
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '轨迹加载失败'), key: MSG_KEY });
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

  const columns = [
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 180 },
    {
      title: '状态', dataIndex: 'signStatus', key: 'signStatus', width: 140,
      render: (v: string) => <Tag color={STATUS_COLOR[v] ?? 'default'}>{STATUS_LABEL[v] ?? v}</Tag>,
    },
    { title: '签收方式', dataIndex: 'signType', key: 'signType', width: 110,
      render: (v: string) => (v === 'AUTO' ? '自动' : '人工') },
    { title: '渠道', dataIndex: 'signChannel', key: 'signChannel', width: 130 },
    {
      title: [
        '影像就绪/明细',
        <Tooltip key="tip" title="已完成影像上传的赔案数 / 批次内赔案总数">
          <InfoCircleOutlined style={{ marginLeft: 4, color: '#999' }} />
        </Tooltip>,
      ],
      dataIndex: 'claimCount', key: 'claimCount', width: 130,
      render: (v: number, row: SignBatch) => `${row.imageReadyCount ?? 0}/${v ?? 0}`,
    },
    {
      title: [
        '自动建案',
        <Tooltip key="tip" title="新建案件数 / 跳过数 / 待人工数">
          <InfoCircleOutlined style={{ marginLeft: 4, color: '#999' }} />
        </Tooltip>,
      ],
      key: 'caseBuild', width: 140,
      render: (_: unknown, row: SignBatch) => (
        <Tooltip title="新建 / 跳过 / 待人工，点击查看轨迹明细">
          <span data-testid="sign-case-summary">
            {row.createdCaseCount ?? 0} / {row.skippedCaseCount ?? 0} / {row.manualCaseCount ?? 0}
          </span>
        </Tooltip>
      ),
    },
    { title: '签收人', dataIndex: 'signOperator', key: 'signOperator', width: 120 },
    {
      title: '签收时间', dataIndex: 'signTime', key: 'signTime', width: 190,
      render: (v: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作', key: 'action', width: 260,
      render: (_: unknown, row: SignBatch) => (
        <Space size={4} wrap>
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
            onClick={() => void showTracks(row.batchId)}>轨迹</Button>
        </Space>
      ),
    },
  ];

  const emptyText = (
    <Empty
      description={source === 'ONLINE' ? '暂无线上自动签收记录' : '暂无线下签收批次'}
    >
      {source === 'OFFLINE' ? (
        <AuthButton code={PERMISSIONS.CLAIMS_WRITE} type="primary"
          data-testid="sign-empty-create" onClick={() => setCreateOpen(true)}>
          新建第一批
        </AuthButton>
      ) : null}
    </Empty>
  );

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="案件签收" description="线上自动签收与线下人工签收统一入口" />
      <Card>
        <Tabs activeKey={source} onChange={(k) => { setSource(k as 'ONLINE' | 'OFFLINE'); setPage(1); }}
          items={[
            { key: 'ONLINE', label: '线上自动签收', children: null },
            { key: 'OFFLINE', label: '线下签收', children: null },
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
        <Space style={{ marginBottom: 16 }} wrap>
          {source === 'OFFLINE' && (
            <AuthButton code={PERMISSIONS.CLAIMS_WRITE} type="primary" icon={<SafetyCertificateOutlined />}
              data-testid="sign-create" onClick={() => setCreateOpen(true)}>新建批次</AuthButton>
          )}
          {source === 'OFFLINE' && (
            <Input placeholder="确认签收意见（不少于10字）" value={confirmRemark}
              onChange={(e) => setConfirmRemark(e.target.value)} style={{ width: 320 }} />
          )}
          <Button onClick={() => void load()}>刷新</Button>
        </Space>
        <Table<SignBatch> rowKey="batchNo" columns={columns} dataSource={rows} loading={loading}
          locale={{ emptyText }}
          scroll={{ x: 1180 }}
          pagination={{ current: page, pageSize: size, total, showSizeChanger: true,
            pageSizeOptions: [20, 50, 100], showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => { if (s !== size) { setSize(s); setPage(1); } else setPage(p); } }} />
      </Card>

      <Modal open={createOpen} title="新建线下签收批次" okText="创建" cancelText="取消"
        onCancel={() => setCreateOpen(false)}
        onOk={async () => {
          try {
            const values = await form.validateFields();
            await claimSignApi.createOfflineBatch({
              signChannel: values.signChannel ?? 'EXPRESS',
              expressCompany: values.expressCompany,
              expressNo: values.expressNo,
              sender: values.sender,
              arriveTime: values.arriveTime,
            });
            message.success({ content: '批次已创建', key: MSG_KEY });
            setCreateOpen(false);
            form.resetFields();
            await load();
          } catch (err) {
            message.error({ content: resolveErrorMessage(err, '创建失败'), key: MSG_KEY });
          }
        }}>
        <Form form={form} layout="vertical" size="small">
          <Form.Item name="signChannel" label="签收渠道" initialValue="EXPRESS"
            rules={[{ required: true, message: '请选择签收渠道' }]}>
            <Select options={[
              { value: 'EXPRESS', label: 'EXPRESS · 快递寄送' },
              { value: 'COUNTER', label: 'COUNTER · 柜面递交' },
            ]} />
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
          const ids = (form.getFieldValue('claimIds') as string | undefined)?.split(/[\s,，]+/).filter(Boolean) ?? [];
          if (!ids.length) {
            message.error({ content: '请填写至少一个赔案ID', key: MSG_KEY });
            return;
          }
          await runAction(batchId, () => claimSignApi.importParticipants(batchId, ids), '名单已导入');
          setParticipantsOpen(null);
        }}>
        <Form form={form} layout="vertical" size="small">
          <Form.Item name="claimIds" label="赔案ID列表（逗号/空格分隔，19位雪花ID保持字符串）">
            <Input.TextArea rows={4} placeholder="例如：1900000000000000001,1900000000000000002" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer open={tracksOpen !== null} title="签收轨迹" width={520}
        onClose={() => setTracksOpen(null)} destroyOnHidden>
        {caseItems.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <Typography.Text strong>自动建案结果</Typography.Text>
            {caseItems.map((item) => (
              <div key={item.itemId} style={{ marginTop: 6 }}>
                <Tag color={item.caseStatus === 'NEW' ? 'blue' : item.caseStatus === 'SKIPPED' ? 'default' : 'orange'}>
                  {item.caseStatus}
                </Tag>
                <span>{item.claimNo}</span>
                {item.caseId && <span> · 案件 {item.caseId}</span>}
                <div style={{ color: '#666', fontSize: 12 }}>{item.result}</div>
              </div>
            ))}
          </div>
        )}
        <Timeline items={tracks.map((t) => ({
          color: t.signStatus === 'FINISHED' ? 'green' : 'blue',
          children: (
            <div>
              <Typography.Text strong>{t.message ?? t.actionType}</Typography.Text>
              <div>{t.operator ?? '系统'} · {t.occurredAt}</div>
              {t.remark && <div>{t.remark}</div>}
            </div>
          ),
        }))} />
      </Drawer>
    </div>
  );
}
