package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 保司推送写入器单测：写入幂等 + payload 完整 + 状态查询。 */
@ExtendWith(MockitoExtension.class)
class InsurerPushWriterTest {

  @Mock DomainEventOutboxRepository repo;
  @InjectMocks InsurerPushWriter writer;

  @Test
  void appendOnFinish_writesOutboxWithFullPayload() {
    ObjectMapper om = new ObjectMapper();
    // 用真实 mapper 替换 @InjectMocks 注入的默认 mock（若有）
    InsurerPushWriter w = new InsurerPushWriter(repo, om);
    when(repo.findLatestByAggregateAndEventType(anyString(), anyLong(), anyString()))
        .thenReturn(null);
    when(repo.save(any())).thenReturn(1L);

    boolean created =
        w.appendOnFinish(
            0L,
            42L,
            "CL2026",
            "RP2026",
            "POL-1",
            "IP-1",
            "INS-CODE",
            "某保司",
            new BigDecimal("710.15"),
            "99");

    assertTrue(created);
    ArgumentCaptor<DomainEventOutbox> cap = ArgumentCaptor.forClass(DomainEventOutbox.class);
    verify(repo).save(cap.capture());
    DomainEventOutbox o = cap.getValue();
    assertEquals("ClaimCase", o.getAggregate());
    assertEquals(42L, o.getAggregateId());
    assertEquals("INSURER_PUSH", o.getEventType());
    assertEquals(DomainEventOutbox.STATUS_PENDING, o.getStatus());
    assertNotNull(o.getId());
    assertTrue(o.getPayload().contains("\"claimNo\":\"CL2026\""));
    assertTrue(o.getPayload().contains("\"insurerCode\":\"INS-CODE\""));
    assertTrue(o.getPayload().contains("710.15"));
  }

  @Test
  void appendOnFinish_isIdempotent_whenAlreadyRegistered() {
    InsurerPushWriter w = new InsurerPushWriter(repo, new ObjectMapper());
    DomainEventOutbox existing = DomainEventOutbox.of(0L, "ClaimCase", 42L, "INSURER_PUSH", "{}");
    when(repo.findLatestByAggregateAndEventType("ClaimCase", 42L, "INSURER_PUSH"))
        .thenReturn(existing);

    boolean created = w.appendOnFinish(0L, 42L, "CL", "RP", "P", "", "", "", BigDecimal.ONE, "1");

    assertFalse(created);
    verify(repo, never()).save(any());
  }

  @Test
  void findByClaim_returnsStatusView() {
    InsurerPushWriter w = new InsurerPushWriter(repo, new ObjectMapper());
    DomainEventOutbox o =
        DomainEventOutbox.of(0L, "ClaimCase", 42L, "INSURER_PUSH", "{\"claimNo\":\"CL9\"}");
    o.setId(7L);
    o.markSent();
    when(repo.findLatestByAggregateAndEventType("ClaimCase", 42L, "INSURER_PUSH")).thenReturn(o);

    InsurerPushWriter.PushStatus st = w.findByClaim(42L);

    assertNotNull(st);
    assertEquals(7L, st.outboxId());
    assertEquals("CL9", st.claimNo());
    assertEquals("SENT", st.status());
  }
}
