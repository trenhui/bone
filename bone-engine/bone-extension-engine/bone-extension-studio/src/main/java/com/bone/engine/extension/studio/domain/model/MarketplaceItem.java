package com.bone.engine.extension.studio.domain.model;

import java.util.List;

/** 插件市场条目（详设 v2.5 §12.2）。 */
public record MarketplaceItem(
    String id,
    String name,
    String description,
    String version,
    String vendor,
    String category,
    List<String> tags,
    String extPointInterface,
    String className,
    String homepage,
    boolean installed) {}
