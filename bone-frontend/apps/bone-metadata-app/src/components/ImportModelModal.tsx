/**
 * F13 模型导入：上传导出的 .model.json → 结构校验预览 → 选归属模块 → 创建实体+字段。
 * 关系不自动导入（跨实体依赖，需人工重建），导入完成后提示。
 */
import { useRef, useState } from 'react';
import { Alert, Badge, Modal, Select, Space, Table, Tag, Typography, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  importEntityModel, parseModelFile, validateModelFile,
  type ImportResult, type ModelTransferFile,
} from '../utils/modelTransfer';

const { Text } = Typography;

interface ImportModelModalProps {
  open: boolean;
  /** scoped 模式下由路由注入（禁改）；非 scoped 由用户在下拉选择。⚠ 雪花 ID 字符串透传 */
  moduleId?: number | string;
  moduleOptions: Array<{ value: string; label: string }>;
  onClose: () => void;
  onImported: (entityId: number) => void;
}

interface FieldRow {
  code: string;
  displayName: string;
  type: string;
  length?: number;
  required?: boolean;
}

const fieldColumns: ColumnsType<FieldRow> = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 140 },
  { title: '显示名', dataIndex: 'displayName', key: 'displayName', width: 140, ellipsis: true },
  { title: '类型', dataIndex: 'type', key: 'type', width: 90, render: (t: string) => <Tag>{t}</Tag> },
  { title: '长度', dataIndex: 'length', key: 'length', width: 60, render: (v?: number) => v ?? '—' },
  {
    title: '必填', dataIndex: 'required', key: 'required', width: 60,
    render: (v?: boolean) => (v ? <Tag color="red">是</Tag> : <Tag>否</Tag>),
  },
];

const ImportModelModal: React.FC<ImportModelModalProps> = ({
  open, moduleId, moduleOptions, onClose, onImported,
}) => {
  const [parsed, setParsed] = useState<ModelTransferFile | null>(null);
  const [fileName, setFileName] = useState<string>('');
  const [issues, setIssues] = useState<string[]>([]);
  const [targetModuleId, setTargetModuleId] = useState<string | undefined>(undefined);
  const [importing, setImporting] = useState(false);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const fileStamp = useRef<string>('');

  const reset = () => {
    setParsed(null);
    setFileName('');
    setIssues([]);
    setProgress(null);
    setResult(null);
  };

  const handleFile = async (file: File) => {
    // 同一文件重复选择时 antd 不会触发 onChange，用时间戳兜底
    if (fileStamp.current === `${file.name}:${file.size}:${file.lastModified}`) return false;
    fileStamp.current = `${file.name}:${file.size}:${file.lastModified}`;
    setResult(null);
    try {
      const next = await parseModelFile(file);
      setParsed(next);
      setFileName(file.name);
      setIssues(validateModelFile(next));
    } catch (err) {
      reset();
      setFileName(file.name);
      setIssues([err instanceof Error ? err.message : '文件解析失败']);
    }
    return false; // 阻止 antd 自动上传
  };

  const canImport = !!parsed && issues.length === 0 && !importing && !result && (!!moduleId || !!targetModuleId);

  const handleImport = async () => {
    if (!parsed) return;
    // ⚠ 雪花 ID 禁止 Number()：19 位超出 2^53 静默截断（"758855807931514880"→...4900 后端报模块不存在）。
    // 后端 Jackson 可从 JSON 字符串反序列化 Long，故一律字符串透传。
    const resolvedModuleId = moduleId ?? targetModuleId;
    setImporting(true);
    setProgress({ done: 0, total: parsed.fields?.length ?? 0 });
    try {
      const r = await importEntityModel(parsed, {
        moduleId: resolvedModuleId,
        onProgress: (done, total) => setProgress({ done, total }),
      });
      setResult(r);
      if (r.failedFields.length === 0) message.success(`导入成功：实体已创建，${r.createdFields} 个字段就绪`);
      else message.warning(`实体已创建；${r.createdFields} 个字段成功，${r.failedFields.length} 个失败`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '导入失败');
      setProgress(null);
    } finally {
      setImporting(false);
    }
  };

  const e = parsed?.entity;
  const okText = result ? '完成' : importing ? '导入中…' : '开始导入';

  return (
    <Modal
      title="导入模型"
      open={open}
      onCancel={() => { onClose(); reset(); }}
      onOk={result ? () => { onImported(result.entityId); reset(); } : handleImport}
      okText={okText}
      okButtonProps={{ disabled: !canImport && !result, loading: importing }}
      destroyOnHidden
      width={640}
    >
      <Upload.Dragger
        accept=".json,application/json"
        maxCount={1}
        showUploadList={false}
        disabled={importing}
        beforeUpload={(file) => handleFile(file as File)}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">点击或拖拽模型 JSON 文件到此处</p>
        <p className="ant-upload-hint">仅支持本平台「导出」生成的 *.model.json 文件</p>
      </Upload.Dragger>

      {issues.length > 0 && (
        <Alert
          type="error"
          showIcon
          style={{ marginTop: 12 }}
          message={fileName ? `「${fileName}」校验未通过` : '文件校验未通过'}
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {issues.map((it, i) => <li key={i}>{it}</li>)}
            </ul>
          }
        />
      )}

      {parsed && issues.length === 0 && e && (
        <div style={{ marginTop: 12 }}>
          <Alert
            type="success"
            showIcon
            style={{ marginBottom: 12 }}
            message={`「${fileName}」校验通过`}
            description={
              <Space size="small" wrap style={{ display: 'flex' }}>
                <Text code>{e.code}</Text>
                <Text>{e.displayName}</Text>
                <Text type="secondary">表：{e.tableName}</Text>
                <Badge count={parsed.fields?.length ?? 0} style={{ backgroundColor: '#1890ff' }} showZero />
                <Text type="secondary">个字段</Text>
                {(parsed.relations?.length ?? 0) > 0 && (
                  <Text type="warning">{parsed.relations!.length} 个关系需导入后人工重建</Text>
                )}
              </Space>
            }
          />
          <Table
            rowKey="code"
            size="small"
            columns={fieldColumns}
            dataSource={(parsed.fields ?? []) as FieldRow[]}
            pagination={false}
            scroll={{ y: 200 }}
          />
        </div>
      )}

      {importing && progress && (
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          message={`正在导入字段 ${progress.done}/${progress.total} …`}
        />
      )}

      {result && (
        <Alert
          style={{ marginTop: 12 }}
          type={result.failedFields.length === 0 ? 'success' : 'warning'}
          showIcon
          message={`实体已创建（编码 ${result.entityCode}）`}
          description={
            result.failedFields.length === 0
              ? `${result.createdFields} 个字段全部导入成功。`
              : (
                <>
                  <div style={{ marginBottom: 4 }}>{result.createdFields} 个字段成功，以下 {result.failedFields.length} 个失败：</div>
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {result.failedFields.map((f) => <li key={f.code}><Text code>{f.code}</Text>：{f.reason}</li>)}
                  </ul>
                </>
              )
          }
        />
      )}

      {!moduleId && (
        <div style={{ marginTop: 12 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>归属模块</Text>
          <Select
            style={{ width: '100%', marginTop: 4 }}
            showSearch
            optionFilterProp="label"
            placeholder="选择导入后实体归属的应用模块"
            value={targetModuleId}
            onChange={setTargetModuleId}
            disabled={importing || !!result}
            options={moduleOptions}
          />
        </div>
      )}
    </Modal>
  );
};

export default ImportModelModal;
