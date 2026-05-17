import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Upload,
  message,
} from 'antd';
import {
  CloudUploadOutlined,
  HistoryOutlined,
  PlusOutlined,
  ReloadOutlined,
  RollbackOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload';
import {
  createPlugin,
  deletePlugin,
  deployPlugin,
  formatStudioError,
  listExtPoints,
  listPluginVersions,
  listPlugins,
  publishPluginRuntime,
  rollbackPlugin,
  simulatePlugin,
  updatePlugin,
  uploadPlugin,
  type ExtensionPayload,
  type ExtensionRow,
  type ExtPointRow,
  type PluginVersionRow,
} from '@/services/extensionApi';

const PluginManagement: React.FC = () => {
  const [plugins, setPlugins] = useState<ExtensionRow[]>([]);
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
  const [form] = Form.useForm<ExtensionPayload>();
  const [uploadForm] = Form.useForm<{
    extPointId: number;
    name: string;
    className: string;
    version: string;
    description?: string;
    pluginId?: number;
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [pluginRows, pointRows] = await Promise.all([listPlugins(), listExtPoints()]);
      setPlugins(pluginRows);
      setExtPoints(pointRows);
    } catch (e) {
      message.error(formatStudioError(e, '加载插件失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const extPointName = (extPointId: number) =>
    extPoints.find((p) => p.id === extPointId)?.name ?? String(extPointId);

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
        await updatePlugin(editing.id, values);
        message.success('更新成功');
      } else {
        await createPlugin(values);
        message.success('创建成功');
      }
      setModalOpen(false);
      load();
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
      load();
    } catch (e) {
      message.error(formatStudioError(e, '上传失败'));
    }
  };

  const handleDeploy = async (id: number, deploy: boolean) => {
    try {
      await deployPlugin(id, deploy);
      message.success(deploy ? '部署成功' : '已卸载');
      load();
    } catch (e) {
      message.error(formatStudioError(e, '操作失败'));
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
      load();
    } catch (e) {
      message.error(formatStudioError(e, '回滚失败'));
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deletePlugin(id);
      message.success('已删除');
      load();
    } catch (e) {
      message.error(formatStudioError(e, '删除失败'));
    }
  };

  const columns: ColumnsType<ExtensionRow> = [
    { title: 'ID', dataIndex: 'id', width: 72 },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    {
      title: '扩展点',
      dataIndex: 'extPointId',
      width: 140,
      render: (id: number) => extPointName(id),
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
      render: (_, record) => (
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
            <Button type="link" size="small" onClick={() => handleDeploy(record.id, false)}>
              卸载
            </Button>
          ) : (
            <Button type="link" size="small" onClick={() => handleDeploy(record.id, true)}>
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
          {record.enabled && (
            <Button type="link" size="small" onClick={() => handleSimulate(record.id)}>
              模拟调用
            </Button>
          )}
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const versionColumns: ColumnsType<PluginVersionRow> = [
    { title: '版本', dataIndex: 'version', width: 100 },
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
      width: 100,
      render: (_, row) =>
        row.active ? null : (
          <Button
            type="link"
            size="small"
            icon={<RollbackOutlined />}
            onClick={() => selectedPlugin && handleRollback(selectedPlugin.id, row.version)}
          >
            回滚
          </Button>
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
        <Button icon={<ReloadOutlined />} onClick={load}>
          刷新
        </Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={plugins} pagination={{ pageSize: 10 }} />

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
    </>
  );
};

export default PluginManagement;
