package com.bone.engine.extension.studio.domain.gateway;

import com.bone.engine.extension.studio.domain.model.MarketplaceItem;
import java.util.List;
import java.util.Optional;

/** 插件市场目录（出站端口；目前由静态 JSON 资源提供，后续可接 OCI/远端仓库）。 */
public interface MarketplaceCatalog {

    List<MarketplaceItem> list(String keyword, String category);

    Optional<MarketplaceItem> findById(String id);
}
