package com.bone.system.adapter.web.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 配置响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigResp {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private String configType;
    private boolean encrypted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
