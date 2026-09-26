package com.bone.system.adapter.web.assembler;

import com.bone.system.adapter.web.dto.request.CreateDictReq;
import com.bone.system.adapter.web.dto.request.DictPageReq;
import com.bone.system.adapter.web.dto.request.UpdateDictReq;
import com.bone.system.adapter.web.dto.response.DictResp;
import com.bone.system.application.command.CreateDictCommand;
import com.bone.system.application.command.UpdateDictCommand;
import com.bone.system.application.query.dto.DictDto;
import com.bone.system.application.query.qry.DictPageQuery;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 字典的 HTTP ↔ application 转换边界。
 *
 * <p>「更新」要把路径变量 {@code id} 与请求体合并成一个 Command——MapStruct 的 {@code @Mapping} 在这里比手写 set
 * 更少出错：漏字段是编译期（未映射策略）而非运行期的静默 null。
 */
@Mapper
public interface DictAssembler {

  CreateDictCommand toCommand(CreateDictReq req);

  @Mapping(target = "id", source = "id")
  UpdateDictCommand toCommand(Long id, UpdateDictReq req);

  DictPageQuery toQuery(DictPageReq req);

  DictResp toResp(DictDto dto);

  /** LocalDateTime → Instant（UTC 归一），对外契约统一带偏移的 ISO-8601（...Z）（i18n 方案 §6.4）。 */
  default Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
