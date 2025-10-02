package com.bone.lowcode.integration.flow.camel;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Endpoint;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpUtil;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ExtendHttpComponent extends HttpComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Map<String, Object> httpClientParameters = new HashMap(parameters);
        Map<String, Object> httpClientOptions = new HashMap();
        Timeout valConnectionRequestTimeout = (Timeout)this.getAndRemoveParameter(parameters, "connectionRequestTimeout", Timeout.class, this.connectionRequestTimeout);
        if (!Timeout.ofMinutes(3L).equals(valConnectionRequestTimeout)) {
            httpClientOptions.put("connectionRequestTimeout", valConnectionRequestTimeout);
        }

        Timeout valResponseTimeout = (Timeout)this.getAndRemoveParameter(parameters, "responseTimeout", Timeout.class, this.responseTimeout);
        if (!Timeout.ofMilliseconds(0L).equals(valResponseTimeout)) {
            httpClientOptions.put("responseTimeout", valResponseTimeout);
        }

        Timeout valConnectTimeout = (Timeout)this.getAndRemoveParameter(parameters, "connectTimeout", Timeout.class, this.connectTimeout);
        if (!Timeout.ofMinutes(3L).equals(valConnectTimeout)) {
            httpClientOptions.put("connectTimeout", valConnectTimeout);
        }

        Map<String, Object> httpConnectionOptions = new HashMap();
        Timeout valSoTimeout = (Timeout)this.getAndRemoveParameter(parameters, "soTimeout", Timeout.class, this.soTimeout);
        if (!Timeout.ofMinutes(3L).equals(valSoTimeout)) {
            httpConnectionOptions.put("soTimeout", valSoTimeout);
        }

        HttpBinding httpBinding = (HttpBinding)this.resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        HttpContext httpContext = (HttpContext)this.resolveAndRemoveReferenceParameter(parameters, "httpContext", HttpContext.class);
        SSLContextParameters sslContextParameters = (SSLContextParameters)this.resolveAndRemoveReferenceParameter(parameters, "sslContextParameters", SSLContextParameters.class);
        if (sslContextParameters == null) {
            sslContextParameters = this.getSslContextParameters();
        }

        if (sslContextParameters == null) {
            boolean secure = HttpHelper.isSecureConnection(uri);
            if (secure) {
                sslContextParameters = this.retrieveGlobalSslContextParameters();
            }
        }

        String httpMethodRestrict = (String)this.getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        boolean muteException = (Boolean)this.getAndRemoveParameter(parameters, "muteException", Boolean.TYPE, this.isMuteException());
        HeaderFilterStrategy headerFilterStrategy = (HeaderFilterStrategy)this.resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        String secureProtocol = uri;
        if (remaining.startsWith("http:") || remaining.startsWith("https:")) {
            secureProtocol = remaining;
        }

        boolean secure = isSecureConnection(secureProtocol) || sslContextParameters != null;
        remaining = HttpUtil.removeHttpOrHttpsProtocol(remaining);
        String addressUri = (secure ? "https://" : "http://") + remaining;
        addressUri = UnsafeUriCharactersEncoder.encodeHttpURI(addressUri);
        URI uriHttpUriAddress = new URI(addressUri);
        String scheme = StringHelper.before(uri, "://");
        uri = HttpUtil.removeHttpOrHttpsProtocol(uri);
        HttpClientConfigurer configurer = this.createHttpClientConfigurer(parameters, secure);
        URI endpointUri = URISupport.createRemainingURI(uriHttpUriAddress, httpClientParameters);
        endpointUri = URISupport.createRemainingURI(new URI(scheme, endpointUri.getUserInfo(), endpointUri.getHost(), endpointUri.getPort(), endpointUri.getPath(), endpointUri.getQuery(), endpointUri.getFragment()), httpClientParameters);
        String endpointUriString = endpointUri.toString();
        log.debug("Creating endpoint uri {}", endpointUriString);
        HttpClientConnectionManager localConnectionManager = this.createConnectionManager(parameters, sslContextParameters, httpConnectionOptions);
        HttpClientBuilder clientBuilder = this.createHttpClientBuilder(uri, parameters, httpClientOptions);
        HttpEndpoint endpoint = new ExtendHttpEndpoint(endpointUriString, this, clientBuilder, localConnectionManager, configurer);
        endpoint.setResponseTimeout(valResponseTimeout);
        endpoint.setSoTimeout(valSoTimeout);
        endpoint.setConnectTimeout(valConnectTimeout);
        endpoint.setConnectionRequestTimeout(valConnectionRequestTimeout);
        endpoint.setCopyHeaders(this.copyHeaders);
        endpoint.setSkipRequestHeaders(this.skipRequestHeaders);
        endpoint.setSkipResponseHeaders(this.skipResponseHeaders);
        endpoint.setUserAgent(this.userAgent);
        endpoint.setMuteException(muteException);
        if (this.getHttpConfiguration() != null) {
            Map<String, Object> properties = new HashMap();
            BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(this.getCamelContext());
            beanIntrospection.getProperties(this.getHttpConfiguration(), properties, (String)null);
            this.setProperties(endpoint, properties);
        }

        this.setProperties(endpoint, parameters);
        URI httpUri = URISupport.createRemainingURI(new URI(uriHttpUriAddress.getScheme(), uriHttpUriAddress.getUserInfo(), uriHttpUriAddress.getHost(), uriHttpUriAddress.getPort(), uriHttpUriAddress.getPath(), uriHttpUriAddress.getQuery(), uriHttpUriAddress.getFragment()), parameters);
        endpoint.setHttpUri(httpUri);
        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        } else {
            this.setEndpointHeaderFilterStrategy(endpoint);
        }

        endpoint.setHttpBinding(this.getHttpBinding());
        if (httpBinding != null) {
            endpoint.setHttpBinding(httpBinding);
        }

        if (httpMethodRestrict != null) {
            endpoint.setHttpMethodRestrict(httpMethodRestrict);
        }

        endpoint.setHttpContext(this.getHttpContext());
        if (httpContext != null) {
            endpoint.setHttpContext(httpContext);
        }

        if (endpoint.getCookieStore() == null) {
            endpoint.setCookieStore(this.getCookieStore());
        }

        endpoint.setHttpClientOptions(httpClientOptions);
        endpoint.setHttpConnectionOptions(httpConnectionOptions);
        return endpoint;
    }

    public static boolean isSecureConnection(String uri) {
        return uri.startsWith("https") || uri.startsWith("extend-https");
    }
}
