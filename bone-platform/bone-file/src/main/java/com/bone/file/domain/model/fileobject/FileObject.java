package com.bone.file.domain.model.fileobject;

/**
 * 文件对象聚合（内存态）：承载一次上传产生的对象事实。
 *
 * <p><b>为何放在 domain</b>：归属判定（{@link #belongsToTenant(Long)}）是业务规则而非存储细节， 必须与 MinIO / S3
 * 这类基础设施解耦，才能在换存储实现时保持不变。
 *
 * <p><b>为何本类无任何外部依赖</b>：架构规则 {@code domainMustNotDependOnOuterLayers} 要求 domain 不反向依赖 application /
 * infrastructure / adapter，故此处只用 JDK 类型。
 *
 * <p><b>持久化</b>：{@code file_object} 表属 FL-4（L3 待审批），未落地前本聚合只存在于单次请求内， 用于生成响应与归属断言；表落地后可直接交由 SDK
 * 仓储持久化。
 */
public final class FileObject {

  private final String id;
  private final Long tenantId;
  private final String bucket;
  private final String objectName;
  private final String originalName;
  private final String contentType;
  private final long size;

  private FileObject(
      String id,
      Long tenantId,
      String bucket,
      String objectName,
      String originalName,
      String contentType,
      long size) {
    this.id = id;
    this.tenantId = tenantId;
    this.bucket = bucket;
    this.objectName = objectName;
    this.originalName = originalName;
    this.contentType = contentType;
    this.size = size;
  }

  /** 创建对象。tenantId 为空即拒绝——无归属的对象在租户隔离体系下不合法。 */
  public static FileObject of(
      String id,
      Long tenantId,
      String bucket,
      String objectName,
      String originalName,
      String contentType,
      long size) {
    if (tenantId == null) {
      throw new IllegalArgumentException("tenantId 不可为空");
    }
    return new FileObject(id, tenantId, bucket, objectName, originalName, contentType, size);
  }

  /** 归属判定：对象是否属于给定租户。 */
  public boolean belongsToTenant(Long candidate) {
    return candidate != null && candidate.equals(tenantId);
  }

  /** 摘要（日志/审计用）：对象名与原始名不同时一并给出。 */
  public String summarize() {
    return objectName.equals(originalName)
        ? objectName + " (" + size + " bytes)"
        : objectName + " <- " + originalName + " (" + size + " bytes)";
  }

  public String getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public String getBucket() {
    return bucket;
  }

  public String getObjectName() {
    return objectName;
  }

  public String getOriginalName() {
    return originalName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSize() {
    return size;
  }
}
