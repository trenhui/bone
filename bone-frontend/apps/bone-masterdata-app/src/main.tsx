import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import App from './App';
import { setQiankunToken } from './services/api';
import './index.css';

let root: Root | null = null;

function render(props?: QiankunProps) {
  const { container } = props || {};
  const mountNode = container
    ? container.querySelector('#root')
    : document.getElementById('root');

  if (!mountNode) return;

  // qiankun 通过 props 传递 token，写入内存 + localStorage
  const token = (props as { token?: string })?.token;
  if (token) {
    setQiankunToken(token);
  }

  root = createRoot(mountNode);
  // 注意：antd v5 / @ant-design/pro-components 的工具栏 Tooltip 在 React.StrictMode 下
  // 会触发 findDOMNode 弃用告警（第三方组件内部行为，仅 dev 控制台噪声，不影响运行）。
  // 为获得干净的控制台，这里不启用 StrictMode。
  root.render(<App />);
}

renderWithQiankun({
  bootstrap() {},
  mount(props: QiankunProps) {
    // qiankun 挂载时将 token 写入内存 + localStorage
    const token = (props as { token?: string })?.token;
    if (token) {
      setQiankunToken(token);
    }
    render(props);
  },
  unmount() {
    // 卸载时清除内存 token 并销毁 React root
    setQiankunToken(null);
    if (root) {
      root.unmount();
      root = null;
    }
  },
});

if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  render();
}

export async function bootstrap() {}
export async function mount(props: QiankunProps) {
  const token = (props as { token?: string })?.token;
  if (token) {
    setQiankunToken(token);
  }
  render(props);
}
export async function unmount() {
  setQiankunToken(null);
  if (root) {
    root.unmount();
    root = null;
  }
}
