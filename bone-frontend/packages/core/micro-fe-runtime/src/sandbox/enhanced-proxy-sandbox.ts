import { Sandbox, SandboxConfig, SandboxStatus, ResourceLimits, SandboxSecurityPolicy } from '../types';

// 从default-security-policy.ts导入安全策略

export class EnhancedProxySandbox implements Sandbox {
  private appId: string;
  private securityPolicy: SandboxSecurityPolicy;
  private sandboxGlobal: Record<string, any> = {};
  private originalWindowProperties: Set<string>;
  private proxyWindow: any; // 使用any类型避免TypeScript错误
  private running: boolean = false;
  private isMounted: boolean = false;

  constructor(config: SandboxConfig) {
    this.appId = config.appId;
    this.securityPolicy = config.securityPolicy || {
      checkAccess: () => 'allow',
      checkWrite: () => true,
      checkDelete: () => true
    };
    this.originalWindowProperties = new Set(Object.keys(window));
    this.proxyWindow = this.createProxyWindow();
    this.preloadCommonGlobals();
  }

  getProxyWindow(): Window {
    return this.proxyWindow;
  }

  /**
   * 挂载沙箱
   */
  mount(): void {
    this.isMounted = true;
  }

  /**
   * 卸载沙箱
   */
  unmount(): void {
    this.isMounted = false;
    this.cleanupEventListeners();
    this.clearSideEffects();
  }

  /**
   * 清理事件监听器
   */
  private cleanupEventListeners(): void {
    // 简化实现，因为已经移除了eventListeners属性
  }

  async execute(code: string): Promise<any> {
    this.startExecution();
    try {
      const moduleExports: any = {};
      
      // 创建一个安全的执行上下文
      const context = {
        window: this.proxyWindow,
        document: this.createProxyDocument(),
        location: this.createProxyLocation(),
        history: this.createProxyHistory(),
        moduleExports
      };

      // 执行代码
      const result = await this.safeEval(code, context);
      
      // 检查资源使用情况
      this.checkResourceUsage();
      
      return result || moduleExports;
    } catch (error) {
      console.error(`Error executing code in sandbox ${this.appId}:`, error);
      throw error;
    } finally {
      this.endExecution();
    }
  }

  destroy(): void {
    // 清理沙箱环境
    this.running = false;
    this.sandboxGlobal = {};
    this.clearSideEffects();
  }

  getStatus(): SandboxStatus {
    return {
      appId: this.appId,
      loaded: true,
      mounted: this.isMounted,
      running: this.running,
      activePropertiesCount: Object.keys(this.sandboxGlobal).length,
      resourceCount: 0, // 简单实现，实际应该计算资源数量
      sideEffectsCount: 0
    };
  }

  private startExecution(): void {
    this.running = true;
  }

  private endExecution(): void {
    this.running = false;
  }

  private checkResourceUsage(): void {
    // 简化实现，移除未使用的逻辑
  }

  // 移除未使用的calculateMemoryUsage方法

  private createProxyWindow(): any {
    const sandbox = this;
    
    return new Proxy(window, {
      get(target: Window, prop: string | symbol, receiver: any): any {
        // 安全检查
        const checkResult = sandbox.securityPolicy.checkAccess('window', prop);
        const propStr = String(prop);
        
        if (checkResult === 'block') {
          throw new Error(`Access denied to ${propStr}`);
        }

        // 优先从沙箱全局获取
        if (propStr in sandbox.sandboxGlobal) {
          return sandbox.sandboxGlobal[propStr];
        }

        // 从原始window获取
        const value = Reflect.get(target, prop, receiver);

        // 包装危险对象
        if (checkResult === 'wrap') {
          return sandbox.wrapDangerousObject(value, propStr);
        }

        // 对于函数，绑定到原始上下文
        if (typeof value === 'function') {
          return value.bind(target);
        }

        return value;
      },

      set(target: Window, prop: string | symbol, value: any, receiver: any): boolean {
        // 安全检查
        if (!sandbox.securityPolicy.checkWrite('window', prop)) {
          throw new Error(`Write denied to ${String(prop)}`);
        }

        const propStr = String(prop);
        // 检查是否是原始属性
        if (sandbox.originalWindowProperties.has(propStr)) {
          // 对于原始属性，只在沙箱内设置
          sandbox.sandboxGlobal[propStr] = value;
          sandbox.sideEffectsCount++;
          return true;
        }

        // 对于新属性，设置到沙箱全局
        sandbox.sandboxGlobal[propStr] = value;
        return true;
      },

      has(target: Window, prop: string | symbol): boolean {
        return String(prop) in sandbox.sandboxGlobal || prop in target;
      },

      deleteProperty(target: Window, prop: string | symbol): boolean {
        // 安全检查
        if (!sandbox.securityPolicy.checkDelete('window', prop)) {
          throw new Error(`Delete denied to ${String(prop)}`);
        }

        const propStr = String(prop);
        // 只能删除沙箱内的属性
        if (propStr in sandbox.sandboxGlobal) {
          delete sandbox.sandboxGlobal[propStr];
          return true;
        }

        return false;
      }
    });
  }

  private createProxyDocument(): Document {
    // 创建document代理，类似window代理的逻辑
    const sandbox = this;
    const sandboxDocument: Record<string, any> = {};

    return new Proxy(document, {
      get(target: Document, prop: string | symbol, receiver: any): any {
        // 安全检查
        const checkResult = sandbox.securityPolicy.checkAccess('document', prop);
        const propStr = String(prop);
        
        if (checkResult === 'block') {
          throw new Error(`Access denied to document.${propStr}`);
        }

        if (propStr in sandboxDocument) {
          return sandboxDocument[propStr];
        }

        const value = Reflect.get(target, prop, receiver);

        if (checkResult === 'wrap') {
          return sandbox.wrapDangerousObject(value, `document.${propStr}`);
        }

        if (typeof value === 'function') {
          return value.bind(target);
        }

        return value;
      },

      set(target: Document, prop: string | symbol, value: any, receiver: any): boolean {
        if (!sandbox.securityPolicy.checkWrite('document', prop)) {
          throw new Error(`Write denied to document.${String(prop)}`);
        }

        const propStr = String(prop);
        sandboxDocument[propStr] = value;
        sandbox.sideEffectsCount++;
        return true;
      }
    });
  }

  private createProxyLocation(): Location {
    // 创建location代理
    const sandbox = this;

    return new Proxy(location, {
      get(target: Location, prop: string | symbol, receiver: any): any {
        if (prop === 'href' || prop === 'pathname' || prop === 'search' || prop === 'hash') {
          return Reflect.get(target, prop, receiver);
        }
        return Reflect.get(target, prop, receiver);
      },

      set(target: Location, prop: string | symbol, value: any, receiver: any): boolean {
        // 拦截location修改，避免微应用直接修改整个页面的URL
        console.warn(`Location.${String(prop)} cannot be modified directly in sandbox`);
        return false;
      }
    });
  }

  private createProxyHistory(): History {
    // 创建history代理
    const sandbox = this;

    return new Proxy(history, {
      get(target: History, prop: string | symbol, receiver: any): any {
        const value = Reflect.get(target, prop, receiver);
        
        if (typeof value === 'function' && (prop === 'pushState' || prop === 'replaceState')) {
          return function(...args: any[]) {
            console.warn(`History.${String(prop)} is intercepted in sandbox`);
            // 可以在这里添加自定义逻辑
            return value.apply(target, args);
          };
        }
        
        return value;
      }
    });
  }

  private wrapDangerousObject(obj: any, propName: string): any {
    // 包装危险对象，如eval、Function构造函数等
    if (typeof obj !== 'function') {
      return obj;
    }

    const dangerousFunctions = ['eval', 'Function', 'setTimeout', 'setInterval'];
    const functionName = propName.split('.').pop() || '';

    if (dangerousFunctions.includes(functionName)) {
      return (...args: any[]) => {
        // 在这里可以添加额外的安全检查
        console.warn(`Executing dangerous function: ${functionName} in sandbox ${this.appId}`);
        return obj(...args);
      };
    }
    
    // 特殊处理事件监听器相关方法
    if (functionName === 'addEventListener' && this.isMounted) {
      return this.wrapAddEventListener(obj as Function);
    } else if (functionName === 'removeEventListener' && this.isMounted) {
      return this.wrapRemoveEventListener(obj as Function);
    }

    return obj;
  }
  
  /**
   * 包装addEventListener方法，跟踪事件监听器
   */
  private wrapAddEventListener(originalMethod: Function): Function {
    const self = this;
    return function(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void {
      // 调用原始方法
      originalMethod.apply(window, arguments);
      
      // 记录事件监听器
      if (!self.eventListeners.has(type)) {
        self.eventListeners.set(type, []);
      }
      self.eventListeners.get(type)!.push({ type, listener, options });
    };
  }
  
  /**
   * 包装removeEventListener方法
   */
  private wrapRemoveEventListener(originalMethod: Function): Function {
    const self = this;
    return function(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void {
      // 调用原始方法
      originalMethod.apply(window, arguments);
      
      // 从记录中移除
      const listeners = self.eventListeners.get(type);
      if (listeners) {
        const index = listeners.findIndex(l => l.listener === listener);
        if (index > -1) {
          listeners.splice(index, 1);
        }
      }
    };
  }

  private safeEval(code: string, context: Record<string, any>): any {
    // 安全的代码执行
    const safeCode = this.preprocessCode(code);
    
    // 使用Function构造函数执行代码
    const func = new Function(
      'window', 'document', 'location', 'history', 'moduleExports',
      `
        try {
          ${safeCode}
          return moduleExports;
        } catch (e) {
          console.error('Error in sandbox:', e);
          throw e;
        }
      `
    );

    return func(
      context.window,
      context.document,
      context.location,
      context.history,
      context.moduleExports
    );
  }

  private preprocessCode(code: string): string {
    // 预处理代码，移除潜在的恶意代码
    // 这是一个简化的实现，实际项目中可能需要更复杂的代码分析
    
    // 移除可能的危险模式
    const dangerousPatterns = [
      /\beval\(/g,
      /\bnew\s+Function\(/g,
      /\bdocument\.write\(/g
    ];

    let processedCode = code;
    dangerousPatterns.forEach(pattern => {
      processedCode = processedCode.replace(pattern, (match) => {
        console.warn(`Potentially dangerous pattern detected: ${match}`);
        return match;
      });
    });

    return processedCode;
  }

  private clearSideEffects(): void {
    // 清理沙箱产生的副作用
    console.log(`Clearing side effects for sandbox ${this.appId || 'unknown'}`);
    this.sideEffectsCount = 0;
    // 清空沙箱全局对象
    Object.keys(this.sandboxGlobal).forEach(key => {
      delete this.sandboxGlobal[key];
    });
  }

  private preloadCommonGlobals(): void {
    // 预加载常用的全局对象，避免每次都从原始window获取
    const commonGlobals = ['Math', 'JSON', 'Date', 'Array', 'Object', 'String', 'Number', 'Boolean'];
    
    commonGlobals.forEach(globalName => {
      if (globalName in window) {
        this.sandboxGlobal[globalName] = Object.create(window[globalName]);
      }
    });
  }
  
  /**
   * 评估资源
   * @param code 代码字符串
   */
  async eval(code: string): Promise<any> {
    try {
      return await this.execute(code);
    } catch (error) {
      console.error('Error evaluating code in sandbox:', error);
      throw error;
    }
  }
}