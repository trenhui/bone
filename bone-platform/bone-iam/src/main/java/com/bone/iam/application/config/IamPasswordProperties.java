package com.bone.iam.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IAM 密码策略与登录锁定配置（详设 IAM-19/IAM-20）。
 *
 * <ul>
 *   <li>{@code maxAgeDays}：密码最长有效期；登录命中策略时返回 {@code requirePasswordChange=true}（&lt;= 0 表示永不过期）
 *   <li>{@code lockoutThreshold}：连续失败几次后锁定（&lt;= 0 表示禁用锁定）
 *   <li>{@code lockoutMinutes}：锁定持续分钟数
 * </ul>
 *
 * /*
 *
 * <p>放在 {@code application.config} 与 {@code OrderOutboxProperties} 一致，使 application 层 无需反向依赖
 * infrastructure。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bone.iam.password")
public class IamPasswordProperties {

  private int maxAgeDays = 90;

  private int lockoutThreshold = 5;

  private int lockoutMinutes = 30;
}
