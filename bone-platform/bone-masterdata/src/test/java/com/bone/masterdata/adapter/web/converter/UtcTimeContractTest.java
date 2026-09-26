package com.bone.masterdata.adapter.web.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bone.masterdata.adapter.web.dto.response.DataStandardResp;
import com.bone.masterdata.adapter.web.dto.response.MasterDataEntityDetailResp;
import com.bone.masterdata.adapter.web.dto.response.MasterDataFieldDetailResp;
import com.bone.masterdata.adapter.web.dto.response.MasterDataRecordDetailResp;
import com.bone.masterdata.application.query.dto.DataStandardDTO;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 时间字段 UTC 契约测试（i18n 方案 §6.4）：对外响应里的 {@code LocalDateTime}/{@code Date} 必须在 converter 边界转为 {@link
 * Instant}（UTC 归一），序列化为带偏移的 ISO-8601（{@code ...Z}），否则前端 {@code dayjs.utc(v)} 会静默 +8h。
 *
 * <p>回归防护：若删掉各 converter 的 {@code toInstant(LocalDateTime)} 私有方法（或 {@code
 * MasterDataFieldDetailResp} 漏映射），响应时间字段会变成无偏移字符串或保持为 {@code null}，CI 脚本查不出——必须用<b>行为级</b>断言钉死口径。
 */
class UtcTimeContractTest {

  private static final LocalDateTime SAMPLE = LocalDateTime.of(2026, 9, 25, 12, 0, 0);
  private static final Instant EXPECTED = Instant.parse("2026-09-25T12:00:00Z");

  @Test
  void dataStandardRespTimeIsUtcInstant() {
    DataStandardDTO dto = DataStandardDTO.builder().createdAt(SAMPLE).updatedAt(SAMPLE).build();
    DataStandardResp resp = new DataStandardWebConverter().toResp(dto);
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void entityRespTimeIsUtcInstant() {
    MasterDataEntityDTO dto =
        MasterDataEntityDTO.builder().createdAt(SAMPLE).updatedAt(SAMPLE).build();
    MasterDataEntityDetailResp resp = new MasterDataEntityWebConverter().toResp(dto);
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void recordRespTimeIsUtcInstant() {
    MasterDataRecordDTO dto =
        MasterDataRecordDTO.builder()
            .createdAt(SAMPLE)
            .updatedAt(SAMPLE)
            .publishTime(SAMPLE)
            .build();
    MasterDataRecordDetailResp resp = new MasterDataRecordWebConverter().toResp(dto);
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
    assertEquals(EXPECTED, resp.getPublishTime());
  }

  @Test
  void fieldRespTimeIsUtcInstant() {
    MasterDataFieldDTO dto =
        MasterDataFieldDTO.builder().createdAt(SAMPLE).updatedAt(SAMPLE).build();
    MasterDataFieldDetailResp resp = new MasterDataFieldWebConverter().toResp(dto);
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }
}
