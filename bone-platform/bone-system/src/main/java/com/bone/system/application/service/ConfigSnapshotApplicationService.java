package com.bone.system.application.service;

import com.bone.core.exception.BizException;
import com.bone.system.application.command.cmd.CreateConfigCommand;
import com.bone.system.application.command.cmd.UpdateConfigCommand;
import com.bone.system.application.command.handler.ConfigCommandHandler;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.handler.ConfigQueryHandler;
import com.bone.system.application.service.dto.ConfigSnapshotImportResult;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置快照导出 / 恢复（MVP-09「配置（含功能开关）」）。
 *
 * <p><b>为什么加密项只导出脱敏值</b>：{@link ConfigDTO} 对 {@code encrypted=true} 的配置统一返回 {@code ******}，
 * 因此快照里不含任何密文明文——导出文件外泄也不会泄露密钥。代价是<b>加密项无法从快照恢复</b>：导入时逐条跳过并 记入 {@code reasons}，由页面提示用户手工补录。若图省事把
 * {@code ******} 原样写回，等于把密钥变成字面量 且没有任何报错，属静默数据损坏。
 *
 * <p><b>幂等</b>：按 {@code configKey} 做 upsert——已存在则更新值 / 描述，不存在则创建。重复导入同一份快照 不会产生重复键，也不会撞上「配置键已存在」异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigSnapshotApplicationService {

  /** 单批导入条目上限：配置量级有限，封顶只为防止超大文件把一次事务拖垮。 */
  private static final int MAX_IMPORT_ITEMS = 2000;

  private final ConfigQueryHandler configQueryHandler;
  private final ConfigCommandHandler configCommandHandler;
  private final ObjectMapper objectMapper;

  /** 导出全量配置快照（JSON 字节）。 */
  @Transactional(readOnly = true)
  public byte[] exportSnapshot() {
    List<ConfigDTO> configs = configQueryHandler.listAll();
    ObjectNode root = objectMapper.createObjectNode();
    root.put("version", 1);
    root.put("exportedAt", LocalDateTime.now().toString());
    root.put("count", configs.size());
    ArrayNode arr = root.putArray("configs");
    for (ConfigDTO c : configs) {
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
      throw BizException.of("配置快照内容为空");
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (Exception e) {
      throw BizException.of("配置快照解析失败: " + e.getMessage());
    }
    JsonNode arr = root == null ? null : root.get("configs");
    if (arr == null || !arr.isArray()) {
      throw BizException.of("配置快照格式不正确：缺少 configs 数组");
    }
    if (arr.size() > MAX_IMPORT_ITEMS) {
      throw BizException.of("配置快照条目过多: " + arr.size() + "，单次上限 " + MAX_IMPORT_ITEMS);
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
      ConfigDTO existing = configQueryHandler.getByKey(key);
      if (existing == null) {
        CreateConfigCommand cmd = new CreateConfigCommand();
        cmd.setConfigKey(key);
        cmd.setConfigValue(value);
        cmd.setDescription(description);
        cmd.setConfigType(item.path("configType").asText("STRING"));
        cmd.setEncrypted(false);
        configCommandHandler.handle(cmd);
        created++;
      } else {
        UpdateConfigCommand cmd = new UpdateConfigCommand();
        cmd.setId(existing.getId());
        cmd.setConfigValue(value);
        cmd.setDescription(description);
        configCommandHandler.handle(cmd);
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
}
