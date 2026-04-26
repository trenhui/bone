package com.bone.tpa.core.util.kuaitong;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kt")
public class KTProperties {

    private String ACCESS_KEY = "APPID_6Gf78H59D3O2Q81u";

    private String ACCESS_SECRET = "947b8829d4d5d55890b304d322ac2d0d";
}
