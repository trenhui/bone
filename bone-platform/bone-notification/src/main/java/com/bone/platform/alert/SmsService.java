package com.bone.platform.alert;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.bone.platform.alert.autoconfigure.AlertProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmsService {

  private static final Logger logger = LoggerFactory.getLogger(SmsService.class);
  private final AlertProperties.SmsConfig smsConfig;
  private final IAcsClient acsClient;

  // 构造方法注入SmsConfig配置并初始化Aliyun短信客户端
  public SmsService(AlertProperties.SmsConfig smsConfig) {
    this.smsConfig = smsConfig;
    this.acsClient = initAliyunSmsClient(); // 初始化客户端
  }

  /**
   * 初始化阿里云短信客户端
   *
   * @return IAcsClient 短信服务客户端
   */
  private IAcsClient initAliyunSmsClient() {
    try {
      IClientProfile profile =
          DefaultProfile.getProfile(
              "cn-hangzhou", // 阿里云区域ID
              smsConfig.getApiKey(), // API Key
              smsConfig.getApiSecret() // API Secret
              );
      DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", "Dysmsapi", "dysmsapi.aliyuncs.com");
      return new DefaultAcsClient(profile); // 返回AcsClient实例
    } catch (Exception e) {
      logger.error("初始化阿里云短信客户端失败", e);
      throw new RuntimeException("阿里云短信客户端初始化失败", e); // 重新抛出异常
    }
  }

  /**
   * 发送短信
   *
   * @param phoneNumber 接收短信的手机号码
   * @param templateId 短信模板ID
   * @param parameters 模板参数，通常是一个 Map，包含模板变量的值
   */
  public void sendSms(String phoneNumber, String templateId, Map<String, String> parameters) {
    try {
      SendSmsRequest request = createSendSmsRequest(phoneNumber, templateId, parameters);
      SendSmsResponse response = acsClient.getAcsResponse(request); // 发送短信请求

      if ("OK".equals(response.getCode())) {
        logger.info("短信发送成功，手机号: {}, 内容: {}", phoneNumber, parameters);
      } else {
        logger.error("短信发送失败，错误码: {}, 错误信息: {}", response.getCode(), response.getMessage());
      }
    } catch (ClientException e) {
      logger.error("阿里云短信发送异常，手机号: {}, 错误信息: {}", phoneNumber, e.getMessage());
    } catch (Exception e) {
      logger.error("未知错误，手机号: {}", phoneNumber, e);
    }
  }

  /**
   * 创建短信请求对象
   *
   * @param phoneNumber 手机号
   * @param templateId 短信模板ID
   * @param parameters 模板参数
   * @return SendSmsRequest 短信请求对象
   */
  private SendSmsRequest createSendSmsRequest(
      String phoneNumber, String templateId, Map<String, String> parameters) {
    SendSmsRequest request = new SendSmsRequest();
    request.setPhoneNumbers(phoneNumber);
    request.setSignName(smsConfig.getProvider()); // 短信签名
    request.setTemplateCode(templateId); // 短信模板ID
    request.setTemplateParam(buildTemplateParam(parameters)); // 格式化的模板参数
    return request;
  }

  /**
   * 构建短信模板参数，格式化为JSON字符串
   *
   * @param parameters 模板参数
   * @return 格式化后的模板参数
   */
  private String buildTemplateParam(Map<String, String> parameters) {
    // 使用更清晰的JSON格式化构建模板参数
    return parameters.toString(); // 可以使用更合适的JSON库（如Jackson）进行格式化
  }
}
