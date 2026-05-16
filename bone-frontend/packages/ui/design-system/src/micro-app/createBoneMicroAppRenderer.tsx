import React, { StrictMode } from 'react';
import type { Root } from 'react-dom/client';
import { createRoot } from 'react-dom/client';

import { BoneAppProvider } from '../components/BoneAppProvider';
import type { Theme } from '../theme/types';
import { readStoredTheme } from '../theme/resolveThemeMode';

export interface BoneMicroAppProps {
  container?: HTMLElement;
  user?: Record<string, unknown>;
  themeMode?: Theme;
  name?: string;
}

type AppComponent<P> = React.ComponentType<P>;

/**
 * 统一微应用 bootstrap / mount / unmount，注入 Bone 主题与 Ant Design ConfigProvider。
 */
export function createBoneMicroAppRenderer<P extends { user?: Record<string, unknown> }>(
  App: AppComponent<P>,
  appName: string,
) {
  let root: Root | null = null;

  function render(props: BoneMicroAppProps) {
    const { container, user, themeMode } = props;
    const rootElement =
      container?.querySelector('#root') ??
      container ??
      document.getElementById('root');
    if (!rootElement) {
      throw new Error(`[${appName}] mount container not found`);
    }
    root = createRoot(rootElement);
    root.render(
      <StrictMode>
        <BoneAppProvider themeMode={themeMode ?? readStoredTheme()}>
          <App {...({ user } as P)} />
        </BoneAppProvider>
      </StrictMode>,
    );
  }

  async function bootstrap() {
    console.log(`[${appName}] bootstraped`);
  }

  async function mount(props: BoneMicroAppProps) {
    console.log(`[${appName}] mounted`, props);
    render(props);
  }

  async function unmount() {
    if (root) {
      root.unmount();
      root = null;
    }
    console.log(`[${appName}] unmounted`);
  }

  return { render, bootstrap, mount, unmount };
}
