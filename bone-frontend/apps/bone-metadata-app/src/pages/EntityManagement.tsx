import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { metadataEntityApi } from '../services/metadataApi';
import type { CreateMetaEntityReq, MetaEntity, UpdateMetaEntityReq } from '../types';
import { DELIVERY_MODE, ENTITY_STATUS, META_DELIVERY_RUNTIME, META_ENTITY_PUBLISHED } from '../types';

const EntityManagement: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MetaEntity[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetaEntity | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await metadataEntityApi.page({ pageNum: page, pageSize, keyword: keyword || undefined });
      if (res.code === 200) {
        setData(res.data.list);
        setTotal(res.data.total);
      } else {
        message.error(res.message || '加载失败');
      }
    } catch {
      message.error('无法连接元数据服务，请确认 bone-metadata-server :9001 已启动');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ type: 0, deliveryMode: 0 });
    setModalOpen(true);
  };

  const openEdit = (record: MetaEntity) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        const body: UpdateMetaEntityReq = {
          name: values.name,
          displayName: values.displayName,
          description: values.description,
          tableName: values.tableName,
          sortOrder: values.sortOrder,
          deliveryMode: values.deliveryMode,
          icon: values.icon,
        };
        const res = await metadataEntityApi.update(editing.id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      } else {
        const body: CreateMetaEntityReq = values;
        const res = await metadataEntityApi.create(body);
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

  const columns: ColumnsType<MetaEntity> = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '编码', dataIndex: 'code', key: 'code' },
    { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
    { title: '表名', dataIndex: 'tableName', key: 'tableName' },
    {
      title: '交付模式',
      dataIndex: 'deliveryMode',
      key: 'deliveryMode',
      render: (m: number, row) => (
        <Tag color={m === 1 ? 'purple' : 'default'}>{row.deliveryModeLabel ?? DELIVERY_MODE[m] ?? m}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: number) => (
        <Tag color={s === 1 ? 'green' : s === 2 ? 'default' : 'blue'}>
          {ENTITY_STATUS[s] ?? s}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(record)} disabled={record.status === 1}>
            编辑
          </Button>
          {record.deliveryMode === META_DELIVERY_RUNTIME &&
            record.status === META_ENTITY_PUBLISHED && (
            <Link to={`/metadata/runtime?entity=${encodeURIComponent(record.code)}`}>运行时数据</Link>
          )}
          {record.status === 0 && (
            <Button
              type="link"
              size="small"
              onClick={async () => {
                const res = await metadataEntityApi.publish(record.id);
                if (res.code === 200) {
                  message.success('已发布');
                  load();
                } else {
                  message.error(res.message);
                }
              }}
            >
              发布
            </Button>
          )}
          <Popconfirm
            title="确认删除？"
            onConfirm={async () => {
              const res = await metadataEntityApi.delete(record.id);
              if (res.code === 200) {
                message.success('已删除');
                load();
              } else {
                message.error(res.message);
              }
            }}
          >
            <Button type="link" size="small" danger disabled={record.status === 1}>
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
        <Input.Search
          placeholder="搜索实体名称"
          allowClear
          onSearch={(v) => {
            setKeyword(v);
            setPage(1);
          }}
          style={{ width: 240 }}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建实体
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
          showSizeChanger: true,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
      />
      <Modal
        title={editing ? '编辑实体' : '新建实体'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          {!editing && (
            <Form.Item name="code" label="编码" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          )}
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="tableName" label="数据库表名" rules={[{ required: true }]}>
            <Input disabled={!!editing && editing.status === 1} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="deliveryMode" label="交付模式" tooltip="生成式走代码生成；运行时由 metadata-engine 提供动态 API（规划）">
            <Select
              disabled={!!editing && editing.status === 1}
              options={[
                { value: 0, label: DELIVERY_MODE[0] },
                { value: 1, label: DELIVERY_MODE[1] },
              ]}
            />
          </Form.Item>
          {!editing && (
            <Form.Item name="type" label="类型">
              <Select
                options={[
                  { value: 0, label: '普通业务实体' },
                  { value: 1, label: '主数据类实体' },
                ]}
              />
            </Form.Item>
          )}
          <Form.Item name="sortOrder" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default EntityManagement;
