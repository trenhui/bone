import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { metadataEntityApi, metadataFieldApi } from '../services/metadataApi';
import type { CreateMetaFieldReq, MetaEntity, MetaField, UpdateMetaFieldReq } from '../types';
import { FIELD_TYPES } from '../types';

const FieldManagement: React.FC = () => {
  const [form] = Form.useForm();
  const [entities, setEntities] = useState<MetaEntity[]>([]);
  const [entityId, setEntityId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MetaField[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetaField | null>(null);

  useEffect(() => {
    metadataEntityApi.page({ pageNum: 1, pageSize: 200 }).then((res) => {
      if (res.code === 200) {
        setEntities(res.data.list);
        if (res.data.list.length > 0 && entityId == null) {
          setEntityId(res.data.list[0].id);
        }
      }
    });
  }, []);

  const load = useCallback(async () => {
    if (entityId == null) return;
    setLoading(true);
    try {
      const res = await metadataFieldApi.page(entityId, { pageNum: page, pageSize });
      if (res.code === 200) {
        setData(res.data.list);
        setTotal(res.data.total);
      } else {
        message.error(res.message);
      }
    } catch {
      message.error('加载字段失败');
    } finally {
      setLoading(false);
    }
  }, [entityId, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: MetaField) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    if (entityId == null) return;
    const values = await form.validateFields();
    try {
      if (editing) {
        const body: UpdateMetaFieldReq = values;
        const res = await metadataFieldApi.update(entityId, editing.id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      } else {
        const body: CreateMetaFieldReq = values;
        const res = await metadataFieldApi.create(entityId, body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      }
    } catch {
      message.error('保存失败');
    }
  };

  const columns: ColumnsType<MetaField> = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '编码', dataIndex: 'code', key: 'code' },
    { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
    { title: '类型', dataIndex: 'type', key: 'type' },
    {
      title: '必填',
      dataIndex: 'required',
      key: 'required',
      render: (v: boolean) => (v ? '是' : '否'),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除？"
            onConfirm={async () => {
              const res = await metadataFieldApi.delete(entityId!, record.id);
              if (res.code === 200) {
                message.success('已删除');
                load();
              }
            }}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="page">
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          style={{ width: 280 }}
          placeholder="选择实体"
          value={entityId ?? undefined}
          onChange={(id) => {
            setEntityId(id);
            setPage(1);
          }}
          options={entities.map((e) => ({
            value: e.id,
            label: `${e.displayName} (${e.code})`,
          }))}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!entityId}>
          新建字段
        </Button>
      </Space>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: page,
          pageSize,
          total,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
      />
      <Modal
        title={editing ? '编辑字段' : '新建字段'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          {!editing && (
            <>
              <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item name="code" label="编码" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </>
          )}
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={FIELD_TYPES.map((t) => ({ value: t, label: t }))} />
          </Form.Item>
          {editing && (
            <>
              <Form.Item name="length" label="长度">
                <Input type="number" />
              </Form.Item>
              <Form.Item name="required" label="必填">
                <Select
                  options={[
                    { value: true, label: '是' },
                    { value: false, label: '否' },
                  ]}
                />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default FieldManagement;
