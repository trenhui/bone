import { useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Progress, Spin } from 'antd';
import {
  UserOutlined,
  UserAddOutlined,
  DatabaseOutlined,
  LinkOutlined,
  SettingOutlined,
  AppstoreOutlined,
  CodeOutlined,
  ShoppingOutlined,
  DollarOutlined,
  ReloadOutlined,
  ApartmentOutlined,
  TeamOutlined,
  ApiOutlined,
} from '@ant-design/icons';
import {
  fetchConsoleOverview,
  fetchQuickActions,
  type ConsoleOverview,
  type QuickAction,
} from '../services/consoleApi';
import { formatDate } from '@bone/shared-utils';
import { ThemeContext } from '../shellContext';

const OVERVIEW_REFRESH_MS = 30_000;

const QUICK_ACTION_ICONS: Record<string, ReactNode> = {
  UserOutlined: <UserAddOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  LinkOutlined: <LinkOutlined />,
  SettingOutlined: <SettingOutlined />,
  AppstoreOutlined: <AppstoreOutlined />,
  CodeOutlined: <CodeOutlined />,
};

/** BONE 平台真实架构能力 —— 用于"架构能力墙"展示框架专业度 */
const CAPABILITIES: { icon: ReactNode; title: string; desc: string }[] = [
  {
    icon: <DatabaseOutlined />,
    title: '元数据驱动引擎',
    desc: '基于 bone-metadata-sdk 的元数据建模，实体 / 字段可视化配置即生效。',
  },
  {
    icon: <ApartmentOutlined />,
    title: 'DDD + CQRS 架构',
    desc: '领域驱动分层 + 命令 / 查询职责分离，复杂业务可持续演进。',
  },
  {
    icon: <TeamOutlined />,
    title: '多租户隔离',
    desc: '租户级数据隔离与上下文传播，原生支撑 SaaS 化部署。',
  },
  {
    icon: <AppstoreOutlined />,
    title: '微前端架构',
    desc: 'Qiankun 微前端，多团队独立开发、独立部署、运行时集成。',
  },
  {
    icon: <ApiOutlined />,
    title: '统一 SDK 持久层',
    desc: '自研 SDK 收敛持久化，禁用异构 ORM，契约单一真源。',
  },
  {
    icon: <LinkOutlined />,
    title: '开放集成网关',
    desc: '统一 API 网关 + 集成流程编排，内外部系统低代码打通。',
  },
];

function serviceState(status?: string): { cls: string; text: string } {
  const normalized = (status ?? '').toLowerCase();
  if (normalized === 'up' || normalized === 'running') {
    return { cls: 'up', text: '运行中' };
  }
  if (normalized === 'down' || normalized === 'out_of_service') {
    return { cls: 'down', text: '异常' };
  }
  return { cls: 'unknown', text: '未知' };
}

function alertStyle(level?: string): { bg: string; color: string; dot: string } {
  const l = (level ?? 'info').toLowerCase();
  if (l === 'error' || l === 'critical') {
    return { bg: 'var(--bone-color-error-bg)', color: 'var(--bone-color-error)', dot: 'var(--bone-color-error)' };
  }
  if (l === 'warning') {
    return { bg: 'var(--bone-color-warning-bg)', color: 'var(--bone-color-warning)', dot: 'var(--bone-color-warning)' };
  }
  return { bg: 'var(--bone-color-info-bg)', color: 'var(--bone-color-info)', dot: 'var(--bone-color-info)' };
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) {
    return '0 MB';
  }
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

/** 后端 Long 以 JSON string 返回（平台约定），数值字段须归一后再参与计算 */
function toNum(v: number | string | undefined | null): number {
  const n = typeof v === 'string' ? parseFloat(v) : typeof v === 'number' ? v : NaN;
  return Number.isFinite(n) ? n : 0;
}

const fmtNum = (v?: number | string): string =>
  v === undefined || v === null || v === '' ? '—' : toNum(v).toLocaleString('zh-CN');
const fmtMoney = (v?: number | string): string =>
  v === undefined || v === null || v === '' ? '—' : `¥${toNum(v).toLocaleString('zh-CN')}`;

function ResourceRing({
  percent,
  label,
  sub,
  from,
  to,
}: {
  percent: number;
  label: string;
  sub?: string;
  from: string;
  to: string;
}): JSX.Element {
  return (
    <div className="db-ring">
      <Progress
        type="dashboard"
        percent={Math.round(percent)}
        size={118}
        strokeColor={{ '0%': from, '100%': to }}
        trailColor="var(--bone-color-border-secondary)"
        format={(p) => (
          <span style={{ fontSize: 22, fontWeight: 700, color: 'var(--bone-color-text)' }}>{p}%</span>
        )}
      />
      <div className="db-ring-label">{label}</div>
      {sub && <div className="db-ring-sub">{sub}</div>}
    </div>
  );
}

export default function DashboardPage(): JSX.Element {
  const { resolvedTheme } = useContext(ThemeContext);

  const [overview, setOverview] = useState<ConsoleOverview | null>(null);
  const [quickActions, setQuickActions] = useState<QuickAction[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [overviewData, actions] = await Promise.all([
        fetchConsoleOverview(),
        fetchQuickActions(),
      ]);
      setOverview(overviewData);
      setQuickActions(actions);
      setError(overviewData ? null : '无法加载系统概览，请确认 bone-system 已启动（端口 8083）');
    } catch {
      setError('加载控制台数据失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => {
      void load();
    }, OVERVIEW_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await load();
    } finally {
      setRefreshing(false);
    }
  }, [load]);

  const stats = useMemo(() => {
    const km = overview?.keyMetrics ?? {};
    return [
      {
        title: '用户数量',
        desc: '注册账户',
        value: fmtNum(km.userCount),
        icon: <UserOutlined />,
        accent: 'linear-gradient(135deg,#1677ff,#69b1ff)',
      },
      {
        title: '实体数量',
        desc: '建模实体',
        value: fmtNum(km.entityCount),
        icon: <DatabaseOutlined />,
        accent: 'linear-gradient(135deg,#52c41a,#95de64)',
      },
      {
        title: '集成流程',
        desc: '活动集成',
        value: fmtNum(km.integrationFlowCount),
        icon: <LinkOutlined />,
        accent: 'linear-gradient(135deg,#fa8c16,#ffc069)',
      },
      {
        title: '扩展插件',
        desc: '已安装',
        value: fmtNum(km.extensionPluginCount),
        icon: <AppstoreOutlined />,
        accent: 'linear-gradient(135deg,#722ed1,#b37feb)',
      },
      {
        title: '订单数量',
        desc: '累计订单',
        value: fmtNum(km.orderCount),
        icon: <ShoppingOutlined />,
        accent: 'linear-gradient(135deg,#13c2c2,#5cdbd3)',
      },
      {
        title: '交易总额',
        desc: '累计成交',
        value: fmtMoney(km.transactionAmount),
        icon: <DollarOutlined />,
        accent: 'linear-gradient(135deg,#faad14,#ffd666)',
      },
    ];
  }, [overview]);

  const resourceUsage = overview?.resourceUsage;
  const keyMetrics = overview?.keyMetrics ?? {};
  const memUsed = toNum(resourceUsage?.memoryUsedBytes);
  const memMax = toNum(resourceUsage?.memoryMaxBytes);
  const memoryPercent = memMax > 0 ? (memUsed / memMax) * 100 : 0;
  const memorySub = memMax > 0 ? `${formatBytes(memUsed)} / ${formatBytes(memMax)}` : formatBytes(memUsed);

  const services = overview?.services ?? [];
  const upCount = services.filter((s) => {
    const n = (s.status ?? '').toLowerCase();
    return n === 'up' || n === 'running';
  }).length;

  const alerts = overview?.alerts;

  if (loading) {
    return (
      <div className={`dashboard ${resolvedTheme}`}>
        <div className="db-loading">
          <Spin size="large" />
          <span>加载控制台数据…</span>
        </div>
      </div>
    );
  }

  return (
    <div className={`dashboard ${resolvedTheme}`}>
      {/* ── Hero 横幅 ── */}
      <section className="db-hero">
        <div className="db-hero-left">
          <div className="db-hero-logo">B</div>
          <div>
            <h1 className="db-hero-title">BONE 平台控制台</h1>
            <p className="db-hero-sub">企业级元数据驱动 · DDD + CQRS · 微前端开发平台</p>
            <span className="db-hero-chip">
              <span className="db-live-dot" />
              系统运行中 · 实时数据
            </span>
          </div>
        </div>
        <div className="db-hero-right">
          {overview?.updatedAt && <span className="db-hero-time">数据更新于 {overview.updatedAt}</span>}
          <button className="db-refresh" onClick={onRefresh} disabled={refreshing}>
            <ReloadOutlined spin={refreshing} />
            {refreshing ? '刷新中…' : '刷新数据'}
          </button>
        </div>
      </section>

      {error && (
        <Alert type="warning" message={error} showIcon style={{ borderRadius: 12 }} />
      )}

      {/* ── KPI 指标 ── */}
      <section className="db-kpis">
        {stats.map((stat, i) => (
          <div className="db-kpi" style={{ animationDelay: `${i * 70}ms` }} key={stat.title}>
            <div className="db-kpi-icon" style={{ background: stat.accent }}>
              {stat.icon}
            </div>
            <div className="db-kpi-body">
              <div className="db-kpi-value">{stat.value}</div>
              <div className="db-kpi-title">{stat.title}</div>
              <div className="db-kpi-desc">{stat.desc}</div>
            </div>
          </div>
        ))}
      </section>

      {/* ── 系统资源 | 服务健康 ── */}
      <div className="db-grid-2">
        <section className="db-section">
          <div className="db-section-head">
            <h2 className="db-section-title">系统资源</h2>
            <span className="db-section-extra">实时占用</span>
          </div>
          <div className="db-rings">
            <ResourceRing
              percent={toNum(resourceUsage?.cpuPercent)}
              label="CPU 使用率"
              sub={`${toNum(resourceUsage?.cpuPercent)}%`}
              from="#1677ff"
              to="#69b1ff"
            />
            <ResourceRing
              percent={memoryPercent}
              label="JVM 内存"
              sub={memorySub}
              from="#13c2c2"
              to="#5cdbd3"
            />
            <ResourceRing
              percent={toNum(resourceUsage?.diskUsedPercent)}
              label="磁盘占用"
              sub={`${toNum(resourceUsage?.diskUsedPercent)}%`}
              from="#fa8c16"
              to="#ffc069"
            />
          </div>
          <div className="db-jvm">
            <div className="db-jvm-item">
              <div className="db-jvm-val">{fmtNum(keyMetrics.jvmThreadsLive)}</div>
              <div className="db-jvm-lbl">活跃线程</div>
            </div>
            <div className="db-jvm-item">
              <div className="db-jvm-val">{fmtNum(keyMetrics.jvmThreadsDaemon)}</div>
              <div className="db-jvm-lbl">守护线程</div>
            </div>
          </div>
        </section>

        <section className="db-section">
          <div className="db-section-head">
            <h2 className="db-section-title">服务健康</h2>
            <span className="db-section-extra">
              {upCount}/{services.length} 正常
            </span>
          </div>
          {services.length === 0 ? (
            <div className="db-empty">暂无服务状态数据</div>
          ) : (
            services.map((item) => {
              const st = serviceState(item.status);
              return (
                <div key={item.serviceCode ?? item.name} className="db-svc">
                  <span className="db-svc-name">
                    <span className={`db-dot ${st.cls}`} />
                    {item.name ?? item.serviceCode}
                    {item.port && <span className="db-svc-port">:{item.port}</span>}
                  </span>
                  {item.latencyMs != null && item.latencyMs !== '' && (
                    <span className="db-svc-latency">{toNum(item.latencyMs)} ms</span>
                  )}
                  <span className="db-svc-status" style={{ color: 'var(--bone-color-text-secondary)' }}>
                    {st.text}
                  </span>
                </div>
              );
            })
          )}
        </section>
      </div>

      {/* ── 架构能力 | 系统告警 ── */}
      <div className="db-grid-2">
        <section className="db-section">
          <div className="db-section-head">
            <h2 className="db-section-title">BONE 架构能力</h2>
          </div>
          <div className="db-arch-grid">
            {CAPABILITIES.map((cap) => (
              <div className="db-arch" key={cap.title}>
                <div className="db-arch-ico">{cap.icon}</div>
                <div>
                  <div className="db-arch-title">{cap.title}</div>
                  <div className="db-arch-desc">{cap.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="db-section">
          <div className="db-section-head">
            <h2 className="db-section-title">系统告警</h2>
          </div>
          {!alerts || alerts.length === 0 ? (
            <div className="db-empty">暂无告警</div>
          ) : (
            alerts.map((alert, index) => {
              const as = alertStyle(alert.level);
              return (
                <div key={index} className="db-alert">
                  <span className="db-alert-badge" style={{ background: as.dot }} />
                  <span className="db-alert-msg">{alert.message ?? '告警'}</span>
                  <span
                    className="db-alert-level"
                    style={{ background: as.bg, color: as.color }}
                  >
                    {alert.level ?? 'info'}
                  </span>
                </div>
              );
            })
          )}
        </section>
      </div>

      {/* ── 快捷操作 ── */}
      <section className="db-section">
        <div className="db-section-head">
          <h2 className="db-section-title">快捷操作</h2>
        </div>
        {quickActions.length === 0 ? (
          <div className="db-empty">暂无快捷操作配置</div>
        ) : (
          <div className="db-quick">
            {quickActions.map((action) => (
              <Link key={action.id} to={action.path} className="db-quick-item">
                <div className="db-quick-ico">
                  {QUICK_ACTION_ICONS[action.icon ?? ''] ?? <AppstoreOutlined />}
                </div>
                <span>{action.title}</span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
