package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;
import com.bone.engine.extension.studio.domain.gateway.StudioAuditReadPort;
import com.bone.engine.extension.studio.domain.repository.StudioAuditRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.converter.StudioPersistenceConverter;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioAuditLog;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioAuditLogRepository;
import com.bone.engine.extension.studio.util.CursorCodec;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnProperty(prefix = "bone.extension.studio.persistence", name = "mode", havingValue = "metadata")
public class MetadataStudioAuditRepository implements StudioAuditRepository, StudioAuditReadPort {

    private final ExtStudioAuditLogRepository repository;

    public MetadataStudioAuditRepository(ExtStudioAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public StudioAuditEntry save(StudioAuditEntry entry) {
        ExtStudioAuditLog row = StudioPersistenceConverter.toAuditLogEntity(entry);
        repository.insert(row);
        entry.setId(row.getId());
        return entry;
    }

    @Override
    public PageResult<StudioAuditEntry> queryByCursor(
            String action, String resourceType, String cursor, int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        Long afterId = CursorCodec.decode(cursor);

        var query = QueryBuilder.from(ExtStudioAuditLog.class);
        if (StringUtils.hasText(action)) {
            query.where(ExtStudioAuditLog::getAction).eq(action);
        }
        if (StringUtils.hasText(resourceType)) {
            query.where(ExtStudioAuditLog::getResourceType).eq(resourceType);
        }
        if (afterId != null) {
            query.where(ExtStudioAuditLog::getId).lt(afterId);
        }

        List<ExtStudioAuditLog> rows = query.orderByDesc(ExtStudioAuditLog::getId).limit(safeLimit + 1).list();

        String nextCursor = null;
        List<StudioAuditEntry> records;
        if (rows.size() > safeLimit) {
            records = rows.subList(0, safeLimit).stream()
                    .map(StudioPersistenceConverter::toAuditDomain)
                    .toList();
            nextCursor = CursorCodec.encode(records.get(records.size() - 1).getId());
        } else {
            records = rows.stream().map(StudioPersistenceConverter::toAuditDomain).toList();
        }
        return PageResult.cursorOf(new ArrayList<>(records), nextCursor, safeLimit);
    }
}
