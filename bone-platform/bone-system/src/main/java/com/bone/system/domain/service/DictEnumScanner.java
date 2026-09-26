package com.bone.system.domain.service;

import java.util.List;

/**
 * 枚举扫描端口：把 Java {@code enum} 反射成字典项候选。
 *
 * <p>定义在 domain 层而不是 infrastructure：application 需要「按枚举同步字典」这一用例， 但不需要知道反射怎么做的。反射实现（{@code
 * ReflectionDictEnumScanner}）属于基础设施细节， 可替换（例如换成从编译期产物的枚举清单读取）而不动用例。
 */
public interface DictEnumScanner {

  /**
   * 扫描枚举类的全部常量。
   *
   * @param enumClassName 枚举全限定名
   * @throws RuntimeException 类不存在、不是枚举、或不允许加载（非白名单包）时抛出
   */
  List<DictEnumConstant> scan(String enumClassName);

  /** 枚举常量的最小投影：常量名 + 序号。 */
  record DictEnumConstant(String name, int ordinal) {}
}
