import React, { useState, useEffect } from 'react';
import { Button } from '@bone/ui/components';
import './App.css';

// 扩展Window接口
declare global {
  interface Window {
    __BONE_CONFIG__?: any;
    __MICRO_APP_ENVIRONMENT__?: boolean;
  }
}

interface AppProps {
  appId?: string;
  container?: string;
  [key: string]: any;
}

interface MetricCardProps {
  title: string;
  value: number | string;
  change?: number;
  unit?: string;
  icon?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({ title, value, change, unit, icon }) => {
  const isPositiveChange = change !== undefined && change > 0;
  const isNegativeChange = change !== undefined && change < 0;

  return (
    <div className="metric-card">
      <div className="metric-header">
        <h3>{title}</h3>
        {icon && <span className={`metric-icon ${icon}`}></span>}
      </div>
      <div className="metric-value">
        {value}
        {unit && <span className="metric-unit">{unit}</span>}
      </div>
      {change !== undefined && (
        <div className={`metric-change ${isPositiveChange ? 'positive' : isNegativeChange ? 'negative' : 'neutral'}`}>
          {isPositiveChange ? '↑' : isNegativeChange ? '↓' : ''}
          {Math.abs(change)}%
        </div>
      )}
    </div>
  );
};

interface MetricsData {
  activeUsers: number;
  pageViews: number;
  conversionRate: number;
  averageSession: number;
  bounceRate: number;
  revenue: number;
}

interface TimeRangeData {
  [key: string]: MetricsData;
}

const App: React.FC<AppProps> = ({ appId, container, ...props }) => {
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [metrics, setMetrics] = useState<MetricsData>({
    activeUsers: 0,
    pageViews: 0,
    conversionRate: 0,
    averageSession: 0,
    bounceRate: 0,
    revenue: 0
  });
  const [timeRange, setTimeRange] = useState<string>('7d');
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  useEffect(() => {
    // 应用初始化逻辑
    console.log('Sub-App-2 initialized with props:', { appId, container, ...props });
    
    // 加载数据
    loadMetrics();
  }, [appId, container]);

  const loadMetrics = async (): Promise<void> => {
    setIsLoading(true);
    try {
      // 模拟数据加载
      await new Promise<void>(resolve => setTimeout(resolve, 800));
      
      // 根据时间范围设置不同的模拟数据
      const mockMetrics: TimeRangeData = {
        '7d': {
          activeUsers: 1250,
          pageViews: 10240,
          conversionRate: 2.8,
          averageSession: 3.5,
          bounceRate: 45.2,
          revenue: 12500
        },
        '30d': {
          activeUsers: 5230,
          pageViews: 45670,
          conversionRate: 3.2,
          averageSession: 4.2,
          bounceRate: 42.1,
          revenue: 68500
        },
        '90d': {
          activeUsers: 15890,
          pageViews: 128900,
          conversionRate: 3.5,
          averageSession: 4.5,
          bounceRate: 39.8,
          revenue: 235000
        }
      };

      const selectedMetrics = mockMetrics[timeRange];
      if (selectedMetrics) {
        setMetrics(selectedMetrics);
      }
    } catch (error: unknown) {
      console.error('Failed to load metrics:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleRefresh = async (): Promise<void> => {
    setIsRefreshing(true);
    try {
      // 模拟刷新操作
      await new Promise<void>(resolve => setTimeout(resolve, 1000));
      await loadMetrics();
      
      // 向主应用发送刷新成功事件
      if (window.__BONE_CONFIG__) {
        console.log('Metrics refreshed successfully in Sub-App-2');
      }
    } catch (error: unknown) {
      console.error('Failed to refresh metrics:', error);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleTimeRangeChange = (range: string): void => {
    setTimeRange(range);
    loadMetrics();
  };

  return (
    <div className="sub-app-2-container">
      <header className="sub-app-2-header">
        <div className="header-content">
          <h1 className="app-title">Analytics Dashboard</h1>
          <div className="header-actions">
            <div className="time-range-selector">
              {['7d', '30d', '90d'].map((range) => (
                <button
                  key={range}
                  className={`time-range-btn ${timeRange === range ? 'active' : ''}`}
                  onClick={() => handleTimeRangeChange(range)}
                >
                  {range === '7d' ? 'Last 7 days' : range === '30d' ? 'Last 30 days' : 'Last 90 days'}
                </button>
              ))}
            </div>
            <Button 
              variant="primary" 
              onClick={handleRefresh}
              loading={isRefreshing}
            >
              Refresh Data
            </Button>
          </div>
        </div>
        {appId && (
          <div className="app-id">ID: {appId}</div>
        )}
      </header>

      <main className="sub-app-2-main">
        {isLoading ? (
          <div className="loading-container">
            <div className="loading-spinner"></div>
            <p>Loading Analytics Dashboard...</p>
          </div>
        ) : (
          <>
            {/* Key Metrics Section */}
            <section className="metrics-section">
              <h2>Key Metrics</h2>
              <div className="metrics-grid">
                <MetricCard 
                  title="Active Users" 
                  value={metrics.activeUsers.toLocaleString()} 
                  change={12.5} 
                  icon="users" 
                />
                <MetricCard 
                  title="Page Views" 
                  value={metrics.pageViews.toLocaleString()} 
                  change={8.3} 
                  icon="eye" 
                />
                <MetricCard 
                  title="Conversion Rate" 
                  value={metrics.conversionRate} 
                  unit="%" 
                  change={-1.2} 
                  icon="trending-up" 
                />
                <MetricCard 
                  title="Avg. Session" 
                  value={metrics.averageSession} 
                  unit="m" 
                  change={5.7} 
                  icon="clock" 
                />
                <MetricCard 
                  title="Bounce Rate" 
                  value={metrics.bounceRate} 
                  unit="%" 
                  change={-3.1} 
                  icon="arrow-left" 
                />
                <MetricCard 
                  title="Revenue" 
                  value={metrics.revenue.toLocaleString()} 
                  unit="$" 
                  change={15.8} 
                  icon="dollar-sign" 
                />
              </div>
            </section>

            {/* Data Visualization Section */}
            <section className="visualization-section">
              <h2>Performance Overview</h2>
              <div className="charts-grid">
                <div className="chart-card">
                  <h3>User Growth</h3>
                  <div className="chart-placeholder user-growth-chart">
                    {/* 实际项目中这里会渲染真实的图表 */}
                    <div className="chart-bars">
                      {[65, 59, 80, 81, 56, 55, 72].map((value, index) => (
                        <div key={index} className="chart-bar">
                          <div className="bar" style={{ height: `${value}%` }}></div>
                          <div className="bar-label">{index + 1}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="chart-card">
                  <h3>Revenue Trend</h3>
                  <div className="chart-placeholder revenue-chart">
                    {/* 实际项目中这里会渲染真实的图表 */}
                    <div className="chart-line">
                      {[12, 19, 13, 15, 20, 25, 22].map((value, index) => (
                        <div 
                          key={index} 
                          className="line-point" 
                          style={{ 
                            left: `${(index / 6) * 100}%`, 
                            bottom: `${value * 3}%` 
                          }}
                        ></div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </section>

            {/* App Info Section */}
            <section className="app-info-section">
              <h2>Application Information</h2>
              <div className="info-grid">
                <div className="info-item">
                  <strong>App Name:</strong> sub-app-2
                </div>
                <div className="info-item">
                  <strong>Version:</strong> 1.0.0
                </div>
                <div className="info-item">
                  <strong>Environment:</strong> {process.env.NODE_ENV || 'development'}
                </div>
                <div className="info-item">
                  <strong>Micro Frontend:</strong> {window.__MICRO_APP_ENVIRONMENT__ ? 'Yes' : 'No'}
                </div>
              </div>
            </section>
          </>
        )}
      </main>

      <footer className="sub-app-2-footer">
        <div className="footer-content">
          <p>&copy; {new Date().getFullYear()} Sub-App-2 - Analytics Dashboard</p>
          <div className="footer-links">
            <a href="#docs" className="footer-link">Documentation</a>
            <a href="#support" className="footer-link">Support</a>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default App;