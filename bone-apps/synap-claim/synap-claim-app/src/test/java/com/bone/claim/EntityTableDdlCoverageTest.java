package com.bone.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 实体—建表对账守卫：{@code @Table} 声明的表必须在 {@code bone-init.sql} 里真的建出来。
 *
 * <p><b>为什么需要这道门禁（已发生的事故）</b>：2026-11-03 做「实体 → DDL」机械比对时发现，31 个 {@code @Table} 实体里 29 个有建表、唯
 * {@code t_claim_flow_config} 与 {@code t_feature_toggle} 全仓零 DDL （{@code bone-init.sql} / {@code
 * scripts/migration} / {@code db/migration} 均无命中，活库也没有）。而这两个聚合的实体、仓储接口、乃至「本批补齐表 + 实体 +
 * 仓储」的建表意图注释都齐全 —— <b>表是计划里的一环，只是没落地</b>。
 *
 * <p><b>为什么它在 CI 里恒绿</b>：本模块的测试用内存仓储替身（{@code ScenarioHarness}），替身持有完整对象、<b>从不碰真表</b>； 仓储接口又是 SDK 的
 * {@code Criteria} 形态，编译期也不校验表是否存在。于是「有代码没表」只能在首个消费方接入的那一刻以 {@code ERROR 1146 Table doesn't exist}
 * 暴露 —— 与 {@link SqlTemplateEntityColumnCoverageTest} 记录的「漏列 ⇒ 字段恒
 * null」同属<b>延迟暴露型</b>缺陷：不编译失败、不让既有用例转红，只在上线时炸。
 *
 * <p><b>第二个判据：{@code @Version} ⇒ 表必须有 {@code version} 列</b>。这是 0098 踩过的坑：只加注解不加列，SDK 的 UPDATE
 * 会因乐观锁列缺失直接失败；只加列不加注解则乐观锁形同虚设（列恒 0，界面像已加固）。两者必须同批，故一并锁住。
 *
 * <p><b>为什么以 {@code bone-init.sql} 为真源</b>：它是新环境的全量入口（{@code scripts/migration} 服务于在途库增量）， 且新增表会经
 * {@code sync-init-from-live.py} 从活库并入托管区块 —— 即 init 有表 ⇔ 活库有表。反向不成立（活库有、init 无）才需要跑同步脚本，所以只查 init
 * 一个方向即可，不引入「本机必须有数据库」的依赖。
 *
 * <p>与 {@code scripts/check-ddl-doc-sync.py} 的分工：后者查 init 与《数据库开发规范》§2 清单是否一致（文档漂移），
 * 本测试查<b>代码实体</b>与 init 是否一致（代码漂移）。两者都缺，才让这次的缺口一路滑到提交。
 */
class EntityTableDdlCoverageTest {

  private static final Path MODEL_ROOT = Path.of("src/main/java/com/bone/claim/domain/model");

  private static final Pattern TABLE_ANNOTATION =
      Pattern.compile("@Table\\(\\s*(?:name\\s*=\\s*)?\"?([A-Za-z0-9_]+)\"?");

  private static final Pattern VERSION_ANNOTATION =
      Pattern.compile("^\\s*@Version\\s*$", Pattern.MULTILINE);

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern VERSION_COLUMN =
      Pattern.compile("^\\s*`?version`?\\s", Pattern.MULTILINE);

  /** {@code UNIQUE KEY name (col_a, col_b, ...)} —— 兼容反引号与多余空白。 */
  private static final Pattern UNIQUE_KEY =
      Pattern.compile(
          "UNIQUE\\s+(?:KEY|INDEX)\\s+`?([A-Za-z0-9_]+)`?\\s*\\(([^)]*)\\)",
          Pattern.CASE_INSENSITIVE);

  private static final int MIN_ENTITY_TABLES = 25;
  private static final int MIN_INIT_TABLES = 100;

  @Test
  @DisplayName("每个 @Table 实体都必须在 bone-init.sql 有同名建表（否则首个消费方接入即 1146）")
  void everyEntityTableIsCreated() throws IOException {
    Map<String, String> entityTables = readEntityTables();
    assertThat(entityTables.size())
        .as("扫描到的 @Table 实体过少 —— 路径或正则失效会让本门禁恒过，必须先修门禁本身")
        .isGreaterThanOrEqualTo(MIN_ENTITY_TABLES);

    Map<String, String> initTables = readInitTables();
    assertThat(initTables.size())
        .as("bone-init.sql 解析出的建表过少 —— 文件结构变了会导致假绿")
        .isGreaterThanOrEqualTo(MIN_INIT_TABLES);

    List<String> offenders = new ArrayList<>();
    for (Map.Entry<String, String> entry : entityTables.entrySet()) {
      if (!initTables.containsKey(entry.getKey())) {
        offenders.add(entry.getValue() + "（表 " + entry.getKey() + "）");
      }
    }

    assertThat(offenders)
        .as(
            "以下实体声明了表，但 bone-init.sql 里没有建表语句。"
                + "内存替身测试不会碰真表，所以这个缺口在 CI 里恒绿，只在上线时 1146 —— "
                + "请补一条迁移脚本（CREATE TABLE IF NOT EXISTS + 终态断言）、登记 migration-manifest，"
                + "再跑 scripts/sync-init-from-live.py 并入 init")
        .isEmpty();
  }

  @Test
  @DisplayName("声明 @Version 的实体，其建表必须含 version 列（缺列 ⇒ 首次 UPDATE 即失败）")
  void versionAnnotatedEntitiesHaveVersionColumn() throws IOException {
    Map<String, String> initTables = readInitTables();
    Map<String, Boolean> versionedEntities = readVersionedEntities();
    assertThat(versionedEntities).as("没有任何实体被判定为「声明了 @Version」—— 解析失效会让本门禁恒过").isNotEmpty();

    List<String> offenders = new ArrayList<>();
    int checked = 0;
    for (Map.Entry<String, Boolean> entry : versionedEntities.entrySet()) {
      if (!Boolean.TRUE.equals(entry.getValue())) {
        continue;
      }
      String table = entry.getKey();
      String ddl = initTables.get(table);
      if (ddl == null) {
        // 缺建表由上一个用例报，这里不重复计数，避免同一缺陷两条红
        continue;
      }
      checked++;
      Matcher column = VERSION_COLUMN.matcher(ddl);
      if (!column.find()) {
        offenders.add(table);
      }
    }

    assertThat(checked).as("受检的乐观锁表为 0 —— 判定逻辑失效").isGreaterThan(0);
    assertThat(offenders)
        .as(
            "以下表的实体声明了 @Version，但建表里没有 version 列。"
                + "两个半成品都比不改更危险：只加注解不加列 ⇒ 任何 save 报 ERROR 1054；"
                + "只加列不加注解 ⇒ 列恒 0，乐观锁形同虚设而界面像已加固。二者必须同批")
        .isEmpty();
  }

  /**
   * 报案 1:0..N 拆分后，<b>防重复赔付的唯一键不得被报案维度稀释</b>。
   *
   * <p><b>为什么这条必须有</b>：拆表最容易被顺手做的一件事，是把 {@code uk_tenant_invoice} 从 {@code (tenant_id,
   * invoice_code, invoice_no, deleted)} 扩成含 {@code report_id}。语义上看起来更"贴合"新模型（发票属于某次报案），
   * 实际上是<b>放开同一张发票在两个赔案下各赔一次</b>——唯一键只挡住同报案内的重复，跨报案的重复赔付全部放行，而且<b>没有任何用例会转红</b>：
   * 内存替身不实现唯一键，这个缺口只在上线后以"同一张票赔了两次"的形式出现。
   *
   * <p>发票是<b>票据维度的全局事实</b>（一张票只能报一次），与"这次事故赔了几笔"是两个正交维度。故唯一键里出现 {@code report_id} 即判红。
   */
  @Test
  @DisplayName("uk_tenant_invoice 不得含 report_id：发票唯一性是租户级事实，不随报案拆分而放宽")
  void invoiceUniqueKeyIsNotScopedByReport() throws IOException {
    Map<String, String> initTables = readInitTables();

    String caseDdl = initTables.get("t_claim_case");
    assertThat(caseDdl).as("bone-init.sql 里找不到 t_claim_case 建表 —— 解析失效会让本门禁恒绿").isNotNull();

    Map<String, List<String>> uniqueKeys = readUniqueKeys(caseDdl);
    assertThat(uniqueKeys).as("t_claim_case 没有任何 UNIQUE KEY —— 解析失效或唯一约束被整体删除").isNotEmpty();

    List<String> invoiceKey = uniqueKeys.get("uk_tenant_invoice");
    assertThat(invoiceKey).as("uk_tenant_invoice 缺失：防重复赔付的最后防线（并发下只有 DB 唯一键有效）").isNotNull();
    assertThat(invoiceKey)
        .as(
            "uk_tenant_invoice 的列集合变了。它必须是 (tenant_id, invoice_code, invoice_no, deleted)："
                + "tenant_id 做租户隔离，deleted 让草稿（deleted=1）不占用唯一键。"
                + "出现 report_id 意味着同一张发票可在不同报案下各赔一次 —— 这是重复赔付，不是新特性")
        .containsExactly("tenant_id", "invoice_code", "invoice_no", "deleted");

    String reportDdl = initTables.get("t_claim_report");
    assertThat(reportDdl).as("bone-init.sql 里找不到 t_claim_report 建表").isNotNull();
    assertThat(readUniqueKeys(reportDdl).get("uk_report_no"))
        .as("报案号必须全局唯一（业务号是客服检索键，重号会让两个事故共用一个号）")
        .containsExactly("report_no");
  }

  // ===================== 读取与解析 =====================

  private Map<String, String> readEntityTables() throws IOException {
    Map<String, String> tables = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(MODEL_ROOT)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = TABLE_ANNOTATION.matcher(source);
        if (matcher.find()) {
          tables.put(matcher.group(1), file.getFileName().toString().replace(".java", ""));
        }
      }
    }
    return tables;
  }

  private Map<String, Boolean> readVersionedEntities() throws IOException {
    Map<String, Boolean> versioned = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(MODEL_ROOT)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Matcher table = TABLE_ANNOTATION.matcher(source);
        if (!table.find()) {
          continue;
        }
        versioned.put(table.group(1), VERSION_ANNOTATION.matcher(source).find());
      }
    }
    return versioned;
  }

  private Map<String, String> readInitTables() throws IOException {
    Path init = locateInit();
    String sql = Files.readString(init, StandardCharsets.UTF_8);
    Map<String, String> tables = new LinkedHashMap<>();
    Matcher matcher = CREATE_TABLE.matcher(sql);
    while (matcher.find()) {
      tables.put(matcher.group(1), matcher.group(2));
    }
    return tables;
  }

  /** 从单表 DDL 体内抽出 {@code 唯一键名 → 列名列表}（列名去反引号，保持书写顺序）。 */
  private Map<String, List<String>> readUniqueKeys(String ddl) {
    Map<String, List<String>> keys = new LinkedHashMap<>();
    Matcher matcher = UNIQUE_KEY.matcher(ddl);
    while (matcher.find()) {
      List<String> columns =
          Stream.of(matcher.group(2).split(","))
              .map(String::trim)
              .map(c -> c.replace("`", ""))
              .filter(c -> !c.isEmpty())
              .toList();
      keys.put(matcher.group(1), columns);
    }
    return keys;
  }

  /**
   * 自模块目录向上定位 {@code bone-init.sql}。
   *
   * <p>不用固定相对路径：有人从仓库根跑 {@code mvn -pl} 时工作目录不同，写死 {@code ../../} 会变成 「文件不存在」的假红；也刻意不 fallback
   * 成「找不到就跳过」——那会让门禁在最需要它的时候静默失效。
   */
  private Path locateInit() {
    Path dir = Path.of("").toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      Path candidate = dir.resolve("bone-init.sql");
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("未找到 bone-init.sql，工作目录 = " + Path.of("").toAbsolutePath());
  }
}
