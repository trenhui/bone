package com.bone.masterdata.domain.model.template;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 域模板版本快照（UC-P2）：发布版本时冻结当时的 schema，供租户比对与跟随升级。
 *
 * <p>非租户作用域：与 {@link DomainTemplate} 同为平台全局目录（tenant_id 列保留，DEFAULT 0），理由见其 javadoc。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_template_version")
public class TemplateVersion extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "template_id")
  private Long templateId;

  @Column(name = "version_number")
  private String versionNumber;

  @Column(name = "change_log")
  private String changeLog;

  @Column(name = "field_schema")
  private String fieldSchema;

  @Column(name = "rule_schema")
  private String ruleSchema;

  @Column(name = "category_schema")
  private String categorySchema;

  private LocalDateTime createdAt;

  public static TemplateVersion snapshot(
      Long id,
      Long templateId,
      String versionNumber,
      String changeLog,
      String fieldSchema,
      String ruleSchema,
      String categorySchema) {
    TemplateVersion version = new TemplateVersion();
    version.id = id;
    version.templateId = templateId;
    version.versionNumber = versionNumber;
    version.changeLog = changeLog;
    version.fieldSchema = fieldSchema;
    version.ruleSchema = ruleSchema;
    version.categorySchema = categorySchema;
    version.createdAt = LocalDateTime.now();
    return version;
  }
}
