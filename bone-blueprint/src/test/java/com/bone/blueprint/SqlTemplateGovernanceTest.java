package com.bone.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 模板治理（ADR-0030 门禁①/③/④/⑥的真实载体）。
 *
 * <p><b>为什么不是一个 ArchUnit 规则</b>：这四条要读的东西<strong>不在字节码里</strong>——外置 {@code
 * resources/sql/**.sql}、SQL 文本里的 {@code FROM}/{@code JOIN} 表集合、README 的 E-1.2 数据所有权声明，
 * 以及「同一方法是否<em>同时</em>存在 {@code @Sql} 与同名 .sql」这种跨 Java 与资源的判定。ArchUnit 只能看字节码，
 * 因此改用本测试做载体（不是把门禁降级为人工评审）。Java 侧结构仍用 ArchUnit 的导入结果读，避免正则解析源码。
 *
 * <p><b>本测试守护的四条</b>：
 *
 * <ul>
 *   <li><b>① 读方法必须声明通道</b>：域仓储里<em>返回非聚合类型</em>的方法，必须是 {@code default}（有方法体）， 或带 {@code @Sql} /
 *       同名外置模板。拦「无通道标记的裸读方法」。
 *   <li><b>③ 两源禁并存</b>：同一模板 ID 不得同时有 {@code @Sql} 与 {@code .sql}（默认 {@code classpath-first} 下 SDK
 *       只加载其一、另一个永不加载且不报错 ⇒ 影子 SQL）；{@code @Sql} 不得为空值。
 *   <li><b>④ 租户必须显式声明</b>：走自定义 SQL 的方法必须有 {@code @TenantScope}；{@code AUTO} 的 SQL 不得再手写 {@code
 *       tenant_id}（会与注入条件重复）；手写软删必须覆盖 {@code JOIN} 的每个子表。
 *   <li><b>⑥ 不许越聚合读</b>：自定义 SQL 的 {@code FROM}/{@code JOIN} 表集合必须 ⊆ 该聚合拥有的表集合——表集合取自 E-1.2
 *       的数据所有权声明（模块 {@code README.md} 的「本上下文拥有的表」表），聚合由 {@code Repository<T, ?>} 的 {@code T} 决定。
 * </ul>
 *
 * <p>另有一组针对上面四条<strong>判据函数本身</strong>的用例（合成 SQL 输入），保证判据不是「恰好通过」而是真的能拦——
 * 真源侧当前是合规的，若只测合规输入，判据失效时会静默变绿。
 *
 * <p>这与 {@link ConfigKeysContractTest} 是同一类载体：<strong>静态分析看不见、又必须机器拦截</strong>的契约， 由扫描 {@code
 * src/main} 的模块级测试承担。
 */
class SqlTemplateGovernanceTest {

  /** 外置模板根：{@code src/main/resources/sql/<接口包路径>/<接口简名>/<方法名>.sql}（ADR-0030 §0.3）。 */
  private static final Path SQL_ROOT = Path.of("src", "main", "resources", "sql");

  private static final Path README = Path.of("README.md");

  private static final String SQL_ANNOTATION = "com.bone.metadata.sdk.domain.annotation.Sql";
  private static final String TENANT_SCOPE_ANNOTATION =
      "com.bone.metadata.sdk.domain.annotation.TenantScope";

  /** 聚合根基类 FQN：用于区分「返回聚合」与「返回读模型/标量」。 */
  private static final String ENTITY_BASE_CLASS = "com.bone.core.domain.entity.Entity";

  private static final String DOMAIN_REPOSITORY_MARKER = ".domain.repository";

  /** E-1.2 数据所有权声明表的标题关键字。 */
  private static final String OWNERSHIP_HEADING = "本上下文拥有的表";

  private static final String OWNED_TABLE_HEADER = "表名";
  private static final String OWNED_TABLE_AGGREGATE_HEADER = "归属聚合";

  /** {@code FROM t} / {@code JOIN t alias} / {@code LEFT JOIN t AS alias}；组 3 可能是关键字（表示无别名）。 */
  private static final Pattern TABLE_REF =
      Pattern.compile(
          "(?i)\\b(from|join)\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_]*))?");

  private static final Set<String> SQL_KEYWORDS =
      Set.of(
          "select",
          "from",
          "join",
          "left",
          "right",
          "inner",
          "outer",
          "full",
          "cross",
          "on",
          "where",
          "and",
          "or",
          "order",
          "group",
          "by",
          "having",
          "limit",
          "offset",
          "as",
          "union",
          "set",
          "values",
          "using",
          "straight_join");

  private static final Pattern HAND_WRITTEN_TENANT_ID = Pattern.compile("(?i)\\btenant_id\\b");

  // ===== 门禁①③④⑥：真源扫描 =====

  @Test
  @DisplayName("门禁① 域仓储返回非聚合类型的方法必须声明通道（default / @Sql / 外置模板）")
  void repositoryReadMethodsMustDeclareChannel() throws IOException {
    Set<String> external = externalTemplateIds();
    Assumptions.assumeTrue(!external.isEmpty(), "未发现外置 SQL 模板，扫描逻辑失效");

    List<String> violations = new ArrayList<>();
    for (JavaMethod method : repositoryMethods()) {
      if (returnsAggregate(method)) {
        continue;
      }
      if (hasChannel(method, external)) {
        continue;
      }
      violations.add(signature(method));
    }
    assertThat(violations)
        .as("ADR-0030 门禁①：域仓储中返回非聚合类型的方法必须带通道标记" + "（default 方法体 / @Sql / 同名外置 .sql）")
        .isEmpty();
  }

  @Test
  @DisplayName("门禁③ 同一模板 ID 不得 @Sql 与 .sql 并存；@Sql 不得为空")
  void sqlTemplateSourceMustNotDuplicate() throws IOException {
    Set<String> external = externalTemplateIds();
    Assumptions.assumeTrue(!external.isEmpty(), "未发现外置 SQL 模板，扫描逻辑失效");

    Set<String> inlineIds = new TreeSet<>();
    List<String> emptyValues = new ArrayList<>();
    for (JavaMethod method : repositoryMethods()) {
      Optional<String> inline = inlineSqlValue(method);
      if (inline.isEmpty()) {
        continue;
      }
      inlineIds.add(templateId(method));
      if (inline.get().isBlank()) {
        emptyValues.add(signature(method));
      }
    }

    assertThat(duplicateTemplateIds(external, inlineIds))
        .as("ADR-0030 门禁③：同一模板 ID 不得同时有 @Sql 与 .sql" + "（默认 classpath-first 下其一永不加载且不报错 = 影子 SQL）")
        .isEmpty();
    assertThat(emptyValues).as("ADR-0030 门禁③：@Sql 不得为空值").isEmpty();
  }

  @Test
  @DisplayName("门禁④ 自定义 SQL 方法必须声明 @TenantScope；AUTO 不得手写 tenant_id；软删须覆盖 JOIN 子表")
  void sqlMethodsMustDeclareTenantScope() throws IOException {
    Map<String, String> sqlByTemplate = templateSqlByMethodId();
    Assumptions.assumeTrue(!sqlByTemplate.isEmpty(), "未发现带自定义 SQL 的仓储方法，扫描逻辑失效");

    List<String> violations = new ArrayList<>();
    for (JavaMethod method : repositoryMethods()) {
      String id = templateId(method);
      Optional<String> inline = inlineSqlValue(method);
      String sql = inline.orElse(sqlByTemplate.get(id));
      if (sql == null) {
        continue;
      }
      String tenantScope = tenantScopeMode(method).orElse(null);
      if (tenantScope == null) {
        violations.add(
            signature(method) + "：走自定义 SQL 却未声明 @TenantScope（缺省 MANUAL 不报错，但等于放弃显式声明，R4 拦截）");
        continue;
      }
      if ("AUTO".equals(tenantScope) && handWrittenTenantId(sql)) {
        violations.add(signature(method) + "：AUTO 模式的 SQL 仍手写 tenant_id（会与自动注入条件重复，R4 拦截）");
      }
      Set<String> missing = joinAliasesMissingSoftDelete(sql);
      if (!missing.isEmpty()) {
        violations.add(signature(method) + "：手写软删未覆盖 JOIN 子表别名 " + missing + "（软删明细会被 join 出来）");
      }
    }
    assertThat(violations).as("ADR-0030 门禁④：租户与软删必须显式且完整").isEmpty();
  }

  @Test
  @DisplayName("门禁⑥ 自定义 SQL 的 FROM/JOIN 表集合必须 ⊆ 该聚合拥有的表（E-1.2 声明）")
  void sqlFromJoinMustStayWithinAggregate() throws IOException {
    Map<String, Map<String, String>> owned = ownedTablesByAggregateHolder();
    Assumptions.assumeTrue(!owned.isEmpty(), "README 缺少 E-1.2 数据所有权声明表，扫描逻辑失效");

    Map<String, String> sqlByTemplate = templateSqlByMethodId();
    Assumptions.assumeTrue(!sqlByTemplate.isEmpty(), "未发现带自定义 SQL 的仓储方法，扫描逻辑失效");

    List<String> violations = new ArrayList<>();
    for (JavaMethod method : repositoryMethods()) {
      String sql = inlineSqlValue(method).orElse(sqlByTemplate.get(templateId(method)));
      if (sql == null) {
        continue;
      }
      String aggregate = repositoryEntitySimpleName(method);
      Set<String> allowed = allowedTables(owned, aggregate);
      if (allowed.isEmpty()) {
        violations.add(
            signature(method) + "：聚合 " + aggregate + " 在 README 的 E-1.2 声明里没有任何表，无法证明读边界");
        continue;
      }
      Set<String> referenced = new TreeSet<>(tablesReferenced(sql));
      referenced.removeAll(allowed);
      if (!referenced.isEmpty()) {
        violations.add(
            signature(method)
                + "：引用了聚合 "
                + aggregate
                + " 不拥有的表 "
                + referenced
                + "（跨聚合读须走 QueryPort）");
      }
    }
    assertThat(violations).as("ADR-0030 门禁⑥：读不得越出本聚合表集合").isEmpty();
  }

  // ===== 判据函数的自带用例（合成输入，确保判据真的能拦） =====

  @Test
  @DisplayName("判据：模板 ID 由资源路径按 <包>/<接口>/<方法> 拼装")
  void templateIdIsDerivedFromResourcePath() {
    assertThat(
            templateIdFromResourcePath(
                "com/bone/blueprint/domain/repository/OrderRepository/findOrderWithItems.sql"))
        .isEqualTo("com.bone.blueprint.domain.repository.OrderRepository.findOrderWithItems");
  }

  @Test
  @DisplayName("判据：两源并存可被检出（影子 SQL）")
  void dualSourceIsDetectable() {
    assertThat(
            duplicateTemplateIds(
                Set.of("a.B.x", "a.B.onlyExternal"), Set.of("a.B.x", "a.B.onlyInline")))
        .containsExactly("a.B.x");
    assertThat(duplicateTemplateIds(Set.of("a.B.x"), Set.of("a.B.y"))).isEmpty();
  }

  @Test
  @DisplayName("判据：AUTO 手写 tenant_id 可被检出")
  void autoHandWrittenTenantIdIsDetectable() {
    assertThat(handWrittenTenantId("SELECT 1 FROM t_order o WHERE o.tenant_id = #{t}")).isTrue();
    assertThat(handWrittenTenantId("SELECT 1 FROM t_order o WHERE o.id = #{id}")).isFalse();
  }

  @Test
  @DisplayName("判据：JOIN 子表缺软删可被检出，单表无别名写法不被误报")
  void joinSoftDeleteGapIsDetectable() {
    String missing =
        "SELECT * FROM t_order o LEFT JOIN t_order_item oi ON o.id = oi.order_id"
            + " WHERE o.deleted = 0";
    assertThat(joinAliasesMissingSoftDelete(missing)).containsExactly("oi");

    String covered =
        "SELECT * FROM t_order o LEFT JOIN t_order_item oi ON o.id = oi.order_id AND oi.deleted = 0"
            + " WHERE o.deleted = 0";
    assertThat(joinAliasesMissingSoftDelete(covered)).isEmpty();

    String unaliased = "SELECT * FROM t_order WHERE deleted = 0 AND status = 'CREATED'";
    assertThat(joinAliasesMissingSoftDelete(unaliased)).isEmpty();

    String unaliasedMissing = "SELECT * FROM t_order WHERE status = 'CREATED'";
    assertThat(joinAliasesMissingSoftDelete(unaliasedMissing)).containsExactly("<unaliased>");
  }

  @Test
  @DisplayName("判据：FROM/JOIN 表抽取")
  void tablesAreExtracted() {
    assertThat(
            tablesReferenced(
                "SELECT 1 FROM t_order o LEFT JOIN t_order_item oi ON o.id = oi.order_id"))
        .containsExactly("t_order", "t_order_item");
  }

  // ===== Java 侧（ArchUnit 导入结果，不解析源码文本） =====

  private static JavaClasses blueprintClasses() {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.bone.blueprint");
  }

  private static List<JavaMethod> repositoryMethods() {
    List<JavaMethod> methods = new ArrayList<>();
    for (JavaClass clazz : blueprintClasses()) {
      if (!clazz.isInterface()
          || !clazz.getPackageName().contains(DOMAIN_REPOSITORY_MARKER)
          || !clazz.getSimpleName().endsWith("Repository")) {
        continue;
      }
      methods.addAll(clazz.getMethods());
    }
    assertThat(methods).as("扫描不到任何 domain.repository 方法，扫描逻辑本身失效（否则四条门禁会静默变绿）").isNotEmpty();
    return methods;
  }

  private static boolean returnsAggregate(JavaMethod method) {
    JavaClass raw = method.getReturnType().toErasure();
    if (raw.isAssignableTo(ENTITY_BASE_CLASS)) {
      return true;
    }
    if (method.getReturnType() instanceof JavaParameterizedType parameterized
        && parameterized.getActualTypeArguments().size() == 1) {
      return parameterized
          .getActualTypeArguments()
          .get(0)
          .toErasure()
          .isAssignableTo(ENTITY_BASE_CLASS);
    }
    return false;
  }

  private static boolean hasChannel(JavaMethod method, Set<String> externalIds) {
    if (!method.getModifiers().contains(JavaModifier.ABSTRACT)) {
      return true; // default：有方法体，内部走 Criteria 或委派给带通道的方法
    }
    return inlineSqlValue(method).isPresent() || externalIds.contains(templateId(method));
  }

  private static Optional<String> inlineSqlValue(JavaMethod method) {
    return annotation(method, SQL_ANNOTATION)
        .map(annotation -> annotation.get("value").map(String::valueOf).orElse(""));
  }

  private static Optional<String> tenantScopeMode(JavaMethod method) {
    return annotation(method, TENANT_SCOPE_ANNOTATION)
        .map(annotation -> String.valueOf(annotation.get("value").orElse("MANUAL")));
  }

  private static Optional<JavaAnnotation<JavaMethod>> annotation(JavaMethod method, String fqn) {
    return method.getAnnotations().stream()
        .filter(annotation -> fqn.equals(annotation.getRawType().getName()))
        .findFirst();
  }

  private static String templateId(JavaMethod method) {
    return method.getOwner().getName() + "." + method.getName();
  }

  private static String repositoryEntitySimpleName(JavaMethod method) {
    return resolveRepositoryEntity(method.getOwner())
        .map(JavaClass::getSimpleName)
        .orElse(method.getOwner().getSimpleName());
  }

  /** 递归向父接口查找 {@code com.bone.metadata.sdk.Repository<T, ID>} 的 {@code T}。 */
  private static Optional<JavaClass> resolveRepositoryEntity(JavaClass repository) {
    for (com.tngtech.archunit.core.domain.JavaType iface : repository.getInterfaces()) {
      if ("com.bone.metadata.sdk.Repository".equals(iface.toErasure().getName())) {
        if (iface instanceof JavaParameterizedType parameterized
            && !parameterized.getActualTypeArguments().isEmpty()) {
          return Optional.of(parameterized.getActualTypeArguments().get(0).toErasure());
        }
        return Optional.empty();
      }
      Optional<JavaClass> nested = resolveRepositoryEntity(iface.toErasure());
      if (nested.isPresent()) {
        return nested;
      }
    }
    return Optional.empty();
  }

  private static String signature(JavaMethod method) {
    return method.getOwner().getSimpleName() + "#" + method.getName() + "()";
  }

  // ===== 资源侧（外置 .sql）=====

  /** 全部外置模板 ID（{@code <接口全限定名>.<方法名>}）。 */
  private static Set<String> externalTemplateIds() throws IOException {
    Set<String> ids = new TreeSet<>();
    for (Path file : sqlFiles()) {
      ids.add(templateIdFromResourcePath(SQL_ROOT.relativize(file).toString().replace('\\', '/')));
    }
    return ids;
  }

  /** 模板 ID → SQL 文本（外置文件）。 */
  private static Map<String, String> templateSqlByMethodId() throws IOException {
    Map<String, String> byId = new LinkedHashMap<>();
    for (Path file : sqlFiles()) {
      String id =
          templateIdFromResourcePath(SQL_ROOT.relativize(file).toString().replace('\\', '/'));
      byId.put(id, Files.readString(file, StandardCharsets.UTF_8));
    }
    return byId;
  }

  private static List<Path> sqlFiles() throws IOException {
    if (!Files.isDirectory(SQL_ROOT)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(SQL_ROOT)) {
      return walk.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
    }
  }

  /**
   * {@code com/bone/blueprint/domain/repository/OrderRepository/findOrderWithItems.sql} → {@code
   * com.bone.blueprint.domain.repository.OrderRepository.findOrderWithItems}
   */
  static String templateIdFromResourcePath(String relativePath) {
    String withoutExtension = relativePath.substring(0, relativePath.length() - ".sql".length());
    String[] segments = withoutExtension.split("/");
    String method = segments[segments.length - 1];
    String typeName = segments[segments.length - 2];
    String packageName = String.join(".", java.util.Arrays.copyOf(segments, segments.length - 2));
    return packageName + "." + typeName + "." + method;
  }

  // ===== SQL 文本判据 =====

  /** 门禁③：同时存在于「外置模板」与「@Sql 标注」两边的模板 ID（影子 SQL）。 */
  static Set<String> duplicateTemplateIds(Set<String> externalIds, Set<String> inlineIds) {
    Set<String> duplicates = new TreeSet<>(inlineIds);
    duplicates.retainAll(externalIds);
    return duplicates;
  }

  /** SQL 里手写了 {@code tenant_id}（AUTO 模式下会与自动注入重复）。 */
  static boolean handWrittenTenantId(String sql) {
    return HAND_WRITTEN_TENANT_ID.matcher(stripSqlComments(sql)).find();
  }

  /** {@code FROM} / {@code JOIN} 引用的表名集合。 */
  static Set<String> tablesReferenced(String sql) {
    Set<String> tables = new TreeSet<>();
    for (TableRef ref : tableRefs(sql)) {
      tables.add(ref.table());
    }
    return tables;
  }

  /**
   * 手写软删未覆盖的别名集合（R4）。
   *
   * <p>每个 {@code FROM}/{@code JOIN} 目标都要有自己的 {@code <别名>.deleted = 0}；无别名的单表写法要求整条 SQL 出现 {@code
   * deleted = 0}，此时用哨兵 {@code <unaliased>} 标记。
   */
  static Set<String> joinAliasesMissingSoftDelete(String sql) {
    String text = stripSqlComments(sql).toLowerCase(java.util.Locale.ROOT);
    Set<String> missing = new TreeSet<>();
    for (TableRef ref : tableRefs(sql)) {
      if (ref.alias() == null) {
        if (!Pattern.compile("\\bdeleted\\s*=\\s*0").matcher(text).find()) {
          missing.add("<unaliased>");
        }
      } else if (!Pattern.compile(
              "\\b"
                  + Pattern.quote(ref.alias().toLowerCase(java.util.Locale.ROOT))
                  + "\\.deleted\\s*=\\s*0")
          .matcher(text)
          .find()) {
        missing.add(ref.alias());
      }
    }
    return missing;
  }

  private static List<TableRef> tableRefs(String sql) {
    List<TableRef> refs = new ArrayList<>();
    Matcher matcher = TABLE_REF.matcher(stripSqlComments(sql));
    while (matcher.find()) {
      String table = matcher.group(2);
      if (SQL_KEYWORDS.contains(table.toLowerCase(java.util.Locale.ROOT))) {
        continue;
      }
      String candidate = matcher.group(3);
      String alias =
          (candidate == null || SQL_KEYWORDS.contains(candidate.toLowerCase(java.util.Locale.ROOT)))
              ? null
              : candidate;
      refs.add(new TableRef(table, alias));
    }
    return refs;
  }

  /** 去掉 {@code --} 行注释与 {@code /* *}{@code /} 块注释，避免注释里的 {@code tenant_id} 造成误报。 */
  private static String stripSqlComments(String sql) {
    return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ");
  }

  private record TableRef(String table, String alias) {}

  // ===== README 的 E-1.2 数据所有权声明 =====

  /** 归属聚合 cell → 该聚合拥有的表集合。 */
  private static Map<String, Map<String, String>> ownedTablesByAggregateHolder()
      throws IOException {
    Map<String, Map<String, String>> owned = new LinkedHashMap<>();
    if (!Files.exists(README)) {
      return owned;
    }
    List<String> lines = Files.readAllLines(README, StandardCharsets.UTF_8);
    boolean inSection = false;
    boolean seenHeader = false;
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.startsWith("#")) {
        inSection = trimmed.contains(OWNERSHIP_HEADING);
        seenHeader = false;
        continue;
      }
      if (!inSection || !trimmed.startsWith("|")) {
        continue;
      }
      List<String> cells = splitRow(trimmed);
      if (cells.size() < 3) {
        continue;
      }
      if (!seenHeader) {
        seenHeader = cells.get(0).equals(OWNED_TABLE_HEADER);
        continue;
      }
      String table = cells.get(0);
      if (table.isEmpty() || table.startsWith("---") || table.startsWith(":")) {
        continue;
      }
      owned.computeIfAbsent(cells.get(2), key -> new LinkedHashMap<>()).put(table, cells.get(1));
    }
    return owned;
  }

  private static List<String> splitRow(String line) {
    String body = line.replaceAll("^\\|", "").replaceAll("\\|$", "");
    List<String> cells = new ArrayList<>();
    for (String cell : body.split("\\|", -1)) {
      cells.add(stripBackticks(cell.trim()));
    }
    return cells;
  }

  private static String stripBackticks(String cell) {
    return cell.replace("`", "").trim();
  }

  private static Set<String> allowedTables(
      Map<String, Map<String, String>> owned, String aggregate) {
    Set<String> tables = new TreeSet<>();
    for (Map.Entry<String, Map<String, String>> entry : owned.entrySet()) {
      if (entry.getKey().contains(aggregate)) {
        tables.addAll(entry.getValue().keySet());
      }
    }
    return tables;
  }
}
