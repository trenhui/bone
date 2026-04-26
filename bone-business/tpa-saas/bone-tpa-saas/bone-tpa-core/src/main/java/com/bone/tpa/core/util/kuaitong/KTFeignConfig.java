package com.bone.tpa.core.util.kuaitong;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

//使Feign支持文件上传场景的编码转换
@Configuration
public class KTFeignConfig {
    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder(new SpringEncoder(
            () -> new HttpMessageConverters(
                new RestTemplate().getMessageConverters()
            )
        ));
    }
}
