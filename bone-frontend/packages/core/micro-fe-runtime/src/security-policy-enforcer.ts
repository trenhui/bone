/**
 * 安全策略执行器（兼容性适配器）
 * @deprecated 使用从 './security/policy-enforcer' 导入的 SecurityPolicyEnforcer 替代
 */
import { SecurityPolicyEnforcer as CoreSecurityPolicyEnforcer } from './security/policy-enforcer';
import { MicroApplication } from './micro-application';

/**
 * 简单权限策略实现，用于适配器
 */
class SimplePermissionPolicy implements AppSecurityPolicy {
  private permissions: Set<string>;
  
  constructor(permissions: string[]) {
    this.permissions = new Set(permissions);
  }
  
  async check(app: MicroApplication, action: string, resource: any) {
    return {
      allowed: this.permissions.has(action),
      reason: this.permissions.has(action) ? undefined : `Permission denied: ${action}`
    };
  }
}

/**
 * @deprecated 此实现已废弃，请使用 './security/policy-enforcer' 中的实现
 */
export class SecurityPolicyEnforcer {
  private static readonly DEFAULT_ALLOWED_ACTIONS = [
    'load',
    'mount',
    'unmount',
    'update',
    'destroy'
  ];
  
  private coreEnforcer: CoreSecurityPolicyEnforcer;
  private permissions: Set<string>;
  private permissionPolicy: SimplePermissionPolicy;

  constructor(permissions: string[] = []) {
    console.warn('SecurityPolicyEnforcer from ./security-policy-enforcer is deprecated. Use SecurityPolicyEnforcer from ./security/policy-enforcer instead.');
    this.permissions = new Set([
      ...permissions,
      ...SecurityPolicyEnforcer.DEFAULT_ALLOWED_ACTIONS
    ]);
    this.permissionPolicy = new SimplePermissionPolicy([...this.permissions]);
    this.coreEnforcer = CoreSecurityPolicyEnforcer.getInstance();
    this.coreEnforcer.addPolicy(this.permissionPolicy);
  }

  checkPermission(action: string): boolean {
    const result = this.permissions.has(action);
    
    if (!result) {
      console.warn(`Permission denied: ${action}`);
    }
    
    return result;
  }

  addPermission(permission: string): void {
    this.permissions.add(permission);
    // 重新创建策略以更新权限
    this.coreEnforcer.removePolicy(this.permissionPolicy);
    this.permissionPolicy = new SimplePermissionPolicy([...this.permissions]);
    this.coreEnforcer.addPolicy(this.permissionPolicy);
  }

  addPermissions(permissions: string[]): void {
    permissions.forEach(permission => this.addPermission(permission));
  }

  removePermission(permission: string): void {
    if (!SecurityPolicyEnforcer.DEFAULT_ALLOWED_ACTIONS.includes(permission)) {
      this.permissions.delete(permission);
      // 重新创建策略以更新权限
      this.coreEnforcer.removePolicy(this.permissionPolicy);
      this.permissionPolicy = new SimplePermissionPolicy([...this.permissions]);
      this.coreEnforcer.addPolicy(this.permissionPolicy);
    }
  }

  getPermissions(): string[] {
    return Array.from(this.permissions);
  }

  hasAllPermissions(requiredPermissions: string[]): boolean {
    return requiredPermissions.every(permission => this.checkPermission(permission));
  }

  hasAnyPermission(requiredPermissions: string[]): boolean {
    return requiredPermissions.some(permission => this.checkPermission(permission));
  }

  checkResourceAccess(resource: string, operation: 'read' | 'write' | 'execute'): boolean {
    const permission = `${operation}:${resource}`;
    return this.checkPermission(permission);
  }

  exportPolicy(): {
    permissions: string[];
    allowedActions: string[];
  } {
    return {
      permissions: this.getPermissions(),
      allowedActions: SecurityPolicyEnforcer.DEFAULT_ALLOWED_ACTIONS
    };
  }
}

// 为了TypeScript编译通过，需要定义AppSecurityPolicy接口
interface AppSecurityPolicy {
  check(app: MicroApplication, action: string, resource: any): Promise<{ allowed: boolean; reason?: string }>;
}