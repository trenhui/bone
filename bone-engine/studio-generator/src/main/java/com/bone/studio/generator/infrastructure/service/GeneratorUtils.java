package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class GeneratorUtils {

  /**
   * 框架已托管的列：这些列由 {@code TenantAggregateRoot} / {@code Entity} 基类或 SDK 自己维护（主键、租户、审计、软删、乐观锁），
   * 模板不得再生成同名字段——重复声明会与基类字段冲突，且人类手写的 {@code delete/update} 语义会被覆盖。
   */
  private static final Set<String> FRAMEWORK_COLUMNS =
      Set.of("id", "tenant_id", "created_at", "updated_at", "deleted", "version");

  /** {@code javaType → 需要 import 的全限定类名}（仅 {@code java.*} 需要显式导入；{@code Lang} 类型默认可见）。 */
  private static final Map<String, String> JAVA_TYPE_IMPORTS =
      Map.of(
          "BigDecimal", "java.math.BigDecimal",
          "LocalDate", "java.time.LocalDate",
          "LocalDateTime", "java.time.LocalDateTime",
          "LocalTime", "java.time.LocalTime");

  /** 聚合包段（ADR-0036 D1 的 {@code domain/model/{聚合}/}）：实体名全小写。 */
  public static String aggregateSegment(String entityName) {
    return entityName == null || entityName.isEmpty()
        ? "model"
        : entityName.toLowerCase(Locale.ROOT);
  }

  /** 控制器 REST 前缀：{@code /api/v1/{模块}}（模块名里的下划线转短横线）。 */
  public static String apiPrefix(String moduleName) {
    if (moduleName == null || moduleName.isEmpty()) {
      return "/api/v1";
    }
    return "/api/v1/" + moduleName.replace('_', '-').toLowerCase(Locale.ROOT);
  }

  /**
   * 生成文件的根目录：{@code sourceRoot} + {@code basePackage} 的路径形式 + 模块段。
   *
   * <p>源根由调用方给（{@code src/main/java} / {@code src/test/java} / 空串=模块根）， 此前写死缺源根，产物落在 {@code
   * generated-code/com/example/demo/...}，拷进工程无法编译。
   */
  public static String basePath(String sourceRoot, String basePackage, String moduleName) {
    // 目录必须与包名同源：都用 effectivePackage 推导，否则「包名去重了、目录没去重」会让产物落到包结构之外的目录，
    // javac 直接报「类 X 位于错误的包/目录」。
    String packagePath = effectivePackage(basePackage, moduleName).replace('.', '/');
    String pkg = packagePath.isEmpty() ? "" : "/" + packagePath;
    String root = sourceRoot == null || sourceRoot.isEmpty() ? "" : sourceRoot + "/";
    String path = pkg;
    // path 以分隔符开头，与 root 直接拼接会出现 src/main/java//com/...
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    return root + path;
  }

  /** 模板入口：Freemarker 以实例方法调用。 */
  public String toPackageSegment(String entityName) {
    return aggregateSegment(entityName);
  }

  public String toCamelCase(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else {
        if (capitalizeNext) {
          result.append(Character.toUpperCase(c));
          capitalizeNext = false;
        } else {
          result.append(Character.toLowerCase(c));
        }
      }
    }
    // 首字母大写，用于类名
    if (result.length() > 0) {
      result.setCharAt(0, Character.toUpperCase(result.charAt(0)));
    }
    return result.toString();
  }

  public String toFieldName(String str) {
    String camelCase = toCamelCase(str);
    if (camelCase.length() > 0) {
      return Character.toLowerCase(camelCase.charAt(0)) + camelCase.substring(1);
    }
    return camelCase;
  }

  public String getJavaType(String columnType) {
    // 简单的类型映射，实际项目中可能需要更复杂的映射
    switch (columnType.toLowerCase()) {
      case "varchar":
      case "char":
      case "text":
      case "longtext":
      case "mediumtext":
        return "String";
      case "int":
      case "integer":
      case "smallint":
      case "tinyint":
        return "Integer";
      case "bigint":
        return "Long";
      case "float":
        return "Float";
      case "double":
        return "Double";
      case "decimal":
        return "BigDecimal";
      case "date":
        return "LocalDate";
      case "datetime":
      case "timestamp":
        return "LocalDateTime";
      case "time":
        return "LocalTime";
      case "boolean":
      case "bit":
        return "Boolean";
      default:
        return "String";
    }
  }

  /**
   * 包路径：{@code basePackage} + 模块段，并对「末段重复」去重。
   *
   * <p><b>为何要去重</b>：真实填写习惯里，用户常把 {@code basePackage} 写成已含模块名（{@code com.bone.order}） 再填 {@code
   * moduleName=order}，直接拼接会得到 {@code com.bone.order.order}——产物包名与目录双双多一层， 拷进工程后整包 import
   * 全部失效。这里只在模块段与基础包末段完全相同时折叠，不影响 {@code com.bone + order} 这类正常组合。
   */
  public String getPackagePath(String basePackage, String moduleName) {
    return effectivePackage(basePackage, moduleName);
  }

  /** {@link #getPackagePath(String, String)} 的静态入口，供 {@link #basePath} 保持目录与包名同源。 */
  public static String effectivePackage(String basePackage, String moduleName) {
    if (basePackage == null || basePackage.isEmpty()) {
      return moduleName == null ? "" : moduleName;
    }
    if (moduleName == null || moduleName.isEmpty()) {
      return basePackage;
    }
    String normalizedModule = moduleName.replace('-', '_').toLowerCase(Locale.ROOT);
    int lastDot = basePackage.lastIndexOf('.');
    String lastSegment = lastDot < 0 ? basePackage : basePackage.substring(lastDot + 1);
    if (lastSegment.replace('-', '_').toLowerCase(Locale.ROOT).equals(normalizedModule)) {
      return basePackage;
    }
    return basePackage + "." + moduleName;
  }

  /**
   * REST 资源段：实体名小写 + 规则复数（{@code Order → orders}、{@code Class → classes}）。
   *
   * <p>对齐 blueprint {@code @RequestMapping("/api/v1/orders")} 的资源写法，不再用单数的 {@code /api/v1/order}。
   */
  public static String resourceSegment(String entityName) {
    if (entityName == null || entityName.isEmpty()) {
      return "resources";
    }
    String lower = entityName.toLowerCase(Locale.ROOT);
    if (lower.endsWith("s")
        || lower.endsWith("x")
        || lower.endsWith("ch")
        || lower.endsWith("sh")) {
      return lower + "es";
    }
    return lower + "s";
  }

  /** 模板入口写法：Freemarker 以实例方法调用。 */
  public String toResourceSegment(String entityName) {
    return resourceSegment(entityName);
  }

  /**
   * 参与生成的业务列：剔除 {@link #FRAMEWORK_COLUMNS}，并把模板要用的派生值一次性算好。
   *
   * <p>为何预先摊平成 {@code Map}：列实体 {@link GenColumnMetadata} 的 {@code boolean isNullable} 经 Lombok 生成
   * {@code isNullable()}，其 JavaBean 属性名是 {@code nullable} 而非 {@code isNullable}，模板里直接写 {@code
   * column.isNullable} 会因属性缺失渲染失败（这一歧义此前没暴露，是因为旧模板根本没用过该字段）。
   */
  public static List<Map<String, Object>> businessColumns(GenTableMetadata table) {
    if (table == null || table.getColumns() == null) {
      return List.of();
    }
    GeneratorUtils self = new GeneratorUtils();
    List<Map<String, Object>> result = new ArrayList<>();
    for (GenColumnMetadata column : table.getColumns()) {
      String original = column.getOriginalColumnName();
      if (original == null || FRAMEWORK_COLUMNS.contains(original.toLowerCase(Locale.ROOT))) {
        continue;
      }
      Map<String, Object> view = new HashMap<>();
      view.put("originalColumnName", original);
      String javaType = column.getJavaType();
      view.put("javaType", javaType == null || javaType.isEmpty() ? "String" : javaType);
      String comment = column.getColumnComment();
      view.put("comment", comment == null || comment.isEmpty() ? original : comment);
      String fieldName = self.toFieldName(original);
      view.put("fieldName", fieldName);
      view.put("getter", getterOf(fieldName, javaType));
      view.put("nullable", column.isNullable());
      Integer length = column.getColumnLength();
      view.put("length", length == null || length <= 0 ? 0 : length);
      result.add(view);
    }
    return List.copyOf(result);
  }

  /**
   * 派生 Lombok {@code @Getter} 的访问器名。
   *
   * <p><b>这条规则是实测出来的，不是猜的</b>：把 {@code is_deleted} 列生成 {@code Boolean isDeleted} 字段后编译验证， Lombok
   * 给出的是 {@code getIsDeleted()} 而非 {@code isDeleted()}——Lombok 只对<b>原始类型</b> {@code boolean} 使用
   * {@code is} 前缀（并在字段名已以 {@code is} 开头时不再叠加），包装类 {@code Boolean} 一律走 {@code get} 前缀。
   *
   * <p>本生成器的 {@code javaType} 产出的是包装类 {@code Boolean}，因此必须按包装类判。早前版本无条件给布尔字段加 {@code is}
   * 前缀，生成出的读模型直接编译不过（javac 报「找不到符号 isDeleted()」）。
   */
  public static String getterOf(String fieldName, String javaType) {
    if (fieldName == null || fieldName.isEmpty()) {
      return "getId";
    }
    String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    if ("boolean".equals(javaType)) {
      boolean alreadyPrefixed =
          fieldName.startsWith("is")
              && fieldName.length() > 2
              && !Character.isLowerCase(fieldName.charAt(2));
      return alreadyPrefixed ? fieldName : "is" + capitalized;
    }
    return "get" + capitalized;
  }

  /**
   * 按 Java 类型给出可直接写进测试代码的示例值。
   *
   * <p>生成的聚合单测要给 {@code create()} 传一套合法入参；类型到字面量的映射只有一份， 散在模板里就得再维护一遍（且漏一种类型就是编译不过）。
   */
  public String sampleOf(String javaType) {
    if (javaType == null) {
      return "\"示例\"";
    }
    switch (javaType) {
      case "Long":
        return "1L";
      case "Integer":
        return "1";
      case "BigDecimal":
        return "new BigDecimal(\"1\")";
      case "LocalDate":
        return "LocalDate.of(2026, 1, 1)";
      case "LocalDateTime":
        return "LocalDateTime.of(2026, 1, 1, 0, 0)";
      case "LocalTime":
        return "LocalTime.NOON";
      case "Boolean":
        return "Boolean.TRUE";
      case "Float":
        return "1.0F";
      case "Double":
        return "1.0D";
      default:
        return "\"示例\"";
    }
  }

  /**
   * 业务列涉及的 {@code java.*} 类型导入清单（去重且有序），模板用 {@code <#list businessTypeImports>} 展开。
   *
   * <p>放在 Java 侧算而不是模板里重复 {@code <#assign needBigDecimal>}：五个模板都要这段判定，同一段逻辑复制五份已经漂移过一次。
   */
  public static List<String> businessTypeImports(GenTableMetadata table) {
    Set<String> imports = new TreeSet<>();
    for (Map<String, Object> column : businessColumns(table)) {
      String fqn = JAVA_TYPE_IMPORTS.get(String.valueOf(column.get("javaType")));
      if (fqn != null) {
        imports.add(fqn);
      }
    }
    return List.copyOf(imports);
  }
}
