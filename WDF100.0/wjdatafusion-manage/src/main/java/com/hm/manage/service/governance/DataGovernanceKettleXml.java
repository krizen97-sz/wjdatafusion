package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.*;

/** Lossless DOM editing of unknown extension nodes; no native engine calls during save. */
public final class DataGovernanceKettleXml
{
    public static final int MAX_XML_BYTES = 2 * 1024 * 1024;
    public static final String ID = "data-rynew-id";
    private static final String SECRET_ID = "data-rynew-secret-id";
    private static final String MARKER = "__RYNEW_SECRET_";
    private static final Pattern SECRET = Pattern.compile("(?i).*(password|passwd|secret|token|access.?key|private.?key|credential|jaas).*|(?i)^pwd$");
    private DataGovernanceKettleXml() { }
    public record Prepared(String xml, String kind, int nodeCount) { }
    private record Slot(Element element, String attribute, String id, String owner, String value) { }

    public static String decode(String base64)
    {
        if (base64 == null || base64.length() > MAX_XML_BYTES * 4 / 3 + 8) reject("XML 最大为 2 MiB");
        try
        {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length > MAX_XML_BYTES) reject("XML 最大为 2 MiB");
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("XML 必须为 UTF-8 的有效 Base64"); }
    }
    public static String encode(String xml) { return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)); }
    public static Prepared prepare(String xml, String previous)
    {
        Document document = parse(xml);
        String kind = document.getDocumentElement().getTagName();
        if (!kind.equals("transformation") && !kind.equals("job")) reject("仅接受 transformation 或 job 根节点的原生 XML");
        stabilize(document);
        Map<String, Slot> old = previous == null ? Map.of() : indexSlots(parse(previous));
        Set<String> consumed = new HashSet<>();
        for (Slot slot : slots(document))
        {
            if (slot.value().contains(MARKER))
            {
                Slot source = old.get(slot.id());
                if (source == null || !slot.value().equals(marker(slot.id())) || !source.owner().equals(slot.owner())
                    || !source.attribute().equals(slot.attribute()) || !source.element().getTagName().equals(slot.element().getTagName())
                    || !consumed.add(slot.id())) reject("秘密保留标记与原元素不匹配，请刷新后重新编辑");
                set(slot, source.value());
            }
        }
        String result = serialize(document);
        if (result.contains(MARKER)) reject("秘密保留标记不能用于其他字段或新建流程");
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) reject("XML 最大为 2 MiB");
        int nodes = kind.equals("job") ? document.getElementsByTagName("entry").getLength() : document.getElementsByTagName("step").getLength();
        return new Prepared(result, kind, nodes);
    }
    public static String masked(String xml)
    {
        Document document = parse(xml);
        for (Slot slot : slots(document)) if (!slot.value().isEmpty()) set(slot, marker(slot.id()));
        return serialize(document);
    }
    public static List<String> secretValues(String xml)
    { return slots(parse(xml)).stream().map(Slot::value).filter(v -> !v.isEmpty()).distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList(); }
    public static String template(String fragment)
    {
        if (fragment == null || fragment.isBlank()) return "";
        Document document = parse("<transformation>" + fragment.replaceFirst("^\\s*<\\?xml[^?]*\\?>", "") + "</transformation>");
        stabilize(document);
        for (Slot slot : slots(document)) set(slot, "");
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++)
        {
            Element element = (Element)elements.item(i); List<String> remove = new ArrayList<>();
            for (int j = 0; j < element.getAttributes().getLength(); j++)
                if (element.getAttributes().item(j).getNodeName().startsWith("data-rynew-")) remove.add(element.getAttributes().item(j).getNodeName());
            remove.forEach(element::removeAttribute);
        }
        String xml = serialize(document);
        return xml.substring(xml.indexOf('>', xml.indexOf("<transformation")) + 1, xml.lastIndexOf("</transformation>"));
    }
    public static Document parse(String xml)
    {
        if (xml == null || xml.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) reject("XML 最大为 2 MiB");
        try
        {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setEntityResolver((a, b) -> { throw new SAXException("External entities disabled"); });
            builder.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) throws SAXException { throw e; }
                public void error(SAXParseException e) throws SAXException { throw e; }
                public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            return builder.parse(new InputSource(new StringReader(xml)));
        }
        catch (Exception e) { throw new ServiceException("XML 无法解析；禁止 DTD、外部实体和无效结构"); }
    }
    static String serialize(Document document)
    {
        try
        {
            TransformerFactory f = TransformerFactory.newInstance();
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = f.newTransformer(); transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no"); transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter(); transformer.transform(new DOMSource(document), new StreamResult(writer)); return writer.toString();
        }
        catch (Exception e) { throw new ServiceException("XML 序列化失败"); }
    }
    private static void stabilize(Document document)
    {
        Set<String> ids = new HashSet<>();
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++)
        {
            Element e = (Element) nodes.item(i);
            if (e == document.getDocumentElement() || Set.of("step", "entry", "connection").contains(e.getTagName()))
            {
                if (!e.hasAttribute(ID)) e.setAttribute(ID, UUID.randomUUID().toString());
                validId(e.getAttribute(ID)); if (!ids.add(e.getAttribute(ID))) reject("元素稳定 ID 重复");
            }
        }
        Set<String> secrets = new HashSet<>();
        for (Slot slot : slots(document)) { validId(slot.id()); if (!secrets.add(slot.id())) reject("秘密字段稳定 ID 重复"); }
    }
    private static Map<String, Slot> indexSlots(Document document)
    { Map<String, Slot> result = new HashMap<>(); for (Slot slot : slots(document)) result.put(slot.id(), slot); return result; }
    private static List<Slot> slots(Document document)
    {
        List<Slot> result = new ArrayList<>(); NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++)
        {
            Element e = (Element) nodes.item(i);
            boolean leaf = true;
            for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element) { leaf = false; break; }
            String owner = owner(e);
            if (leaf && sensitiveElement(e))
            {
                if (!e.hasAttribute(SECRET_ID)) e.setAttribute(SECRET_ID, UUID.randomUUID().toString());
                result.add(new Slot(e, "", e.getAttribute(SECRET_ID), owner, e.getTextContent()));
            }
            List<Attr> attrs = new ArrayList<>();
            for (int j = 0; j < e.getAttributes().getLength(); j++) attrs.add((Attr)e.getAttributes().item(j));
            for (Attr attr : attrs) if (!attr.getName().startsWith("data-rynew-") && sensitiveName(attr.getName()))
            {
                String key = "data-rynew-secret-attr-" + attr.getName().replace(':', '-');
                if (!e.hasAttribute(key)) e.setAttribute(key, UUID.randomUUID().toString());
                result.add(new Slot(e, attr.getName(), e.getAttribute(key), owner, attr.getValue()));
            }
        }
        return result;
    }
    private static boolean sensitiveElement(Element e)
    {
        for (Node current = e; current instanceof Element p; current = current.getParentNode())
            if (sensitiveName(p.getTagName())) return true;
        if (Set.of("value", "attribute").contains(e.getTagName()) && e.getParentNode() instanceof Element parent)
            for (String key : List.of("name", "code", "key"))
                if (sensitiveName(child(parent, key))) return true;
        return false;
    }
    static boolean sensitiveName(String name) { return name != null && SECRET.matcher(name).matches(); }
    private static String owner(Element e)
    { for (Node n = e; n instanceof Element p; n = n.getParentNode()) if (p.hasAttribute(ID)) return p.getAttribute(ID); return "root"; }
    public static String child(Element parent, String name)
    { for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element e && e.getTagName().equals(name)) return e.getTextContent(); return ""; }
    private static void set(Slot slot, String value) { if (slot.attribute().isEmpty()) slot.element().setTextContent(value); else slot.element().setAttribute(slot.attribute(), value); }
    private static String marker(String id) { return MARKER + id + "__"; }
    private static void validId(String id) { if (!id.matches("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")) reject("元素稳定 ID 无效"); }
    static void reject(String message) { throw new ServiceException(message); }
}
