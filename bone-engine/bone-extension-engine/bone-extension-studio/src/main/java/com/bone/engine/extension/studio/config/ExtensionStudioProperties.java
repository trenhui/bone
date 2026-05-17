package com.bone.engine.extension.studio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "bone.extension.studio")
public class ExtensionStudioProperties {

  @NestedConfigurationProperty
  private final RuntimeSyncConfig runtimeSync = new RuntimeSyncConfig();

  @NestedConfigurationProperty
  private final ArtifactConfig artifact = new ArtifactConfig();

  public RuntimeSyncConfig getRuntimeSync() {
    return runtimeSync;
  }

  public ArtifactConfig getArtifact() {
    return artifact;
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
