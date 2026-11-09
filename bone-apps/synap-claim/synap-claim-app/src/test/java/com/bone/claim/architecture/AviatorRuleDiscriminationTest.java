package com.bone.claim.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link BoneDddArchRules#noAviatorDependency()} 的判别力证明（no-go 塞 + go 塞）。
 *
 * <p><b>为什么需要它</b>：规则是 advisory 形态（{@code allowEmptyShould(true)}），全仓零引用时
 * 天然恒绿。上一轮补登记只证明了「规则在本模块被执行」（surefire 里有该用例），没有证明 「规则会判红」——而一个从没见红的闸门给的绿灯不是证据。
 *
 * <p><b>为什么用动态编译</b>：直接在仓库里放一个 {@code com.googlecode.aviator} 包下的桩类， 会让「全仓零 aviator
 * 引用」这个事实变成假的，还会污染依赖面与 SBOM。这里用 JDK 自带的 {@code JavaCompiler} 在 {@code @TempDir}
 * 里现编现导，测试结束即销毁；被导入的字节码与 产品代码完全隔离（独立性：证据不复用被检查对象的假设）。
 *
 * <p><b>go 塞同等重要</b>：一个什么都拦的规则和一个什么都不拦的规则一样会很快被关掉， 所以两个用例成对存在。
 */
class AviatorRuleDiscriminationTest {

  @TempDir Path temp;

  @Test
  void no_go_rule_fires_when_a_class_depends_on_aviator() throws IOException {
    Path classes =
        compile(
            temp.resolve("violation"),
            "com.googlecode.aviator.AviatorEvaluator",
            "package com.googlecode.aviator; public class AviatorEvaluator {}",
            "probe.UsesAviator",
            "package probe; public class UsesAviator {"
                + " com.googlecode.aviator.AviatorEvaluator evaluator; }");

    JavaClasses imported = new ClassFileImporter().importPaths(classes);
    ArchRule rule = BoneDddArchRules.noAviatorDependency();

    assertThatThrownBy(() -> rule.check(imported))
        .as("引用 aviator 包的类必须被规则拦下，否则该规则恒绿")
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Aviator");
  }

  @Test
  void go_rule_stays_green_without_aviator() throws IOException {
    Path classes =
        compile(
            temp.resolve("clean"),
            "probe.CleanClass",
            "package probe; public class CleanClass { String value; }");

    JavaClasses imported = new ClassFileImporter().importPaths(classes);

    BoneDddArchRules.noAviatorDependency().check(imported);
  }

  /** 把 (类名, 源码) 对编译到 {@code out/classes}，返回可直接交给 ClassFileImporter 的目录。 源文件全部落在临时目录，不进入仓库源码树。 */
  private static Path compile(Path out, String... pairs) throws IOException {
    Path src = out.resolve("src");
    Path classes = out.resolve("classes");
    Files.createDirectories(src);
    Files.createDirectories(classes);
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("本测试须在 JDK 上运行（JRE 无 JavaCompiler）").isNotNull();

    String[] args = new String[pairs.length / 2 + 2];
    args[0] = "-d";
    args[1] = classes.toString();
    for (int i = 0; i < pairs.length; i += 2) {
      Path file = src.resolve(pairs[i].replace('.', '/') + ".java");
      Files.createDirectories(file.getParent());
      Files.writeString(file, pairs[i + 1]);
      args[2 + i / 2] = file.toString();
    }
    assertThat(compiler.run(null, null, null, args))
        .as("动态编译夹具失败（详见 surefire 输出的 javac 诊断）")
        .isZero();
    return classes;
  }
}
