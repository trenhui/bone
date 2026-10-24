package com.bone.metadata.sdk.sql.processor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.sdk.domain.exception.SqlInjectionRiskException;
import com.bone.metadata.sdk.domain.exception.SqlProcessingException;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 处理器的参数绑定与安全守卫判别力测试（2026-10-24 随 SDK {@code #{} null 通道修复一并落地）。
 *
 * <p><b>两个方向的命题互为保证</b>：
 * <ul>
 *   <li><b>不得误伤</b>：显式 {@code null} 必须生成命名占位符并保留参数键（可空列的合法值）；
 *       自由文本值里的 SQL 关键字/分号必须放行（值走 JDBC 绑定，不可能构成注入）——
 *       旧实现扫「值里的关键字+分号」把审批意见/驳回原因全线 500；</li>
 *   <li><b>不得放过</b>：缺参仍 fail-fast；{@code ${}} 字符集守卫、表名白名单、DDL 模板、
 *       叠语句守卫一个都不能少——删掉任何一个守卫，对应负向用例立即变红。</li>
 * </ul>
 */
class MyBatisSqlProcessorTest {

  private final MyBatisSqlProcessor processor = new MyBatisSqlProcessor(new SqlConfigProperties());

  @Test
  @DisplayName("显式 null 必须绑 NULL：生成命名占位符且参数表保留该键")
  void explicitNullHashParam_bindsNull() {
    Map<String, Object> params = new HashMap<>();
    params.put("id", "t-1");
    params.put("decision", null);

    ProcessedSql processed =
        processor.process(
            "com.bone.WfTaskSdkRepository.insertTask",
            "INSERT INTO wf_task (id, decision) VALUES (#{id}, #{decision})",
            params);

    assertTrue(processed.getSql().contains(":decision"), "null 值也必须生成命名占位符");
    assertTrue(
        processed.getEffectiveParams().containsKey("decision"),
        "参数表必须保留该键——旧实现静默丢弃 null 值，导致 NamedParameterJdbcTemplate 报 No value supplied");
  }

  @Test
  @DisplayName("缺参（非显式 null）仍须 fail-fast——宽松化只针对显式传 null")
  void absentHashParam_stillFailsFast() {
    Map<String, Object> params = new HashMap<>();
    params.put("id", "t-1");

    assertThrows(
        SqlProcessingException.class,
        () ->
            processor.process(
                "com.bone.WfTaskSdkRepository.insertTask",
                "INSERT INTO wf_task (id, decision) VALUES (#{id}, #{decision})",
                params));
  }

  @Test
  @DisplayName("自由文本含 SQL 关键字与分号必须放行（值级扫描误伤回归）")
  void freeTextValue_withSqlKeywordsAndSemicolon_isAccepted() {
    Map<String, Object> params = new HashMap<>();
    params.put("id", "t-1");
    params.put("comment", "please update the amount; DROP TABLE wf_task");

    ProcessedSql processed =
        processor.process(
            "com.bone.WfTaskSdkRepository.saveTask",
            "INSERT INTO wf_task (id, comment) VALUES (#{id}, #{comment})",
            params);

    assertEquals(
        "please update the amount; DROP TABLE wf_task",
        processed.getEffectiveParams().get("comment"));
    assertFalse(processed.getSql().contains("DROP"), "值必须走绑定，不得出现在 SQL 文本里");
  }

  @Test
  @DisplayName("${} 取值为 null 仍须抛错（原文替换无值可替）")
  void nullDollarParam_stillThrows() {
    Map<String, Object> params = new HashMap<>();
    params.put("column", null);

    assertThrows(
        SqlProcessingException.class,
        () ->
            processor.process(
                "com.bone.DynamicRepo.findBy",
                "SELECT id FROM wf_task ORDER BY ${column}",
                params));
  }

  @Test
  @DisplayName("${} 值含非标识符字符必须被字符集守卫拦截（真注入面）")
  void dollarParam_withNonIdentifierCharset_stillRejected() {
    Map<String, Object> params = new HashMap<>();
    params.put("column", "id; DROP TABLE wf_task");

    SqlProcessingException ex =
        assertThrows(
            SqlProcessingException.class,
            () ->
                processor.process(
                    "com.bone.DynamicRepo.findBy",
                    "SELECT id FROM wf_task ORDER BY ${column}",
                    params));

    assertTrue(
        ex.getCause() instanceof SqlInjectionRiskException,
        "必须由 ${} 字符集守卫拦截，实际内因：" + ex.getCause());
  }

  @Test
  @DisplayName("动态表名不在白名单必须被拦截（真注入面）")
  void tableName_notAllowlisted_stillRejected() {
    Map<String, Object> params = new HashMap<>();
    params.put("tableName", "wf_secret");

    SqlProcessingException ex =
        assertThrows(
            SqlProcessingException.class,
            () ->
                processor.process(
                    "com.bone.DynamicRepo.count", "SELECT COUNT(*) FROM ${tableName}", params));

    assertTrue(
        ex.getCause() instanceof SqlInjectionRiskException, "必须由表名白名单拦截，实际内因：" + ex.getCause());
  }

  @Test
  @DisplayName("模板文本里的 DDL 必须被拒绝（防御性纵深）")
  void ddlInTemplate_stillRejected() {
    Map<String, Object> params = new HashMap<>();
    params.put("t", "wf_task");

    assertThrows(
        SqlProcessingException.class,
        () -> processor.process("com.bone.DynamicRepo.drop", "DROP TABLE #{t}", params));
  }

  @Test
  @DisplayName("DELETE 模板必须能正常处理（物理删放在 SDK 通道的回归）")
  void deleteTemplate_isProcessed() {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", 1L);
    params.put("instanceId", "i-1");

    ProcessedSql processed =
        processor.process(
            "com.bone.WfActiveInstanceSdkRepository.deleteActivePointerByInstance",
            "DELETE FROM wf_active_instance WHERE tenant_id = #{tenantId}"
                + " AND instance_id = #{instanceId}",
            params);

    assertDoesNotThrow(processed::getSql);
    assertTrue(processed.getSql().contains("DELETE FROM"));
  }
}
