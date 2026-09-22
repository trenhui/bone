package com.bone.system.application;

import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.CreateConfigCommand;
import com.bone.system.application.command.UpdateConfigCommand;
import com.bone.system.application.query.dto.ConfigDto;
import com.bone.system.application.query.qry.ConfigPageQuery;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.valueobject.ConfigKey;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import com.bone.system.domain.model.config.valueobject.ConfigValue;
import com.bone.system.domain.repository.SystemConfigRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统配置用例入口（写 + 读同一入口，ADR-0028 Application Service First）。
 *
 * <p><b>为何收敛到这里</b>：原 {@code ConfigCommandHandler} + {@code ConfigQueryHandler} 各自持有同一个 {@code
 * SystemConfigRepository}，Handler 之间没有独立的事务边界、路由或生命周期——典型的两件套夫套（E-3.2 Ceremonial Architecture）。配置的写
 * donors 前后都需要读同一聚合，合成一个类反而让依赖与事务语义看得见。
 *
 * <p><b>operator 为什么是常量</b>：{@code updateValue} 的第二个参数是审计用的「谁改的」，当前编排成员的 JWT 未贯穿到应用层（仅用于网关鉴权），故保留
 * {@link #OPERATOR} 占位。接入 {@code TenantPort} / 当前账号后改这里， 不要把责任推给调用方再传一次 id。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfigApplicationService {

  private final SystemConfigRepository systemConfigRepository;
  private final DomainEventPublisher domainEventPublisher;

  /** 尚未接入操作人上下文前的审计占位符。 */
  private static final String OPERATOR = "admin";

  @Capability(
      name = "CreateSystemConfig",
      description = "创建系统配置",
      inputSchema =
          "{\"configKey\": \"string\", \"configValue\": \"string\", \"configType\": \"string\","
              + " \"description\": \"string\", \"encrypted\": \"boolean\"}",
      outputSchema = "{\"configId\": \"long\"}",
      idempotent = false,
      cost = 2,
      retryable = true,
      timeout = 15)
  @Transactional
  public Long create(CreateConfigCommand command) {
    ConfigKey configKey = parseConfigKey(command.getConfigKey());
    if (systemConfigRepository.findByConfigKey(configKey).isPresent()) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_KEY_CONFLICT, command.getConfigKey());
    }
    SystemConfig config =
        SystemConfig.create(
            DistributedIdGenerator.generateLongId(),
            configKey,
            ConfigValue.of(command.getConfigValue()),
            command.getDescription(),
            parseConfigType(command.getConfigType()),
            command.isEncrypted());
    systemConfigRepository.save(config);
    domainEventPublisher.publishFrom(config);
    return config.getId();
  }

  /** 更新配置值 / 描述；两者都可缺省，缺省项保持原值（PATCH 语义）。 */
  @Transactional
  public void update(UpdateConfigCommand command) {
    SystemConfig config = requireConfig(command.getId());
    if (command.getConfigValue() != null) {
      config.updateValue(ConfigValue.of(command.getConfigValue()), OPERATOR);
    }
    if (command.getDescription() != null) {
      config.updateDescription(command.getDescription());
    }
    systemConfigRepository.save(config);
    domainEventPublisher.publishFrom(config);
  }

  /**
   * 删除配置；不存在视为已删除（幂等）。
   *
   * <p>为何不「先查再删」：{@code deleteById} 对不存在的行返回 {@code false}，幂等由它自己保证；先查一次 既多余，又在检查与删除之间留出竞态窗口（E-4.1
   * 明令禁止用读结果决定写动作）。
   */
  @Transactional
  public void delete(Long id) {
    systemConfigRepository.deleteById(id);
    log.debug("[Config] 删除配置 id={}, removed={}", id, id);
  }

  /** 按主键读取；不存在返回 {@code Optional.empty()}（由 adapter 决定 404 还是 null body）。 */
  public Optional<ConfigDto> getById(Long id) {
    return Optional.ofNullable(systemConfigRepository.findById(id)).map(ConfigDto::from);
  }

  /** 按配置键读取。 */
  public Optional<ConfigDto> getByKey(String key) {
    return systemConfigRepository.findByConfigKey(parseConfigKey(key)).map(ConfigDto::from);
  }

  /** 关键字分页（命中配置键或描述）+ 配置类型精确过滤。 */
  public PageResult<ConfigDto> page(ConfigPageQuery query) {
    PageResult<SystemConfig> page =
        systemConfigRepository.pageByKeyword(
            query.getKeyword(),
            parseConfigTypeOrNull(query.getConfigType()),
            query.getPageNum(),
            query.getPageSize());
    return mapPage(page);
  }

  /**
   * 全量配置（快照导出用）。
   *
   * <p>不分页的取舍见 {@link SystemConfigRepository#findAllOrderedByKey()}：配置是业务配置项而非流水数据。
   */
  public List<ConfigDto> listAll() {
    return systemConfigRepository.findAllOrderedByKey().stream().map(ConfigDto::from).toList();
  }

  private SystemConfig requireConfig(Long id) {
    SystemConfig config = systemConfigRepository.findById(id);
    if (config == null) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_NOT_FOUND, id);
    }
    return config;
  }

  /**
   * 把 {@code ConfigKey} / {@code ConfigType} 构造抛出的 {@code DomainException} 翻译成 400。
   *
   * <p>为何必须翻译：值对象保护的是不变量，它不知道自己来自 HTTP 参数还是定时任务入参；「哪种失败对应哪个状态码」 是应用层契约（E-7）。任由 {@code
   * DomainException} 冒泡，用户把 {@code configType} 写错一个字母就看到一个含义不明的失败，监控也会把参数错误计入可用性。
   */
  private static ConfigKey parseConfigKey(String value) {
    try {
      return ConfigKey.of(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_KEY_INVALID, value);
    }
  }

  private static ConfigType parseConfigType(String value) {
    try {
      return ConfigType.fromString(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_TYPE_INVALID, value);
    }
  }

  /** 过滤条件可缺省：这里 null 表示「不按类型过滤」，空串是合法缺省值，不要把它翻译成 400。 */
  private static ConfigType parseConfigTypeOrNull(String value) {
    return value == null || value.isBlank() ? null : parseConfigType(value);
  }

  private static PageResult<ConfigDto> mapPage(PageResult<SystemConfig> page) {
    return PageResult.of(
        page.getRecords().stream().map(ConfigDto::from).toList(),
        page.getTotal(),
        page.getPage(),
        page.getSize());
  }
}
