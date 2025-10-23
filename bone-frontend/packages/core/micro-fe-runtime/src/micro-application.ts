import { Sandbox, SandboxOptions } from './sandbox/enhanced-proxy-sandbox';
import { EnhancedProxySandbox } from './sandbox/enhanced-proxy-sandbox';
import { AppStatus } from '../types';

export interface MicroAppConfig {
  name: string;
  entry: string;
  container: string | HTMLElement;
  activeWhen?: string | RegExp | ((path: string) => boolean);
  props?: Record<string, any>;
  sandbox?: boolean | SandboxOptions;
  metadata?: {
    version?: string;
    permissions?: string[];
    author?: string;
  };
}

interface MicroAppLifecycle {
  bootstrap?: () => Promise<void>;
  mount?: (props: Record<string, any>) => Promise<void>;
  unmount?: () => Promise<void>;
  update?: (props: Record<string, any>) => Promise<void>;
}

export class MicroApplication {
  private appConfig: MicroAppConfig;
  private status: AppStatus = 'NOT_LOADED';
  private sandbox?: Sandbox;
  private lifecycle: MicroAppLifecycle = {};
  private containerElement: HTMLElement | null = null;
  private appWindow?: Window;
  private bootstrapPromise?: Promise<void>;
  private mountPromise?: Promise<void>;
  private unmountPromise?: Promise<void>;

  constructor(config: MicroAppConfig) {
    this.appConfig = config;
    this.containerElement = this.getContainerElement(config.container);
    this.initializeSandbox();
  }

  get name(): string {
    return this.appConfig.name;
  }

  get currentStatus(): AppStatus {
    return this.status;
  }

  get metadata() {
    return this.appConfig.metadata;
  }

  private getContainerElement(container: string | HTMLElement): HTMLElement | null {
    if (typeof container === 'string') {
      return document.querySelector(container) || null;
    }
    return container;
  }

  private initializeSandbox(): void {
    if (this.appConfig.sandbox === false) {
      return;
    }

    const sandboxOptions = typeof this.appConfig.sandbox === 'object' 
      ? this.appConfig.sandbox 
      : {};
    
    this.sandbox = new EnhancedProxySandbox({
      appId: this.appConfig.name,
      ...sandboxOptions
    });
    this.appWindow = this.sandbox.getProxyWindow();
  }

  async load(): Promise<void> {
    if (this.status !== 'NOT_LOADED') {
      return;
    }

    this.status = 'LOADING';

    try {
      // 1. 创建容器元素
      if (!this.containerElement) {
        throw new Error(`Container not found for app: ${this.appConfig.name}`);
      }

      // 2. 加载入口文件
      const script = await this.loadEntryScript(this.appConfig.entry);
      
      // 3. 获取生命周期方法
      const appExports = await this.evaluateEntryScript(script);
      this.lifecycle = appExports || {};

      this.status = 'NOT_MOUNTED';
    } catch (error) {
      console.error(`Failed to load app ${this.appConfig.name}:`, error);
      this.status = 'LOAD_ERROR';
      throw error;
    }
  }

  private async loadEntryScript(entry: string): Promise<string> {
    try {
      const response = await fetch(entry);
      if (!response.ok) {
        throw new Error(`Failed to load entry script: ${response.status} ${response.statusText}`);
      }
      return await response.text();
    } catch (error) {
      throw new Error(`Failed to fetch entry script: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private async evaluateEntryScript(script: string): Promise<MicroAppLifecycle> {
    if (this.sandbox) {
      return await this.sandbox.execute(script);
    }
    
    // 创建一个临时的执行上下文
    const tempContext = {
      window: window,
      document: document,
      location: location,
      history: history,
      ...globalThis
    };

    const enhancedScript = `
      (function(window, document, location, history) {
        ${script}
      })(tempContext.window, tempContext.document, tempContext.location, tempContext.history);
    `;

    try {
      // 执行脚本并获取导出的生命周期方法
      const moduleExports: any = {};
      (new Function('tempContext', 'moduleExports', enhancedScript))(tempContext, moduleExports);
      return moduleExports;
    } catch (error) {
      throw new Error(`Failed to evaluate entry script: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  async mount(props?: Record<string, any>): Promise<void> {
    if (this.status === 'LOADING') {
      await this.bootstrapPromise;
    }

    if (this.status !== 'NOT_MOUNTED') {
      return;
    }

    this.status = 'MOUNTING';

    const finalProps = {
      ...this.appConfig.props,
      ...props
    };

    try {
      // 执行 bootstrap 生命周期
      if (!this.bootstrapPromise && this.lifecycle.bootstrap) {
        this.bootstrapPromise = this.lifecycle.bootstrap();
      }
      if (this.bootstrapPromise) {
        await this.bootstrapPromise;
      }

      // 执行 mount 生命周期
      if (this.lifecycle.mount) {
        this.mountPromise = this.lifecycle.mount(finalProps);
        await this.mountPromise;
      }

      this.status = 'MOUNTED';
    } catch (error) {
      console.error(`Failed to mount app ${this.appConfig.name}:`, error);
      this.status = 'MOUNT_ERROR';
      throw error;
    }
  }

  async unmount(): Promise<void> {
    if (this.status !== 'MOUNTED') {
      return;
    }

    this.status = 'UNMOUNTING';

    try {
      // 执行 unmount 生命周期
      if (this.lifecycle.unmount) {
        this.unmountPromise = this.lifecycle.unmount();
        await this.unmountPromise;
      }

      // 清理沙箱
      if (this.sandbox) {
        this.sandbox.destroy();
      }

      // 清空容器
      if (this.containerElement) {
        this.containerElement.innerHTML = '';
      }

      this.status = 'NOT_MOUNTED';
    } catch (error) {
      console.error(`Failed to unmount app ${this.appConfig.name}:`, error);
      this.status = 'UNMOUNT_ERROR';
      throw error;
    }
  }

  async update(props?: Record<string, any>): Promise<void> {
    if (this.status !== 'MOUNTED') {
      return;
    }

    const finalProps = {
      ...this.appConfig.props,
      ...props
    };

    try {
      if (this.lifecycle.update) {
        await this.lifecycle.update(finalProps);
      }
    } catch (error) {
      console.error(`Failed to update app ${this.appConfig.name}:`, error);
      throw error;
    }
  }

  isActive(path: string): boolean {
    const activeWhen = this.appConfig.activeWhen;
    if (!activeWhen) {
      return false;
    }

    if (typeof activeWhen === 'string') {
      return path.startsWith(activeWhen);
    }

    if (activeWhen instanceof RegExp) {
      return activeWhen.test(path);
    }

    if (typeof activeWhen === 'function') {
      return activeWhen(path);
    }

    return false;
  }

  destroy(): void {
    if (this.status === 'MOUNTED' || this.status === 'MOUNTING') {
      this.unmount().catch(console.error);
    }

    // 清理引用
    this.containerElement = null;
    this.sandbox = undefined;
    this.appWindow = undefined;
  }
}