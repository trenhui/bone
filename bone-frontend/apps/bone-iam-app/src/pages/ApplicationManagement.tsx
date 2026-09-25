import React, { useCallback, useEffect, useState } from 'react';
import {
  App as AntApp,
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Space,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { appApi, type BoneApplication, type CreateAppReq, type UpdateAppReq } from '../services/appApi';
import ModulePage from '../components/ModulePage';

const { Text, Paragraph } = Typography;

const PERM_COLORS: Record<string, string> = { admin: 'red', developer: 'blue', viewer: 'green' };
const PERM_LABELS: Record<string, string> = { admin: '管理员', developer: '开发者', viewer: '只读' };

/**
 * 应用管理（IAM 限界上下文）。
 *
 * 应用(App)聚合的管理入口：新建/编辑/删除。应用同时是元数据建模的归属维度，
 * 建模链路（应用 → 模块 → 实体 → 字段）见 bone-metadata-app「建模工作台」；
 * 应用与模块的后端真源在 bone-iam `AppController`（写操作需 `iam:apps:write`）。
 */
const ApplicationManagement: React.FC = () => {
  const { message } = AntApp.useApp();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editApp, setEditApp] = useState<BoneApplication | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchApps = useCallback(async () => {
    setLoading(true);
    try {
      const res = await appApi.listMine({ pageNum: 1, pageSize: 50 });
      if (res.code === 200) {
        setApps(res.data.list ?? []);
      }
    } catch {
      message.error('获取应用列表失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    fetchApps();
  }, [fetchApps]);

  const openCreate = () => {
    setEditApp(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (app: BoneApplication) => {
    setEditApp(app);
    form.setFieldsValue({ name: app.name, description: app.description, icon: app.icon });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editApp) {
        const res = await appApi.update(editApp.id, values as UpdateAppReq);
        if (res.code === 200) {
          message.success('应用更新成功');
        }
      } else {
        const res = await appApi.create(values as CreateAppReq);
        if (res.code === 200) {
          message.success('应用创建成功');
        }
      }
      setModalOpen(false);
      fetchApps();
    } catch {
      message.error(editApp ? '更新失败' : '创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      const res = await appApi.delete(id);
      if (res.code === 200) {
        message.success('已删除');
        setApps((prev) => prev.filter((a) => a.id !== id));
      }
    } catch {
      message.error('删除失败');
    }
  };

  return (
    <ModulePage
      title="应用管理"
      description="应用的创建与维护；应用作为元数据建模与授权的归属维度，建模入口见「元数据管理 → 建模工作台」"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchApps} loading={loading}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建应用
          </Button>
        </Space>
      }
      card={false}
    >
      {apps.length === 0 && !loading ? (
        <Alert type="info" showIcon message="暂无应用，点击右上角「新建应用」创建" />
      ) : (
        <Row gutter={[16, 16]}>
          {apps.map((app) => (
            <Col xs={24} sm={12} lg={8} xl={6} key={app.id}>
              <Card
                hoverable
                loading={loading && apps.length === 0}
                actions={[
                  <Button key="edit" type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(app)}>
                    编辑
                  </Button>,
                  <Popconfirm key="del" title="确认删除该应用？" onConfirm={() => handleDelete(app.id)}>
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>,
                ]}
              >
                <Card.Meta
                  avatar={<span style={{ fontSize: 36 }}>{app.icon || '📱'}</span>}
                  title={
                    <Space>
                      {app.name}
                      <Text code style={{ fontSize: 12 }}>
                        {app.code}
                      </Text>
                    </Space>
                  }
                  description={
                    <>
                      <Paragraph type="secondary" ellipsis style={{ marginBottom: 8 }}>
                        {app.description || '暂无描述'}
                      </Paragraph>
                      {app.myRole && <Tag color={PERM_COLORS[app.myRole]}>{PERM_LABELS[app.myRole]}</Tag>}
                      <div style={{ marginTop: 8 }}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {app.moduleCount} 模块 · {app.entityCount} 实体
                        </Text>
                      </div>
                    </>
                  }
                />
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Modal
        title={editApp ? '编辑应用' : '新建应用'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="应用名称"
            rules={[{ required: true, message: '请输入应用名称' }]}
          >
            <Input placeholder="如：订单管理系统" />
          </Form.Item>
          <Form.Item
            name="code"
            label="应用编码"
            rules={[{ required: true, message: '请输入应用编码' }]}
            tooltip="全局唯一标识，创建后不可修改"
            hidden={!!editApp}
          >
            <Input placeholder="如：OMS" disabled={!!editApp} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="应用的用途说明" />
          </Form.Item>
        </Form>
      </Modal>
    </ModulePage>
  );
};

export default ApplicationManagement;
