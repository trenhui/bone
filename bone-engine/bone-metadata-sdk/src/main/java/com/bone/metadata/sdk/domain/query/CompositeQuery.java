package com.bone.metadata.sdk.domain.query;

import java.util.Collections;
import java.util.List;

public class CompositeQuery extends CompiledQuery {
  private final List<CompiledQuery> segments;

  public CompositeQuery(List<CompiledQuery> segments) {
    super("", Collections.emptyMap()); // 调用父类构造方法
    this.segments = segments;
  }

  public List<CompiledQuery> getSegments() {
    return segments;
  }
}
