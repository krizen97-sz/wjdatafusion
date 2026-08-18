package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class IpamMapperContractTest
{
    @Test
    void networkRangeValuesMustBeUpdatedOnlyOnNetworkTable() throws Exception
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/ipam/IpamMapper.xml"))
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
            String updateNetwork = xpath.evaluate("/mapper/update[@id='updateNetwork']", document);
            String updateSegment = xpath.evaluate("/mapper/update[@id='updateSegment']", document);

            assertTrue(updateNetwork.contains("start_value = #{startValue}"));
            assertTrue(updateNetwork.contains("end_value = #{endValue}"));
            assertFalse(updateSegment.contains("startValue"));
            assertFalse(updateSegment.contains("endValue"));
        }
    }

    @Test
    void policeStationMustParticipateInNetworkReadWriteAndSearch() throws Exception
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/ipam/IpamMapper.xml"))
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
            String selectNetworkVo = xpath.evaluate("/mapper/sql[@id='selectNetworkVo']", document);
            String selectNetworkList = xpath.evaluate("/mapper/select[@id='selectNetworkList']", document);
            String insertNetwork = xpath.evaluate("/mapper/insert[@id='insertNetwork']", document);
            String updateNetwork = xpath.evaluate("/mapper/update[@id='updateNetwork']", document);

            assertEquals("1", xpath.evaluate(
                "count(/mapper/resultMap[@id='IpamNetworkResult']/result[@property='policeStationName' and @column='police_station_name'])",
                document));
            assertTrue(selectNetworkVo.contains("police_station_name"));
            assertTrue(selectNetworkList.contains("n.police_station_name like"));
            assertTrue(selectNetworkList.contains("trim(n.police_station_name) = #{policeStationName}"));
            assertTrue(insertNetwork.contains("#{policeStationName}"));
            assertTrue(updateNetwork.contains("police_station_name = #{policeStationName}"));
        }
    }

    @Test
    void addressPersistenceMustUseOnlyThePlainPasswordColumn() throws Exception
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/ipam/IpamMapper.xml"))
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
            String insertAddress = xpath.evaluate("/mapper/insert[@id='insertAddress']", document);
            String selectAddress = xpath.evaluate("/mapper/sql[@id='selectAddressVo']", document);
            String updateAddress = xpath.evaluate("/mapper/update[@id='updateAddress']", document);

            assertTrue(insertAddress.contains("login_password,"));
            assertTrue(insertAddress.contains("#{loginPassword}"));
            assertTrue(selectAddress.contains("a.login_password"));
            assertTrue(updateAddress.contains("login_password = #{loginPassword}"));
            assertFalse(insertAddress.contains("login_password_cipher"));
            assertFalse(selectAddress.contains("login_password_cipher"));
            assertFalse(updateAddress.contains("login_password_cipher"));
        }
    }
}
