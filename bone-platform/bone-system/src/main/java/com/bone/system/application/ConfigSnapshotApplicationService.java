package com.bone.system.application;

import com.bone.system.application.command.ConfigSnapshotImportResult;
import com.bone.system.application.command.CreateConfigCommand;
import com.bone.system.application.command.UpdateConfigCommand;
import com.bone.system.application.query.dto.ConfigDto;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置快照导出 / 恢复（MVP-09「配置（含功能开关）」）。
 *
 * <p><b>为什么加密项只导出脱敏值</b>：{@link ConfigDto} 对 {@code encrypted=true} 的配置统一返回脱敏串，
 * 因此快照里不含任何密文明文——导出文件外泄也不会泄露密钥。代价是<b>加密项无法从快照恢复</b>：导入时逐条跳过并 记入 {@code
 * reasons}，由页面提示用户手工补录。若图省事把脱敏串原样写回，等于把密钥变成字面量且没有任何报错， 属静默数据损坏。
 *
 * <p><b>幂等</b>：按 {@code configKey} 做 upsert——已存在则更新值 / 描述，不存在则创建。重复导入同一份快照 不会产生重复键，也不会撞上「配置键已存在」冲突。
 *
 * <p><b>为何它是独立用例而不是 {@code ConfigApplicationService} 的一个方法</b>：它有自己的执行语义—— 一次导入最多 {@value
 * #MAX_IMPORT_ITEMS} 条、逐条容错并汇总 {@code reasons}，把它塞进后者会让「单条 CRUD」 与「批量 upsert」两种事务粒度混在一个类里（E-3.8
 * 拆分标准：按是否共享同一组依赖与事务语义拆）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigSnapshotApplicationService {

  /** 单批导入条目上限：配置量级有限，封顶只为防止超大文件把一次事务拖垮。 */
  private static final int MAX_IMPORT_ITEMS = 2000;

  private final ConfigApplicationService configApplicationService;
  private final ObjectMapper objectMapper;

  /** 导出全量配置快照（JSON 字节）。 */
  @Transactional(readOnly = true)
  public byte[] exportSnapshot() {
    List<ConfigDto> configs = configApplicationService.listAll();
    ObjectNode root = objectMapper.createObjectNode();
    root.put("version", 1);
    root.put("exportedAt", LocalDateTime.now().toString());
    root.put("count", configs.size());
    ArrayNode arr = root.putArray("configs");
    for (ConfigDto c : configs) {
      ObjectNode node = arr.addObject();
      node.put("configKey", c.getConfigKey());
      node.put("configValue", c.getConfigValue());
      node.put("description", c.getDescription());
      node.put("configType", c.getConfigType());
      node.put("encrypted", c.isEncrypted());
    }
    return root.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 从快照恢复配置。
   *
   * @param json 快照内容（{@link #exportSnapshot()} 的产物，或同结构的手编文件）
   */
  @Transactional
  public ConfigSnapshotImportResult importSnapshot(String json) {
    if (json == null || json.isBlank()) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_SNAPSHOT_EMPTY);
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (Exception e) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_SNAPSHOT_INVALID, e.getMessage());
    }
    JsonNode arr = root == null ? null : root.get("configs");
    if (arr == null || !arr.isArray()) {
      throw SystemErrors.of(SystemErrorCodes.CONFIG_SNAPSHOT_INVALID, "缺少 configs 数组");
    }
    if (arr.size() > MAX_IMPORT_ITEMS) {
      throw SystemErrors.of(
          SystemErrorCodes.CONFIG_SNAPSHOT_TOO_LARGE, arr.size() + " > " + MAX_IMPORT_ITEMS);
    }

    int created = 0;
    int updated = 0;
    List<String> reasons = new ArrayList<>();
    for (JsonNode item : arr) {
      String key = item.path("configKey").asText(null);
      if (key == null || key.isBlank()) {
        reasons.add("缺少 configKey，已跳过");
        continue;
      }
      if (item.path("encrypted").asBoolean(false)) {
        reasons.add(key + "：加密配置无法从快照恢复（快照内为脱敏值），请手工补录");
        continue;
      }
      String value = item.path("configValue").asText(null);
      if (value == null) {
        reasons.add(key + "：缺少 configValue，已跳过");
        continue;
      }
      String description = item.path("description").asText(null);
      String configType = item.path("configType").asText("STRING");
      ConfigDto existing = configApplicationService.getByKey(key).orElse(null);
      if (existing == null) {
        configApplicationService.create(toCreateCommand(key, value, description, configType));
        created++;
      } else {
        configApplicationService.update(toUpdateCommand(existing.getId(), value, description));
        updated++;
      }
    }
    log.info("配置快照导入完成：created={}, updated={}, skipped={}", created, updated, reasons.size());
    return ConfigSnapshotImportResult.builder()
        .created(created)
        .updated(updated)
        .skipped(reasons.size())
        .reasons(reasons)
        .build();
  }

  private static CreateConfigCommand toCreateCommand(
      String key, String value, String description, String configType) {
    CreateConfigCommand command = new CreateConfigCommand();
    command.setConfigKey(key);
    command.setConfigValue(value);
    command.setDescription(description);
    command.setConfigType(configType);
    command.setEncrypted(false);
    return command;
  }

  /**
   * upsert 的更新分支：用已存在配置的主键定位。
   *
   * <p>为何不直接按 {@code configKey} 更新：{@code UpdateConfigCommand} 的定位键是主键，而 「按 key 查 id」这一次查询已经在上一步
   * upsert 判定中做过，这里复用结果即可，不要再引入第二条定位语义。
   */
  private static UpdateConfigCommand toUpdateCommand(Long id, String value, String description) {
    UpdateConfigCommand command = new UpdateConfigCommand();
    command.setId(id);
    command.setConfigValue(value);
    command.setDescription(description);
    return command;
  }
}
