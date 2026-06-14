package com.bone.metadata.sdk.extension.plugin;

import java.net.URL;
import java.util.Properties;

/** 插件描述符 - 包含插件元数据 */
public class PluginDescriptor {
  private final String id;
  private final String name;
  private final String version;
  private final String entryClass;
  private final URL location;
  private Properties config = new Properties();
  private PluginStatus status = PluginStatus.INSTALLED;
  private ClassLoader classLoader;

  public PluginDescriptor(String id, String name, String version, String entryClass, URL location) {
    this.id = id;
    this.name = name;
    this.version = version;
    this.entryClass = entryClass;
    this.location = location;
  }

  // Getters
  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public String getEntryClass() {
    return entryClass;
  }

  public URL getLocation() {
    return location;
  }

  public Properties getConfig() {
    return config;
  }

  public PluginStatus getStatus() {
    return status;
  }

  public ClassLoader getClassLoader() {
    return classLoader;
  }

  // Setters
  public void setStatus(PluginStatus status) {
    this.status = status;
  }

  public void setConfig(Properties config) {
    this.config = config;
  }

  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }
}
