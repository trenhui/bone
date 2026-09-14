package com.bone.metadata.sdk.test.testcase;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import com.bone.metadata.sdk.extension.ExtensionContext;
import com.bone.metadata.sdk.extension.handler.EavHandler;
import com.bone.metadata.sdk.test.config.TestConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * EavHandler 专项测试：验证语法修复后的方言 upsert 与软删恢复语义。
 *
 * <p>背景：历史上 save 使用 PG 专属 {@code ON CONFLICT}，MySQL 下直接语法错误。修复后按 DatabaseType 分发 （对齐 JsonHandler
 * 先例）。CI 默认 H2 profile 跑 MERGE 真库链路；MYSQL/POSTGRESQL 分支由 SQL 字符串断言覆盖语法形态。
 */
@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
public class EavHandlerTest {

  @Autowired private NamedParameterJdbcOperations jdbc;

  private EavHandler eavHandler;

  @BeforeEach
  void setUp() {
    jdbc.getJdbcOperations().execute("DELETE FROM ext_data_eav");
    eavHandler = new EavHandler(jdbc);
  }

  private ExtensionContext context(Map<String, Object> props) {
    return ExtensionContext.of(null, null, null, "member", 1L, props, ExtensionMode.EAV);
  }

  @Test
  void saveShouldUpsertOnH2() {
    eavHandler.save(context(Map.of("level", "gold")));
    eavHandler.save(context(Map.of("level", " platinum ", "city", "hangzhou")));

    Map<String, Object> loaded = eavHandler.load(context(Map.of()));
    assertEquals(2, loaded.size());
    assertEquals(" platinum ", loaded.get("level"));
    assertEquals("hangzhou", loaded.get("city"));
  }

  @Test
  void saveShouldReviveSoftDeletedRowOnH2() {
    eavHandler.save(context(Map.of("level", "gold")));
    jdbc.getJdbcOperations()
        .execute("UPDATE ext_data_eav SET deleted = TRUE WHERE attr_key = 'level'");

    // 逻辑删除后 load 不可见，再次 save 应恢复而非插入重复主键
    assertTrue(eavHandler.load(context(Map.of())).isEmpty());
    eavHandler.save(context(Map.of("level", "silver")));

    Map<String, Object> loaded = eavHandler.load(context(Map.of()));
    assertEquals(1, loaded.size());
    assertEquals("silver", loaded.get("level"));
    assertEquals(
        1L,
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM ext_data_eav WHERE attr_key = 'level'", Map.of(), Long.class));
  }

  @Test
  void upsertSqlShouldUseDialectSpecificSyntax() {
    Map<DatabaseType, String> expectedSig =
        Map.of(
            DatabaseType.MYSQL, "ON DUPLICATE KEY UPDATE attr_value = :attrValue, deleted = FALSE",
            DatabaseType.POSTGRESQL, "ON CONFLICT (entity_type, entity_id, attr_key)",
            DatabaseType.H2, "MERGE INTO ext_data_eav");
    expectedSig.forEach(
        (type, sig) -> assertTrue(EavHandler.upsertSqlFor(type).contains(sig), type.name()));
  }

  @Test
  void saveWithEmptyPropsShouldBeNoop() {
    eavHandler.save(context(new LinkedHashMap<>()));
    assertEquals(
        0L, jdbc.queryForObject("SELECT COUNT(1) FROM ext_data_eav", Map.of(), Long.class));
  }
}
