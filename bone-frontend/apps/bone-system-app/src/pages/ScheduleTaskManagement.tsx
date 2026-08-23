import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Form, Input, Modal, message, Card, Switch, Tag, Pagination } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ScheduleTask } from '@/types';
import { scheduleTaskApi } from '@/services/api';

const ScheduleTaskManagement: React.FC = () => {
  const [data, setData] = useState<ScheduleTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScheduleTask | null>(null);
  const [form] = Form.useForm();

  const fetchData = async (page = 1, pageSize = 20) => {
    setLoading(true);
    try {
      const res = await scheduleTaskApi.getScheduleTaskPage({ pageNum: page, pageSize });
      setData(res.data.list);
      setPagination({ current: page, pageSize, total: res.data.total });
    } catch {
      message.error('获取定时任务失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 'DISABLED' });
    setModalOpen(true);
  };

  const openEdit = (record: ScheduleTask) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await scheduleTaskApi.updateScheduleTask(editing.id!, { name: values.name, cron: values.cron, handler: values.handler });
        message.success('任务已更新');
      } else {
        await scheduleTaskApi.createScheduleTask({ name: values.name, cron: values.cron, handler: values.handler, status: values.status });
        message.success('任务已创建');
      }
      setModalOpen(false);
      fetchData(pagination.current);
    } catch {
      message.error('保存失败');
    }
  };

  const remove = (record: ScheduleTask) => {
    Modal.confirm({
      title: `确认删除任务「${record.name}」？`,
      onOk: async () => {
        await scheduleTaskApi.deleteScheduleTask(record.id!);
        message.success('任务已删除');
        fetchData(pagination.current);
      },
    });
  };

  const toggle = async (record: ScheduleTask, enabled: boolean) => {
    try {
      await scheduleTaskApi.toggleScheduleTask(record.id!, enabled);
      message.success(enabled ? '任务已启用' : '任务已停用');
      fetchData(pagination.current);
    } catch {
      message.error('操作失败');
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '任务名称', dataIndex: 'name', width: 160 },
    { title: 'CRON', dataIndex: 'cron', width: 140 },
    { title: '处理器', dataIndex: 'handler', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => (v === 'ENABLED' ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '启停',
      width: 90,
      render: (_: unknown, record: ScheduleTask) => (
        <Switch
          checked={record.status === 'ENABLED'}
          onChange={(checked) => toggle(record, checked)}
        />
      ),
    },
    { title: '上次执行', dataIndex: 'lastRunAt', width: 170, render: (v?: string) => v || '-' },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, record: ScheduleTask) => (
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
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={() => fetchData(pagination.current)}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建任务
        </Button>
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1200 }}
        pagination={false}
      />
      <Pagination
        style={{ marginTop: 16, textAlign: 'right' }}
        current={pagination.current}
        pageSize={pagination.pageSize}
        total={pagination.total}
        showSizeChanger
        showTotal={(total) => `共 ${total} 条`}
        onChange={(page, pageSize) => fetchData(page, pageSize)}
      />
      <Modal
        title={editing ? '编辑定时任务' : '新建定时任务'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="如：日志清理" />
          </Form.Item>
          <Form.Item name="cron" label="CRON 表达式" rules={[{ required: true, message: '请输入 CRON' }]}>
            <Input placeholder="如：0 0 2 * * ?" />
          </Form.Item>
          <Form.Item name="handler" label="处理器 Bean" rules={[{ required: true, message: '请输入处理器' }]}>
            <Input placeholder="如：logCleanTaskHandler" />
          </Form.Item>
          {!editing && (
            <Form.Item name="status" label="初始状态">
              <Input placeholder="ENABLED / DISABLED" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
};

export default ScheduleTaskManagement;
