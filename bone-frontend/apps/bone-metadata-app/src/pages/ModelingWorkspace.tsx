import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Button, Card, Col, Collapse, Modal, Row,
  Select, Space, Tag, Typography, message,
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
 * 建模工作台 —— **元数据域的唯一入口**，任务导向首页（2b F1/UC-W1）。
 *
 * ── 三个菜单的职责边界（本页 / 「实体」/ 「运行时数据」）──────────────
 *「你在这里提问的是『从零开始建一个模型吗』，不是『我有哪些模型』」：
 *   · 本页（`/apps`）      = **入口与待办**：怎么开始建模（3 个 CTA）+ 有多少草稿待发布 + 最近编辑。
 *                不做实体清单 —— 清单真源在「实体」页（有搜索/筛选/批量发布）。
 *   · 实体   （`/entities`）= **实体的唯一权威清单**：搜索、筛选、批量发布、复制、导出、删除。
 *   · 运行时数据（`/runtime`）= **跨实体的数据运营台**：录入/编辑物理表记录。
 *                            单个实体的数据请走「实体详情 → 数据 Tab」（带上下文，不会看错实体）。
 * 三者不是三个并列的「同类列表」，而是 **建模入口 → 模型管理 → 数据运营** 的一条动线。
 *
 * 对标依据：NocoBase「集合页即入口」（列表单真源）、Salesforce Setup「对象」页
 * （工作台不重复列对象，只给待办与快捷动作）。
 *
 * 领域边界：应用(App)聚合的管理（新建/编辑/删除/成员授权）归 IAM —— 见
 * bone-iam-app `ApplicationManagement` 页面（shell 菜单「权限与角色 → 应用管理」）。
 *
 * 「全新建模 / 从存量表导入」不跳模块列表（不再硬取 apps[0]），改为选应用+模块后直达
 * 实体列表并自动开对话框（?new=1 / ?import=1，由 EntityManagement 消费）。
 */
const ModelingWorkspace: React.FC = () => {
  const navigate = useNavigate();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [draftTotal, setDraftTotal] = useState<number | null>(null);
  /** 实体总数（元数据分页 total；不用 sum(app.entityCount)，后者恒为 0） */
  const [entityTotal, setEntityTotal] = useState<number | null>(null);
  const [recent, setRecent] = useState<RecentEntityItem[]>([]);
  /** 新建/导入落点选择：「选应用+模块 → 直达实体列表并自动开对话框」 */
  const [targetOpen, setTargetOpen] = useState(false);
  const [targetIntent, setTargetIntent] = useState<'new' | 'import'>('new');
  const [targetAppId, setTargetAppId] = useState<string | undefined>(undefined);
  const [targetModuleId, setTargetModuleId] = useState<string | undefined>(undefined);
  /** 待发布实体前 6 条（仅作待办提示，完整清单在 /entities） */
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
      if (res.code === 200) {
        setDraftTotal(normalizeTotal(res.data.total));
        // 实体总数取**元数据侧**分页 total，不取 sum(app.entityCount)：
        // 后者实测恒为 0（IAM 的 app 响应不带 entityCount），会让首屏显示
        // 「实体共 0」而实际有 71 个——一个比不显示更糟的错数字。
        const all = await metadataEntityApi.page({ page: 1, size: 1 });
        if (all.code === 200) setEntityTotal(normalizeTotal(all.data.total));
      }
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

  /**
   * 待发布实体（**不是**实体清单）。
   *
   * 此前此处是一张「我的实体」网格（前 20 条 + 草稿/已发布切换），与左侧菜单「实体」
   * 是**同一份数据的第二个残缺版**（无搜索、无分页、无批量操作），用户点两处得到两个
   * 不同结果 ⇒ 不知道该在哪管理实体。对标 Salesforce Setup / NocoBase「集合页即入口」：
   * 列表只应有一个真源。工作台退回到它该有的角色 —— **待办入口**：把「有多少草稿待发布」
   * 这个跨应用聚合数字告诉用户，并一键跳到实体列表的对应筛选态。
   */
  useEffect(() => {
    let cancelled = false;
    setEntitiesLoading(true);
    metadataEntityApi.page({ page: 1, size: 6, status: 0 })
      .then((res) => {
        if (!cancelled) setMyEntities(res.code === 200 ? res.data.records ?? [] : []);
      })
      .catch(() => { /* 待办增强信息，失败静默 */ })
      .finally(() => { if (!cancelled) setEntitiesLoading(false); });
    return () => { cancelled = true; };
  }, []);

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

  return (
    <div className="page">
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>
            建模工作台
            <Tag color="blue" style={{ marginLeft: 8, fontSize: 12 }}>元数据建模入口</Tag>
          </Title>
          <Text type="secondary">
            从零开始建一个模型：选基线（模板 / 空白 / 存量表）→ 定义实体与字段 → 校验 → 发布。
            已有模型请到左侧菜单「实体」检索与管理；录入业务数据请到「运行时数据」或实体详情的「数据」页签。
          </Text>
        </Col>
        <Col>
          <Text type="secondary">
            实体共 {entityTotal === null ? '…' : entityTotal} · 待发布 {draftTotal === null ? '…' : draftTotal}
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

      {/* 待发布实体（待办入口，非实体清单 —— 清单真源在左侧菜单「实体」） */}
      <Card
        size="small"
        title={(
          <Space>
            <span>待发布实体</span>
            {draftTotal != null && (
              <Tag color={draftTotal > 0 ? 'blue' : 'green'}>{draftTotal} 个草稿待发布</Tag>
            )}
          </Space>
        )}
        extra={(
          <Button type="link" size="small" onClick={() => navigate('/entities?status=0')}>
            查看全部实体 <ArrowRightOutlined />
          </Button>
        )}
        style={{ marginBottom: 16 }}
      >
        {myEntities.length === 0 && !entitiesLoading ? (
          <Text type="secondary">没有待发布的草稿；用上方「从场景模板开始 / 空白建模 / 从存量表导入」创建。实体清单与批量操作见左侧菜单「实体」。</Text>
        ) : (
          <>
            <Paragraph type="secondary" style={{ marginBottom: 8, fontSize: 12 }}>
              草稿尚未生成物理表，发布后才能录入数据。完整列表（搜索 / 筛选 / 批量发布）见左侧菜单「实体」。
            </Paragraph>
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
                          草稿 · {e.deliveryMode === 0 ? '手写界面' : '自动生成界面'} · {e.tableName}
                        </Text>
                      )}
                    />
                  </Card>
                </Col>
              ))}
            </Row>
          </>
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
