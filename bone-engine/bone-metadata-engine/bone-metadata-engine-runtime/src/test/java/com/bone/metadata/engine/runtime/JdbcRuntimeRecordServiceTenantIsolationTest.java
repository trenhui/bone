package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@link JdbcRuntimeRecordService} 的<b>租户隔离负向</b>用例（P1-12，2026-10-05）。
 *
 * <p>ADR-0015 的运行时 CRUD 直接对客户数据生成 SQL，而本类此前<b>零行为测试</b>：任何对 {@code buildTenantWhere} 的改动（少拼一个
 * {@code AND}、把 {@code :tenantId} 写死、实体快照缺列时 静默不加条件）都不会被任何测试发现 —— 而这正是<b>跨租户读</b>的直接成因。
 *
 * <p>用真实 H2 而不是 mock 数据访问层：mock 掉 {@code NamedParameterJdbcTemplate} 就等于把「SQL 里到底带没带
 * tenant_id」这一关键事实从测试里抹掉，正好放过要防的那类缺陷。
 *
 * <p>核心判据是<b>对照组</b>：B 租户读不到 A 的数据，<b>且</b>A 租户能读到自己的数据。只有负向没有对照的话，「永远读不到任何东西」也会全绿。
 *
 * <p><b>效力边界（2026-10-05 实测后如实收窄，勿再往外扩）</b>：本测试证明的是 <b>「租户/软删条件被正确拼进 WHERE，且在 B 租户上下文下确实读不到 A
 * 的行」</b>—— 这是字符串与查询语义层面的结论，H2 足够。它<b>不</b>证明：
 *
 * <ul>
 *   <li>这些 DDL/SQL 在 <b>MySQL 真机</b>上可执行。实测反证：H2 连 {@code ENGINE=InnoDB DEFAULT CHARSET=utf8mb4}
 *       都报语法错误，更不用说 {@code information_schema} 与 {@code DATABASE()} 的方言差异；用 H2 验 MySQL DDL 属自欺欺人。
 *   <li>字符集/排序规则/类型的真实行为。为此本测试还被迫加了 {@code DATABASE_TO_LOWER=TRUE} 来抵消 H2 把标识符折成大写的行为 —— <b>MySQL
 *       不需要这个开关</b>，它本身就是一个「为迁就测试库而存在」 的信号，说明两者不是同一种数据库。
 * </ul>
 *
 * 之所以仍用 H2：CI（{@code .github/workflows/ci.yml}）<b>没有 MySQL service</b>，也没有 failsafe （{@code mvn
 * clean verify -DskipITs=true}），写成 MySQL 依赖的测试在 CI 里只会永远skip 或直接失败。 MySQL 真机验证属独立立项（需要
 * testcontainers 或 CI 加 service），不在本测试范围内。
 */
class JdbcRuntimeRecordServiceTenantIsolationTest {

  private static final long TENANT_A = 1L;
  private static final long TENANT_B = 2L;
  private static final String ENTITY = "demo";
  private static final String TABLE = "t_demo";

  private JdbcRuntimeRecordService service;

  @BeforeEach
  void setUp() throws Exception {
    DataSource dataSource = h2();
    NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
    // 夹具每次重建表：DB_CLOSE_DELAY=-1 让内存库活过整个 JVM，@BeforeEach 里重复插同一主键会撞
    // DuplicateKey（实测踩到）。清库交给 DROP 而不是「换个库名」，后者会让每条用例各留一个库。
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS `" + TABLE + "`");
    // DDL 走底层 JdbcTemplate：NamedParameterJdbcTemplate.update 没有只收 SQL 的重载
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE `"
                + TABLE
                + "` (`id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL, `name` VARCHAR(64),"
                + " `deleted` SMALLINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`))");
    // NamedParameterJdbcTemplate 没有 update(sql, Object...) 重载（那是 JdbcTemplate 的 API），
    // 插数据必须走具名参数，否则编译期就报「不适用」
    org.springframework.jdbc.core.namedparam.MapSqlParameterSource seed =
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
    seed.addValue("tid", TENANT_A);
    seed.addValue("nm", "A-tenant-record");
    jdbc.update(
        "INSERT INTO "
            + TABLE
            + " (`id`, `tenant_id`, `name`, `deleted`) VALUES (1001, :tid, :nm, 0)",
        seed);

    RuntimeEntityCatalog catalog = mock(RuntimeEntityCatalog.class);
    when(catalog.findPublishedRuntime(ENTITY, TENANT_A))
        .thenReturn(Optional.of(publishedEntity(TENANT_A)));
    when(catalog.findPublishedRuntime(ENTITY, TENANT_B))
        .thenReturn(Optional.of(publishedEntity(TENANT_B)));

    service = new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @Test
  @DisplayName("负向：B 租户按 id 读 A 租户的行 ⇒ 读不到（抛「记录不存在」）")
  void tenantBCannotReadTenantARecord() {
    RuntimeRecordException thrown =
        assertThrows(RuntimeRecordException.class, () -> service.getById(ENTITY, TENANT_B, "1001"));
    assertTrue(
        String.valueOf(thrown.getMessage()).contains("不存在"),
        "异常不是「记录不存在」而是别的：" + thrown.getMessage() + " ⇒ 可能是把A 的数据返回给了 B");
  }

  @Test
  @DisplayName("对照：A 租户能读到自己的行（证明上一条不是因为查询恒空而绿）")
  void tenantACanReadOwnRecord() {
    var record = service.getById(ENTITY, TENANT_A, "1001");
    assertEquals("A-tenant-record", record.get("name"), "A 租户读不到自己的数据，负向用例就成了假绿");
  }

  @Test
  @DisplayName("软删行即使同租户也读不到（deleted 条件未被拼进 WHERE 时会漏）")
  void softDeletedRecordIsInvisibleToOwnTenant() {
    NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(h2OfSameDb());
    jdbc.getJdbcTemplate().update("UPDATE " + TABLE + " SET `deleted` = 1 WHERE `id` = 1001");

    assertThrows(
        RuntimeRecordException.class,
        () -> service.getById(ENTITY, TENANT_A, "1001"),
        "已软删的行仍可读 ⇒ 租户过滤写对了但软删过滤丢了/ 写反了");
  }

  // ---------------------------------------------------------------- 夹具

  private static DataSource h2() {
    org.springframework.jdbc.datasource.DriverManagerDataSource ds =
        new org.springframework.jdbc.datasource.DriverManagerDataSource();
    ds.setDriverClassName("org.h2.Driver");
    // DATABASE_TO_LOWER 与 bone-iam 的 test 配置一致：H2 默认把未加引号的标识符存成大写，
    // 而 getById 返回前要把物理列回填为**逻辑 code（小写）**，不设这个开关就永远回填不上
    ds.setUrl("jdbc:h2:mem:rt;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    ds.setUsername("sa");
    ds.setPassword("");
    return ds;
  }

  private DataSource h2OfSameDb() {
    return h2(); // 同一 URL + DB_CLOSE_DELAY=-1 ⇒ 共享同一个内存库
  }

  private static PublishedRuntimeEntity publishedEntity(long tenantId) {
    return new PublishedRuntimeEntity(
        ENTITY,
        TABLE,
        "id",
        tenantId,
        List.of(
            new RuntimeFieldColumn("id", "LONG", true, true, true),
            new RuntimeFieldColumn("tenant_id", "LONG", true, false, false),
            new RuntimeFieldColumn("name", "STRING", false, false, false),
            new RuntimeFieldColumn("deleted", "INTEGER", false, false, false)));
  }
}
