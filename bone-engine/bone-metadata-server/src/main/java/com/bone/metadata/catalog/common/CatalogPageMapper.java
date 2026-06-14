package com.bone.metadata.catalog.common;

import com.bone.core.model.PageResult;
import java.util.List;
import java.util.function.Function;

public final class CatalogPageMapper {

  private CatalogPageMapper() {}

  public static <S, T> PageResult<T> toApiPage(
      com.bone.core.model.PageResult<S> sdkPage, Function<S, T> mapper) {
    List<T> list = sdkPage.getRecords().stream().map(mapper).toList();
    return PageResult.of(list, sdkPage.getTotal(), sdkPage.getPage(), sdkPage.getSize());
  }
}
