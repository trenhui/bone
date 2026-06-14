package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.domain.query.CompiledQuery;

public interface SqlQueryBuilder<T> {
  CompiledQuery build(T ctx);
}
