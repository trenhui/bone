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

  public static class SecurityConfig {
    /** 本地联调：允许无 JWT 访问 Studio API（生产必须 false） */
    private boolean permitUnauthenticated = false;

    public boolean isPermitUnauthenticated() {
      return permitUnauthenticated;
    }

    public void setPermitUnauthenticated(boolean permitUnauthenticated) {
      this.permitUnauthenticated = permitUnauthenticated;
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
