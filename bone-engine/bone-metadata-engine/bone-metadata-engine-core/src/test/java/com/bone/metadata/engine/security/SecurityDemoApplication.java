package com.bone.metadata.engine.security;

import java.util.*;

/** 安全组件演示应用（已从 src/main 移至 test，避免被打包进生产 JAR） */
public class SecurityDemoApplication {

  public static void main(String[] args) {
    PermissionEvaluator permissionEvaluator = new DefaultPermissionEvaluator();
    DataMaskingService maskingService = new DefaultDataMaskingService();
    AuditService auditService = new DefaultAuditService();
    FieldLevelSecurityManager securityManager =
        new FieldLevelSecurityManager(permissionEvaluator, maskingService, auditService);

    System.out.println("安全组件演示应用启动成功!");
    System.out.println("================================");

    demoAuthentication(permissionEvaluator, maskingService, auditService);
    demoDataProcessing(securityManager);

    System.out.println("================================");
    System.out.println("安全组件演示完成!");
  }

  private static void demoAuthentication(
      PermissionEvaluator permissionEvaluator,
      DataMaskingService maskingService,
      AuditService auditService) {
    System.out.println("\n=== 认证和权限演示 ===");

    CustomAuthentication adminAuth = DefaultCustomAuthentication.createAdmin();
    System.out.println("管理员认证: " + adminAuth.getName());
    System.out.println("管理员角色: " + adminAuth.getAuthorities());
    System.out.println("管理员有ADMIN角色: " + adminAuth.hasRole("ADMIN"));

    List<String> userRoles = new ArrayList<>();
    userRoles.add("USER");
    userRoles.add("VIEWER");
    CustomAuthentication userAuth = DefaultCustomAuthentication.createUser("user1", userRoles);
    System.out.println("\n普通用户认证: " + userAuth.getName());
    System.out.println("普通用户角色: " + userAuth.getAuthorities());
    System.out.println("普通用户有ADMIN角色: " + userAuth.hasRole("ADMIN"));
    System.out.println("普通用户有USER角色: " + userAuth.hasRole("USER"));

    auditService.logAuthentication(adminAuth.getName(), "127.0.0.1", true, "登录成功");
    auditService.logAuthentication("unknown", "192.168.1.1", false, "密码错误");
  }

  private static void demoDataProcessing(FieldLevelSecurityManager securityManager) {
    System.out.println("\n=== 数据处理演示 ===");

    Map<String, Object> testData = new HashMap<>();
    testData.put("id", "1001");
    testData.put("name", "张三");
    testData.put("phoneNumber", "13800138000");
    testData.put("idCardNumber", "110101199001011234");
    testData.put("email", "zhangsan@example.com");
    testData.put("salary", "15000.00");
    testData.put("address", "北京市朝阳区");

    List<String> viewerRoles = new ArrayList<>();
    viewerRoles.add("VIEWER");
    CustomAuthentication viewerAuth = DefaultCustomAuthentication.createUser("viewer", viewerRoles);

    System.out.println("\n原始测试数据:");
    printData(testData);

    System.out.println("\nVIEWER用户读取数据（可能会脱敏）:");
    try {
      Map<String, Object> processedData =
          securityManager.processDataAccess(viewerAuth, "user", testData, "READ");
      printData(processedData);
    } catch (Exception e) {
      System.out.println("数据处理异常: " + e.getMessage());
    }

    System.out.println("\n演示批量操作验证:");
    List<String> recordIds = Arrays.asList("1001", "1002", "1003");
    try {
      securityManager.validateBatchOperation(viewerAuth, "viewRecords", recordIds);
      System.out.println("批量操作权限验证通过");
    } catch (Exception e) {
      System.out.println("批量操作权限验证失败: " + e.getMessage());
    }
  }

  private static void printData(Map<String, Object> data) {
    if (data == null || data.isEmpty()) {
      System.out.println("  (空数据)");
      return;
    }

    for (Map.Entry<String, Object> entry : data.entrySet()) {
      System.out.printf("  %s: %s\n", entry.getKey(), entry.getValue());
    }
  }
}
