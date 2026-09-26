import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Button, Card, Col, Row,
  Space, Statistic, Tag, Typography, message,
} from 'antd';
import {
  ArrowRightOutlined, ClockCircleOutlined, PlusOutlined, SolutionOutlined,
  AppstoreOutlined, BlockOutlined,
} from '@ant-design/icons';
import { appApi, type BoneApplication } from '../services/appModuleApi';
import { metadataEntityApi } from '../services/metadataApi';
import { listRecent, recentPath, type RecentEntityItem } from '../utils/recent';

const { Title, Text, Paragraph } = Typography;

const PERM_COLORS: Record<string, string> = { admin: 'red', developer: 'blue', viewer: 'green' };
const PERM_LABELS: Record<string, string> = { admin: '管理员', developer: '开发者', viewer: '只读' };

/**
 * 应用 icon 字段存的是图标名字符串（如 AccountBookOutlined），直接渲染会显示成文字；
 * 这里映射为 emoji 兜底展示（IAM 侧维护真正的图标库归 IAM 应用管理，不在本页扩展）。
 */
function appEmoji(icon?: string): string {
  if (!icon) return '📱';
  const key = icon.replace(/[^a-zA-Z]/g, '').toLowerCase();
  if (key.includes('accountbook') || key.includes('finance') || key.includes('money')) return '📒';
  if (key.includes('user') || key.includes('team')) return '👥';
  if (key.includes('shopping') || key.includes('cart')) return '🛒';
  if (key.includes('truck') || key.includes('car')) return '🚚';
  if (key.includes('setting') || key.includes('tool')) return '⚙️';
  if (key.includes('cloud')) return '☁️';
  if (key.includes('chart')) return '📊';
  if (key.includes('appstore') || key.includes('app')) return '🧩';
  return /^\p{Extended_Pictographic}$/u.test(icon) ? icon : '📱';
}

/**
 * 建模工作台（消费侧入口）—— 任务导向首页（2b F1/UC-W1）。
 *
 * 领域边界：应用(App)聚合的管理（新建/编辑/删除/成员授权）归 IAM —— 见
 * bone-iam-app `ApplicationManagement` 页面（shell 菜单「IAM 管理 → 应用管理」）。
 * 本页面按「用户要办的事」组织：继续建模（最近编辑）→ 从模板新建 → 全新建模 → 应用列表。
 */
const ModelingWorkspace: React.FC = () => {
  const navigate = useNavigate();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [draftTotal, setDraftTotal] = useState<number | null>(null);
  const [recent, setRecent] = useState<RecentEntityItem[]>([]);

  const loadApps = async () => {
    setLoading(true);
    try {
      const res = await appApi.listMine({ page: 1, size: 50 });
      if (res.code === 200) {
        setApps(res.data.list);
      }
    } catch {
      message.error('加载应用列表失败');
    } finally {
      setLoading(false);
    }
  };

  const loadDraftTotal = async () => {
    try {
      const res = await metadataEntityApi.page({ pageNum: 1, pageSize: 1, status: 0 });
      if (res.code === 200) setDraftTotal(res.data.total);
    } catch {
      // 统计条为增强信息，失败静默
    }
  };

  useEffect(() => {
    loadApps();
    loadDraftTotal();
    setRecent(listRecent().slice(0, 5));
  }, []);

  const totalEntities = useMemo(
    () => apps.reduce((sum, a) => sum + (a.entityCount ?? 0), 0),
    [apps],
  );

  return (
    <div className="page">
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>建模工作台</Title>
          <Text type="secondary">从建模到发布：选择基线（模板 / 复制 / 全新）→ 定义实体与字段 → 校验 → 发布；应用的创建与维护请前往 IAM 管理 → 应用管理</Text>
        </Col>
        <Col>
          <Space>
            <Button icon={<SolutionOutlined />} onClick={() => navigate('/template-wizard')}>
              从模板新建
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={apps.length === 0 && !loading}
              onClick={() => (apps.length > 0 ? navigate(`/apps/${apps[0].id}/modules`) : undefined)}
            >
              全新建模
            </Button>
          </Space>
        </Col>
      </Row>

      {/* 概览条 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={8} md={6}><Card size="small"><Statistic title="我的应用" value={apps.length} /></Card></Col>
        <Col xs={8} md={6}>
          <Card size="small">
            <Statistic
              title="实体总数"
              value={totalEntities}
              loading={loading}
            />
          </Card>
        </Col>
        <Col xs={8} md={6}>
          <Card size="small">
            <Statistic
              title="草稿（待发布）"
              value={draftTotal ?? 0}
              loading={draftTotal === null}
              valueStyle={draftTotal ? { color: '#fa8c16' } : undefined}
            />
          </Card>
        </Col>
        <Col xs={8} md={6}>
          <Card size="small">
            <Statistic
              title="可建模应用"
              value={apps.filter((a) => a.myRole !== 'viewer').length}
              loading={loading}
            />
          </Card>
        </Col>
      </Row>

      {/* 继续建模：最近编辑实体 Top5（localStorage） */}
      {recent.length > 0 && (
        <Card
          size="small"
          title={<Space><ClockCircleOutlined /><span>继续建模（最近编辑）</span></Space>}
          style={{ marginBottom: 16 }}
        >
          <Space wrap>
            {recent.map((r) => (
              <Button
                key={r.id}
                size="small"
                onClick={() => navigate(recentPath(r))}
              >
                {r.displayName || r.code} <Text code style={{ fontSize: 12 }}>{r.code}</Text>
              </Button>
            ))}
          </Space>
        </Card>
      )}

      {apps.length === 0 && !loading ? (
        <Alert type="info" message="暂无可建模应用，请先在「IAM 管理 → 应用管理」创建" />
      ) : (
        <>
          <Row style={{ marginBottom: 8 }}>
            <Col><Space><AppstoreOutlined /><Text strong>我的应用</Text><Text type="secondary">点击「进入建模」按「模块 → 实体 → 字段」继续</Text></Space></Col>
          </Row>
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
                      进入建模 <ArrowRightOutlined />
                    </Button>,
                  ]}
                >
                  <Card.Meta
                    avatar={<span style={{ fontSize: 36 }}>{appEmoji(app.icon)}</span>}
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
                            <BlockOutlined /> {app.moduleCount} 模块 · {app.entityCount} 实体
                          </Text>
                        </div>
                      </>
                    }
                  />
                </Card>
              </Col>
            ))}
          </Row>
        </>
      )}
    </div>
  );
};

export default ModelingWorkspace;
