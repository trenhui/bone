package com.bone.integration.flow.camel;

import cn.hutool.crypto.SecureUtil;
import com.bone.integration.application.dto.MockDTO;
import com.bone.integration.application.service.IMockService;
import com.bone.integration.uitls.WebApplicationContextUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.component.http.HttpProducer;
import org.apache.camel.component.http.helper.HttpMethodHelper;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;

import static org.apache.camel.builder.Builder.simple;

public class ExtendHttpProducer extends HttpProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ExtendHttpProducer.class);

    public ExtendHttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getMessage();

        String isMock = message.getHeader("_mock_", String.class);
        if ("true".equalsIgnoreCase(isMock)) {
            String url = this.getEndpoint().getHttpUri().toString();

            IMockService mockService = WebApplicationContextUtils.getBean(IMockService.class);
            String appCode = message.getHeader("appCode", String.class);
            MockDTO mockDTO = mockService.getByMockKey(getMockKey(appCode, url));
            if (mockDTO == null) {
                super.process(exchange);
                return;
            }

            setUrlParams(message, url);

            List<MockDTO.MockCondition> mockConditionList = mockDTO.getConditions();
            MockDTO.MockCondition matchCondition = findMatchCondition(exchange, mockConditionList);

            String mockResponse = matchCondition.getResponse().getBody();
            message.setBody(mockResponse);

            setResponseHeader(message, matchCondition);

        } else {
            super.process(exchange);
        }
    }

    @Override
    protected HttpUriRequest createMethod(Exchange exchange) throws Exception {
        String defaultUrl = (String) getFieldValue("defaultUrl", this);
        URI defaultUri = (URI) getFieldValue("defaultUri", this);

        if (defaultUri == null || defaultUrl == null) {
            throw new IllegalArgumentException("Producer must be started");
        }
        String url = defaultUrl;
        URI uri = defaultUri;

        // create http holder objects for the request
        HttpMethods methodToUse = HttpMethodHelper.createMethod(exchange, getEndpoint());
        HttpUriRequest method = methodToUse.createMethod(uri);

        // special for HTTP DELETE/GET if the message body should be included
        if (getEndpoint().isDeleteWithBody() && "DELETE".equals(method.getMethod())
                || getEndpoint().isGetWithBody() && "GET".equals(method.getMethod())) {
            method.setEntity(createRequestEntity(exchange));
        }

        LOG.trace("Using URL: {} with method: {}", url, method);

        if (methodToUse.isEntityEnclosing()) {
            // only create entity for http payload if the HTTP method carries payload (such as POST)
            HttpEntity requestEntity = createRequestEntity(exchange);
            method.setEntity(requestEntity);
            if (requestEntity != null && requestEntity.getContentType() == null) {
                LOG.debug("No Content-Type provided for URL: {} with exchange: {}", url, exchange);
            }
        }

        // there must be a host on the method
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "Invalid url: " + url + ". If you are forwarding/bridging http endpoints, then enable the bridgeEndpoint option on the endpoint: "
                            + getEndpoint());
        }

        return method;
    }

    private String getMockKey(String appCode, String url) {
        int index = url.indexOf('?');
        String path = url;
        if (index != -1) {
            path = url.substring(0, index);
        }

        return SecureUtil.md5(appCode + "|" + path);
    }

    private MockDTO.MockCondition findMatchCondition(Exchange exchange, List<MockDTO.MockCondition> mockConditionList) {
        MockDTO.MockCondition matchCondition = null;
        MockDTO.MockCondition elseCondition = null;
        if (mockConditionList.size() == 1) {
            matchCondition = mockConditionList.get(0);
        } else {
            for (MockDTO.MockCondition mockCondition : mockConditionList) {
                String expression = mockCondition.getExpression();
                if ("else".equalsIgnoreCase(mockCondition.getConditionType())) {
                    elseCondition = mockCondition;
                    continue;
                }

                boolean isMatch = simple(expression).matches(exchange);
                if (isMatch) {
                    matchCondition = mockCondition;
                }
            }
        }

        if (matchCondition == null) {
            matchCondition = elseCondition;
        }

        return matchCondition;
    }

    private void setUrlParams(Message message, String url) throws Exception {
        URIBuilder uriBuilder = new URIBuilder(url);

        List<NameValuePair> params = uriBuilder.getQueryParams();
        for (NameValuePair param : params) {
            message.setHeader(param.getName(), param.getValue());
        }
    }

    private void setResponseHeader(Message message, MockDTO.MockCondition matchCondition) {
        List<MockDTO.ResponseHeader> responseHeaderList = matchCondition.getResponse().getResponseHeader();
        for (MockDTO.ResponseHeader responseHeader : responseHeaderList) {
            if (!StringUtils.hasText(responseHeader.getKey())) {
                continue;
            }

            message.setHeader(responseHeader.getKey(), responseHeader.getValue());
        }
    }

    private Object getFieldValue(String fieldName, Object object) throws Exception {
        Field field = ReflectionUtils.findField(object.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
}
