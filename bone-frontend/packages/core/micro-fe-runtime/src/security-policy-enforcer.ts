/**
 * 安全策略执行器
 * 负责检查和执行应用的权限控制
 */
export class SecurityPolicyEnforcer {
  private permissions: Set<string>;
  private static readonly DEFAULT_ALLOWED_ACTIONS = [
    'load',
    'mount',
    'unmount',
    'update',
    'destroy'
  ];

  /**
   * 构造函数
   * @param permissions 应用拥有的权限列表
   */
  constructor(permissions: string[] = []) {
    this.permissions = new Set([
      ...permissions,
      ...SecurityPolicyEnforcer.DEFAULT_ALLOWED_ACTIONS
    ]);
  }

  /**
   * 检查应用是否有权限执行特定操作
   * @param action 要执行的操作
   * @returns 是否有权限
   */
  checkPermission(action: string): boolean {
    const result = this.permissions.has(action);
    
    if (!result) {
      console.warn(`Permission denied: ${action}`);
    }
    
    return result;
  }

  /**
   * 添加权限
   * @param permission 要添加的权限
   */
  addPermission(permission: string): void {
    this.permissions.add(permission);
  }

  /**
   * 添加多个权限
   * @param permissions 要添加的权限列表
   */
  addPermissions(permissions: string[]): void {
    permissions.forEach(permission => this.permissions.add(permission));
  }

  /**
   * 移除权限
   * @param permission 要移除的权限
   */
  removePermission(permission: string): void {
    // 不允许移除默认权限
    if (!SecurityPolicyEnforcer.DEFAULT_ALLOWED_ACTIONS.includes(permission)) {
      this.permissions.delete(permission);
    }
  }

  /**
   * 获取所有已授权的权限
   */
  getPermissions(): string[] {
    return Array.from(this.permissions);
  }

  /**
   * 检查是否拥有所有指定的权限
   * @param requiredPermissions 需要的权限列表
   * @returns 是否拥有所有权限
   */
  hasAllPermissions(requiredPermissions: string[]): boolean {
    return requiredPermissions.every(permission => this.checkPermission(permission));
  }

  /**
   * 检查是否拥有至少一个指定的权限
   * @param requiredPermissions 需要的权限列表
   * @returns 是否拥有至少一个权限
   */
  hasAnyPermission(requiredPermissions: string[]): boolean {
    return requiredPermissions.some(permission => this.checkPermission(permission));
  }

  /**
   * 验证资源访问权限
   * @param resource 要访问的资源
   * @param operation 对资源的操作
   * @returns 是否允许访问
   */
  checkResourceAccess(resource: string, operation: 'read' | 'write' | 'execute'): boolean {
    const permission = `${operation}:${resource}`;
    return this.checkPermission(permission);
  }

  /**
   * 导出安全策略信息
   */
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