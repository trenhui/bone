//package com.bone.lowcode.integration.transformation;
//
//import io.atlasmap.api.AtlasContext;
//import io.atlasmap.api.AtlasContextFactory;
//import io.atlasmap.api.AtlasSession;
//import io.atlasmap.core.DefaultAtlasContextFactory;
//
//import javax.xml.transform.OutputKeys;
//import javax.xml.transform.Transformer;
//import javax.xml.transform.TransformerFactory;
//import javax.xml.transform.stream.StreamResult;
//import javax.xml.transform.stream.StreamSource;
//import java.io.StringReader;
//import java.io.StringWriter;
//import java.net.URL;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//
//public class AtlasmapDemo {
//
//    public static void main(String args[]) throws Exception {
//        AtlasmapDemo m = new AtlasmapDemo();
//        m.process();
//    }
//
//    public void process() throws Exception {
//        //URL url = Thread.currentThread().getContextClassLoader().getResource("atlasmap/atlasmap-mapping.adm");
//        //URL url = Thread.currentThread().getContextClassLoader().getResource("partner/atlasmap/cpl/acknowledgeClaim.adm");
//        URL url = Thread.currentThread().getContextClassLoader().getResource("partner/atlasmap/cpl/cpl-claimRequest-input.adm");
//
//        AtlasContextFactory factory = DefaultAtlasContextFactory.getInstance();
//        AtlasContext context = factory.createContext(url.toURI());
//        AtlasSession session = context.createSession();
//
//        url = Thread.currentThread().getContextClassLoader().getResource("atlasmap/TransInfo.json");
//        String source = new String(Files.readAllBytes(Paths.get(url.toURI())));
//        System.out.println("Source document:\n" + source);
//
//        session.setSourceDocument("JSONSchemaSource", source);
//        context.process(session);
//
//        String targetDoc = (String) session.getTargetDocument("JSONInstanceSource");
//        Object targetObj = (String) session.getTargetDocument("JAVAInstanceSource");
//         //String targetDoc = (String) session.getTargetDocument("XMLInstanceSource");
//       // printXML(targetDoc);
//    }
//
//    private void printXML(String targetDoc) throws Exception {
//        Transformer transformer = TransformerFactory.newInstance().newTransformer();
//        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
//        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
//        StringWriter writer = new StringWriter();
//        transformer.transform(new StreamSource(new StringReader((String) targetDoc)), new StreamResult(writer));
//        System.out.println("Target Document:\n" + writer.toString());
//    }
//}