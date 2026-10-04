package com.bone.iam.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户离场清退：每个租户作用域表都必须被一条 {@code DELETE FROM {表} WHERE tenant_id = ?} 命中。
 *
 * <p>为什么必须有这个测试：{@code TENANT_TABLES} 是一份**手工维护**的清单，而 DDL 里的租户表会随 模块迭代不断增加。2026-10-03 实测发现 {@code
 * sys_config} / {@code sys_dict} 已登记，但它们的 5 张子表 （{@code sys_config_history}、{@code
 * sys_dict_type}、{@code sys_dict_hierarchy}、{@code sys_dict_item}、 {@code sys_dict_item_text}）全部漏登记
 * —— 父表被清干净、子表数据在租户离场后残留，且**没有任何报错**。
 *
 * <p>门禁 {@code scripts/check-tenant-deletion-coverage.py} 能静态发现这类缺口，但它读的是源文件文本；
 * 本测试从**行为侧**兜住：真正跑一遍清退，断言每张已知租户表都收到了 DELETE。两者互补 ——
 * 门禁防止清单漏登，本测试防止清单登了却没真正执行到（比如被异常吞掉、或某张表被误加进条件分支）。
 *
 * <p>不依赖真实数据库：{@link NamedParameterJdbcTemplate} 用Mockito 收集调用参数即可。
 */
@ExtendWith(MockitoExtension.class)
class TenantDeletionGatewayAdapterTest {

  @Mock private NamedParameterJdbcTemplate jdbcTemplate;

  /**
   * {@code purgeTenantData} 会调 {@code getJdbcTemplate().execute(...)} 开关外键校验， Mockito 默认深桩返回 null
   * 会直接 NPE —— 必须显式给出底层 JdbcTemplate mock。
   */
  @Mock private JdbcTemplate rawJdbcTemplate;

  /**
   * 挂上底层 {@link JdbcTemplate}。Mockito 默认深桩返回 null，{@code purgeTenantData} 里的 {@code
   * getJdbcTemplate().execute(...)} 会直接 NPE。
   *
   * <p>刻意做成显式调用而非 {@code @BeforeEach}：{@link #purgeIsTransactional()} 只做反射断言、 不碰
   * jdbcTemplate，用 @BeforeEach 会被 Mockito 严格模式判为 unnecessary stubbing 而失败。
   */
  private void wireUnderlyingJdbcTemplate() {
    when(jdbcTemplate.getJdbcTemplate()).thenReturn(rawJdbcTemplate);
  }

  /**
   * 已登记进 {@code TENANT_TABLES} 的租户表**采样子集**（选取跨模块代表值：iam / system / masterdata / integration /
   * metadata / generator / extension / notification）。
   *
   * <p>刻意只采样、不逐张全列：清单有 60+ 张，本测试要证明的是「登记了就会真的删」这条不变量。 <b>覆盖面由静态门禁 {@code
   * scripts/check-tenant-deletion-coverage.py} 负责</b> —— 它比对 {@code bone-init.sql} 里全部含 {@code
   * tenant_id} 的表，能抓到「某张表压根没登记」；本测试抓的是另一类： 登记了但执行路径没走到。两者互补，不可互相替代。
   *
   * <p>换言之：从清单里删掉一张<b>不在此采样内</b>的表，本测试不会失败（那是门禁的职责）；删掉一张 <b>在采样内</b>的表，本测试必定失败。已用探针验证过后者（移除 {@code
   * ntf_message} → 断言失败并点名该表）。
   */
  private static final List<String> SAMPLED_TENANT_TABLES =
      List.of(
          "iam_account",
          "iam_role",
          "iam_refresh_token",
          "sys_config",
          "sys_log",
          "sys_dict",
          "mdm_entity",
          "mdm_record",
          "mdm_qcheck_detail",
          "int_flow",
          "int_connector",
          "meta_entity",
          "meta_field",
          "gen_table_metadata",
          "gen_data_source",
          "exts_extension_impl",
          "ntf_message");

  @Test
  void everyRegisteredTableGetsDeleted() {
    wireUnderlyingJdbcTemplate();
    new TenantDeletionGatewayAdapter(jdbcTemplate).purgeTenantData(1001L);

    List<String> deletedTables = captureDeletedTables();
    assertThat(deletedTables)
        .as("清退清单里登记的表必须逐张收到 DELETE，否则租户离场会残留数据")
        .containsAll(SAMPLED_TENANT_TABLES);
  }

  /**
   * 每条 DELETE 都必须带 {@code tenant_id} 条件。
   *
   * <p>漏掉租户条件 = 全表清空，是最坏的数据事故形态：租户A 离场把租户 B 的数据也删了。 断言 {@code WHERE} 子句存在，比断言完整 SQL 字符串更能容忍实现细节调整。
   */
  @Test
  void everyDeleteIsScopedByTenantId() {
    wireUnderlyingJdbcTemplate();
    new TenantDeletionGatewayAdapter(jdbcTemplate).purgeTenantData(1001L);

    List<String> unscoped = new ArrayList<>();
    for (String sql : captureAllSql()) {
      String normalized = sql.replaceAll("\\s+", " ").trim();
      if (normalized.startsWith("DELETE FROM") && !normalized.contains("tenant_id =")) {
        unscoped.add(normalized);
      }
    }
    assertThat(unscoped).as("DELETE 必须带tenant_id 条件，否则会清空全表").isEmpty();
  }

  /** 清退必须在关闭外键校验的事务内进行，否则 60+ 表间外键顺序会导致中途失败只删了一半。 */
  @Test
  void foreignKeyChecksAreDisabledAroundTheBatch() {
    wireUnderlyingJdbcTemplate();
    new TenantDeletionGatewayAdapter(jdbcTemplate).purgeTenantData(1001L);

    verify(rawJdbcTemplate, atLeastOnce()).execute(contains("FOREIGN_KEY_CHECKS = 0"));
    verify(rawJdbcTemplate, atLeastOnce()).execute(contains("FOREIGN_KEY_CHECKS = 1"));
  }

  /** 清退必须打上 {@code @Transactional}：SET 与全部 DELETE 要在同一连接/事务内，否则 SET 泄漏到连接池。 */
  @Test
  void purgeIsTransactional() throws NoSuchMethodException {
    Method purge = TenantDeletionGatewayAdapter.class.getMethod("purgeTenantData", long.class);
    assertThat(purge.getAnnotation(Transactional.class))
        .as("purgeTenantData 丢了 @Transactional，SET FOREIGN_KEY_CHECKS 会泄漏到连接池后续借用者")
        .isNotNull();
  }

  /** 收集所有以 DELETE 开头的 SQL（覆盖参数化重载）。 */
  private List<String> captureAllSql() {
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate, atLeastOnce())
        .update(sqlCaptor.capture(), ArgumentMatchers.any(MapSqlParameterSource.class));
    return sqlCaptor.getAllValues();
  }

  /** 提取被清退的表名（形如 {@code DELETE FROM sys_config WHERE ...}）。 */
  private List<String> captureDeletedTables() {
    List<String> tables = new ArrayList<>();
    for (String sql : captureAllSql()) {
      String normalized = sql.replaceAll("\\s+", " ").trim();
      if (normalized.startsWith("DELETE FROM ")) {
        String rest = normalized.substring("DELETE FROM ".length());
        int space = rest.indexOf(' ');
        tables.add(space > 0 ? rest.substring(0, space) : rest);
      }
    }
    return tables;
  }
}
