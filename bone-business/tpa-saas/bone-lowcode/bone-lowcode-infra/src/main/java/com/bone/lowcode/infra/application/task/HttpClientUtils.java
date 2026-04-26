package com.bone.lowcode.infra.application.task;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpClientUtils {

    private static final RequestConfig defaultConfig;

    private static final CloseableHttpClient httpClient;

    public static final String CHARSET = "UTF-8";

    static {
        defaultConfig = RequestConfig.custom().setConnectTimeout(60000).setSocketTimeout(60000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(defaultConfig).build();
    }

    public static String doGet(String url, Map<String, String> params, Integer connectTimeout) {
        RequestConfig localConfig = RequestConfig.custom().setConnectTimeout(connectTimeout).setSocketTimeout(connectTimeout).build();
        String result = null;
        try {
            result = doGet(url, params, CHARSET, localConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String doGet(String url, Map<String, String> params) {
        String result = null;
        try {
            result = doGet(url, params, CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String doPost(String url, Map<String, String> params, Integer connectTimeout) {
        RequestConfig localConfig = RequestConfig.custom().setConnectTimeout(connectTimeout).setSocketTimeout(connectTimeout).build();
        String result = null;
        try {
            result = doPost(url, params, CHARSET, localConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    public static String doPost(String url, Map<String, String> params) {
        String result = null;
        try {
            result = doPost(url, params, CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String doPost(String url, String transJson, Integer connectTimeout) {
        RequestConfig localConfig = RequestConfig.custom().setConnectTimeout(connectTimeout).setSocketTimeout(connectTimeout).build();
        String result = null;
        try {
            result = doPost(url, transJson, CHARSET,localConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String doPost(String url, String transJson) {
        String result = null;
        try {
            result = doPost(url, transJson, CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * HTTP Get ��ȡ����
     *
     * @param url     �����url��ַ ?֮ǰ�ĵ�ַ
     * @param params  ����Ĳ���
     * @param charset �����ʽ
     * @return ҳ������
     */
    public static String doGet(String url, Map<String, String> params, String charset) throws Exception {
        return doGet(url, params, charset, defaultConfig);
    }

    private static String doGet(String url, Map<String, String> params, String charset, RequestConfig requestConfig) throws Exception {

        try {
            if (params != null && !params.isEmpty()) {
                List<NameValuePair> pairs = new ArrayList<NameValuePair>(params.size());
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String value = entry.getValue();
                    if (value != null) {
                        pairs.add(new BasicNameValuePair(entry.getKey(), value));
                    }
                }
                url += "?" + EntityUtils.toString(new UrlEncodedFormEntity(pairs, charset));
            }
            HttpGet httpGet = new HttpGet(url);
            httpGet.setConfig(requestConfig);
            CloseableHttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                httpGet.abort();
                throw new RuntimeException("HttpClient,error status code :" + statusCode);
            }
            HttpEntity entity = response.getEntity();
            String result = null;
            if (entity != null) {
                result = EntityUtils.toString(entity, "utf-8");
            }
            EntityUtils.consume(entity);
            response.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static String doPost(String url, Map<String, String> params, String charset) throws Exception {
        return doPost(url, params, charset, defaultConfig);
    }

    /**
     * HTTP Post ��ȡ����
     *
     * @param url     �����url��ַ ?֮ǰ�ĵ�ַ
     * @param params  ����Ĳ���
     * @param charset �����ʽ
     * @return ҳ������
     */
    public static String doPost(String url, Map<String, String> params, String charset, RequestConfig requestConfig) throws Exception {
        try {
            List<NameValuePair> pairs = null;
            if (params != null && !params.isEmpty()) {
                pairs = new ArrayList<NameValuePair>(params.size());
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String value = entry.getValue();
                    if (value != null) {
                        pairs.add(new BasicNameValuePair(entry.getKey(), value));
                    }
                }
            }
            HttpPost httpPost = new HttpPost(url);
            httpPost.setConfig(requestConfig);
            if (pairs != null && pairs.size() > 0) {
                httpPost.setEntity(new UrlEncodedFormEntity(pairs, CHARSET));
            }
            CloseableHttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                httpPost.abort();
                throw new RuntimeException("HttpClient,error status code :" + statusCode);
            }
            HttpEntity entity = response.getEntity();
            String result = null;
            if (entity != null) {
                result = EntityUtils.toString(entity, "utf-8");
            }
            EntityUtils.consume(entity);

            response.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static String doPost(String url, String transJson, String charset) throws Exception {
        return doPost(url, transJson, charset, defaultConfig);
    }

    private static String doPost(String url, String transJson, String charset, RequestConfig requestConfig) throws Exception {
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setConfig(requestConfig);
            httpPost.setHeader(HTTP.CONTENT_TYPE, "application/json");
            StringEntity se = new StringEntity(transJson, charset);
            se.setContentType("text/json");
            httpPost.setEntity(se);
            CloseableHttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                httpPost.abort();
                throw new RuntimeException("HttpClient,error status code :" + statusCode);
            }
            HttpEntity entity = response.getEntity();
            String result = null;
            if (entity != null) {
                result = EntityUtils.toString(entity, charset);
            }
            EntityUtils.consume(entity);

            response.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
