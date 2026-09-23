package com.bone.engine.extension.studio.application.support;

import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PluginArtifactSupport {

  private final Path storageRoot;
  private final int maxVersionsPerPlugin;

  public PluginArtifactSupport(ExtensionStudioProperties properties) throws IOException {
    String configured = properties.getArtifact().getStoragePath();
    this.storageRoot =
        Path.of(StringUtils.hasText(configured) ? configured : "./data/extension-plugins")
            .toAbsolutePath()
            .normalize();
    Files.createDirectories(storageRoot);
    this.maxVersionsPerPlugin = Math.max(1, properties.getArtifact().getMaxVersionsPerPlugin());
  }

  public StoredArtifact store(Long pluginId, String version, MultipartFile file)
      throws IOException {
    if (pluginId == null || pluginId <= 0) {
      throw new IllegalArgumentException("pluginId 无效");
    }
    if (!StringUtils.hasText(version)) {
      throw new IllegalArgumentException("version 不能为空");
    }
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("插件包不能为空");
    }
    String original = file.getOriginalFilename();
    if (original != null && !original.toLowerCase().endsWith(".jar")) {
      throw new IllegalArgumentException("仅支持 JAR 插件包");
    }

    try (InputStream in = file.getInputStream()) {
      JarMagicValidator.validate(in);
    }

    Path pluginDir = storageRoot.resolve(String.valueOf(pluginId));
    Files.createDirectories(pluginDir);
    String safeVersion = version.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    Path target = pluginDir.resolve(safeVersion + ".jar");

    try (InputStream in = file.getInputStream()) {
      Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    byte[] bytes = Files.readAllBytes(target);
    String logicalPath = pluginId + "/" + safeVersion + ".jar";
    return new StoredArtifact(logicalPath, bytes.length, sha256(bytes));
  }

  /** 将库中逻辑路径或历史绝对路径解析为可读文件路径。 */
  public Path resolveArtifactPath(String filePath) {
    if (!StringUtils.hasText(filePath)) {
      throw new IllegalArgumentException("制品路径为空");
    }
    Path candidate = Path.of(filePath.trim());
    if (candidate.isAbsolute()) {
      return candidate.normalize();
    }
    Path resolved = storageRoot.resolve(candidate).normalize();
    if (!resolved.startsWith(storageRoot)) {
      throw new IllegalArgumentException("非法制品路径");
    }
    return resolved;
  }

  public Resource openArtifact(String filePath) throws IOException {
    Path path = resolveArtifactPath(filePath);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("制品文件不存在");
    }
    Resource resource = new UrlResource(path.toUri());
    if (!resource.exists() || !resource.isReadable()) {
      throw new IllegalArgumentException("制品文件不可读");
    }
    return resource;
  }

  public int getMaxVersionsPerPlugin() {
    return maxVersionsPerPlugin;
  }

  private static String sha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  /**
   * @param filePath 逻辑相对路径（推荐）或历史绝对路径
   */
  public record StoredArtifact(String filePath, long fileSize, String checksum) {}
}
