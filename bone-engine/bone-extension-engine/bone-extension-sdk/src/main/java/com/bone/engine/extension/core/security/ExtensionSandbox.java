package com.bone.engine.extension.core.security;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 扩展执行沙箱 提供扩展执行的沙箱隔离机制 */
@Slf4j
@Component
public class ExtensionSandbox {

  // 沙箱策略
  private SandboxPolicy sandboxPolicy;

  /** 初始化沙箱 */
  public void init() {
    // 保存原始策略
    Policy originalPolicy = Policy.getPolicy();

    // 创建沙箱策略
    sandboxPolicy = new SandboxPolicy(originalPolicy);

    // 设置为系统策略
    Policy.setPolicy(sandboxPolicy);

    // 启用安全管理器
    if (System.getSecurityManager() == null) {
      System.setSecurityManager(new SecurityManager());
    }

    log.info("Extension sandbox initialized");
  }

  /**
   * 执行扩展代码
   *
   * @param task 扩展任务
   * @param <T> 结果类型
   * @return 执行结果
   * @throws Exception 执行异常
   */
  public <T> T executeInSandbox(SandboxTask<T> task) throws Exception {
    // 保存当前上下文
    SecurityManager originalSecurityManager = System.getSecurityManager();

    try {
      // 启用安全管理器
      if (System.getSecurityManager() == null) {
        System.setSecurityManager(new SecurityManager());
      }

      // 执行任务
      return task.execute();
    } finally {
      // 恢复原始安全管理器
      if (originalSecurityManager == null) {
        System.setSecurityManager(null);
      }
    }
  }

  /** 沙箱策略 */
  private static class SandboxPolicy extends Policy {
    private final Policy originalPolicy;

    public SandboxPolicy(Policy originalPolicy) {
      this.originalPolicy = originalPolicy;
    }

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
      // 获取原始权限
      PermissionCollection permissions =
          originalPolicy != null
              ? originalPolicy.getPermissions(domain)
              : super.getPermissions(domain);

      // 移除危险权限
      // 实际实现中，这里应该根据需要限制权限

      return permissions;
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
      // 检查危险操作
      String permissionName = permission.getName();

      // 禁止文件系统写入
      if (permissionName.startsWith("java.io.FilePermission")
          && permission.getActions().contains("write")) {
        log.warn("Sandbox blocked file write operation: {}", permission);
        return false;
      }

      // 禁止网络访问
      if (permissionName.startsWith("java.net.SocketPermission")) {
        log.warn("Sandbox blocked network operation: {}", permission);
        return false;
      }

      // 禁止系统命令执行
      if (permissionName.startsWith("java.lang.RuntimePermission")
          && permissionName.contains("exec")) {
        log.warn("Sandbox blocked runtime exec operation: {}", permission);
        return false;
      }

      // 其他权限检查
      return originalPolicy != null
          ? originalPolicy.implies(domain, permission)
          : super.implies(domain, permission);
    }
  }

  /**
   * 沙箱任务接口
   *
   * @param <T> 结果类型
   */
  @FunctionalInterface
  public interface SandboxTask<T> {
    T execute() throws Exception;
  }
}
