import { useEffect, useState } from 'react';
import {
  Alert, Button, Collapse, Modal, Space, Spin, Table, Tag, Typography, message,
} from 'antd';
import { ExclamationCircleOutlined, CheckCircleOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { errorMessage, metadataEntityApi } from '../services/metadataApi';
import type { EntityValidationIssue, MetaField, PublishPreview } from '../types';

const { Text, Paragraph } = Typography;

const LEVEL_TAG: Record<string, { color: string; label: string }> = {
  ERROR: { color: 'red', label: '错误' },
  WARNING: { color: 'orange', label: '警告' },
  INFO: { color: 'blue', label: '提示' },
};

/**
 * 发布摘要预览 Modal（UC-W7 摘要级，先行于 ADR-0039 R1 发布包）。
 *
 * 内容：静态校验问题（ERROR 阻断发布按钮）+ 字段清单 + RUNTIME 物理 inspect 计划（将要执行的建表/加列语句）。
 * 确认后调用既有单实体发布接口；预览 API 不可用时降级为直接发布（后端发布期校验兜底）。
 */
interface Props {
  open: boolean;
  entityId: number | null;
  entityName?: string;
  onClose: () => void;
  onPublished: () => void;
}

const PublishPreviewModal: React.FC<Props> = ({ open, entityId, entityName, onClose, onPublished }) => {
  const [preview, setPreview] = useState<PublishPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [previewFailed, setPreviewFailed] = useState(false);

  useEffect(() => {
    if (!open || !entityId) return;
    setLoading(true);
    setPreviewFailed(false);
    metadataEntityApi
      .publishPreview(entityId)
      .then((res) => {
        if (res.code === 200) setPreview(res.data);
        else setPreviewFailed(true);
      })
      .catch(() => setPreviewFailed(true))
      .finally(() => setLoading(false));
  }, [open, entityId]);

  const handlePublish = async () => {
    if (!entityId) return;
    setPublishing(true);
    try {
      const res = await metadataEntityApi.publish(entityId);
      if (res.code === 200) {
        message.success(`「${entityName || preview?.entityCode || entityId}」已发布`);
        onClose();
        onPublished();
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('发布失败，请确认服务可用');
    } finally {
      setPublishing(false);
    }
  };

  const issueColumns: ColumnsType<EntityValidationIssue> = [
    {
      title: '级别', dataIndex: 'level', width: 70,
      render: (lv: string) => <Tag color={LEVEL_TAG[lv]?.color ?? 'default'}>{LEVEL_TAG[lv]?.label ?? lv}</Tag>,
    },
    { title: '问题码', dataIndex: 'code', width: 190, ellipsis: true },
    { title: '说明', dataIndex: 'message', ellipsis: true },
  ];

  const fieldColumns: ColumnsType<MetaField> = [
    { title: '字段', dataIndex: 'displayName', width: 140, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 140, ellipsis: true },
    { title: '类型', dataIndex: 'type', width: 90 },
    { title: '长度', dataIndex: 'length', width: 70, render: (v: number | null) => v ?? '—' },
    {
      title: '必填', dataIndex: 'required', width: 60,
      render: (v: boolean) => (v ? <Tag color="red">是</Tag> : <Tag>否</Tag>),
    },
  ];

  return (
    <Modal
      title={`发布预览${entityName ? `：${entityName}` : ''}`}
      open={open}
      onCancel={onClose}
      width={720}
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            danger
            loading={publishing}
            disabled={loading || (!!preview && !preview.runnable)}
            onClick={handlePublish}
          >
            确认发布
          </Button>
        </Space>
      }
    >
      {loading && (
        <div style={{ textAlign: 'center', padding: 32 }}><Spin tip="正在生成发布预览..." /></div>
      )}

      {!loading && previewFailed && (
        <Alert
          type="warning"
          showIcon
          message="预览服务暂不可用"
          description="无法获取变更摘要，仍可发布（后端发布期校验将兜底拦截漂移等问题）。"
        />
      )}

      {!loading && preview && (
        <>
          {preview.hasBlockingErrors ? (
            <Alert
              type="error"
              showIcon
              icon={<ExclamationCircleOutlined />}
              message="存在阻断性问题，发布按钮已禁用"
              description="请先解决下方「错误」级别的问题再发布。"
              style={{ marginBottom: 12 }}
            />
          ) : (
            <Alert
              type="success"
              showIcon
              icon={<CheckCircleOutlined />}
              message="校验通过，可发布"
              description={`字段 ${preview.fields.length} 个 · 关系 ${preview.relationCount} 条`}
              style={{ marginBottom: 12 }}
            />
          )}

          <Collapse
            size="small"
            defaultActiveKey={preview.hasBlockingErrors ? ['issues'] : []}
            items={[
              {
                key: 'issues',
                label: `校验问题（${preview.validationIssues.length}）`,
                children: (
                  <Table
                    rowKey={(r, i) => `${r.code}-${i}`}
                    columns={issueColumns}
                    dataSource={preview.validationIssues}
                    pagination={false}
                    size="small"
                  />
                ),
              },
              {
                key: 'fields',
                label: `字段清单（${preview.fields.length}）`,
                children: (
                  <Table
                    rowKey="id"
                    columns={fieldColumns}
                    dataSource={preview.fields}
                    pagination={false}
                    size="small"
                    scroll={{ y: 220 }}
                  />
                ),
              },
              ...(preview.physical
                ? [{
                    key: 'physical',
                    label: `物理结构计划（${preview.physical.createTable ? '整表新建' : '增量对齐'}，${preview.physical.statements.length} 条 DDL）`,
                    children: (
                      <>
                        <Paragraph type="secondary" style={{ marginBottom: 8 }}>
                          <Text code>{preview.physical.status}</Text> {preview.physical.message}
                        </Paragraph>
                        <pre
                          style={{
                            maxHeight: 180, overflow: 'auto', fontSize: 12,
                            background: '#f6f6f6', padding: 8, borderRadius: 4,
                          }}
                        >
                          {preview.physical.statements.join('\n') || '（结构与模型一致，无需执行 DDL）'}
                        </pre>
                      </>
                    ),
                  }]
                : []),
            ]}
          />
        </>
      )}
    </Modal>
  );
};

export default PublishPreviewModal;
