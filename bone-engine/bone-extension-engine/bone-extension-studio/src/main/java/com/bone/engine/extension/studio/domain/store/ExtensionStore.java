package com.bone.engine.extension.studio.domain.store;

import com.bone.engine.extension.studio.domain.model.Extension;
import org.springframework.lang.Nullable;

import java.util.List;

/** Studio 扩展实现持久化端口（与 Metadata Repository 解耦）。 */
public interface ExtensionStore {

    List<Extension> findAll();

    @Nullable
    Extension findById(Long id);

    List<Extension> findByExtPointId(Long extPointId);

    List<Extension> findByTenantCode(String tenantCode);

    List<Extension> search(String keyword);

    Extension save(Extension extension);

    boolean update(Extension extension);

    boolean deleteById(Long id);

    long count();
}
