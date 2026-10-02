package com.bone.studio.generator.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 模板类型四处一致门禁：classpath 模板 / 生成器内置清单 / 初始化种子 / 存量迁移脚本。
 *
 * <p><b>为何要这道门禁</b>：内置模板的类型码散落在四处，任一处漏改都会静默少产出文件—— 控制器引用不存在的 {@code *Req/*Resp}，或用户勾选不到某个类型。此前
 * {@code response} 类型就只存在于 classpath 与生成器里、种子库里根本没有那一行，物理库链路因此永远不产出响应对象。
 *
 * <p>新增模板时四处都要加，这里会以失败的方式提醒你，避免再次出现"生成半截骨架"。
 */
class TemplateTypeConsistencyTest {

  /** 模块基于目录 → 仓库根。 */
  private static final Path REPO_ROOT = Paths.get("../..").normalize();

  private static final Pattern SEED_ROW =
      Pattern.compile("0, '[^']*', '([A-Za-z]+)', '[^']*', '([A-Za-z]+)',");
  private static final Pattern MIGRATION_ROW = Pattern.compile("code = '([A-Za-z]+)'\\);");

  @Test
  void builtInTypesMatchClasspathTemplates() {
    // classpath 模板 = 固定骨架 12 类 + 开关产物 2 类（单测 / 文档）+ 关系级产物 1 类（主子聚合）
    Set<String> all = new TreeSet<>(CodeGeneratorServiceImpl.BUILT_IN_TEMPLATE_TYPES);
    all.addAll(CodeGeneratorServiceImpl.OPTIONAL_TEMPLATE_TYPES);
    all.addAll(CodeGeneratorServiceImpl.RELATION_TEMPLATE_TYPES);
    assertEquals(all, new TreeSet<>(classpathTemplateCodes()), "生成器清单与 classpath 模板文件不一致");
  }

  @Test
  void optionalTypesAreNotSeededAsTemplateRows() throws IOException {
    // 开关产物不进模板管理：物理库链路的入参没有这两个开关，做成可勾选行会「勾了没反应」
    Set<String> seeded = new TreeSet<>(parseGroups(seedBlock(), SEED_ROW, 2));
    assertTrue(
        seeded.containsAll(CodeGeneratorServiceImpl.BUILT_IN_TEMPLATE_TYPES),
        "12 个骨架模板必须有种子行，否则物理库链路不产出对应文件");
    for (String optional : CodeGeneratorServiceImpl.OPTIONAL_TEMPLATE_TYPES) {
      assertFalse(seeded.contains(optional), "开关产物不应成为模板行: " + optional);
    }
  }

  @Test
  void seedSqlCoversAllBuiltInTypes() throws IOException {
    assertEquals(
        new TreeSet<>(CodeGeneratorServiceImpl.BUILT_IN_TEMPLATE_TYPES),
        new TreeSet<>(parseGroups(seedBlock(), SEED_ROW, 2)),
        "bone-init.sql 的种子行与内置模板清单不一致");
  }

  @Test
  void migrationScriptCoversAllBuiltInTypes() throws IOException {
    Path migration =
        REPO_ROOT.resolve("scripts/migration/0009_generator_builtin_template_convergence.sql");
    assertTrue(Files.exists(migration), "缺少存量迁移脚本 0009");
    String sql = Files.readString(migration);
    assertEquals(
        new TreeSet<>(CodeGeneratorServiceImpl.BUILT_IN_TEMPLATE_TYPES),
        new TreeSet<>(parseGroups(sql, MIGRATION_ROW, 1)),
        "迁移脚本 0009 的补齐列表与内置模板清单不一致");
    // 幂等兜底：补齐语句必须带 NOT EXISTS，否则存量环境重复执行会撞主键
    long inserts = sql.lines().filter(l -> l.startsWith("INSERT INTO gen_code_template")).count();
    assertEquals(12, inserts, "迁移脚本应为 12 个内置类型各准备一条补齐语句");
    assertEquals(12, sql.lines().filter(l -> l.contains("FROM DUAL WHERE NOT EXISTS")).count());
  }

  @Test
  void seedRowsMustNotCarryTemplateContent() throws IOException {
    // 真源是 classpath .ftl：种子里复制正文曾漂移成引用不存在的属性，导致生成任务直接失败
    assertFalse(seedBlock().contains("${table.customEntityName}"), "种子数据不应复制模板正文");
  }

  /** 只取 gen_code_template 的种子语句块：文件里还有其他形近的 INSERT，整表扫会误命中。 */
  private String seedBlock() throws IOException {
    String init = Files.readString(REPO_ROOT.resolve("bone-init.sql"));
    int start = init.indexOf("INSERT INTO gen_code_template (");
    assertTrue(start > 0, "未找到 gen_code_template 种子语句");
    int end = init.indexOf("CREATE TABLE gen_type_mapping", start);
    return init.substring(start, end > 0 ? end : init.length());
  }

  private List<String> classpathTemplateCodes() {
    Path dir = Paths.get("target/classes/templates");
    assertTrue(Files.isDirectory(dir), "未找到 classpath 模板目录: " + dir);
    try (var stream = Files.list(dir)) {
      List<String> codes = new ArrayList<>();
      stream
          .filter(p -> p.getFileName().toString().endsWith(".ftl"))
          .forEach(
              p -> {
                String name = p.getFileName().toString();
                codes.add(name.substring(0, name.length() - ".ftl".length()));
              });
      return codes;
    } catch (IOException e) {
      throw new IllegalStateException("读取模板目录失败: " + dir, e);
    }
  }

  private List<String> parseGroups(String text, Pattern pattern, int group) {
    List<String> found = new ArrayList<>();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      found.add(matcher.group(group));
    }
    Set<String> unique = new TreeSet<>(found);
    assertEquals(found.size(), unique.size(), "解析到重复的模板类型码，正则或源文件需核对");
    return found;
  }
}
