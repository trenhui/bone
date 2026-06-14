package com.bone.engine.extension.core.doc;

import com.bone.engine.extension.api.annotation.ExtensionPointDoc;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 扩展文档生成器 增强 @ExtPointDoc 注解的功能，实现自动生成扩展点文档 */
@Slf4j
@Component
public class ExtensionDocGenerator {

  /**
   * 生成扩展点文档
   *
   * @param extensionPointClasses 扩展点类列表
   * @param outputDir 输出目录
   */
  public void generateDocumentation(List<Class<?>> extensionPointClasses, String outputDir) {
    try {
      // 创建输出目录
      File dir = new File(outputDir);
      if (!dir.exists()) {
        dir.mkdirs();
      }

      // 生成主文档
      generateMainDocumentation(extensionPointClasses, outputDir);

      // 生成每个扩展点的详细文档
      for (Class<?> clazz : extensionPointClasses) {
        generateExtensionPointDocumentation(clazz, outputDir);
      }

      log.info("Extension documentation generated successfully: {}", outputDir);
    } catch (Exception e) {
      log.error("Error generating extension documentation", e);
    }
  }

  /**
   * 生成主文档
   *
   * @param extensionPointClasses 扩展点类列表
   * @param outputDir 输出目录
   * @throws IOException IO异常
   */
  private void generateMainDocumentation(List<Class<?>> extensionPointClasses, String outputDir)
      throws IOException {
    File file = new File(outputDir, "extension-points.md");
    try (FileWriter writer = new FileWriter(file)) {
      writer.write("# 扩展点文档\n\n");
      writer.write("## 扩展点列表\n\n");

      for (Class<?> clazz : extensionPointClasses) {
        ExtensionPointDoc docAnnotation = clazz.getAnnotation(ExtensionPointDoc.class);
        if (docAnnotation != null) {
          writer.write(
              String.format(
                  "- [%s](%s.md) - %s\n",
                  clazz.getSimpleName(), clazz.getSimpleName(), docAnnotation.description()));
        } else {
          writer.write(
              String.format("- [%s](%s.md)\n", clazz.getSimpleName(), clazz.getSimpleName()));
        }
      }
    }
  }

  /**
   * 生成扩展点详细文档
   *
   * @param extensionPointClass 扩展点类
   * @param outputDir 输出目录
   * @throws IOException IO异常
   */
  private void generateExtensionPointDocumentation(Class<?> extensionPointClass, String outputDir)
      throws IOException {
    File file = new File(outputDir, extensionPointClass.getSimpleName() + ".md");
    try (FileWriter writer = new FileWriter(file)) {
      // 写入类信息
      ExtensionPointDoc docAnnotation = extensionPointClass.getAnnotation(ExtensionPointDoc.class);

      writer.write(String.format("# %s\n\n", extensionPointClass.getSimpleName()));

      if (docAnnotation != null) {
        writer.write(String.format("## 描述\n%s\n\n", docAnnotation.description()));
        writer.write(String.format("## 版本\n%s\n\n", docAnnotation.version()));
        writer.write(String.format("## 作者\n%s\n\n", docAnnotation.author()));
      }

      // 写入方法信息
      writer.write("## 方法\n\n");

      Method[] methods = extensionPointClass.getDeclaredMethods();
      for (Method method : methods) {
        writer.write(String.format("### %s\n\n", method.getName()));
        writer.write(String.format("**参数**: %s\n\n", getParameterDescription(method)));
        writer.write(String.format("**返回值**: %s\n\n", method.getReturnType().getSimpleName()));
        writer.write(String.format("**描述**: %s\n\n", getMethodDescription(method)));
      }
    }
  }

  /**
   * 获取参数描述
   *
   * @param method 方法
   * @return 参数描述
   */
  private String getParameterDescription(Method method) {
    Class<?>[] parameterTypes = method.getParameterTypes();
    List<String> paramDescriptions = new ArrayList<>();

    for (int i = 0; i < parameterTypes.length; i++) {
      paramDescriptions.add(parameterTypes[i].getSimpleName() + " param" + i);
    }

    return String.join(", ", paramDescriptions);
  }

  /**
   * 获取方法描述
   *
   * @param method 方法
   * @return 方法描述
   */
  private String getMethodDescription(Method method) {
    // 实际实现中，这里可以从方法注解中获取描述
    return "方法描述";
  }

  /**
   * 生成在线文档
   *
   * @param extensionPointClasses 扩展点类列表
   * @return 在线文档HTML
   */
  public String generateOnlineDocumentation(List<Class<?>> extensionPointClasses) {
    StringBuilder html = new StringBuilder();

    html.append("<!DOCTYPE html>");
    html.append("<html>");
    html.append("<head>");
    html.append("<title>扩展点文档</title>");
    html.append("<style>");
    html.append("body { font-family: Arial, sans-serif; margin: 20px; }");
    html.append("h1 { color: #333; }");
    html.append("h2 { color: #555; }");
    html.append("h3 { color: #777; }");
    html.append(
        ".extension-point { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }");
    html.append(".method { margin: 10px 0; padding: 10px; border-left: 3px solid #ccc; }");
    html.append("</style>");
    html.append("</head>");
    html.append("<body>");
    html.append("<h1>扩展点文档</h1>");

    for (Class<?> clazz : extensionPointClasses) {
      ExtensionPointDoc docAnnotation = clazz.getAnnotation(ExtensionPointDoc.class);

      html.append("<div class='extension-point'>");
      html.append(String.format("<h2>%s</h2>", clazz.getSimpleName()));

      if (docAnnotation != null) {
        html.append(String.format("<p><strong>描述:</strong> %s</p>", docAnnotation.description()));
        html.append(String.format("<p><strong>版本:</strong> %s</p>", docAnnotation.version()));
        html.append(String.format("<p><strong>作者:</strong> %s</p>", docAnnotation.author()));
      }

      html.append("<h3>方法</h3>");

      Method[] methods = clazz.getDeclaredMethods();
      for (Method method : methods) {
        html.append("<div class='method'>");
        html.append(String.format("<h4>%s</h4>", method.getName()));
        html.append(
            String.format("<p><strong>参数:</strong> %s</p>", getParameterDescription(method)));
        html.append(
            String.format(
                "<p><strong>返回值:</strong> %s</p>", method.getReturnType().getSimpleName()));
        html.append(String.format("<p><strong>描述:</strong> %s</p>", getMethodDescription(method)));
        html.append("</div>");
      }

      html.append("</div>");
    }

    html.append("</body>");
    html.append("</html>");

    return html.toString();
  }
}
