package com.bone.system.adapter.web.assembler;

import com.bone.system.adapter.web.dto.request.CreateDictItemReq;
import com.bone.system.adapter.web.dto.request.CreateDictTypeReq;
import com.bone.system.adapter.web.dto.request.DictItemPageReq;
import com.bone.system.adapter.web.dto.request.DictTypePageReq;
import com.bone.system.adapter.web.dto.request.MoveDictItemReq;
import com.bone.system.adapter.web.dto.request.UpdateDictItemReq;
import com.bone.system.adapter.web.dto.request.UpdateDictTypeReq;
import com.bone.system.adapter.web.dto.response.DictExportResp;
import com.bone.system.adapter.web.dto.response.DictHierarchyResp;
import com.bone.system.adapter.web.dto.response.DictItemResp;
import com.bone.system.adapter.web.dto.response.DictItemTextResp;
import com.bone.system.adapter.web.dto.response.DictOptionResp;
import com.bone.system.adapter.web.dto.response.DictTypeResp;
import com.bone.system.application.command.CreateDictItemCommand;
import com.bone.system.application.command.CreateDictTypeCommand;
import com.bone.system.application.command.MoveDictItemCommand;
import com.bone.system.application.command.UpdateDictItemCommand;
import com.bone.system.application.command.UpdateDictTypeCommand;
import com.bone.system.application.query.dto.DictExportDto;
import com.bone.system.application.query.dto.DictHierarchyDto;
import com.bone.system.application.query.dto.DictItemDto;
import com.bone.system.application.query.dto.DictItemTextDto;
import com.bone.system.application.query.dto.DictOptionDto;
import com.bone.system.application.query.dto.DictTypeDto;
import com.bone.system.application.query.qry.DictItemPageQuery;
import com.bone.system.application.query.qry.DictTypePageQuery;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 字典的 HTTP ↔ application 转换边界（类型与项共用同一个 assembler）。
 *
 * <p>「更新」要把路径变量 {@code id} 与请求体合并成一个 Command——MapStruct 的 {@code @Mapping} 在这里比手写 set
 * 更少出错：漏字段是编译期（未映射策略）而非运行期的静默 null。
 */
@Mapper
public interface DictAssembler {

  CreateDictTypeCommand toCommand(CreateDictTypeReq req);

  @Mapping(target = "id", source = "id")
  UpdateDictTypeCommand toCommand(Long id, UpdateDictTypeReq req);

  DictTypePageQuery toQuery(DictTypePageReq req);

  CreateDictItemCommand toCommand(CreateDictItemReq req);

  @Mapping(target = "id", source = "id")
  UpdateDictItemCommand toCommand(Long id, UpdateDictItemReq req);

  @Mapping(target = "id", source = "id")
  MoveDictItemCommand toCommand(Long id, MoveDictItemReq req);

  DictItemPageQuery toQuery(DictItemPageReq req);

  DictTypeResp toResp(DictTypeDto dto);

  DictItemResp toResp(DictItemDto dto);

  DictOptionResp toResp(DictOptionDto dto);

  DictItemTextResp toResp(DictItemTextDto dto);

  DictItemTextDto toDto(DictItemTextResp resp);

  DictHierarchyResp toResp(DictHierarchyDto dto);

  DictHierarchyDto toDto(DictHierarchyResp resp);

  DictExportResp toResp(DictExportDto dto);

  DictExportDto toDto(DictExportResp resp);

  /** LocalDateTime → Instant（UTC 归一），对外契约统一带偏移的 ISO-8601（...Z）（i18n 方案 §6.4）。 */
  default Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }

  /** 导入方向：Instant → LocalDateTime，与 {@link #toInstant} 互为逆运算（同用 UTC，不做时区推断）。 */
  default LocalDateTime toLocalDateTime(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
