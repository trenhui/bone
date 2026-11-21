import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';
import {
  Button,
  Card,
  Input,
  InputNumber,
  Modal,
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
  ZoomInOutlined,
  ZoomOutOutlined,
  RotateRightOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  FullscreenOutlined,
  QuestionCircleOutlined,
  EyeOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { attachmentApi, evidenceApi, type EvidenceResp } from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';
import { CaseIdBar, CaseNotSelected } from '../components/CaseIdBar';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { useClaimIdParam } from '../hooks/useClaimIdParam';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { AuthButton } from '@bone/ui';
import { PERMISSIONS } from '../constants/permissions';
import { FILE_NAME_INVALID_HINT, isSafeFileName } from '../utils/fileName';

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
 * 内容哈希（去重依据）：在 Worker 中计算，避免大文件 digest 卡住核赔员的主线程。
 *
 * <p>摘要口径与旧实现逐字节一致（仍是 crypto.subtle.digest SHA-256），
 * 只是把 CPU 密集段移出 UI 线程；阶段提示保留，便于用户解释"为什么慢"。
 */
async function sha256Hex(file: File): Promise<string> {
  const { hashFile } = await import('../utils/hashFile');
  return hashFile(file);
}

/** 单文件在途阶段（用于进度提示与防重复）。 */
interface UploadJob {
  /** 0–100；哈希阶段不占百分比（未知耗时），统一显示为 0 */
  percent: number;
  stage: 'hashing' | 'uploading' | 'registering';
}

/** 影像清晰度三态（与后端 ImageClarity 对齐；非 CLEAR 在锁定时 fail-closed 阻断入理算）。 */
const CLEAR_TYPE: Record<string, { color: string; label: string }> = {
  CLEAR: { color: 'green', label: '清晰' },
  UNCLEAR: { color: 'red', label: '不清晰' },
  INCOMPLETE: { color: 'orange', label: '不完整' },
};
const CLEAR_TYPE_OPTIONS = [
  { value: 'CLEAR', label: '清晰' },
  { value: 'UNCLEAR', label: '不清晰' },
  { value: 'INCOMPLETE', label: '不完整' },
];
const clearTypeMeta = (v?: string) =>
  v ? (CLEAR_TYPE[v] ?? { color: 'default', label: v }) : { color: 'default', label: '未标注' };

/**
 * 从 ProblemDetail 里取后端 detail（如不清晰证据 ID + 补传/标注引导）。
 * 锁定失败的 SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK 若走通用 resolveErrorMessage，
 * 就只剩一句码表译文，用户看不到该补传哪几张，故单独按码取 detail。
 */
function problemDetail(err: unknown): string {
  const e = err as {
    response?: { data?: { data?: { detail?: unknown } } };
    displayMessage?: unknown;
  } | null;
  const detail = e?.response?.data?.data?.detail;
  if (typeof detail === 'string' && detail.trim()) return detail.trim();
  const top = e?.displayMessage;
  return typeof top === 'string' && top.trim() ? top.trim() : '';
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
        <a onClick={() => window.open(attachmentApi.downloadUrl(v), '_blank')}>
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
  {
    title: '清晰度',
    dataIndex: 'clearType',
    key: 'clearType',
    render: (v?: string) => {
      const c = clearTypeMeta(v);
      return <Tag color={c.color}>{c.label}</Tag>;
    },
  },
];

/** 同类失败共用 message key：重复触发时替换而非堆叠。 */
const MSG_KEY_UPLOAD = 'claim-evidence-upload';

export interface CasePageProps {
  /**
   * 案件 360 内嵌时由 Tab 直接传入 claimId；**传入即表示「无壳内容形态」**：
   * 不渲染 PageHeader / CaseIdBar / 外层留白，业务逻辑与取数仍是同一份实现。
   * 旧路由不带此 prop，继续从 `?claimId=` 读取。
   */
  embeddedClaimId?: string;
}

export function EvidenceUploadPage({ embeddedClaimId }: CasePageProps = {}): ReactElement {
  const { claimId: urlClaimId, input, setInput, submit } = useClaimIdParam();
  const claimId = embeddedClaimId ?? urlClaimId;
  const [docType, setDocType] = useState('INVOICE');
  /** 表格勾选的影像（批量标注清晰度；单张即长度为 1 的 items）。 */
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  /** 操作区待应用的清晰度值。 */
  const [clearTypeDraft, setClearTypeDraft] = useState('CLEAR');
  const [applyingClear, setApplyingClear] = useState(false);
  const [rows, setRows] = useState<EvidenceResp[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  /** 锁定是单向动作：确认弹窗必须逐张列出将纳入的材料，不能只说「确认锁定」。 */
  const [lockOpen, setLockOpen] = useState(false);
  const [locking, setLocking] = useState(false);
  /** 在途文件：key = 文件名+大小+最后修改时间（同一文件重选不会被当成同一任务） */
  const [jobs, setJobs] = useState<Record<string, UploadJob>>({});
  /** 卸载后不再 setState（上传/哈希是长任务，用户可能中途切页） */
  const mounted = useRef(true);
  /** 影像作业台状态：分类树筛选、当前影像、缩放/旋转与快捷键帮助。 */
  const [activeDocType, setActiveDocType] = useState('ALL');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [manualTarget, setManualTarget] = useState<EvidenceResp | null>(null);
  const [manualConfidence, setManualConfidence] = useState(1);
  const [manualNote, setManualNote] = useState('');
  const previewRef = useRef<HTMLDivElement | null>(null);

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
    setLoadError(null);
    try {
      const resp = await evidenceApi.list(claimId);
      if (mounted.current) setRows(resp?.data ?? []);
    } catch (err) {
      // 查无此案是服务端答复，与「没选案件」区分开；其余错误按码表出文案
      // 持久错误 + 重试按钮：只弹 toast 会被用户划掉，随后空表看起来像"没有材料"。
      if (mounted.current) setLoadError(resolveErrorMessage(err, '材料列表加载失败'));
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

  const ACCEPTED_MIME = ['image/jpeg', 'image/png', 'application/pdf'];
  const MAX_FILE_BYTES = 20 * 1024 * 1024;
  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    // 未选案件时不该进队：否则 fileRef 已传到附件服务、登记却必然 404
    if (!claimId) {
      message.warning('请先输入案件ID并查询');
      return false;
    }
    // 前端闸门只做快速失败；服务端仍按 FileObjectKeyGenerator.assertSafeName 同口径复核。
    if (!isSafeFileName(file.name)) {
      message.error(FILE_NAME_INVALID_HINT);
      return false;
    }
    if ((file as File).type && !ACCEPTED_MIME.includes((file as File).type)) {
      message.error('仅支持 JPG / PNG / PDF 格式，请更换文件');
      return false;
    }
    if (file.size > MAX_FILE_BYTES) {
      message.error('单个文件不得超过 20MB，请压缩后重试');
      return false;
    }
    const jobKey = `${file.name}@${file.size}@${file.lastModified}`;
    setJobs((prev) => (prev[jobKey] ? prev : { ...prev, [jobKey]: { percent: 0, stage: 'hashing' } }));
    void handleUpload(file as File, jobKey);
    return false; // 阻止 antd 默认上传，由我们控制 fileRef + 内容哈希 + 登记
  };

  const onApplyClearType = async () => {
    if (!claimId || selectedRowKeys.length === 0) return;
    setApplyingClear(true);
    try {
      const items = selectedRowKeys.map((evidenceId) => ({ evidenceId, clearType: clearTypeDraft }));
      const updated = await evidenceApi.updateClearType(claimId, items);
      message.success(`已标注 ${updated?.data ?? items.length} 份影像为「${clearTypeMeta(clearTypeDraft).label}」`);
      setSelectedRowKeys([]);
      await load();
    } catch (err) {
      message.error(resolveErrorMessage(err, '清晰度标注失败'));
    } finally {
      if (mounted.current) setApplyingClear(false);
    }
  };

  const onLock = async (): Promise<boolean> => {
    setLocking(true);
    try {
      await evidenceApi.lock(claimId);
      message.success('证据集已锁定（理算前置）');
      await load();
      return true;
    } catch (err) {
      if (resolveErrorCode(err) === 'SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK') {
        // 后端 detail 带不清晰证据 ID 与补传/标注引导，不能被通用码表文案吞掉
        message.error(problemDetail(err) || resolveErrorMessage(err, '锁定失败'));
      } else {
        message.error(resolveErrorMessage(err, '锁定失败'));
      }
      return false;
    } finally {
      if (mounted.current) setLocking(false);
    }
  };

  const jobList = Object.entries(jobs);
  const allLocked = rows.length > 0 && rows.every((r) => r.locked);
  const embedded = Boolean(embeddedClaimId);

  // 分类树计数来自服务端返回的证据清单，不另造一份 mock 分类数据。
  const filteredRows = activeDocType === 'ALL' ? rows : rows.filter((r) => r.docType === activeDocType);
  const selected = filteredRows.find((r) => r.id === selectedId) ?? filteredRows[0] ?? null;
  const invoiceRows = rows.filter((r) => r.docType === 'INVOICE');

  useEffect(() => {
    if (filteredRows.length > 0 && !filteredRows.some((r) => r.id === selectedId)) {
      setSelectedId(filteredRows[0].id);
    }
  }, [filteredRows, selectedId]);

  /**
   * 影像键盘流转（旧系统十年打磨的效率核心）。
   * 焦点在输入控件时不触发——否则在备注里打 "r" 会旋转影像，属典型键盘劫持。
   */
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
      if (!filteredRows.length) return;
      const index = filteredRows.findIndex((r) => r.id === selected?.id);
      if (event.key === 'ArrowLeft') setSelectedId(filteredRows[Math.max(0, index - 1)].id);
      else if (event.key === 'ArrowRight')
        setSelectedId(filteredRows[Math.min(filteredRows.length - 1, index + 1)].id);
      else if (event.key === '+' || event.key === '=') setZoom((z) => Math.min(3, Number((z + 0.25).toFixed(2))));
      else if (event.key === '-' || event.key === '_') setZoom((z) => Math.max(0.5, Number((z - 0.25).toFixed(2))));
      else if (event.key.toLowerCase() === 'r') setRotation((r) => (r + 90) % 360);
      else if (event.key.toLowerCase() === 'f') void previewRef.current?.requestFullscreen?.();
      else return;
      event.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [filteredRows, selected?.id]);

  const onManualParsed = async () => {
    if (!manualTarget) return;
    try {
      await evidenceApi.markParsed(claimId, manualTarget.id, {
        ocrStatus: 'MANUAL',
        ocrConfidence: manualConfidence,
        verifyValid: manualNote || undefined,
      });
      message.success('人工补录已保存');
      setManualTarget(null);
      await load();
    } catch (err) {
      message.error(resolveErrorMessage(err, '人工补录失败'));
    }
  };

  return (
    <div
      data-case-content={embedded ? 'true' : undefined}
      style={embedded ? undefined : { padding: 24 }}
    >
      {embedded ? null : <PageHeader title="材料上传" />}
      <Card
        title={embedded ? undefined : '材料清单'}
        extra={
          <Space wrap>
            {embedded ? null : (
              <CaseIdBar value={input} onChange={setInput} onSubmit={submit} loading={loading} />
            )}
            <Button
              icon={<ReloadOutlined />}
              onClick={() => void load()}
              disabled={!claimId}
              loading={loading}
            >
              刷新
            </Button>
          </Space>
        }
      >
        {!claimId ? (
          <CaseNotSelected hint="材料按案件归集，输入案件ID后即可上传与查看" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space wrap align="center">
              <Select
                data-testid="evidence-doc-type"
                value={docType}
                onChange={setDocType}
                options={DOC_TYPES}
                style={{ width: 160 }}
                placeholder="材料类型"
              />
              <Upload beforeUpload={beforeUpload} showUploadList={false} multiple accept="image/jpeg,image/png,application/pdf" maxCount={20}>
                <AuthButton
                  data-testid="evidence-upload"
                  code={PERMISSIONS.CLAIMS_WRITE}
                  type="primary"
                  icon={<UploadOutlined />}
                  loading={jobList.length > 0}
                >
                  选择并上传材料
                </AuthButton>
              </Upload>
              <AuthButton
                data-testid="evidence-lock-open"
                code={PERMISSIONS.CLAIMS_WRITE}
                icon={<LockOutlined />}
                loading={locking}
                disabled={allLocked || rows.length === 0}
                onClick={() => setLockOpen(true)}
              >
                锁定证据集{allLocked ? '（已锁定）' : ''}
              </AuthButton>
              <Typography.Text type="secondary">
                单案件最多 20 份；相同内容哈希的材料会被去重。
              </Typography.Text>
            </Space>

            <Space wrap align="center">
              <Typography.Text type="secondary">
                已选 {selectedRowKeys.length} 份影像
              </Typography.Text>
              <Select
                value={clearTypeDraft}
                onChange={setClearTypeDraft}
                options={CLEAR_TYPE_OPTIONS}
                style={{ width: 140 }}
                placeholder="设置清晰度"
                aria-label="清晰度标注"
              />
              <Button
                disabled={selectedRowKeys.length === 0 || applyingClear}
                loading={applyingClear}
                onClick={() => void onApplyClearType()}
              >
                应用
              </Button>
              <Typography.Text type="secondary">
                不清晰/不完整影像会阻断证据集锁定，请补传或改标为清晰。
              </Typography.Text>
            </Space>

            <Card
              type="inner"
              size="small"
              title={<Space><EyeOutlined />影像作业台（分类 · 翻阅 · 关联发票）</Space>}
              extra={
                <Button
                  type="link"
                  size="small"
                  icon={<QuestionCircleOutlined />}
                  aria-keyshortcuts="F1"
                  onClick={() => setShortcutsOpen(true)}
                >
                  快捷键说明
                </Button>
              }
            >
              <div style={{ display: 'grid', gridTemplateColumns: '170px minmax(300px, 1fr) 250px', gap: 12, minHeight: 340 }}>
                {/* 左：分类树（计数取服务端清单，不写死 mock） */}
                <div role="tree" aria-label="影像分类" style={{ borderRight: '1px solid #f0f0f0', paddingRight: 8 }}>
                  <button
                    type="button"
                    role="treeitem"
                    aria-selected={activeDocType === 'ALL'}
                    data-testid="evidence-cat-all"
                    onClick={() => setActiveDocType('ALL')}
                    style={{ display: 'block', width: '100%', textAlign: 'left', border: 0, background: activeDocType === 'ALL' ? '#e6f4ff' : 'transparent', padding: '6px 8px', borderRadius: 4, cursor: 'pointer' }}
                  >
                    全部影像（{rows.length}）
                  </button>
                  {DOC_TYPES.map((d) => (
                    <button
                      key={d.value}
                      type="button"
                      role="treeitem"
                      aria-selected={activeDocType === d.value}
                      data-testid={`evidence-cat-${d.value}`}
                      onClick={() => setActiveDocType(d.value)}
                      style={{ display: 'block', width: '100%', textAlign: 'left', border: 0, background: activeDocType === d.value ? '#e6f4ff' : 'transparent', padding: '6px 8px', borderRadius: 4, cursor: 'pointer' }}
                    >
                      {d.label}（{rows.filter((r) => r.docType === d.value).length}）
                    </button>
                  ))}
                  <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
                    分类用于作业筛选，不改变已登记材料类型。
                  </Typography.Text>
                </div>

                {/* 中：影像预览与缩放/旋转 */}
                <div>
                  <Space size={4} wrap style={{ marginBottom: 8 }}>
                    <Button size="small" icon={<ArrowLeftOutlined />} aria-label="上一张" aria-keyshortcuts="ArrowLeft" onClick={() => {
                      const i = filteredRows.findIndex((r) => r.id === selected?.id);
                      if (i > 0) setSelectedId(filteredRows[i - 1].id);
                    }} />
                    <Button size="small" icon={<ArrowRightOutlined />} aria-label="下一张" aria-keyshortcuts="ArrowRight" onClick={() => {
                      const i = filteredRows.findIndex((r) => r.id === selected?.id);
                      if (i >= 0 && i < filteredRows.length - 1) setSelectedId(filteredRows[i + 1].id);
                    }} />
                    <Button size="small" icon={<ZoomInOutlined />} aria-label="放大" aria-keyshortcuts="+" onClick={() => setZoom((z) => Math.min(3, z + 0.25))} />
                    <Button size="small" icon={<ZoomOutOutlined />} aria-label="缩小" aria-keyshortcuts="-" onClick={() => setZoom((z) => Math.max(0.5, z - 0.25))} />
                    <Button size="small" icon={<RotateRightOutlined />} aria-label="旋转" aria-keyshortcuts="R" onClick={() => setRotation((r) => (r + 90) % 360)} />
                    <Button size="small" icon={<FullscreenOutlined />} aria-label="全屏" aria-keyshortcuts="F" onClick={() => void previewRef.current?.requestFullscreen?.()} />
                    <Typography.Text type="secondary">{selected ? `${zoom.toFixed(2)}× · ${rotation}°` : '未选择影像'}</Typography.Text>
                  </Space>
                  <div
                    ref={previewRef}
                    data-testid="evidence-preview"
                    style={{ height: 250, border: '1px dashed #d9d9d9', borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', background: '#fafafa' }}
                  >
                    {selected?.fileRef && /\.(png|jpe?g|webp)$/i.test(selected.fileRef) ? (
                      <img
                        alt={`影像 ${selected.id}`}
                        src={attachmentApi.downloadUrl(selected.fileRef)}
                        style={{ maxHeight: '100%', maxWidth: '100%', transform: `scale(${zoom}) rotate(${rotation}deg)` }}
                      />
                    ) : (
                      <Space direction="vertical" align="center">
                        <FileSearchOutlined style={{ fontSize: 32, color: '#bfbfbf' }} />
                        <Typography.Text type="secondary">
                          {selected ? '该材料暂无可直接内嵌预览的图片地址（PDF 请下载查看）' : '点击左侧分类或右侧发票定位影像'}
                        </Typography.Text>
                      </Space>
                    )}
                  </div>
                  {selected && (
                    <Space wrap style={{ marginTop: 8 }}>
                      <Tag color="blue">{docTypeLabel(selected.docType)}</Tag>
                      <Typography.Text type="secondary" ellipsis style={{ maxWidth: 260 }}>
                        {selected.fileRef ?? selected.id}
                      </Typography.Text>
                      <Button
                        size="small"
                        data-testid="evidence-manual-parse"
                        disabled={Boolean(selected.locked)}
                        onClick={() => {
                          setManualTarget(selected);
                          setManualConfidence(1);
                          setManualNote('');
                        }}
                      >
                        人工补录解析结果
                      </Button>
                    </Space>
                  )}
                </div>

                {/* 右：关联发票来自服务端 evidence 清单，不写死金额与号码 */}
                <div style={{ borderLeft: '1px solid #f0f0f0', paddingLeft: 8 }}>
                  <Space size={4}>
                    <LinkOutlined />
                    <Typography.Text strong>关联发票</Typography.Text>
                  </Space>
                  <div style={{ marginTop: 8, maxHeight: 250, overflowY: 'auto' }}>
                    {invoiceRows.length === 0 ? (
                      <Typography.Text type="secondary">该案件尚未登记发票类材料</Typography.Text>
                    ) : (
                      invoiceRows.map((r) => (
                        <button
                          key={r.id}
                          type="button"
                          data-testid={`evidence-invoice-${r.id}`}
                          onClick={() => {
                            setActiveDocType('INVOICE');
                            setSelectedId(r.id);
                          }}
                          style={{ display: 'block', width: '100%', textAlign: 'left', border: 0, borderBottom: '1px solid #f5f5f5', background: selected?.id === r.id ? '#e6f4ff' : 'transparent', padding: '8px 6px', cursor: 'pointer' }}
                        >
                          <Typography.Text ellipsis style={{ display: 'block', maxWidth: 210 }}>
                            {r.fileRef ?? r.id}
                          </Typography.Text>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {ocrStatus(r.ocrStatus).label} · {r.ocrConfidence == null ? '置信度未知' : `${(r.ocrConfidence * 100).toFixed(1)}%`}
                          </Typography.Text>
                        </button>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </Card>

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

            <AsyncBoundary
              loading={loading}
              error={loadError}
              empty={rows.length === 0}
              emptyText="该案件暂无已登记材料"
              onRetry={() => void load()}
            >
              <Table<EvidenceResp>
                rowKey="id"
                columns={columns}
                dataSource={rows}
                size="small"
                pagination={false}
                rowSelection={{
                  selectedRowKeys,
                  onChange: (keys) => setSelectedRowKeys(keys.map(String)),
                }}
              />
            </AsyncBoundary>
          </Space>
        )}
      </Card>
      <Modal
        title="锁定证据集 · 逐张确认"
        open={lockOpen}
        okText="确认锁定"
        cancelText="取消"
        confirmLoading={locking}
        onCancel={() => setLockOpen(false)}
        onOk={() => {
          void onLock().then((ok) => {
            if (ok) setLockOpen(false);
          });
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="warning">
            锁定后不可再上传或回填；理算必须以锁定后的证据集为输入，这是一个单向动作。
          </Typography.Text>
          <Typography.Text strong>本次将纳入以下 {rows.length} 张材料：</Typography.Text>
          <div
            data-testid="lock-evidence-list"
            style={{ maxHeight: 260, overflowY: 'auto', border: '1px solid #f0f0f0', borderRadius: 6 }}
          >
            {rows.map((r, i) => (
              <div
                key={r.id}
                data-testid={`lock-evidence-item-${r.id}`}
                style={{ padding: '6px 10px', borderBottom: '1px solid #f5f5f5' }}
              >
                <Typography.Text type="secondary" style={{ marginRight: 8 }}>{i + 1}.</Typography.Text>
                <Tag color="blue">{docTypeLabel(r.docType)}</Tag>
                <Typography.Text ellipsis style={{ maxWidth: 420 }}>
                  {r.fileRef ?? r.id}
                </Typography.Text>
              </div>
            ))}
          </div>
        </Space>
      </Modal>
      <Modal
        title="影像工作台快捷键"
        open={shortcutsOpen}
        footer={null}
        onCancel={() => setShortcutsOpen(false)}
      >
        <Space direction="vertical">
          <Typography.Text>← / →：上一张 / 下一张影像</Typography.Text>
          <Typography.Text>+ / -：放大 / 缩小（0.5×–3×）</Typography.Text>
          <Typography.Text>R：旋转 90° · F：全屏预览</Typography.Text>
          <Typography.Text type="secondary">焦点在输入框、下拉框或文本域时不触发快捷键，避免打断录入。</Typography.Text>
        </Space>
      </Modal>
      <Modal
        title={`人工补录解析结果 · ${manualTarget?.fileRef ?? ''}`}
        open={manualTarget !== null}
        okText="保存补录"
        onCancel={() => setManualTarget(null)}
        onOk={() => void onManualParsed()}
        destroyOnHidden
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="secondary">补录会标记为「人工补录」，保留原 OCR 结果不被覆盖为机器判定。</Typography.Text>
          <InputNumber
            min={0}
            max={1}
            step={0.05}
            value={manualConfidence}
            onChange={(v) => setManualConfidence(v ?? 1)}
            addonAfter="置信度"
            style={{ width: 220 }}
          />
          <Input
            value={manualNote}
            onChange={(e) => setManualNote(e.target.value)}
            placeholder="校验结论/补录说明（可选）"
          />
        </Space>
      </Modal>
    </div>
  );
}
