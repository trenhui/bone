package com.bone.iam.infrastructure.external;

import com.bone.iam.domain.client.StorageClient;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
public class MinioStorageClientImpl implements StorageClient {
    private final MinioClient minioClient;
    private final String bucketName = "audit-logs";

    public MinioStorageClientImpl() {
        // 初始化Minio客户端
        this.minioClient = MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("minioadmin", "minioadmin")
                .build();
    }

    @Override
    public void save(String key, String content) {
        try {
            byte[] bytes = content.getBytes();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .contentType("text/plain")
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to save to MinIO", e);
        }
    }

    @Override
    public void save(String key, InputStream inputStream) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .stream(inputStream, -1, 10485760)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to save to MinIO", e);
        }
    }

    @Override
    public String get(String key) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .build()
            );
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get from MinIO", e);
        }
    }

    @Override
    public InputStream getStream(String key) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get stream from MinIO", e);
        }
    }

    @Override
    public boolean exists(String key) {
        // 实现检查对象是否存在的逻辑
        return false;
    }

    @Override
    public void delete(String key) {
        // 实现删除对象的逻辑
    }
}