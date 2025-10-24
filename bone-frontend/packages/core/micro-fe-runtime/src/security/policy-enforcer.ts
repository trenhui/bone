import { AppSecurityPolicy } from '../types';
import { MicroApplication } from '../micro-application';

export class SecurityPolicyEnforcer {
  private policies: AppSecurityPolicy[] = [];

  // 添加安全策略
  addPolicy(policy: AppSecurityPolicy): void {
    this.policies.push(policy);
    console.log('Security policy added');
  }

  // 批量添加安全策略
  addPolicies(policies: AppSecurityPolicy[]): void {
    policies.forEach(policy => this.addPolicy(policy));
  }

  // 移除安全策略
  removePolicy(policyToRemove: AppSecurityPolicy): void {
    const index = this.policies.findIndex(policy => policy === policyToRemove);
    if (index !== -1) {
      this.policies.splice(index, 1);
      console.log('Security policy removed');
    }
  }

  // 清空所有策略
  clearPolicies(): void {
    this.policies = [];
    console.log('All security policies cleared');
  }

  // 执行安全检查
  async enforce(app: MicroApplication, action: string, resource: any): Promise<boolean> {
    for (const policy of this.policies) {
      try {
        const result = await policy.check(app, action, resource);
        if (!result.allowed) {
          console.warn(`Security policy violation for app ${app.config.id}: ${result.reason || 'Access denied'}`);
          return false;
        }
      } catch (error) {
        console.error(`Error enforcing security policy:`, error);
        // 出错时默认拒绝，以保证安全性
        return false;
      }
    }
    return true;
  }

  // 检查应用是否有特定权限
  async checkPermission(app: MicroApplication, permission: string): Promise<boolean> {
    return this.enforce(app, 'check-permission', { permission });
  }

  // 获取已添加的策略数量
  getPolicyCount(): number {
    return this.policies.length;
  }

  // 创建单例实例
  private static instance: SecurityPolicyEnforcer | null = null;

  static getInstance(): SecurityPolicyEnforcer {
    if (!SecurityPolicyEnforcer.instance) {
      SecurityPolicyEnforcer.instance = new SecurityPolicyEnforcer();
    }
    return SecurityPolicyEnforcer.instance;
  }
}

// CSP策略实现
export class CSPPolicy implements AppSecurityPolicy {
  private allowedPermissions = new Map<string, string[]>([
    ['script-eval', ['unsafe-eval']],
    ['inline-script', ['unsafe-inline']],
    ['remote-script', ['script-src']],
    ['fetch-api', ['fetch']],
    ['websocket', ['connect-src']],
    ['local-storage', ['storage']],
    ['session-storage', ['storage']],
    ['indexed-db', ['indexeddb']],
    ['cookie', ['cookie']]
  ]);

  async check(app: MicroApplication, action: string, resource: any) {
    // 获取应用的权限
    const appPermissions = app.config.metadata?.permissions || [];

    // 处理特定操作的检查
    switch (action) {
      case 'script-eval':
        return this.checkEvalPermission(appPermissions);
      
      case 'inline-script':
        return this.checkInlineScriptPermission(appPermissions);
      
      case 'fetch':
        return this.checkFetchPermission(appPermissions, resource as string);
      
      case 'storage-access':
        return this.checkStoragePermission(appPermissions, resource as { type: string });
      
      case 'dom-manipulation':
        return this.checkDOMManipulation(appPermissions, resource as { selector: string });
      
      case 'check-permission':
        return this.checkGenericPermission(appPermissions, resource.permission);
      
      default:
        return this.checkGenericAction(action, appPermissions);
    }
  }

  private checkEvalPermission(permissions: string[]): { allowed: boolean; reason?: string } {
    const requiredPermissions = this.allowedPermissions.get('script-eval') || [];
    const hasPermission = requiredPermissions.some(perm => permissions.includes(perm));
    
    if (!hasPermission) {
      return {
        allowed: false,
        reason: 'CSP: eval() and related functions are not allowed without \'unsafe-eval\' permission'
      };
    }
    
    return { allowed: true };
  }

  private checkInlineScriptPermission(permissions: string[]): { allowed: boolean; reason?: string } {
    const requiredPermissions = this.allowedPermissions.get('inline-script') || [];
    const hasPermission = requiredPermissions.some(perm => permissions.includes(perm));
    
    if (!hasPermission) {
      return {
        allowed: false,
        reason: 'CSP: inline scripts are not allowed without \'unsafe-inline\' permission'
      };
    }
    
    return { allowed: true };
  }

  private checkFetchPermission(permissions: string[], url: string): { allowed: boolean; reason?: string } {
    // 检查是否有fetch权限
    const requiredPermissions = this.allowedPermissions.get('fetch-api') || [];
    if (!requiredPermissions.some(perm => permissions.includes(perm))) {
      return {
        allowed: false,
        reason: 'CSP: fetch API is not allowed without \'fetch\' permission'
      };
    }

    // 检查是否是允许的域名（可以根据需要扩展域名白名单功能）
    return { allowed: true };
  }

  private checkStoragePermission(permissions: string[], storageInfo: { type: string }): { allowed: boolean; reason?: string } {
    const { type } = storageInfo;
    let requiredPermissions: string[] = [];
    
    switch (type) {
      case 'localStorage':
        requiredPermissions = this.allowedPermissions.get('local-storage') || [];
        break;
      case 'sessionStorage':
        requiredPermissions = this.allowedPermissions.get('session-storage') || [];
        break;
      default:
        return { allowed: false, reason: `CSP: unknown storage type: ${type}` };
    }

    if (!requiredPermissions.some(perm => permissions.includes(perm))) {
      return {
        allowed: false,
        reason: `CSP: ${type} access is not allowed without 'storage' permission`
      };
    }

    return { allowed: true };
  }

  private checkDOMManipulation(permissions: string[], domInfo: { selector: string }): { allowed: boolean; reason?: string } {
    // 检查DOM操作权限
    // 这里可以根据selector实现更细粒度的控制
    if (domInfo.selector.includes('document.body') || domInfo.selector.includes('html')) {
      return {
        allowed: false,
        reason: 'CSP: direct manipulation of body or html elements is not allowed'
      };
    }

    return { allowed: true };
  }

  private checkGenericPermission(permissions: string[], requestedPermission: string): { allowed: boolean; reason?: string } {
    if (permissions.includes(requestedPermission)) {
      return { allowed: true };
    }
    return {
      allowed: false,
      reason: `Required permission not granted: ${requestedPermission}`
    };
  }

  private checkGenericAction(action: string, permissions: string[]): { allowed: boolean; reason?: string } {
    // 对于未明确处理的操作，检查是否有对应的权限
    const requiredPermissions = this.allowedPermissions.get(action) || [];
    if (requiredPermissions.length > 0) {
      const hasPermission = requiredPermissions.some(perm => permissions.includes(perm));
      if (!hasPermission) {
        return {
          allowed: false,
          reason: `CSP: ${action} is not allowed without appropriate permissions`
        };
      }
    }
    
    // 默认允许未明确限制的操作
    return { allowed: true };
  }
}