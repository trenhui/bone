import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  createExtPoint,
  deleteExtPoint,
  listExtPoints,
  postExtPointEnable,
  updateExtPoint,
  type ExtPointPayload,
  type ExtPointRow,
} from '@/services/extensionApi';

const ExtensionPointManagement: React.FC = () => {
  const [rows, setRows] = useState<ExtPointRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ExtPointRow | null>(null);
  const [form] = Form.useForm<ExtPointPayload>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listExtPoints());
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载扩展点失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ enabled: true, domain: 'common', category: 'default' });
    setModalOpen(true);
  };

  const openEdit = (record: ExtPointRow) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      interfaceName: record.interfaceName,
      domain: record.domain,
      category: record.category,
      enabled: record.enabled,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editing?.id) {
        await updateExtPoint(editing.id, values);
        message.success('更新成功');
      } else {
        await createExtPoint(values);
        message.success('创建成功');
      }
      setModalOpen(false);
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败');
    }
  };

  const handleToggle = async (record: ExtPointRow, enable: boolean) => {
    try {
      await postExtPointEnable(record.id, enable);
      message.success(enable ? '已启用' : '已禁用');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteExtPoint(id);
      message.success('已删除');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败');
    }
  };

  const columns: ColumnsType<ExtPointRow> = [
    { title: 'ID', dataIndex: 'id', width: 72 },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    { title: '接口', dataIndex: 'interfaceName', ellipsis: true },
    { title: '领域', dataIndex: 'domain', width: 100 },
    { title: '分类', dataIndex: 'category', width: 100 },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 88,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '禁用'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => handleToggle(record, !record.enabled)}
          >
            {record.enabled ? '禁用' : '启用'}
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建扩展点
        </Button>
        <Button icon={<ReloadOutlined />} onClick={load}>
          刷新
        </Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={{ pageSize: 10 }} />
      <Modal
        title={editing ? '编辑扩展点' : '新建扩展点'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="interfaceName"
            label="接口全限定名"
            rules={[{ required: true, message: '请输入接口名' }]}
          >
            <Input placeholder="com.bone.example.ExtPoint" />
          </Form.Item>
          <Form.Item name="domain" label="领域">
            <Input />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default ExtensionPointManagement;
