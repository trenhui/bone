package com.bone.lowcode.integration.uitls;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bone.lowcode.integration.flow.node.WebServiceNode;
import com.sun.xml.ws.client.BindingProviderProperties;
import com.sun.xml.ws.developer.JAXWSProperties;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.io.DOMReader;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.ws.Dispatch;
import java.net.URL;
import java.util.List;

public class WebServiceExecutor {

    public static void main(String[] args) throws Exception {
        WebServiceNode webServiceNode = new WebServiceNode();
        webServiceNode.setTns("http://server.webservice.springbootdemo.example.org/");
        webServiceNode.setNs("org.example.springbootdemo.webservice.server");
        webServiceNode.setWsdlUrl("http://127.0.0.1:9999/ws/helloWebService?wsdl");
        webServiceNode.setServiceName("HelloWebServiceImplService");
        webServiceNode.setPortName("HelloWebServiceImplPort");
        webServiceNode.setMethodName("addUser2");

        String content = "{\n" +
                "    \"user\": {\n" +
                "        \"name\": \"coco\",\n" +
                "        \"age\": 8,\n" +
                "        \"email\": \"coco@163.com\",\n" +
                "        \"school\": \"大关小学\"\n" +
                "    },\n" +
                "    \"token\": \"123dsfsdfslfsk\"\n" +
                "}";

        execute(content, webServiceNode);
    }

    public static String execute(String content, WebServiceNode webServiceNode) throws Exception {
        String nameSpace = webServiceNode.getTns();
        String origNameSpace = webServiceNode.getNs();
        String wsdlUrl = webServiceNode.getWsdlUrl();
        String serviceName = webServiceNode.getServiceName();
        String portName = webServiceNode.getPortName();
        String methodName = webServiceNode.getMethodName();
        String protocol = webServiceNode.getProtocol();

        URL url = new URL(wsdlUrl);
        QName sname = new QName(nameSpace, serviceName);
        javax.xml.ws.Service service = javax.xml.ws.Service.create(url, sname);

        Dispatch<SOAPMessage> dispatch = service.createDispatch(new QName(nameSpace, portName), SOAPMessage.class, javax.xml.ws.Service.Mode.MESSAGE);

        SOAPMessage msg;
        if ("1.2".equals(protocol)) {
            msg = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL).createMessage();
        } else {
            msg = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL).createMessage();
        }

        msg.setProperty(SOAPMessage.CHARACTER_SET_ENCODING, "UTF-8");

        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        envelope.setPrefix("soap");
        envelope.addNamespaceDeclaration("tem", nameSpace);
        envelope.addNamespaceDeclaration("org", origNameSpace);
        envelope.removeAttribute("xmlns:env");

        SOAPHeader header = envelope.getHeader();
        if (null == header) {
            header = envelope.addHeader();
        }
        header.setPrefix("soap");

        SOAPBody body = envelope.getBody();
        SOAPBodyElement bodyElement = body.addBodyElement(new QName(origNameSpace, methodName, "org"));
        generateParams(bodyElement, convertToJSON(content));

        msg.writeTo(System.out); // print xml
        System.out.println("\n----------");

        // set timeout
        dispatch.getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, 2000);
        dispatch.getRequestContext().put(JAXWSProperties.REQUEST_TIMEOUT, 2000);
        SOAPMessage response = dispatch.invoke(msg);
        System.out.println("\n~~~~~~~~");
        response.writeTo(System.out);
        System.out.println();

        Document doc = response.getSOAPPart().getEnvelope().getBody().extractContentAsDocument();
        DOMReader xmlReader = new DOMReader();
        org.dom4j.Document dom = xmlReader.read(doc);

        JSONObject result = new JSONObject();
        dom4j2Json(dom.getRootElement(), result);
        System.out.println(result);

        return result.toString();
    }

    private static JSON convertToJSON(String content) {
        if (content == null) {
            return null;
        }

        return JSON.parseObject(content);
    }

    private static void generateParams(SOAPElement payload, JSON params) throws SOAPException {
        if (params instanceof JSONObject) {
            JSONObject obj = (JSONObject) params;
            for (String key : obj.keySet()) {
                if (obj.get(key) instanceof JSONObject) {
                    SOAPElement soapElement = payload.addChildElement(key);
                    generateParams(soapElement, (JSON) obj.get(key));
                } else if (obj.get(key) instanceof JSONArray) {
                    JSONArray ary = ((JSONArray) obj.get(key));
                    for (Object ar : ary) {
                        SOAPElement soapElement = payload.addChildElement(key);
                        generateParams(soapElement, (JSON) ar);
                    }
                } else {
                    payload.addChildElement(key).setValue(obj.get(key) == null ? "" : obj.get(key).toString());
                }
            }
        }
        if (params instanceof JSONArray) {
            JSONArray ary = ((JSONArray) params);
            for (Object obj : ary) {
                generateParams(payload, (JSON) obj);
            }
        }
    }

    private static void dom4j2Json(Element element, JSONObject json) {
        //如果是属性
        for (Object o : element.attributes()) {
            Attribute attr = (Attribute) o;
            if (StringUtils.isNotEmpty(attr.getValue())) {
                json.put("@" + attr.getName(), attr.getValue());
            }
        }
        List<Element> chdEl = element.elements();
        if (chdEl.isEmpty()) {
            //如果没有子元素,只有一个值  null值也放入JSON
            json.put(element.getName(), element.getText());
        } else {
            for (Element e : chdEl) {
                if (!e.elements().isEmpty()) {
                    //子元素也有子元素
                    JSONObject chdjson = new JSONObject();
                    dom4j2Json(e, chdjson);
                    Object o = json.get(e.getName());
                    if (o != null) {
                        JSONArray jsona = null;
                        if (o instanceof JSONObject) {
                            //如果此元素已存在,则转为jsonArray
                            JSONObject jsono = (JSONObject) o;
                            json.remove(e.getName());
                            jsona = new JSONArray();
                            jsona.add(jsono);
                            jsona.add(chdjson);
                        }
                        if (o instanceof JSONArray) {
                            jsona = (JSONArray) o;
                            jsona.add(chdjson);
                        }
                        json.put(e.getName(), jsona);
                    } else {
                        if (!chdjson.isEmpty()) {
                            json.put(e.getName(), chdjson);
                        } else {
                            json.put(e.getName(), null);
                        }
                    }


                } else {
                    //子元素没有子元素
                    for (Object o : element.attributes()) {
                        Attribute attr = (Attribute) o;
                        if (StringUtils.isNotEmpty(attr.getValue())) {
                            json.put("@" + attr.getName(), attr.getValue());
                        }
                    }
                    json.put(e.getName(), e.getText());
                }
            }
        }
    }
}
