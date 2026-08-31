package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class SupportEquipmentTopologyMapperContractTest
{
    @Test
    void roomAndCabinetMappingsMustPersistThreeDimensionalLayout() throws Exception
    {
        Document document = readMapper("mapper/support/SupportEquipmentLocationMapper.xml");
        var xpath = XPathFactory.newInstance().newXPath();
        String roomWidthColumn = xpath.evaluate("/mapper/resultMap[@id='SupportEquipmentRoomResult']/result[@property='roomWidth']/@column", document);
        String roomDepthColumn = xpath.evaluate("/mapper/resultMap[@id='SupportEquipmentRoomResult']/result[@property='roomDepth']/@column", document);
        String cabinetXColumn = xpath.evaluate("/mapper/resultMap[@id='SupportEquipmentCabinetResult']/result[@property='positionX']/@column", document);
        String cabinetZColumn = xpath.evaluate("/mapper/resultMap[@id='SupportEquipmentCabinetResult']/result[@property='positionZ']/@column", document);
        String cabinetRotationColumn = xpath.evaluate("/mapper/resultMap[@id='SupportEquipmentCabinetResult']/result[@property='rotationY']/@column", document);
        String roomInsert = xpath.evaluate("/mapper/insert[@id='insertRoom']", document);
        String cabinetInsert = xpath.evaluate("/mapper/insert[@id='insertCabinet']", document);
        String cabinetLayout = xpath.evaluate("/mapper/update[@id='updateCabinetLayout']", document);
        String hardwarePlacement = xpath.evaluate("/mapper/update[@id='updateHardwarePlacement']", document);
        String serverPlacement = xpath.evaluate("/mapper/update[@id='updateServerPlacement']", document);
        String clearCabinet = xpath.evaluate("/mapper/update[@id='clearHardwareCabinetLocation']", document);
        String hardwareConflicts = xpath.evaluate("/mapper/select[@id='countHardwareRackConflicts']", document);
        String serverConflicts = xpath.evaluate("/mapper/select[@id='countServerRackConflicts']", document);

        assertTrue(roomWidthColumn.contains("room_width"));
        assertTrue(roomDepthColumn.contains("room_depth"));
        assertTrue(cabinetXColumn.contains("position_x"));
        assertTrue(cabinetZColumn.contains("position_z"));
        assertTrue(cabinetRotationColumn.contains("rotation_y"));
        assertTrue(roomInsert.contains("room_width"));
        assertTrue(cabinetInsert.contains("position_x"));
        assertTrue(cabinetLayout.contains("where cabinet_id = #{cabinetId}"));
        assertTrue(hardwarePlacement.contains("where asset_id = #{assetId}"));
        assertTrue(hardwarePlacement.contains("rack_u_start = #{rackUStart}"));
        assertTrue(serverPlacement.contains("where server_id = #{serverId}"));
        assertTrue(serverPlacement.contains("equipment_room = #{equipmentRoom}"));
        assertTrue(clearCabinet.contains("rack_u_start = null"));
        assertTrue(clearCabinet.contains("rack_u_end = null"));
        assertTrue(hardwareConflicts.contains("rack_u_start <= #{rackUEnd}"));
        assertTrue(hardwareConflicts.contains("rack_u_end >= #{rackUStart}"));
        assertTrue(serverConflicts.contains("excludeServerId"));
    }

    @Test
    void equipmentLinksMustKeepTypedEndpointsAndDeviceCleanup() throws Exception
    {
        Document document = readMapper("mapper/support/SupportEquipmentTopologyMapper.xml");
        var xpath = XPathFactory.newInstance().newXPath();
        String insert = xpath.evaluate("/mapper/insert[@id='insertLink']", document);
        String duplicate = xpath.evaluate("/mapper/select[@id='countDuplicateLink']", document);
        String deleteDevice = xpath.evaluate("/mapper/delete[@id='deleteLinksByDevice']", document);

        assertTrue(insert.contains("source_type"));
        assertTrue(insert.contains("target_type"));
        assertTrue(insert.contains("medium_type"));
        assertTrue(insert.contains("port_count"));
        assertTrue(duplicate.contains("source_port"));
        assertTrue(duplicate.contains("target_port"));
        assertTrue(deleteDevice.contains("source_type = #{sourceType}"));
        assertTrue(deleteDevice.contains("target_type = #{sourceType}"));
    }

    @Test
    void equipmentProjectionMustLoadAllPlatformBindingsInOneSiteScopedQuery() throws Exception
    {
        Document document = readMapper("mapper/support/SupportEquipmentBindingMapper.xml");
        var xpath = XPathFactory.newInstance().newXPath();
        String serverBindings = xpath.evaluate("/mapper/select[@id='selectServerBindingsBySiteId']", document);
        String hardwareBindings = xpath.evaluate("/mapper/select[@id='selectHardwareBindingsBySiteId']", document);
        String bindingColumns = xpath.evaluate("/mapper/sql[@id='selectPlatformBindingColumns']", document);
        String serverSourceMapping = xpath.evaluate("/mapper/resultMap[@id='SupportEquipmentPlatformBindingResult']/result[@property='sourceId']/@column", document);
        String hardwarePlatformMapping = xpath.evaluate("/mapper/resultMap[@id='SupportEquipmentPlatformBindingResult']/result[@property='platformId']/@column", document);

        assertTrue(serverSourceMapping.contains("source_id"));
        assertTrue(hardwarePlatformMapping.contains("platform_id"));
        assertTrue(serverBindings.contains("rel.server_id as source_id"));
        assertTrue(serverBindings.contains("where p.site_id = #{siteId}"));
        assertTrue(bindingColumns.contains("main_platform_id"));
        assertTrue(hardwareBindings.contains("rel.asset_id as source_id"));
        assertTrue(hardwareBindings.contains("where p.site_id = #{siteId}"));
        assertTrue(bindingColumns.contains("network_env"));
    }

    private Document readMapper(String resource) throws Exception
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(input);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(input);
        }
    }
}
