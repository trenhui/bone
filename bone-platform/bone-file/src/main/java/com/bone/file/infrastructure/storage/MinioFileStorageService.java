package com.bone.file.infrastructure.storage;

import com.bone.file.application.port.out.FileStoragePort;
import com.bone.file.common.FileErrorCodes;
import com.bone.file.common.FileErrors;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
      @Value("${bone.file.minio.bucket:platform-files}") String defaultBucket,
      Environment environment) {
    if ("minioadmin".equals(accessKey) && environment.acceptsProfiles(Profiles.of("prod"))) {
      throw new IllegalStateException(
          "生产环境禁止使用默认 MinIO 凭证 minioadmin，请通过 BONE_FILE_MINIO_ACCESS_KEY/SECRET_KEY 外部化");
    }
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
      throw FileErrors.of(FileErrorCodes.UPLOAD_FAILED, objectName, e);
    }
  }

  @Override
  public InputStream download(String bucket, String objectName) {
    String targetBucket = bucket == null || bucket.isBlank() ? defaultBucket : bucket;
    try {
      // 先 stat 确认对象存在：getObject 返回的是惰性流，缺失错误在 controller 的 in.transferTo(out)
      // 时才抛出，无法在 download() 内被 ErrorResponseException 捕获，最终会被 controller 兜底成
      // DOWNLOAD_FAILED(500)。用 statObject（急切 HEAD）提前把「对象不存在」识别为 NOT_FOUND(404)，
      // 让前端按 errorCode 正确分流，避免把「请求合理但资源缺失」误判为「服务端故障」。
      minioClient.statObject(
          StatObjectArgs.builder().bucket(targetBucket).object(objectName).build());
      return minioClient.getObject(
          GetObjectArgs.builder().bucket(targetBucket).object(objectName).build());
    } catch (ErrorResponseException e) {
      // 对象不存在（NoSuchKey / NoSuchObject / NoSuchBucket）→ 404，而非 500 下载失败。
      String errorCode = e.errorResponse() != null ? e.errorResponse().code() : "";
      if ("NoSuchKey".equals(errorCode)
          || "NoSuchObject".equals(errorCode)
          || "NoSuchBucket".equals(errorCode)) {
        throw FileErrors.of(FileErrorCodes.NOT_FOUND, objectName, e);
      }
      throw FileErrors.of(FileErrorCodes.DOWNLOAD_FAILED, objectName, e);
    } catch (Exception e) {
      throw FileErrors.of(FileErrorCodes.DOWNLOAD_FAILED, objectName, e);
    }
  }

  @Override
  public void delete(String bucket, String objectName) {
    String targetBucket = bucket == null || bucket.isBlank() ? defaultBucket : bucket;
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(targetBucket).object(objectName).build());
    } catch (Exception e) {
      throw FileErrors.of(FileErrorCodes.DELETE_FAILED, objectName, e);
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
