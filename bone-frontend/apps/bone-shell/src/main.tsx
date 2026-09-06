import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import { setupAxiosAuthInterceptor } from './auth/axiosAuth';
import './index.css';

setupAxiosAuthInterceptor();

// 初始化性能监控（模拟）
const performanceMonitor = {
  init: () => {
    console.log('Performance monitor initialized');
  }
};
performanceMonitor.init();

// 注意：antd v5 / @ant-design/pro-components 的工具栏 Tooltip 在 React.StrictMode 下会触发
// findDOMNode 弃用告警（第三方组件内部行为，仅 dev 控制台噪声，不影响运行）。为获得干净控制台，不启用 StrictMode。
ReactDOM.createRoot(document.getElementById('root')!).render(<App />);