package com.bone.engine.extension.studio.domain.gateway;

import com.bone.engine.extension.studio.domain.model.extension.Extension;
import java.util.List;

/** 扩展实现读侧端口（§18.3 / ADR-0013）。 */
public interface ExtensionReadPort {

  List<Extension> findAll();

  List<Extension> findByExtPointId(Long extPointId);

  List<Extension> findByTenantCode(String tenantCode);

  /** 按归属应用过滤（5a G4 应用扩展视图） */
  List<Extension> findByAppId(Long appId);

  List<Extension> search(String keyword);

  long count();
}
