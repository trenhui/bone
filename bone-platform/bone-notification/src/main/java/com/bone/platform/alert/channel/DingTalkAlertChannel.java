package com.bone.platform.alert.channel;

import com.bone.platform.alert.AlertChannel;
import com.bone.platform.alert.AlertChannelType;
import com.bone.platform.alert.AlertException;
import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.autoconfigure.AlertProperties;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class DingTalkAlertChannel implements AlertChannel {
  private static final String DINGTALK_API_URL = "https://oapi.dingtalk.com/robot/send";
  private final AlertProperties.DingTalkConfig config;
  private final RestTemplate restTemplate;

  public DingTalkAlertChannel(AlertProperties.DingTalkConfig config) {
    this.config = config;
    this.restTemplate = new RestTemplate();
    this.restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
  }

  @Override
  public AlertChannelType channelType() {
    return AlertChannelType.DINGTALK;
  }

  @Override
  public void send(AlertMessage message) {
    try {
      String timestamp = String.valueOf(System.currentTimeMillis());
      String sign = generateSign(timestamp);

      HttpHeaders headers = createHeaders();

      Map<String, Object> body = createRequestBody(message);

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
      String url = buildRequestUrl(timestamp, sign);

      ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
      handleResponse(response, message);
    } catch (Exception e) {
      log.error("Error sending DingTalk alert", e);
      throw new AlertException("DingTalk alert failed", e);
    }
  }

  private HttpHeaders createHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private Map<String, Object> createRequestBody(AlertMessage message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("msgtype", "markdown");

    Map<String, String> markdown = new HashMap<>();
    markdown.put("title", message.getTitle());
    markdown.put("text", buildDingTalkContent(message));
    body.put("markdown", markdown);

    if (!CollectionUtils.isEmpty(config.getAtMobiles())) {
      Map<String, Object> at = new HashMap<>();
      at.put("atMobiles", config.getAtMobiles());
      at.put("isAtAll", false);
      body.put("at", at);
    }

    return body;
  }

  private String buildRequestUrl(String timestamp, String sign) {
    return UriComponentsBuilder.fromHttpUrl(DINGTALK_API_URL)
        .queryParam("access_token", extractTokenFromWebhook(config.getWebhook()))
        .queryParam("timestamp", timestamp)
        .queryParam("sign", sign)
        .toUriString();
  }

  private String extractTokenFromWebhook(String webhook) {
    return webhook.split("=")[1];
  }

  private void handleResponse(ResponseEntity<Map> response, AlertMessage message) {
    if (response.getStatusCode() != HttpStatus.OK) {
      throw new AlertException("DingTalk API returned " + response.getStatusCode());
    }

    if (response.getBody() != null
        && !"0".equals(String.valueOf(response.getBody().get("errcode")))) {
      throw new AlertException("DingTalk error: " + response.getBody());
    }

    log.debug("DingTalk alert sent successfully, message ID: {}", message.getId());
  }

  private String generateSign(String timestamp)
      throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
    String stringToSign = timestamp + "\n" + config.getSecret();
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(config.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
    return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), "UTF-8");
  }

  private String buildDingTalkContent(AlertMessage alertMessage) {
    return """
               ### %s告警
               **业务ID**: %s
               **时间**: %s
               **详情**:
               %s
               """
        .formatted(
            alertMessage.getLevel().name(),
            alertMessage.getBusinessId(),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            alertMessage.getContent());
  }
}
