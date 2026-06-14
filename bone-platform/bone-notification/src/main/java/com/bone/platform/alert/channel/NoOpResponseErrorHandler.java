package com.bone.platform.alert.channel;

import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

/** 自定义的 NoOpResponseErrorHandler，忽略所有 HTTP 错误。 */
public class NoOpResponseErrorHandler implements ResponseErrorHandler {

  @Override
  public boolean hasError(ClientHttpResponse response) throws IOException {
    // 始终返回 false，表示没有错误
    return false;
  }

  @Override
  public void handleError(ClientHttpResponse response) throws IOException {
    // 空实现，不处理任何错误
  }
}
