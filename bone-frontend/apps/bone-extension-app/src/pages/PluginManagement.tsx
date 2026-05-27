import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Drawer,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Upload,
  message,
} from 'antd';
import {
  ApiOutlined,
  CloudUploadOutlined,
  DisconnectOutlined,
  EllipsisOutlined,
  HistoryOutlined,
  PlusOutlined,
  ReloadOutlined,
  DownloadOutlined,
  RollbackOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload';
import type { MenuProps } from 'antd';
import {
  bindPlugin,
  createPlugin,
  deletePlugin,
  deployPlugin,
  formatStudioError,
  listExtPoints,
  listPluginVersions,
  listPlugins,
  newIdempotencyKey,
  publishPluginRuntime,
  type StudioPageResult,
  rollbackPlugin,
  simulatePlugin,
  unbindPlugin,
  updatePlugin,
  uploadPlugin,
  type ExtensionPayload,
  type ExtensionRow,
  type ExtPointRow,
  downloadPluginVersion,
  type PluginVersionRow,
} from '@/services/extensionApi';

const UNBOUND_EXT_POINT_ID = 0;

const PluginManagement: React.FC = () => {
  const [plugins, setPlugins] = useState<ExtensionRow[]>([]);
  const [pluginTotal, setPluginTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [extPoints, setExtPoints] = useState<ExtPointRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
  const [versionLoading, setVersionLoading] = useState(false);
  const [versions, setVersions] = useState<PluginVersionRow[]>([]);
  const [selectedPlugin, setSelectedPlugin] = useState<ExtensionRow | null>(null);
  const [editing, setEditing] = useState<ExtensionRow | null>(null);
  const [jarFile, setJarFile] = useState<File | null>(null);
  const [deploying, setDeploying] = useState<{ id: number; name: string; progress: number } | null>(
    null,
  );
  const [undeployingId, setUndeployingId] = useState<number | null>(null);
  const [bindTarget, setBindTarget] = useState<ExtensionRow | null>(null);
  const [bindForm] = Form.useForm<{ extPointId: number }>();
  const [bindSubmitting, setBindSubmitting] = useState(false);
  const [form] = Form.useForm<ExtensionPayload>();
  const [uploadForm] = Form.useForm<{
    extPointId: number;
    name: string;
    className: string;
    version: string;
    description?: string;
    pluginId?: number;
  }>();

  const load = useCallback(async (p: number, ps: number) => {
    setLoading(true);
    try {
      const [pluginResult, pointResult] = await Promise.all([
        listPlugins({ page: p, size: ps }),
        listExtPoints(),
      ]);
      const pluginList = Array.isArray(pluginResult)
        ? pluginResult
        : (pluginResult as StudioPageResult<ExtensionRow>).records;
      const pluginCount = Array.isArray(pluginResult)
        ? pluginResult.length
        : (pluginResult as StudioPageResult<ExtensionRow>).total ?? pluginList.length;
      setPlugins(pluginList);
      setPluginTotal(pluginCount);
      setExtPoints(Array.isArray(pointResult) ? pointResult : pointResult.records);
    } catch (e) {
      message.error(formatStudioError(e, '加载插件失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(page, pageSize);
  }, [load, page, pageSize]);

  const extPointName = (extPointId: number) => {
    if (!extPointId || extPointId === UNBOUND_EXT_POINT_ID) {
      return '未绑定';
    }
    return extPoints.find((p) => p.id === extPointId)?.name ?? `#${extPointId}`;
  };

  const isBound = (record: ExtensionRow) =>
    record.extPointId != null && record.extPointId !== UNBOUND_EXT_POINT_ID;

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      enabled: true,
      priority: 100,
      tenantCode: 'DEFAULT',
      bizCode: '*',
    });
    setModalOpen(true);
  };

  const openUpload = (plugin?: ExtensionRow) => {
    uploadForm.resetFields();
    setJarFile(null);
    if (plugin) {
      uploadForm.setFieldsValue({
        pluginId: plugin.id,
        extPointId: plugin.extPointId,
        name: plugin.name,
        className: plugin.className,
        version: '',
        description: plugin.description,
      });
    } else {
      uploadForm.setFieldsValue({ version: '1.0.0' });
    }
    setUploadOpen(true);
  };

  const openVersions = async (plugin: ExtensionRow) => {
    setSelectedPlugin(plugin);
    setVersionDrawerOpen(true);
    setVersionLoading(true);
    try {
      setVersions(await listPluginVersions(plugin.id));
    } catch (e) {
      message.error(formatStudioError(e, '加载版本失败'));
    } finally {
      setVersionLoading(false);
    }
  };

  const openEdit = (record: ExtensionRow) => {
    setEditing(record);
    form.setFieldsValue({
      extPointId: record.extPointId,
      name: record.name,
      description: record.description,
      className: record.className,
      tenantCode: record.tenantCode,
      bizCode: record.bizCode,
      priority: record.priority,
      config: record.config,
      enabled: record.enabled,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editing?.id) {
        await updatePlugin(editing.id, values, { version: editing.version });
        message.success('更新成功');
      } else {
        await createPlugin(values, { idempotencyKey: newIdempotencyKey() });
        message.success('创建成功');
      }
      setModalOpen(false);
      load(page, pageSize);
    } catch (e) {
      message.error(formatStudioError(e, '保存失败'));
    }
  };

  const handleUpload = async () => {
    const values = await uploadForm.validateFields();
    if (!jarFile) {
      message.warning('请选择 JAR 插件包');
      return;
    }
    try {
      await uploadPlugin({
        file: jarFile,
        name: values.name,
        className: values.className,
        extPointId: values.pluginId ? undefined : values.extPointId,
        pluginId: values.pluginId,
        version: values.version || '1.0.0',
        description: values.description,
      });
      message.success('上传成功');
      setUploadOpen(false);
      load(page, pageSize);
    } catch (e) {
      message.error(formatStudioError(e, '上传失败'));
    }
  };

  const handleDeploy = async (record: ExtensionRow, deploy: boolean) => {
    if (!deploy) {
      setUndeployingId(record.id);
      const hide = message.loading('卸载中…', 0);
      try {
        await deployPlugin(record.id, false, { sync: true });
        hide();
        message.success('已卸载');
        load(page, pageSize);
      } catch (e) {
        hide();
        message.error(formatStudioError(e, '卸载失败'));
      } finally {
        setUndeployingId(null);
      }
      return;
    }
    setDeploying({ id: record.id, name: record.name, progress: 0 });
    try {
      await deployPlugin(record.id, true, {
        sync: false,
        onProgress: (progress) =>
          setDeploying((prev) => (prev ? { ...prev, progress } : null)),
      });
      message.success('部署成功');
      load(page, pageSize);
    } catch (e) {
      message.error(formatStudioError(e, '部署失败'));
    } finally {
      setDeploying(null);
    }
  };

  const handlePublish = async (id: number) => {
    try {
      await publishPluginRuntime(id);
      message.success('已推送到运行时');
    } catch (e) {
      message.error(formatStudioError(e, '发布失败'));
    }
  };

  const handleSimulate = async (id: number) => {
    try {
      const log = await simulatePlugin(id);
      message.success(`模拟调用成功，耗时 ${log.durationMs ?? '-'} ms`);
    } catch (e) {
      message.error(formatStudioError(e, '模拟调用失败'));
    }
  };

  const handleRollback = async (pluginId: number, version?: string) => {
    try {
      await rollbackPlugin(pluginId, version);
      message.success(version ? `已回滚到 ${version}` : '已回滚到上一版本');
      if (selectedPlugin?.id === pluginId) {
        await openVersions({ ...selectedPlugin, id: pluginId });
      }
      load(page, pageSize);
    } catch (e) {
      message.error(formatStudioError(e, '回滚失败'));
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deletePlugin(id);
      message.success('已删除');
      load(page, pageSize);
    } catch (e) {
      message.error(formatStudioError(e, '删除失败'));
    }
  };

  const openBind = (record: ExtensionRow) => {
    setBindTarget(record);
    bindForm.resetFields();
    bindForm.setFieldsValue({
      extPointId: isBound(record) ? record.extPointId : (extPoints[0]?.id ?? 0),
    });
  };

  const handleBindSubmit = async () => {
    if (!bindTarget) return;
    const { extPointId } = await bindForm.validateFields();
    setBindSubmitting(true);
    try {
      await bindPlugin(bindTarget.id, extPointId);
      message.success(`已绑定到「${extPointName(extPointId)}」`);
      setBindTarget(null);
      load(page, pageSize);
    } catch (e) {
      message.error(formatStudioError(e, '绑定失败'));
    } finally {
      setBindSubmitting(false);
    }
  };

  const handleUnbind = async (record: ExtensionRow) => {
    try {
      await unbindPlugin(record.id);
      message.success('已解绑');
      load(page, pageSize);
    } catch (e) {
      message.error(formatStudioError(e, '解绑失败'));
    }
  };

  const buildMoreMenu = (record: ExtensionRow): MenuProps['items'] => {
    const items: NonNullable<MenuProps['items']> = [
      {
        key: 'bind',
        icon: <ApiOutlined />,
        label: isBound(record) ? '重新绑定扩展点…' : '绑定扩展点…',
        onClick: () => openBind(record),
      },
    ];
    if (isBound(record)) {
      items.push({
        key: 'unbind',
        icon: <DisconnectOutlined />,
        danger: true,
        disabled: record.enabled,
        label: record.enabled ? (
          <span>解绑扩展点（请先卸载）</span>
        ) : (
          <Popconfirm
            title={`解绑「${record.name}」与「${extPointName(record.extPointId)}」？`}
            description="解绑后插件将不参与该扩展点路由，需重新绑定后才能再次部署到该点。"
            onConfirm={() => handleUnbind(record)}
            okText="解绑"
            cancelText="取消"
          >
            <span>解绑扩展点</span>
          </Popconfirm>
        ),
      });
    }
    if (record.enabled) {
      items.push({
        key: 'simulate',
        icon: <ReloadOutlined />,
        label: '模拟调用',
        onClick: () => handleSimulate(record.id),
      });
    }
    return items;
  };

  const columns: ColumnsType<ExtensionRow> = [
    { title: 'ID', dataIndex: 'id', width: 72 },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    {
      title: '扩展点',
      dataIndex: 'extPointId',
      width: 160,
      render: (id: number) =>
        id && id !== UNBOUND_EXT_POINT_ID ? (
          extPointName(id)
        ) : (
          <Tag color="warning">未绑定</Tag>
        ),
    },
    { title: '实现类', dataIndex: 'className', ellipsis: true },
    { title: '优先级', dataIndex: 'priority', width: 80 },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 88,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'processing' : 'default'}>{enabled ? '已部署' : '未部署'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 360,
      render: (_, record) => {
        const deployDisabled = !isBound(record) && !record.enabled;
        return (
          <Space size="small" wrap>
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Button type="link" size="small" icon={<HistoryOutlined />} onClick={() => openVersions(record)}>
              版本
            </Button>
            <Button type="link" size="small" icon={<UploadOutlined />} onClick={() => openUpload(record)}>
              上传包
            </Button>
            {record.enabled ? (
              <Button
                type="link"
                size="small"
                loading={undeployingId === record.id}
                disabled={(!!deploying || undeployingId != null) && undeployingId !== record.id}
                onClick={() => handleDeploy(record, false)}
              >
                卸载
              </Button>
            ) : (
              <Button
                type="link"
                size="small"
                loading={deploying?.id === record.id}
                disabled={
                  deployDisabled ||
                  ((!!deploying || undeployingId != null) && deploying?.id !== record.id)
                }
                title={deployDisabled ? '请先绑定扩展点再部署' : undefined}
                onClick={() => handleDeploy(record, true)}
              >
                部署
              </Button>
            )}
            <Button
              type="link"
              size="small"
              icon={<CloudUploadOutlined />}
              onClick={() => handlePublish(record.id)}
            >
              推送运行时
            </Button>
            <Dropdown menu={{ items: buildMoreMenu(record) }} trigger={['click']}>
              <Button type="link" size="small" icon={<EllipsisOutlined />}>
                更多
              </Button>
            </Dropdown>
            <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  const handleDownloadVersion = async (pluginId: number, version: string) => {
    try {
      await downloadPluginVersion(pluginId, version);
      message.success('制品下载已开始');
    } catch (e) {
      message.error(formatStudioError(e, '下载失败'));
    }
  };

  const versionColumns: ColumnsType<PluginVersionRow> = [
    { title: '版本', dataIndex: 'version', width: 100 },
    {
      title: '部署状态',
      dataIndex: 'deploymentStatus',
      width: 110,
      render: (status?: string) => (status ? <Tag>{status}</Tag> : '—'),
    },
    {
      title: '状态',
      dataIndex: 'active',
      width: 80,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'default'}>{active ? '当前' : '历史'}</Tag>
      ),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 100,
      render: (size: number) => `${(size / 1024).toFixed(1)} KB`,
    },
    { title: '校验和', dataIndex: 'checksum', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => selectedPlugin && handleDownloadVersion(selectedPlugin.id, row.version)}
          >
            下载
          </Button>
          {!row.active && selectedPlugin ? (
            <Button
              type="link"
              size="small"
              icon={<RollbackOutlined />}
              onClick={() => handleRollback(selectedPlugin.id, row.version)}
            >
              回滚
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          注册插件
        </Button>
        <Button icon={<UploadOutlined />} onClick={() => openUpload()}>
          上传 JAR
        </Button>
        <Button icon={<ReloadOutlined />} onClick={() => load(page, pageSize)}>
          刷新
        </Button>
      </Space>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={plugins}
        pagination={{
          current: page,
          pageSize,
          total: pluginTotal,
          showSizeChanger: true,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
      />

      <Modal
        title={editing ? '编辑插件' : '注册插件'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="extPointId" label="关联扩展点" rules={[{ required: true }]}>
            <Select
              options={extPoints.map((p) => ({ value: p.id, label: `${p.name} (#${p.id})` }))}
              placeholder="选择扩展点"
            />
          </Form.Item>
          <Form.Item name="name" label="插件名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="className" label="实现类" rules={[{ required: true }]}>
            <Input placeholder="com.bone.example.MyExtension" />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="tenantCode" label="租户">
            <Input />
          </Form.Item>
          <Form.Item name="bizCode" label="业务码">
            <Input />
          </Form.Item>
          <Form.Item name="config" label="路由配置 (JSON)">
            <Input.TextArea rows={3} placeholder='{"traffic":80,"defaultImpl":true}' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="上传插件 JAR"
        open={uploadOpen}
        onOk={handleUpload}
        onCancel={() => setUploadOpen(false)}
        width={560}
        destroyOnClose
      >
        <Form form={uploadForm} layout="vertical">
          <Form.Item name="pluginId" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            name="extPointId"
            label="关联扩展点"
            rules={[{ required: !uploadForm.getFieldValue('pluginId'), message: '请选择扩展点' }]}
          >
            <Select
              disabled={!!uploadForm.getFieldValue('pluginId')}
              options={extPoints.map((p) => ({ value: p.id, label: `${p.name} (#${p.id})` }))}
            />
          </Form.Item>
          <Form.Item name="name" label="插件名称" rules={[{ required: true }]}>
            <Input disabled={!!uploadForm.getFieldValue('pluginId')} />
          </Form.Item>
          <Form.Item name="className" label="实现类" rules={[{ required: true }]}>
            <Input disabled={!!uploadForm.getFieldValue('pluginId')} />
          </Form.Item>
          <Form.Item name="version" label="版本号" rules={[{ required: true }]}>
            <Input placeholder="1.0.0" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item label="JAR 文件" required>
            <Upload
              accept=".jar"
              maxCount={1}
              beforeUpload={(file) => {
                setJarFile(file);
                return false;
              }}
              onRemove={() => setJarFile(null)}
              fileList={
                jarFile
                  ? [{ uid: '-1', name: jarFile.name, status: 'done' } as UploadFile]
                  : []
              }
            >
              <Button icon={<UploadOutlined />}>选择 JAR 文件</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="正在部署插件"
        open={!!deploying}
        footer={null}
        closable={false}
        maskClosable={false}
        width={420}
      >
        {deploying ? (
          <>
            <p style={{ marginBottom: 12 }}>
              {deploying.name}（#{deploying.id}）
            </p>
            <Progress
              percent={deploying.progress}
              status={deploying.progress >= 100 ? 'success' : 'active'}
            />
            <p style={{ marginTop: 8, color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
              大制品部署为异步任务，完成后将自动刷新列表
            </p>
          </>
        ) : null}
      </Modal>

      <Drawer
        title={selectedPlugin ? `版本历史 — ${selectedPlugin.name}` : '版本历史'}
        open={versionDrawerOpen}
        onClose={() => setVersionDrawerOpen(false)}
        width={640}
        extra={
          selectedPlugin ? (
            <Button icon={<RollbackOutlined />} onClick={() => handleRollback(selectedPlugin.id)}>
              回滚上一版本
            </Button>
          ) : null
        }
      >
        <Table
          rowKey="id"
          loading={versionLoading}
          columns={versionColumns}
          dataSource={versions}
          pagination={false}
          size="small"
        />
      </Drawer>

      <Modal
        title={bindTarget ? `绑定扩展点 — ${bindTarget.name}` : '绑定扩展点'}
        open={!!bindTarget}
        onOk={handleBindSubmit}
        confirmLoading={bindSubmitting}
        onCancel={() => setBindTarget(null)}
        okText="确认绑定"
        cancelText="取消"
        destroyOnClose
        width={520}
      >
        {bindTarget ? (
          <Form form={bindForm} layout="vertical" preserve={false}>
            {isBound(bindTarget) && (
              <p style={{ color: 'rgba(0,0,0,0.55)', marginBottom: 16 }}>
                当前绑定：<strong>{extPointName(bindTarget.extPointId)}</strong>
                ，选择新的扩展点将覆盖原绑定。
              </p>
            )}
            <Form.Item
              name="extPointId"
              label="目标扩展点"
              rules={[{ required: true, message: '请选择目标扩展点' }]}
            >
              <Select
                placeholder="选择扩展点"
                showSearch
                optionFilterProp="label"
                options={extPoints
                  .filter((p) => p.enabled !== false)
                  .map((p) => ({
                    value: p.id,
                    label: `${p.name} (#${p.id})`,
                  }))}
                notFoundContent="没有可用扩展点，请先到「扩展点管理」启用"
              />
            </Form.Item>
            <p style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
              提示：绑定后插件仍处于「未部署」状态，需在表格中执行「部署」才能进入路由。
            </p>
          </Form>
        ) : null}
      </Modal>
    </>
  );
};

export default PluginManagement;
