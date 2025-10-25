import { MicroAppConfig, AppStatus, AppLifecycleHooks, RouteRule, ErrorContext } from './types';
import { getEventBus } from './shared/event-bus';
import { Sandbox } from './types';
import { SandboxFactory } from './sandbox';
import { ErrorHandler } from './error-handler';
import { PerformanceMonitor } from './performance-monitor';
import { SecurityPolicyEnforcer } from './security-policy-enforcer';
import { ResourceCache } from './resource-cache';
import { AppLifecycle } from './app-lifecycle';

// 运行时版本常量
export const BONE_RUNTIME_VERSION = '1.0.0';

// 全局环境变量检测
export const MICRO_FRONTEND_ENVIRONMENT = {
  isBone: typeof window !== 'undefined' && !!window.__BONE_MICRO_FRONTEND__,
  isQiankun: typeof window !== 'undefined' && !!window.__INJECTED_PUBLIC_PATH_BY_QIANKUN__,
  isWujie: typeof window !== 'undefined' && !!window.__POWERED_BY_WUJIE__,
  isMicroApp: typeof window !== 'undefined' && !!window.__MICRO_APP_ENVIRONMENT__
};

/**
 * 微前端应用核心类
 * 管理应用的完整生命周期、沙箱隔离、性能监控等
 */
export class MicroApplication {
  private status: AppStatus = 'NOT_LOADED';
  private sandbox: Sandbox | null = null;
  private lifecycle: AppLifecycle;
  private errorHandler: ErrorHandler;
  private performanceMonitor: PerformanceMonitor;
  private securityEnforcer: SecurityPolicyEnforcer;
  private resourceCache: ResourceCache;
  private eventBus = getEventBus();
  private containerElement: HTMLElement | null = null;

  /**
   * 获取当前应用状态
   */
  get currentStatus(): AppStatus {
    return this.status;
  }

  /**
   * 获取应用配置
   */
  get config(): MicroAppConfig {
    return this._config;
  }
  
  /**
   * 私有配置引用，避免getter中的递归调用
   */
  private _config: MicroAppConfig;

  /**
   * 构造函数
   * @param config 应用配置
   */
  constructor(config: MicroAppConfig) {
    // 存储配置引用，避免getter中的递归调用
    this._config = config;
    
    // 初始化各个管理器
    this.lifecycle = new AppLifecycle(this);
    this.errorHandler = new ErrorHandler({ appId: config.id }); // 使用正确的配置对象格式
    this.performanceMonitor = new PerformanceMonitor(config.id);
    this.securityEnforcer = new SecurityPolicyEnforcer(config.metadata?.permissions || []);
    this.resourceCache = new ResourceCache(config.id);
    
    // 初始化容器元素
    this.initializeContainer(config.container);
    
    // 记录应用创建
    this.performanceMonitor.record('create', 0);
  }
  
  /**
   * 初始化应用容器元素
   * 处理字符串选择器或直接的DOM元素作为容器
   * 验证容器的有效性并准备应用挂载
   * @param container 容器选择器或DOM元素，可选
   */
  private initializeContainer(container?: string | HTMLElement): void {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    
    try {
      // 如果没有提供容器，使用默认行为
      if (!container) {
        console.log(`${loggerPrefix} No container provided during initialization`);
        return;
      }
      
      console.log(`${loggerPrefix} Initializing container of type: ${typeof container}`);
      
      if (typeof container === 'string') {
        // 处理字符串选择器
        console.log(`${loggerPrefix} Querying container with selector: ${container}`);
        this.containerElement = document.querySelector(container);
        
        if (!this.containerElement) {
          console.warn(`${loggerPrefix} Could not find container element with selector: ${container}`);
          this.containerElement = null;
        } else if (!(this.containerElement instanceof HTMLElement)) {
          console.warn(`${loggerPrefix} Container element is not a valid HTMLElement`);
          this.containerElement = null;
        } else {
          console.log(`${loggerPrefix} Container element found successfully`);
        }
      } else if (container instanceof HTMLElement) {
        // 直接使用DOM元素
        this.containerElement = container;
        console.log(`${loggerPrefix} Container element provided directly`);
      } else {
        console.warn(`${loggerPrefix} Invalid container type: expected string selector or HTMLElement`);
        this.containerElement = null;
      }
    } catch (error) {
      console.error(`${loggerPrefix} Error during container initialization:`, error);
      this.errorHandler.handle(error as Error, { 
        phase: 'init', 
        appId: this._config.id, 
        additionalInfo: { 
          action: 'initializeContainer', 
          containerType: typeof container,
          containerValue: typeof container === 'string' ? container : '[HTMLElement]'
        } 
      });
      this.containerElement = null;
    }
  }

  /**
   * 根据当前路由路径检查应用是否应该被激活
   * 支持字符串前缀匹配、正则表达式测试和自定义函数判断
   * @param path 当前路由路径
   * @returns 布尔值，表示应用是否应该被激活
   * @throws 当路由规则类型不正确时抛出错误
   */
  isActive(path: string): boolean {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    const rule = this.config.activeRule;
    
    try {
      console.log(`${loggerPrefix} Checking if app should be active for path: ${path}`);
      
      // 确保路由规则存在
      if (!rule) {
        console.warn(`${loggerPrefix} No active rule defined, defaulting to inactive`);
        return false;
      }
      
      // 根据规则类型执行不同的匹配逻辑
      if (typeof rule === 'string') {
        // 字符串规则：精确匹配或前缀匹配
        const isExactMatch = path === rule;
        const isPrefixMatch = path.startsWith(`${rule}/`);
        const result = isExactMatch || isPrefixMatch;
        console.log(`${loggerPrefix} String rule check result: ${result} (exact: ${isExactMatch}, prefix: ${isPrefixMatch})`);
        return result;
      } else if (rule instanceof RegExp) {
        // 正则表达式规则：测试匹配
        const result = rule.test(path);
        console.log(`${loggerPrefix} RegExp rule check result: ${result}`);
        return result;
      } else if (typeof rule === 'function') {
        // 函数规则：执行函数判断
        const result = rule(path);
        console.log(`${loggerPrefix} Function rule check result: ${result}`);
        return result;
      } else {
        // 无效规则类型
        console.error(`${loggerPrefix} Invalid active rule type: ${typeof rule}`);
        throw new Error(`Invalid active rule type for app ${this._config.id}: expected string, RegExp or function`);
      }
    } catch (error) {
      console.error(`${loggerPrefix} Error during activation check:`, error);
      this.errorHandler.handle(error as Error, {
        phase: 'activation',
        appId: this._config.id,
        additionalInfo: {
          path: path,
          ruleType: typeof rule,
          hasRule: !!rule
        }
      });
      return false;
    }
  }

  /**
   * 加载应用资源
   * 并行加载所有配置的入口资源，支持多种资源类型
   * @returns {Promise<any[]>} 成功加载的资源数组
   * @throws {Error} 当所有资源加载失败时抛出错误
   */
  private async loadResources(): Promise<any[]> {
    try {
      // 确保入口资源是数组格式
      const entryPoints = Array.isArray(this._config.entry) ? this._config.entry : [this._config.entry];
      const loadedResources: any[] = [];
      const loadPromises: Promise<any>[] = [];

      // 并行加载所有资源以提高性能
      for (const entryPoint of entryPoints) {
        const loadPromise = this.loadSingleResource(entryPoint)
          .then(resource => {
            loadedResources.push(resource);
            return resource;
          })
          .catch(error => {
            // 记录单个资源加载失败，但继续尝试其他资源
            const resourceError = new Error(`Failed to load resource: ${entryPoint}, ${error instanceof Error ? error.message : String(error)}`);
            this.errorHandler.handle(resourceError, {
              phase: 'resourceLoad',
              appId: this._config.id,
              additionalInfo: { 
                resourceUrl: entryPoint,
                isSingleResourceFailure: true
              }
            });
            return null; // 返回null表示加载失败
          });
        
        loadPromises.push(loadPromise);
      }

      await Promise.all(loadPromises);
      
      // 检查是否所有资源都加载失败
      const successfulResources = loadedResources.filter(resource => resource !== null);
      if (successfulResources.length === 0) {
        const failureError = new Error('Failed to load all application resources');
        this.errorHandler.handle(failureError, {
          phase: 'resourceLoad',
          appId: this._config.id,
          additionalInfo: { entryCount: entryPoints.length }
        });
        throw failureError;
      }

      return successfulResources;
    } catch (error) {
      this.errorHandler.handle(error as Error, {
        phase: 'resourceLoad',
        appId: this._config.id,
        appInfo: this._config
      });
      throw error;
    }
  }
  
  /**
   * 加载单个应用资源
   * @param {string} resourceUrl - 资源的URL地址
   * @returns {Promise<any>} 加载的资源内容
   * @throws {Error} 当资源URL无效或加载失败时抛出错误
   */
  private async loadSingleResource(resourceUrl: string): Promise<any> {
    // 验证资源URL的有效性
    if (!resourceUrl || typeof resourceUrl !== 'string' || resourceUrl.trim() === '') {
      const invalidUrlError = new Error(`Invalid resource URL: ${String(resourceUrl)}`);
      this.errorHandler.handle(invalidUrlError, {
        phase: 'resourceLoad',
        appId: this._config.id,
        additionalInfo: { resourceUrl, validationError: true }
      });
      throw invalidUrlError;
    }
    
    // 带超时的资源获取函数
    const fetchWithTimeout = (url: string, options: RequestInit = {}, timeoutMs: number = 30000): Promise<Response> => {
      return Promise.race([
        fetch(url, options),
        new Promise<never>((_, reject) => 
          setTimeout(() => {
            const timeoutError = new Error(`Resource fetch timeout after ${timeoutMs}ms: ${url}`);
            this.errorHandler.handle(timeoutError, {
              phase: 'resourceLoad',
              appId: this._config.id,
              additionalInfo: { resourceUrl: url, timeoutMs }
            });
            reject(timeoutError);
          }, timeoutMs)
        )
      ]);
    };
    
    // 性能监控：开始资源加载计时
    const loadStartTime = performance.now();
    
    const response = await fetchWithTimeout(resourceUrl.trim());
    
    if (!response.ok) {
      const loadingError = new Error(`Failed to load resource: ${resourceUrl}, status: ${response.status} ${response.statusText}`);
      this.errorHandler.handle(loadingError, {
        phase: 'resourceLoad',
        appId: this._config.id,
        additionalInfo: { resourceUrl, status: response.status, statusText: response.statusText }
      });
      throw loadingError;
    }
    
    // 根据资源类型进行不同处理
    const contentType = response.headers.get('content-type') || '';
    
    try {
      // 处理JSON资源
      if (resourceUrl.endsWith('.json') || contentType.includes('application/json')) {
        const jsonData = await response.json();
        // 性能监控：记录资源加载完成
        this.performanceMonitor.record('resourceLoad', performance.now() - loadStartTime, {
          resourceUrl,
          resourceType: 'json',
          size: response.headers.get('content-length')
        });
        return jsonData;
      } 
      // 处理JavaScript资源
      else if (resourceUrl.endsWith('.js') || resourceUrl.endsWith('.mjs') || resourceUrl.endsWith('.ts') || resourceUrl.endsWith('.tsx')) {
        const scriptContent = await response.text();
        // 性能监控：记录资源加载完成
        this.performanceMonitor.record('resourceLoad', performance.now() - loadStartTime, {
          resourceUrl,
          resourceType: 'script',
          size: response.headers.get('content-length')
        });
        return scriptContent;
      } 
      // 处理其他类型资源
      else {
        // 默认作为文本处理
        const textContent = await response.text();
        // 性能监控：记录资源加载完成
        this.performanceMonitor.record('resourceLoad', performance.now() - loadStartTime, {
          resourceUrl,
          resourceType: 'text',
          contentType,
          size: response.headers.get('content-length')
        });
        return textContent;
      }
    } catch (contentError) {
      // 处理内容解析错误
      const parseError = new Error(`Failed to parse resource content: ${resourceUrl}, error: ${contentError instanceof Error ? contentError.message : String(contentError)}`);
      this.errorHandler.handle(parseError, {
        phase: 'resourceLoad',
        appId: this._config.id,
        additionalInfo: { 
          resourceUrl, 
          contentType, 
          parseErrorType: contentError instanceof Error ? contentError.name : 'UnknownError' 
        }
      });
      throw parseError;
    }
  }

  /**
   * 加载微应用
   * 负责加载应用资源、创建沙箱环境、准备应用初始化
   * 是应用生命周期的第一个阶段
   * @returns {Promise<void>}
   * @throws {Error} 当加载失败时抛出错误
   */
  async load(): Promise<void> {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    
    // 状态检查
    if (this.status !== 'NOT_LOADED') {
      console.warn(`${loggerPrefix} Cannot load app in ${this.status} state, skipping`);
      return;
    }
    
    // 性能监控：开始加载计时
    const loadStartTime = performance.now();
    
    try {
        // 安全策略检查
        if (!this.securityEnforcer.checkPermission('load')) {
          const permissionError = new Error(`App doesn't have permission to load`);
          this.status = 'LOAD_ERROR';
          this.errorHandler.handle(permissionError, {
            phase: 'load',
            appId: this._config.id,
            additionalInfo: { reason: 'permission_denied' }
          });
          throw permissionError;
        }
        
        // 更新状态为加载中
        this.status = 'LOADING';
        console.log(`${loggerPrefix} Loading application...`);
        
        // 1. 资源加载性能标记
        const resourcesStartTime = performance.now();
        
        // 尝试从缓存获取资源
        let applicationResources = this.resourceCache.get('main');
        if (!applicationResources) {
          console.log(`${loggerPrefix} Loading application resources...`);
          // 2. 加载应用资源
          applicationResources = await this.loadResources();
          
          // 3. 缓存资源
          if (this._config.useCache !== false) {
            console.log(`${loggerPrefix} Caching application resources`);
            this.resourceCache.set('main', applicationResources);
          }
        } else {
          console.log(`${loggerPrefix} Using cached resources`);
        }
        
        this.performanceMonitor.record('resourceLoad', performance.now() - resourcesStartTime, {
          resourceCount: applicationResources.length
        });
      
      // 3. 创建沙箱环境
        console.log(`${loggerPrefix} Creating sandbox environment...`);
        const sandboxStartTime = performance.now();
        this.sandbox = await SandboxFactory.create(this._config);
        this.performanceMonitor.record('sandboxCreate', performance.now() - sandboxStartTime);
        
        // 4. 执行应用代码
        console.log(`${loggerPrefix} Executing application code...`);
        const evalStartTime = performance.now();
        try {
          if (this.sandbox && typeof this.sandbox.eval === 'function') {
            await this.sandbox.eval(applicationResources);
          } else {
            throw new Error('Sandbox eval function is not available');
          }
        } catch (evalError) {
          // 专门处理代码执行错误
          const wrappedError = new Error(`Failed to execute app code: ${evalError instanceof Error ? evalError.message : String(evalError)}`);
          wrappedError.stack = evalError instanceof Error ? evalError.stack : wrappedError.stack;
          throw wrappedError;
        }
        this.performanceMonitor.record('codeEval', performance.now() - evalStartTime);
      
      // 5. 调用应用生命周期
      console.log(`${loggerPrefix} Bootstrapping application lifecycle...`);
      await this.lifecycle.bootstrap();
      
      // 更新状态为已加载未挂载
      this.status = 'NOT_MOUNTED';
      const totalLoadTime = performance.now() - loadStartTime;
      this.performanceMonitor.record('totalLoad', totalLoadTime);
      
      // 记录应用加载成功
      this.performanceMonitor.reportSuccess('appLoad');
      
      // 触发加载成功事件
      this.eventBus.emit('app:load:success', { 
        appId: this._config.id,
        loadTime: totalLoadTime,
        timestamp: Date.now()
      });
      
      console.log(`${loggerPrefix} Load completed successfully in ${totalLoadTime.toFixed(2)}ms`);
      
    } catch (error) {
      // 加载失败处理
      const errorLoadTime = performance.now() - loadStartTime;
      this.status = 'LOAD_ERROR';
      
      console.error(`${loggerPrefix} Failed to load in ${errorLoadTime.toFixed(2)}ms`, error);
      
      // 记录错误并上报
      const errorContext = {
        phase: 'load',
        appId: this._config.id,
        appInfo: this._config,
        additionalInfo: {
          attemptedState: 'LOADED',
          loadDuration: errorLoadTime,
          currentStatus: this.status,
          errorType: error instanceof Error ? error.name : 'UnknownError'
        }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      this.performanceMonitor.reportError('appLoad', error as Error);
      
      // 触发加载失败事件
      this.eventBus.emit('app:load:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext,
        timestamp: Date.now()
      });
      
      // 尝试清理已创建的资源
      this.cleanupOnLoadError();
      
      throw error;
    }
  }
  
  /**
   * 加载错误时的资源清理
   * 负责在应用加载失败时清理已创建的沙箱环境和资源
   */
  private cleanupOnLoadError(): void {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    console.warn(`${loggerPrefix} Cleaning up resources due to load error`);
    
    try {
      // 清理沙箱环境
      if (this.sandbox) {
        console.log(`${loggerPrefix} Destroying sandbox...`);
        try {
          this.sandbox.destroy?.();
        } catch (destroyError) {
          console.error(`${loggerPrefix} Error destroying sandbox:`, destroyError);
        }
        this.sandbox = null;
      }
      
      // 清理资源缓存
      console.log(`${loggerPrefix} Clearing resource cache...`);
      this.resourceCache.clear();
    } catch (cleanupError) {
      console.error(`${loggerPrefix} Error during resource cleanup:`, cleanupError);
      // 记录清理错误但不抛出，避免影响主错误流程
      this.errorHandler.handle(cleanupError as Error, {
        phase: 'cleanup',
        appId: this._config.id,
        additionalInfo: { cleanupType: 'loadError' }
      });
    }
  }

/**
   * 挂载微应用
   * 将微应用渲染到指定容器中，是应用生命周期的第二个阶段
   * @param {HTMLElement|string|Object} containerOrProps - 容器元素、容器选择器或属性对象
   * @param {Object} [props] - 传递给应用的属性对象
   * @returns {Promise<void>}
   * @throws {Error} 当挂载失败时抛出错误
   */
  async mount(containerOrProps?: HTMLElement | string | Record<string, any>, props?: Record<string, any>): Promise<void> {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    
    // 状态检查
    if (this.status !== 'NOT_MOUNTED') {
      const statusInfo = `Cannot mount app in ${this.status} state`;
      console.warn(`${loggerPrefix} ${statusInfo}`);
      
      if (this.status === 'LOAD_ERROR' || this.status === 'MOUNT_ERROR') {
        // 对于错误状态，尝试重新加载
        console.log(`${loggerPrefix} Attempting to reload app before mounting...`);
        await this.load();
      } else {
        return;
      }
    }
    
    // 处理参数
    let container: HTMLElement | null = null;
    let actualProps = props || {};
    
    try {
      console.log(`${loggerPrefix} Resolving container and processing props...`);
      
      // 解析容器和属性
      container = this.resolveContainer(containerOrProps);
      
      // 如果containerOrProps是对象，则合并到属性中
      if (typeof containerOrProps === 'object' && !(containerOrProps instanceof HTMLElement)) {
        actualProps = { ...actualProps, ...containerOrProps };
        console.log(`${loggerPrefix} Merged containerOrProps into actual props`);
      }
      
      // 验证容器
      if (!container || !(container instanceof HTMLElement)) {
        const containerError = new Error(`No valid container provided or found for mounting`);
        console.error(`${loggerPrefix} Container validation failed`, { containerOrProps });
        throw containerError;
      }
      
      // 保存容器引用
      this.containerElement = container;
      console.log(`${loggerPrefix} Container resolved successfully:`, container.id || 'unnamed-container');
      
      const mountStartTime = performance.now();
      this.status = 'MOUNTING';
      console.log(`${loggerPrefix} Starting mounting process...`);
      
      // 增强的属性传递，包含上下文信息
      console.log(`${loggerPrefix} Preparing mount properties...`);
      const mountProps = {
        ...this._config.props,
        ...actualProps,
        __boneContext: {
          appId: this._config.id,
          version: this._config.version,
          runtime: { version: BONE_RUNTIME_VERSION },
          mountTime: Date.now()
        }
      };
      
      // 执行沙箱挂载
      if (this.sandbox && typeof this.sandbox.mount === 'function') {
        console.log(`${loggerPrefix} Mounting sandbox environment...`);
        try {
          await this.sandbox.mount();
          console.log(`${loggerPrefix} Sandbox mounted successfully`);
        } catch (sandboxMountError) {
          // 记录沙箱挂载错误，但继续尝试
          console.error(`${loggerPrefix} Error mounting sandbox:`, sandboxMountError);
          this.errorHandler.handle(sandboxMountError as Error, {
            phase: 'mount',
            appId: this._config.id,
            additionalInfo: { action: 'sandboxMount' }
          });
        }
      } else {
        console.log(`${loggerPrefix} No sandbox available or mount function not defined`);
      }
      
      // 调用生命周期挂载
      console.log(`${loggerPrefix} Executing lifecycle mount with container and props...`);
      await this.lifecycle.mount(container, mountProps);
      
      // 更新应用状态为已挂载
      this.status = 'MOUNTED';
      console.log(`${loggerPrefix} Successfully mounted`);
      
      // 记录性能指标
      const mountDuration = performance.now() - mountStartTime;
      console.log(`${loggerPrefix} Mount completed in ${mountDuration.toFixed(2)}ms`);
      this.performanceMonitor.record('mount', mountDuration);
      
      // 触发挂载成功事件
      this.eventBus.emit('app:mount:success', { 
        appId: this._config.id,
        container,
        mountTime: mountDuration,
        timestamp: Date.now()
      });
      
    } catch (error) {
      this.status = 'MOUNT_ERROR';
      console.error(`${loggerPrefix} Mounting failed with error:`, error);
      
      // 记录错误并上报
      const errorContext = {
        phase: 'mount',
        appId: this._config.id,
        appInfo: this._config,
        additionalInfo: {
          containerType: containerOrProps ? typeof containerOrProps : 'undefined',
          hasValidContainer: !!container && container instanceof HTMLElement,
          timestamp: Date.now()
        }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 触发挂载失败事件
      this.eventBus.emit('app:mount:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext,
        timestamp: Date.now()
      });
      
      // 清理容器
      if (container && container instanceof HTMLElement) {
        console.log(`${loggerPrefix} Cleaning up container after mount error...`);
        try {
          while (container.firstChild) {
            container.removeChild(container.firstChild);
          }
          console.log(`${loggerPrefix} Container cleaned up successfully`);
        } catch (cleanupError) {
          console.error(`${loggerPrefix} Failed to cleanup container after mount error:`, cleanupError);
          // 记录清理错误
          this.errorHandler.handle(cleanupError as Error, {
            phase: 'containerCleanup',
            appId: this._config.id,
            additionalInfo: { originalErrorType: (error as Error).name }
          });
        }
      }
      
      throw error;
    }
  }
  
  /**
   * 解析微应用容器元素
   * 支持从多种来源获取容器，并进行有效性验证
   * 来源优先级：传入参数 > 已存在的容器 > 配置中的容器
   * @param containerOrProps 容器元素、选择器字符串或属性对象
   * @returns 解析到的容器元素，或者null（如果无法解析到有效的容器）
   * @throws 当明确指定了容器但无法找到时抛出错误
   */
  private resolveContainer(containerOrProps?: HTMLElement | string | Record<string, any>): HTMLElement | null {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    let container: HTMLElement | null = null;
    
    try {
      console.log(`${loggerPrefix} Resolving container...`);
      
      // 1. 如果是字符串，尝试选择器查询
      if (typeof containerOrProps === 'string') {
        console.log(`${loggerPrefix} Trying to resolve container with selector: ${containerOrProps}`);
        container = document.querySelector(containerOrProps);
        
        if (!container) {
          const errorMsg = `Container element not found: ${containerOrProps}`;
          console.error(`${loggerPrefix} ${errorMsg}`);
          throw new Error(errorMsg);
        }
        
        if (!(container instanceof HTMLElement)) {
          console.warn(`${loggerPrefix} Resolved container is not an HTMLElement`);
          container = null;
        } else {
          console.log(`${loggerPrefix} Container resolved successfully from selector`);
        }
      }
      // 2. 如果是HTMLElement，直接使用
      else if (containerOrProps instanceof HTMLElement) {
        container = containerOrProps;
        console.log(`${loggerPrefix} Using provided HTMLElement as container`);
      }
      // 3. 尝试使用已有的容器引用
      else if (this.containerElement) {
        container = this.containerElement;
        console.log(`${loggerPrefix} Using previously initialized container element`);
      }
      // 4. 尝试从配置中获取
      else if (this._config.container) {
        console.log(`${loggerPrefix} Trying to resolve container from app configuration`);
        if (typeof this._config.container === 'string') {
          container = document.querySelector(this._config.container);
          console.log(`${loggerPrefix} Querying container with selector from config: ${this._config.container}`);
        } else {
          container = this._config.container;
          console.log(`${loggerPrefix} Using DOM element from config`);
        }
        
        if (!container) {
          console.warn(`${loggerPrefix} Container specified in config not found`);
        } else if (!(container instanceof HTMLElement)) {
          console.warn(`${loggerPrefix} Container from config is not an HTMLElement`);
          container = null;
        }
      }
      
      if (!container) {
        console.warn(`${loggerPrefix} Could not resolve any valid container element`);
      }
      
      return container;
    } catch (error) {
      console.error(`${loggerPrefix} Error during container resolution:`, error);
      this.errorHandler.handle(error as Error, {
        phase: 'container',
        appId: this._config.id,
        additionalInfo: {
          containerType: typeof containerOrProps,
          hasExistingContainer: !!this.containerElement,
          hasConfigContainer: !!this._config.container
        }
      });
      throw error;
    }
  }

  /**
   * 卸载应用
   */
  /**
   * 卸载微应用
   * 从容器中卸载微应用并清理相关资源，是应用生命周期的第三个阶段
   * @returns {Promise<void>}
   * @throws {Error} 当卸载过程中发生错误时抛出
   */
  async unmount(): Promise<void> {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    
    // 状态检查
    if (this.status !== 'MOUNTED') {
      console.warn(`${loggerPrefix} Cannot unmount in ${this.status} state`);
      // 即使不是MOUNTED状态，也尝试清理资源
      if (this.status === 'MOUNT_ERROR' || this.status === 'UNMOUNT_ERROR') {
        console.log(`${loggerPrefix} Attempting to cleanup resources due to error state...`);
        this.cleanupResources();
      }
      return;
    }
    
    const unmountStartTime = performance.now();
    this.status = 'UNMOUNTING';
    console.log(`${loggerPrefix} Starting unmount process...`);
    
    // 定义清理资源的函数
    const cleanupResources = () => {
      console.log(`${loggerPrefix} Cleaning up resources...`);
      try {
        // 执行沙箱卸载
        if (this.sandbox) {
          console.log(`${loggerPrefix} Unmounting sandbox...`);
          try {
            this.sandbox.unmount?.();
            console.log(`${loggerPrefix} Sandbox unmounted successfully`);
          } catch (sandboxUnmountError) {
            console.error(`${loggerPrefix} Failed to unmount sandbox:`, sandboxUnmountError);
            this.errorHandler.handle(sandboxUnmountError as Error, {
              phase: 'unmount',
              appId: this._config.id,
              additionalInfo: { action: 'sandboxUnmount' }
            });
          }
        }
        
        // 清理容器引用
        console.log(`${loggerPrefix} Clearing container reference...`);
        this.containerElement = null;
        
        // 清理全局环境（如果有）
        if (this._cleanupGlobalEnv) {
          console.log(`${loggerPrefix} Cleaning up global environment...`);
          try {
            this._cleanupGlobalEnv();
            this._cleanupGlobalEnv = null;
          } catch (envCleanupError) {
            console.error(`${loggerPrefix} Error cleaning up global environment:`, envCleanupError);
          }
        }
      } catch (cleanupError) {
        console.error(`${loggerPrefix} Error during resource cleanup:`, cleanupError);
        // 记录清理错误
        this.errorHandler.handle(cleanupError as Error, {
          phase: 'cleanup',
          appId: this._config.id,
          additionalInfo: { cleanupType: 'unmount' }
        });
      }
    };
    
    try {
      // 调用生命周期卸载
      console.log(`${loggerPrefix} Executing lifecycle unmount...`);
      await this.lifecycle.unmount();
      
      // 清理资源
      cleanupResources();
      
      // 更新应用状态为未挂载
      this.status = 'NOT_MOUNTED';
      console.log(`${loggerPrefix} Successfully unmounted`);
      
      // 记录性能指标
      const unmountDuration = performance.now() - unmountStartTime;
      console.log(`${loggerPrefix} Unmount completed in ${unmountDuration.toFixed(2)}ms`);
      this.performanceMonitor.record('unmount', unmountDuration);
      
      // 触发卸载成功事件
      this.eventBus.emit('app:unmount:success', { 
        appId: this._config.id,
        unmountTime: unmountDuration,
        timestamp: Date.now()
      });
      
    } catch (error) {
      this.status = 'UNMOUNT_ERROR';
      console.error(`${loggerPrefix} Unmounting failed with error:`, error);
      
      // 即使出错也强制清理资源
      console.log(`${loggerPrefix} Forcing resource cleanup despite error...`);
      cleanupResources();
      
      // 记录错误并上报
      const errorContext = {
        phase: 'unmount',
        appId: this._config.id,
        appInfo: this._config,
        additionalInfo: {
          timestamp: Date.now()
        }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 触发卸载失败事件
      this.eventBus.emit('app:unmount:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext,
        timestamp: Date.now()
      });
      
      throw error;
    }
  }

  /**
   * 更新微应用
   * 更新已挂载微应用的属性，是应用生命周期的更新阶段
   * @param {Object} [props] - 要更新的属性对象
   * @returns {Promise<void>}
   * @throws {Error} 当更新失败时抛出错误
   */
  async update(props?: Record<string, any>): Promise<void> {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    
    // 状态检查
    if (this.status !== 'MOUNTED') {
      const errorMsg = `Cannot update in ${this.status} state`;
      console.error(`${loggerPrefix} ${errorMsg}`);
      throw new Error(`${loggerPrefix} ${errorMsg}`);
    }
    
    console.log(`${loggerPrefix} Starting update process with props:`, props);
    const updateStartTime = performance.now();
    this.status = 'UPDATING';
    
    try {
      // 准备更新属性，包含上下文信息
      const updateProps = {
        ...props,
        __boneContext: {
          appId: this._config.id,
          version: this._config.version,
          runtime: { version: BONE_RUNTIME_VERSION },
          updateTime: Date.now(),
          updateCount: Number(this.performanceMonitor.getMetrics().updateCount || 0) + 1
        }
      };
      
      console.log(`${loggerPrefix} Executing lifecycle update with prepared props...`);
      await this.lifecycle.update(updateProps);
      
      // 更新应用状态回MOUNTED
      this.status = 'MOUNTED';
      console.log(`${loggerPrefix} Successfully updated`);
      
      // 记录性能指标
      const updateDuration = performance.now() - updateStartTime;
      console.log(`${loggerPrefix} Update completed in ${updateDuration.toFixed(2)}ms`);
      this.performanceMonitor.record('update', updateDuration);
      
      // 触发更新成功事件
      this.eventBus.emit('app:update:success', { 
        appId: this._config.id,
        updateTime: updateDuration,
        timestamp: Date.now()
      });
    } catch (error) {
      console.error(`${loggerPrefix} Update failed with error:`, error);
      this.status = 'UPDATE_ERROR';
      
      // 记录错误并上报
      const errorContext: ErrorContext = {
        phase: 'update',
        appId: this._config.id,
        appInfo: this._config,
        additionalInfo: { 
          hasProps: !!props,
          errorType: error instanceof Error ? error.name : 'UnknownError',
          updateDuration: performance.now() - updateStartTime,
          timestamp: Date.now()
        }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      this.performanceMonitor.reportError('appUpdate', error as Error);
      
      // 触发更新失败事件
      this.eventBus.emit('app:update:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext,
        timestamp: Date.now()
      });
      
      // 尝试回滚到稳定状态
      this.status = 'MOUNTED';
      
      throw error;
    }
  }
  
  /**
   * 强制重新加载应用
   */
  async reload(): Promise<void> {
    const appId = this._config.id;
    console.log(`Reloading app: ${appId}`);
    
    try {
      // 先卸载
      if (this.status === 'MOUNTED') {
        await this.unmount();
      }
      
      // 清理资源
      this.cleanupResources();
      
      // 重置状态
      this.status = 'NOT_LOADED';
      
      // 重新加载
      await this.load();
      
      // 如果之前是挂载状态，重新挂载
      if (this.containerElement && this.status !== 'NOT_LOADED') {
        await this.mount(this.containerElement);
      }
      
      console.log(`App ${appId} reloaded successfully`);
      
    } catch (error) {
      this.errorHandler.handle(error as Error, {
        phase: 'reload',
        appId: this._config.id,
        appInfo: this._config
      });
      throw error;
    }
  }
  
  /**
   * 清理微应用资源
   * 负责清理应用的沙箱环境、容器元素和其他相关资源
   * 确保应用卸载后不会残留引用或导致内存泄漏
   */
  private cleanupResources(): void {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    console.log(`${loggerPrefix} Starting resource cleanup...`);
    
    try {
      // 清理沙箱环境
      if (this.sandbox) {
        console.log(`${loggerPrefix} Cleaning up sandbox...`);
        try {
          this.sandbox.destroy?.();
          console.log(`${loggerPrefix} Sandbox destroyed successfully`);
        } catch (destroyError) {
          console.error(`${loggerPrefix} Failed to destroy sandbox:`, destroyError);
          this.errorHandler.handle(destroyError as Error, {
            phase: 'cleanup',
            appId: this._config.id,
            additionalInfo: { cleanupType: 'sandboxDestroy' }
          });
        }
        this.sandbox = null;
      }
      
      // 清理容器元素
      if (this.containerElement) {
        console.log(`${loggerPrefix} Cleaning up container element...`);
        try {
          while (this.containerElement.firstChild) {
            this.containerElement.removeChild(this.containerElement.firstChild);
          }
          console.log(`${loggerPrefix} Container cleared successfully`);
        } catch (cleanupError) {
          console.error(`${loggerPrefix} Failed to cleanup container:`, cleanupError);
          this.errorHandler.handle(cleanupError as Error, {
            phase: 'cleanup',
            appId: this._config.id,
            additionalInfo: { cleanupType: 'containerCleanup' }
          });
        }
        this.containerElement = null;
      }
      
      // 清理全局环境（如果有）
      if (this._cleanupGlobalEnv) {
        console.log(`${loggerPrefix} Cleaning up global environment...`);
        try {
          this._cleanupGlobalEnv();
          this._cleanupGlobalEnv = null;
          console.log(`${loggerPrefix} Global environment cleaned up successfully`);
        } catch (envCleanupError) {
          console.error(`${loggerPrefix} Failed to cleanup global environment:`, envCleanupError);
          this.errorHandler.handle(envCleanupError as Error, {
            phase: 'cleanup',
            appId: this._config.id,
            additionalInfo: { cleanupType: 'globalEnvCleanup' }
          });
        }
      }
      
      // 不清理资源缓存，允许后续复用
      console.log(`${loggerPrefix} Resource cleanup completed`);
      
    } catch (error) {
      console.error(`${loggerPrefix} Error during resource cleanup:`, error);
      this.errorHandler.handle(error as Error, {
        phase: 'cleanup',
        appId: this._config.id,
        additionalInfo: { cleanupType: 'general' }
      });
    }
  }

  /**
   * 销毁应用
   */
  /**
   * 销毁应用
   */
  async destroy(): Promise<void> {
    const appId = this._config.id;
    console.log(`Destroying app: ${appId}`);
    
    // 无论发生什么，都要尝试销毁资源
    const forceDestroyResources = () => {
      try {
        // 销毁沙箱
        if (this.sandbox) {
          try {
            this.sandbox.destroy?.();
          } catch (destroyError) {
            console.warn('Failed to destroy sandbox during force cleanup:', destroyError);
          }
          this.sandbox = null;
        }
        
        // 清空缓存
        this.resourceCache.clear();
        
        // 重置状态
        this.status = 'NOT_LOADED';
        
      } catch (cleanupError) {
        console.error('Failed to cleanup resources during force destroy:', cleanupError);
      }
    };
    
    try {
      // 先卸载
      if (this.status === 'MOUNTED') {
        try {
          await this.unmount();
        } catch (unmountError) {
          // 记录卸载错误，但继续销毁流程
          this.errorHandler.handle(unmountError as Error, {
            phase: 'destroy',
            appId: this._config.id,
            additionalInfo: { action: 'unmount' }
          });
        }
      }
      
      // 调用生命周期销毁
      await this.lifecycle.destroy();
      
      // 清理资源
      forceDestroyResources();
      
      // 触发销毁成功事件
      this.eventBus.emit('app:destroy:success', { appId });
      
      console.log(`App ${appId} destroyed successfully`);
      
    } catch (error) {
      // 记录错误并上报
      const errorContext = {
        phase: 'destroy',
        appId: this._config.id,
        appInfo: this._config,
        additionalInfo: { attemptedState: this.status }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 强制销毁资源
      forceDestroyResources();
      
      // 触发销毁失败事件
      this.eventBus.emit('app:destroy:error', { 
        appId,
        error: error as Error,
        context: errorContext
      });
      
      throw error;
    }
  }
  
  /**
   * 获取应用诊断信息
   * 提供微应用的完整诊断数据，包括应用状态、资源状态、性能指标和错误统计
   * 用于监控、调试和问题排查
   * @returns 包含诊断信息的对象
   */
  getDiagnostics(): Record<string, any> {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    console.log(`${loggerPrefix} Collecting diagnostic information...`);
    
    try {
      // 收集沙箱状态信息
      let sandboxStatus = null;
      let sandboxDetails = null;
      if (this.sandbox && typeof this.sandbox.getStatus === 'function') {
        sandboxStatus = this.sandbox.getStatus();
        // 如果沙箱支持获取详细信息，收集更多数据
        if (typeof this.sandbox.getDetails === 'function') {
          sandboxDetails = this.sandbox.getDetails();
        }
      }
      
      const diagnostics = {
        // 基本应用信息
        appInfo: {
          appId: this._config.id,
          appName: this._config.name,
          version: this._config.version,
          description: this._config.metadata?.description || 'No description',
          technology: this._config.metadata?.technology || null
        },
        
        // 应用当前状态
        status: {
          currentState: this.status,
          isMounted: this.status === 'MOUNTED',
          isErrorState: ['LOAD_ERROR', 'MOUNT_ERROR', 'UPDATE_ERROR'].includes(this.status),
          timestamp: Date.now()
        },
        
        // 资源状态
        resources: {
          hasContainer: !!this.containerElement,
          hasSandbox: !!this.sandbox,
          sandboxStatus: sandboxStatus,
          sandboxDetails: sandboxDetails,
          resourceCacheStatus: this.resourceCache.getStatus?.() || 'unknown'
        },
        
        // 性能指标
        performance: this.performanceMonitor.getMetrics(),
        
        // 错误统计
        errors: this.errorHandler.getStatistics(),
        
        // 安全相关信息
        security: {
          permissions: this._config.metadata?.permissions || [],
          hasSecurityIssues: this.errorHandler.getSecurityViolationsCount?.() || 0
        }
      };
      
      console.log(`${loggerPrefix} Diagnostic information collected successfully`);
      return diagnostics;
    } catch (error) {
      console.error(`${loggerPrefix} Error collecting diagnostic information:`, error);
      
      // 即使出错也返回基本诊断信息
      return {
        appInfo: {
          appId: this._config.id,
          error: `Failed to collect full diagnostics: ${error instanceof Error ? error.message : String(error)}`
        },
        status: { currentState: this.status },
        collectionError: true,
        timestamp: Date.now()
      };
    }
  }

  /**
   * 获取应用性能指标
   * 返回微应用的详细性能数据，包括各生命周期阶段的耗时
   * @returns 性能指标对象
   */
  getPerformanceMetrics(): Record<string, any> {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    console.log(`${loggerPrefix} Retrieving performance metrics...`);
    
    try {
      const metrics = this.performanceMonitor.getMetrics();
      console.log(`${loggerPrefix} Performance metrics retrieved successfully`);
      return metrics;
    } catch (error) {
      console.error(`${loggerPrefix} Error retrieving performance metrics:`, error);
      return {
        error: `Failed to get performance metrics: ${error instanceof Error ? error.message : String(error)}`,
        timestamp: Date.now()
      };
    }
  }

  /**
   * 获取应用沙箱状态
   * 返回微应用沙箱的当前运行状态
   * @returns 沙箱状态对象或null（如果沙箱不存在或无法获取状态）
   */
  getSandboxStatus(): Record<string, any> | null {
    const loggerPrefix = `[MicroApp] ${this._config.id}`;
    
    try {
      if (!this.sandbox) {
        console.log(`${loggerPrefix} No sandbox exists`);
        return null;
      }
      
      if (typeof this.sandbox.getStatus !== 'function') {
        console.warn(`${loggerPrefix} Sandbox does not support getStatus method`);
        return { status: 'unknown', hasGetStatusMethod: false };
      }
      
      const status = this.sandbox.getStatus();
      console.log(`${loggerPrefix} Sandbox status retrieved successfully`);
      return status;
    } catch (error) {
      console.error(`${loggerPrefix} Error retrieving sandbox status:`, error);
      return { 
        status: 'error', 
        error: `Failed to get sandbox status: ${error instanceof Error ? error.message : String(error)}` 
      };
    }
  }
}