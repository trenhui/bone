package com.bone.system.application;

import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.CreateDictCommand;
import com.bone.system.application.command.UpdateDictCommand;
import com.bone.system.application.query.dto.DictDto;
import com.bone.system.application.query.qry.DictPageQuery;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.bone.system.domain.model.dict.SysDict;
import com.bone.system.domain.model.dict.vo.DictType;
import com.bone.system.domain.repository.SysDictRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统字典用例入口（写 + 读同一入口，ADR-0028 Application Service First）。
 *
 * <p>字典是典型 CRUD 支撑域（E-3.9「简单 CRUD / 字典 / 配置 → ApplicationService」）：没有跨聚合事务、没有独立 路由或异步入口，原 {@code
 * DictCommandHandler} / {@code DictQueryHandler} 两件套不具备独立的事务边界或生命周期， 属 E-3.2 定义的 Ceremonial
 * Architecture，故收敛为单一入口。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictApplicationService {

  private final SysDictRepository sysDictRepository;
  private final DomainEventPublisher domainEventPublisher;

  @Capability(
      name = "ManageDict",
      description = "字典增删改",
      inputSchema =
          "{\"type\": \"string\", \"typeName\": \"string\", \"code\": \"string\", \"label\":"
              + " \"string\", \"value\": \"string\", \"sort\": \"int\", \"status\": \"int\"}",
      outputSchema = "{\"dictId\": \"long\"}",
      idempotent = false,
      cost = 1,
      retryable = true,
      timeout = 5)
  @Transactional
  public Long create(CreateDictCommand command) {
    DictType type = parseDictType(command.getType());
    if (sysDictRepository.findByTypeAndCode(type, command.getCode()).isPresent()) {
      throw SystemErrors.of(
          SystemErrorCodes.DICT_CODE_CONFLICT, type.value() + "/" + command.getCode());
    }
    SysDict dict =
        SysDict.create(
            DistributedIdGenerator.generateLongId(),
            type,
            command.getTypeName(),
            command.getCode(),
            command.getLabel(),
            command.getValue(),
            command.getSort(),
            command.getStatus());
    sysDictRepository.save(dict);
    domainEventPublisher.publishFrom(dict);
    return dict.getId();
  }

  @Transactional
  public void update(UpdateDictCommand command) {
    SysDict dict = requireDict(command.getId());
    dict.update(
        command.getTypeName(),
        command.getLabel(),
        command.getValue(),
        command.getSort(),
        command.getStatus());
    sysDictRepository.save(dict);
    domainEventPublisher.publishFrom(dict);
  }

  /** 删除字典项；不存在视为已删除（幂等，见 {@code deleteById} 自身语义）。 */
  @Transactional
  public void delete(Long id) {
    sysDictRepository.deleteById(id);
  }

  /** 按主键读取。 */
  public Optional<DictDto> getById(Long id) {
    return Optional.ofNullable(sysDictRepository.findById(id)).map(DictDto::from);
  }

  /** 按类型 + 编码读取（前端按业务键取字典项）。 */
  public Optional<DictDto> getByTypeAndCode(String type, String code) {
    return sysDictRepository.findByTypeAndCode(parseDictType(type), code).map(DictDto::from);
  }

  /** 某类型下的全部字典项（下拉框数据源，不分页）。 */
  public List<DictDto> listByType(String type) {
    return sysDictRepository.findByType(parseDictType(type)).stream().map(DictDto::from).toList();
  }

  /** 字典项分页：类型精确 + 关键字模糊。 */
  public PageResult<DictDto> page(DictPageQuery query) {
    PageResult<SysDict> page =
        sysDictRepository.pageByTypeAndKeyword(
            parseDictTypeOrNull(query.getType()),
            query.getKeyword(),
            query.getPageNum(),
            query.getPageSize());
    return PageResult.of(
        page.getRecords().stream().map(DictDto::from).toList(),
        page.getTotal(),
        page.getPage(),
        page.getSize());
  }

  private SysDict requireDict(Long id) {
    SysDict dict = sysDictRepository.findById(id);
    if (dict == null) {
      throw SystemErrors.of(SystemErrorCodes.DICT_NOT_FOUND, id);
    }
    return dict;
  }

  /**
   * 字典类型解析：{@code DictType} 对空值/超长抛 {@code BizException}（默认码为 500），这里翻译成 400。
   *
   * <p>为什么在应用层翻译而不是改值对象：值对象保护的是「不变量」，它不知道自己是 HTTP 参数还是定时任务入参； 「哪种失败对应哪个状态码」是应用层契约（E-7）。
   */
  private static DictType parseDictType(String value) {
    try {
      return DictType.of(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.DICT_TYPE_INVALID, value);
    }
  }

  /** 分页过滤用：类型可缺省，缺省时不传 {@code null} 之外的东西给仓储。 */
  private static DictType parseDictTypeOrNull(String value) {
    return value == null || value.isBlank() ? null : parseDictType(value);
  }
}
