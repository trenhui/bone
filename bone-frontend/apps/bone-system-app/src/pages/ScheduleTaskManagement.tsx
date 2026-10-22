import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Form, Input, Modal, message, Card, Switch, Tag, Pagination, Select, Drawer } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ScheduleTask, ScheduleTaskLog } from '@/types';
import { scheduleTaskApi } from '@/services/api';
import { normalizeTotal, formatDate } from '@bone/shared-utils';
import { Auth, AuthButton, usePermission } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';

const ScheduleTaskManagement: React.FC = () => {
  const { t } = useTranslation();
  // 启停 Switch 不是 Button，无法用 AuthButton 替代，单独取权限码决定是否可写。
  const { has } = usePermission();
  const canWrite = has(BonePermissionCodes.SYS_SCHEDULE_WRITE);
  const [data, setData] = useState<ScheduleTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScheduleTask | null>(null);
  const [form] = Form.useForm();
  const [keyword, setKeyword] = useState('');

  const fetchData = async (page = 1, pageSize = 20, kw = keyword) => {
    setLoading(true);
    try {
      const res = await scheduleTaskApi.getScheduleTaskPage({ page: page, size: pageSize, keyword: kw.trim() || undefined });
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

  // ---- 执行记录抽屉（sys_schedule_task_log 流水，按任务倒序分页）----
  const [execVisible, setExecVisible] = useState(false);
  const [execTask, setExecTask] = useState<ScheduleTask | null>(null);
  const [execData, setExecData] = useState<ScheduleTaskLog[]>([]);
  const [execLoading, setExecLoading] = useState(false);
  const [execPagination, setExecPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const fetchExecutions = async (task: ScheduleTask, page = 1, pageSize = 10) => {
    setExecLoading(true);
    try {
      const res = await scheduleTaskApi.getScheduleTaskExecutions(task.id!, { page, size: pageSize });
      setExecData(res.data.records);
      setExecPagination({ current: page, pageSize, total: normalizeTotal(res.data.total) });
    } catch {
      message.error(t('system.scheduleTaskManagement.fetchExecutionsFailed'));
    } finally {
      setExecLoading(false);
    }
  };

  const openExecutions = (record: ScheduleTask) => {
    setExecTask(record);
    setExecVisible(true);
    fetchExecutions(record);
  };

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
      render: (_: unknown, record: ScheduleTask) =>
        canWrite ? (
          <Switch
            checked={record.status === 'ENABLED'}
            onChange={(checked) => toggle(record, checked)}
          />
        ) : (
          <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>
            {record.status === 'ENABLED'
              ? t('system.scheduleTaskManagement.statusEnabled')
              : t('system.scheduleTaskManagement.statusDisabled')}
          </Tag>
        ),
    },
    { title: t('system.scheduleTaskManagement.columnLastRun'), dataIndex: 'lastRunAt', width: 170, render: (v?: string) => v || '-' },
    { title: t('system.scheduleTaskManagement.columnNextRun'), dataIndex: 'nextRunAt', width: 170, render: (v?: string) => v || '-' },
    {
      title: t('system.scheduleTaskManagement.columnAction'),
      width: 260,
      render: (_: unknown, record: ScheduleTask) => (
        <Space>
          {/* 执行记录是只读查询，无写权限也可看（与任务读同权，后端 /executions 无写入门禁）。 */}
          <Button type="link" size="small" onClick={() => openExecutions(record)}>
            {t('system.scheduleTaskManagement.executions')}
          </Button>
          {/* 立即执行会真实触发任务处理器，属写操作（副作用），与增删改同码。 */}
          <AuthButton
            code={BonePermissionCodes.SYS_SCHEDULE_WRITE}
            type="link"
            size="small"
            loading={runningIds.has(record.id!)}
            onClick={() => runNow(record)}
          >
            {t('system.scheduleTaskManagement.runNow')}
          </AuthButton>
          <AuthButton
            code={BonePermissionCodes.SYS_SCHEDULE_WRITE}
            type="link"
            size="small"
            onClick={() => openEdit(record)}
          >
            {t('system.scheduleTaskManagement.edit')}
          </AuthButton>
          {/* 删除走 Modal.confirm（命令式），无法包 <Auth>，故门禁落在触发按钮上。 */}
          <AuthButton
            code={BonePermissionCodes.SYS_SCHEDULE_WRITE}
            type="link"
            size="small"
            danger
            onClick={() => remove(record)}
          >
            {t('system.scheduleTaskManagement.delete')}
          </AuthButton>
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
          onPressEnter={() => fetchData(1, pagination.pageSize)}
          onClear={() => {
            setKeyword('');
            fetchData(1, pagination.pageSize, '');
          }}
          style={{ width: 240 }}
        />
        <Button icon={<ReloadOutlined />} onClick={() => fetchData(pagination.current, pagination.pageSize)}>
          {t('system.scheduleTaskManagement.refresh')}
        </Button>
        <AuthButton
          code={BonePermissionCodes.SYS_SCHEDULE_WRITE}
          type="primary"
          icon={<PlusOutlined />}
          onClick={openCreate}
        >
          {t('system.scheduleTaskManagement.createTask')}
        </AuthButton>
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1300 }}
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
      {/* 弹窗「确定」由 antd 内部渲染，用 <Auth> 包住 Modal，无权限时弹窗不渲染。 */}
      <Auth code={BonePermissionCodes.SYS_SCHEDULE_WRITE}>
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
                {/*
                  ★ 枚举必须走 Select，不能用自由文本 Input。
                  原写法 `<Input placeholder="ENABLED / DISABLED" />` 把「初始状态」交给用户手打：
                  打错一个字符（或大小写不符）服务端就回 400 SYS_SCHEDULE_TASK_STATUS_INVALID，
                  而用户在填表时完全无从知道合法取值只有两个。（2026-10-16 全模块联调实证：
                  新建定时任务填任意文本 ⇒ 400，弹窗卡住不关，用户只能靠猜。）
                */}
                <Select
                  options={[
                    { value: 'ENABLED', label: t('system.scheduleTaskManagement.statusEnabled') },
                    { value: 'DISABLED', label: t('system.scheduleTaskManagement.statusDisabled') },
                  ]}
                />
              </Form.Item>
            )}
          </Form>
        </Modal>
      </Auth>

      {/* 执行记录抽屉：某任务的执行流水（append-only），失败可追溯。 */}
      <Drawer
        title={
          execTask
            ? t('system.scheduleTaskManagement.executionsTitle', { name: execTask.name })
            : t('system.scheduleTaskManagement.executions')
        }
        width={720}
        open={execVisible}
        onClose={() => setExecVisible(false)}
      >
        <Table<ScheduleTaskLog>
          rowKey="id"
          columns={[
            {
              title: t('system.scheduleTaskManagement.columnStartedAt'),
              dataIndex: 'startedAt',
              width: 180,
              render: (v?: string) => (v ? formatDate(v) : '-'),
            },
            {
              title: t('system.scheduleTaskManagement.columnTriggerType'),
              dataIndex: 'triggerType',
              width: 100,
              render: (v: string) =>
                v === 'MANUAL' ? (
                  <Tag color="blue">{t('system.scheduleTaskManagement.triggerManual')}</Tag>
                ) : (
                  <Tag>{t('system.scheduleTaskManagement.triggerCron')}</Tag>
                ),
            },
            {
              title: t('system.scheduleTaskManagement.columnRunStatus'),
              dataIndex: 'runStatus',
              width: 90,
              render: (v: string) =>
                v === 'SUCCESS' ? (
                  <Tag color="green">{t('system.scheduleTaskManagement.runStatusSuccess')}</Tag>
                ) : (
                  <Tag color="red">{t('system.scheduleTaskManagement.runStatusFailed')}</Tag>
                ),
            },
            {
              title: t('system.scheduleTaskManagement.columnDuration'),
              dataIndex: 'durationMs',
              width: 100,
              render: (v?: number) => (v == null ? '-' : `${v} ms`),
            },
            {
              title: t('system.scheduleTaskManagement.columnMessage'),
              dataIndex: 'message',
              ellipsis: true,
              render: (v?: string) => v || '-',
            },
          ]}
          dataSource={execData}
          loading={execLoading}
          pagination={false}
          locale={{ emptyText: t('system.scheduleTaskManagement.executionsEmpty') }}
        />
        <Pagination
          style={{ marginTop: 16, textAlign: 'right' }}
          current={execPagination.current}
          pageSize={execPagination.pageSize}
          total={execPagination.total}
          showSizeChanger
          onChange={(page, pageSize) => execTask && fetchExecutions(execTask, page, pageSize)}
        />
      </Drawer>
    </Card>
  );
};

export default ScheduleTaskManagement;
