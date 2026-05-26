import { useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Spin, theme as antdTheme } from 'antd';
import {
  UserOutlined,
  UserAddOutlined,
  DatabaseOutlined,
  LinkOutlined,
  SettingOutlined,
  AppstoreOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import {
  fetchConsoleOverview,
  fetchQuickActions,
  type ConsoleOverview,
  type QuickAction,
} from '../services/consoleApi';
import { themePreferenceLabel } from '@bone/ui';
import { LayoutContext, ThemeContext } from '../shellContext';

const OVERVIEW_REFRESH_MS = 30_000;

const QUICK_ACTION_ICONS: Record<string, ReactNode> = {
  UserOutlined: <UserAddOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  LinkOutlined: <LinkOutlined />,
  SettingOutlined: <SettingOutlined />,
  AppstoreOutlined: <AppstoreOutlined />,
  CodeOutlined: <CodeOutlined />,
};

function serviceStatusLabel(status?: string): { text: string; color: string } {
  const normalized = (status ?? '').toLowerCase();
  if (normalized === 'up' || normalized === 'running') {
    return { text: '运行中', color: '#52c41a' };
  }
  if (normalized === 'down' || normalized === 'out_of_service') {
    return { text: '异常', color: '#ff4d4f' };
  }
  return { text: '未知', color: '#faad14' };
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) {
    return '—';
  }
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

export default function DashboardPage(): JSX.Element {
  const { theme, resolvedTheme } = useContext(ThemeContext);
  const { layoutMode } = useContext(LayoutContext);
  const { token } = antdTheme.useToken();

  const [overview, setOverview] = useState<ConsoleOverview | null>(null);
  const [quickActions, setQuickActions] = useState<QuickAction[]>([]);
  const [loading, setLoading] = useState(true);
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

  const stats = useMemo(() => {
    const km = overview?.keyMetrics ?? {};
    return [
      {
        title: '用户数量',
        value: km.userCount ?? '—',
        icon: <UserOutlined />,
        color: token.colorPrimary,
      },
      {
        title: '实体数量',
        value: km.entityCount ?? '—',
        icon: <DatabaseOutlined />,
        color: token.colorSuccess,
      },
      {
        title: '集成流程',
        value: km.integrationFlowCount ?? '—',
        icon: <LinkOutlined />,
        color: token.colorWarning,
      },
      {
        title: '扩展插件',
        value: km.extensionPluginCount ?? '—',
        icon: <AppstoreOutlined />,
        color: token.colorError,
      },
    ];
  }, [overview, token]);

  const resourceUsage = overview?.resourceUsage;
  const memoryLine =
    resourceUsage?.memoryMaxBytes && resourceUsage.memoryMaxBytes > 0
      ? `${formatBytes(resourceUsage.memoryUsedBytes ?? 0)} / ${formatBytes(resourceUsage.memoryMaxBytes)}`
      : formatBytes(resourceUsage?.memoryUsedBytes ?? 0);

  const alerts = overview?.alerts;

  if (loading) {
    return (
      <div className={`dashboard ${resolvedTheme}`} style={{ padding: 48, textAlign: 'center' }}>
        <Spin size="large" tip="加载控制台数据…" />
      </div>
    );
  }

  return (
    <div className={`dashboard ${resolvedTheme}`}>
      <div className="dashboard-header">
        <h1>BONE 平台控制台</h1>
        <p>欢迎回来，admin！</p>
        {overview?.updatedAt && (
          <p className="dashboard-updated">数据更新于 {overview.updatedAt}</p>
        )}
        <div className="dashboard-info">
          <div className="info-item">
            <span>当前主题: </span>
            <span>{themePreferenceLabel(theme)}</span>
          </div>
          <div className="info-item">
            <span>布局模式: </span>
            <span>
              {layoutMode === 'side' ? '侧边菜单' : layoutMode === 'top' ? '顶部菜单' : '混合模式'}
            </span>
          </div>
          <div className="info-item">
            <span>JVM 内存: </span>
            <span>{memoryLine}</span>
          </div>
        </div>
      </div>

      {error && <Alert type="warning" message={error} showIcon style={{ marginBottom: 16 }} />}

      <div className="stats-section">
        <h2 className="section-title">系统概览</h2>
        <div className="stats-cards">
          {stats.map((stat) => (
            <div key={stat.title} className="stat-card">
              <div
                className="stat-icon"
                style={{ backgroundColor: `${stat.color}20`, color: stat.color }}
              >
                {stat.icon}
              </div>
              <div className="stat-content">
                <div className="stat-value">{stat.value}</div>
                <div className="stat-title">{stat.title}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-section">
          <h2 className="section-title">服务状态</h2>
          <div className="status-list">
            {(overview?.services ?? []).map((item) => {
              const label = serviceStatusLabel(item.status);
              return (
                <div key={item.serviceCode ?? item.name} className="status-item">
                  <span className="status-service">{item.name ?? item.serviceCode}</span>
                  <span className="status-indicator" style={{ backgroundColor: label.color }} />
                  <span className="status-text">{label.text}</span>
                </div>
              );
            })}
            {(overview?.services ?? []).length === 0 && (
              <span className="status-empty">暂无服务状态数据</span>
            )}
          </div>
        </div>

        <div className="dashboard-section">
          <h2 className="section-title">系统告警</h2>
          <div className="activity-list">
            {!alerts || alerts.length === 0 ? (
              <span className="status-empty">暂无告警</span>
            ) : (
              alerts.map((alert, index) => (
                <div key={index} className="activity-item">
                  <div className="activity-content">
                    <span className="activity-action">{alert.message ?? '告警'}</span>
                  </div>
                  <div className="activity-status">{alert.level ?? 'info'}</div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="dashboard-section">
        <h2 className="section-title">快捷操作</h2>
        <div className="quick-actions">
          {quickActions.map((action) => (
            <Link key={action.id} to={action.path} className="quick-action-item">
              <div className="quick-action-icon">
                {QUICK_ACTION_ICONS[action.icon ?? ''] ?? <AppstoreOutlined />}
              </div>
              <span>{action.title}</span>
            </Link>
          ))}
          {quickActions.length === 0 && <span className="status-empty">暂无快捷操作配置</span>}
        </div>
      </div>
    </div>
  );
}
