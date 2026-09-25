import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import App from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
});

let root: Root | null = null;

function render(props?: QiankunProps) {
  const { container } = props || {};
  const mountNode = container
    ? container.querySelector('#root')
    : document.getElementById('root');

  if (!mountNode) return;

  const token = (props as { token?: string })?.token;
  if (token) {
    localStorage.setItem('token', token);
  }

  root = createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </React.StrictMode>,
  );
}

renderWithQiankun({
  bootstrap() {},
  mount(props: QiankunProps) {
    const token = (props as { token?: string })?.token;
    if (token) {
      localStorage.setItem('token', token);
    }
    render(props);
  },
  unmount() {
    if (root) {
      root.unmount();
      root = null;
    }
  },
});

if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  render();
}

export async function bootstrap(): Promise<void> {}
export async function mount(props: QiankunProps): Promise<void> {
  const token = (props as { token?: string })?.token;
  if (token) {
    localStorage.setItem('token', token);
  }
  render(props);
}
export async function unmount(): Promise<void> {
  if (root) {
    root.unmount();
    root = null;
  }
}
