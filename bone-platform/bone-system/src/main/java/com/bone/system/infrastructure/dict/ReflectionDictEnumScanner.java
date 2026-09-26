package com.bone.system.infrastructure.dict;

import com.bone.system.domain.service.DictEnumScanner;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 枚举扫描的反射实现。
 *
 * <p><b>为什么限制 {@code com.bone.} 前缀</b>：{@code enumClass} 是管理员在页面上填的字段，直接 {@code Class.forName}
 * 等于给了「加载任意类」的能力——类加载本身就会触发静态初始化，是实打实的攻击面。 限定本平台包前缀后，最坏情况也只是加载平台自己的枚举。
 */
@Component
public class ReflectionDictEnumScanner implements DictEnumScanner {

  private static final String ALLOWED_PREFIX = "com.bone.";

  @Override
  public List<DictEnumConstant> scan(String enumClassName) {
    if (enumClassName == null || enumClassName.isBlank()) {
      throw new IllegalArgumentException("枚举类名不能为空");
    }
    String fqn = enumClassName.trim();
    if (!fqn.startsWith(ALLOWED_PREFIX)) {
      throw new IllegalArgumentException("仅允许绑定 com.bone.* 下的枚举：" + fqn);
    }
    Class<?> clazz;
    try {
      clazz = Class.forName(fqn);
    } catch (ClassNotFoundException ex) {
      throw new IllegalArgumentException("枚举类不存在：" + fqn, ex);
    }
    if (!clazz.isEnum()) {
      throw new IllegalArgumentException("不是枚举类型：" + fqn);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    Class<? extends Enum> enumClass = (Class<? extends Enum>) clazz;
    return Arrays.stream(enumClass.getEnumConstants())
        .map(e -> new DictEnumConstant(e.name(), e.ordinal()))
        .toList();
  }
}
