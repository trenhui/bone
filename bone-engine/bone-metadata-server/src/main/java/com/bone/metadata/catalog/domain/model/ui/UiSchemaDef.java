package com.bone.metadata.catalog.domain.model.ui;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.util.Date;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 业务对象界面 schema 聚合根（团队级，迁移 0046）。
 *
 * <p>与 {@code MetaEntity} 不同的写生命周期：草稿可随时改（PUT 全量替换幂等），发布才冻结快照。 并发用 SDK 原生 {@code @Version}
 * 乐观锁（ADR-0031），禁 {@code SELECT ... FOR UPDATE}。
 *
 * <p>本聚合只做<b>不变量守护</b>（版本断言、大小上限、白名单常量）：引用存在性（fieldCode / actionCode /
 * policyCode）需读其它聚合，落在应用层端口（application 不直连 repository 以外的读由端口承载）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("meta_entity_ui_schema")
public class UiSchemaDef extends AbstractEntity<Long> {

  /** 协议大版本（锁 1；未知大版本显式拒绝，不猜渲染）。 */
  public static final int SCHEMA_VERSION = 1;

  /** 已知增量版本（渲染器遇更高 minor 只告警不拒绝，前向兼容）。 */
  public static final int SCHEMA_MINOR = 1;

  /** 草稿存前上限（MEDIUMTEXT 是上限不是目标；超限指引拆 section/减规则）。 */
  public static final int MAX_SCHEMA_BYTES = 256 * 1024;

  /** 条件嵌套深度上限（与前端引擎 MAX_COND_DEPTH 同值，发布校验 ERROR）。 */
  public static final int MAX_COND_DEPTH = 3;

  public static final String FOLLOW_PINNED = "pinned";

  public static final String FOLLOW_NOTIFY = "notify";

  /** controlKind 白名单（与 bone-ui-schema.schema.json 的 enum 同源；服务端测试反向断言一致性）。 */
  public static final Set<String> CONTROL_KINDS =
      Set.of(
          "INPUT",
          "TEXTAREA",
          "NUMBER",
          "SWITCH",
          "DATE",
          "DATETIME",
          "ENUM",
          "REFERENCE",
          "CHILD_TABLE",
          "ATTACHMENT",
          "MONEY",
          "STAT",
          "CHART",
          "TIMELINE",
          "COMPUTED",
          "QUOTE",
          "ALERT",
          "SLIDER");

  /** 条件比较符白名单（linkage when / submit operator 子集 / actionBar 门控复用同一套）。 */
  public static final Set<String> CONDITION_OPS =
      Set.of(
          "isNull",
          "isNotNull",
          "in",
          "notIn",
          "eq",
          "neq",
          "gt",
          "gte",
          "lt",
          "lte",
          "between",
          "earlierThan",
          "laterThan");

  /** submit 函数白名单（不开自定义函数名，封公式引擎口）。 */
  public static final Set<String> SUBMIT_FUNCTIONS =
      Set.of(
          "sum",
          "count",
          "max",
          "min",
          "compare",
          "balance",
          "unique",
          "consistency",
          "completeness");

  /** tier 上下文变量命名空间白名单（UI-REF-08）。 */
  public static final Set<String> TIER_NAMESPACES =
      Set.of("tier", "user", "org", "channel", "device");

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "entity_id", nullable = false)
  private Long entityId;

  @Column(name = "schema_json", nullable = false)
  private String schemaJson;

  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion;

  @Column(name = "schema_minor", nullable = false)
  private Integer schemaMinor;

  @Column(name = "published_schema_json")
  private String publishedSchemaJson;

  @Column(name = "published_version")
  private Integer publishedVersion;

  @Column(name = "base_template_code")
  private String baseTemplateCode;

  @Column(name = "base_version")
  private String baseVersion;

  @Column(name = "follow_mode", nullable = false)
  private String followMode;

  @Version
  @Column(name = "lock_version", nullable = false)
  private Long lockVersion;

  public static UiSchemaDef create(Long tenantId, Long entityId, String schemaJson, int minor) {
    assertSchemaSize(schemaJson);
    assertMinorRange(minor);
    UiSchemaDef def = new UiSchemaDef();
    def.tenantId = tenantId;
    def.entityId = entityId;
    def.schemaJson = schemaJson;
    def.schemaVersion = SCHEMA_VERSION;
    def.schemaMinor = minor;
    def.followMode = FOLLOW_PINNED;
    def.lockVersion = 0L;
    Date now = new Date();
    def.setCreatedAt(now);
    def.setUpdatedAt(now);
    def.setDeleted(false);
    return def;
  }

  /**
   * 幂等全量替换草稿。
   *
   * <p>乐观锁由 SDK 在 {@code update} 时强制（WHERE lock_version = :old）：调用方须先比对期望锁版本，
   * 本方法只推进业务字段，不自己加版本号（SDK 回写）。
   */
  public void replaceDraft(String schemaJson, int minor) {
    assertSchemaSize(schemaJson);
    assertMinorRange(minor);
    this.schemaJson = schemaJson;
    this.schemaMinor = minor;
    this.setUpdatedAt(new Date());
  }

  /** 发布快照：同事务内草稿→已发布冻结；快照失败则发布整体回滚，不出现半吊子。 */
  public void snapshotOnPublish(int entityVersion) {
    this.publishedSchemaJson = this.schemaJson;
    this.publishedVersion = entityVersion;
    this.setUpdatedAt(new Date());
  }

  /** 实例化写 base 指针（租户确认式跟随，永不自动推送）。 */
  public void markFromTemplate(String templateCode, String templateVersion) {
    this.baseTemplateCode = templateCode;
    this.baseVersion = templateVersion;
    this.followMode = FOLLOW_PINNED;
    this.setUpdatedAt(new Date());
  }

  public void enableNotifyFollow() {
    this.followMode = FOLLOW_NOTIFY;
    this.setUpdatedAt(new Date());
  }

  public static void assertSchemaSize(String schemaJson) {
    if (schemaJson == null || schemaJson.isBlank()) {
      throw new DomainException("界面 schema 不能为空");
    }
    if (schemaJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SCHEMA_BYTES) {
      throw new DomainException("界面 schema 超过 256KB 上限，请拆 section 或删减规则");
    }
  }

  public static void assertMinorRange(int minor) {
    if (minor < 1) {
      throw new DomainException("界面 schema minor 非法（须 ≥1）");
    }
  }
}
