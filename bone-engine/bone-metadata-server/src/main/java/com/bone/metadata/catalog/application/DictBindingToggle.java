package com.bone.metadata.catalog.application;

import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 字典绑定灰度开关（界面设计器字典打通 v1.1 §6.5「按租户灰度开放」）。
 *
 * <p><b>为什么必须有</b>：绑定解析会改写「字段选项从哪来」这条运行时主链路（L0 enumValues → L1 平台字典）。 存量租户的字段都是 L0
 * 形态，若一刀切开启，任何解析异常都会直接打到在跑的表单上；灰度开关让上线风险收敛为「逐租户可回退的开关」， 而不是「一次不可逆的全局切换」。
 *
 * <p><b>判定优先级</b>（高 → 低）：
 *
 * <ol>
 *   <li>运行时覆盖（{@link #override}，运维即时开/关单租户）
 *   <li>租户白名单（{@code bone.metadata.dict-binding.enabled-tenants}，持久灰度名单；{@code *} = 全部）
 *   <li>全局默认（{@code bone.metadata.dict-binding.enabled}，生产默认为 {@code false}）
 * </ol>
 *
 * <p><b>关闭时语义 = 逐字节回到 L0</b>：不是「部分功能降级」，而是绑定声明被整体视为不存在—— 解析不发起、物化不覆写、写入口拒绝新声明。字段里已有的 enum_values
 * 快照即 L0 口径，运行时读到的与开启前完全一致。
 *
 * <p><b>诚实边界</b>：运行时覆盖是<b>进程内</b>的（无 DDL、不跨实例），重启即失效；它不是配置中心的替代品， 只用于「临时拉闸 / 单租户验证」。需要持久的灰度名单请写配置。
 */
@Component
public class DictBindingToggle {

  /** 判定来源：运行时覆盖（最高优先级）。 */
  public static final String SOURCE_RUNTIME = "runtime";

  /** 判定来源：租户白名单配置。 */
  public static final String SOURCE_TENANT_CONFIG = "tenant-config";

  /** 判定来源：全局默认配置。 */
  public static final String SOURCE_GLOBAL_DEFAULT = "global-default";

  private static final String WILDCARD = "*";

  private final boolean globalEnabled;
  private final Set<Long> enabledTenants;
  private final boolean allTenants;
  private final Map<Long, Boolean> runtimeOverrides = new ConcurrentHashMap<>();
  private final TenantProvider tenantProvider;

  public DictBindingToggle(
      @Value("${bone.metadata.dict-binding.enabled:false}") boolean globalEnabled,
      @Value("${bone.metadata.dict-binding.enabled-tenants:}") String enabledTenants,
      TenantProvider tenantProvider) {
    this.globalEnabled = globalEnabled;
    this.tenantProvider = tenantProvider;
    Set<Long> parsed = new LinkedHashSet<>();
    boolean all = false;
    if (enabledTenants != null) {
      for (String token : enabledTenants.split(",")) {
        String t = token.trim();
        if (t.isEmpty()) {
          continue;
        }
        if (WILDCARD.equals(t)) {
          all = true;
          continue;
        }
        try {
          parsed.add(Long.parseLong(t));
        } catch (NumberFormatException e) {
          // 配置项写错时不能静默：灰度名单写错 = 该租户静默未开放，排查成本极高
          throw new IllegalStateException(
              "bone.metadata.dict-binding.enabled-tenants 含非法租户 ID: " + t, e);
        }
      }
    }
    this.enabledTenants = Collections.unmodifiableSet(parsed);
    this.allTenants = all;
  }

  /** 指定租户是否开放字典绑定。 */
  public boolean isEnabled(long tenantId) {
    Boolean override = runtimeOverrides.get(tenantId);
    if (override != null) {
      return override;
    }
    if (allTenants || enabledTenants.contains(tenantId)) {
      return true;
    }
    return globalEnabled;
  }

  /** 当前请求租户是否开放字典绑定（读路径 / 写入口守卫共用同一判据）。 */
  public boolean isEnabledForCurrentTenant() {
    return isEnabled(tenantProvider.currentTenantId());
  }

  /** 当前租户的判定结果与来源（供管理端点/探针断言「开关到底由谁决定」）。 */
  public ToggleState currentState() {
    long tenantId = tenantProvider.currentTenantId();
    return new ToggleState(tenantId, isEnabled(tenantId), sourceOf(tenantId));
  }

  /** 运行时覆盖当前租户（进程内，重启失效；用于临时拉闸与单租户验证）。 */
  public ToggleState overrideCurrentTenant(boolean enabled) {
    long tenantId = tenantProvider.currentTenantId();
    runtimeOverrides.put(tenantId, enabled);
    return new ToggleState(tenantId, isEnabled(tenantId), SOURCE_RUNTIME);
  }

  /** 撤销运行时覆盖，回落到配置判定。 */
  public ToggleState clearCurrentTenantOverride() {
    long tenantId = tenantProvider.currentTenantId();
    runtimeOverrides.remove(tenantId);
    return new ToggleState(tenantId, isEnabled(tenantId), sourceOf(tenantId));
  }

  private String sourceOf(long tenantId) {
    if (runtimeOverrides.containsKey(tenantId)) {
      return SOURCE_RUNTIME;
    }
    if (allTenants || enabledTenants.contains(tenantId)) {
      return SOURCE_TENANT_CONFIG;
    }
    return SOURCE_GLOBAL_DEFAULT;
  }

  /**
   * 开关判定结果。
   *
   * @param tenantId 被判定的租户
   * @param enabled 该租户是否开放字典绑定
   * @param source 判定来源（runtime / tenant-config / global-default）
   */
  public record ToggleState(long tenantId, boolean enabled, String source) {}
}
