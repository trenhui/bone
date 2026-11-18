package com.bone.procurement.fulfillment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.procurement.fulfillment.application.command.AckCommand;
import com.bone.procurement.fulfillment.domain.model.ack.PoAcknowledgement;
import com.bone.procurement.fulfillment.domain.port.PoQueryPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.PoAcknowledgementRepository;
import com.bone.procurement.transaction.application.PurchaseOrderApplicationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/** PO 确认服务 TDD：幂等、终态拒绝、跨供应商越权零数据、版本一致。 */
class PoAckApplicationServiceTest {

  private PoAcknowledgementRepository repo;
  private PoQueryPort poQuery;
  private VendorIdentityPort identity;
  private ApplicationContext context;
  private PoAckApplicationService service;

  @BeforeEach
  void setUp() {
    repo = mock(PoAcknowledgementRepository.class);
    poQuery = mock(PoQueryPort.class);
    identity = mock(VendorIdentityPort.class);
    context = mock(ApplicationContext.class);
    service = new PoAckApplicationService(repo, poQuery, identity, context);
    when(identity.requireVendorPartyId()).thenReturn("V-A");
    when(repo.save(any())).thenAnswer(inv -> 1L);
    PurchaseOrderApplicationService poService = mock(PurchaseOrderApplicationService.class);
    when(context.getBean("purchaseOrderApplicationService")).thenReturn(poService);
  }

  private PoQueryPort.PoSnapshot sentPo() {
    return new PoQueryPort.PoSnapshot(
        "PO-1",
        0,
        "SENT",
        "V-A",
        List.of(
            new PoQueryPort.PoSnapshot.Line(
                1, "ITEM-1", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN)));
  }

  private AckCommand cmd() {
    return new AckCommand(1L, "PO-1", 0, "FULL", List.of());
  }

  @Test
  void 已下达PO确认成功并保存() {
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(sentPo()));
    when(repo.findByIdempotentKey(anyLong(), anyString(), anyInt(), anyString()))
        .thenReturn(Optional.empty());

    PoAcknowledgement ack = service.ack(cmd());

    assertThat(ack.getAckType()).isEqualTo("FULL");
    assertThat(ack.getVendorPartyId()).isEqualTo("V-A");
    verify(repo).save(any());
  }

  @Test
  void 重复确认同一版本幂等只返回首条() {
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(sentPo()));
    PoAcknowledgement first = new PoAcknowledgement();
    first.setAckType("FULL");
    when(repo.findByIdempotentKey(anyLong(), anyString(), anyInt(), anyString()))
        .thenReturn(Optional.of(first));

    PoAcknowledgement result = service.ack(cmd());

    assertThat(result.getAckType()).isEqualTo("FULL");
    verify(repo, never()).save(any());
  }

  @Test
  void 跨供应商确认PO被拒绝403() {
    PoQueryPort.PoSnapshot otherVendor =
        new PoQueryPort.PoSnapshot("PO-1", 0, "SENT", "V-B", List.of());
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(otherVendor));

    assertThatThrownBy(() -> service.ack(cmd()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("403");
    verify(repo, never()).save(any());
  }

  @Test
  void 终态PO确认被拒绝() {
    PoQueryPort.PoSnapshot cancelled =
        new PoQueryPort.PoSnapshot("PO-1", 0, "CANCELLED", "V-A", List.of());
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(cancelled));

    assertThatThrownBy(() -> service.ack(cmd()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("409");
  }

  @Test
  void 陈旧版本确认被拒绝() {
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(sentPo()));
    AckCommand stale = new AckCommand(1L, "PO-1", 5, "FULL", List.of()); // 提交版本 5 > 当前 0（不等即拒绝）

    assertThatThrownBy(() -> service.ack(stale))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("版本不匹配");
  }
}
