package com.bone.engine.extension.studio.domain.gateway;

import com.bone.engine.extension.studio.domain.model.Extension;
import java.util.List;

/** 扩展实现读侧端口（§18.3 / ADR-0013）。 */
public interface ExtensionReadPort {

  List<Extension> findAll();

  List<Extension> findByExtPointId(Long extPointId);

  List<Extension> findByTenantCode(String tenantCode);

  List<Extension> search(String keyword);

  long count();
}
