package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
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

  /** 当前主体是否为平台管理员（行级 ACL 豁免判据，Runtime §4.7.1）。 */
  public boolean isPlatformAdmin() {
    return currentAuthorities().contains(PLATFORM_ADMIN);
  }

  /**
   * 读判定（布尔版）。与 {@link #requireRead} 同一判据——<strong>判据只此一份</strong>， 布尔版供
   * {@code @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")} 注解层 fail-fast 与 Describe
   * 展示裁剪复用， 避免「注解放行 / Guard 拒绝」两套口径互相漂移。
   */
  public boolean canRead(String entityCode) {
    Set<String> authorities = currentAuthorities();
    return authorities.contains(PLATFORM_ADMIN)
        || authorities.contains(WILDCARD_READ)
        || authorities.contains("runtime:" + entityCode + ":read");
  }

  /** 写判定（布尔版）。与 {@link #requireWrite} 同一判据，供注解层与展示层复用。 */
  public boolean canWrite(String entityCode) {
    Set<String> authorities = currentAuthorities();
    return authorities.contains(PLATFORM_ADMIN)
        || authorities.contains(WILDCARD_WRITE)
        || authorities.contains("runtime:" + entityCode + ":write");
  }

  /**
   * 动作判定（布尔版）。与 {@link #requireAction} <strong>逐行同一判据</strong>： {@code
   * RuntimeActionDescribeSupport} 按本方法裁剪「可见动作」，执行门禁走 {@link #requireAction}—— 两者共用一份判据，
   * 从结构上杜绝「按钮可见但点击 403」的漂移（2026-10-13 实测缺陷：Describe 曾各自复制一份判定，漏掉逃逸动作必须细码的约束）。
   */
  public boolean canInvoke(RuntimeActionDef def) {
    Set<String> authorities = currentAuthorities();
    if (authorities.contains(PLATFORM_ADMIN)) {
      return true;
    }
    String entityCode = def.getEntityCode();
    String actionCode = def.getActionCode();
    boolean actionFineCode =
        authorities.contains("runtime:" + entityCode + ":" + actionCode)
            || (def.getPermissionCode() != null
                && !def.getPermissionCode().isBlank()
                && authorities.contains(def.getPermissionCode()));
    if (def.isEscape()) {
      // 逃逸动作：细码以外一律拒绝（实体级写 / 通配写不含「放款/入库」授权，§4.6 门禁）
      return actionFineCode;
    }
    // 非逃逸动作：实体级写 OR 通配写 OR 动作级细码
    return authorities.contains("runtime:" + entityCode + ":write")
        || authorities.contains(WILDCARD_WRITE)
        || actionFineCode;
  }

  /** 读：实体级读 或 通配读 或 平台管理员。 */
  public void requireRead(String entityCode) {
    if (!canRead(entityCode)) {
      throw forbidden(entityCode, "read");
    }
  }

  /** 写：实体级写 或 通配写 或 平台管理员。 */
  public void requireWrite(String entityCode) {
    if (!canWrite(entityCode)) {
      throw forbidden(entityCode, "write");
    }
  }

  /**
   * 动作授权（def 感知，P0-1 修复）：<strong>逃逸动作（有副作用，如确认打款）必须命中「声明码或动作级细码」之一</strong>； 实体级写 / 通配写只覆盖非逃逸动作。
   *
   * <p><b>为什么实体级写不能覆盖逃逸动作</b>：{@code runtime:expense_reimbursement:write} 管的是「这笔报销单的数据」， 而 {@code
   * runtime:expense_reimbursement:confirmPayout} 管的是「允许把钱从公司账户放出去」——后者是资金授权面， 前者不隐含后者。 0034 迁移特意不给
   * demo 租户管理员发 {@code confirmPayout} 细码，就是为了让「按动作收回授权」 有真实的不动点；若实体级写可以短路掉这层判定， 0034
   * 的整张角色-权限表退化为摆设，§4.6 门禁空转。
   *
   * <p>非逃逸动作（状态流转如提交 / 审批）保持「实体级写 OR 通配 OR 细码」——它们不产生资金/外部副作用， 与 CRUD 共用授权面是 {@code
   * meta_entity_action_def.permission_code} 的本意（种子 0032 即按粗码登记）。
   *
   * <p><b>2026-10-12 删除废弃重载</b>：原 {@code requireAction(String,String,String)} 零调用方（生产与测试皆无），
   * 且其「通配写/实体级写先于细码短路」的判定会让 {@code runtime:*:write} 持有者绕过逃逸动作细码—— 与本方法的 §4.6
   * 语义直接矛盾，留着就是等后人踩的语义陷阱（footgun）。控制层统一走本重载。
   */
  public void requireAction(RuntimeActionDef def) {
    if (canInvoke(def)) {
      return;
    }
    // 拒绝消息按逃逸/非逃逸分流：逃逸动作必须提示「细码」而非笼统无权限（§4.6 可审计性）
    if (def.isEscape()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION,
          "逃逸动作需动作级授权: runtime:"
              + def.getEntityCode()
              + ":"
              + def.getActionCode()
              + "（实体级 write / 通配 write 不覆盖有副作用的动作，见 Runtime 数据面设计 §4.6）");
    }
    throw new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION,
        "无动作权限: runtime:" + def.getEntityCode() + ":" + def.getActionCode());
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
