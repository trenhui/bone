package com.bone.metadata.catalog.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.core.exception.DomainException;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 物理结构 DDL 的<b>真机 MySQL</b>语义验证（2026-10-05）。
 *
 * <p><b>为什么必须有真机</b>：H2 的 {@code MODE=MySQL} 只覆盖语法子集。实测它连 {@code CREATE TABLE ... ENGINE=InnoDB
 * DEFAULT CHARSET=utf8mb4} 都直接报语法错误 （{@code expected "UTF8"}），也无法反映真实 {@code information_schema}
 * 的行为。 因此 「这段 DDL 在 MySQL 上到底能不能跑、重复执行会不会炸、字符集落在哪」只能在真机上验， 这正是 {@link
 * JdbcPhysicalStructureGatewayAdapterIdempotencyTest}（文本契约）覆盖不到的部分。
 *
 * <p><b>连接参数全部外部注入，不写死密码</b>：系统属性 {@code bone.it.db.*} 优先，其次环境变量 {@code BONE_IT_DB_*}，都没有时用 {@code
 * localhost:3306/bone_it} + {@code root} + 空密码。 密码<b>刻意不给默认值</b> —— 仓库里不存机器相关口令，缺密码时测试自动 skip。
 *
 * <pre>
 *   # 本机启用（MySQL 8.x，账号按你的实际情况调整）
 *   export BONE_IT_DB_PASSWORD=你的密码
 *   mvn -pl bone-engine/bone-metadata-server test -Dtest=JdbcPhysicalStructureGatewayAdapterMysqlTest
 * </pre>
 *
 * <p><b>连不上就 skip 而不是失败</b>：没装 MySQL 的同事不会因此变红。
 *
 * <p><b>CI 也真跑</b>（2026-10-05 补）：{@code .github/workflows/ci.yml} 的 {@code backend-quality} job 提供了
 * MySQL 8.0 service 并注入 {@code BONE_IT_DB_*}，这些断言不再只在本机生效——本文件此前一度是
 * 「有真机才跑」，那句话现在不成立，故同步更正。仍未覆盖的环境：本地无 MySQL 的机器，以及未来 新增的其它 job（它们不注入该 env ⇒ 自动 skip）。
 */
class JdbcPhysicalStructureGatewayAdapterMysqlTest {

  private static final String TABLE = "t_it_physical_ddl";

  private static String url() {
    return prop(
        "bone.it.db.url",
        "BONE_IT_DB_URL",
        "jdbc:mysql://localhost:3306/bone_it"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
            + "&createDatabaseIfNotExist=true");
  }

  private static String user() {
    return prop("bone.it.db.username", "BONE_IT_DB_USERNAME", "root");
  }

  private static String password() {
    // 刻意无默认值：仓库不存机器口令
    return prop("bone.it.db.password", "BONE_IT_DB_PASSWORD", "");
  }

  private static String prop(String sysProp, String env, String fallback) {
    String v = System.getProperty(sysProp);
    if (v == null || v.isBlank()) {
      v = System.getenv(env);
    }
    return v == null || v.isBlank() ? fallback : v;
  }

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void connect() throws Exception {
    try {
      // JdbcTemplate 没有接收 Connection 的构造器，走 DataSource
      org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
          new org.springframework.jdbc.datasource.DriverManagerDataSource(
              url(), user(), password());
      jdbc = new JdbcTemplate(dataSource);
      jdbc.execute("SELECT 1"); // 立刻验证连通性，别等到第一条用例才发现连不上
    } catch (Exception e) {
      assumeTrue(
          false,
          "本机连不上 MySQL（"
              + url()
              + "，user="
              + user()
              + "）⇒ 跳过真机 DDL 验证。"
              + "启用方式：export BONE_IT_DB_PASSWORD=... 后重跑。原始原因："
              + e.getMessage());
    }
    assumeTrue(jdbc != null, "未启用真机 MySQL 验证");
  }

  @AfterEach
  void dropFixture() {
    if (jdbc != null) {
      jdbc.execute("DROP TABLE IF EXISTS `" + TABLE + "`");
    }
  }

  private JdbcPhysicalStructureGatewayAdapter adapter() {
    return new JdbcPhysicalStructureGatewayAdapter(
        jdbc, mock(MetaEntityRepository.class), mock(MetaFieldRepository.class));
  }

  @Test
  @DisplayName("真机：建表 DDL（含 IF NOT EXISTS + ENGINE/CHARSET）能执行，且重复执行不报错")
  void createTableIsExecutableAndIdempotentOnMysql() {
    String ddl = buildCreateTable();
    assertTrue(ddl.contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"), "DDL 未固定引擎/字符集：" + ddl);

    jdbc.execute(ddl);
    // 第二次：H2 上这一步毫无意义，只有真机能证明 IF NOT EXISTS 真的挡住了「Table already exists」
    jdbc.execute(ddl);
    assertEquals(1, tableCount(), "重复执行建表后应仍只有一张表");
  }

  @Test
  @DisplayName("真机：建出的表字符集确实是 utf8mb4（不跟随库默认值漂移）")
  void createdTableUsesUtf8mb4() {
    jdbc.execute(buildCreateTable());

    String collation =
        jdbc.queryForObject(
            "SELECT TABLE_COLLATION FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
            String.class,
            TABLE);
    assertNotNull(collation, "未查到表 " + TABLE + " 的 COLLATION");
    assertTrue(
        collation.toLowerCase().contains("utf8mb4"),
        "表字符集是 " + collation + " ⇒ 不是 utf8mb4，后续类型比对会误报「漂移」");
  }

  @Test
  @DisplayName("真机：重复加同一列被当作「已达成」而非失败（P1-11 的核心修复）")
  void duplicateAddColumnIsToleratedOnMysql() {
    jdbc.execute(buildCreateTable());
    jdbc.execute("ALTER TABLE `" + TABLE + "` ADD COLUMN `c_dup` INT");

    // 第二次必然抛 MySQL 1060（Duplicate column name）；executeIdempotently 必须判为已达成
    invokeExecuteIdempotently("ALTER TABLE `" + TABLE + "` ADD COLUMN `c_dup` INT");
  }

  @Test
  @DisplayName("真机：非重复列的错误照旧抛出（幂等逻辑不吞真错误）")
  void unrelatedSqlErrorStillPropagates() {
    assertThrows(
        RuntimeException.class,
        () -> invokeExecuteIdempotently("ALTER TABLE `" + TABLE + "` ADD COLUMN `c_x` INT"),
        "表不存在时必须继续抛错（表真的不存在，吞掉就是伪装成功）");
  }

  @Test
  @DisplayName("真机：verifyAligned 用真实 information_schema 回读，缺列时显式失败")
  void verifyAlignedDetectsMissingColumnOnMysql() {
    jdbc.execute(buildCreateTable());

    // buildCreateTable 建出的列就是 c_name（见 buildCreateTable 里的 metaField("c_name")），
    // 拿不存在的列当「存在」会让本用例自己先炸掉——第一次跑就是这么错的
    MetaField present = metaField("c_name");
    invokeVerifyAligned(TABLE, List.of(present));

    MetaField missing = metaField("c_absent");
    DomainException thrown =
        assertThrows(
            DomainException.class, () -> invokeVerifyAligned(TABLE, List.of(present, missing)));
    assertTrue(
        thrown.getMessage().contains("c_absent"), "异常未点名缺失列，排障时看不出缺口在哪：" + thrown.getMessage());
  }

  @Test
  @DisplayName("真机：verifyAligned 在表不存在时显式失败（而不是当成「无需对齐」）")
  void verifyAlignedFailsWhenTableMissing() {
    assertThrows(
        DomainException.class, () -> invokeVerifyAligned(TABLE, List.of()), "表不存在时必须失败——它意味着建表没生效");
  }

  // ---------------------------------------------------------------- 工具

  private int tableCount() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
            Integer.class,
            TABLE);
    return count == null ? 0 : count;
  }

  private static MetaField metaField(String code) {
    MetaField field = mock(MetaField.class);
    when(field.getCode()).thenReturn(code);
    when(field.getPhysicalColumn()).thenReturn(null); // 机制 A：物理列名回退 code
    when(field.getType()).thenReturn("STRING");
    when(field.getRequired()).thenReturn(false);
    return field;
  }

  /** 包成unchecked：反射失败属测试自身问题，不该让每个用例都写 throws。 */
  private String buildCreateTable() {
    try {
      Method m =
          JdbcPhysicalStructureGatewayAdapter.class.getDeclaredMethod(
              "buildCreateTable", String.class, List.class);
      m.setAccessible(true);
      return (String) m.invoke(adapter(), TABLE, List.of(metaField("c_name")));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("反射调用 buildCreateTable 失败（方法改名会让本测试静默失效）", e);
    }
  }

  private void invokeVerifyAligned(String table, List<MetaField> fields) {
    try {
      Method m =
          JdbcPhysicalStructureGatewayAdapter.class.getDeclaredMethod(
              "verifyAligned", String.class, List.class);
      m.setAccessible(true);
      m.invoke(adapter(), table, fields);
    } catch (java.lang.reflect.InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException(cause);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private void invokeExecuteIdempotently(String ddl) {
    try {
      Method m =
          JdbcPhysicalStructureGatewayAdapter.class.getDeclaredMethod(
              "executeIdempotently", String.class);
      m.setAccessible(true);
      m.invoke(adapter(), ddl);
    } catch (java.lang.reflect.InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException(cause);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("真机：连接与夹具就绪（否则本类其余用例会毫无意义）")
  void mysqlIsReachable() throws Exception {
    try (Connection c = DriverManager.getConnection(url(), user(), password());
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT VERSION()")) {
      rs.next();
      String version = rs.getString(1);
      assertNotNull(version);
      assertTrue(
          version.startsWith("8") || version.startsWith("5.7"),
          "这不是项目面向的 MySQL 版本（实际 " + version + "）⇒ 结论不可外推");
    }
  }
}
