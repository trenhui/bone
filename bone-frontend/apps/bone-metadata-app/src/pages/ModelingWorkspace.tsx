import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button, Card, Col, Collapse, Empty, Form, Input, Modal, Row, Skeleton, Statistic, Steps,
  Select, Space, Tag, Tooltip, Typography, message,
} from 'antd';
import {
  ArrowRightOutlined, ClockCircleOutlined, DatabaseOutlined, PlusOutlined, SolutionOutlined,
  AppstoreOutlined, BlockOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import { appApi, moduleApi, type BoneApplication } from '../services/appModuleApi';
import { metadataWorkspaceApi } from '../services/metadataApi';
import { listRecent, recentPath, type RecentEntityItem } from '../utils/recent';
import PublishPreviewModal from '../components/PublishPreviewModal';
import CommandPalette from '../components/CommandPalette';
import type { MetaEntity, WorkspaceAppModules } from '../types';
const { Title, Text } = Typography;

/** 标识符白名单（与后端 validate 规则一致：字母开头，仅字母/数字/下划线） */
const CODE_RULE = { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '以字母开头，仅字母/数字/下划线' };

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
 * 模型设计 —— **元数据域的唯一创建入口**，任务导向首页（2b F1/UC-W1；命名 IA 定稿 2026-10-11）。
 *
 * ── 三个菜单的职责边界（本页 / 「业务对象」/ 「业务数据」）────────────
 *「你在这里提问的是『从零开始建一个模型吗』，不是『我有哪些模型』」：
 *   · 本页（`/apps`）      = **入口与待办**：怎么开始建模（3 个 CTA）+ 有多少草稿待发布 + 最近编辑。
 *                不做对象清单 —— 清单真源在「业务对象」页（有搜索/筛选/批量发布）。
 *   · 业务对象（`/entities`）= **对象的唯一权威清单**：搜索、筛选、批量发布、复制、导出、删除。
 *   · 业务数据（`/runtime`）  = **跨对象的数据运营台**：录入/编辑物理表记录。
 *                            单个对象的数据请走「对象详情 → 数据 Tab」（带上下文，不会看错对象）。
 * 三者不是三个并列的「同类列表」，而是 **设计入口 → 对象管理 → 数据运营** 的一条动线。
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
/**
 * 快速开始磁贴（对标 飞书/钉钉「快速创建」、NocoBase 首页卡片）：
 * 用「大图标 + 标题 + 一句话说明」替代一排文字按钮，让用户在一屏内扫一眼就分清
 * 三种建模入口的差别，降低「我该点哪个」的决策成本（业界低代码平台的通用首页范式）。
 */
const QuickStartTile: React.FC<{
  icon: React.ReactNode;
  title: string;
  desc: string;
  onClick: () => void;
  disabled?: boolean;
  /** 推荐标记（对标 mock-best-scheme 的 rec 磁贴）：首次用户走最快路径的引导，而非又一个装饰 */
  badge?: string;
  /** 推荐副文案（rec-sub）：说明为什么推荐，避免「推荐」变成无意义的红点 */
  subHint?: string;
}> = ({ icon, title, desc, onClick, disabled, badge, subHint }) => (
  <div
    role="button"
    tabIndex={disabled ? -1 : 0}
    aria-disabled={disabled}
    onClick={disabled ? undefined : onClick}
    onKeyDown={(e) => {
      if (!disabled && (e.key === 'Enter' || e.key === ' ')) {
        e.preventDefault();
        onClick();
      }
    }}
    style={{
      flex: '1 1 220px',
      minWidth: 220,
      padding: '16px 18px',
      borderRadius: 10,
      border: '1px solid #e5e7eb',
      background: '#fff',
      cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.55 : 1,
      transition: 'border-color .2s, box-shadow .2s',
      display: 'flex',
      gap: 14,
      alignItems: 'flex-start',
      position: 'relative',
    }}
    onMouseEnter={(e) => { if (!disabled) e.currentTarget.style.borderColor = '#1677ff'; }}
    onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#e5e7eb'; }}
  >
    {badge && (
      <span
        style={{
          position: 'absolute', top: -9, right: 12, background: '#fa8c16', color: '#fff',
          fontSize: 10, lineHeight: '16px', padding: '0 7px', borderRadius: 8, fontWeight: 500,
        }}
      >
        {badge}
      </span>
    )}
    <div style={{ fontSize: 26, color: '#1677ff', lineHeight: 1.1 }}>{icon}</div>
    <div style={{ flex: 1 }}>
      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 2 }}>
        {title}
        {badge && <Tag color="orange" style={{ marginLeft: 6, fontSize: 10, lineHeight: '16px', marginInlineEnd: 0 }}>{badge}</Tag>}
      </div>
      <div style={{ fontSize: 12, color: '#8c8c8c', lineHeight: 1.5 }}>{desc}</div>
      {/* 有 badge 时是「推荐理由」（橙色，行动引导）；无 badge 时回落中性灰，避免
          没有推荐标记却渲染出一句意为「推荐」的橙字 —— 语气必须与角标一致。 */}
      {subHint && (
        <div style={{ fontSize: 11, color: badge ? '#fa8c16' : '#8c8c8c', marginTop: 4 }}>{subHint}</div>
      )}
    </div>
  </div>
);

const ModelingWorkspace: React.FC = () => {
  const navigate = useNavigate();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [draftTotal, setDraftTotal] = useState<number | null>(null);
  /** 实体总数（元数据分页 total；不用 sum(app.entityCount)，后者恒为 0） */
  const [entityTotal, setEntityTotal] = useState<number | null>(null);
  /** 已发布实体数（概览指标条用，独立于筛选口径） */
  const [publishedTotal, setPublishedTotal] = useState<number | null>(null);
  const [recent, setRecent] = useState<RecentEntityItem[]>([]);
  /** 新建/导入落点选择：「选应用+模块 → 直达实体列表并自动开对话框」 */
  const [targetOpen, setTargetOpen] = useState(false);
  const [targetIntent, setTargetIntent] = useState<'new' | 'import'>('new');
  const [targetAppId, setTargetAppId] = useState<string | undefined>(undefined);
  const [targetModuleId, setTargetModuleId] = useState<string | undefined>(undefined);
  /** 待发布实体前 6 条（仅作待办提示，完整清单在 /entities） */
  const [myEntities, setMyEntities] = useState<MetaEntity[]>([]);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  /**
   * 待办就地发布：草稿的**下一步动作**就是发布，此前只能「去设计」再翻到发布页签，
   * 一次待办要跨两页两步。接 PublishPreviewModal 后，草稿行可直接发布，
   * 且发布成功给出「打开数据 / 继续加字段」的前向引导（动线闭环，与业务对象页同一条路径）。
   */
  const [publishTarget, setPublishTarget] = useState<MetaEntity | null>(null);
  /**
   * 应用→模块两级结构（P0-1：改由聚合端点一次返回，替代此前 listMine + N×listByApp）。
   * 仅承载目标选择器下拉与 moduleId→appId 反查所需的最小集（id/name/code）。
   */
  const [appModules, setAppModules] = useState<WorkspaceAppModules[]>([]);
  /**
   * 内联容器创建（O2）：应用/模块的**管理**归 IAM，但建模流最常见的「还没有应用/模块」
   * 不该把用户踢到另一个微应用（他甚至未必有 IAM 菜单入口）。这里就地创建并自动选中，
   * 完整成员授权仍回 IAM —— 只内联「建容器」这一步，不越权代管授权。
   */
  const [creatingApp, setCreatingApp] = useState(false);
  const [creatingModule, setCreatingModule] = useState(false);
  const [appForm] = Form.useForm();
  const [moduleForm] = Form.useForm();

  /** 建应用/模块后刷新可写应用 + 应用→模块结构，并把新建项自动选中（免去二次手选） */
  const refreshAppsAndModules = async (selectAppId?: string, selectModuleId?: string) => {
    await loadApps();
    await loadWorkspace();
    if (selectAppId) setTargetAppId(selectAppId);
    if (selectModuleId) setTargetModuleId(selectModuleId);
  };

  const handleCreateApp = async () => {
    const v = await appForm.validateFields();
    try {
      const res = await appApi.create({ name: v.name, code: v.code, description: v.description });
      if (res.code === 200 || res.code === 201) {
        const newId = String(res.data ?? v.code);
        setCreatingApp(false);
        appForm.resetFields();
        message.success(`已创建应用「${v.name}」`);
        // 建模者即创建者：IAM 侧创建接口会授予创建者 admin 角色，这里刷新后即可建模
        await refreshAppsAndModules(newId);
      } else {
        message.error(res.message || '创建应用失败');
      }
    } catch {
      message.error('创建应用失败，请检查编码是否已存在');
    }
  };

  const handleCreateModule = async () => {
    if (!targetAppId) return;
    const v = await moduleForm.validateFields();
    try {
      const res = await moduleApi.create(targetAppId, { name: v.name, code: v.code, description: v.description });
      if (res.code === 200 || res.code === 201) {
        const newId = String(res.data ?? v.code);
        setCreatingModule(false);
        moduleForm.resetFields();
        message.success(`已创建模块「${v.name}」并自动选中`);
        await refreshAppsAndModules(targetAppId, newId);
      } else {
        message.error(res.message || '创建模块失败');
      }
    } catch {
      message.error('创建模块失败，请检查编码是否已存在');
    }
  };

  const writableApps = useMemo(() => apps.filter((a) => a.myRole !== 'viewer'), [apps]);
  /** 是否已有草稿：步骤引导条 current 据此推进（有草稿 = 已过「选起点」步） */
  const modelingHasDraft = (draftTotal ?? 0) > 0;
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

  /**
   * 建模工作台聚合加载（P0-1：一次请求拿全 统计 + 待发布草稿 + 应用→模块结构）。
   *
   * 改造前这里是 4 条独立加载路径：listMine×2 + listByApp×N + entities page×3（串行），
   * 首屏 RTT 随应用数线性增长，且三次 size=1 分页只为凑 total。现由后端
   * GET /api/v1/metadata/modeling/workspace 一次返回（统计口径与业务对象页同源）。
   *
   * 降级策略与改造前逐加载器 catch 的行为一致：聚合失败时统计留空（null）、
   * 待办与模块结构留空——页面主体（应用 rail / 空态引导）不依赖本调用，仍可用。
   */
  const loadWorkspace = useCallback(async () => {
    setEntitiesLoading(true);
    try {
      const res = await metadataWorkspaceApi.workspace();
      if (res.code === 200) {
        const d = res.data;
        setDraftTotal(d.draftTotal ?? null);
        setEntityTotal(d.entityTotal ?? null);
        setPublishedTotal(d.publishedTotal ?? null);
        setMyEntities(d.recentDrafts ?? []);
        setAppModules(d.appModules ?? []);
      }
    } catch {
      // 统计条/待办/模块结构均为增强信息，失败静默（与改造前各加载器一致）
    } finally {
      setEntitiesLoading(false);
    }
  }, []);

  /**
   * 待发布草稿列表（前 6 条待办，**不是**实体清单 —— 完整清单真源在「业务对象」页）。
   *
   * 此前此处是一张「我的实体」网格（前 20 条 + 草稿/已发布切换），与左侧菜单「业务对象」
   * 是**同一份数据的第二个残缺版**（无搜索、无分页、无批量操作），用户点两处得到两个
   * 不同结果 ⇒ 不知道该在哪管理实体。对标 Salesforce Setup / NocoBase「集合页即入口」：
   * 列表只应有一个真源。工作台退回到它该有的角色 —— **待办入口**：把「有多少草稿待发布」
   * 这个跨应用聚合数字告诉用户，并一键跳到实体列表的对应筛选态。
   *
   * P0-1 起数据来自聚合端点 {@link loadWorkspace}（统计 + 草稿 + 模块结构一次返回）；
   * 「就地发布」成功后重调 loadWorkspace（该条已不再是草稿）。
   */

  useEffect(() => {
    loadApps();
    loadWorkspace().catch(() => { /* 增强信息，失败静默 */ });
    setRecent(listRecent().slice(0, 5));
    // loadApps 为组件内定义的一次性加载器，故意不入依赖（避免无限触发）
  }, [loadWorkspace]);

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
      <CommandPalette />
      {/* 页头：只留标题与一句话职责，不再堆说明段落 */}
      <div style={{ marginBottom: 12 }}>
        <Space size={8} wrap>
          <Title level={4} style={{ margin: 0 }}>模型设计</Title>
          <Tag color="blue" style={{ fontSize: 12 }}>建模入口 · 从零建一个模型</Tag>
        </Space>
        <Text type="secondary">选基线（模板 / 空白 / 存量表）→ 定义对象与字段 → 校验 → 发布。</Text>
      </div>

      {/* 无可写应用：空状态替代被禁用的磁贴（O1）。
          此前此处是「按钮灰掉 + 一条让用户去 IAM 建应用的 Alert」——首次使用者在建模流
          遇到死路，还被要求理解另一个微应用。对标 NocoBase / 飞书宜搭：零容器时同页创建，
          创建后立刻回到主路径。

          ⚠ 判据是 `writableApps` 而非 `apps`：只被授予 viewer 角色的用户 apps 非空但
          全部不可写，此前会渲染出磁贴 → 点「空白建模」打开一个**没有任何选项**的
          应用下拉 → okButton 永久禁用，是比空状态更糟的死胡同。 */}
      {writableApps.length === 0 && !loading ? (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={(
              <Space direction="vertical" size={2}>
                <Text strong>{apps.length === 0 ? '还没有可建模的应用' : '没有可写入的应用'}</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {apps.length === 0
                    ? '应用是对象的归属容器。先创建一个应用（或模块），即可开始定义业务对象。'
                    : '你在现有应用中均为「只读（viewer）」角色，无法建模。请联系应用管理员授予 ADMIN / DEVELOPER 角色，或创建一个属于自己的应用。'}
                </Text>
              </Space>
            )}
          >
            <Space>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreatingApp(true)}>
                {apps.length === 0 ? '创建第一个应用' : '创建应用并开始建模'}
              </Button>
              <Button onClick={() => navigate('/entities')}>先浏览已有业务对象</Button>
            </Space>
            <div style={{ marginTop: 10 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                应用的成员授权与权限管理归 IAM「应用管理」，此处仅创建容器。
              </Text>
            </div>
          </Empty>
        </Card>
      ) : loading ? (
        /* 首屏加载：给骨架而非空白，避免磁贴闪现后又消失（布局抖动） */
        <Card size="small" style={{ marginBottom: 16 }}>
          <Skeleton active title={false} paragraph={{ rows: 4 }} />
        </Card>
      ) : (
        <>
          {/* 建模生命周期步骤引导条（综述 §1.1「步骤引导条（选起点→定义结构→发布）」）：
              入口页首屏是 Action-first 的磁贴，但首次用户仍会问「建模到底分几步、我现在在哪」。
              对标 Salesforce Setup / NocoBase onboarding：用一条轻量 Steps 把生命周期可视化，
              步骤可点击下钻到对应动作，current 随草稿数推进（有草稿 = 已过「选起点」）。
              不喧宾夺主：size=small、放在磁贴上方，纯引导不阻断主行动。 */}
          <Card size="small" style={{ marginBottom: 16 }} styles={{ body: { padding: '12px 16px' } }}>
            <Steps
              size="small"
              current={modelingHasDraft ? 1 : 0}
              items={[
                {
                  title: <a onClick={() => navigate('/template-wizard')}>选起点</a>,
                  description: '模板 / 空白 / 存量表',
                  status: modelingHasDraft ? 'finish' : 'process',
                },
                {
                  title: (
                    <a onClick={() => (myEntities.length > 0 ? navigate(entityPath(myEntities[0])) : navigate('/entities'))}>
                      定义对象与字段
                    </a>
                  ),
                  description: '在对象工作区补齐字段与界面',
                  status: modelingHasDraft ? 'process' : 'wait',
                },
                {
                  title: <a onClick={() => navigate('/entities?status=0')}>校验并发布</a>,
                  description: '发布后生成物理表，即可录入数据',
                  status: 'wait',
                },
              ]}
            />
          </Card>

          {/* 主行动区置顶（Action-first）：三种建模入口是第一视觉落点。
              对标飞书/钉钉「快速创建」、NocoBase 首页——入口页要先回答「我该点哪个」。
              ⚠ 落点选择器需要应用列表才能选位：apps 未就绪时必须禁用，
              否则用户点「空白建模」会打开一个空下拉的死胡同（实测首屏竞态）。 */}
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
            {/* 「推荐」标记已于模板级状态机产物落地后恢复（迁移 0040）：
                实例化现在同步写出 meta_entity_action_def ⇒ 入口状态被平台注入、状态流转可用，
                UI 实测「新建记录 201 + 状态流转成功」——此时「推荐」才是一条真能走通的路径。 */}
            <QuickStartTile
              icon={<SolutionOutlined />}
              title="从场景模板开始"
              desc="开箱即用的行业模型，实例化即可录入数据"
              badge="推荐"
              subHint="基于常见场景，最快出数据（含字段与界面）"
              onClick={() => navigate('/template-wizard')}
            />
            <QuickStartTile
              icon={<PlusOutlined />}
              title="空白建模"
              desc="从零定义对象与字段，发布后即可录数据"
              disabled={loading}
              onClick={() => openTargetPicker('new')}
            />
            <QuickStartTile
              icon={<DatabaseOutlined />}
              title="从存量表导入"
              desc="已有物理表反向生成模型草稿"
              disabled={loading}
              onClick={() => openTargetPicker('import')}
            />
          </div>

          {/* 双栏：左=待发布待办（主区），右=概览与快捷重入的 rail。
              对标 Salesforce Setup 右栏 rail：一屏零滚动掌握全部状态，避免 5 段纵向堆叠。 */}
          <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <Card
                size="small"
                title={(
                  <Space>
                    <span>待发布业务对象</span>
                    {draftTotal != null && (
                      <Tag color={draftTotal > 0 ? 'blue' : 'green'}>{draftTotal} 个草稿待发布</Tag>
                    )}
                  </Space>
                )}
                extra={(
                  <Button type="link" size="small" onClick={() => navigate('/entities?status=0')}>
                    查看全部 <ArrowRightOutlined />
                  </Button>
                )}
              >
                {entitiesLoading && myEntities.length === 0 ? (
                  /* 骨架屏而非空白：待办区是本页主视觉，闪现空容器会让用户以为「没有草稿」 */
                  <div style={{ padding: '4px 0' }}>
                    <Skeleton active title={false} paragraph={{ rows: 3 }} />
                  </div>
                ) : myEntities.length === 0 ? (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    没有待发布的草稿；用上方三个入口创建。完整清单（搜索 / 筛选 / 批量发布）见左侧菜单「业务对象」。
                  </Text>
                ) : (
                  /* 行列表而非卡片网格：待办是「要去处理的事」，不是第二个对象清单。
                     卡片网格视觉上像一份可浏览的清单，与「业务对象=唯一真源」的边界冲突（D3）。 */
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {myEntities.map((e, idx) => (
                      <div
                        key={e.id}
                        onClick={() => navigate(entityPath(e))}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 12,
                          padding: '9px 4px',
                          borderTop: idx === 0 ? 'none' : '1px solid #f0f0f0',
                          cursor: 'pointer',
                        }}
                        onMouseEnter={(ev) => { ev.currentTarget.style.background = '#fafafa'; }}
                        onMouseLeave={(ev) => { ev.currentTarget.style.background = 'transparent'; }}
                      >
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <Space size={6} wrap>
                            <Text strong style={{ fontSize: 13 }}>{e.displayName}</Text>
                            <Text code style={{ fontSize: 12 }}>{e.code}</Text>
                            <Tag color="blue">草稿</Tag>
                          </Space>
                          <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              草稿尚未生成物理表，发布后才能录入业务数据
                            </Text>
                          </div>
                        </div>
                        {/* 待办的主动作即「发布」：一次点击走完待办的终点（PublishPreview 内含校验摘要），
                            「去设计」退为次级（还要补字段时才用）。两者都是行内直达，不外跳。 */}
                        <Tooltip title="校验字段后发布，发布完成即建出物理表">
                          <Button
                            size="small"
                            type="primary"
                            icon={<ThunderboltOutlined />}
                            onClick={(ev) => { ev.stopPropagation(); setPublishTarget(e); }}
                          >
                            发布
                          </Button>
                        </Tooltip>
                        <Tooltip title="进入对象工作区（结构 / 界面 / 数据 / 发布）">
                          <Button
                            size="small"
                            onClick={(ev) => { ev.stopPropagation(); navigate(entityPath(e)); }}
                          >
                            去设计
                          </Button>
                        </Tooltip>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            </div>

            {/* 右栏 rail：概览 + 最近 + 应用导航 */}
            <div style={{ width: 280, flexShrink: 0 }}>
              {/* 概览行式（rail 宽度有限，行式比三张大卡省空间；口径独立于列表筛选） */}
              <Card size="small" title="建模概览" style={{ marginBottom: 12 }} styles={{ body: { padding: '10px 14px' } }}>
                <Row gutter={[12, 8]}>
                  <Col span={12}>
                    <Statistic title="业务对象" value={entityTotal ?? 0} valueStyle={{ fontSize: 18 }} />
                  </Col>
                  <Col span={12}>
                    <Statistic title="已发布" value={publishedTotal ?? 0} valueStyle={{ fontSize: 18, color: '#52c41a' }} />
                  </Col>
                </Row>
              </Card>

              {/* 继续建模：最近编辑（localStorage），chip 化便于扫读重入（D4） */}
              {recent.length > 0 && (
                <Card
                  size="small"
                  title={<Space><ClockCircleOutlined /><span>继续建模</span></Space>}
                  style={{ marginBottom: 12 }}
                >
                  <Space wrap size={[6, 6]}>
                    {recent.map((r) => (
                      <Tag
                        key={r.id}
                        color="blue"
                        style={{ cursor: 'pointer', marginInlineEnd: 0 }}
                        onClick={() => navigate(recentPath(r))}
                      >
                        {r.displayName || r.code}
                      </Tag>
                    ))}
                  </Space>
                </Card>
              )}

              {/* 应用导航收进折叠区（低频） */}
              <Collapse
                size="small"
                items={[{
                  key: 'apps',
                  label: <Space><AppstoreOutlined /><span>我的应用</span></Space>,
                  children: (
                    <Space direction="vertical" style={{ width: '100%' }} size={6}>
                      {apps.map((app) => (
                        <div
                          key={app.id}
                          onClick={() => navigate(`/apps/${app.id}/modules`)}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            padding: '6px 8px',
                            borderRadius: 6,
                            cursor: 'pointer',
                          }}
                          onMouseEnter={(ev) => { ev.currentTarget.style.background = '#f5f5f5'; }}
                          onMouseLeave={(ev) => { ev.currentTarget.style.background = 'transparent'; }}
                        >
                          <span style={{ fontSize: 18 }}>{appEmoji(app.icon)}</span>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <Text style={{ fontSize: 13 }} ellipsis>{app.name}</Text>
                            <div>
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                <BlockOutlined /> {app.moduleCount} 模块
                              </Text>
                            </div>
                          </div>
                          {app.myRole && (
                            <Tag color={PERM_COLORS[app.myRole]} style={{ marginInlineEnd: 0, fontSize: 11 }}>
                              {PERM_LABELS[app.myRole]}
                            </Tag>
                          )}
                        </div>
                      ))}
                    </Space>
                  ),
                }]}
              />
            </div>
          </div>
        </>
      )}

      {/* 待办就地发布：与「业务对象」页复用同一发布预览组件（单一真源，不复制校验摘要逻辑）。
          发布成功后刷新待发布列表（该条应从待办中消失）并给出前向引导。 */}
      <PublishPreviewModal
        open={publishTarget != null}
        entityId={publishTarget?.id ?? null}
        entityName={publishTarget?.displayName}
        onClose={() => setPublishTarget(null)}
        onPublished={() => {
          loadWorkspace();
        }}
        onViewInterface={() => {
          if (publishTarget?.id) navigate(`/entities/${publishTarget.id}?tab=ui`);
          setPublishTarget(null);
        }}
        onOpenData={() => {
          if (publishTarget?.id) navigate(`/entities/${publishTarget.id}/data`);
          setPublishTarget(null);
        }}
        onAddField={() => {
          if (publishTarget?.id) navigate(`/entities/${publishTarget.id}?tab=structure`);
          setPublishTarget(null);
        }}
      />

      {/* 新建/导入落点选择：应用+模块 → 直达实体列表并自动开对话框。
          两个下拉都带「＋ 新建」（O2）：缺容器时就地创建并自动选中，不外跳 IAM。 */}
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
            {/* 零可写应用时给出内联创建入口，而不是一个空下拉 + 永久禁用的「进入」 */}
            {writableApps.length === 0 ? (
              <Button block icon={<PlusOutlined />} onClick={() => setCreatingApp(true)}>
                先创建一个应用
              </Button>
            ) : (
              <Select
                style={{ width: '100%' }}
                placeholder="选择应用"
                value={targetAppId}
                onChange={(v) => { setTargetAppId(v); setTargetModuleId(undefined); }}
                options={writableApps.map((a) => ({ value: a.id, label: a.name }))}
                popupRender={(menu) => (
                  <>
                    {menu}
                    <div style={{ padding: 4, borderTop: '1px solid #f0f0f0' }}>
                      <Button type="text" size="small" icon={<PlusOutlined />} onClick={() => setCreatingApp(true)}>
                        新建应用
                      </Button>
                    </div>
                  </>
                )}
              />
            )}
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
              /* 选了应用但该应用还没有模块 = 死路（okButton 永久禁用且无提示）。
                 直接把「新建模块」提到空态位，用户不必先打开下拉再点底部的加号。 */
              notFoundContent={
                moduleOptions.length === 0 ? (
                  <div style={{ padding: 8 }}>
                    <Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
                      该应用下还没有模块
                    </Text>
                    <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => setCreatingModule(true)}>
                      新建模块
                    </Button>
                  </div>
                ) : undefined
              }
              popupRender={(menu) => (
                <>
                  {menu}
                  <div style={{ padding: 4, borderTop: '1px solid #f0f0f0' }}>
                    <Button
                      type="text"
                      size="small"
                      icon={<PlusOutlined />}
                      disabled={!targetAppId}
                      onClick={() => setCreatingModule(true)}
                    >
                      新建模块
                    </Button>
                  </div>
                </>
              )}
            />
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            应用是对象的归属容器（模块 → 物理表归属的硬约束），因此新建对象必须落在某个模块下。
          </Text>
        </Space>
      </Modal>

      {/* 内联新建应用（O2）：仅创建容器，成员授权仍归 IAM */}
      <Modal
        open={creatingApp}
        title="新建应用"
        okText="创建"
        onOk={handleCreateApp}
        onCancel={() => setCreatingApp(false)}
        destroyOnHidden
        width={440}
      >
        <Form form={appForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="name" label="应用名称" rules={[{ required: true, message: '请输入应用名称' }]}>
            <Input placeholder="如：费用报销" />
          </Form.Item>
          <Form.Item
            name="code"
            label="应用编码"
            rules={[{ required: true, message: '请输入应用编码' }, CODE_RULE]}
            tooltip="全局唯一，仅字母开头，可用字母/数字/下划线"
          >
            <Input placeholder="如：expense" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="这个应用承载什么业务" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 内联新建模块（O2） */}
      <Modal
        open={creatingModule}
        title={targetAppId ? `在「${writableApps.find((a) => a.id === targetAppId)?.name ?? ''}」下新建模块` : '新建模块'}
        okText="创建"
        onOk={handleCreateModule}
        onCancel={() => setCreatingModule(false)}
        destroyOnHidden
        width={440}
      >
        <Form form={moduleForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="name" label="模块名称" rules={[{ required: true, message: '请输入模块名称' }]}>
            <Input placeholder="如：报销单" />
          </Form.Item>
          <Form.Item
            name="code"
            label="模块编码"
            rules={[{ required: true, message: '请输入模块编码' }, CODE_RULE]}
            tooltip="应用内唯一，仅字母开头"
          >
            <Input placeholder="如：claim" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ModelingWorkspace;
