package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.gateway.StudioAuditReadPort;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;
import com.bone.engine.extension.studio.domain.repository.StudioAuditRepository;
import com.bone.engine.extension.studio.util.CursorCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnProperty(
    prefix = "bone.extension.studio.persistence",
    name = "mode",
    havingValue = "in-memory",
    matchIfMissing = true)
public class InMemoryStudioAuditRepository implements StudioAuditRepository, StudioAuditReadPort {

  private final CopyOnWriteArrayList<StudioAuditEntry> entries = new CopyOnWriteArrayList<>();
  private final AtomicLong idSequence = new AtomicLong(1);

  @Override
  public StudioAuditEntry save(StudioAuditEntry entry) {
    if (entry.getId() == null) {
      entry.setId(idSequence.getAndIncrement());
    }
    entries.add(entry);
    return entry;
  }

  @Override
  public PageResult<StudioAuditEntry> queryByCursor(
      String action, String resourceType, String cursor, int limit) {
    int safeLimit = Math.min(100, Math.max(1, limit));
    Long afterId = CursorCodec.decode(cursor);
    List<StudioAuditEntry> sorted =
        entries.stream()
            .filter(e -> !StringUtils.hasText(action) || action.equals(e.getAction()))
            .filter(
                e -> !StringUtils.hasText(resourceType) || resourceType.equals(e.getResourceType()))
            .sorted(
                Comparator.comparing(
                    StudioAuditEntry::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    List<StudioAuditEntry> page =
        sorted.stream()
            .filter(e -> afterId == null || (e.getId() != null && e.getId() < afterId))
            .limit(safeLimit + 1L)
            .toList();
    String nextCursor = null;
    List<StudioAuditEntry> records;
    if (page.size() > safeLimit) {
      records = new ArrayList<>(page.subList(0, safeLimit));
      nextCursor = CursorCodec.encode(records.get(records.size() - 1).getId());
    } else {
      records = new ArrayList<>(page);
    }
    return PageResult.cursorOf(records, nextCursor, safeLimit);
  }
}
