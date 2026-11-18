package com.bone.procurement.fulfillment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.procurement.fulfillment.application.command.AsnCommand;
import com.bone.procurement.fulfillment.domain.model.asn.Asn;
import com.bone.procurement.fulfillment.domain.port.PoQueryPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.AsnLineRepository;
import com.bone.procurement.fulfillment.domain.repository.AsnRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ASN 服务 TDD：asn_no 唯一、未确认 PO 拒绝(31503)、行引用/数量校验、包装引用、越权零数据。 */
class AsnApplicationServiceTest {

  private AsnRepository asnRepo;
  private AsnLineRepository lineRepo;
  private PoQueryPort poQuery;
  private VendorIdentityPort identity;
  private AsnApplicationService service;

  @BeforeEach
  void setUp() {
    asnRepo = mock(AsnRepository.class);
    lineRepo = mock(AsnLineRepository.class);
    poQuery = mock(PoQueryPort.class);
    identity = mock(VendorIdentityPort.class);
    service = new AsnApplicationService(asnRepo, lineRepo, poQuery, identity);
    when(identity.requireVendorPartyId()).thenReturn("V-A");
    when(asnRepo.save(any())).thenAnswer(inv -> 1L);
  }

  private PoQueryPort.PoSnapshot ackPo() {
    return new PoQueryPort.PoSnapshot(
        "PO-1",
        0,
        "ACKNOWLEDGED",
        "V-A",
        List.of(
            new PoQueryPort.PoSnapshot.Line(
                1, "ITEM-1", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN)));
  }

  private AsnCommand cmd() {
    return new AsnCommand(
        1L,
        "ASN-1",
        "PO-1",
        "2026-11-17",
        "SF",
        "SF123",
        List.of("SSCC-1"),
        List.of(new AsnCommand.Line(1, 1, "ITEM-1", BigDecimal.valueOf(5), "EA", "SSCC-1")));
  }

  @Test
  void 已确认PO发ASN成功() {
    when(asnRepo.findByAsnNo(anyLong(), anyString())).thenReturn(Optional.empty());
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(ackPo()));

    Asn asn = service.create(cmd());

    assertThat(asn.getAsnNo()).isEqualTo("ASN-1");
    assertThat(asn.getStatus()).isEqualTo("CREATED");
    verify(lineRepo).save(any());
  }

  @Test
  void 未确认PO发ASN被拒绝31503() {
    PoQueryPort.PoSnapshot sent = new PoQueryPort.PoSnapshot("PO-1", 0, "SENT", "V-A", List.of());
    when(asnRepo.findByAsnNo(anyLong(), anyString())).thenReturn(Optional.empty());
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(sent));

    assertThatThrownBy(() -> service.create(cmd()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("31503");
    verify(asnRepo, never()).save(any());
  }

  @Test
  void 包装引用不存在被拒绝() {
    when(asnRepo.findByAsnNo(anyLong(), anyString())).thenReturn(Optional.empty());
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(ackPo()));
    AsnCommand bad =
        new AsnCommand(
            1L,
            "ASN-1",
            "PO-1",
            "2026-11-17",
            "SF",
            "SF123",
            List.of("SSCC-1"),
            List.of(new AsnCommand.Line(1, 1, "ITEM-1", BigDecimal.ONE, "EA", "SSCC-OTHER")));

    assertThatThrownBy(() -> service.create(bad))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("ASN_PACK_REF_INVALID");
  }

  @Test
  void 发货数量超过PO行被拒绝() {
    when(asnRepo.findByAsnNo(anyLong(), anyString())).thenReturn(Optional.empty());
    when(poQuery.findByNo(anyLong(), anyString())).thenReturn(Optional.of(ackPo()));
    AsnCommand over =
        new AsnCommand(
            1L,
            "ASN-1",
            "PO-1",
            "2026-11-17",
            "SF",
            "SF123",
            List.of("SSCC-1"),
            List.of(new AsnCommand.Line(1, 1, "ITEM-1", BigDecimal.valueOf(99), "EA", "SSCC-1")));

    assertThatThrownBy(() -> service.create(over))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("ASN_QTY_EXCEED");
  }

  @Test
  void 跨供应商ASN幂等返回他人单据被拒绝403() {
    Asn other = new Asn();
    other.setVendorPartyId("V-B");
    when(asnRepo.findByAsnNo(anyLong(), anyString())).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.create(cmd()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("403");
  }
}
