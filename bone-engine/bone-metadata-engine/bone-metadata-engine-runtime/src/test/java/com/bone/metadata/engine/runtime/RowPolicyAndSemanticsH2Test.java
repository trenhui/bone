package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * 行级 ACL 的 <b>AND 语义端到端</b>测试（H2 真实执行，断言可见行集合）。
 *
 * <p>与 {@link RowPolicySpecWhereSemanticsTest} 的分工：那个断言「生成的 SQL 字符串是什么」，
 * 本类断言「数据库<b>真的</b>按收窄语义返回行」。二者缺一不可——只看 SQL 文本，实现退化成 「拍平 OR」时仍可能生成合法 SQL 文本，只有真实行集断言才能抓住权限放大。
 *
 * <p><b>数据布局的关键设计（第一版曾在此踩坑）</b>：{@code buildWhere} 的整体语义是 {@code owner条件 OR (规则)}。若所有行的 {@code
 * created_by} 都等于 operator，则 owner 条件恒真、 所有行都可见，规则里的 AND 根本没有机会生效 ⇒ 测试会「假红」（断言失败但不是被测缺陷）。
 * 故本类让<b>所有行的 created_by 都不等于 operator</b>（恒为 "9000"），使 owner 条件恒不命中， 于是可见性<b>完全由规则决定</b>——AND
 * 的收窄效果才可被真实观测。
 *
 * <pre>
 *   title          | created_by | approver_id | 规则(created_by=op AND approver_id=op)
 *   ---------------+------------+-------------+-----------------------------------
 *   both           | 9000       | 42          | true  ⇒ 可见
 *   approver-only  | 9000       | 42          | 同上（approver 命中）
 *   ...
 * </pre>
 *
 * 为同时观察 OR 与 AND 的差异，规则列选 {@code owner_alt}（非 created_by，避免与 owner 条件同列） 与 {@code approver_id} 两列：
 *
 * <pre>
 *   title         | owner_alt | approver_id | created_by=op | approver=op | AND | OR
 *   --------------+-----------+-------------+--------------+-------------+-----+----
 *   both          | 42        | 42          | false        | true        | T   | T
 *   left-only     | 42        | 7| false        | false        | F   | T   ← 拍平 OR 会泄露这行
 *   right-only    | 7          | 42          | false        | true        | F   | T   ← 拍平 OR 会泄露这行
 *   neither       | 7| 7           | false        | false        | F   | F
 * </pre>
 */
class RowPolicyAndSemanticsH2Test {

  private static final long TENANT_1 = 1L;
  private static final long OP = 42L;

  /** 所有行的 created_by（owner 条件列）恒不等于 operator，确保 owner 条件不命中。 */
  private static final String FOREIGN_OWNER = "9000";

  private static final String POLICY_AND =
      "{\"owner_field\":\"created_by\",\"rules\":["
          + "{\"name\":\"strict\",\"effect\":\"allow\","
          + "\"when\":\"owner_alt = :operator AND approver_id = :operator\"}]}";

  private static final String POLICY_OR =
      "{\"owner_field\":\"created_by\",\"rules\":["
          + "{\"name\":\"loose\",\"effect\":\"allow\","
          + "\"when\":\"owner_alt = :operator OR approver_id = :operator\"}]}";

  private EmbeddedDatabase db;
  private NamedParameterJdbcTemplate jdbc;
  private final AtomicLong idGen = new AtomicLong(9000);

  private JdbcRuntimeRecordService serviceWith(String policyJson) {
    RuntimeEntityCatalog catalog =
        (code, tenantId) ->
            "bp_expense".equals(code)
                ? Optional.of(
                    new PublishedRuntimeEntity(
                        "bp_expense",
                        "bp_expense",
                        "id",
                        TENANT_1,
                        List.of(
                            new RuntimeFieldColumn("id", "LONG", false, false, true),
                            new RuntimeFieldColumn("title", "STRING", true, false, false),
                            new RuntimeFieldColumn("owner_alt", "LONG", false, false, false),
                            new RuntimeFieldColumn("approver_id", "LONG", false, false, false),
                            new RuntimeFieldColumn("status", "STRING", true, false, false)),
                        null,
                        policyJson))
                : Optional.empty();
    return new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @BeforeEach
  void setUp() {
    db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("md_row_and;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            .build();
    jdbc = new NamedParameterJdbcTemplate(db);
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE `bp_expense` ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`title` VARCHAR(255),"
                + "`amount` DECIMAL(20,6),"
                + "`owner_alt` BIGINT,"
                + "`approver_id` BIGINT,"
                + "`status` VARCHAR(255),"
                + "`version` INT NOT NULL DEFAULT 0,"
                + "`deleted` SMALLINT NOT NULL DEFAULT 0,"
                + "`created_at` DATETIME,"
                + "`updated_at` DATETIME,"
                + "`created_by` VARCHAR(64),"
                + "`updated_by` VARCHAR(64),"
                + "PRIMARY KEY (`id`))");
  }

  @AfterEach
  void tearDown() {
    db.shutdown();
  }

  /** 直接用 JDBC 写入以精确控制各列取值（走 create 会被写入校验与默认值干扰， 且 created_by 会被强制盖成 operatorId，导致 owner 条件恒真）。 */
  private String insert(String title, long ownerAlt, long approverId) {
    long id = idGen.incrementAndGet();
    jdbc.getJdbcTemplate()
        .update(
            "INSERT INTO `bp_expense` (`id`,`tenant_id`,`title`,`owner_alt`,`approver_id`,`status`,"
                + "`deleted`,`version`,`created_at`,`updated_at`,`created_by`) "
                + "VALUES (?,?,?,?,?,?,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
            id,
            TENANT_1,
            title,
            ownerAlt,
            approverId,
            "DRAFT",
            FOREIGN_OWNER);
    return String.valueOf(id);
  }

  private void seedFourRows() {
    insert("both", OP, OP);
    insert("left-only", OP, 7L);
    insert("right-only", 7L, OP);
    insert("neither", 7L, 7L);
  }

  @Test
  @DisplayName("AND 策略：只见两条件同时满足的行（拍平 OR 会多放行 left-only / right-only）")
  void andPolicy_seesOnlyRowsMatchingBothConditions() {
    seedFourRows();

    var page =
        serviceWith(POLICY_AND).page("bp_expense", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, OP);
    assertEquals(1, page.getTotal(), "AND 语义下只应可见 both 一行；若为 3 则说明 AND 被降级为 OR（权限放大）");
    assertEquals("both", String.valueOf(page.getRecords().get(0).get("title")));
  }

  @Test
  @DisplayName("OR 策略（存量口径）：任一条件命中即可见，可见 3 行（验证用例本身有判别力）")
  void orPolicy_seesUnion() {
    seedFourRows();

    var page =
        serviceWith(POLICY_OR).page("bp_expense", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, OP);
    assertEquals(
        3,
        page.getTotal(),
        "OR 语义下应可见 both/left-only/right-only 三行；neither 不应命中。"
            + "此用例是AND 用例的对照——若它也只返回 1 行，说明数据布局没造出差异，测试无效");
  }

  @Test
  @DisplayName("AND 策略：条件不满足的行 getById 404（双向对照，证明上条不是整体不渲染）")
  void andPolicy_rowFailingCondition_getById404() {
    String both = insert("both", OP, OP);
    String leftOnly = insert("left-only", OP, 7L);
    String rightOnly = insert("right-only", 7L, OP);

    JdbcRuntimeRecordService service = serviceWith(POLICY_AND);
    assertThrows(
        RuntimeRecordException.class,
        () -> service.getById("bp_expense", TENANT_1, leftOnly, OP),
        "owner_alt 命中但 approver 不命中 ⇒ AND 不满足 ⇒ 404");
    assertThrows(
        RuntimeRecordException.class,
        () -> service.getById("bp_expense", TENANT_1, rightOnly, OP),
        "approver 命中但 owner_alt 不命中 ⇒ AND 不满足 ⇒ 404");
    // 反向对照：真正满足 AND 的行必须可读（否则上面两个 404 是「整体不渲染」造成的假绿）
    assertEquals(
        "both", String.valueOf(service.getById("bp_expense", TENANT_1, both, OP).get("title")));
  }

  @Test
  @DisplayName("括号分组策略：(a OR b) AND c —— c 不满足时恒空（括号被拍平则不会恒空）")
  void parenthesizedAnd_appliesNarrowing() {
    seedFourRows();

    // 第三条件 status = :operator：所有行 status 为 DRAFT，而 operator=42 ⇒ 恒不满足 ⇒ 恒空
    String policy =
        "{\"owner_field\":\"created_by\",\"rules\":["
            + "{\"name\":\"grp\",\"effect\":\"allow\","
            + "\"when\":\"(owner_alt = :operator OR approver_id = :operator) "
            + "AND status = :operator\"}]}";
    var page = serviceWith(policy).page("bp_expense", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, OP);
    assertEquals(0, page.getTotal(), "AND 的第三条件不满足时应恒空；若 >0 说明括号分组被拍平、AND 被降级为 OR");
  }

  @Test
  @DisplayName("括号分组策略：status AND (a OR b) ——括号内 OR 决定可见性（括号被丢弃则恒空）")
  void parenthesizedOr_decidesVisibility() {
    seedFourRows();

    // status = :operator 恒不满足（所有行 status 为 DRAFT，operator 为 42）⇒ 整个 AND 恒为false。
    // 但此例的判据在括号：若括号分组被丢弃（拍平后变成 status OR a OR b），
    // 则 a/b 命中的行会重新可见 ⇒ 返回 3 行；括号正确保留时必须恒为 0。
    // 与andPolicy_seesOnlyRowsMatchingBothConditions 不同：这里 status 不参与 owner 条件，
    // 排除了「owner 条件恒真导致假红」的可能。
    String policy =
        "{\"owner_field\":\"created_by\",\"rules\":["
            + "{\"name\":\"grp3\",\"effect\":\"allow\","
            + "\"when\":\"status = :operator AND (owner_alt = :operator OR approver_id = :operator)\"}]}";
    var page = serviceWith(policy).page("bp_expense", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, OP);
    assertEquals(0, page.getTotal(), "AND 的左侧条件恒不满足时必须恒空；若>0 说明括号分组被丢弃、AND 被降级为 OR");
  }

  @Test
  @DisplayName("写侧同口径：AND 不满足的行不可改（可见性是权限判定，不是只读展示）")
  void andPolicy_rowFailingCondition_updateRejected() {
    String leftOnly = insert("left-only", OP, 7L);

    assertThrows(
        RuntimeRecordException.class,
        () ->
            serviceWith(POLICY_AND)
                .update(
                    "bp_expense",
                    TENANT_1,
                    leftOnly,
                    Map.of("title", "篡改"),
                    null,
                    String.valueOf(OP)),
        "AND 不满足的行不应可写");
  }

  @Test
  @DisplayName("回归护栏：owner 条件仍独立生效（owner_field 语义未被本次改动破坏）")
  void ownerCondition_stillWorks() {
    // 本行 created_by = operator ⇒ owner 条件命中 ⇒ 即使规则（AND）完全不满足也可见。
    // 这条守住「本次只修 AND/OR 渲染语义，不动 owner_field 既有的 OR 短路语义」。
    long id = idGen.incrementAndGet();
    jdbc.getJdbcTemplate()
        .update(
            "INSERT INTO `bp_expense` (`id`,`tenant_id`,`title`,`owner_alt`,`approver_id`,`status`,"
                + "`deleted`,`version`,`created_at`,`updated_at`,`created_by`) "
                + "VALUES (?,?,?,?,?,?,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
            id,
            TENANT_1,
            "own-row",
            7L,
            7L,
            "DRAFT",
            String.valueOf(OP));

    var page =
        serviceWith(POLICY_AND).page("bp_expense", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, OP);
    assertEquals(1, page.getTotal(), "owner 条件命中时应可见（本次只修 AND/OR 语义，不应动owner_field 的既有语义）");
  }
}
