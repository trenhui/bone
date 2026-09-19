package com.bone.masterdata.infrastructure.gateway;

import com.bone.masterdata.domain.gateway.MetadataClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MetadataClientAdapter implements MetadataClient {
  @Override
  public boolean convertFromBusinessEntity(Long businessEntityId) {
    log.info("从业务实体转换为主数据实体: businessEntityId={}", businessEntityId);
    // 模拟实现，实际应该调用元数据服务
    return true;
  }

  @Override
  public String getBusinessEntityInfo(Long businessEntityId) {
    log.info("获取业务实体信息: businessEntityId={}", businessEntityId);
    // 模拟实现，实际应该调用元数据服务
    return "{\"id\": " + businessEntityId + ", \"name\": \"测试业务实体\"}";
  }
}
