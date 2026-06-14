package com.bone.iam.domain.client;

import java.io.InputStream;

public interface StorageClient {
  void save(String key, String content);

  void save(String key, InputStream inputStream);

  String get(String key);

  InputStream getStream(String key);

  boolean exists(String key);

  void delete(String key);
}
