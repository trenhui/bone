package com.bone.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author renhui.trh
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class MetadataApplication {
  public static void main(String[] args) {
    SpringApplication.run(MetadataApplication.class, args);
  }
}
