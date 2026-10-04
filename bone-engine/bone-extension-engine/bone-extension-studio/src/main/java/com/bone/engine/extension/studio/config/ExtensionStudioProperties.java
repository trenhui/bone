package com.bone.engine.extension.studio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "bone.extension.studio")
public class ExtensionStudioProperties {

  @NestedConfigurationProperty
  private final RuntimeSyncConfig runtimeSync = new RuntimeSyncConfig();

  @NestedConfigurationProperty private final ArtifactConfig artifact = new ArtifactConfig();

  @NestedConfigurationProperty private final SecurityConfig security = new SecurityConfig();

  @NestedConfigurationProperty private final LroConfig lro = new LroConfig();

  @NestedConfigurationProperty
  private final IdempotencyConfig idempotency = new IdempotencyConfig();

  @NestedConfigurationProperty private final LifecycleConfig lifecycle = new LifecycleConfig();

  public RuntimeSyncConfig getRuntimeSync() {
    return runtimeSync;
  }

  public ArtifactConfig getArtifact() {
    return artifact;
  }

  public SecurityConfig getSecurity() {
    return security;
  }

  public LroConfig getLro() {
    return lro;
  }

  public IdempotencyConfig getIdempotency() {
    return idempotency;
  }

  public LifecycleConfig getLifecycle() {
    return lifecycle;
  }

  public static class SecurityConfig {
    /** 本地联调：允许无 JWT 访问 Studio API（生产必须 false） */
    private boolean permitUnauthenticated = false;

    /** 5a G3 过渡期回退：true（默认）时新 Scope 端点同时接受旧 Scope；false（切流）后仅认新 Scope。 */
    private boolean legacyScopeFallback = true;

    public boolean isPermitUnauthenticated() {
      return permitUnauthenticated;
    }

    public void setPermitUnauthenticated(boolean permitUnauthenticated) {
      this.permitUnauthenticated = permitUnauthenticated;
    }

    public boolean isLegacyScopeFallback() {
      return legacyScopeFallback;
    }

    public void setLegacyScopeFallback(boolean legacyScopeFallback) {
      this.legacyScopeFallback = legacyScopeFallback;
    }

    /**
     * 数据面上报通道的共享密钥（对应 SDK 侧 {@code bone.extension.studio.report.token}）。
     *
     * <p>上报方是**业务进程**（{@code StudioExecutionLogReporter}）而非终端用户， 它不持有用户 JWT，因此 {@code
     * /execution-logs:ingest} 在 SecurityConfig 里只能 {@code permitAll()}——而 permitAll
     * 意味着**任何能访问该端口的人都能伪造执行日志** （className / status / errorMessage 全部取自请求体），污染运维视图与告警依据。
     *
     * <p>业界标准做法：数据面用**机器身份**（共享密钥 / mTLS / 网关 ACL）而非用户权限码认证。 本项即该机器身份，SDK 以 {@code
     * X-Reporter-Token} 头携带。
     *
     * <p><b>是否必须配置由 {@link #reporterAuthRequired} 决定，默认必须</b>：留空且要求校验时， 上报端点一律返回 503（拒绝而非放行）， 启动日志打
     * ERROR。见该字段说明。
     */
    private String reporterToken = "";

    /**
     * 未配置 {@link #reporterToken} 时，是否拒绝一切上报（失败关闭）。
     *
     * <p><b>默认 true（失败关闭）</b>。业界安全配置的默认必须是"拒绝"：若默认放行， 那么"忘记配置"就是一次静默的安全降级—— 上报端点退化为匿名可写， 而日志里只有一条
     * WARN，足够被忽略。生产环境漏配密钥时，正确的表现是**上报失败并报警**（业务方立刻发现日志没上报）， 而不是日志被无声污染。
     *
     * <p>确需本地联调时置 {@code false}（或经环境变量 {@code
     * BONE_EXTENSION_REPORTER_AUTH_REQUIRED=false}），此时行为与旧版一致。
     *
     * <p>该开关**不适用于生产**：把"拒绝"改成"放行"必须是一次显式的、有痕迹的动作，不能是默认值。
     */
    private boolean reporterAuthRequired = true;

    public String getReporterToken() {
      return reporterToken;
    }

    public void setReporterToken(String reporterToken) {
      this.reporterToken = reporterToken;
    }

    public boolean isReporterAuthRequired() {
      return reporterAuthRequired;
    }

    public void setReporterAuthRequired(boolean reporterAuthRequired) {
      this.reporterAuthRequired = reporterAuthRequired;
    }
  }

  /** 5a G3 生命周期分权：部署与生效（publish-runtime）分离，SoD 最低要求。 */
  public static class LifecycleConfig {
    /**
     * true（As-Is 默认）：:deploy 即置 ACTIVE 并推送运行时（部署即生效）； false（[Target]）：:deploy 停在 STAGED，生效由
     * :publish-runtime 完成（切 ACTIVE + 推送路由）。
     */
    private boolean deployPublishes = true;

    public boolean isDeployPublishes() {
      return deployPublishes;
    }

    public void setDeployPublishes(boolean deployPublishes) {
      this.deployPublishes = deployPublishes;
    }
  }

  public static class RuntimeSyncConfig {
    private boolean enabled = false;
    private String refreshChannel = "bone:ext:metadata:refresh";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getRefreshChannel() {
      return refreshChannel;
    }

    public void setRefreshChannel(String refreshChannel) {
      this.refreshChannel = refreshChannel;
    }
  }

  public static class LroConfig {
    /** 是否对 :deploy 启用 LRO */
    private boolean deployEnabled = true;

    /** true：:deploy 默认同步 200（联调/契约测试）；false：默认 202 异步（生产推荐）。 */
    private boolean deploySyncByDefault = false;

    public boolean isDeployEnabled() {
      return deployEnabled;
    }

    public void setDeployEnabled(boolean deployEnabled) {
      this.deployEnabled = deployEnabled;
    }

    public boolean isDeploySyncByDefault() {
      return deploySyncByDefault;
    }

    public void setDeploySyncByDefault(boolean deploySyncByDefault) {
      this.deploySyncByDefault = deploySyncByDefault;
    }
  }

  public static class IdempotencyConfig {
    /** memory | redis */
    private String backend = "memory";

    private String keyPrefix = "bone:ext:idem:";

    public String getBackend() {
      return backend;
    }

    public void setBackend(String backend) {
      this.backend = backend;
    }

    public String getKeyPrefix() {
      return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
      this.keyPrefix = keyPrefix;
    }
  }

  public static class ArtifactConfig {
    /** 插件 JAR 本地存储目录 */
    private String storagePath = "./data/extension-plugins";

    /** 每插件保留的最大版本数（详设：最近 3 个） */
    private int maxVersionsPerPlugin = 3;

    public String getStoragePath() {
      return storagePath;
    }

    public void setStoragePath(String storagePath) {
      this.storagePath = storagePath;
    }

    public int getMaxVersionsPerPlugin() {
      return maxVersionsPerPlugin;
    }

    public void setMaxVersionsPerPlugin(int maxVersionsPerPlugin) {
      this.maxVersionsPerPlugin = maxVersionsPerPlugin;
    }
  }
}
