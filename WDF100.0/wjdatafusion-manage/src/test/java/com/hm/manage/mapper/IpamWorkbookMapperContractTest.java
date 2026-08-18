package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class IpamWorkbookMapperContractTest
{
    @Test
    void communitySheetMustBeExactOrderedAndNeverReturnPlaintextPassword() throws Exception
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/ipam/IpamWorkbookMapper.xml"))
        {
            assertNotNull(input);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(input);
            var xpath = XPathFactory.newInstance().newXPath();
            String catalog = xpath.evaluate("/mapper/select[@id='selectCommunityCatalog']", document);
            String detail = xpath.evaluate("/mapper/select[@id='selectCommunityAddressList']", document);
            String resultMap = xpath.evaluate("/mapper/resultMap[@id='IpamWorkbookAddressResult']", document);

            assertTrue(catalog.contains("group by trim(a.community_name)"));
            assertTrue(catalog.contains("order by min(a.ip_value)"));
            assertFalse(catalog.contains("target_type"));
            assertFalse(catalog.contains("manufacturer"));
            assertTrue(detail.contains("a.status in ('ALLOCATED', 'ISSUED')"));
            assertTrue(detail.contains("trim(a.community_name) = #{communityName}"));
            assertTrue(detail.contains("order by a.ip_value"));
            assertTrue(detail.contains("credential_configured"));
            assertFalse(detail.contains("as login_password"));
            assertFalse(resultMap.contains("loginPassword"));
            assertFalse(detail.contains("like concat"));
        }
    }
}
