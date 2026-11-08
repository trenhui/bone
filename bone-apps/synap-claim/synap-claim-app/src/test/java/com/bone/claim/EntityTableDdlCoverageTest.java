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
