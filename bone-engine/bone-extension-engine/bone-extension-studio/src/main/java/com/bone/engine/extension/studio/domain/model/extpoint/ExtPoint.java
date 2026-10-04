package com.bone.engine.extension.studio.domain.model.extpoint;

import com.bone.core.domain.entity.Entity;

public class ExtPoint extends Entity<Long> {

  private String name;
  private String description;
  private String interfaceName;
  private String domain;
  private String category;
  private boolean enabled;

  /** 乐观锁版本（对应 exts_extension_point.version） */
  private Integer version = 0;

  /**
   * 工厂方法：新建一个启用的扩展点（DDD E-6.4 / R2 反贫血）。
   *
   * <p><b>为何必须有工厂</b>：应用层直接 {@code new ExtPoint()} + setter 会被 ArchUnit 规则 {@code
   * application_services_no_domain_rules} 判为违规（E-6.4 反贫血）——状态变更须走领域行为方法。 本工厂是「创建一个新聚合」的领域行为，故合法。
   *
   * <p><b>为何不暴露 setter</b>：本工厂创建即终态（运行时自动登记的扩展点无后续编辑流程）； 需要修改的既有路径请新增带语义的方法（如 {@code
   * disable()}），而不是暴露裸 setter。
   *
   * @param name 扩展点名称（通常是接口名的simple name）
   * @param interfaceName 扩展点接口全限定名（唯一标识）
   * @param description 描述
   * @return 已启用（{@code enabled=true}）的扩展点实例
   */
  public static ExtPoint create(String name, String interfaceName, String description) {
    ExtPoint extPoint = new ExtPoint();
    extPoint.name = name;
    extPoint.interfaceName = interfaceName;
    extPoint.description = description;
    extPoint.enabled = true;
    return extPoint;
  }

  // Getter methods
  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getInterfaceName() {
    return interfaceName;
  }

  public String getDomain() {
    return domain;
  }

  public String getCategory() {
    return category;
  }

  public boolean isEnabled() {
    return enabled;
  }

  // Setter methods
  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setInterfaceName(String interfaceName) {
    this.interfaceName = interfaceName;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
