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

class IpamDashboardMapperContractTest
{
    @Test
    void dashboardMustOnlyCountAssignedAddressesAndCommunityDetailMustBeExact() throws Exception
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/ipam/IpamDashboardMapper.xml"))
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
            String overview = xpath.evaluate("/mapper/select[@id='selectCommunityOverview']", document);
            String detail = xpath.evaluate("/mapper/select[@id='selectCommunityAddressList']", document);

            assertTrue(overview.contains("a.status in ('ALLOCATED', 'ISSUED')"));
            assertTrue(overview.contains("nullif(trim(a.community_name), '') is not null"));
            assertTrue(overview.contains("trim(n.police_station_name) = #{policeStationName}"));
            assertTrue(detail.contains("trim(a.community_name) = #{communityName}"));
            assertFalse(detail.contains("login_password"));
            assertFalse(detail.contains("like concat"));
        }
    }
}
