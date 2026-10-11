package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 运行时实体级 / 动作级授权（RC-004 · Must；Runtime 数据面设计 §4.7）。
 *
 * <p><b>为什么必须有这一层</b>：{@code metadata:runtime:read|write} 是<strong>租户内全库 CRUD</strong>——
 * 任何拿到写码的人可读写本租户<strong>所有</strong>低代码实体的所有行，实体间、行间都不隔离。
 * 而「谁能动哪些数据」这条线上，实体级是<strong>唯一不能后置的一级</strong>（行级 / 字段级可延后）： 实体级缺失意味着「任何人都能改所有人的数据」，实现成本却只是一个鉴权判断。
 *
 * <p><b>权限码形状</b>（与 NocoBase {@code resource:action} 同构）：
 *
 * <pre>
 * runtime:{entityCode}:read     —— 某实体的读
 * runtime:{entityCode}:write    —— 某实体的写（含 create/update/delete）
 * runtime:{entityCode}:{action} —— 某实体的某动作（逃逸动作上线前必备）
 * runtime:*:{read|write}        —— 通配：全实体授权（等价旧粗粒度码，形状统一到实体级命名空间）
 * platform.admin                —— 平台管理员（数据修复通道，强制审计）
 * </pre>
 *
 * <p><b>为什么引入通配 {@code runtime:*}</b>：旧码 {@code metadata:runtime:write} 一旦保留为回退分支， 「无实体级权限 →
 * 403」就永远不成立（人人都有旧码），实体级授权形同虚设。改为通配后， 判定逻辑只有一条 {@code 实体级 OR 通配 OR admin}，旧语义（全库 CRUD）由通配显式承载——
 * 语义不变，但「什么时候能全库」变成一个<strong>可被收回</strong>的授权决策，而不是硬编码在注解字符串里。
 */
@Component
public class RuntimeAccessGuard {

  /** 通配：全实体读 / 写。 */
  public static final String WILDCARD_READ = "runtime:*:read";

  public static final String WILDCARD_WRITE = "runtime:*:write";

  /** 平台管理员（数据修复通道，Runtime §4.5 例外：允许直写受保护状态字段，但强制 reason + 审计）。 */
  public static final String PLATFORM_ADMIN = "platform.admin";

  /** 读：实体级读 或 通配读 或 平台管理员。 */
  public void requireRead(String entityCode) {
    Set<String> authorities = currentAuthorities();
    if (authorities.contains(PLATFORM_ADMIN)
        || authorities.contains(WILDCARD_READ)
        || authorities.contains("runtime:" + entityCode + ":read")) {
      return;
    }
    throw forbidden(entityCode, "read");
  }

  /** 写：实体级写 或 通配写 或 平台管理员。 */
  public void requireWrite(String entityCode) {
    Set<String> authorities = currentAuthorities();
    if (authorities.contains(PLATFORM_ADMIN)
        || authorities.contains(WILDCARD_WRITE)
        || authorities.contains("runtime:" + entityCode + ":write")) {
      return;
    }
    throw forbidden(entityCode, "write");
  }

  /**
   * 动作：实体级写 + （动作码在库时）动作级权限。
   *
   * <p>动作级码为 {@code null} 时只校验实体级写——未登记细码的动作不该因为「没配码」而不可用； 逃逸动作（打款 / 入库）上线前须在 {@code
   * meta_entity_action_def.permission_code} 登记细码（§4.6 门禁）。
   */
  public void requireAction(String entityCode, String actionCode, String declaredPermissionCode) {
    Set<String> authorities = currentAuthorities();
    if (authorities.contains(PLATFORM_ADMIN)) {
      return;
    }
    // 通配写 / 实体级写：覆盖该实体全部动作（含逃逸动作），必须先于声明细码判定短路——
    // 否则通配持有者会因「无具体 runtime:{entity}:{action} 细码」被误拒（§4.6 本意是让未授权者 403，
    // 而非让通配持有者 403）。dev 匿名主体带 PLATFORM_ADMIN 已 early-return，此处仅影响真实鉴权语义。
    if (authorities.contains(WILDCARD_WRITE)
        || authorities.contains("runtime:" + entityCode + ":write")) {
      return;
    }
    // 细粒度：必须持有声明码或动作级码，否则 403
    if (declaredPermissionCode != null
        && !declaredPermissionCode.isBlank()
        && (authorities.contains(declaredPermissionCode)
            || authorities.contains("runtime:" + entityCode + ":" + actionCode))) {
      return;
    }
    throw new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION,
        "无动作权限: runtime:" + entityCode + ":" + actionCode);
  }

  public boolean isPlatformAdmin() {
    return currentAuthorities().contains(PLATFORM_ADMIN);
  }

  /** 当前主体的权限码集合（Spring Security 上下文）。 */
  private static Set<String> currentAuthorities() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getAuthorities() == null) {
      return Set.of();
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  private static RuntimeRecordException forbidden(String entityCode, String op) {
    return new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_ENTITY_FORBIDDEN,
        "无实体级权限: runtime:" + entityCode + ":" + op + "（或通配 " + WILDCARD_WRITE + "）");
  }
}
