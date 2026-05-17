package com.bone.studio.generator.common;

/** Studio Generator HTTP 路径（唯一前缀，见 doc/architecture/Bone-API-规范.md §13.1.1）。 */
public final class GeneratorApiPaths {

  public static final String V1_PREFIX = "/api/v1/generator";

  public static final String DATA_SOURCES = V1_PREFIX + "/data-sources";

  public static final String TABLE_METADATA = V1_PREFIX + "/table-metadata";

  public static final String CODE_TEMPLATES = V1_PREFIX + "/code-templates";

  public static final String CODE_GENERATION = V1_PREFIX + "/code-generation";

  /** 同步代码生成（字符串模板内核，原 /generate） */
  public static final String GENERATION_TASKS = V1_PREFIX + "/generation-tasks";

  /**
   * 已发布 meta_* 快照（只读，供 CATALOG_SNAPSHOT 生成）；非 metadata-server catalog CRUD。
   */
  public static final String METADATA_ENTITY_SNAPSHOTS = V1_PREFIX + "/metadata-entity-snapshots";

  public static final String CAPABILITIES = V1_PREFIX + "/capabilities";

  private GeneratorApiPaths() {}
}
