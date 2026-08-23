import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Button, Card, Col, Form, Input, Modal, Popconfirm, Row,
  Space, Tag, Typography, message,
} from 'antd';
import {
  PlusOutlined, DeleteOutlined,
} from '@ant-design/icons';
import { appApi, type BoneApplication } from '../services/appModuleApi';

const { Title, Text, Paragraph } = Typography;

const PERM_COLORS: Record<string, string> = { admin: 'red', developer: 'blue', viewer: 'green' };
const PERM_LABELS: Record<string, string> = { admin: '管理员', developer: '开发者', viewer: '只读' };

const ApplicationManagement: React.FC = () => {
  const navigate = useNavigate();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const loadApps = async () => {
    setLoading(true);
    try {
      const res = await appApi.listMine({ pageNum: 1, pageSize: 50 });
      if (res.code === 200) {
        setApps(res.data.list);
      }
    } catch {
      message.error('加载应用列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadApps(); }, []);

  const handleCreate = async () => {
    const values = await form.validateFields();
    try {
      const res = await appApi.create(values);
      if (res.code === 200) {
        message.success('应用创建成功');
        setModalOpen(false);
        form.resetFields();
        loadApps();
      }
    } catch {
      message.error('创建失败');
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
    <div className="page">
      <Row justify="space-between" align="middle" style={{ marginBottom: 24 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>应用管理</Title>
          <Text type="secondary">选择有权限的应用进入领域建模</Text>
        </Col>
        <Col>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            新建应用
          </Button>
        </Col>
      </Row>

      {apps.length === 0 && !loading ? (
        <Alert type="info" message="暂无应用，请先创建" />
      ) : (
        <Row gutter={[16, 16]}>
          {apps.map((app) => (
            <Col xs={24} sm={12} lg={8} xl={6} key={app.id}>
              <Card
                hoverable
                loading={loading}
                actions={[
                  <Button type="link" size="small" onClick={() => {
                    navigate(`/apps/${app.id}/modules`);
                  }}>
                    进入建模
                  </Button>,
                  ...(app.myRole === 'admin'
                    ? [<Popconfirm title="确认删除？" onConfirm={() => handleDelete(app.id)}>
                        <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                      </Popconfirm>]
                    : []),
                ]}
              >
                <Card.Meta
                  avatar={<span style={{ fontSize: 36 }}>{app.icon || '📱'}</span>}
                  title={
                    <Space>
                      {app.name}
                      <Text code style={{ fontSize: 12 }}>{app.code}</Text>
                    </Space>
                  }
                  description={
                    <>
                      <Paragraph type="secondary" ellipsis style={{ marginBottom: 8 }}>
                        {app.description}
                      </Paragraph>
                      {app.myRole && (
                        <Tag color={PERM_COLORS[app.myRole]}>{PERM_LABELS[app.myRole]}</Tag>
                      )}
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

      <Modal title="新建应用" open={modalOpen} onOk={handleCreate} onCancel={() => { setModalOpen(false); form.resetFields(); }} destroyOnHidden>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="应用名称" rules={[{ required: true }]}>
            <Input placeholder="如：订单管理系统" />
          </Form.Item>
          <Form.Item name="code" label="应用编码" rules={[{ required: true }]} tooltip="全局唯一标识">
            <Input placeholder="如：OMS" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ApplicationManagement;
