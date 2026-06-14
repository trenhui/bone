/**
 * Qiankun 微应用 Dev Loader — 替代 qiankun 自带的 importEntry。
 *
 * qiankun 在 dev 模式下通过 fetch 微应用 HTML，然后用 new Function() / eval()
 * 执行其中的 <script> 内容。但微应用使用了 ES Module（import/export），
 * eval() 不支持 ES Module，所以报错。
 *
 * 本方案：直接 fetch 微应用的 JS bundle（Vite 实时编译），
 * 用 <script type="module"> 执行，模块执行后 window['bone-iam-app'] 被赋值，
 * qiankun 据此完成挂载/卸载。
 */
declare global {
  interface Window {
    [key: string]: unknown;
  }
}

// qiankun 生命周期接口定义
export interface MicroAppLifecycle {
  bootstrap?: () => Promise<void>;
  mount?: (props: { container: string; name: string }) => Promise<void>;
  unmount?: (props: { container: string; name: string }) => Promise<void>;
}

export interface MicroAppConfig {
  name: string;
  entry: string;      // e.g. 'http://localhost:3003'
  container: string;   // e.g. '#micro-app-container'
}

export async function loadMicroApp(config: MicroAppConfig): Promise<MicroAppLifecycle | null> {
  const { name, entry, container } = config;

  try {
    // 1. 创建 Shadow DOM / 隔离容器
    const containerEl = document.querySelector(container) as HTMLElement;
    if (!containerEl) {
      console.warn(`[qiankun-dev] Container ${container} not found`);
      return null;
    }

    // 2. 直接 fetch 微应用的 qiankun-entry.js（Vite dev server 实时转译）
    const scriptUrl = `${entry}/qiankun-entry.js`;
    const resp = await fetch(scriptUrl);
    if (!resp.ok) {
      throw new Error(`Failed to fetch ${scriptUrl}: ${resp.status}`);
    }

    // 3. 用 <script type="module"> 执行（支持 import/export）
    const mod = await import(/* @vite-ignore */ scriptUrl);

    const lifecycle: MicroAppLifecycle = {
      bootstrap: mod.bootstrap ?? (async () => {}),
      mount: mod.mount ?? (async () => {}),
      unmount: mod.unmount ?? (async () => {}),
    };

    // 4. 通知 qiankun 有新的 lifecycle
    //    window[name] 需要被 qiankun 感知，这里手动触发 mount
    console.log(`[qiankun-dev] ${name} loaded, mounting...`);
    if (lifecycle.mount) {
      await lifecycle.mount({
        container,
        name,
      });
    }

    return lifecycle;
  } catch (err) {
    console.error(`[qiankun-dev] Failed to load ${name}:`, err);
    return null;
  }
}
