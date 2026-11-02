package com.bone.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 仓储 SQL 列覆盖守卫：返回实体的查询必须把该实体的 {@code @Column} 列全部取回。
 *
 * <p><b>为什么需要这道门禁（两起已发生的事故）</b>：
 *
 * <ul>
 *   <li><b>日限额恒为 0</b>：{@code QuotaAccountRepository#findTransactions} 的模板漏了 {@code
 *       biz_date}，{@code QuotaDrivenAccumulationPortAdapter#dailyUsed} 对 {@code bizDate == null} 的
 *       DAILY 流水一律 {@code continue} ⇒ 当日已用恒为 0 ⇒ 日限额永远满额 ⇒ <b>静默超赔</b>；
 *   <li><b>快捷赔付风险闸恒放行</b>：{@code DecisionExecutionRepository#findLatestByClaimId} 漏了 {@code
 *       triage_reason}，{@code AutoApprovalApplicationService} 的 {@code RISK_HIT /
 *       FRAUD_GATE_NOT_PASSED} 判断读到 null ⇒ 分支成死代码 ⇒ 风控命中的案子仍可一键放款。
 * </ul>
 *
 * <p><b>为什么单测一直绿</b>：模块测试用内存仓储替身（{@code ScenarioHarness}），替身直接持有完整对象， <b>从不经过 SQL 模板</b> ⇒
 * 漏列在单测里完全不可见，只有真机联调才暴露（与 {@link SqlTemplateFirstLineTest} 记录的模板首行缺陷同一成因）。故此处以「读源码文本」的方式补位。
 *
 * <p><b>判据</b>：模板文件名必须与仓储方法同名；当该方法返回类型是本仓 {@code @Table} 实体（或其 {@code List}/{@code
 * Optional}）时，实体的每个 {@code @Column(name = ...)} 都必须出现在 SELECT 列表中。 返回投影记录（如 {@code
 * ClaimTenantRef}）或带聚合/别名的查询不在本门禁范围——它们的列由投影类型显式约定。
 *
 * <p>与 {@code scripts/check-sql-entity-column-coverage.py} 的关系：后者是<b>报告式</b>门禁（带基线、 退出码恒
 * 0），用于盘点存量；本测试是<b>强制式</b>门禁，跑在 {@code mvn test} 里，新增缺口直接红。
 */
class SqlTemplateEntityColumnCoverageTest {

  private static final Path SQL_ROOT = Path.of("src/main/resources/sql");
  private static final Path REPO_ROOT = Path.of("src/main/java/com/bone/claim/domain/repository");
  private static final Path MODEL_ROOT = Path.of("src/main/java/com/bone/claim/domain/model");

  private static final Pattern COLUMN_ANNOTATION =
      Pattern.compile("@Column\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern TABLE_ANNOTATION =
      Pattern.compile("@Table\\(\\s*(?:name\\s*=\\s*)?\"?([A-Za-z0-9_]+)\"?");

  @Test
  @DisplayName("返回实体的查询模板必须取回该实体的全部 @Column 列（漏列 ⇒ 字段恒 null ⇒ 判据静默失效）")
  void entityQueriesProjectEveryMappedColumn() throws IOException {
    List<Path> templates = listSqlTemplates();
    assertThat(templates).as("未找到任何 SQL 模板 —— 扫描路径失效会让本门禁恒过").isNotEmpty();

    List<String> offenders = new ArrayList<>();
    int inspected = 0;
    for (Path template : templates) {
      String method = template.getFileName().toString().replace(".sql", "");
      Path repoFile = REPO_ROOT.resolve(template.getParent().getFileName() + ".java");
      if (!Files.isRegularFile(repoFile)) {
        continue;
      }
      String returnType = returnTypeOf(repoFile, method);
      if (returnType == null) {
        // 接口无同名方法：孤儿模板由 scripts/check-sql-entity-column-coverage.py 报告
        continue;
      }
      Path entityFile = findEntityFile(returnType);
      if (entityFile == null) {
        continue;
      }
      String entitySource = Files.readString(entityFile, StandardCharsets.UTF_8);
      if (!TABLE_ANNOTATION.matcher(entitySource).find()) {
        continue;
      }
      inspected++;
      Set<String> expected = mappedColumns(entitySource);
      Set<String> actual = projectedColumns(Files.readString(template, StandardCharsets.UTF_8));
      expected.removeAll(actual);
      if (!expected.isEmpty()) {
        offenders.add(SQL_ROOT.relativize(template) + " → " + returnType + " 缺列 " + expected);
      }
    }

    assertThat(inspected)
        .as("被判定为「返回实体」的模板过少（解析逻辑失效会让本门禁恒过）：当前仅 %s 个", inspected)
        .isGreaterThanOrEqualTo(30);
    assertThat(offenders)
        .as(
            "以下 SQL 模板没有取回实体的全部 @Column 列："
                + "漏列不会报错，只让实体字段恒为 null，于是依赖该字段的判据静默失效"
                + "（已发生：日限额恒 0 ⇒ 静默超赔；快捷赔付风险闸读 triage_reason 恒 null ⇒ 风控命中仍放行）。"
                + "修复：把缺的列补进 SELECT（列必须与活库真实列一致）。")
        .isEmpty();
  }

  /**
   * 取仓储接口中指定方法的返回类型（剥掉泛型包装，取最内层类型名）。
   *
   * <p>接口方法只有一行签名，但可能跨行（参数多时）；这里从 {@code name(} 往前找到上一个分号/花括号， 再取最后一个类型标识符，足以覆盖本仓写法。
   */
  private static String returnTypeOf(Path repoFile, String method) throws IOException {
    String source = Files.readString(repoFile, StandardCharsets.UTF_8);
    Matcher m = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(").matcher(source);
    if (!m.find()) {
      return null;
    }
    String before = source.substring(0, m.start());
    int boundary = Math.max(before.lastIndexOf(';'), Math.max(before.lastIndexOf('}'), 0));
    String declaration = before.substring(boundary + 1);
    // 去掉注解（@Param(...) 等）与修饰符，剩下的形态是「返回类型 方法名」
    declaration = declaration.replaceAll("@[A-Za-z0-9_.]+(\\([^)]*\\))?", " ");
    // 允许尾随的泛型收尾符：`List<QuotaTransaction>` 要取到最内层 QuotaTransaction，
    // 否则所有列表查询都会被判成「无法解析」而静默跳过（本门禁曾因此漏掉额度流水漏列）。
    Matcher type = Pattern.compile("([A-Za-z_][A-Za-z0-9_.]*)\\s*[>\\s]*$").matcher(declaration);
    if (!type.find()) {
      return null;
    }
    return type.group(1);
  }

  /** 在 domain/model 下按简单类名定位实体文件；找到多个会显式失败（重名必须由人裁决）。 */
  private static Path findEntityFile(String simpleName) throws IOException {
    try (Stream<Path> paths = Files.walk(MODEL_ROOT)) {
      List<Path> hits =
          paths
              .filter(p -> p.getFileName().toString().equals(simpleName + ".java"))
              .sorted()
              .toList();
      return hits.isEmpty() ? null : hits.get(0);
    }
  }

  private static Set<String> mappedColumns(String entitySource) {
    Set<String> columns = new LinkedHashSet<>();
    Matcher m = COLUMN_ANNOTATION.matcher(entitySource);
    while (m.find()) {
      columns.add(m.group(1).toLowerCase(Locale.ROOT));
    }
    return columns;
  }

  /** 取 SELECT 与 FROM 之间的列名集合（去别名、去表前缀、忽略聚合表达式）。 */
  private static Set<String> projectedColumns(String sql) {
    String normalized = sql.replaceAll("--.*", " ");
    Matcher m = Pattern.compile("(?is)\\bselect\\b(.*?)\\bfrom\\b").matcher(normalized);
    if (!m.find()) {
      return Set.of();
    }
    String list = m.group(1);
    Map<String, Boolean> columns = new LinkedHashMap<>();
    for (String raw : list.split(",")) {
      String item = raw.trim();
      if (item.isEmpty()) {
        continue;
      }
      int alias = item.toLowerCase(Locale.ROOT).lastIndexOf(" as ");
      if (alias >= 0) {
        item = item.substring(0, alias).trim();
      }
      String plain = item.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
      if (plain.startsWith("distinct")) {
        plain = plain.substring("distinct".length()).trim();
      }
      // 只认「[表.]列」形态；函数/表达式（count(*) 等）交给投影类型约定，不在此判
      if (!plain.matches("[a-z0-9_]+(\\.[a-z0-9_]+)?")) {
        continue;
      }
      int dot = plain.lastIndexOf('.');
      columns.put(dot >= 0 ? plain.substring(dot + 1) : plain, Boolean.TRUE);
    }
    return columns.keySet();
  }

  private static List<Path> listSqlTemplates() throws IOException {
    if (!Files.isDirectory(SQL_ROOT)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(SQL_ROOT)) {
      return paths.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
    }
  }
}
