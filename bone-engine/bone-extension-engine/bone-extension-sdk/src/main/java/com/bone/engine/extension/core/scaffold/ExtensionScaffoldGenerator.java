package com.bone.engine.extension.core.scaffold;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扩展点脚手架生成器
 *
 * <p>提供扩展点脚手架工具，自动生成扩展点模板
 *
 * @since 1.0.0
 */
public class ExtensionScaffoldGenerator {

  private static final Logger log = LoggerFactory.getLogger(ExtensionScaffoldGenerator.class);

  /**
   * 生成扩展点接口模板
   *
   * @param packageName 包名
   * @param interfaceName 接口名
   * @param outputDir 输出目录
   * @throws IOException 生成文件失败
   */
  public void generateExtensionPointInterface(
      String packageName, String interfaceName, String outputDir) throws IOException {
    // 构建文件路径
    String filePath =
        outputDir + "/" + packageName.replace(".", "/") + "/" + interfaceName + ".java";
    File file = new File(filePath);

    // 创建目录
    file.getParentFile().mkdirs();

    // 生成代码
    String code = generateInterfaceCode(packageName, interfaceName);

    // 写入文件
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(code);
    }

    log.info("Generated extension point interface: {}", filePath);
  }

  /**
   * 生成扩展点实现模板
   *
   * @param packageName 包名
   * @param interfaceName 接口名
   * @param implementationName 实现类名
   * @param outputDir 输出目录
   * @throws IOException 生成文件失败
   */
  public void generateExtensionImplementation(
      String packageName, String interfaceName, String implementationName, String outputDir)
      throws IOException {
    // 构建文件路径
    String filePath =
        outputDir + "/" + packageName.replace(".", "/") + "/" + implementationName + ".java";
    File file = new File(filePath);

    // 创建目录
    file.getParentFile().mkdirs();

    // 生成代码
    String code = generateImplementationCode(packageName, interfaceName, implementationName);

    // 写入文件
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(code);
    }

    log.info("Generated extension point implementation: {}", filePath);
  }

  /**
   * 生成扩展点接口代码
   *
   * @param packageName 包名
   * @param interfaceName 接口名
   * @return 接口代码
   */
  private String generateInterfaceCode(String packageName, String interfaceName) {
    return String.format(
        "package %s;\n\n"
            + "import com.bone.engine.extension.api.annotation.ExtensionPoint;\n\n"
            + "/**\n"
            + " * %s 扩展点接口\n"
            + " */\n"
            + "@ExtensionPoint\n"
            + "public interface %s {\n\n"
            + "    /**\n"
            + "     * 扩展点方法示例\n"
            + "     * \n"
            + "     * @param context 业务上下文\n"
            + "     * @return 结果\n"
            + "     */\n"
            + "    String execute(com.bone.engine.extension.support.context.BizContext<?> context);\n\n"
            + "}",
        packageName, interfaceName, interfaceName);
  }

  /**
   * 生成扩展点实现代码
   *
   * @param packageName 包名
   * @param interfaceName 接口名
   * @param implementationName 实现类名
   * @return 实现代码
   */
  private String generateImplementationCode(
      String packageName, String interfaceName, String implementationName) {
    return String.format(
        "package %s;\n\n"
            + "import com.bone.engine.extension.api.annotation.Extension;\n"
            + "import com.bone.engine.extension.support.context.BizContext;\n\n"
            + "/**\n"
            + " * %s 扩展点实现\n"
            + " */\n"
            + "@Extension(\n"
            + "    name = \"%s\",\n"
            + "    description = \"%s 扩展点实现\",\n"
            + "    tenant = \"*\",\n"
            + "    bizCode = \"*\",\n"
            + "    useCase = \"*\",\n"
            + "    scenario = \"*\",\n"
            + "    env = \"*\",\n"
            + "    userGroup = \"*\",\n"
            + "    version = \"1.0.0\",\n"
            + "    order = 100,\n"
            + "    weight = 100,\n"
            + "    traffic = 100,\n"
            + "    enabled = true,\n"
            + "    condition = \"\",\n"
            + "    tags = {},\n"
            + "    startTime = \"\",\n"
            + "    endTime = \"\",\n"
            + "    async = false,\n"
            + "    timeout = 0\n"
            + ")\n"
            + "public class %s implements %s {\n\n"
            + "    @Override\n"
            + "    public String execute(BizContext<?> context) {\n"
            + "        // 实现扩展点逻辑\n"
            + "        return \"Hello from %s!\";\n"
            + "    }\n\n"
            + "}",
        packageName,
        implementationName,
        implementationName,
        implementationName,
        implementationName,
        interfaceName,
        implementationName);
  }
}
