package com.bone.metadata.sdk.sql.template;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import com.bone.metadata.sdk.domain.exception.TemplateSecurityException;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模板安全校验器的关键字判别力测试（2026-10-24 随 wf_active_instance 物理删除通道一并落地）。
 *
 * <p><b>核心命题</b>：禁用表必须只禁「数据访问层越界」的关键字（DDL / 叠查询 / exec）， <b>不得把合法 DML 一棍子打死</b>——{@code DELETE}
 * 是「原子释放活动槽位」的唯一正确语义 （软删不释放主键会把「终态后可重提」永久焊死）。旧实现曾把 {@code delete} 加进禁用表， 任何需要物理删除的 SDK
 * 仓储直接无法装配（bean 初始化即炸）。
 *
 * <p><b>判别性设计（防假绿）</b>：{@link #keywordAsSubstringOfIdentifier_isAllowed()} 钉死词边界锚定—— {@code
 * drop_logs}/{@code deleted_at} 里的禁用词是标识符子串，{@code contains("drop")} 式退化实现 会把它们误杀；删掉正则里的 {@code
 * \\b} 本用例立即变红。
 */
class TemplateSecurityValidatorTest {

  private final TemplateSecurityValidator validator =
      new TemplateSecurityValidator(new SqlConfigProperties());

  @Test
  @DisplayName("物理 DELETE 是合法 DML（释放活动槽位），不得被误禁")
  void deleteTemplate_isAllowed() {
    assertDoesNotThrow(
        () ->
            validator.validateContent(
                "DELETE FROM wf_active_instance WHERE tenant_id = #{tenantId}"
                    + " AND instance_id = #{instanceId}",
                descriptor()));
  }

  @Test
  @DisplayName("软删列用法（deleted = 0/1）不得被误禁——禁用词是标识符子串")
  void softDeleteColumnUsage_isAllowed() {
    assertDoesNotThrow(
        () ->
            validator.validateContent(
                "SELECT id, entity_code FROM wf_binding WHERE entity_code = #{entityCode}"
                    + " AND tenant_id = #{tenantId} AND enabled = 1 AND deleted = 0",
                descriptor()));
    assertDoesNotThrow(
        () ->
            validator.validateContent(
                "UPDATE wf_task SET deleted = 1, updated_by = #{operatorId} WHERE id = #{id}",
                descriptor()));
  }

  @Test
  @DisplayName("DROP 必须拒绝：数据访问模板出现 DDL 即越界")
  void dropTemplate_isRejected() {
    assertThrows(
        TemplateSecurityException.class,
        () -> validator.validateContent("DROP TABLE wf_instance", descriptor()));
  }

  @Test
  @DisplayName("TRUNCATE 必须拒绝")
  void truncateTemplate_isRejected() {
    assertThrows(
        TemplateSecurityException.class,
        () -> validator.validateContent("TRUNCATE wf_status_outbox", descriptor()));
  }

  @Test
  @DisplayName("UNION ALL 必须拒绝：堆叠查询是经典注入形态")
  void unionAllTemplate_isRejected() {
    assertThrows(
        TemplateSecurityException.class,
        () ->
            validator.validateContent(
                "SELECT id FROM wf_task UNION ALL SELECT id FROM wf_instance", descriptor()));
  }

  @Test
  @DisplayName("EXEC 必须拒绝：存储过程调用不在数据访问契约内")
  void execTemplate_isRejected() {
    assertThrows(
        TemplateSecurityException.class,
        () -> validator.validateContent("EXEC sp_wf_purge", descriptor()));
  }

  @Test
  @DisplayName("禁用词作为标识符子串（drop_logs / deleted_at）不得误杀——词边界必须锚定")
  void keywordAsSubstringOfIdentifier_isAllowed() {
    assertDoesNotThrow(
        () ->
            validator.validateContent(
                "SELECT deleted_at FROM drop_logs WHERE deleted = 0", descriptor()));
  }

  private static TemplateDescriptor descriptor() {
    return TemplateDescriptor.builder()
        .templateId("com.bone.WfActiveInstanceSdkRepository.deleteActivePointer")
        .sourceUri("annotation://com.bone.WfActiveInstanceSdkRepository.deleteActivePointer")
        .format(SqlTemplateType.MYBATIS)
        .tags(Collections.singletonMap("source", "annotation"))
        .version("1.0")
        .build();
  }
}
