import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import App from './App';
import { setQiankunToken } from '@bone/shared-services';
import { globalEventBus } from '@bone/core-event-bus';
import './index.css';

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
  const ctx = props as { token?: string; user?: unknown; permissions?: unknown; theme?: string } | undefined;
  const token = ctx?.token;
  if (token) {
    setQiankunToken(token);
  }
  // 如果 Shell 下发了完整全局上下文，写入 window 供 shared-services 读取
  if (ctx && (ctx.user || ctx.theme)) {
    (window as unknown as Record<string, unknown>).__BONE_GLOBAL_CONTEXT__ = {
      token: token ?? null,
      user: ctx.user ?? null,
      permissions: ctx.permissions ?? null,
      theme: ctx.theme ?? 'light',
      locale: 'zh-CN',
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
      <App />
    </React.StrictMode>,
  );
}

// 注册 qiankun 生命周期
renderWithQiankun({
  bootstrap() {
    // 首次加载时调用，仅一次
  },
  mount(props: QiankunProps) {
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
});

// 独立运行时直接渲染
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  subscribeGlobalContextChanges();
  render();
}

// 导出生命周期供 qiankun 识别（开发模式下 vite-plugin-qiankun 会自动处理）
export async function bootstrap() {}
export async function mount(props: QiankunProps) {
  initGlobalContext(props);
  render(props);
}
export async function unmount() {
  setQiankunToken(null);
  if (root) {
    root.unmount();
    root = null;
  }
}
