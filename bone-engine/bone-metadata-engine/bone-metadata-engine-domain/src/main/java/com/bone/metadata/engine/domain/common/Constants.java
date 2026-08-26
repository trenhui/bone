package com.bone.metadata.engine.domain.common;

/**
 * 统一常量管理类
 *
 * <p>集中管理系统中使用的各种常量，包括工作流状态、规则类型等。 遵循业界最佳实践： 1. 常量按功能模块分组 2. 使用枚举类型替代字符串常量（提高类型安全） 3. 提供辅助方法便于常量使用
 * 4. 避免常量分散在多个类中导致的维护困难
 */
public final class Constants {

  private Constants() {
    throw new UnsupportedOperationException("Constants class cannot be instantiated");
  }

  /** 工作流状态枚举 替代 WorkflowStatusConstants 中的字符串常量，提供类型安全 */
  public enum WorkflowStatus {
    /** 工作流状态：初始化 - 表示工作流刚刚创建，尚未启动 */
    INITIATED("INITIATED"),
    /** 工作流状态：运行中 - 表示工作流正在执行中 */
    RUNNING("RUNNING"),
    /** 工作流状态：已完成 - 表示工作流已经成功完成所有任务 */
    COMPLETED("COMPLETED"),
    /** 工作流状态：已终止 - 表示工作流由于某种原因被提前终止 */
    TERMINATED("TERMINATED");

    private final String code;

    WorkflowStatus(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    /**
     * 根据状态码获取枚举实例
     *
     * @param code 状态码
     * @return 对应的枚举实例，如果不存在则返回null
     */
    public static WorkflowStatus fromCode(String code) {
      for (WorkflowStatus status : values()) {
        if (status.code.equals(code)) {
          return status;
        }
      }
      return null;
    }

    /**
     * 检查状态是否有效
     *
     * @param code 状态码
     * @return 是否为有效的工作流状态
     */
    public static boolean isValidStatus(String code) {
      return fromCode(code) != null;
    }
  }

  /** 规则类型枚举 替代 RuleTypeConstants 中的字符串常量，提供类型安全 */
  public enum RuleType {
    /** 高价值订单规则 - 用于评估订单是否为高价值订单，需要特殊审批流程 */
    HIGH_VALUE_ORDER("HIGH_VALUE_ORDER"),
    /** 订单优先级规则 - 用于评估和设置订单的优先级 */
    ORDER_PRIORITY("ORDER_PRIORITY");

    private final String code;

    RuleType(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    /**
     * 根据规则类型码获取枚举实例
     *
     * @param code 规则类型码
     * @return 对应的枚举实例，如果不存在则返回null
     */
    public static RuleType fromCode(String code) {
      for (RuleType type : values()) {
        if (type.code.equals(code)) {
          return type;
        }
      }
      return null;
    }

    /**
     * 检查规则类型是否有效
     *
     * @param code 规则类型码
     * @return 是否为有效的规则类型
     */
    public static boolean isValidRuleType(String code) {
      return fromCode(code) != null;
    }
  }

  /** 通用分隔符常量 */
  public static final String SEPARATOR = "|";

  public static final String COMMA = ",";
  public static final String DOT = ".";
  public static final String UNDERLINE = "_";
  public static final String EMPTY_STRING = "";

  /** 缓存相关常量 */
  public static final int DEFAULT_CACHE_SIZE = 1000;

  public static final int DEFAULT_CACHE_TTL = 3600; // 默认缓存过期时间（秒）

  /** 分页相关常量 */
  public static final int DEFAULT_PAGE_SIZE = 20;

  public static final int MAX_PAGE_SIZE = 100;
  public static final int DEFAULT_PAGE_NO = 1;

  /** 正则表达式常量 */
  public static final String REGEX_UUID =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  public static final String REGEX_CODE = "^[A-Za-z0-9_]+$";
}
