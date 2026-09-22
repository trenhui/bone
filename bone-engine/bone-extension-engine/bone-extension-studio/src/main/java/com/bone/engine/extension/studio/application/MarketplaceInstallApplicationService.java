package com.bone.engine.extension.studio.application;

import com.bone.core.model.ApiResponse;
import com.bone.engine.extension.studio.application.service.StudioAuditService;
import com.bone.engine.extension.studio.application.service.StudioCommandResponses;
import com.bone.engine.extension.studio.domain.gateway.MarketplaceCatalog;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.model.marketplace.MarketplaceItem;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 市场安装命令处理器（详设 §12.2）：把市场条目落地为扩展实现。 */
@Component
@Transactional
@RequiredArgsConstructor
public class MarketplaceInstallApplicationService {

  private final MarketplaceCatalog marketplaceCatalog;
  private final ExtensionCommandApplicationService extensionCommandHandler;
  private final ExtPointRepository extPointRepository;
  private final StudioAuditService auditService;

  public ResponseEntity<ApiResponse<Map<String, Object>>> install(
      String itemId, Long requestedExtPointId) {
    MarketplaceItem item = marketplaceCatalog.findById(itemId).orElse(null);
    if (item == null) {
      return StudioCommandResponses.notFound("市场条目不存在: " + itemId);
    }
    Long extPointId = resolveExtPointId(item, requestedExtPointId);
    if (extPointId == null) {
      return StudioCommandResponses.badRequest(
          "未找到匹配的扩展点（请指定 extPointId 或先注册接口 " + item.extPointInterface() + "）");
    }
    Extension plugin =
        Extension.create(extPointId, item.name(), item.description(), item.className());
    plugin.setConfig(buildConfig(item));
    Extension saved = extensionCommandHandler.saveExtension(plugin);
    auditService.success("marketplace.install", "plugin", String.valueOf(saved.getId()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("pluginId", saved.getId());
    data.put("itemId", item.id());
    data.put("extPointId", extPointId);
    return ResponseEntity.ok(ApiResponse.success("已从市场安装", data));
  }

  private Long resolveExtPointId(MarketplaceItem item, Long requestedExtPointId) {
    if (requestedExtPointId != null) {
      return requestedExtPointId;
    }
    if (item.extPointInterface() == null) {
      return null;
    }
    ExtPoint point = extPointRepository.findByInterfaceName(item.extPointInterface());
    return point != null ? point.getId() : null;
  }

  private static String buildConfig(MarketplaceItem item) {
    StringBuilder sb = new StringBuilder("{");
    sb.append("\"source\":\"marketplace\"");
    sb.append(",\"marketplaceId\":\"").append(escape(item.id())).append('"');
    if (item.version() != null) {
      sb.append(",\"version\":\"").append(escape(item.version())).append('"');
    }
    List<String> tags = item.tags();
    if (tags != null && !tags.isEmpty()) {
      sb.append(",\"tags\":[");
      for (int i = 0; i < tags.size(); i++) {
        if (i > 0) sb.append(',');
        sb.append('"').append(escape(tags.get(i))).append('"');
      }
      sb.append(']');
    }
    sb.append('}');
    return sb.toString();
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
