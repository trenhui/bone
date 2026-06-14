package com.bone.engine.extension.studio.application.service;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import java.util.Map;
import org.springframework.util.StringUtils;

/** PATCH 部分更新：仅合并请求体中出现的字段。 */
public final class StudioPatchSupport {

  private StudioPatchSupport() {}

  public static void applyToExtPoint(ExtPoint target, Map<String, Object> patch) {
    if (patch == null || patch.isEmpty()) {
      return;
    }
    if (patch.containsKey("name")) {
      target.setName(requiredString(patch.get("name"), "name"));
    }
    if (patch.containsKey("description")) {
      target.setDescription(asString(patch.get("description")));
    }
    if (patch.containsKey("interfaceName")) {
      target.setInterfaceName(requiredString(patch.get("interfaceName"), "interfaceName"));
    }
    if (patch.containsKey("domain")) {
      target.setDomain(asString(patch.get("domain")));
    }
    if (patch.containsKey("category")) {
      target.setCategory(asString(patch.get("category")));
    }
    if (patch.containsKey("enabled")) {
      target.setEnabled(asBoolean(patch.get("enabled")));
    }
  }

  public static void applyToExtension(Extension target, Map<String, Object> patch) {
    if (patch == null || patch.isEmpty()) {
      return;
    }
    if (patch.containsKey("extPointId")) {
      target.setExtPointId(asLong(patch.get("extPointId"), "extPointId"));
    }
    if (patch.containsKey("name")) {
      target.setName(requiredString(patch.get("name"), "name"));
    }
    if (patch.containsKey("description")) {
      target.setDescription(asString(patch.get("description")));
    }
    if (patch.containsKey("className")) {
      target.setClassName(requiredString(patch.get("className"), "className"));
    }
    if (patch.containsKey("tenantCode")) {
      target.setTenantCode(asString(patch.get("tenantCode")));
    }
    if (patch.containsKey("bizCode")) {
      target.setBizCode(asString(patch.get("bizCode")));
    }
    if (patch.containsKey("useCase")) {
      target.setUseCase(asString(patch.get("useCase")));
    }
    if (patch.containsKey("scenario")) {
      target.setScenario(asString(patch.get("scenario")));
    }
    if (patch.containsKey("userGroup")) {
      target.setUserGroup(asString(patch.get("userGroup")));
    }
    if (patch.containsKey("priority")) {
      target.setPriority(asInt(patch.get("priority"), "priority"));
    }
    if (patch.containsKey("config")) {
      Object cfg = patch.get("config");
      target.setConfig(cfg == null ? null : String.valueOf(cfg));
    }
    if (patch.containsKey("enabled")) {
      target.setEnabled(asBoolean(patch.get("enabled")));
    }
  }

  private static String requiredString(Object value, String field) {
    String s = asString(value);
    if (!StringUtils.hasText(s)) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    return s;
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static boolean asBoolean(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static int asInt(Object value, String field) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(field + " 须为整数");
    }
  }

  private static long asLong(Object value, String field) {
    if (value instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(field + " 须为长整型");
    }
  }
}
