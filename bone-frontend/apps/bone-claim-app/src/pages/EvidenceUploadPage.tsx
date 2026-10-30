import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';
import {
  Button,
  Card,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
  type TableColumnsType,
} from 'antd';
import {
  UploadOutlined,
  LockOutlined,
  ReloadOutlined,
  DownloadOutlined,
  FileSearchOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { attachmentApi, evidenceApi, type EvidenceResp } from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';
import { CaseIdBar, CaseNotSelected } from '../components/CaseIdBar';
import { useClaimIdParam } from '../hooks/useClaimIdParam';
import { resolveErrorMessage } from '@bone/shared-utils';

/**
 * 材料上传页（synapClaim A2，登记 + 展示）。
 *
 * 关键约束（与后端 EvidenceController §7.2 一致）：影像本体**不走 synap-claim**，
 * 前端直传平台附件服务 bone-file（`attachmentApi.upload`）拿 `objectName` 即 `fileRef`，
 * 本地算 SHA-256 得 `contentHash`，再 `evidenceApi.register`（只登记 fileRef+哈希）。
 * 列表以服务端为准（server-authoritative），避免前端自造第二数据。
 *
 * <p><b>本次改造的三处硬缺陷</b>：
 * <ol>
 *   <li>原 `searchParams.get('claimId') ?? '1'` 进页面即对「案件 1」发请求——未命中时 404 被兜底成
 *       「材料列表加载失败」，把「还没选案件」报成系统故障。现改为未选择即空态、不发请求。
 *   <li>错误文案手写了 3 个 if 分支（其中 `SC_CLAIM_FILE_UPLOAD_FAILED` 后端根本没有），且其余全落
 *       「登记失败」。现统一走 i18n 码表（`errors.SC_CLAIM_*`），码与文案不再各写一遍。
 *   <li>多个文件同时选时，哈希+上传期间界面零反馈（大文件可达数十秒），用户会重复点击。
 *       现按文件追踪阶段（计算哈希 → 上传 %）并禁止重复进队。
 * </ol>
 */

/** 材料类型（与后端 `DocType` 枚举逐一对应，勿在此增删）。 */
const DOC_TYPES: { value: string; label: string }[] = [
  { value: 'INVOICE', label: '发票' },
  { value: 'LIST', label: '费用清单' },
  { value: 'MEDICAL_RECORD', label: '病历' },
  { value: 'MEDICAL_SETTLEMENT', label: '医保结算单' },
];

const OCR_STATUS: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待解析' },
  DONE: { color: 'green', label: '已解析' },
  MANUAL: { color: 'orange', label: '人工补录' },
  FAILED: { color: 'red', label: '解析失败' },
};

const docTypeLabel = (v?: string): string => DOC_TYPES.find((d) => d.value === v)?.label ?? v ?? '-';
const ocrStatus = (v?: string) =>
  v ? (OCR_STATUS[v] ?? { color: 'default', label: v }) : { color: 'default', label: '待解析' };

/**
 * 内容哈希（去重依据）。
 *
 * <p>已知限制：`crypto.subtle.digest` 只接受整块 ArrayBuffer，没有流式 API，故大文件会整体驻留内存
 * （一份 50MB 扫描件 ≈ 50MB 堆 + 100MB hex 字符串峰值）。理赔影像量级（发票/清单/病历）通常远低于此，
 * 因此不引入第三方增量哈希实现；但**必须在 UI 上给出「正在计算哈希」阶段**，否则用户以为没反应。
 */
async function sha256Hex(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/** 单文件在途阶段（用于进度提示与防重复）。 */
interface UploadJob {
  /** 0–100；哈希阶段不占百分比（未知耗时），统一显示为 0 */
  percent: number;
  stage: 'hashing' | 'uploading' | 'registering';
}

const columns: TableColumnsType<EvidenceResp> = [
  {
    title: '材料类型',
    dataIndex: 'docType',
    key: 'docType',
    render: (v?: string) => <Tag color="blue">{docTypeLabel(v)}</Tag>,
  },
  {
    title: '文件引用(fileRef)',
    dataIndex: 'fileRef',
    key: 'fileRef',
    ellipsis: true,
    render: (v?: string) =>
      v ? (
        <a onClick={() => window.open(`/api/v1/file/files/${encodeURIComponent(v)}`, '_blank')}>
          <DownloadOutlined /> {v.length > 24 ? `${v.slice(0, 12)}…${v.slice(-8)}` : v}
        </a>
      ) : (
        '-'
      ),
  },
  {
    title: '内容哈希',
    dataIndex: 'contentHash',
    key: 'contentHash',
    ellipsis: true,
    render: (v?: string) => (v ? <Typography.Text copyable ellipsis>{v}</Typography.Text> : '-'),
  },
  {
    title: '解析状态',
    dataIndex: 'ocrStatus',
    key: 'ocrStatus',
    render: (v?: string) => {
      const s = ocrStatus(v);
      return <Tag color={s.color}>{s.label}</Tag>;
    },
  },
  {
    title: 'OCR 置信度',
    dataIndex: 'ocrConfidence',
    key: 'ocrConfidence',
    render: (v?: number) => (v == null ? '-' : `${(v * 100).toFixed(1)}%`),
  },
  {
    title: '校验有效性',
    dataIndex: 'verifyValid',
    key: 'verifyValid',
    ellipsis: true,
    render: (v?: string) => v ?? '-',
  },
  {
    title: '已锁定',
    dataIndex: 'locked',
    key: 'locked',
    render: (v?: boolean) => (v ? <Tag color="red">已锁定</Tag> : <Tag>可改</Tag>),
  },
];

/** 同类失败共用 message key：重复触发时替换而非堆叠。 */
const MSG_KEY_UPLOAD = 'claim-evidence-upload';

export function EvidenceUploadPage(): ReactElement {
  const { claimId, input, setInput, submit } = useClaimIdParam();
  const [docType, setDocType] = useState('INVOICE');
  const [rows, setRows] = useState<EvidenceResp[]>([]);
  const [loading, setLoading] = useState(false);
  const [locking, setLocking] = useState(false);
  /** 在途文件：key = 文件名+大小+最后修改时间（同一文件重选不会被当成同一任务） */
  const [jobs, setJobs] = useState<Record<string, UploadJob>>({});
  /** 卸载后不再 setState（上传/哈希是长任务，用户可能中途切页） */
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const load = useCallback(async () => {
    if (!claimId) {
      setRows([]);
      return;
    }
    setLoading(true);
    try {
      const resp = await evidenceApi.list(claimId);
      if (mounted.current) setRows(resp?.data ?? []);
    } catch (err) {
      // 查无此案是服务端答复，与「没选案件」区分开；其余错误按码表出文案
      if (mounted.current) message.error(resolveErrorMessage(err, '材料列表加载失败'));
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const patchJob = (key: string, patch: Partial<UploadJob>) =>
    setJobs((prev) => (prev[key] ? { ...prev, [key]: { ...prev[key], ...patch } } : prev));

  const handleUpload = async (file: File, jobKey: string) => {
    try {
      patchJob(jobKey, { stage: 'hashing', percent: 0 });
      const contentHash = await sha256Hex(file);
      patchJob(jobKey, { stage: 'uploading', percent: 0 });
      const uploaded = await attachmentApi.upload(file, {
        onProgress: (p) => patchJob(jobKey, { percent: p }),
      });
      const fileRef = uploaded?.data?.objectName;
      if (!fileRef) {
        message.error('附件服务已受理但未返回 fileRef，请检查 bone-file 配置');
        return;
      }
      patchJob(jobKey, { stage: 'registering', percent: 100 });
      await evidenceApi.register(claimId, { fileRef, contentHash, docType });
      message.success({ content: `已登记：${file.name}`, key: MSG_KEY_UPLOAD });
      await load();
    } catch (err) {
      // 份数超限 / 内容重复 / 证据已锁定 等都已在 zh-CN 有译文，不再逐个 if 手写
      message.error({ content: resolveErrorMessage(err, `登记失败：${file.name}`), key: MSG_KEY_UPLOAD });
    } finally {
      if (mounted.current) {
        setJobs((prev) => {
          const next = { ...prev };
          delete next[jobKey];
          return next;
        });
      }
    }
  };

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    // 未选案件时不该进队：否则 fileRef 已传到附件服务、登记却必然 404
    if (!claimId) {
      message.warning('请先输入案件ID并查询');
      return false;
    }
    const jobKey = `${file.name}@${file.size}@${file.lastModified}`;
    setJobs((prev) => (prev[jobKey] ? prev : { ...prev, [jobKey]: { percent: 0, stage: 'hashing' } }));
    void handleUpload(file as File, jobKey);
    return false; // 阻止 antd 默认上传，由我们控制 fileRef + 内容哈希 + 登记
  };

  const onLock = async () => {
    setLocking(true);
    try {
      await evidenceApi.lock(claimId);
      message.success('证据集已锁定（理算前置）');
      await load();
    } catch (err) {
      message.error(resolveErrorMessage(err, '锁定失败'));
    } finally {
      if (mounted.current) setLocking(false);
    }
  };

  const jobList = Object.entries(jobs);
  const allLocked = rows.length > 0 && rows.every((r) => r.locked);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="材料上传" />
      <Card
        title="材料清单"
        extra={
          <CaseIdBar
            value={input}
            onChange={setInput}
            onSubmit={submit}
            loading={loading}
            extra={
              <Button
                icon={<ReloadOutlined />}
                onClick={() => void load()}
                disabled={!claimId}
                loading={loading}
              >
                刷新
              </Button>
            }
          />
        }
      >
        {!claimId ? (
          <CaseNotSelected hint="材料按案件归集，输入案件ID后即可上传与查看" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space wrap align="center">
              <Select
                value={docType}
                onChange={setDocType}
                options={DOC_TYPES}
                style={{ width: 160 }}
                placeholder="材料类型"
              />
              <Upload beforeUpload={beforeUpload} showUploadList={false} multiple>
                <Button type="primary" icon={<UploadOutlined />} loading={jobList.length > 0}>
                  选择并上传材料
                </Button>
              </Upload>
              <Popconfirm
                title="锁定后不可再上传或回填"
                description="理算必须以锁定后的证据集为输入，锁定是一个单向动作。"
                okText="确认锁定"
                cancelText="取消"
                disabled={allLocked || rows.length === 0}
                onConfirm={() => void onLock()}
              >
                <Button
                  icon={<LockOutlined />}
                  loading={locking}
                  disabled={allLocked || rows.length === 0}
                >
                  锁定证据集{allLocked ? '（已锁定）' : ''}
                </Button>
              </Popconfirm>
              <Typography.Text type="secondary">
                单案件最多 20 份；相同内容哈希的材料会被去重。
              </Typography.Text>
            </Space>

            {jobList.length > 0 && (
              <Card type="inner" title={<Space><FileSearchOutlined />正在处理（{jobList.length}）</Space>} size="small">
                <Space direction="vertical" style={{ width: '100%' }}>
                  {jobList.map(([key, job]) => (
                    <div key={key}>
                      <Typography.Text ellipsis style={{ maxWidth: 420 }}>
                        {key.split('@')[0]}
                      </Typography.Text>
                      <Progress
                        percent={job.stage === 'registering' ? 100 : job.percent}
                        size="small"
                        status="active"
                        format={() =>
                          // 阶段文案比百分比更能解释「为什么慢」：哈希纯本地但不显示进度
                          job.stage === 'hashing'
                            ? '计算内容哈希…'
                            : job.stage === 'uploading'
                              ? `${job.percent}%`
                              : '登记中…'
                        }
                      />
                    </div>
                  ))}
                </Space>
              </Card>
            )}

            <Table<EvidenceResp>
              rowKey="id"
              columns={columns}
              dataSource={rows}
              loading={loading}
              size="small"
              pagination={false}
              locale={{ emptyText: '该案件暂无已登记材料' }}
            />
          </Space>
        )}
      </Card>
    </div>
  );
}
