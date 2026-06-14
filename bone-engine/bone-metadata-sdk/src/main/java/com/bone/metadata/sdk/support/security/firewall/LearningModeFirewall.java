package com.bone.metadata.sdk.support.security.firewall;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能学习模式 SQL 防火墙。 首次遇到未审批的表会调用 LearningModeService.requestApproval(sql)，返回 false； 审批通过后调用
 * approveTable(tableName) 即可放行。
 */
public class LearningModeFirewall {

  // 已批准的“操作:表名”白名单
  private final Set<String> allowedTables = new ConcurrentSkipListSet<>();
  // 模式签名 -> 审批状态
  private final ConcurrentHashMap<String, ApprovalStatus> patternApprovals =
      new ConcurrentHashMap<>();

  // 提取操作和表名的正则：捕获 GROUP1=操作, GROUP2=表名
  private static final Pattern SQL_PATTERN =
      Pattern.compile(
          "\\b(SELECT|INSERT|UPDATE|DELETE)\\b.*?\\b(?:FROM|INTO|UPDATE)\\s+([\\w_]+)",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  // 仅允许字母、数字、下划线组成的关键字和表名
  private static final Pattern SAFE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

  private boolean learningEnabled = true;
  private final LearningModeService learningModeService;

  public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
  }

  public LearningModeFirewall(LearningModeService svc) {
    this.learningModeService = svc;
  }

  /**
   * 校验 SQL 是否允许执行。
   *
   * @return true 放行；false 拦截（等待审批或非法）
   */
  public boolean validate(String sql) {
    if (sql == null || sql.isBlank()) {
      return false;
    }
    // 基础安全：首词必须合法
    String first = sql.trim().split("\\s+")[0];
    if (!SAFE_PATTERN.matcher(first).matches()) {
      throw new SqlSecurityException("SQL 关键字不合法: " + first);
    }
    // 提取操作与表名
    Matcher m = SQL_PATTERN.matcher(sql);
    if (!m.find()) {
      throw new SqlSecurityException("无法识别的 SQL 结构");
    }
    String op = m.group(1).toUpperCase(java.util.Locale.ROOT);
    String table = m.group(2);
    if (!SAFE_PATTERN.matcher(table).matches()) {
      throw new SqlSecurityException("表名包含非法字符: " + table);
    }
    String key = op + ":" + table;
    // 学习模式：若未在白名单中，触发审批
    if (learningEnabled && !allowedTables.contains(key)) {
      String signature = createSignature(op, table);
      ApprovalStatus status =
          patternApprovals.computeIfAbsent(
              signature,
              sig -> {
                learningModeService.requestApproval(sql);
                return ApprovalStatus.PENDING;
              });
      return status == ApprovalStatus.APPROVED;
    }
    // 白名单已通过
    return allowedTables.contains(key);
  }

  /** 审批通过后调用，将对应模式加入白名单 */
  public void approveTable(String operation, String tableName) {
    String key = operation.toUpperCase(java.util.Locale.ROOT) + ":" + tableName;
    allowedTables.add(key);
    // 更新所有对应签名的状态
    String sig = createSignature(operation, tableName);
    patternApprovals.put(sig, ApprovalStatus.APPROVED);
  }

  private String createSignature(String op, String table) {
    return op + ":" + table;
  }

  public void setLearningEnabled(boolean enabled) {
    this.learningEnabled = enabled;
  }

  public static class SqlSecurityException extends RuntimeException {
    public SqlSecurityException(String msg) {
      super(msg);
    }
  }

  /** 审批服务接口，需在外部实现并注入 */
  public interface LearningModeService {
    /** 收到审批请求后，应异步通知管理员并最终调用 approveTable */
    void requestApproval(String sql);
  }
}
