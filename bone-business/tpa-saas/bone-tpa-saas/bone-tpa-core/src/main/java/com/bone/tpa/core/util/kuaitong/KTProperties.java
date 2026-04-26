package com.bone.tpa.core.util.kuaitong;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kt")
public class KTProperties {

    private String ACCESS_KEY = "REDACTED_KT_ACCESS_KEY";

    private String ACCESS_SECRET = "REDACTED_KT_ACCESS_SECRET";
}
