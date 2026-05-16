package com.bone.tpa.core.util.kuaitong;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kt")
public class KTProperties {

    private String accessKey = "";

    private String accessSecret = "";
}
