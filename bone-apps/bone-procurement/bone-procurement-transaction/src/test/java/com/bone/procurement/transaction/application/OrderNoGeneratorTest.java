package com.bone.procurement.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.bone.procurement.transaction.domain.repository.PurchaseOrderRepository;
import com.bone.procurement.transaction.domain.repository.RequisitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TDD RED：单号格式 PR-{yyyy}-{6位} / PO-{yyyy}-{6位}，连续递增，冲突重试上限 3 次。 */
@ExtendWith(MockitoExtension.class)
class OrderNoGeneratorTest {

  @Mock RequisitionRepository reqRepo;
  @Mock PurchaseOrderRepository poRepo;

  @Test
  void 生成PR单号格式正确() {
    when(reqRepo.hasNo(any(), anyString())).thenReturn(false);
    OrderNoGenerator gen = new OrderNoGenerator(reqRepo, poRepo, () -> 2026);
    String no = gen.nextPoRequisitionNo(1L);
    assertThat(no).matches("PR-2026-\\d{6}");
  }

  @Test
  void 生成PO单号格式正确() {
    when(poRepo.hasNo(any(), anyString())).thenReturn(false);
    OrderNoGenerator gen = new OrderNoGenerator(reqRepo, poRepo, () -> 2026);
    assertThat(gen.nextPurchaseOrderNo(1L)).matches("PO-2026-\\d{6}");
  }

  @Test
  void 冲突重试三次后仍冲突抛异常() {
    when(reqRepo.hasNo(any(), anyString())).thenReturn(true);
    OrderNoGenerator gen = new OrderNoGenerator(reqRepo, poRepo, () -> 2026);
    assertThatThrownBy(() -> gen.nextPoRequisitionNo(1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("重试");
    verify(reqRepo, times(3)).hasNo(any(), anyString());
  }
}
