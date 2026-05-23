package com.bone.engine.extension.studio.domain.repository;

import com.bone.engine.extension.studio.domain.model.Extension;
import org.springframework.lang.Nullable;

/** 扩展实现写侧仓储（§18.2 白名单；读方法见 {@link com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort}）。 */
public interface ExtensionRepository {

    @Nullable
    Extension findById(Long id);

    @Nullable
    Extension findByClassName(String className);

    Extension save(Extension extension);

    boolean remove(Long id);
}
