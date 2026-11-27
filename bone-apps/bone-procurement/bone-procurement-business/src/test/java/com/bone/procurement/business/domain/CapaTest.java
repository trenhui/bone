package com.bone.procurement.business.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.business.domain.model.business.Capa;
import com.bone.procurement.business.domain.model.business.CapaStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapaTest {

  private static final Date NOW = Date.from(Instant.parse("2026-11-26T00:00:00Z"));

  @Test
  @DisplayName("责任人或期限缺失不得立项")
  void missingOwnerOrDueDateRejected() {
    assertThatThrownBy(() -> Capa.open(1L, "CAPA-1", "SUP-1", "批次交付延误", " ", plusDays(3)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("责任人");
    assertThatThrownBy(() -> Capa.open(1L, "CAPA-2", "SUP-1", "批次交付延误", "alice", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("期限");
  }

  @Test
  @DisplayName("超期未关闭转人工跟进并保留原期限")
  void overdueKeepsOriginalDueDate() {
    Date due = plusDays(-1);
    Capa capa = Capa.open(1L, "CAPA-3", "SUP-1", "批次交付延误", "alice", due);

    Capa after = capa.markOverdue(NOW);

    assertThat(after.status()).isEqualTo(CapaStatus.OVERDUE);
    assertThat(after.followUpRequired()).isTrue();
    assertThat(after.dueAt()).isEqualTo(due);
  }

  @Test
  @DisplayName("关闭必须具备名操作人与关闭证据")
  void closeRequiresNamedOperatorAndEvidence() {
    Capa capa = Capa.open(1L, "CAPA-4", "SUP-1", "批次交付延误", "alice", plusDays(3));

    assertThatThrownBy(() -> capa.close(" ", "附件", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("操作人");
    assertThatThrownBy(() -> capa.close("bob", " ", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("证据");
    assertThat(capa.status()).isEqualTo(CapaStatus.OPEN);
  }

  @Test
  @DisplayName("关闭后终态不可回写")
  void closedCapaIsTerminal() {
    Capa capa =
        Capa.open(1L, "CAPA-5", "SUP-1", "批次交付延误", "alice", plusDays(3))
            .close("bob", "整改报告引用", NOW);

    assertThat(capa.status()).isEqualTo(CapaStatus.CLOSED);
    assertThat(capa.closedBy()).isEqualTo("bob");
    assertThatThrownBy(capa::assertWritable).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("CAPA 不持有自动改变供应商准入、绩效或授信的字段")
  void capaDoesNotChangeSupplierState() {
    List<String> forbidden =
        Arrays.stream(Capa.class.getDeclaredFields()).map(Field::getName).toList();

    assertThat(forbidden)
        .noneMatch(
            name ->
                name.contains("supplierStatus")
                    || name.contains("score")
                    || name.contains("credit")
                    || name.contains("qualification"));
  }

  private static Date plusDays(int days) {
    return Date.from(NOW.toInstant().plusSeconds(86_400L * days));
  }
}
