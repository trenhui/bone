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

  // ===== 字段（catalog META_FIELD_*）=====

  /** 字段不存在（含跨租户不可见）。 */
  public static final String FIELD_NOT_FOUND = "META_FIELD_NOT_FOUND";

  /** 字段编码在同实体下已存在。 */
  public static final String FIELD_CODE_CONFLICT = "META_FIELD_CODE_CONFLICT";

  // ===== 关系（catalog META_RELATION_*）=====

  /** 关系不存在（含跨租户不可见）。 */
  public static final String RELATION_NOT_FOUND = "META_RELATION_NOT_FOUND";

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
