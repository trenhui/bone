package com.bone.architecture;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R8「每个聚合根一个无容器纯单测」的机器判定守卫（真源：主规范 R8 / E-8 反贫血主判据）。
 *
 * <p><b>为何不是一条普通 ArchUnit 规则</b>：ArchUnit 的 {@code @AnalyzeClasses} 默认带 {@code
 * ImportOption.DoNotIncludeTests}，各模块 {@code ArchitectureTest} 因此看不到测试类，无法判定「某聚合是否存在 对应的纯单测」。本守卫改用
 * {@code ClassFileImporter} 分别导入<strong>主代码</strong>与<strong>测试代码 </strong>两个类集合后交叉比对，故以独立测试类承载，而非
 * {@code BoneDddArchRules} 中的 {@code ArchRule}。
 *
 * <p><b>判定口径</b>（五条全满足才算通过）：
 *
 * <ol>
 *   <li>存在同名测试类：{@code Order} → {@code OrderTest}；
 *   <li>该测试类至少有 1 个 {@code @Test} 方法（堵住「空测试类绕过存在性检查」的漏洞）；
 *   <li>该测试类不带容器注解（{@code @SpringBootTest} / {@code @DataJpaTest} /
 *       {@code @ExtendWith(SpringExtension.class)} 等），即必须能在无 Spring/DB/MQ 环境下运行；
 *   <li><b>v4.6 新增</b>：该测试类<strong>调用了聚合的行为方法</strong>（非 getter 的领域方法或静态工厂 方法）——只调用 getter
 *       的测试无法证明任何不变量生效；
 *   <li><b>v4.6 新增</b>：该测试类<strong>包含断言或异常期望</strong>（JUnit / AssertJ / Hamcrest 的 {@code assert*}
 *       系列，或 {@code assertThrows}）——堵住 {@code assertEquals(1, 1)} 式空测试。
 * </ol>
 *
 * <p><b>为何补 ④⑤ 两条</b>：①②③ 是<strong>结构判据</strong>，只能证明「存在一个形似纯单测的类」， 无法证明「不变量真的被验证过」。一个只有 {@code
 * assertEquals(1, 1)} 的测试类可以全部通过 ①②③， 反贫血主判据形同虚设。④⑤ 把判据从「结构」推进到「行为」——这正是 E-8「弱约束代码形式、强约束行为」
 * 落在门禁上的应有形态。
 *
 * <p><b>使用方式</b>（各应用模块新增一个测试类即可）：
 *
 * <pre>{@code
 * class AggregatePureUnitTestCoverageTest {
 *   @Test
 *   void allAggregateRootsHavePureUnitTest() {
 *     AggregatePureUnitTestGuard.verify("com.bone.blueprint");
 *   }
 * }
 * }</pre>
 *
 * <p>仅统计 {@code ..domain..} 包下的<strong>具体</strong>聚合根：基础设施中的持久化记录（如 {@code OrderOutboxRecord}）虽继承
 * {@code AggregateRoot}，但属技术对象而非业务聚合，不计入门禁。
 */
public final class AggregatePureUnitTestGuard {

  /** 聚合根基类 FQN。 */
  private static final Set<String> AGGREGATE_ROOT_BASES =
      Set.of(
          "com.bone.core.domain.AggregateRoot",
          "com.bone.core.domain.TenantAggregateRoot",
          "com.bone.core.domain.AuditableAggregateRoot");

  /** 判据 ④ 用：明确不算「行为方法」的 Object 方法。 */
  private static final Set<String> NON_BEHAVIOUR_METHODS = Set.of("equals", "hashCode", "toString");

  /** 判据 ④ 用：访问器方法名前缀——以这些前缀开头的方法视为读取而非行为。 */
  private static final List<String> ACCESSOR_PREFIXES = List.of("get", "is", "has", "to", "of");

  private static final String TEST_ANNOTATION = "org.junit.jupiter.api.Test";
  private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
  private static final String SPRING_EXTENSION =
      "org.springframework.test.context.junit.jupiter.SpringExtension";

  /** 会启动容器或连接外部资源的测试注解：出现即不属于「无容器纯单测」。 */
  private static final Set<String> CONTAINER_ANNOTATIONS =
      Set.of(
          "org.springframework.boot.test.context.SpringBootTest",
          "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
          "org.springframework.boot.test.autoconfigure.jdbc.JdbcTest",
          "org.mybatis.spring.boot.test.autoconfigure.MybatisTest",
          "org.springframework.test.context.junit.jupiter.SpringJUnitConfig",
          "org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig",
          "io.quarkus.test.junit.QuarkusTest",
          "io.micronaut.test.extensions.junit5.annotation.MicronautTest",
          "org.testcontainers.junit.jupiter.Testcontainers");

  private AggregatePureUnitTestGuard() {}

  /**
   * 校验指定模块内每个具体聚合根都有无容器纯单测（严格模式，无待补清单）。
   *
   * <p>参考样板 {@code bone-blueprint} 与新建模块使用本方法；存量模块用 {@link #verifyAllowingPending(Set, String...)}
   * 登记缺口后接入。
   *
   * @param modulePackages 模块根包（可多个）
   * @throws AssertionError 存在未覆盖的聚合根时抛出，消息逐条列出违规项
   */
  public static void verify(String... modulePackages) {
    verifyAllowingPending(Set.of(), modulePackages);
  }

  /**
   * 存量模块接入版：显式登记「待补纯单测」的聚合，未登记的仍<strong>严格</strong>判定。
   *
   * <p><b>为何需要这个方法</b>：R8 没有 freeze 基线，直接 {@link #verify} 会让存量模块立即大面积失败。 实践中只会有两种结果——团队绕过门禁，或 CI
   * 长期红灯而无人处理——两者都等于没有门禁。本方法提供 <strong>可审计的过渡形态</strong>：
   *
   * <ol>
   *   <li>清单内的聚合暂不判失败，但<strong>新增聚合一旦不在清单内即失败</strong>——门禁立即生效，守住增量；
   *   <li>清单内的聚合若已存在合规纯单测 → <strong>报错要求从清单移除</strong>，防止清单退化为永久豁免；
   *   <li>清单为空时本方法等价于 {@link #verify}。
   * </ol>
   *
   * <p>清单以<strong>聚合简名</strong>写在测试代码中，每补一个聚合就从清单删掉一行——清单的收缩过程 本身就是可审计的进度记录，比写在 README 里的台账更难造假。
   *
   * @param pendingAggregates 待补纯单测的聚合简名集合
   * @param modulePackages 模块根包（可多个）
   * @throws AssertionError 存在未登记的覆盖缺口，或待补清单中存在已覆盖的聚合时抛出
   */
  public static void verifyAllowingPending(
      Set<String> pendingAggregates, String... modulePackages) {
    if (modulePackages == null || modulePackages.length == 0) {
      throw new IllegalArgumentException(
          "AggregatePureUnitTestGuard.verify 至少需要一个模块根包，例如 verify(\"com.bone.iam\")");
    }
    Set<String> pending = pendingAggregates == null ? Set.of() : pendingAggregates;

    JavaClasses production =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(modulePackages);
    JavaClasses tests =
        new ClassFileImporter()
            .withImportOption(new ImportOption.OnlyIncludeTests())
            .importPackages(modulePackages);

    List<String> violations = new ArrayList<>();
    List<String> stalePending = new ArrayList<>();

    for (JavaClass aggregate : concreteAggregateRoots(production)) {
      String fqn = aggregate.getPackageName() + "." + aggregate.getSimpleName();
      List<String> problems = collectProblems(aggregate, tests);

      if (problems.isEmpty()) {
        if (pending.contains(aggregate.getSimpleName())) {
          stalePending.add(aggregate.getSimpleName());
        }
        continue;
      }
      if (pending.contains(aggregate.getSimpleName())) {
        continue;
      }
      for (String problem : problems) {
        violations.add(fqn + "：" + problem);
      }
    }

    if (violations.isEmpty() && stalePending.isEmpty()) {
      return;
    }

    StringBuilder message = new StringBuilder("R8 聚合纯单测门禁未通过：");
    if (!violations.isEmpty()) {
      message
          .append(System.lineSeparator())
          .append("【未登记的覆盖缺口 ")
          .append(violations.size())
          .append(" 项 — 须补纯单测，或按规范登记到待补清单】");
      for (String violation : violations) {
        message.append(System.lineSeparator()).append("  - ").append(violation);
      }
    }
    if (!stalePending.isEmpty()) {
      message
          .append(System.lineSeparator())
          .append("【待补清单中存在已覆盖的聚合 ")
          .append(stalePending.size())
          .append(" 项 — 清单只许收缩，请立即移除】");
      for (String name : stalePending) {
        message.append(System.lineSeparator()).append("  - ").append(name);
      }
    }
    throw new AssertionError(message.toString());
  }

  /** 收集单个聚合的纯单测问题；返回空列表表示完全合规。 */
  private static List<String> collectProblems(JavaClass aggregate, JavaClasses tests) {
    List<String> problems = new ArrayList<>();
    String expected = aggregate.getSimpleName() + "Test";
    JavaClass test = findBySimpleName(tests, expected);

    if (test == null) {
      problems.add("缺少同名纯单测类 " + expected);
      return problems;
    }
    if (!hasAnyTestMethod(test)) {
      problems.add(test.getSimpleName() + " 没有任何 @Test 方法（空测试类不算纯单测）");
      return problems;
    }
    Optional<String> container = findContainerAnnotation(test);
    if (container.isPresent()) {
      problems.add(test.getSimpleName() + " 带容器注解 @" + container.get() + "，不属于无容器纯单测");
    }
    // v4.6 强化判据 ④⑤：从「结构存在」推进到「行为被验证」
    if (!invokesAggregateBehaviour(test, aggregate)) {
      problems.add(test.getSimpleName() + " 未调用聚合的行为方法（仅 getter 调用无法证明任何不变量生效）");
    }
    if (!containsAssertion(test)) {
      problems.add(test.getSimpleName() + " 没有任何断言或异常期望（assertEquals(1, 1) 式空测试不算纯单测）");
    }
    return problems;
  }

  /** 主代码中 {@code ..domain..} 包下的具体聚合根，按 FQN 排序保证输出稳定。 */
  private static List<JavaClass> concreteAggregateRoots(JavaClasses production) {
    return production.stream()
        .filter(AggregatePureUnitTestGuard::isConcreteAggregateRoot)
        .sorted(Comparator.comparing(JavaClass::getName))
        .collect(Collectors.toList());
  }

  private static boolean isConcreteAggregateRoot(JavaClass clazz) {
    if (clazz.isInterface()
        || clazz.isEnum()
        || clazz.isAnnotation()
        || clazz.getModifiers().contains(JavaModifier.ABSTRACT)) {
      return false;
    }
    String pkg = clazz.getPackageName();
    if (!pkg.contains(".domain.") && !pkg.endsWith(".domain")) {
      return false;
    }
    // Outbox 记录等基础设施技术对象不计入业务聚合。即便它位于 domain 包（放置本身不规范，
    // 应迁到 infrastructure/messaging/outbox），也不要求配套纯单测——技术对象没有领域不变量可测。
    if (pkg.contains(".domain.outbox.") || pkg.endsWith(".domain.outbox")) {
      return false;
    }
    return AGGREGATE_ROOT_BASES.stream().anyMatch(clazz::isAssignableTo);
  }

  private static JavaClass findBySimpleName(JavaClasses tests, String simpleName) {
    return tests.stream()
        .filter(candidate -> simpleName.equals(candidate.getSimpleName()))
        .findFirst()
        .orElse(null);
  }

  private static boolean hasAnyTestMethod(JavaClass test) {
    return test.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(TEST_ANNOTATION));
  }

  /**
   * 判据 ④：测试类是否调用了聚合的<strong>行为方法</strong>（非 getter 的领域方法或静态工厂方法）。
   *
   * <p>只调用 getter 的测试无法证明任何不变量生效——它可能只是把字段读出来断言一遍，而这恰恰是贫血模型 的典型测试形态（先 get 出来、在外层拼逻辑、再 set 回去）。
   */
  private static boolean invokesAggregateBehaviour(JavaClass test, JavaClass aggregate) {
    for (JavaMethodCall call : test.getMethodCallsFromSelf()) {
      AccessTarget.MethodCallTarget target = call.getTarget();
      if (!target.getOwner().isAssignableTo(aggregate.getName())) {
        continue;
      }
      String name = target.getName();
      if (NON_BEHAVIOUR_METHODS.contains(name)) {
        continue;
      }
      if (ACCESSOR_PREFIXES.stream().anyMatch(name::startsWith)) {
        continue;
      }
      return true;
    }
    return false;
  }

  /**
   * 判据 ⑤：测试类是否包含断言或异常期望。
   *
   * <p>匹配方式：方法名以 {@code assert} 开头（覆盖 JUnit 5 / JUnit 4 的 {@code assertEquals}、 {@code
   * assertThrows} 与 AssertJ / Hamcrest 的 {@code assertThat}），或调用目标位于 AssertJ / Hamcrest 包（覆盖 {@code
   * assertThat(x).isEqualTo(...)} 链式调用中的非 assert 前缀方法）。
   */
  private static boolean containsAssertion(JavaClass test) {
    for (JavaMethodCall call : test.getMethodCallsFromSelf()) {
      if (isAssertionCall(call.getTarget())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAssertionCall(AccessTarget.MethodCallTarget target) {
    if (target.getName().startsWith("assert")) {
      return true;
    }
    String owner = target.getOwner().getName();
    return owner.startsWith("org.assertj.") || owner.startsWith("org.hamcrest.");
  }

  private static String simpleNameOf(String fqn) {
    return fqn.substring(fqn.lastIndexOf('.') + 1);
  }

  /** 返回首个会启动容器的注解简名；无则返回空。 */
  private static Optional<String> findContainerAnnotation(JavaClass test) {
    for (JavaAnnotation<JavaClass> annotation : test.getAnnotations()) {
      String fqn = annotation.getType().getName();
      if (CONTAINER_ANNOTATIONS.contains(fqn)) {
        return Optional.of(simpleNameOf(fqn));
      }
      if (EXTEND_WITH.equals(fqn) && isSpringExtension(annotation)) {
        return Optional.of("ExtendWith(SpringExtension.class)");
      }
    }
    return Optional.empty();
  }

  /**
   * 判定 {@code @ExtendWith} 的值是否为 {@code SpringExtension}。注解属性值的运行时形态随 ArchUnit 版本变化（数组 /
   * 单值），故统一降级为字符串匹配，读取失败时保守返回 false。
   */
  private static boolean isSpringExtension(JavaAnnotation<JavaClass> annotation) {
    try {
      Object value = annotation.get("value");
      if (value instanceof Object[] values) {
        return java.util.Arrays.stream(values)
            .anyMatch(v -> String.valueOf(v).contains(SPRING_EXTENSION));
      }
      return String.valueOf(value).contains(SPRING_EXTENSION);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
