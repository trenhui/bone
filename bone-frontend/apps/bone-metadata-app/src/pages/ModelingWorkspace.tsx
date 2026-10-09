import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Button, Card, Col, Collapse, Modal, Row,
  Select, Segmented, Space, Tag, Typography, message,
} from 'antd';
import {
  ArrowRightOutlined, ClockCircleOutlined, DatabaseOutlined, PlusOutlined, SolutionOutlined,
  AppstoreOutlined, BlockOutlined,
} from '@ant-design/icons';
import { appApi, moduleApi, type BoneApplication, type BoneModule } from '../services/appModuleApi';
import { metadataEntityApi } from '../services/metadataApi';
import { normalizeTotal } from '@bone/shared-utils';
import { listRecent, recentPath, type RecentEntityItem } from '../utils/recent';
import type { MetaEntity } from '../types';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';

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
 *
 * 布局定案（融合方案 §3.3.3，对齐 NocoBase「集合页即入口」）：首屏 = 主 CTA 区
 * （从场景模板开始 / 空白建模 / 从存量表导入）→ 最近编辑 → 我的实体（DRAFT/PUBLISHED
 * 展示切换）→ 应用折叠区（低频）。统计降级为一行小字；「全新建模 / 从存量表导入」
 * 不再跳模块列表（不再硬取 apps[0]），改为选应用+模块后直达实体列表并自动开对话框
 * （?new=1 / ?import=1，由 EntityManagement 消费）。
 */
const ModelingWorkspace: React.FC = () => {
  const navigate = useNavigate();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [draftTotal, setDraftTotal] = useState<number | null>(null);
  const [recent, setRecent] = useState<RecentEntityItem[]>([]);
  /** 新建/导入落点选择：「选应用+模块 → 直达实体列表并自动开对话框」 */
  const [targetOpen, setTargetOpen] = useState(false);
  const [targetIntent, setTargetIntent] = useState<'new' | 'import'>('new');
  const [targetAppId, setTargetAppId] = useState<string | undefined>(undefined);
  const [targetModuleId, setTargetModuleId] = useState<string | undefined>(undefined);
  /** 我的实体（取代「我的应用」网格成为主路径）：status 切换 + 分应用加载 */
  const [entityView, setEntityView] = useState<'draft' | 'published'>('draft');
  const [myEntities, setMyEntities] = useState<MetaEntity[]>([]);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  const [appModules, setAppModules] = useState<{ appId: string; appName: string; modules: BoneModule[] }[]>([]);

  const writableApps = useMemo(() => apps.filter((a) => a.myRole !== 'viewer'), [apps]);
  const moduleOptions = useMemo(() => {
    if (!targetAppId) return [];
    return appModules.find((m) => m.appId === targetAppId)?.modules ?? [];
  }, [appModules, targetAppId]);

  /** 实体行点击落点：moduleId 经 appModules 反查 appId，带源上下文回详情（与 recentPath 同规则） */
  const appIdByModule = useMemo(() => {
    const map = new Map<string, string>();
    for (const m of appModules) for (const mod of m.modules) map.set(String(mod.id), String(m.appId));
    return map;
  }, [appModules]);
  const entityPath = (e: MetaEntity): string => {
    const appId = e.moduleId ? appIdByModule.get(String(e.moduleId)) : undefined;
    return appId && e.moduleId
      ? `/apps/${appId}/modules/${e.moduleId}/entities/${e.id}`
      : `/entities/${e.id}`;
  };

  const loadApps = async () => {
    setLoading(true);
    try {
      const res = await appApi.listMine({ page: 1, size: 50 });
      if (res.code === 200) {
        setApps(res.data.records);
      }
    } catch {
      message.error('加载应用列表失败');
    } finally {
      setLoading(false);
    }
  };

  const loadDraftTotal = async () => {
    try {
      const res = await metadataEntityApi.page({ page: 1, size: 1, status: 0 });
      if (res.code === 200) setDraftTotal(normalizeTotal(res.data.total));
    } catch {
      // 统计条为增强信息，失败静默
    }
  };

  /** 应用→模块两级结构：目标选择器的模块下拉 + 实体行反查 appId 共用 */
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await appApi.listMine({ page: 1, size: 100 });
        const list = res.code === 200 ? res.data.records ?? [] : [];
        const pairs = await Promise.all(
          list.map(async (a) => {
            try {
              const mr = await moduleApi.listByApp(a.id, { page: 1, size: 100 });
              return { appId: a.id, appName: a.name, modules: mr.code === 200 ? mr.data.records ?? [] : [] };
            } catch {
              return { appId: a.id, appName: a.name, modules: [] as BoneModule[] };
            }
          }),
        );
        if (!cancelled) setAppModules(pairs);
      } catch {
        /* 模块结构仅增强导航，失败静默 */
      }
    })();
    return () => { cancelled = true; };
  }, []);

  /** 我的实体（主路径网格）：按展示状态加载前 20 条 */
  useEffect(() => {
    let cancelled = false;
    setEntitiesLoading(true);
    metadataEntityApi.page({ page: 1, size: 20, status: entityView === 'draft' ? 0 : 1 })
      .then((res) => {
        if (!cancelled) setMyEntities(res.code === 200 ? res.data.records ?? [] : []);
      })
      .catch(() => { /* 网格增强信息，失败静默 */ })
      .finally(() => { if (!cancelled) setEntitiesLoading(false); });
    return () => { cancelled = true; };
  }, [entityView]);

  useEffect(() => {
    loadApps();
    loadDraftTotal();
    setRecent(listRecent().slice(0, 5));
  }, []);

  const openTargetPicker = (intent: 'new' | 'import') => {
    setTargetIntent(intent);
    setTargetModuleId(undefined);
    setTargetAppId(writableApps.length === 1 ? writableApps[0].id : undefined);
    setTargetOpen(true);
  };

  const confirmTarget = () => {
    if (!targetAppId || !targetModuleId) return;
    setTargetOpen(false);
    navigate(
      `/apps/${targetAppId}/modules/${targetModuleId}/entities?${targetIntent === 'new' ? 'new=1' : 'import=1'}`,
    );
  };

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
          <Text type="secondary">
            实体共 {loading ? '…' : totalEntities} · 待发布 {draftTotal === null ? '…' : draftTotal}
          </Text>
        </Col>
      </Row>

      {/* 主 CTA 区（首屏视觉最重；融合方案 §3.3.3） */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap size="middle">
          <AuthButton
            code={BonePermissionCodes.METADATA_TEMPLATE_WRITE}
            type="primary"
            icon={<SolutionOutlined />}
            onClick={() => navigate('/template-wizard')}
          >
            从场景模板开始
          </AuthButton>
          <AuthButton
            code={BonePermissionCodes.METADATA_MODEL_WRITE}
            icon={<PlusOutlined />}
            disabled={writableApps.length === 0 && !loading}
            onClick={() => openTargetPicker('new')}
          >
            空白建模
          </AuthButton>
          <AuthButton
            code={BonePermissionCodes.METADATA_MODEL_WRITE}
            icon={<DatabaseOutlined />}
            disabled={writableApps.length === 0 && !loading}
            onClick={() => openTargetPicker('import')}
          >
            从存量表导入
          </AuthButton>
          <Text type="secondary" style={{ fontSize: 12 }}>
            场景模板 6–9 字段开箱可用；空白建模默认运行时交付
          </Text>
        </Space>
      </Card>

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

      {/* 我的实体（主路径网格，取代「我的应用」首屏；§3.3.3） */}
      <Card
        size="small"
        title={(
          <Space>
            <span>我的实体</span>
            <Segmented
              size="small"
              value={entityView}
              onChange={(v) => setEntityView(v as 'draft' | 'published')}
              options={[{ label: '草稿', value: 'draft' }, { label: '已发布', value: 'published' }]}
            />
          </Space>
        )}
        style={{ marginBottom: 16 }}
      >
        {myEntities.length === 0 && !entitiesLoading ? (
          <Text type="secondary">暂无{entityView === 'draft' ? '草稿' : '已发布'}实体；用上方「从场景模板开始 / 空白建模」创建</Text>
        ) : (
          <Row gutter={[12, 12]}>
            {myEntities.map((e) => (
              <Col xs={24} sm={12} lg={8} xl={6} key={e.id}>
                <Card
                  size="small"
                  hoverable
                  loading={entitiesLoading}
                  onClick={() => navigate(entityPath(e))}
                >
                  <Card.Meta
                    title={<Space size={4}>{e.displayName} <Text code style={{ fontSize: 12 }}>{e.code}</Text></Space>}
                    description={(
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {entityView === 'draft' ? '草稿' : '已发布'}{e.deliveryMode === 0 ? ' · 生成式' : ' · 运行时'} · {e.tableName}
                      </Text>
                    )}
                  />
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Card>

      {/* 应用网格收进折叠区（低频；模块/实体主路径由上方 CTA 与实体网格承接） */}
      {apps.length === 0 && !loading ? (
        <Alert type="info" message="暂无可建模应用，请先在「IAM 管理 → 应用管理」创建" />
      ) : (
        <Collapse
          size="small"
          items={[{
            key: 'apps',
            label: <Space><AppstoreOutlined /><span>我的应用</span><Text type="secondary">应用/模块管理属低频操作，实体主路径见上方</Text></Space>,
            children: (
              <Row gutter={[16, 16]}>
                {apps.map((app) => (
                  <Col xs={24} sm={12} lg={8} xl={6} key={app.id}>
                    <Card
                      hoverable
                      loading={loading}
                      actions={[
                        <Button key="enter" type="link" size="small" onClick={() => {
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
            ),
          }]}
        />
      )}

      {/* 新建/导入落点选择：应用+模块 → 直达实体列表并自动开对话框 */}
      <Modal
        open={targetOpen}
        title={targetIntent === 'new' ? '选择新建位置' : '选择导入位置'}
        okText="进入"
        okButtonProps={{ disabled: !targetAppId || !targetModuleId }}
        onOk={confirmTarget}
        onCancel={() => setTargetOpen(false)}
        width={480}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>应用（仅可建模）</Text>
            <Select
              style={{ width: '100%' }}
              placeholder="选择应用"
              value={targetAppId}
              onChange={(v) => { setTargetAppId(v); setTargetModuleId(undefined); }}
              options={writableApps.map((a) => ({ value: a.id, label: a.name }))}
            />
          </div>
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>模块</Text>
            <Select
              style={{ width: '100%' }}
              placeholder={targetAppId ? '选择模块' : '先选应用'}
              disabled={!targetAppId}
              value={targetModuleId}
              onChange={setTargetModuleId}
              options={moduleOptions.map((m) => ({ value: m.id, label: m.name }))}
            />
          </div>
        </Space>
      </Modal>
    </div>
  );
};

export default ModelingWorkspace;
