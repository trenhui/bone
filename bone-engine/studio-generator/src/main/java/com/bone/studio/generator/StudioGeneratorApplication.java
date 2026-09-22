package com.bone.studio.generator;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.studio.generator.config.GeneratorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@SpringBootApplication(
    exclude = {
      org.springframework.boot.actuate.autoconfigure.jdbc
          .DataSourceHealthContributorAutoConfiguration.class,
      org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
    })
@EnableConfigurationProperties(GeneratorProperties.class)
@ComponentScan(
    basePackages = {
      "com.bone.studio.generator",
      "com.bone.metadata.sdk",
      "com.bone.core.capability"
    })
@EnableSqlRepositories(basePackages = {"com.bone.studio.generator.domain.repository"})
public class StudioGeneratorApplication {

  public static void main(String[] args) {
    SpringApplication.run(StudioGeneratorApplication.class, args);
  }

  @Bean
  public CorsFilter corsFilter() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin("*");
    config.addAllowedMethod("*");
    config.addAllowedHeader("*");
    source.registerCorsConfiguration("/api/**", config);
    return new CorsFilter(source);
  }
}
