package com.bone.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 模板首行契约守卫。
 *
 * <p><b>为什么需要这道门禁</b>：metadata-sdk 的 {@code TemplateContentParser#detectSqlType} 用 {@code
 * sql.toUpperCase().trim().startsWith(...)} 判语句类型（SELECT / INSERT / UPDATE / DELETE / WITH），
 * <b>且不剥离前置注释</b>。于是任何以 {@code --} 注释开头的模板，其 SQL 类型被判成 {@code UNKNOWN}， 运行期由 {@code
 * RepositoryFactoryBean#executeQuery} 抛 {@code UnsupportedOperationException: 不支持的 SQL 类型:
 * UNKNOWN}。
 *
 * <p><b>为什么这类缺陷特别难发现</b>：
 *
 * <ul>
 *   <li><b>症状与病因隔得很远</b>：报错发生在<b>调用方</b>（如报案提交）而非模板本身，堆栈里只有 SDK 内部帧，看不出"某个 SQL 模板开头多了个注释"；
 *   <li><b>表现成 500 而非 4xx</b>：业务前置校验（保单是否生效）本该给出 422，模板一坏就变成 "系统异常"，把配置问题伪装成服务故障；
 *   <li><b>单测测不出来</b>：模块测试用的是内存仓储替身（{@code ScenarioHarness}）， <b>从不加载 SQL 模板</b> ⇒
 *       模板语法/首行缺陷在单测里完全不可见（2026-10-31 实测： 本仓 7 个既有模板正是因此长期带着该缺陷，直到真机联调才暴露）。
 * </ul>
 *
 * <p><b>本门禁的判据</b>：模板首个非空行必须以受支持的语句关键字开头。修复方式是把说明性注释 移到<b>仓储接口的 Javadoc</b>（本仓既有约定：SQL
 * 模板内不放注释，说明写在仓储方法上）。
 */
class SqlTemplateFirstLineTest {

  /** metadata-sdk 支持的语句类型首关键字。 */
  private static final List<String> SUPPORTED_KEYWORDS =
      List.of("SELECT", "INSERT", "UPDATE", "DELETE", "WITH");

  /** SQL 模板根目录（与 SDK 的 {@code classpath:/sql/...} 约定一致）。 */
  private static final Path SQL_ROOT = Path.of("src/main/resources/sql");

  @Test
  @DisplayName("SQL 模板首个非空行必须是受支持的语句关键字（否则运行期被判成 UNKNOWN 而抛 500）")
  void everyTemplateStartsWithSupportedKeyword() throws IOException {
    List<String> offenders = new ArrayList<>();
    List<Path> templates = listTemplates();
    assertThat(templates).as("未找到任何 SQL 模板 —— 扫描路径失效会让本门禁恒过").isNotEmpty();

    for (Path template : templates) {
      String firstMeaningfulLine =
          Files.readAllLines(template, StandardCharsets.UTF_8).stream()
              .map(String::trim)
              .filter(line -> !line.isEmpty())
              .findFirst()
              .orElse("");
      String upper = firstMeaningfulLine.toUpperCase(Locale.ROOT);
      boolean ok = SUPPORTED_KEYWORDS.stream().anyMatch(upper::startsWith);
      if (!ok) {
        offenders.add(SQL_ROOT.relativize(template) + " → 首行: \"" + firstMeaningfulLine + "\"");
      }
    }

    assertThat(offenders)
        .as(
            "以下 SQL 模板的首个非空行不是受支持的语句关键字："
                + "SDK 的 detectSqlType 不剥离前置注释，会把语句类型判成 UNKNOWN，"
                + "运行期抛「不支持的 SQL 类型: UNKNOWN」（表现为 500 而非业务 4xx）。"
                + "修复：把说明性注释移到仓储接口 Javadoc，模板首行直接写语句关键字。")
        .isEmpty();
  }

  @Test
  @DisplayName("SQL 模板内不放行内注释（说明写在仓储 Javadoc；注释进模板是本次缺陷的成因）")
  void templatesCarryNoInlineComments() throws IOException {
    List<String> offenders = new ArrayList<>();
    for (Path template : listTemplates()) {
      boolean hasComment =
          Files.readAllLines(template, StandardCharsets.UTF_8).stream()
              .anyMatch(line -> line.trim().startsWith("--"));
      if (hasComment) {
        offenders.add(SQL_ROOT.relativize(template).toString());
      }
    }
    assertThat(offenders)
        .as("SQL 模板内出现注释行：本仓约定是模板只放语句、说明写仓储 Javadoc；" + "注释一旦出现在模板内，极易被后人挪到首行而触发 UNKNOWN 缺陷。")
        .isEmpty();
  }

  private static List<Path> listTemplates() throws IOException {
    if (!Files.isDirectory(SQL_ROOT)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(SQL_ROOT)) {
      return paths.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
    }
  }
}
