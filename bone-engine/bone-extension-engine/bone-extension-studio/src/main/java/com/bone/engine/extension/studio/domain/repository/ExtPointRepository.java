package com.bone.engine.extension.studio.domain.repository;

import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import org.springframework.lang.Nullable;

/** 扩展点写侧仓储；读方法见 {@link com.bone.engine.extension.studio.domain.gateway.ExtPointReadPort}。 */
public interface ExtPointRepository {

  @Nullable
  ExtPoint findById(Long id);

  @Nullable
  ExtPoint findByInterfaceName(String interfaceName);

  ExtPoint save(ExtPoint extPoint);

  boolean remove(Long id);
}
