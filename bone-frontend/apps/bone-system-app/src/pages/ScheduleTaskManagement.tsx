import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Form, Input, Modal, message, Card, Switch, Tag, Pagination } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ScheduleTask } from '@/types';
import { scheduleTaskApi } from '@/services/api';
import { normalizeTotal } from '@bone/shared-utils';

const ScheduleTaskManagement: React.FC = () => {
  const { t } = useTranslation();
  const [data, setData] = useState<ScheduleTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScheduleTask | null>(null);
  const [form] = Form.useForm();
  const [keyword, setKeyword] = useState('');

  const fetchData = async (page = 1, pageSize = 20) => {
    setLoading(true);
    try {
      const res = await scheduleTaskApi.getScheduleTaskPage({ pageNum: page, pageSize });
      // 权威字段是 records；list 是后端 PageResult 的 @Deprecated 兼容 getter，
      // 将在 @JsonIgnore 收敛后消失（Bone-API-规范 §5.3）。此处刻意只读 records。
      setData(res.data.records);
      setPagination({ current: page, pageSize, total: normalizeTotal(res.data.total) });
    } catch {
      message.error(t('system.scheduleTaskManagement.fetchTaskFailed'));
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
        message.success(t('system.scheduleTaskManagement.taskUpdated'));
      } else {
        await scheduleTaskApi.createScheduleTask({ name: values.name, cron: values.cron, handler: values.handler, status: values.status });
        message.success(t('system.scheduleTaskManagement.taskCreated'));
      }
      setModalOpen(false);
      fetchData(pagination.current);
    } catch {
      message.error(t('system.scheduleTaskManagement.saveFailed'));
    }
  };

  const remove = (record: ScheduleTask) => {
    Modal.confirm({
      title: t('system.scheduleTaskManagement.confirmDeleteTask', { name: record.name }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        await scheduleTaskApi.deleteScheduleTask(record.id!);
        message.success(t('system.scheduleTaskManagement.taskDeleted'));
        fetchData(pagination.current);
      },
    });
  };

  const toggle = async (record: ScheduleTask, enabled: boolean) => {
    try {
      await scheduleTaskApi.toggleScheduleTask(record.id!, enabled);
      message.success(enabled ? t('system.scheduleTaskManagement.taskEnabled') : t('system.scheduleTaskManagement.taskDisabled'));
      fetchData(pagination.current);
    } catch {
      message.error(t('system.scheduleTaskManagement.operationFailed'));
    }
  };

  const [runningIds, setRunningIds] = useState<Set<string>>(new Set());

  const runNow = async (record: ScheduleTask) => {
    setRunningIds((prev) => new Set(prev).add(record.id!));
    try {
      const res = await scheduleTaskApi.runScheduleTaskNow(record.id!);
      message.success(t('system.scheduleTaskManagement.runSuccess', { name: record.name, cost: res.data }));
      fetchData(pagination.current);
    } catch (err) {
      const detail = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      message.error(detail ? t('system.scheduleTaskManagement.runFailedWithDetail', { detail }) : t('system.scheduleTaskManagement.runFailed'));
    } finally {
      setRunningIds((prev) => {
        const next = new Set(prev);
        next.delete(record.id!);
        return next;
      });
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('system.scheduleTaskManagement.taskName'), dataIndex: 'name', width: 160 },
    { title: 'CRON', dataIndex: 'cron', width: 140 },
    { title: t('system.scheduleTaskManagement.columnHandler'), dataIndex: 'handler', width: 180 },
    {
      title: t('system.scheduleTaskManagement.columnStatus'),
      dataIndex: 'status',
      width: 100,
      render: (v: string) => (v === 'ENABLED' ? <Tag color="green">{t('system.scheduleTaskManagement.statusEnabled')}</Tag> : <Tag>{t('system.scheduleTaskManagement.statusDisabled')}</Tag>),
    },
    {
      title: t('system.scheduleTaskManagement.columnToggle'),
      width: 90,
      render: (_: unknown, record: ScheduleTask) => (
        <Switch
          checked={record.status === 'ENABLED'}
          onChange={(checked) => toggle(record, checked)}
        />
      ),
    },
    { title: t('system.scheduleTaskManagement.columnLastRun'), dataIndex: 'lastRunAt', width: 170, render: (v?: string) => v || '-' },
    {
      title: t('system.scheduleTaskManagement.columnAction'),
      width: 190,
      render: (_: unknown, record: ScheduleTask) => (
        <Space>
          <Button
            type="link"
            size="small"
            loading={runningIds.has(record.id!)}
            onClick={() => runNow(record)}
          >
            {t('system.scheduleTaskManagement.runNow')}
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            {t('system.scheduleTaskManagement.edit')}
          </Button>
          <Button type="link" size="small" danger onClick={() => remove(record)}>
            {t('system.scheduleTaskManagement.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder={t('system.scheduleTaskManagement.searchPlaceholder')}
          prefix={<SearchOutlined />}
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: 240 }}
        />
        <Button icon={<ReloadOutlined />} onClick={() => fetchData(pagination.current)}>
          {t('system.scheduleTaskManagement.refresh')}
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          {t('system.scheduleTaskManagement.createTask')}
        </Button>
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data.filter((item) => {
          const kw = keyword.trim().toLowerCase();
          if (!kw) return true;
          return [item.name, item.handler].some((v) => (v ?? '').toLowerCase().includes(kw));
        })}
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
        showTotal={(total) => t('system.scheduleTaskManagement.totalItems', { total })}
        onChange={(page, pageSize) => fetchData(page, pageSize)}
      />
      <Modal
        title={editing ? t('system.scheduleTaskManagement.editModalTitle') : t('system.scheduleTaskManagement.createModalTitle')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('system.scheduleTaskManagement.taskName')} rules={[{ required: true, message: t('system.scheduleTaskManagement.ruleNameRequired') }]}>
            <Input placeholder={t('system.scheduleTaskManagement.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="cron" label={t('system.scheduleTaskManagement.cronLabel')} rules={[{ required: true, message: t('system.scheduleTaskManagement.ruleCronRequired') }]}>
            <Input placeholder={t('system.scheduleTaskManagement.cronPlaceholder')} />
          </Form.Item>
          <Form.Item name="handler" label={t('system.scheduleTaskManagement.handlerLabel')} rules={[{ required: true, message: t('system.scheduleTaskManagement.ruleHandlerRequired') }]}>
            <Input placeholder={t('system.scheduleTaskManagement.handlerPlaceholder')} />
          </Form.Item>
          {!editing && (
            <Form.Item name="status" label={t('system.scheduleTaskManagement.initialStatusLabel')}>
              <Input placeholder="ENABLED / DISABLED" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
};

export default ScheduleTaskManagement;
