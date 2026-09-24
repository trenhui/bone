package com.bone.file.application.port.out;

import java.io.InputStream;
import java.util.Map;

/** 文件存储出站端口：上传 / 下载 / 删除 / 探测 / 连接测试。由 infrastructure 适配 MinIO / S3 / OSS 实现。 */
public interface FileStoragePort {

  /** 上传文件，返回对象元数据。 */
  StoredObject upload(
      String bucket, String objectName, InputStream in, long size, String contentType);

  /** 下载文件流。 */
  InputStream download(String bucket, String objectName);

  /** 删除文件。 */
  void delete(String bucket, String objectName);

  /** 探测对象是否存在。 */
  boolean exists(String bucket, String objectName);

  /** 连接测试。 */
  boolean testConnection();

  /** 存储对象元数据。 */
  record StoredObject(
      String bucket, String objectName, String etag, long size, Map<String, String> metadata) {}
}
