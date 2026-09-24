package com.bone.file.infrastructure.storage;

import com.bone.file.application.port.out.FileStoragePort;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.InputStream;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** MinIO 文件存储实现（兼容 S3）。 */
@Slf4j
@Service
public class MinioFileStorageService implements FileStoragePort {

  private final MinioClient minioClient;
  private final String defaultBucket;

  public MinioFileStorageService(
      @Value("${bone.file.minio.endpoint:http://localhost:9000}") String endpoint,
      @Value("${bone.file.minio.access-key:minioadmin}") String accessKey,
      @Value("${bone.file.minio.secret-key:minioadmin}") String secretKey,
      @Value("${bone.file.minio.bucket:platform-files}") String defaultBucket) {
    this.defaultBucket = defaultBucket;
    this.minioClient =
        MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    initBucket(defaultBucket);
  }

  private void initBucket(String bucket) {
    try {
      boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        log.info("创建 MinIO bucket: {}", bucket);
      }
    } catch (Exception e) {
      log.warn("初始化 MinIO bucket 失败: {}", e.getMessage());
    }
  }

  @Override
  public StoredObject upload(
      String bucket, String objectName, InputStream in, long size, String contentType) {
    String targetBucket = bucket == null || bucket.isBlank() ? defaultBucket : bucket;
    try {
      var response =
          minioClient.putObject(
              PutObjectArgs.builder()
                  .bucket(targetBucket)
                  .object(objectName)
                  .contentType(contentType == null ? "application/octet-stream" : contentType)
                  .stream(in, size, -1)
                  .build());
      return new StoredObject(targetBucket, objectName, response.etag(), size, Map.of());
    } catch (Exception e) {
      throw new IllegalStateException("文件上传失败: " + e.getMessage(), e);
    }
  }

  @Override
  public InputStream download(String bucket, String objectName) {
    String targetBucket = bucket == null || bucket.isBlank() ? defaultBucket : bucket;
    try {
      return minioClient.getObject(
          GetObjectArgs.builder().bucket(targetBucket).object(objectName).build());
    } catch (Exception e) {
      throw new IllegalStateException("文件下载失败: " + e.getMessage(), e);
    }
  }

  @Override
  public void delete(String bucket, String objectName) {
    String targetBucket = bucket == null || bucket.isBlank() ? defaultBucket : bucket;
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(targetBucket).object(objectName).build());
    } catch (Exception e) {
      throw new IllegalStateException("文件删除失败: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean exists(String bucket, String objectName) {
    String targetBucket = bucket == null || bucket.isBlank() ? defaultBucket : bucket;
    try {
      StatObjectResponse stat =
          minioClient.statObject(
              StatObjectArgs.builder().bucket(targetBucket).object(objectName).build());
      return stat != null;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean testConnection() {
    try {
      minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucket).build());
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
