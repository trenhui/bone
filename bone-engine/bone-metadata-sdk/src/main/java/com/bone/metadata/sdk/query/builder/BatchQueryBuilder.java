package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;

public interface BatchQueryBuilder<T> {
  BatchCompiledQuery build(T ctx);
}
