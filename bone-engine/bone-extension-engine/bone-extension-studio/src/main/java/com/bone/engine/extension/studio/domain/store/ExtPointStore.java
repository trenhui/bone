package com.bone.engine.extension.studio.domain.store;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import org.springframework.lang.Nullable;

import java.util.List;

/** Studio 扩展点持久化端口。 */
public interface ExtPointStore {

    List<ExtPoint> findAll();

    @Nullable
    ExtPoint findById(Long id);

    @Nullable
    ExtPoint findByInterfaceName(String interfaceName);

    List<ExtPoint> search(String keyword);

    ExtPoint save(ExtPoint extPoint);

    boolean update(ExtPoint extPoint);

    boolean deleteById(Long id);

    long count();
}
