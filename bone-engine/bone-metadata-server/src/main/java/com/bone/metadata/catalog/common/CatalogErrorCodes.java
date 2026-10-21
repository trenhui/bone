package com.bone.metadata.catalog.common;

/**
 * catalog（server 侧）业务错误码。
 *
 * <p><b>命名</b>：{@code META_} 前缀，格式 {@code {DOMAIN}_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。 与 {@code
 * com.bone.metadata.engine.runtime.MetadataErrorCodes} 同前缀但不同限界上下文， 二者不可混用（engine 侧码由其模块自行登记）。
 *
 * <p>本类只承载稳定的业务码字符串 + 语义，<b>不承载状态</b>（真源见 {@link CatalogErrors}）， 也不承载文案（文案由抛出点补充，便于携带业务标识）。
 */
public final class CatalogErrorCodes {

  // ===== 实体（catalog META_ENTITY_*）=====

  /** 实体不存在（含跨租户不可见）。 */
  public static final String ENTITY_NOT_FOUND = "META_ENTITY_NOT_FOUND";

  /** 实体编码已存在（软删实体曾发布/归档时编码不可复用）。 */
  public static final String ENTITY_CODE_CONFLICT = "META_ENTITY_CODE_CONFLICT";

  /** 实体表名已存在（软删实体曾发布/归档时表名不可复用）。 */
  public static final String ENTITY_TABLE_NAME_CONFLICT = "META_ENTITY_TABLE_NAME_CONFLICT";

  /** 复制实体必须提供新编码。 */
  public static final String ENTITY_COPY_CODE_REQUIRED = "META_ENTITY_COPY_CODE_REQUIRED";

  /** 复制实体必须提供新表名。 */
  public static final String ENTITY_COPY_TABLE_NAME_REQUIRED =
      "META_ENTITY_COPY_TABLE_NAME_REQUIRED";

  /** 仅 RUNTIME 实体支持物理结构维护（非 RUNTIME 实体无物理表）。 */
  public static final String ENTITY_NOT_RUNTIME = "META_ENTITY_NOT_RUNTIME";

  /** 自动编号配置非法（缺 field/prefix 或 reset 取值越界，或整体非 JSON）。 */
  public static final String AUTO_NUMBER_CONFIG_INVALID = "META_AUTO_NUMBER_CONFIG_INVALID";

  /**
   * 行级 ACL 策略配置非法（非 JSON / 缺 rules / 规则缺 name）。
   *
   * <p>引擎对非法策略的兜底是「回退 owner 单规则」，即安全策略静默收紧而不报错——写入口必须挡住，否则建模者以为配了审批人可见、线上审批人却 404。
   */
  public static final String ROW_POLICIES_INVALID = "META_ROW_POLICIES_INVALID";

  // ===== 字段（catalog META_FIELD_*）=====

  /** 字段不存在（含跨租户不可见）。 */
  public static final String FIELD_NOT_FOUND = "META_FIELD_NOT_FOUND";

  /** 字段编码在同实体下已存在。 */
  public static final String FIELD_CODE_CONFLICT = "META_FIELD_CODE_CONFLICT";

  /**
   * 枚举选项源声明非法（非 JSON / type 越界 / ref 缺失 / external 缺 connectorId）。
   *
   * <p>写入口守卫：与 {@link #ROW_POLICIES_INVALID} 同姿态——声明非法必须在保存时挡住， 不能等发布物化时才炸（doc/design/modules/11
   * §4.2/§9）。
   */
  public static final String ENUM_SOURCE_INVALID = "META_ENUM_SOURCE_INVALID";

  /**
   * 枚举选项源解析失败（发布物化 / 手动刷新时 dict/reference 真源不可达，发布阻断）。
   *
   * <p>fail-closed：写路径不接受「静默用旧快照」——运营以为改了字典生效，实际跑的是上个月的码表 （doc/design/modules/11 §4.3）。读路径（预览/漂移）由
   * ENUM_SOURCE_UNRESOLVED 问题项表达，不走异常。
   */
  public static final String ENUM_SOURCE_RESOLVE_FAILED = "META_ENUM_SOURCE_RESOLVE_FAILED";

  /**
   * L0→L1 迁移：目标值与本地枚举存在「同码不同义 / 同义不同码」告警，需建模者确认后才可绑定。
   *
   * <p>MDM match/merge 的标准姿态：机器报疑、人做裁决——<b>告警不阻断</b>，但必须显式确认， 避免「静默把 1=男 的旧数据挂到 1=未知
   * 的新值域上」这类不可逆语义错位。
   */
  public static final String MIGRATE_CONFLICT_UNCONFIRMED = "META_MIGRATE_CONFLICT_UNCONFIRMED";

  /** L0→L1 迁移：字段已绑定真源（重复迁移会把既有快照与覆写关系冲掉）。 */
  public static final String MIGRATE_ALREADY_BOUND = "META_MIGRATE_ALREADY_BOUND";

  /** L0→L1 迁移：目标值域不可达 / 不存在（无法做 diff，迁移无从判定）。 */
  public static final String MIGRATE_TARGET_UNRESOLVED = "META_MIGRATE_TARGET_UNRESOLVED";

  /** L0→L1 迁移：「并入字典」分支写入真源失败（fail-closed：字典侧没建成就绝不绑定）。 */
  public static final String MIGRATE_MERGE_FAILED = "META_MIGRATE_MERGE_FAILED";

  // ===== 关系（catalog META_RELATION_*）=====

  /** 关系不存在（含跨租户不可见）。 */
  public static final String RELATION_NOT_FOUND = "META_RELATION_NOT_FOUND";

  // ===== 状态机动作定义（catalog META_ACTION_DEF_*）=====

  /** 动作定义不存在（含已软删）。 */
  public static final String ACTION_DEF_NOT_FOUND = "META_ACTION_DEF_NOT_FOUND";

  /** 动作定义入参非法（逃逸动作缺 handlerBean / 未声明幂等）。 */
  public static final String ACTION_DEF_INVALID = "META_ACTION_DEF_INVALID";

  // ===== 模板（catalog META_TEMPLATE_*）=====

  /** 模板不存在（含跨租户不可见）。 */
  public static final String TEMPLATE_NOT_FOUND = "META_TEMPLATE_NOT_FOUND";

  /** 模板编码已存在。 */
  public static final String TEMPLATE_CODE_CONFLICT = "META_TEMPLATE_CODE_CONFLICT";

  /** 模板尚未发布，不可实例化（仅status==1 的已发布模板可实例化）。 */
  public static final String TEMPLATE_NOT_PUBLISHED = "META_TEMPLATE_NOT_PUBLISHED";

  /** 模板 ID 不能为空（实例化入参缺失）。 */
  public static final String TEMPLATE_ID_REQUIRED = "META_TEMPLATE_ID_REQUIRED";

  // ===== 导入（catalog META_IMPORT_*）=====

  /** 物理表不存在，无法导入（发布期物理结构不存在）。 */
  public static final String PHYSICAL_TABLE_NOT_FOUND = "META_PHYSICAL_TABLE_NOT_FOUND";

  // ===== 界面 schema（catalog META_UI_SCHEMA_*，迁移 0046）=====

  /** 界面 schema JSON 非法（不可解析 / 顶层缺 views / 大版本非 1）。 */
  public static final String UI_SCHEMA_INVALID = "META_UI_SCHEMA_INVALID";

  /** 界面 schema 并发冲突（If-Match 锁版本与库内不一致，412 缺前置条件见 HTTP 层）。 */
  public static final String UI_SCHEMA_VERSION_CONFLICT = "META_UI_SCHEMA_VERSION_CONFLICT";

  /** 模板界面资产缺失（ui_schema_json 为空，跟随入口保持 disabled）。 */
  public static final String UI_SCHEMA_TEMPLATE_ASSET_INCOMPLETE =
      "META_UI_SCHEMA_TEMPLATE_ASSET_INCOMPLETE";

  /** 模板跟随合入冲突未确认（merge-confirm 时模板又发版或草稿被并发修改）。 */
  public static final String UI_SCHEMA_MERGE_CONFLICT = "META_UI_SCHEMA_MERGE_CONFLICT";

  /** 界面 schema 存储不可用（迁移 0046 未执行，meta_entity_ui_schema 缺失；写路径 503，读路径静默降级）。 */
  public static final String UI_SCHEMA_STORE_UNAVAILABLE = "META_UI_SCHEMA_STORE_UNAVAILABLE";

  /** 缺 If-Match 前置条件（行已存在时防误覆盖，刷新后重试）。 */
  public static final String UI_SCHEMA_PRECONDITION_REQUIRED =
      "META_UI_SCHEMA_PRECONDITION_REQUIRED";

  // ===== 发布闸门（catalog META_PUBLISH_*）=====

  /**
   * 发布闸门拦截：实体存在 ERROR 级校验问题（命名/字段/关系/物理漂移/UI-REF 引用），不允许发布。
   *
   * <p>与 {@code getPublishPreview} 的 {@code hasBlockingErrors/runnable} 同源：预览说「不可发布」，
   * 服务端必须兜底拦截，否则前端按钮置灰形同虚设（P1-4）。
   */
  public static final String META_PUBLISH_BLOCKED = "META_PUBLISH_BLOCKED";

  // ===== 发布期漂移（catalog META_DOMAIN_*）=====

  /**
   * 发布期物理结构漂移拦截：模型类型与物理列类型不兼容（doc2a §328，409 Conflict）。
   *
   * <p>唯一在收口前就带码的码（{@code new BizException(409, e.getMessage(), META_DOMAIN_ERROR, e)}），
   * 保留原语义与状态不变。
   */
  public static final String META_DOMAIN_ERROR = "META_DOMAIN_ERROR";

  private CatalogErrorCodes() {}
}
