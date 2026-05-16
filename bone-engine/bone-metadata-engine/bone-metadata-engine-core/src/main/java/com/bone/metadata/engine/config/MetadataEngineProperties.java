package com.bone.metadata.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Metadata Engine 配置属性类 支持通过 application.properties 或 application.yml 进行配置 */
@Data
@ConfigurationProperties(prefix = "bone.smartmeta")
public class MetadataEngineProperties {

  /** 是否启用AI增强功能 */
  private boolean aiEnhancementEnabled = true;

  /** 是否启用热加载功能 */
  private boolean hotReloadEnabled = true;

  /** 热加载状态（供isHotReloadEnabled方法使用） */
  private boolean hotReload = true;

  /** 缓存过期时间（毫秒） */
  private long cacheTtl = 3600000; // 默认1小时

  /** 最大缓存大小 */
  private int maxCacheSize = 1000;

  /** 是否启用自动验证 */
  private boolean autoValidationEnabled = true;

  /** 是否启用表达式缓存 */
  private boolean expressionCacheEnabled = true;

  /** 是否启用严格模式 在严格模式下，表达式计算失败会抛出异常 */
  private boolean strictMode = true;

  /** 热加载间隔（毫秒） */
  private long hotReloadInterval = 60000; // 默认1分钟

  /** 批量操作的批次大小 */
  private int batchSize = 100;

  /** 是否启用安全模式 */
  private boolean secureModeEnabled = false;

  /** 默认工作域 */
  private String defaultDomain = "DEFAULT";

  /** AI模型配置 */
  private AiProperties ai = new AiProperties();

  /** 日志配置 */
  private LogProperties log = new LogProperties();

  /** 存储配置 */
  private StorageProperties storage = new StorageProperties();

  /** AI配置属性 */
  @Data
  public static class AiProperties {

    /** 默认AI模型名称 */
    private String defaultModel = "gpt-4o"; // 示例模型，需根据实际情况配置

    /** 获取默认AI模型名称 */
    public String getDefaultModel() {
      return defaultModel;
    }

    /** AI请求超时时间（毫秒） */
    private long timeout = 30000;

    /** AI请求重试次数 */
    private int retryCount = 3;

    /** AI提示模板路径 */
    private String promptTemplatesPath = "classpath:/ai/prompts";

    /** 是否启用流式响应 */
    private boolean streamingEnabled = false;

    /** 最大上下文长度 */
    private int maxContextLength = 8192;
  }

  /** 日志配置属性 */
  @Data
  public static class LogProperties {

    /** 是否启用详细日志 */
    private boolean verbose = false;

    /** 是否记录表达式执行日志 */
    private boolean logExpressions = false;

    /** 是否记录元数据变更日志 */
    private boolean logChanges = true;

    /** 是否记录性能指标 */
    private boolean logPerformance = false;
  }

  /** 存储配置属性 */
  @Data
  public static class StorageProperties {

    /** 存储类型 (MEMORY, FILE, DATABASE) */
    private String type = "MEMORY";

    /** 文件存储路径 */
    private String filePath = "${user.home}/.bone/smartmeta";

    /** 数据库URL */
    private String databaseUrl;

    /** 数据库用户名 */
    private String databaseUsername;

    /** 数据库密码 */
    private String databasePassword;

    /** 是否启用自动备份 */
    private boolean autoBackupEnabled = false;

    /** 备份间隔（毫秒） */
    private long backupInterval = 86400000; // 默认24小时

    /** 获取存储类型 */
    public String getType() {
      return type;
    }
  }

  /** 获取存储类型枚举 */
  public StorageType getStorageType() {
    try {
      return StorageType.valueOf(storage.getType().toUpperCase());
    } catch (Exception e) {
      return StorageType.MEMORY;
    }
  }

  /** 获取默认工作域 */
  public String getDefaultDomain() {
    return defaultDomain;
  }

  /** 获取AI配置 */
  public AiProperties getAi() {
    return ai;
  }

  /** 检查是否启用热重载 */
  public boolean isHotReloadEnabled() {
    return hotReload;
  }

  /** 检查是否启用AI增强 */
  public boolean isAiEnhancementEnabled() {
    return aiEnhancementEnabled;
  }

  /** 获取热重载间隔（毫秒） */
  public long getHotReloadInterval() {
    // 默认返回5000毫秒（5秒）
    return 5000;
  }

  /** 存储类型枚举 */
  public enum StorageType {
    MEMORY,
    FILE,
    DATABASE
  }
}
