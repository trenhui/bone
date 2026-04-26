package com.bone.tpa.facade;

import com.bone.metadata.sdk.metafeign.JsonDecoder;
import com.bone.metadata.sdk.metafeign.JsonEncoder;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.springframework.context.annotation.Bean;

public class FeignTpaConfig {

    @Bean
    public Decoder getDecoder(){
        return  new JsonDecoder();
    }
    @Bean
    public Encoder getEncoder(){
        return  new JsonEncoder();
    }
}
