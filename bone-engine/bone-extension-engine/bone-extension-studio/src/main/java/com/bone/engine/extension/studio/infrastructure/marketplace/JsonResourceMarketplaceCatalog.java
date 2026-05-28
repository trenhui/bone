package com.bone.engine.extension.studio.infrastructure.marketplace;

import com.bone.engine.extension.studio.domain.gateway.MarketplaceCatalog;
import com.bone.engine.extension.studio.domain.model.MarketplaceItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 静态 JSON 目录实现（{@code classpath:marketplace/items.json}）。 */
@Component
@Slf4j
public class JsonResourceMarketplaceCatalog implements MarketplaceCatalog {

    private static final String DEFAULT_LOCATION = "marketplace/items.json";

    private final List<MarketplaceItem> items;

    @Autowired
    public JsonResourceMarketplaceCatalog(ObjectMapper objectMapper) {
        this(objectMapper, new ClassPathResource(DEFAULT_LOCATION));
    }

    JsonResourceMarketplaceCatalog(ObjectMapper objectMapper, Resource resource) {
        this.items = load(objectMapper, resource);
    }

    @Override
    public List<MarketplaceItem> list(String keyword, String category) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(item -> kw.isEmpty()
                        || (item.name() != null && item.name().toLowerCase(Locale.ROOT).contains(kw))
                        || (item.description() != null
                                && item.description().toLowerCase(Locale.ROOT).contains(kw)))
                .filter(item -> cat.isEmpty()
                        || (item.category() != null && item.category().equalsIgnoreCase(cat)))
                .toList();
    }

    @Override
    public Optional<MarketplaceItem> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return items.stream().filter(item -> id.equals(item.id())).findFirst();
    }

    private static List<MarketplaceItem> load(ObjectMapper objectMapper, Resource resource) {
        if (!resource.exists()) {
            log.warn("Marketplace catalog not found: {}", resource);
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<MarketplaceItem>>() {});
        } catch (IOException ex) {
            log.warn("Marketplace catalog parse failed: {}", ex.getMessage());
            return List.of();
        }
    }
}
