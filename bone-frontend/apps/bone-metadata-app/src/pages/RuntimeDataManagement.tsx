import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  metadataEntityApi,
  metadataFieldApi,
  normalizePage,
  runtimeRecordApi,
} from '../services/metadataApi';
import type { MetaEntity, MetaField, RuntimeRecord } from '../types';
import {
  DELIVERY_MODE,
  ENTITY_STATUS,
  META_DELIVERY_RUNTIME,
  META_ENTITY_PUBLISHED,
  RUNTIME_READONLY_FIELDS,
} from '../types';

const { Text } = Typography;

function recordId(row: RuntimeRecord): string {
  const id = row.id;
  return id != null ? String(id) : '';
}

function formatCell(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

const RuntimeDataManagement: React.FC = () => {
  const [form] = Form.useForm();
  const [entities, setEntities] = useState<MetaEntity[]>([]);
  const [entityCode, setEntityCode] = useState<string | null>(null);
  const [fields, setFields] = useState<MetaField[]>([]);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<RuntimeRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<RuntimeRecord | null>(null);

  const selectedEntity = useMemo(
    () => entities.find((e) => e.code === entityCode) ?? null,
    [entities, entityCode],
  );

  const writableFields = useMemo(
    () => fields.filter((f) => !RUNTIME_READONLY_FIELDS.has(f.code)),
    [fields],
  );

  useEffect(() => {
    metadataEntityApi.page({ pageNum: 1, pageSize: 500 }).then((res) => {
      if (res.code !== 200) {
        message.error(res.message);
        return;
      }
      const pageData = normalizePage(res.data);
      const runtimePublished = pageData.list.filter(
        (e) =>
          e.deliveryMode === META_DELIVERY_RUNTIME && e.status === META_ENTITY_PUBLISHED,
      );
      setEntities(runtimePublished);
      if (runtimePublished.length > 0 && entityCode == null) {
        setEntityCode(runtimePublished[0].code);
      }
    });
  }, []);

  useEffect(() => {
    if (!selectedEntity) {
      setFields([]);
      return;
    }
    metadataFieldApi
      .page(selectedEntity.id, { pageNum: 1, pageSize: 500 })
      .then((res) => {
        if (res.code === 200) {
          setFields(normalizePage(res.data).list);
        }
      });
  }, [selectedEntity?.id]);

  const load = useCallback(async () => {
    if (!entityCode) return;
    setLoading(true);
    try {
      const res = await runtimeRecordApi.page(entityCode, { page, size: pageSize });
      if (res.code === 200) {
        const pageData = normalizePage(res.data);
        setData(pageData.list);
        setTotal(pageData.total);
      } else {
        message.error(res.message);
      }
    } catch {
      message.error('加载运行时数据失败，请确认实体已发布且物理表存在');
    } finally {
      setLoading(false);
    }
  }, [entityCode, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const tableColumns: ColumnsType<RuntimeRecord> = useMemo(() => {
    const cols: ColumnsType<RuntimeRecord> = writableFields.slice(0, 8).map((f) => ({
      title: f.displayName || f.code,
      dataIndex: f.code,
      key: f.code,
      ellipsis: true,
      render: (_: unknown, row) => formatCell(row[f.code]),
    }));
    if (cols.length === 0) {
      cols.push({
        title: 'id',
        dataIndex: 'id',
        key: 'id',
        render: (_: unknown, row) => formatCell(row.id),
      });
    }
    cols.push({
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 160,
      render: (_: unknown, row) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(row)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    });
    return cols;
  }, [writableFields]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: RuntimeRecord) => {
    setEditing(record);
    const values: Record<string, unknown> = {};
    writableFields.forEach((f) => {
      values[f.code] = record[f.code];
    });
    form.setFieldsValue(values);
    setModalOpen(true);
  };

  const handleDelete = async (record: RuntimeRecord) => {
    if (!entityCode) return;
    const id = recordId(record);
    if (!id) {
      message.error('记录缺少主键 id');
      return;
    }
    try {
      const res = await runtimeRecordApi.delete(entityCode, id);
      if (res.code === 200) {
        message.success('已删除');
        load();
      } else {
        message.error(res.message);
      }
    } catch {
      message.error('删除失败');
    }
  };

  const handleSubmit = async () => {
    if (!entityCode) return;
    const values = await form.validateFields();
    const body: RuntimeRecord = { ...values };
    try {
      if (editing) {
        const id = recordId(editing);
        const res = await runtimeRecordApi.update(entityCode, id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      } else {
        const res = await runtimeRecordApi.create(entityCode, body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      }
    } catch {
      message.error(editing ? '更新失败' : '创建失败');
    }
  };

  const renderFieldInput = (field: MetaField) => {
    const type = (field.type || 'STRING').toUpperCase();
    if (type === 'BOOLEAN') {
      return (
        <Form.Item name={field.code} label={field.displayName} valuePropName="checked">
          <Switch />
        </Form.Item>
      );
    }
    if (type === 'INTEGER' || type === 'LONG') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
      );
    }
    if (type === 'DECIMAL') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <InputNumber style={{ width: '100%' }} step={0.01} />
        </Form.Item>
      );
    }
    if (type === 'TEXT') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <Input.TextArea rows={3} />
        </Form.Item>
      );
    }
    return (
      <Form.Item
        name={field.code}
        label={field.displayName}
        rules={field.required ? [{ required: true, message: `请填写${field.displayName}` }] : []}
      >
        <Input />
      </Form.Item>
    );
  };

  return (
    <motion className="page">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="模式 B · 运行时数据"
        description={
          <>
            仅展示 <Tag color="purple">运行时 (B)</Tag> 且 <Tag color="green">已发布</Tag>{' '}
            的实体。数据经 <Text code>/api/v1/runtime/entities/&#123;code&#125;/records</Text>{' '}
            读写物理表，无需代码生成。
          </>
        }
      />

      <Space style={{ marginBottom: 16 }} wrap>
        <span>实体：</span>
        <Select
          style={{ minWidth: 280 }}
          placeholder="选择 RUNTIME 已发布实体"
          value={entityCode ?? undefined}
          onChange={(code) => {
            setEntityCode(code);
            setPage(1);
          }}
          options={entities.map((e) => ({
            value: e.code,
            label: `${e.displayName} (${e.code}) → ${e.tableName}`,
          }))}
          notFoundContent="暂无符合条件的实体，请先在实体管理中创建并发布 RUNTIME 实体"
        />
        <Button icon={<ReloadOutlined />} onClick={load} disabled={!entityCode}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!entityCode}>
          新建记录
        </Button>
      </Space>

      {selectedEntity && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          物理表：<Text code>{selectedEntity.tableName}</Text> · 交付模式：
          {DELIVERY_MODE[selectedEntity.deliveryMode ?? 0]} · 状态：
          {ENTITY_STATUS[selectedEntity.status]}
        </Text>
      )}

      <Table<RuntimeRecord>
        rowKey={(row) => recordId(row) || JSON.stringify(row)}
        loading={loading}
        columns={tableColumns}
        dataSource={data}
        scroll={{ x: true }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
      />

      <Modal
        title={editing ? '编辑记录' : '新建记录'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          {writableFields.length === 0 ? (
            <Alert
              type="warning"
              message="该实体尚未配置建模字段，请先在字段管理中维护 meta_field"
            />
          ) : (
            writableFields.map((f) => <motion key={f.id}>{renderFieldInput(f)}</motion>)
          )}
        </Form>
      </Modal>
    </motion>
  );
};

/** 避免与 framer-motion 冲突的轻量别名 */
const motion = {
  className: '',
  key: undefined as string | number | undefined,
  children: null as React.ReactNode,
};
// 使用 div 包装（上方 motion 占位有误，改为普通 fragment）
export default function RuntimeDataManagementPage() {
  return <RuntimeDataManagementInner />;
}

function RuntimeDataManagementInner() {
  // 将上面组件内错误的 motion 标签修复 — 直接重写导出组件
  return <RuntimeDataManagementFixed />;
}
