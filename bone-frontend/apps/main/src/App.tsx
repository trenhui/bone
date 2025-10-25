import React, { useEffect, useState } from 'react';
import { getApplicationRegistry, MicroAppConfig } from '@bone/core/micro-fe-runtime';
import { Button } from '@bone/ui/components';
import './App.css';

const App: React.FC = () => {
  const [activeApp, setActiveApp] = useState<string | null>(null);
  const [apps, setApps] = useState<MicroAppConfig[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // 初始化应用注册表
    const registry = getApplicationRegistry();
    
    // 注册子应用
    const subApps: MicroAppConfig[] = [
      {
        name: 'admin-portal',
        entry: 'http://localhost:3001',
        container: '#micro-app-container',
        activeRule: '/admin',
        sandbox: true,
        props: {
          appId: 'admin-123',
          theme: 'light'
        }
      },
      {
        name: 'user-center',
        entry: 'http://localhost:3002',
        container: '#micro-app-container',
        activeRule: '/user',
        sandbox: true,
        props: {
          appId: 'user-456',
          theme: 'light'
        }
      },
      {
        name: 'bone-extension-studio-ui',
        entry: 'http://localhost:3003', // 假设微应用运行在3003端口
        container: '#micro-app-container',
        activeRule: '/extension-studio',
        sandbox: true,
        props: {
          appId: 'extension-789',
          theme: 'light',
          config: {
            apiBaseUrl: '/api/extension',
            environment: process.env.NODE_ENV || 'development'
          },
          context: {
            token: localStorage.getItem('bone-token') || '',
            userId: localStorage.getItem('bone-user-id') || ''
          }
        }
      }
    ];

    subApps.forEach(app => {
      registry.register(app);
    });
    
    setApps(subApps);

    // 监听路由变化
    const handleRouteChange = () => {
      const path = window.location.pathname;
      
      // 找到匹配的应用
      const matchedApp = subApps.find(app => {
        if (typeof app.activeRule === 'string') {
          return path.startsWith(app.activeRule);
        } else if (app.activeRule instanceof RegExp) {
          return app.activeRule.test(path);
        }
        return false;
      });

      if (matchedApp) {
        setActiveApp(matchedApp.name);
      } else {
        setActiveApp(null);
      }
    };

    // 初始化路由状态
    handleRouteChange();

    // 监听popstate事件
    window.addEventListener('popstate', handleRouteChange);

    // 清理函数
    return () => {
      window.removeEventListener('popstate', handleRouteChange);
    };
  }, []);

  // 激活应用
  const activateApp = async (appName: string) => {
    setLoading(true);
    try {
      const registry = getApplicationRegistry();
      await registry.activateApp(appName);
      setActiveApp(appName);
      
      // 更新URL
      const app = apps.find(a => a.name === appName);
      if (app && typeof app.activeRule === 'string') {
        window.history.pushState(null, '', app.activeRule);
      }
    } catch (error) {
      console.error(`Failed to activate app ${appName}:`, error);
    } finally {
      setLoading(false);
    }
  };

  // 停用应用
  const deactivateApp = async () => {
    if (activeApp) {
      try {
        const registry = getApplicationRegistry();
        await registry.deactivateApp(activeApp);
        setActiveApp(null);
        window.history.pushState(null, '', '/');
      } catch (error) {
        console.error(`Failed to deactivate app ${activeApp}:`, error);
      }
    }
  };

  return (
    <div className="app-container">
      <header className="app-header">
        <h1 className="app-title">Bone Micro Frontend Platform</h1>
        <nav className="app-nav">
          {apps.map(app => (
            <Button
              key={app.name}
              variant={activeApp === app.name ? 'primary' : 'secondary'}
              size="sm"
              onClick={() => activateApp(app.name)}
              className="nav-button"
            >
              {app.name}
            </Button>
          ))}
          {activeApp && (
            <Button
              variant="outline"
              size="sm"
              onClick={deactivateApp}
              className="nav-button"
            >
              Exit
            </Button>
          )}
        </nav>
      </header>

      <main className="app-main">
        {activeApp ? (
          <div className="micro-app-wrapper">
            {loading && (
              <div className="loading-overlay">
                <div className="loading-text">Loading {activeApp}...</div>
              </div>
            )}
            <div id="micro-app-container" className="micro-app-container"></div>
          </div>
        ) : (
          <div className="welcome-screen">
            <h2>Welcome to Bone Platform</h2>
            <p>Select an application from the navigation menu above</p>
            <div className="app-info">
              <h3>Available Applications</h3>
              <ul>
                {apps.map(app => (
                  <li key={app.name}>
                    <strong>{app.name}</strong>
                    <span className="app-entry">- {app.entry}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </main>

      <footer className="app-footer">
        <p>Bone Frontend Framework © {new Date().getFullYear()}</p>
        <div className="footer-info">
          <span>Runtime: v1.0.0</span>
          <span>Active: {activeApp || 'none'}</span>
        </div>
      </footer>
    </div>
  );
};

export default App;