package com.bone.core.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

/**
 * 判定异常是否属于 Spring Security 的「已认证但权限不足」（HTTP 403 语义）。
 *
 * <p><b>为何单独成类而不放在 {@link GlobalExceptionHandler} 里</b>：本判定必须在<b>没有</b> Spring Security
 * 的模块上安全缺席（{@code bone-web} 不强依赖 security），而它的正确性又必须在<b>有</b> security 的模块上被
 * 真实断言——尤其是「子类也必须被识别」这一点，只有装 security 的模块才造得出子类实例。 合成一个类后， 无 security 的模块测「安全缺席」，有 security
 * 的模块测「子类覆盖」，两侧互不将就。
 *
 * <p><b>为何缓存成 {@code Class} 而非比对类名字符串</b>：早期实现按 {@code getName().equals(...)} 精确比对，
 * 而鉴权异常存在大量子类（{@code CsrfException}、各类 {@code AuthorizationDeniedException} 派生等），
 * 精确比对会把子类<b>全部漏判</b>，使其重新掉回 500 兜底——正是本判定要修的现象本身。 改用 {@code isAssignableFrom} 后子类一并覆盖。
 *
 * <p><b>为何不直接 {@code instanceof}</b>：编译期 {@code import} 具体异常类型会把 security 反向拉成本模块的强 依赖（属依赖变更）。
 * 按名字解析可在无 security 的模块上安全缺席。
 *
 * <p><b>失败模式的选择</b>：类型解析不到时<b>显式告警</b>而非静默失效。 若 security 在 classpath 上却解析不到 （典型场景是 Spring Security
 * 升级后包名/类名变更），判定会静默失效、鉴权失败重新变成 500 ——即修复原样 回归且无人察觉。 告警把「静默失效」改成「可观测降级」；security 整体缺席属正常形态，不打日志。
 */
public final class AccessDeniedDetector {

  // 声明顺序有意义：LOG 必须先于 ACCESS_DENIED_TYPES 完成初始化，
  // 因为类初始化按声明顺序执行，resolveAccessDeniedTypes() 会用到 LOG。
  private static final Logger LOG = LoggerFactory.getLogger(AccessDeniedDetector.class);

  /**
   * 候选鉴权失败类型：经典 {@code AccessDeniedException} 与 Security 6 新授权栈的 {@code
   * AuthorizationDeniedException}。 元素为 {@code null} 表示该类型在当前 classpath 上不存在。
   */
  private static final Class<?>[] ACCESS_DENIED_TYPES = resolveAccessDeniedTypes();

  private AccessDeniedDetector() {}

  /**
   * 遍历异常及其 cause 链，判断是否命中鉴权失败类型。
   *
   * <p><b>为何遍历 cause 链</b>：真实链路里鉴权异常常被链路中的其他异常包一层，只看顶层类型会漏判， 继而再次掉进 500 兜底——这正是要修的现象本身。
   *
   * @param ex 待判定的异常，允许为 {@code null}
   * @return 命中鉴权失败类型返回 {@code true}
   */
  public static boolean isAccessDenied(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      for (Class<?> type : ACCESS_DENIED_TYPES) {
        if (type != null && type.isAssignableFrom(t.getClass())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * 解析鉴权失败类型；解析不到时置 {@code null}，且在 security 确实在 classpath 上时告警。
   *
   * <p>只在类初始化时执行一次；解析结果不可变，故无需同步。
   */
  private static Class<?>[] resolveAccessDeniedTypes() {
    String[] names = {
      "org.springframework.security.access.AccessDeniedException",
      "org.springframework.security.authorization.AuthorizationDeniedException"
    };
    boolean securityPresent =
        ClassUtils.isPresent("org.springframework.security.core.Authentication", null);
    Class<?>[] types = new Class<?>[names.length];
    for (int i = 0; i < names.length; i++) {
      types[i] = resolveOrNull(names[i]);
      if (types[i] == null && securityPresent) {
        LOG.warn(
            "[AccessDeniedDetector] Spring Security 在 classpath 上，但鉴权类型 {} 解析失败；"
                + "该类型的 403 判定将失效，鉴权失败可能回落 500",
            names[i]);
      }
    }
    return types;
  }

  private static Class<?> resolveOrNull(String name) {
    try {
      return ClassUtils.resolveClassName(name, null);
    } catch (RuntimeException | LinkageError e) {
      // ClassNotFoundException 是 RuntimeException；NoClassDefFoundError 是 LinkageError。
      // 两者都表示「该类型在本 classpath 上不可用」，属预期情形，不向上抛以免影响启动。
      return null;
    }
  }
}
