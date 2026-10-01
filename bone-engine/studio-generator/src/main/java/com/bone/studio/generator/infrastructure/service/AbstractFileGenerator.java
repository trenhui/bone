package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.service.FileGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件生成器公共实现：统一装配模板变量 + 统一 {@code filePath} 与 {@code package} 的对齐规则。
 *
 * <p><b>为何抽基类</b>：此前五个 Generator 各自 {@code model.put} 一份，变量集合并不一致——{@code repository} 塞了模板从不使用的
 * {@code entityName}；{@code applicationService} 没塞 {@code columns}，导致模板一旦要遍历列就抛 {@code
 * InvalidReferenceException}。抽成基类后变量全集只有一处定义。
 *
 * <p><b>变量全集</b>（模板可用）：{@code table}、{@code columns}、{@code businessColumns}、{@code
 * businessTypeImports}、{@code basePackage}、{@code moduleName}、{@code apiPrefix}、{@code utils}。
 */
public abstract class AbstractFileGenerator implements FileGenerator {

  /**
   * 顶层类型声明：{@code public (final|abstract|sealed)? (class|interface|enum|record) Name}。
   *
   * <p>用于渲染后把「文件名」对齐到「模板真正声明的类型名」。
   */
  private static final Pattern TOP_LEVEL_TYPE =
      Pattern.compile(
          "^\\s*public\\s+(?:final\\s+|abstract\\s+|sealed\\s+)*"
              + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)",
          Pattern.MULTILINE);

  protected final TemplateRenderer templateRenderer;

  protected AbstractFileGenerator(TemplateRenderer templateRenderer) {
    this.templateRenderer = templateRenderer;
  }

  @Override
  public boolean supports(String templateType) {
    return templateType().equals(templateType);
  }

  @Override
  public GeneratedFile generate(
      GenTableMetadata table, CodeTemplate template, String basePackage, String moduleName) {
    Map<String, Object> model = new HashMap<>();
    model.put("table", table);
    model.put(
        "columns", table == null || table.getColumns() == null ? List.of() : table.getColumns());
    model.put("businessColumns", GeneratorUtils.businessColumns(table));
    model.put("businessTypeImports", GeneratorUtils.businessTypeImports(table));
    model.put("basePackage", basePackage);
    model.put("moduleName", moduleName);
    model.put("utils", new GeneratorUtils());
    model.put("apiPrefix", GeneratorUtils.apiPrefix(moduleName));

    String content = templateRenderer.render(template, model);
    String fileName = reconcileFileName(fileName(table), content);
    String filePath =
        GeneratorUtils.basePath(sourceRoot(), basePackage, moduleName)
            + directory(table)
            + fileName;
    return GeneratedFile.builder()
        .filePath(filePath)
        .fileName(fileName)
        .content(content)
        .fileType(fileType())
        .fileSize(content.length())
        .build();
  }

  /**
   * 文件名对齐到模板实际声明的顶层类型名（编译要求：public 类名必须与文件名一致）。
   *
   * <p><b>为何必须做</b>：{@code fileName()} 是生成器侧的硬编码约定（如 {@code {Agg}Resp.java}），而模板内容
   * 可由用户在模板管理页自由编辑并入库（{@code gen_code_template.content}）。两者一旦漂移——例如用户把类名改成 {@code
   * {Agg}Response}——生成的 Java 文件就会因「public 类名 ≠ 文件名」在 javac 阶段直接失败，
   * 且报错出现在用户下载的工程里，很难回溯到模板。这里以模板声明为准做一次收敛：模板是用户可见可改的真源， 生成器命名约定只是默认值。
   *
   * <p>只对 {@code .java} 生效；非 Java 产物（md）与「模板未声明 public 类型」（如 package-info、 匿名脚本）保持生成器原命名，不做猜测。
   *
   * @param declaredByGenerator 生成器约定的文件名
   * @param content 渲染后的文件内容
   * @return 与顶层类型声明一致的文件名
   */
  private String reconcileFileName(String declaredByGenerator, String content) {
    if (declaredByGenerator == null || !declaredByGenerator.endsWith(".java")) {
      return declaredByGenerator;
    }
    String declared = primaryTypeName(content);
    if (declared == null) {
      return declaredByGenerator;
    }
    int dot = declaredByGenerator.lastIndexOf('.');
    String base = declaredByGenerator.substring(0, dot);
    if (base.equals(declared)) {
      return declaredByGenerator;
    }
    return declared + ".java";
  }

  /** 取文件内容里第一个 public 顶层类型名；没有则返回 null。 */
  private static String primaryTypeName(String content) {
    if (content == null || content.isEmpty()) {
      return null;
    }
    Matcher matcher = TOP_LEVEL_TYPE.matcher(content);
    return matcher.find() ? matcher.group(1) : null;
  }

  /** 模板类型码，与 {@code gen_code_template.type} 及 classpath 下 {@code *.ftl} 文件名一致。 */
  protected abstract String templateType();

  /** 产物源根：主源码 {@code src/main/java}、测试源码 {@code src/test/java}、文档为空串（模块根）。 */
  protected String sourceRoot() {
    return "src/main/java";
  }

  /** 产物类型：默认 java，文档类模板覆盖为 md。 */
  protected String fileType() {
    return "java";
  }

  /** 产物相对目录，必须以 {@code /} 结尾，且与模板声明的 package 保持同源规则。 */
  protected abstract String directory(GenTableMetadata table);

  /** 产物文件名（含 {@code .java}）。 */
  protected abstract String fileName(GenTableMetadata table);
}
