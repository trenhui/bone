import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import App from './App';
import { subscribeLocaleChange, currentLocale } from '@bone/shared-utils';
import { setQiankunToken } from '@bone/shared-services';
import { globalEventBus } from '@bone/core-event-bus';
import './index.css';
import { AppAntdProvider } from './components/AppAntdProvider';

/**
 * 订阅 Shell 经 core/event-bus 广播的全局上下文变更（主题/语言），
 * 更新 window.__BONE_GLOBAL_CONTEXT__ 并派发自定义事件供 App 响应。
 */
function subscribeGlobalContextChanges() {
  const bus =
    (window as unknown as { __BONE_EVENT_BUS__?: typeof globalEventBus }).__BONE_EVENT_BUS__ || globalEventBus;
  bus.on('bone:theme:change', (data: { theme?: string; locale?: string }) => {
    const ctx = (window as unknown as { __BONE_GLOBAL_CONTEXT__?: Record<string, unknown> }).__BONE_GLOBAL_CONTEXT__;
    const next = { ...(ctx ?? {}), ...((data ?? {}) as Record<string, unknown>) };
    (window as unknown as Record<string, unknown>).__BONE_GLOBAL_CONTEXT__ = next;
    window.dispatchEvent(new CustomEvent('bone:global:context', { detail: next }));
  });
}

let root: Root | null = null;

/**
 * 从 qiankun props 中提取全局上下文信息。
 * 兼容旧格式（只传 token）和新格式（传完整 GlobalContext）。
 */
function initGlobalContext(props?: QiankunProps) {
  const ctx = props as
    | { token?: string; user?: unknown; permissions?: unknown; theme?: string; locale?: string }
    | undefined;
  const token = ctx?.token;
  if (token) {
    setQiankunToken(token);
  }
  // 如果 Shell 下发了完整全局上下文，写入 window 供 shared-services 读取。
  // locale 必须以 Shell 下发值为准：硬编码 'zh-CN' 会让切英文后本应用仍是中文（详设 §2.11）。
  if (ctx && (ctx.user || ctx.theme)) {
    (window as unknown as Record<string, unknown>).__BONE_GLOBAL_CONTEXT__ = {
      token: token ?? null,
      user: ctx.user ?? null,
      permissions: ctx.permissions ?? null,
      theme: ctx.theme ?? 'light',
      locale: ctx.locale ?? currentLocale(),
    };
  }
}

function render(props?: QiankunProps) {
  const { container } = props || {};
  const mountNode = container
    ? container.querySelector('#root')
    : document.getElementById('root');

  if (!mountNode) return;

  initGlobalContext(props);

  root = createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <AppAntdProvider>
      <App />
    </AppAntdProvider>
    </React.StrictMode>,
  );
}

/**
 * qiankun 生命周期**唯一真源**（详设 §2.11）。
 *
 * <p>整改前这里有两份并存且**不等价**的实现：`renderWithQiankun({...})` 里的 mount 会
 * `subscribeLocaleChange()` + `subscribeGlobalContextChanges()`，而底部手写的 `export async function mount`
 * 只做 `initGlobalContext` + `render`。dev 的 JS-entry 加载器（`public/qiankun-entry.js`）走的是后者，
 * 于是「切语言/换主题无反应」这类故障只在 dev 复现、prod 不复现，排查成本极高。
 *
 * <p>为何不能只留 `renderWithQiankun`：该 helper 只是把 lifecycle 挂到
 * `window.moudleQiankunAppLifeCycles[qiankunName]`（见 vite-plugin-qiankun/dist/helper.js），
 * **它本身不导出生命周期函数**；dev 的 JS-entry 依赖本模块的命名导出。
 * 因此正确做法是「一份对象、两处复用」，而不是删掉其中一份。
 */
const lifecycle = {
  bootstrap() {
    // 首次加载时调用，仅一次
  },
  mount(props: QiankunProps) {
    subscribeLocaleChange();
    initGlobalContext(props);
    subscribeGlobalContextChanges();
    render(props);
  },
  unmount() {
    setQiankunToken(null);
    if (root) {
      root.unmount();
      root = null;
    }
  },
};

renderWithQiankun(lifecycle);

// 独立运行时直接渲染
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  subscribeLocaleChange();
  subscribeGlobalContextChanges();
  render();
}

// 导出生命周期供 dev JS-entry 识别（与上面共用同一份实现，禁止再写第二份逻辑）
export async function bootstrap(): Promise<void> {
  return lifecycle.bootstrap();
}
export async function mount(props: QiankunProps): Promise<void> {
  return lifecycle.mount(props);
}
export async function unmount(): Promise<void> {
  return lifecycle.unmount();
}
