package com.bone.metadata.catalog.common;

/**
 * catalog（server 侧）业务错误码常量。
 *
 * <p><b>为何不并入 {@code com.bone.metadata.engine.runtime.MetadataErrorCodes}</b>：该类位于
 * bone-metadata-engine-runtime，当前被 Comet change {@code refactor/metadata-engine-boundary-ddd} 占用；
 * 本类只承载 catalog 限界上下文自有码，待 engine 归档后再评估是否合并。
 *
 * <p>登记：{@code META_DOMAIN_ERROR} 见 {@code config/i18n/errorcode-baseline.json} 白名单与 doc2a
 * §328（发布期漂移冲突 409 契约）。
 */
public final class CatalogErrorCodes {

  /** 发布期物理结构漂移拦截：模型类型与物理列类型不兼容（doc2a §328，409 Conflict）。 */
  public static final String META_DOMAIN_ERROR = "META_DOMAIN_ERROR";

  private CatalogErrorCodes() {}
}
