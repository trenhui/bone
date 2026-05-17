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
import { metadataEntityApi, metadataRelationApi } from '../services/metadataApi';
import type { CreateMetaRelationReq, MetaEntity, MetaRelation, UpdateMetaRelationReq } from '../types';
import { RELATION_TYPES } from '../types';

const RelationManagement: React.FC = () => {
  const [form] = Form.useForm();
  const [entities, setEntities] = useState<MetaEntity[]>([]);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MetaRelation[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetaRelation | null>(null);

  useEffect(() => {
    metadataEntityApi.page({ pageNum: 1, pageSize: 200 }).then((res) => {
      if (res.code === 200) setEntities(res.data.list);
    });
  }, []);

  const entityLabel = (id: number) => {
    const e = entities.find((x) => x.id === id);
    return e ? `${e.displayName}` : String(id);
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await metadataRelationApi.page({ pageNum: page, pageSize });
      if (res.code === 200) {
        setData(res.data.list);
        setTotal(res.data.total);
      }
    } catch {
      message.error('加载关系失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: MetaRelation) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        const body: UpdateMetaRelationReq = {
          name: values.name,
          type: values.type,
          foreignKeyField: values.foreignKeyField,
          required: values.required,
          cascadeType: values.cascadeType,
        };
        const res = await metadataRelationApi.update(editing.id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          load();
        }
      } else {
        const body: CreateMetaRelationReq = values;
        const res = await metadataRelationApi.create(body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          load();
        }
      }
    } catch {
      message.error('保存失败');
    }
  };

  const columns: ColumnsType<MetaRelation> = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '源实体',
      dataIndex: 'sourceEntityId',
      render: (id: number) => entityLabel(id),
    },
    {
      title: '目标实体',
      dataIndex: 'targetEntityId',
      render: (id: number) => entityLabel(id),
    },
    { title: '类型', dataIndex: 'type', key: 'type' },
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
              const res = await metadataRelationApi.delete(record.id);
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
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建关系
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
        title={editing ? '编辑关系' : '新建关系'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="关系名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          {!editing && (
            <>
              <Form.Item name="sourceEntityId" label="源实体" rules={[{ required: true }]}>
                <Select
                  options={entities.map((e) => ({ value: e.id, label: e.displayName }))}
                />
              </Form.Item>
              <Form.Item name="targetEntityId" label="目标实体" rules={[{ required: true }]}>
                <Select
                  options={entities.map((e) => ({ value: e.id, label: e.displayName }))}
                />
              </Form.Item>
            </>
          )}
          <Form.Item name="type" label="关系类型" rules={[{ required: true }]}>
            <Select options={RELATION_TYPES.map((t) => ({ value: t, label: t }))} />
          </Form.Item>
          <Form.Item name="foreignKeyField" label="外键字段">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default RelationManagement;
