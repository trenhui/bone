import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  Card,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
  type TableColumnsType,
} from 'antd';
import { UploadOutlined, LockOutlined, ReloadOutlined, DownloadOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import {
  attachmentApi,
  evidenceApi,
  type EvidenceResp,
} from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';

/**
 * 材料上传页（synapClaim A2，登记 + 展示）。
 *
 * 关键约束（与后端 EvidenceController §7.2 一致）：影像本体**不走 synap-claim**，
 * 前端直传平台附件服务 bone-file（`attachmentApi.upload`）拿 `objectName` 即 `fileRef`，
 * 本地算 SHA-256 得 `contentHash`，再 `evidenceApi.register`（只登记 fileRef+哈希）。
 * 列表以服务端为准（server-authoritative），避免前端自造第二数据。
 */

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
const ocrStatus = (v?: string) => (v ? (OCR_STATUS[v] ?? { color: 'default', label: v }) : { color: 'default', label: '待解析' });

async function sha256Hex(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
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

export function EvidenceUploadPage(): ReactElement {
  const [searchParams, setSearchParams] = useSearchParams();
  const claimId = searchParams.get('claimId') ?? '1';
  const [input, setInput] = useState(claimId);
  const [docType, setDocType] = useState('INVOICE');
  const [rows, setRows] = useState<EvidenceResp[]>([]);
  const [loading, setLoading] = useState(false);
  const [locking, setLocking] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await evidenceApi.list(claimId);
      setRows(resp?.data ?? []);
    } catch {
      message.error('材料列表加载失败');
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleUpload = async (file: File) => {
    try {
      const contentHash = await sha256Hex(file);
      const uploaded = await attachmentApi.upload(file);
      const fileRef = uploaded?.data?.objectName;
      if (!fileRef) {
        message.error('附件服务未返回 fileRef');
        return;
      }
      await evidenceApi.register(claimId, { fileRef, contentHash, docType });
      message.success(`已登记：${file.name}`);
      void load();
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      if (errorCode === 'SC_CLAIM_EVIDENCE_LIMIT_EXCEEDED') {
        message.error('单案件材料已达上限（≤20 份）');
      } else if (errorCode === 'SC_CLAIM_EVIDENCE_DUPLICATED') {
        message.error('相同内容哈希的材料已登记（去重）');
      } else if (errorCode === 'SC_CLAIM_FILE_UPLOAD_FAILED') {
        message.error('附件上传失败');
      } else {
        message.error(`登记失败：${file.name}`);
      }
    }
  };

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    void handleUpload(file as File);
    return false; // 阻止 antd 默认上传，由我们控制 fileRef + 内容哈希 + 登记
  };

  const onLock = async () => {
    setLocking(true);
    try {
      await evidenceApi.lock(claimId);
      message.success('证据集已锁定（理算前置）');
      void load();
    } catch {
      message.error('锁定失败');
    } finally {
      setLocking(false);
    }
  };

  const onSearch = () => {
    const id = input.trim();
    if (!id) return;
    setSearchParams({ claimId: id });
  };

  const allLocked = rows.length > 0 && rows.every((r) => r.locked);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="材料上传" />
      <Card
        title="材料清单"
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
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap>
            <Select
              value={docType}
              onChange={setDocType}
              options={DOC_TYPES}
              style={{ width: 160 }}
              placeholder="材料类型"
            />
            <Upload beforeUpload={beforeUpload} showUploadList={false} multiple>
              <Button type="primary" icon={<UploadOutlined />}>
                选择并上传材料
              </Button>
            </Upload>
            <Button
              icon={<LockOutlined />}
              loading={locking}
              disabled={allLocked || rows.length === 0}
              onClick={() => void onLock()}
            >
              锁定证据集{allLocked ? '（已锁定）' : ''}
            </Button>
          </Space>
          <Table<EvidenceResp>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            size="small"
            pagination={false}
          />
        </Space>
      </Card>
    </div>
  );
}
