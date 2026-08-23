import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button, Breadcrumb, Card, Col, Form, Input, Modal,
  Row, Space, Tag, Typography, message, Statistic, Empty,
} from 'antd';
import {
  PlusOutlined, ArrowLeftOutlined, DatabaseOutlined,
  FolderOutlined, LinkOutlined,
} from '@ant-design/icons';
import {
  moduleApi, appApi, type BoneApplication, type BoneModule,
} from '../services/appModuleApi';

const { Title, Text, Paragraph } = Typography;

const STATUS_COLORS: Record<number, string> = { 0: 'green', 1: 'blue', 2: 'default' };
const STATUS_LABELS: Record<number, string> = { 0: '启用', 1: '停用', 2: '归档' };

const ModuleManagement: React.FC = () => {
  const { appId } = useParams<{ appId: string }>();
  const navigate = useNavigate();
  const [appInfo, setAppInfo] = useState<BoneApplication | null>(null);
  const [modules, setModules] = useState<BoneModule[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const appName = appInfo?.name || '未知应用';

  const loadData = async () => {
    if (!appId) return;
    setLoading(true);
    try {
      const [appRes, modRes] = await Promise.all([
        appApi.detail(appId),
        moduleApi.listByApp(appId, { pageNum: 1, pageSize: 50 }),
      ]);
      if (appRes.code === 200) setAppInfo(appRes.data);
      if (modRes.code === 200) {
        setModules(modRes.data.list);
      }
    } catch {
      message.error('加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [appId]);

  const stats = {
    total: modules.length,
    active: modules.filter((m) => m.status === 0).length,
    entities: modules.reduce((sum, m) => sum + (m.entityCount || 0), 0),
    fields: modules.reduce((sum, m) => sum + (m.fieldCount || 0), 0),
  };

  const handleCreate = async () => {
    if (!appId) return;
    const values = await form.validateFields();
    try {
      const res = await moduleApi.create(appId, values);
      if (res.code === 200) {
        message.success('模块创建成功');
        setModalOpen(false);
        form.resetFields();
        loadData();
      }
    } catch {
      message.error('创建失败');
    }
  };

  return (
    <div className="page">
      <Breadcrumb style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/apps')}>应用管理</a> },
          { title: appName },
        ]}
      />

      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>{appName}</Title>
          <Text type="secondary">选择模块进入领域建模</Text>
        </Col>
        <Col>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/apps')}>返回</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新建模块</Button>
          </Space>
        </Col>
      </Row>

      {modules.length > 0 && (
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={6}><Card size="small"><Statistic title="模块总数" value={stats.total} prefix={<FolderOutlined />} /></Card></Col>
          <Col span={6}><Card size="small"><Statistic title="启用中" value={stats.active} valueStyle={{ color: '#3f8600' }} /></Card></Col>
          <Col span={6}><Card size="small"><Statistic title="实体数" value={stats.entities} prefix={<DatabaseOutlined />} /></Card></Col>
          <Col span={6}><Card size="small"><Statistic title="字段数" value={stats.fields} /></Card></Col>
        </Row>
      )}

      {modules.length === 0 && !loading ? (
        <Empty description="暂无模块，请先创建" />
      ) : (
        <Row gutter={[16, 16]}>
          {modules.map((mod) => (
            <Col xs={24} sm={12} lg={8} key={mod.id}>
              <Card
                hoverable
                loading={loading}
                actions={[
                  <Button type="link" size="small" icon={<DatabaseOutlined />}
                    onClick={() => navigate(`/apps/${appId}/modules/${mod.id}/entities`)}>
                    领域建模
                  </Button>,
                  <Button type="link" size="small" icon={<LinkOutlined />}
                    onClick={() => navigate(`/apps/${appId}/modules/${mod.id}/relations`)}>
                    关系管理
                  </Button>,
                ]}
              >
                <Card.Meta
                  avatar={<FolderOutlined style={{ fontSize: 28, color: '#1890ff' }} />}
                  title={
                    <Space>
                      {mod.name}
                      <Tag color={STATUS_COLORS[mod.status] || 'default'}>{STATUS_LABELS[mod.status] || mod.status}</Tag>
                    </Space>
                  }
                  description={
                    <>
                      <Text type="secondary" style={{ fontSize: 12 }}>编码：{mod.code}</Text>
                      <Paragraph type="secondary" ellipsis style={{ margin: '4px 0' }}>{mod.description}</Paragraph>
                      {mod.entityCount > 0 && (
                        <Space split={<Text type="secondary">|</Text>}>
                          <Text style={{ fontSize: 12 }}>{mod.entityCount} 实体</Text>
                          <Text style={{ fontSize: 12 }}>{mod.fieldCount} 字段</Text>
                        </Space>
                      )}
                    </>
                  }
                />
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Modal title="新建模块" open={modalOpen} onOk={handleCreate} onCancel={() => { setModalOpen(false); form.resetFields(); }} destroyOnHidden>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="模块名称" rules={[{ required: true }]}>
            <Input placeholder="如：订单核心" />
          </Form.Item>
          <Form.Item name="code" label="模块编码" rules={[{ required: true }]} tooltip="应用内唯一">
            <Input placeholder="如：ORDER" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ModuleManagement;
