import React, { useState, useEffect } from 'react';
import { Button } from '@bone/ui/components';
import './App.css';

// 扩展Window接口
declare global {
  interface Window {
    __BONE_CONFIG__?: any;
  }
}

interface AppProps {
  appId?: string;
  container?: string;
  [key: string]: any;
}

const App: React.FC<AppProps> = ({ appId, container, ...props }) => {
  const [count, setCount] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [appInfo, setAppInfo] = useState<{ name: string; version: string }>({
    name: 'Sub-App-1',
    version: '1.0.0'
  });

  useEffect(() => {
    // 应用初始化逻辑
    console.log('Sub-App-1 initialized with props:', { appId, container, ...props });
    
    // 模拟数据加载
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 500);

    return () => clearTimeout(timer);
  }, [appId, container]);

  const handleIncrement = () => {
    setCount((prevCount: number) => {
      const newCount = prevCount + 1;
      
      // 向主应用或其他子应用发送事件示例
      if (window.__BONE_CONFIG__) {
        // 实际项目中可以使用事件总线发送事件
        console.log('Sending event from Sub-App-1:', { event: 'count-updated', data: { count: newCount } });
      }
      return newCount;
    });
  };

  const handleDecrement = () => {
    setCount((prevCount: number) => Math.max(0, prevCount - 1));
  };

  const handleReset = () => {
    setCount(0);
  };

  return (
    <div className="sub-app-1-container">
      <header className="sub-app-1-header">
        <div className="header-content">
          <h1 className="app-title">{appInfo.name}</h1>
          <div className="app-version">v{appInfo.version}</div>
        </div>
        {appId && (
          <div className="app-id">ID: {appId}</div>
        )}
      </header>

      <main className="sub-app-1-main">
        {isLoading ? (
          <div className="loading-container">
            <div className="loading-spinner"></div>
            <p>Loading Sub-App-1...</p>
          </div>
        ) : (
          <div className="counter-section">
            <h2>Counter Example</h2>
            <div className="counter-display">
              <span className="count-value">{count}</span>
            </div>
            <div className="counter-controls">
              <Button variant="secondary" onClick={handleDecrement} disabled={count === 0}>
                Decrement
              </Button>
              <Button variant="primary" onClick={handleIncrement}>
                Increment
              </Button>
              <Button variant="outline" onClick={handleReset} disabled={count === 0}>
                Reset
              </Button>
            </div>
          </div>
        )}

        <div className="features-section">
          <h2>Available Features</h2>
          <div className="feature-cards">
            <div className="feature-card">
              <h3>Feature 1</h3>
              <p>Basic counter functionality with state management</p>
            </div>
            <div className="feature-card">
              <h3>Feature 2</h3>
              <p>Micro frontend integration with main app</p>
            </div>
            <div className="feature-card">
              <h3>Feature 3</h3>
              <p>Independent operation capability</p>
            </div>
          </div>
        </div>

        <div className="props-info">
          <h3>App Props</h3>
          <div className="props-display">
            {Object.keys(props).length > 0 ? (
              <pre>{JSON.stringify(props, null, 2)}</pre>
            ) : (
              <p>No additional props provided</p>
            )}
          </div>
        </div>
      </main>

      <footer className="sub-app-1-footer">
        <div className="footer-content">
          <p>&copy; {new Date().getFullYear()} Sub-App-1 - Bone Micro Frontend Platform</p>
          <div className="footer-links">
            <a href="#about" className="footer-link">About</a>
            <a href="#docs" className="footer-link">Documentation</a>
            <a href="#contact" className="footer-link">Contact</a>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default App;