import { Sandbox, SandboxConfig, SandboxStatus } from '../types';

/**
 * iframe沙箱
 * 基于iframe实现的沙箱，提供最强的隔离性
 */
export class IframeSandbox implements Sandbox {
  private config: SandboxConfig & { strictMode?: boolean; appId?: string };
  private iframe: HTMLIFrameElement | null = null;
  private iframeWindow: Window | null = null;
  private iframeDocument: Document | null = null;
  private isLoaded: boolean = false;
  private isMounted: boolean = false;
  private resourceCache: Map<string, any> = new Map();
  private appExports: any = {};

  /**
   * 构造函数
   * @param config 沙箱配置
   */
  constructor(config: SandboxConfig) {
    this.config = config;
  }

  /**
   * 创建iframe元素
   */
  private createIframe(): HTMLIFrameElement {
    const iframe = document.createElement('iframe');
    
    // 设置iframe属性
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.style.position = 'absolute';
    iframe.style.left = '-9999px';
    iframe.style.border = 'none';
    iframe.style.opacity = '0';
    
    // 设置sandbox属性提供安全隔离
    iframe.sandbox.add(
      'allow-scripts',       // 允许执行脚本
      'allow-same-origin',   // 允许同源访问
      'allow-popups-to-escape-sandbox', // 允许弹出窗口
      'allow-forms'          // 允许表单提交
    );
    
    // 如果需要，添加更多权限
    if (this.config.strictMode === false) {
      iframe.sandbox.add('allow-modals');
      iframe.sandbox.add('allow-popups');
    }
    
    // 设置srcdoc，创建一个空的HTML文档
    iframe.srcdoc = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>${this.config.appId || 'Anonymous'} Sandbox</title>
        <style>body { margin: 0; padding: 0; overflow: hidden; }</style>
      </head>
      <body>
        <script>
          // 初始化微应用环境
          window.__bone_sandbox__ = true;
          window.__bone_app_id__ = '${this.config.appId || 'anonymous'}';
        </script>
      </body>
      </html>
    `;
    
    return iframe;
  }

  /**
   * 加载iframe
   */
  private async loadIframe(): Promise<void> {
    if (this.iframe && this.iframeWindow) {
      return;
    }

    return new Promise((resolve, reject) => {
      this.iframe = this.createIframe();
      
      this.iframe.onload = function(this: GlobalEventHandlers, ev: Event): any {
        if (this.iframe?.contentWindow && this.iframe?.contentDocument) {
          this.iframeWindow = this.iframe.contentWindow;
          this.iframeDocument = this.iframe.contentDocument;
          this.isLoaded = true;
          
          // 设置全局引用，方便调试
          (this.iframeWindow as any).__bone_sandbox_instance__ = this;
          
          resolve();
        } else {
          reject(new Error('Failed to initialize iframe sandbox'));
        }
        return undefined;
      };
      
      this.iframe.onerror = () => {
        reject(new Error('Iframe loading failed'));
      };
      
      // 将iframe添加到document中
      document.body.appendChild(this.iframe);
    });
  }

  /**
   * 在iframe中执行代码
   * @param code 要执行的代码
   */
  async execute(code: string): Promise<any> {
    await this.loadIframe();
    
    if (!this.iframeWindow) {
      throw new Error('Iframe window not available');
    }

    try {
      // 创建一个安全的执行环境
      const context = {
        window: this.iframeWindow,
        document: this.iframeDocument,
        location: this.iframeWindow.location,
        history: this.iframeWindow.history,
        self: this.iframeWindow,
        globalThis: this.iframeWindow,
        exports: this.appExports
      };

      // 使用Function构造函数执行代码
      const func = new Function(
        'window', 'document', 'location', 'history', 'self', 'globalThis', 'exports',
        `
          try {
            ${code}
            return exports;
          } catch (e) {
            console.error('Error in iframe sandbox:', e);
            throw e;
          }
        `
      );

      return func(
        context.window,
        context.document,
        context.location,
        context.history,
        context.self,
        context.globalThis,
        context.exports
      );
    } catch (error) {
      console.error('Error executing code in iframe sandbox:', error);
      throw error;
    }
  }

  /**
   * 评估资源
   * @param code 代码字符串
   */
  async eval(code: string): Promise<any> {
    await this.loadIframe();

    try {
      // 尝试从缓存获取
      if (this.resourceCache.has(code)) {
        return this.appExports;
      }
      
      await this.execute(code);
      this.resourceCache.set(code, true);
      
      // 返回应用导出
      return this.appExports;
    } catch (error) {
      console.error('Error evaluating resources in iframe sandbox:', error);
      throw error;
    }
  }

  /**
   * 挂载沙箱
   */
  mount(): void {
    this.isMounted = true;
    
    // 通知iframe内部应用已挂载
    if (this.iframeWindow) {
      try {
        this.iframeWindow.postMessage({
          type: '__bone_mount__',
          appId: this.config.appId || 'anonymous'
        }, '*');
      } catch (error) {
        console.warn('Failed to send mount message to iframe:', error);
      }
    }
  }

  /**
   * 卸载沙箱
   */
  unmount(): void {
    this.isMounted = false;
    
    // 通知iframe内部应用已卸载
    if (this.iframeWindow) {
      try {
        this.iframeWindow.postMessage({
          type: '__bone_unmount__',
          appId: this.config.appId || 'anonymous'
        }, '*');
      } catch (error) {
        console.warn('Failed to send unmount message to iframe:', error);
      }
    }
  }

  /**
   * 销毁沙箱
   */
  destroy(): void {
    this.unmount();
    
    // 清理缓存
    this.resourceCache.clear();
    this.appExports = {};
    
    // 移除iframe
    if (this.iframe && this.iframe.parentNode) {
      this.iframe.parentNode.removeChild(this.iframe);
    }
    
    this.iframe = null;
    this.iframeWindow = null;
    this.iframeDocument = null;
    this.isLoaded = false;
  }

  /**
   * 获取代理窗口对象
   */
  getProxyWindow(): Window {
    if (!this.iframeWindow) {
      throw new Error('Iframe window not available, please call load first');
    }
    return this.iframeWindow;
  }

  /**
   * 获取沙箱状态
   */
  getStatus(): SandboxStatus {
    return {
      appId: this.config.appId || '',
      loaded: this.isLoaded,
      mounted: this.isMounted,
      running: this.isLoaded && this.isMounted,
      activePropertiesCount: 0,
      resourceCount: this.resourceCache.size,
      sideEffectsCount: 0
    };
  }

  /**
   * 向iframe注入样式
   * @param css 样式字符串
   */
  injectStyle(css: string): void {
    if (!this.iframeDocument) {
      console.warn('Cannot inject style, iframe document not available');
      return;
    }

    const style = this.iframeDocument.createElement('style');
    style.textContent = css;
    this.iframeDocument.head.appendChild(style);
  }

  /**
   * 向iframe注入脚本
   * @param scriptUrl 脚本URL
   */
  async injectScript(scriptUrl: string): Promise<void> {
    await this.loadIframe();
    
    if (!this.iframeDocument) {
      throw new Error('Cannot inject script, iframe document not available');
    }

    return new Promise((resolve, reject) => {
      const script = this.iframeDocument!.createElement('script');
      script.src = scriptUrl;
      script.onload = resolve;
      script.onerror = reject;
      this.iframeDocument!.body.appendChild(script);
    });
  }

  /**
   * 获取iframe元素
   */
  getIframeElement(): HTMLIFrameElement | null {
    return this.iframe;
  }

  /**
   * 设置应用导出
   * @param exports 导出对象
   */
  setExports(exports: any): void {
    this.appExports = exports;
  }

  /**
   * 获取应用导出
   */
  getExports(): any {
    return this.appExports;
  }
}