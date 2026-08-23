package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import io.minio.GetObjectArgs;
import io.minio.ListBucketsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** S3 / MinIO 连接器：基于 {@link MinioClient} 的真实调用（MinIO SDK，兼容 S3）。 */
@Component("S3")
public class S3ClientImpl implements ExternalSystemClient {

  @Override
  public boolean testConnection(Map<String, Object> config) {
    try {
      MinioClient client = buildClient(config);
      client.listBuckets(ListBucketsArgs.builder().build());
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public Object sendRequest(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    MinioClient client = buildClient(config);
    String bucket = bucketOf(params, config);
    String key = keyOf(endpoint, params, config);
    String operation = operationOf(params, config);
    try {
      switch (operation.toUpperCase()) {
        case "PUT" -> {
          byte[] content = contentOf(params, config);
          client.putObject(
              PutObjectArgs.builder().bucket(bucket).object(key).stream(
                      new ByteArrayInputStream(content), content.length, -1)
                  .build());
          return Map.of("ok", true, "bucket", bucket, "key", key);
        }
        case "GET" -> {
          try (var stream =
              client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes = stream.readAllBytes();
            return Map.of(
                "bucket", bucket, "key", key, "content", new String(bytes, StandardCharsets.UTF_8));
          }
        }
        case "LIST" -> {
          var result = new LinkedHashMap<String, Object>();
          result.put("bucket", bucket);
          result.put("key", key);
          return result;
        }
        default -> {
          return Map.of("bucket", bucket, "key", key, "operation", operation);
        }
      }
    } catch (MinioException | java.io.IOException ex) {
      throw new IllegalStateException("S3 请求失败: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new IllegalStateException("S3 请求失败: " + ex.getMessage(), ex);
    }
  }

  @Override
  public String getType() {
    return "S3";
  }

  private static MinioClient buildClient(Map<String, Object> config) {
    String endpoint =
        config.get("endpoint") == null ? null : String.valueOf(config.get("endpoint"));
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("S3 endpoint 不能为空");
    }
    String accessKey =
        config.get("accessKey") == null ? "" : String.valueOf(config.get("accessKey"));
    String secretKey =
        config.get("secretKey") == null ? "" : String.valueOf(config.get("secretKey"));
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }

  private static String bucketOf(Map<String, Object> params, Map<String, Object> config) {
    Object bucket = params != null ? params.get("bucket") : null;
    if (bucket == null) {
      bucket = config.get("bucket");
    }
    return bucket == null ? "default" : String.valueOf(bucket);
  }

  private static String keyOf(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    if (endpoint != null && !endpoint.isBlank()) {
      return endpoint;
    }
    Object key = params != null ? params.get("key") : null;
    if (key == null) {
      key = config.get("key");
    }
    return key == null ? "default" : String.valueOf(key);
  }

  private static String operationOf(Map<String, Object> params, Map<String, Object> config) {
    Object op = params != null ? params.get("operation") : null;
    if (op == null) {
      op = config.get("operation");
    }
    return op == null ? "GET" : String.valueOf(op);
  }

  private static byte[] contentOf(Map<String, Object> params, Map<String, Object> config) {
    Object content = params != null ? params.get("content") : null;
    if (content == null) {
      content = config.get("content");
    }
    return content == null ? new byte[0] : String.valueOf(content).getBytes(StandardCharsets.UTF_8);
  }
}
