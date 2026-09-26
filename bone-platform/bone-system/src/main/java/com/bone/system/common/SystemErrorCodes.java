package com.bone.system.common;

/**
 * bone-system 业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md}）。
 *
 * <p><b>为何需要这一层，而不是直接写中文消息</b>：{@code throw new BizException("xxx失败")} 让前端、监控、告警 只能按中文 message
 * 分类——文案一改，聚合口径即断（错误码登记 §2「可聚合」「可 i18n」）。稳定码是跨系统契约， 中文说明只是 fallback。
 *
 * <p><b>用法</b>：抛出走 {@link SystemErrors#of(String, Object)}——{@code throw
 * SystemErrors.of(SystemErrorCodes.CONFIG_NOT_FOUND, configId)}。HTTP 状态由 {@link SystemErrors} 的「码 →
 * 状态」表提供，<strong>不要</strong>在抛出点再手写状态数字：状态与码各写一处即会漂移，且不一致时无机制发现。
 *
 * <p><b>本类只承载「稳定的业务码字符串 + 语义」</b>，不承载状态（真源见 {@link SystemErrors}），也不承载文案。
 *
 * <p><b>命名</b>：{@code SYS_} 为系统模块前缀，格式 {@code {DOMAIN_PREFIX}_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。
 */
public final class SystemErrorCodes {

  // ===== 配置（SYS_CONFIG_*）=====

  /** 配置不存在（含跨租户不可见）。 */
  public static final String CONFIG_NOT_FOUND = "SYS_CONFIG_NOT_FOUND";

  /** 配置键在本租户内已存在（唯一约束冲突）。 */
  public static final String CONFIG_KEY_CONFLICT = "SYS_CONFIG_KEY_CONFLICT";

  /** 配置类型非法（不是合法的 ConfigType 取值）。 */
  public static final String CONFIG_TYPE_INVALID = "SYS_CONFIG_TYPE_INVALID";

  /** 配置键非法（空值或超出 {@code ConfigKey} 允许的长度）。 */
  public static final String CONFIG_KEY_INVALID = "SYS_CONFIG_KEY_INVALID";

  // ===== 配置快照（SYS_CONFIG_SNAPSHOT_*）=====

  /** 快照内容为空。 */
  public static final String CONFIG_SNAPSHOT_EMPTY = "SYS_CONFIG_SNAPSHOT_EMPTY";

  /** 快照结构不正确（JSON 解析失败或缺 configs 数组）。 */
  public static final String CONFIG_SNAPSHOT_INVALID = "SYS_CONFIG_SNAPSHOT_INVALID";

  /** 快照条目数超过单批上限。 */
  public static final String CONFIG_SNAPSHOT_TOO_LARGE = "SYS_CONFIG_SNAPSHOT_TOO_LARGE";

  /** 读取上传的快照文件失败。 */
  public static final String CONFIG_SNAPSHOT_READ_FAILED = "SYS_CONFIG_SNAPSHOT_READ_FAILED";

  // ===== 字典（SYS_DICT_*，两级模型见 doc/design/modules/7a）=====

  /** 字典类型不存在（含跨租户不可见）。 */
  public static final String DICT_TYPE_NOT_FOUND = "SYS_DICT_TYPE_NOT_FOUND";

  /** 同一租户作用域内该类型编码已存在。 */
  public static final String DICT_TYPE_CODE_CONFLICT = "SYS_DICT_TYPE_CODE_CONFLICT";

  /** 内置字典类型不可删除/不可改编码，或该类型的项对租户只读。 */
  public static final String DICT_TYPE_READONLY = "SYS_DICT_TYPE_READONLY";

  /** 字典类型下仍有项，不允许删除。 */
  public static final String DICT_TYPE_IN_USE = "SYS_DICT_TYPE_IN_USE";

  /** 字典项不存在（含跨租户不可见）。 */
  public static final String DICT_ITEM_NOT_FOUND = "SYS_DICT_ITEM_NOT_FOUND";

  /** 同一类型下该 code 已存在（唯一键 uk_dict_item）。 */
  public static final String DICT_ITEM_CODE_CONFLICT = "SYS_DICT_ITEM_CODE_CONFLICT";

  /** 字典项仍有子项，不允许删除（级联树需先删叶子）。 */
  public static final String DICT_ITEM_HAS_CHILDREN = "SYS_DICT_ITEM_HAS_CHILDREN";

  /** 父项编码在同类型下不存在。 */
  public static final String DICT_PARENT_NOT_FOUND = "SYS_DICT_PARENT_NOT_FOUND";

  /** 层级关系不存在（该项尚未挂到指定层级视图）。 */
  public static final String DICT_HIERARCHY_NOT_FOUND = "SYS_DICT_HIERARCHY_NOT_FOUND";

  /** 非 CASCADE 类值域不允许设置父项。 */
  public static final String DICT_PARENT_NOT_ALLOWED = "SYS_DICT_PARENT_NOT_ALLOWED";

  /** 移动后会出现父子环。 */
  public static final String DICT_CYCLE_DETECTED = "SYS_DICT_CYCLE_DETECTED";

  /** 层级深度超过该值域的 maxDepth。 */
  public static final String DICT_CASCADE_DEPTH_EXCEEDED = "SYS_DICT_CASCADE_DEPTH_EXCEEDED";

  /** 字典编码非法（空值、超长或含空白）。 */
  public static final String DICT_CODE_INVALID = "SYS_DICT_CODE_INVALID";

  /** 值域分类非法（不是 ENUM/LIST/CASCADE）。 */
  public static final String DICT_CATEGORY_INVALID = "SYS_DICT_CATEGORY_INVALID";

  /** 绑定的枚举类不可用（未绑定、类不存在、不是枚举或不在允许包内）。 */
  public static final String DICT_ENUM_CLASS_INVALID = "SYS_DICT_ENUM_CLASS_INVALID";

  /** 导入条目数超过单批上限。 */
  public static final String DICT_IMPORT_TOO_LARGE = "SYS_DICT_IMPORT_TOO_LARGE";

  /** 值不符合值域定义（数据类型或格式正则不匹配）。 */
  public static final String DICT_VALUE_INVALID = "SYS_DICT_VALUE_INVALID";

  /** 生效区间非法（开始时间晚于结束时间）。 */
  public static final String DICT_EFFECTIVE_RANGE_INVALID = "SYS_DICT_EFFECTIVE_RANGE_INVALID";

  // ===== 告警（SYS_ALERT_*）=====

  /** 告警规则不存在（含跨租户不可见）。 */
  public static final String ALERT_RULE_NOT_FOUND = "SYS_ALERT_RULE_NOT_FOUND";

  /** 告警记录不存在（含跨租户不可见）。 */
  public static final String ALERT_RECORD_NOT_FOUND = "SYS_ALERT_RECORD_NOT_FOUND";

  /** 告警级别非法（不是合法的 AlertLevel 取值）。 */
  public static final String ALERT_LEVEL_INVALID = "SYS_ALERT_LEVEL_INVALID";

  /** 告警规则入参非法（指标名或阈值超出值对象允许范围）。 */
  public static final String ALERT_RULE_INVALID = "SYS_ALERT_RULE_INVALID";

  /** 告警状态非法（不是合法的 AlertStatus 取值）。 */
  public static final String ALERT_STATUS_INVALID = "SYS_ALERT_STATUS_INVALID";

  // ===== 定时任务（SYS_SCHEDULE_TASK_*）=====

  /** 定时任务不存在（含跨租户不可见）。 */
  public static final String SCHEDULE_TASK_NOT_FOUND = "SYS_SCHEDULE_TASK_NOT_FOUND";

  /** 任务状态非法（不是合法的 TaskStatus 取值）。 */
  public static final String SCHEDULE_TASK_STATUS_INVALID = "SYS_SCHEDULE_TASK_STATUS_INVALID";

  /** 任务处理器 bean 不存在或未实现 {@code TaskHandler}（手动执行时发现）。 */
  public static final String SCHEDULE_TASK_HANDLER_NOT_FOUND =
      "SYS_SCHEDULE_TASK_HANDLER_NOT_FOUND";

  /** 任务手动执行失败（处理器抛出异常）。 */
  public static final String SCHEDULE_TASK_RUN_FAILED = "SYS_SCHEDULE_TASK_RUN_FAILED";

  // ===== 日志（SYS_LOG_*）=====

  /** 日志不存在（含跨租户不可见）。 */
  public static final String LOG_NOT_FOUND = "SYS_LOG_NOT_FOUND";

  /** 日志级别非法（不是合法的 LogLevel 取值）。 */
  public static final String LOG_LEVEL_INVALID = "SYS_LOG_LEVEL_INVALID";

  private SystemErrorCodes() {}
}
