package com.bone.iam.infrastructure.external;

import com.bone.iam.domain.client.StorageClient;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MinioStorageClientImpl implements StorageClient {
  private final MinioClient minioClient;
  private final String bucketName;

  public MinioStorageClientImpl(
      @Value("${bone.minio.endpoint:http://localhost:9000}") String endpoint,
      @Value("${bone.minio.access-key:minioadmin}") String accessKey,
      @Value("${bone.minio.secret-key:minioadmin}") String secretKey,
      @Value("${bone.minio.bucket-name:audit-logs}") String bucketName) {
    this.bucketName = bucketName;
    this.minioClient =
        MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    initBucket();
  }

  private void initBucket() {
    try {
      boolean exists =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        log.info("创建 MinIO bucket: {}", bucketName);
      }
    } catch (Exception e) {
      log.warn("初始化 MinIO bucket 失败: {}", e.getMessage());
    }
  }

  @Override
  public void save(String key, String content) {
    try {
      byte[] bytes = content.getBytes();
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(key).contentType("text/plain").stream(
                  new ByteArrayInputStream(bytes), bytes.length, -1)
              .build());
    } catch (Exception e) {
      throw new RuntimeException("Failed to save to MinIO", e);
    }
  }

  @Override
  public void save(String key, InputStream inputStream) {
    try {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(key).stream(inputStream, -1, 10485760)
              .build());
    } catch (Exception e) {
      throw new RuntimeException("Failed to save to MinIO", e);
    }
  }

  @Override
  public String get(String key) {
    try {
      InputStream stream =
          minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(key).build());
      return new String(stream.readAllBytes());
    } catch (Exception e) {
      throw new RuntimeException("Failed to get from MinIO", e);
    }
  }

  @Override
  public InputStream getStream(String key) {
    try {
      return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(key).build());
    } catch (Exception e) {
      throw new RuntimeException("Failed to get stream from MinIO", e);
    }
  }

  @Override
  public boolean exists(String key) {
    try {
      StatObjectResponse stat =
          minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(key).build());
      return stat != null;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void delete(String key) {
    try {
      minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(key).build());
    } catch (Exception e) {
      throw new RuntimeException("Failed to delete from MinIO", e);
    }
  }
}
