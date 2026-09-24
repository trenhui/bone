import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Form, Input, Modal, message, Card, InputNumber, Select, Tag } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { SysDict } from '@/types';
import { dictApi } from '@/services/api';

const DictManagement: React.FC = () => {
  const [data, setData] = useState<SysDict[]>([]);
  const [types, setTypes] = useState<string[]>([]);
  const [selectedType, setSelectedType] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SysDict | null>(null);
  const [form] = Form.useForm();
  const [keyword, setKeyword] = useState('');

  const fetchData = async () => {
    setLoading(true);
    try {
      const page = await dictApi.getDictPage({ pageNum: 1, pageSize: 500, type: selectedType });
      setData(page.data.list);
      setTypes(Array.from(new Set(page.data.list.map((d) => d.type))));
    } catch {
      message.error('获取字典失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedType]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: SysDict) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await dictApi.updateDict(editing.id!, values);
        message.success('字典已更新');
      } else {
        await dictApi.createDict(values);
        message.success('字典已创建');
      }
      setModalOpen(false);
      fetchData();
    } catch {
      message.error('保存失败');
    }
  };

  const remove = (record: SysDict) => {
    Modal.confirm({
      title: `确认删除字典「${record.label}」？`,
      onOk: async () => {
        await dictApi.deleteDict(record.id!);
        message.success('字典已删除');
        fetchData();
      },
    });
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '类型', dataIndex: 'type', width: 120 },
    { title: '类型名称', dataIndex: 'typeName', width: 120 },
    { title: '编码', dataIndex: 'code', width: 120 },
    { title: '标签', dataIndex: 'label', width: 140 },
    { title: '值', dataIndex: 'value', width: 140 },
    { title: '排序', dataIndex: 'sort', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, record: SysDict) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" danger onClick={() => remove(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder="搜索类型 / 编码 / 标签"
          prefix={<SearchOutlined />}
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: 240 }}
        />
        <Select
          allowClear
          placeholder="按类型筛选"
          style={{ width: 200 }}
          value={selectedType}
          onChange={setSelectedType}
          options={types.map((t) => ({ value: t, label: t }))}
        />
        <Button icon={<ReloadOutlined />} onClick={fetchData}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建字典
        </Button>
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data.filter((d) => {
          const kw = keyword.trim().toLowerCase();
          if (!kw) return true;
          return [d.type, d.code, d.label].some((v) => (v ?? '').toLowerCase().includes(kw));
        })}
        loading={loading}
        scroll={{ x: 1100 }}
      />
      <Modal
        title={editing ? '编辑字典' : '新建字典'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="type" label="字典类型" rules={[{ required: true, message: '请输入字典类型' }]}>
            <Input placeholder="如：sys_user_status" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="typeName" label="类型名称">
            <Input placeholder="如：用户状态" />
          </Form.Item>
          <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
            <Input placeholder="如：ACTIVE" />
          </Form.Item>
          <Form.Item name="label" label="标签" rules={[{ required: true, message: '请输入标签' }]}>
            <Input placeholder="如：启用" />
          </Form.Item>
          <Form.Item name="value" label="值">
            <Input placeholder="如：1" />
          </Form.Item>
          <Form.Item name="sort" label="排序" initialValue={0}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <InputNumber style={{ width: '100%' }} min={0} max={1} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default DictManagement;
