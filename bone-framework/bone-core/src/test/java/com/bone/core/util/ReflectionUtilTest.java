package com.bone.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.util.ReflectionUtil.CacheStats;
import com.bone.core.util.ReflectionUtil.ReflectionException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ReflectionUtil} 测试：覆盖方法调用、字段访问、构造器实例化、注解扫描与缓存管理。
 *
 * <p>该工具类被 SDK 与引擎层广泛使用（仓储代理、扩展点发现等），其行为一旦回归影响面大，故在此固化。
 */
class ReflectionUtilTest {

  @AfterEach
  void clearCaches() {
    // 缓存是静态的，必须逐测试清理，避免用例间相互污染
    ReflectionUtil.clearAllCaches();
  }

  // ===== 方法调用 =====

  @Test
  void invokeMethod_noArguments() {
    Sample target = new Sample();

    assertThat(ReflectionUtil.<String>invokeMethod(target, "greet")).isEqualTo("hello");
  }

  @Test
  void invokeMethod_withArguments() {
    Sample target = new Sample();

    assertThat(ReflectionUtil.<Integer>invokeMethod(target, "add", 2, 3)).isEqualTo(5);
    assertThat(ReflectionUtil.<String>invokeMethod(target, "echo", "x")).isEqualTo("x");
  }

  /** 装箱兼容：目标方法形参为基本类型 int，实参传入包装类型 Integer 也必须匹配成功。 */
  @Test
  void invokeMethod_boxingCompatibleArguments() {
    Sample target = new Sample();

    assertThat(
            ReflectionUtil.<Integer>invokeMethod(
                target, "add", Integer.valueOf(2), Integer.valueOf(3)))
        .isEqualTo(5);
  }

  @Test
  void invokeMethod_returnsNullWhenMethodReturnsVoidOrNull() {
    Sample target = new Sample();

    // 显式声明为 Object：invokeMethod 的泛型 T 若交由 assertThat 重载推断会产生歧义
    Object result = ReflectionUtil.invokeMethod(target, "nullReturning");
    assertThat(result).isNull();
  }

  /** 方法不存在：抛出统一的 ReflectionException，便于上层统一处理。 */
  @Test
  void invokeMethod_missingMethodThrows() {
    Sample target = new Sample();

    assertThatThrownBy(() -> ReflectionUtil.invokeMethod(target, "noSuchMethod"))
        .isInstanceOf(ReflectionException.class);
  }

  /**
   * 现状固化（含已知不一致提示）：目标方法抛出的 RuntimeException 最终被包装为 {@link ReflectionException}， 原始异常保留在 cause 中。
   *
   * <p><b>⚠ 已知不一致</b>：{@code invokeWithReflection} 的设计意图是「原样传播原始异常以保持调用栈完整性」， 但 {@code
   * invokeMethod} 外层的 {@code catch (Exception e)} 会再次包装，导致调用方须从 {@code getCause()}
   * 取原始异常。若需异常透明传播，应调整 {@code invokeMethod} 的 catch 分支（优先放行 RuntimeException）。
   */
  @Test
  void invokeMethod_wrapsTargetExceptionInReflectionException() {
    Sample target = new Sample();

    assertThatThrownBy(() -> ReflectionUtil.invokeMethod(target, "boom"))
        .isInstanceOf(ReflectionException.class)
        .hasMessageContaining("boom")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void invokeMethod_nullTargetRejected() {
    assertThatThrownBy(() -> ReflectionUtil.invokeMethod(null, "greet"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invokeMethod_blankMethodNameRejected() {
    assertThatThrownBy(() -> ReflectionUtil.invokeMethod(new Sample(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invokeStaticMethod_invokesStaticMembers() {
    assertThat(ReflectionUtil.<String>invokeStaticMethod(Sample.class, "staticTag"))
        .isEqualTo("static-tag");
    assertThat(ReflectionUtil.<Integer>invokeStaticMethod(Sample.class, "staticAdd", 1, 2))
        .isEqualTo(3);
  }

  @Test
  void invokeStaticMethod_missingMethodThrows() {
    assertThatThrownBy(() -> ReflectionUtil.invokeStaticMethod(Sample.class, "nope"))
        .isInstanceOf(ReflectionException.class);
  }

  // ===== 实例化 =====

  @Test
  void newInstance_noArgConstructor() {
    Object created = ReflectionUtil.newInstance(Sample.class, null, null);

    assertThat(created).isInstanceOf(Sample.class);
  }

  @Test
  void newInstance_withArgsConstructor() {
    Object created =
        ReflectionUtil.newInstance(
            Sample.class, new Class<?>[] {String.class}, new Object[] {"named"});

    assertThat(created).isInstanceOf(Sample.class);
    assertThat(((Sample) created).getName()).isEqualTo("named");
  }

  @Test
  void newInstance_nullClassRejected() {
    assertThatThrownBy(() -> ReflectionUtil.newInstance(null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ===== 字段访问 =====

  @Test
  void setAndGetFieldValue() {
    Sample target = new Sample();

    ReflectionUtil.setFieldValue(target, "name", "updated");

    assertThat(ReflectionUtil.<String>getFieldValue(target, "name")).isEqualTo("updated");
  }

  /** 字段查找须沿继承链向上（子类实例访问父类私有字段）。 */
  @Test
  void fieldLookupWalksUpInheritanceChain() {
    Child target = new Child();

    ReflectionUtil.setFieldValue(target, "name", "from-parent");
    ReflectionUtil.setFieldValue(target, "childField", "own");

    assertThat(ReflectionUtil.<String>getFieldValue(target, "name")).isEqualTo("from-parent");
    assertThat(ReflectionUtil.<String>getFieldValue(target, "childField")).isEqualTo("own");
  }

  @Test
  void setAndGetStaticFieldValue() {
    try {
      ReflectionUtil.setStaticFieldValue(Sample.class, "STATIC_TAG", "changed");

      assertThat(ReflectionUtil.<String>getStaticFieldValue(Sample.class, "STATIC_TAG"))
          .isEqualTo("changed");
    } finally {
      ReflectionUtil.setStaticFieldValue(Sample.class, "STATIC_TAG", "static-init");
    }
  }

  @Test
  void missingFieldThrows() {
    Sample target = new Sample();

    assertThatThrownBy(() -> ReflectionUtil.getFieldValue(target, "noSuchField"))
        .isInstanceOf(ReflectionException.class);
  }

  // ===== 注解扫描 =====

  @Test
  void getInterfaceByAnnotation_findsAnnotatedInterface() {
    assertThat(ReflectionUtil.getInterfaceByAnnotation(Impl.class, Marker.class))
        .isEqualTo(Marked.class);
  }

  /** 沿继承链向上找：子类自身无注解接口，但父类实现有。 */
  @Test
  void getInterfaceByAnnotation_searchesSuperclass() {
    assertThat(ReflectionUtil.getInterfaceByAnnotation(SubImpl.class, Marker.class))
        .isEqualTo(Marked.class);
  }

  @Test
  void getInterfaceByAnnotation_returnsNullWhenAbsent() {
    assertThat(ReflectionUtil.getInterfaceByAnnotation(String.class, Marker.class)).isNull();
  }

  @Test
  void getInterfaceByAnnotation_nullInputsReturnNull() {
    assertThat(ReflectionUtil.getInterfaceByAnnotation(null, Marker.class)).isNull();
    assertThat(ReflectionUtil.getInterfaceByAnnotation(Impl.class, null)).isNull();
  }

  // ===== 缓存 =====

  @Test
  void cacheStatsReflectsUsageAndCanBeCleared() {
    Sample target = new Sample();
    ReflectionUtil.invokeMethod(target, "greet");
    ReflectionUtil.getFieldValue(target, "name");
    ReflectionUtil.newInstance(Sample.class, null, null);

    CacheStats stats = ReflectionUtil.getCacheStats();

    // 注意：methodHandle 缓存是「可选性能路径」——unreflect 失败时 compute 返回 null（缓存项被移除），
    // 调用自动回退到反射，属正常行为，因此不强断言其大小 > 0。
    assertThat(stats.fieldCacheSize() > 0).isTrue();
    assertThat(stats.constructorCacheSize() > 0).isTrue();
    assertThat(stats.toString()).contains("MethodCache:");

    ReflectionUtil.clearAllCaches();

    CacheStats cleared = ReflectionUtil.getCacheStats();
    assertThat(cleared.methodCacheSize() == 0).isTrue();
    assertThat(cleared.fieldCacheSize() == 0).isTrue();
    assertThat(cleared.methodHandleCacheSize() == 0).isTrue();
    assertThat(cleared.constructorCacheSize() == 0).isTrue();
  }

  /** 工具类禁止实例化：私有构造必须抛 UnsupportedOperationException。 */
  @Test
  void utilityClassCannotBeInstantiated() throws Exception {
    Constructor<ReflectionUtil> ctor = ReflectionUtil.class.getDeclaredConstructor();
    ctor.setAccessible(true);

    InvocationTargetException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            InvocationTargetException.class, ctor::newInstance);

    assertThat(ex.getCause()).isInstanceOf(UnsupportedOperationException.class);
  }

  // ===== 测试夹具 =====

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface Marker {}

  @Marker
  interface Marked {}

  static class Impl implements Marked {}

  static class SubImpl extends Impl {}

  public static class Sample {
    private String name = "init";
    private int count;
    public static String STATIC_TAG = "static-init";

    public Sample() {}

    public Sample(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public int getCount() {
      return count;
    }

    public String greet() {
      return "hello";
    }

    public int add(int a, int b) {
      return a + b;
    }

    public String echo(String s) {
      return s;
    }

    public String nullReturning() {
      return null;
    }

    public void boom() {
      throw new IllegalStateException("boom");
    }

    public static String staticTag() {
      return "static-tag";
    }

    public static int staticAdd(int a, int b) {
      return a + b;
    }
  }

  static class Child extends Sample {
    private String childField;
  }
}
