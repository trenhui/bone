package com.bone.workflow.starter.org;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 组织目录接入配置（{@code bone.workflow.org-directory.*}）。
 *
 * <p><b>本类必须带 {@code @ConfigurationProperties}</b>：它由 {@code
 * WorkflowBoneIntegrationAutoConfiguration} 上的 {@code @EnableConfigurationProperties} 注册。少这个注解时
 * Spring 不会降级或跳过，而是在配置类解析阶段直接抛 {@code IllegalStateException: No ConfigurationProperties annotation
 * found on '...'}——连带整个 {@code @AutoConfiguration} 装配失败，表现为该模块<b>所有</b> Spring 上下文型测试集体报错（实测一次 113
 * 个），而纯 POJO 单测仍全绿，极易被误读成「改动无害」。
 *
 * <p><b>默认关闭（{@code enabled=false}）</b>，这是刻意的：
 *
 * <ul>
 *   <li>引擎在缺组织数据源时的既有行为是<b>挂起</b>（fail-closed），已有定义里的 {@code LINE_MANAGER} / {@code DEPT_HEAD}
 *       节点因此从不可用。默认关闭让升级本模块<b>不改变任何现有部署的行为</b>——不会因为 多了一个 Bean，让原本「按设计挂起」的单据突然路由到某个真实的人。
 *   <li>开启需要一个可用的 IAM 地址与服务账号；配错了应该表现为「开关没开」，而不是「审批悄悄走错人」。
 * </ul>
 *
 * <p>默认值指向本地 127.0.0.1（不用 localhost：macOS 把 localhost 解析成 ::1，而 Spring 默认绑 IPv4，会出现
 * 「服务起着却连不上」的假故障——bone-local-e2e 已记录过同款陷阱）。
 */
@ConfigurationProperties(prefix = "bone.workflow.org-directory")
public class OrgDirectoryProperties {

  /** 是否接入 IAM 组织目录。关闭时不存在 {@code OrgHierarchyProvider} bean，引擎保持 fail-closed 挂起。 */
  private boolean enabled = false;

  /** IAM 地址（取组织层级与服务间 token）。 */
  private String iamBaseUrl = "http://127.0.0.1:8081";

  /** 服务间登录账号。 */
  private String serviceUsername = "admin";

  /** 服务间登录密码；非开发环境必须外部化。 */
  private String servicePassword = "123456";

  /**
   * 单个账户组织位置的缓存秒数。
   *
   * <p>取值权衡：审批是低频关键路径，每次多一次 HTTP 完全可以接受，所以这里<b>故意取小值</b>——刚给某人设了主管就
   * 发起单据，不应该因为缓存而解析到旧主管（那会让单据挂到一个不该审的人头上）。0 表示不缓存。
   */
  private long cacheTtlSeconds = 30;

  /** 向上 N 级的最深层数上限：既防脏数据成环导致死循环，也防把请求打成链式放大。 */
  private int maxLevels = 10;

  private int connectTimeoutMillis = 2000;

  private int readTimeoutMillis = 4000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getIamBaseUrl() {
    return iamBaseUrl;
  }

  public void setIamBaseUrl(String iamBaseUrl) {
    this.iamBaseUrl = iamBaseUrl;
  }

  public String getServiceUsername() {
    return serviceUsername;
  }

  public void setServiceUsername(String serviceUsername) {
    this.serviceUsername = serviceUsername;
  }

  public String getServicePassword() {
    return servicePassword;
  }

  public void setServicePassword(String servicePassword) {
    this.servicePassword = servicePassword;
  }

  public long getCacheTtlSeconds() {
    return cacheTtlSeconds;
  }

  public void setCacheTtlSeconds(long cacheTtlSeconds) {
    this.cacheTtlSeconds = cacheTtlSeconds;
  }

  public int getMaxLevels() {
    return maxLevels;
  }

  public void setMaxLevels(int maxLevels) {
    this.maxLevels = maxLevels;
  }

  public int getConnectTimeoutMillis() {
    return connectTimeoutMillis;
  }

  public void setConnectTimeoutMillis(int connectTimeoutMillis) {
    this.connectTimeoutMillis = connectTimeoutMillis;
  }

  public int getReadTimeoutMillis() {
    return readTimeoutMillis;
  }

  public void setReadTimeoutMillis(int readTimeoutMillis) {
    this.readTimeoutMillis = readTimeoutMillis;
  }
}
