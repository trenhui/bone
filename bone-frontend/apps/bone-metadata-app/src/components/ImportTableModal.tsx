/**
 * UC-IMP 逆向建模：从存量物理表采集结构 → 预览（字段数 / 跳过的保留列）→ 导入为 DRAFT 实体。
 *
 * 真实场景：企业已有业务表（如自研交易库的 t_order）要先被元数据平台纳管，才能在其上做
 * 「元数据驱动的字段扩展」。没有这一步，存量表只能人工逐字段重录，且极易与物理表漂移。
 *
 * 与「导入模型」（上传 .model.json）的区别：本组件读的是数据库里的真实物理表结构，
 * 不是平台导出的模型文件。
 */
import { useState } from 'react';
import {
  Alert, Button, Form, Input, Modal, Select, Space, Switch, Tag, Typography, message,
} from 'antd';
import { DatabaseOutlined } from '@ant-design/icons';
import { errorMessage, metadataEntityApi } from '../services/metadataApi';
import type { ImportMetaEntityResult } from '../types';

const { Text } = Typography;

/** 标识符白名单（与后端 requireIdentifier 一致） */
const TABLE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;

interface ImportTableModalProps {
  open: boolean;
  onClose: () => void;
  onImported: (entityId: string | string) => void;
}

const ImportTableModal: React.FC<ImportTableModalProps> = ({ open, onClose, onImported }) => {
  const [form] = Form.useForm();
  const [previewing, setPreviewing] = useState(false);
  const [importing, setImporting] = useState(false);
  const [preview, setPreview] = useState<ImportMetaEntityResult | null>(null);
  const [result, setResult] = useState<ImportMetaEntityResult | null>(null);

  const reset = () => {
    form.resetFields();
    setPreview(null);
    setResult(null);
  };

  const values = () => form.getFieldsValue(true) as {
    tableName?: string; code?: string; displayName?: string;
    description?: string; deliveryMode?: number; includeReserved?: boolean;
  };

  /** 试运行采集：只回统计，不落库（后端 dryRun=true） */
  const handlePreview = async () => {
    try {
      await form.validateFields();
    } catch {
      return;
    }
    setPreviewing(true);
    setResult(null);
    try {
      const v = values();
      const res = await metadataEntityApi.importFromTable({
        tableName: v.tableName!,
        code: v.code,
        displayName: v.displayName,
        description: v.description,
        deliveryMode: v.deliveryMode ?? 1,
        includeReserved: v.includeReserved ?? false,
        dryRun: true,
      });
      if (res.code === 200 || res.code === 201) {
        setPreview(res.data);
      } else {
        message.error(errorMessage(res));
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '采集失败');
    } finally {
      setPreviewing(false);
    }
  };

  const handleImport = async () => {
    setImporting(true);
    try {
      const v = values();
      const res = await metadataEntityApi.importFromTable({
        tableName: v.tableName!,
        code: v.code,
        displayName: v.displayName,
        description: v.description,
        deliveryMode: v.deliveryMode ?? 1,
        includeReserved: v.includeReserved ?? false,
        dryRun: false,
      });
      if (res.code === 200 || res.code === 201) {
        setResult(res.data);
        message.success(`已纳管：实体 ${res.data.entityCode}（${res.data.importedFields} 个字段，草稿态）`);
      } else {
        message.error(errorMessage(res));
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '导入失败');
    } finally {
      setImporting(false);
    }
  };

  const columnsText = (r: ImportMetaEntityResult) =>
    r.skippedColumns.length ? r.skippedColumns.join('、') : '无';

  return (
    <Modal
      title={<Space><DatabaseOutlined />从存量表导入（逆向建模）</Space>}
      open={open}
      onCancel={() => { onClose(); reset(); }}
      onOk={result ? () => { onImported(result.entityId!); reset(); } : handleImport}
      okText={result ? '完成' : importing ? '导入中…' : '确认导入'}
      okButtonProps={{ disabled: !preview || importing || !!result, loading: importing }}
      destroyOnHidden
      width={620}
    >
      <Form form={form} layout="vertical" initialValues={{ deliveryMode: 1, includeReserved: false }}>
        <Form.Item
          name="tableName"
          label="物理表名"
          rules={[
            { required: true, message: '请填写物理表名' },
            { pattern: TABLE_PATTERN, message: '以字母开头，仅字母/数字/下划线' },
          ]}
        >
          <Input placeholder="如：t_order" disabled={importing || !!result} onChange={() => setPreview(null)} />
        </Form.Item>

        <Space size="middle" style={{ display: 'flex' }}>
          <Form.Item name="code" label="实体编码（缺省取表名）" style={{ flex: 1 }}>
            <Input placeholder="如：t_order" disabled={importing || !!result} />
          </Form.Item>
          <Form.Item name="displayName" label="显示名（缺省取表名）" style={{ flex: 1 }}>
            <Input placeholder="如：订单" disabled={importing || !!result} />
          </Form.Item>
        </Space>

        <Space size="middle" style={{ display: 'flex' }}>
          <Form.Item name="deliveryMode" label="交付模式" style={{ flex: 1 }}>
            <Select
              disabled={importing || !!result}
              options={[
                { value: 1, label: 'RUNTIME（运行时元数据面）' },
                { value: 0, label: 'GENERATIVE（生成式交付）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="includeReserved" label="纳入平台保留列" valuePropName="checked" style={{ flex: 1 }}>
            <Switch disabled={importing || !!result} />
          </Form.Item>
        </Space>

        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} disabled={importing || !!result} />
        </Form.Item>
      </Form>

      <Button
        style={{ marginBottom: 12 }}
        onClick={handlePreview}
        loading={previewing}
        disabled={importing || !!result}
      >
        ① 采集预览（只读，不落库）
      </Button>

      {preview && (
        <Alert
          type="info"
          showIcon
          message={
            <Space size="small" wrap>
              <Text>表 <Text code>{preview.tableName}</Text></Text>
              <Tag color="blue">将建模 {preview.importedFields} 个字段</Tag>
            </Space>
          }
          description={
            <>
              <div>跳过的平台保留列：{columnsText(preview)}</div>
              <Text type="secondary">
                保留列（id / tenant_id / version / deleted / 审计列）由平台托管，导入时不建模为业务字段。
                导入产物为「草稿」实体，需人工复核后再发布。
              </Text>
            </>
          }
        />
      )}

      {result && (
        <Alert
          style={{ marginTop: 12 }}
          type="success"
          showIcon
          message={`导入完成：${result.entityCode}（${result.importedFields} 个字段）`}
          description={`实体 ID ${result.entityId}；跳过保留列：${columnsText(result)}。请到实体详情复核字段后发布。`}
        />
      )}
    </Modal>
  );
};

export default ImportTableModal;
