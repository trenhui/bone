import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Button, Card, Col, Row,
  Space, Tag, Typography, message,
} from 'antd';
import { appApi, type BoneApplication } from '../services/appModuleApi';

const { Title, Text, Paragraph } = Typography;

const PERM_COLORS: Record<string, string> = { admin: 'red', developer: 'blue', viewer: 'green' };
const PERM_LABELS: Record<string, string> = { admin: '管理员', developer: '开发者', viewer: '只读' };

/**
 * 建模工作台（消费侧入口）。
 *
 * 领域边界：应用(App)聚合的管理（新建/编辑/删除/成员授权）归 IAM —— 见
 * bone-iam-app `ApplicationManagement` 页面（shell 菜单「IAM 管理 → 应用管理」）。
 * 本页面是元数据建模的消费侧入口：只读列出当前用户可建模的应用，点击进入
 * 「模块 → 实体 → 字段」建模链路。
 */
const ModelingWorkspace: React.FC = () => {
  const navigate = useNavigate();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [loading, setLoading] = useState(true);

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

  return (
    <div className="page">
      <Row justify="space-between" align="middle" style={{ marginBottom: 24 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>建模工作台</Title>
          <Text type="secondary">选择要建模的应用进入「模块 → 实体 → 字段」；应用的创建与维护请前往 IAM 管理 → 应用管理</Text>
        </Col>
      </Row>

      {apps.length === 0 && !loading ? (
        <Alert type="info" message="暂无可建模应用，请先在「IAM 管理 → 应用管理」创建" />
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
    </div>
  );
};

export default ModelingWorkspace;
