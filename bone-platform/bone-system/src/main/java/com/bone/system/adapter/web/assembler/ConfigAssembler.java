package com.bone.system.adapter.web.assembler;

import com.bone.system.adapter.web.dto.request.ConfigPageReq;
import com.bone.system.adapter.web.dto.request.CreateConfigReq;
import com.bone.system.adapter.web.dto.request.UpdateConfigReq;
import com.bone.system.adapter.web.dto.response.ConfigResp;
import com.bone.system.application.command.CreateConfigCommand;
import com.bone.system.application.command.UpdateConfigCommand;
import com.bone.system.application.query.dto.ConfigDto;
import com.bone.system.application.query.qry.ConfigPageQuery;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

/**
 * HTTP 协议对象 ↔ application 用例对象的转换边界（E-10.1）。
 *
 * <p>命名由 {@code *WebConverter} 收敛为 {@code *Assembler}：`Converter` 后缀在 E-6.6 已指派给 infrastructure
 * 的持久化转换，一个后缀承载两个构件类别会让命名失去辨识度（E-13.4）。
 */
@Mapper
public interface ConfigAssembler {

  CreateConfigCommand toCommand(CreateConfigReq req);

  UpdateConfigCommand toCommand(UpdateConfigReq req);

  ConfigPageQuery toQuery(ConfigPageReq req);

  ConfigResp toResp(ConfigDto dto);

  /** LocalDateTime → Instant（UTC 归一），对外契约统一带偏移的 ISO-8601（...Z）（i18n 方案 §6.4）。 */
  default Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
